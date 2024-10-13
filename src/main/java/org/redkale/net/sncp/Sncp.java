/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.sncp;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.reflect.*;
import java.nio.channels.CompletionHandler;
import java.util.*;
import org.redkale.annotation.*;
import org.redkale.asm.*;
import static org.redkale.asm.ClassWriter.COMPUTE_FRAMES;
import static org.redkale.asm.Opcodes.*;
import org.redkale.asm.Type;
import org.redkale.convert.pb.ProtobufConvert;
import org.redkale.inject.Resourcable;
import org.redkale.inject.ResourceFactory;
import org.redkale.mq.spi.MessageAgent;
import org.redkale.net.http.WebSocketNode;
import org.redkale.scheduled.Scheduled;
import org.redkale.service.*;
import org.redkale.util.AnyValue;
import org.redkale.util.RedkaleClassLoader;
import org.redkale.util.TypeToken;
import org.redkale.util.Uint128;
import org.redkale.util.Utility;
import static org.redkale.util.Utility.isEmpty;

/**
 * Service Node Communicate Protocol 生成Service的本地模式或远程模式Service-Class的工具类
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
public abstract class Sncp {

    static final String FIELDPREFIX = "_redkale";

    /**
     * 修饰由SNCP协议动态生成的class、和method 本地模式：动态生成的_DynLocalXXXXService类会打上&#64;SncpDyn(remote = false) 的注解
     * 远程模式：动态生成的_DynRemoteXXXService类会打上&#64;SncpDyn(remote = true) 的注解
     *
     * <p>详情见: https://redkale.org
     *
     * @author zhangjx
     */
    @Documented
    @Target({METHOD, TYPE})
    @Retention(RUNTIME)
    public static @interface SncpDyn {

        boolean remote();

        Class type(); // serviceType

        int index() default 0; // 排列顺序， 主要用于Method
    }

    private Sncp() {
        // do nothing
    }

    // key: actionid
    public static LinkedHashMap<Uint128, Method> loadRemoteMethodActions(final Class serviceTypeOrImplClass) {
        final List<Method> list = new ArrayList<>();
        final List<Method> multis = new ArrayList<>();
        final Map<Uint128, Method> actionids = new LinkedHashMap<>();
        RedkaleClassLoader.putReflectionPublicMethods(serviceTypeOrImplClass.getName());
        for (final java.lang.reflect.Method method : serviceTypeOrImplClass.getMethods()) {
            if (method.isSynthetic()) {
                continue;
            }
            if (method.getAnnotation(Scheduled.class) != null) {
                continue;
            }
            if (Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (Modifier.isFinal(method.getModifiers())) {
                continue;
            }
            if (method.getAnnotation(Local.class) != null) {
                continue;
            }
            if (method.getAnnotation(ResourceChanged.class) != null) {
                continue;
            }
            if (method.getName().equals("getClass") || method.getName().equals("toString")) {
                continue;
            }
            if (method.getName().equals("equals") || method.getName().equals("hashCode")) {
                continue;
            }
            if (method.getName().equals("notify")
                    || method.getName().equals("notifyAll")
                    || method.getName().equals("wait")) {
                continue;
            }
            if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == AnyValue.class) {
                if (method.getName().equals("init") || method.getName().equals("destroy")) {
                    continue;
                }
            }

            Uint128 actionid = Sncp.actionid(method);
            Method old = actionids.get(actionid);
            if (old != null) {
                if (old.getDeclaringClass().equals(method.getDeclaringClass())) {
                    throw new SncpException(serviceTypeOrImplClass.getName() + " have one more same action(Method="
                            + method + ", " + old + ", actionid=" + actionid + ")");
                }
                continue;
            }
            actionids.put(actionid, method);
            if (method.getAnnotation(Sncp.SncpDyn.class) != null) {
                multis.add(method);
            } else {
                list.add(method);
            }
        }
        multis.sort((m1, m2) -> m1.getAnnotation(Sncp.SncpDyn.class).index()
                - m2.getAnnotation(Sncp.SncpDyn.class).index());
        list.sort((Method o1, Method o2) -> {
            if (!o1.getName().equals(o2.getName())) {
                return o1.getName().compareTo(o2.getName());
            }
            if (o1.getParameterCount() != o2.getParameterCount()) {
                return o1.getParameterCount() - o2.getParameterCount();
            }
            return 0;
        });
        // 带SncpDyn必须排在前面
        multis.addAll(list);
        final LinkedHashMap<Uint128, Method> rs = new LinkedHashMap<>();
        for (Method method : multis) {
            for (Map.Entry<Uint128, Method> en : actionids.entrySet()) {
                if (en.getValue() == method) {
                    rs.put(en.getKey(), en.getValue());
                    break;
                }
            }
        }
        return rs;
    }

    public static <T extends Service> SncpRemoteInfo createSncpRemoteInfo(
            String resourceName,
            Class<T> resourceServiceType,
            Class<T> serviceImplClass,
            ProtobufConvert convert,
            SncpRpcGroups sncpRpcGroups,
            SncpClient sncpClient,
            MessageAgent messageAgent,
            String remoteGroup) {
        return new SncpRemoteInfo(
                resourceName,
                resourceServiceType,
                serviceImplClass,
                convert,
                sncpRpcGroups,
                sncpClient,
                messageAgent,
                remoteGroup);
    }

    /**
     * 格式：资源类型:资源名
     *
     * @param resourceName 资源名
     * @param resourceType 资源类型
     * @return resourceid
     */
    public static String resourceid(String resourceName, Class resourceType) {
        return resourceType.getName() + ':' + (resourceName == null ? "" : resourceName);
    }

    public static Uint128 serviceid(String serviceResourceName, Class serviceResourceType) {
        return hash(resourceid(serviceResourceName, serviceResourceType));
    }

    public static Uint128 actionid(final RpcAction action) {
        return hash(action.name());
    }

    public static Uint128 actionid(final java.lang.reflect.Method method) {
        if (method == null) {
            return Uint128.ZERO;
        }
        RpcAction action = method.getAnnotation(RpcAction.class);
        if (action != null) {
            return hash(action.name());
        }
        StringBuilder sb = new StringBuilder(); // 不能使用method.toString() 因为包含declaringClass信息导致接口与实现类的方法hash不一致
        sb.append(method.getReturnType().getName()).append(' ');
        sb.append(method.getName());
        sb.append('(');
        boolean first = true;
        for (Class pt : method.getParameterTypes()) {
            if (!first) {
                sb.append(',');
            }
            sb.append(pt.getName());
            first = false;
        }
        sb.append(')');
        return hash(sb.toString());
    }

    /**
     * 对类名或者name字符串进行hash。
     *
     * @param name String
     * @return hash值
     */
    private static Uint128 hash(final String name) {
        if (name == null || name.isEmpty()) {
            return Uint128.ZERO;
        }
        return Uint128.create(Utility.md5(name.trim().getBytes()));
    }

    public static boolean isRemote(Service service) {
        SncpDyn dyn = service.getClass().getAnnotation(SncpDyn.class);
        return dyn != null && dyn.remote();
    }

    public static boolean isSncpDyn(Service service) {
        return service.getClass().getAnnotation(SncpDyn.class) != null;
    }

    public static boolean isSncpDyn(Class serviceType) {
        return serviceType.getAnnotation(SncpDyn.class) != null;
    }

    public static boolean isComponent(Service service) {
        return service.getClass().getAnnotation(Component.class) != null;
    }

    public static boolean isComponent(Class serviceType) {
        return serviceType.getAnnotation(Component.class) != null;
    }

    public static int getVersion(Service service) {
        return -1; // 预留功能，暂不实现
    }

    public static String getResourceName(Service service) {
        Resource res = service.getClass().getAnnotation(Resource.class);
        return res != null
                ? res.name()
                : (service instanceof Resourcable ? ((Resourcable) service).resourceName() : null);
    }

    public static Class getResourceType(Service service) {
        ResourceType type = service.getClass().getAnnotation(ResourceType.class);
        return type != null ? type.value() : service.getClass();
    }

    public static <T extends Service> Class getResourceType(Class<T> serviceImplClass) {
        ResourceType type = serviceImplClass.getAnnotation(ResourceType.class);
        return type != null ? type.value() : serviceImplClass;
    }

    public static Class getServiceType(Service service) {
        SncpDyn dyn = service.getClass().getAnnotation(SncpDyn.class);
        return dyn != null ? dyn.type() : service.getClass();
    }

    public static <T extends Service> Class getServiceType(Class<T> serviceImplClass) {
        SncpDyn dyn = serviceImplClass.getAnnotation(SncpDyn.class);
        return dyn != null ? dyn.type() : serviceImplClass;
    }

    // 格式: sncp.req.module.user
    public static String generateSncpReqTopic(Service service, String nodeid) {
        return generateSncpReqTopic(getResourceName(service), getResourceType(service), nodeid);
    }

    // 格式: sncp.req.module.user
    public static String generateSncpReqTopic(String resourceName, Class resourceType, String nodeid) {
        if (WebSocketNode.class.isAssignableFrom(resourceType)) {
            return getSncpReqTopicPrefix() + "module.wsnode" + nodeid
                    + (isEmpty(resourceName) ? "" : ("-" + resourceName));
        }
        return getSncpReqTopicPrefix() + "module."
                + resourceType.getSimpleName().replaceAll("Service.*$", "").toLowerCase()
                + (isEmpty(resourceName) ? "" : ("-" + resourceName));
    }

    public static String getSncpReqTopicPrefix() {
        return "sncp.req.";
    }

    public static String getSncpRespTopicPrefix() {
        return "sncp.resp.";
    }

    public static AnyValue getResourceConf(Service service) {
        if (service == null || !isSncpDyn(service)) {
            return null;
        }
        try {
            Field ts = service.getClass().getDeclaredField(FIELDPREFIX + "_conf");
            ts.setAccessible(true);
            return (AnyValue) ts.get(service);
        } catch (Exception e) {
            throw new SncpException(service + " not found " + FIELDPREFIX + "_conf");
        }
    }

    public static String getResourceMQ(Service service) {
        if (service == null || !isSncpDyn(service)) {
            return null;
        }
        try {
            Field ts = service.getClass().getDeclaredField(FIELDPREFIX + "_mq");
            ts.setAccessible(true);
            return (String) ts.get(service);
        } catch (Exception e) {
            throw new SncpException(service + " not found " + FIELDPREFIX + "_mq");
        }
    }

    static void checkAsyncModifier(Class param, Method method) {
        if (param == CompletionHandler.class) {
            return;
        }
        if (Modifier.isFinal(param.getModifiers())) {
            throw new SncpException("CompletionHandler Type Parameter on {" + method + "} cannot final modifier");
        }
        if (!Modifier.isPublic(param.getModifiers())) {
            throw new SncpException("CompletionHandler Type Parameter on {" + method + "} must be public modifier");
        }
        if (param.isInterface()) {
            return;
        }
        boolean constructorflag = false;
        RedkaleClassLoader.putReflectionDeclaredConstructors(param, param.getName());
        for (Constructor c : param.getDeclaredConstructors()) {
            if (c.getParameterCount() == 0) {
                int mod = c.getModifiers();
                if (Modifier.isPublic(mod) || Modifier.isProtected(mod)) {
                    constructorflag = true;
                    break;
                }
            }
        }
        if (param.getDeclaredConstructors().length == 0) {
            constructorflag = true;
        }
        if (!constructorflag) {
            throw new SncpException(param + " must have a empty parameter Constructor");
        }
        for (Method m : param.getMethods()) {
            if (m.getName().equals("completed") && m.getParameterCount() == 2 && Modifier.isFinal(m.getModifiers())) {
                throw new SncpException(param + "'s completed method cannot final modifier");
            } else if (m.getName().equals("failed")
                    && m.getParameterCount() == 2
                    && Modifier.isFinal(m.getModifiers())) {
                throw new SncpException(param + "'s failed method cannot final modifier");
            }
        }
    }

    public static String toSimpleString(final Service service, int maxNameLength, int maxTypeLength) {
        StringBuilder sb = new StringBuilder();
        sb.append(isRemote(service) ? "RemoteService" : "LocalService ");
        int len;
        Class type = getResourceType(service);
        String name = getResourceName(service);
        if (name == null) {
            name = "";
        }
        sb.append("(type= ").append(type.getName());
        len = maxTypeLength - type.getName().length();
        for (int i = 0; i < len; i++) {
            sb.append(' ');
        }
        sb.append(", name='").append(name).append("'");
        for (int i = 0; i < maxNameLength - name.length(); i++) {
            sb.append(' ');
        }
        sb.append(")");
        return sb.toString();
    }

    // 获取一个clazz内所有未被实现的方法
    public static List<Method> loadNotImplMethods(Class clazz) {
        LinkedHashSet<Class> types = new LinkedHashSet<>();
        loadAllSubClasses(clazz, types);
        List<Method> methods = new ArrayList<>();
        Set<String> ms = new HashSet<>();
        for (Class c : types) {
            for (Method m : c.getDeclaredMethods()) {
                if (c.isInterface() || Modifier.isAbstract(m.getModifiers())) {
                    StringBuilder sb =
                            new StringBuilder(); // 不能使用method.toString() 因为包含declaringClass信息导致接口与实现类的方法hash不一致
                    sb.append(m.getName());
                    sb.append('(');
                    boolean first = true;
                    for (Class pt : m.getParameterTypes()) {
                        if (!first) {
                            sb.append(',');
                        }
                        sb.append(pt.getName());
                        first = false;
                    }
                    sb.append(')');
                    String key = sb.toString();
                    Uint128 a = actionid(m);
                    if (!ms.contains(key)) {
                        methods.add(m);
                        ms.add(key);
                    }
                }
            }
        }
        return methods;
    }

    private static void loadAllSubClasses(Class clazz, LinkedHashSet<Class> types) {
        if (clazz == null || clazz == Object.class) {
            return;
        }
        types.add(clazz);
        if (clazz.getSuperclass() != null) {
            loadAllSubClasses(clazz.getSuperclass(), types);
        }
        if (clazz.getInterfaces() != null) {
            for (Class sub : clazz.getInterfaces()) {
                loadAllSubClasses(sub, types);
            }
        }
    }

    /**
     *
     *
     * <blockquote>
     *
     * <pre>
     * public class TestService implements Service {
     *
     *      public String findSomeThing(){
     *          return "hello";
     *      }
     *
     *      &#64;RpcMultiRun(selfrun = false)
     *      public void createSomeThing(TestBean bean){
     *          //do something
     *      }
     *
     *      &#64;RpcMultiRun
     *      public String updateSomeThing(String id){
     *          return "hello" + id;
     *      }
     * }
     * </pre>
     *
     * </blockquote>
     *
     * <blockquote>
     *
     * <pre>
     * &#64;Resource(resourceName = "")
     * &#64;SncpDyn(remote = false)
     * &#64;ResourceType(TestService.class)
     * public final class _DynLocalTestService extends TestService {
     *
     *      private AnyValue _redkale_conf;
     *
     * }
     * </pre>
     *
     * </blockquote>
     *
     * 创建Service的本地模式Class
     *
     * @param <T> Service子类
     * @param classLoader DynBytesClassLoader
     * @param resourceName 资源名
     * @param serviceImplClass Service类
     * @param methodBoost 方法扩展
     * @return Service实例
     */
    @SuppressWarnings("unchecked")
    protected static <T extends Service> Class<? extends T> createLocalServiceClass(
            RedkaleClassLoader classLoader,
            final String resourceName,
            final Class<T> serviceImplClass,
            final AsmMethodBoost methodBoost) {
        Objects.requireNonNull(serviceImplClass);
        if (!Service.class.isAssignableFrom(serviceImplClass)) {
            throw new SncpException(serviceImplClass + " is not Service type");
        }
        ResourceFactory.checkResourceName(resourceName);
        int mod = serviceImplClass.getModifiers();
        if (!java.lang.reflect.Modifier.isPublic(mod)) {
            throw new SncpException(serviceImplClass + " is not public");
        }
        if (java.lang.reflect.Modifier.isAbstract(mod)) {
            throw new SncpException(serviceImplClass + " is abstract");
        }
        final String supDynName = serviceImplClass.getName().replace('.', '/');
        final String resDesc = Type.getDescriptor(Resource.class);
        final String anyValueDesc = Type.getDescriptor(AnyValue.class);
        final String sncpDynDesc = Type.getDescriptor(SncpDyn.class);
        // String newDynName = supDynName.substring(0, supDynName.lastIndexOf('/') + 1) + LOCALPREFIX +
        // serviceImplClass.getSimpleName();
        String newDynName = "org/redkaledyn/service/local/_DynLocalService__"
                + serviceImplClass.getName().replace('.', '_').replace('$', '_');
        if (!resourceName.isEmpty()) {
            boolean normal = true;
            for (char ch : resourceName.toCharArray()) {
                if (!((ch >= '0' && ch <= '9') || ch == '_' || (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z'))) {
                    normal = false;
                }
            }
            if (!normal) {
                throw new SncpException(serviceImplClass + "'s resource name is illegal, must be 0-9 _ a-z A-Z");
            }
            newDynName += "_" + (normal ? resourceName : hash(resourceName));
        }
        // if (Utility.inNativeImage() || methodBoost == null) { // 加强动态时不能重复加载
        try {
            return (Class<T>) classLoader.loadClass(newDynName.replace('/', '.'));
        } catch (Throwable t) {
            // do nothing
        }
        // }
        // ------------------------------------------------------------------------------
        ClassWriter cw = new ClassWriter(COMPUTE_FRAMES);
        FieldVisitor fv;
        MethodDebugVisitor mv;
        AnnotationVisitor av0;

        cw.visit(V11, ACC_PUBLIC + ACC_SUPER, newDynName, null, supDynName, null);
        { // 给动态生成的Service类标记上Resource
            av0 = cw.visitAnnotation(resDesc, true);
            av0.visit("name", resourceName);
            av0.visitEnd();
        }
        {
            av0 = cw.visitAnnotation(sncpDynDesc, true);
            av0.visit("remote", Boolean.FALSE);
            av0.visit("type", Type.getType(Type.getDescriptor(serviceImplClass)));
            av0.visitEnd();
        }
        { // 给新类加上原有的Annotation
            for (Annotation ann : serviceImplClass.getAnnotations()) {
                if (ann instanceof Resource || ann instanceof SncpDyn || ann instanceof ResourceType) {
                    continue;
                }
                Asms.visitAnnotation(
                        cw.visitAnnotation(Type.getDescriptor(ann.annotationType()), true), ann.annotationType(), ann);
            }
        }
        {
            av0 = cw.visitAnnotation(Type.getDescriptor(ResourceType.class), true);
            ResourceType rty = serviceImplClass.getAnnotation(ResourceType.class);
            org.redkale.util.ResourceType rty2 = serviceImplClass.getAnnotation(org.redkale.util.ResourceType.class);
            av0.visit(
                    "value",
                    Type.getType(Type.getDescriptor(
                            rty != null ? rty.value() : (rty2 != null ? rty2.value() : serviceImplClass))));
            av0.visitEnd();
        }
        {
            fv = cw.visitField(ACC_PRIVATE, FIELDPREFIX + "_conf", anyValueDesc, null, null);
            fv.visitEnd();
        }
        {
            fv = cw.visitField(ACC_PRIVATE, FIELDPREFIX + "_mq", Type.getDescriptor(String.class), null, null);
            fv.visitEnd();
        }
        if (methodBoost != null) {
            createNewMethods(classLoader, serviceImplClass, methodBoost, new HashSet<>(), cw, newDynName, supDynName);
            methodBoost.doAfterMethods(classLoader, cw, newDynName, FIELDPREFIX);
        }
        { // 构造函数
            mv = new MethodDebugVisitor(cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null));
            // mv.setDebug(true);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, supDynName, "<init>", "()V", false);
            if (methodBoost != null) {
                methodBoost.doConstructorMethod(classLoader, cw, mv, newDynName, FIELDPREFIX, false);
            }
            mv.visitInsn(RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        cw.visitEnd();
        byte[] bytes = cw.toByteArray();
        final String newDynClass = newDynName.replace('/', '.');
        Class<?> newClazz = classLoader.loadClass(newDynClass, bytes);
        RedkaleClassLoader.putReflectionPublicClasses(newDynClass);
        RedkaleClassLoader.putReflectionDeclaredConstructors(newClazz, newDynClass);
        try {
            Field c = newClazz.getDeclaredField(FIELDPREFIX + "_conf");
            RedkaleClassLoader.putReflectionField(newDynClass, c);

            c = newClazz.getDeclaredField(FIELDPREFIX + "_mq");
            RedkaleClassLoader.putReflectionField(newDynClass, c);
        } catch (Exception e) {
            // do nothing
        }
        return (Class<T>) newClazz;
    }

    public static <T extends Service> T createSimpleLocalService(
            RedkaleClassLoader classLoader, Class<T> serviceImplClass, ResourceFactory resourceFactory) {
        return createLocalService(
                classLoader, "", serviceImplClass, null, resourceFactory, null, null, null, null, null);
    }

    private static void createNewMethods(
            RedkaleClassLoader classLoader,
            Class clazz,
            final AsmMethodBoost methodBoost,
            Set<String> methodKeys,
            ClassWriter cw,
            String newDynName,
            String supDynName) {
        if (methodBoost == null) {
            return;
        }
        MethodDebugVisitor mv = null;
        do {
            Map<String, AsmMethodBean> methodBeans = AsmMethodBoost.getMethodBeans(clazz);
            for (final Method method : clazz.getDeclaredMethods()) {
                String mk = Utility.methodKey(method);
                if (methodKeys.contains(mk)) {
                    // 跳过已处理的继承方法
                    continue;
                }
                methodKeys.add(mk);
                List<Class<? extends Annotation>> filterAnns = methodBoost.filterMethodAnnotations(method);
                AsmNewMethod newMethod =
                        methodBoost.doMethod(classLoader, cw, clazz, newDynName, FIELDPREFIX, filterAnns, method, null);
                if (newMethod != null) {
                    String desc = Type.getMethodDescriptor(method);
                    AsmMethodBean methodBean = AsmMethodBean.get(methodBeans, method);
                    String signature = null;
                    String[] exceptions = null;
                    if (methodBean == null) {
                        Class<?>[] expTypes = method.getExceptionTypes();
                        if (expTypes.length > 0) {
                            exceptions = new String[expTypes.length];
                            for (int i = 0; i < expTypes.length; i++) {
                                exceptions[i] = expTypes[i].getName().replace('.', '/');
                            }
                        }
                    } else {
                        signature = methodBean.getSignature();
                        exceptions = methodBean.getExceptions();
                    }
                    // 需要定义一个新方法调用 super.method
                    mv = new MethodDebugVisitor(cw.visitMethod(
                            newMethod.getMethodAccs(), newMethod.getMethodName(), desc, signature, exceptions));
                    Label l0 = new Label();
                    mv.visitLabel(l0);
                    // mv.setDebug(true);
                    mv.visitVarInsn(ALOAD, 0);
                    // 传参数
                    Class[] paramTypes = method.getParameterTypes();
                    List<Integer> insns = new ArrayList<>();
                    int insn = 0;
                    for (Class pt : paramTypes) {
                        insn++;
                        if (pt.isPrimitive()) {
                            if (pt == long.class) {
                                mv.visitVarInsn(LLOAD, insn++);
                            } else if (pt == float.class) {
                                mv.visitVarInsn(FLOAD, insn++);
                            } else if (pt == double.class) {
                                mv.visitVarInsn(DLOAD, insn++);
                            } else {
                                mv.visitVarInsn(ILOAD, insn);
                            }
                        } else {
                            mv.visitVarInsn(ALOAD, insn);
                        }
                        insns.add(insn);
                    }
                    mv.visitMethodInsn(
                            INVOKESPECIAL, supDynName, method.getName(), Type.getMethodDescriptor(method), false);
                    if (method.getGenericReturnType() == void.class) {
                        mv.visitInsn(RETURN);
                    } else {
                        Class returnclz = method.getReturnType();
                        if (returnclz.isPrimitive()) {
                            if (returnclz == long.class) {
                                mv.visitInsn(LRETURN);
                            } else if (returnclz == float.class) {
                                mv.visitInsn(FRETURN);
                            } else if (returnclz == double.class) {
                                mv.visitInsn(DRETURN);
                            } else {
                                mv.visitInsn(IRETURN);
                            }
                        } else {
                            mv.visitInsn(ARETURN);
                        }
                    }
                    if (methodBean != null && paramTypes.length > 0) {
                        Label l2 = new Label();
                        mv.visitLabel(l2);
                        // mv.visitLocalVariable("this", thisClassDesc, null, l0, l2, 0);
                        List<AsmMethodParam> params = methodBean.getParams();
                        for (int i = 0; i < paramTypes.length; i++) {
                            AsmMethodParam param = params.get(i);
                            mv.visitLocalVariable(
                                    param.getName(),
                                    param.description(paramTypes[i]),
                                    param.signature(paramTypes[i]),
                                    l0,
                                    l2,
                                    insns.get(i));
                        }
                    }
                    mv.visitMaxs(20, 20);
                    mv.visitEnd();
                }
            }
        } while ((clazz = clazz.getSuperclass()) != Object.class);
    }

    /**
     * 创建本地模式Service实例
     *
     * @param <T> Service泛型
     * @param classLoader ClassLoader
     * @param resourceName 资源名
     * @param serviceImplClass Service类
     * @param methodBoost 方法扩展
     * @param resourceFactory ResourceFactory
     * @param sncpRpcGroups SncpRpcGroups
     * @param client SncpClient
     * @param agent MessageAgent
     * @param remoteGroup 所有的组节点
     * @param conf 启动配置项
     * @return Service的本地模式实例
     */
    @SuppressWarnings("unchecked")
    public static <T extends Service> T createLocalService(
            final RedkaleClassLoader classLoader,
            final String resourceName,
            final Class<T> serviceImplClass,
            final AsmMethodBoost methodBoost,
            final ResourceFactory resourceFactory,
            final SncpRpcGroups sncpRpcGroups,
            final SncpClient client,
            final MessageAgent agent,
            final String remoteGroup,
            final AnyValue conf) {
        try {
            final Class newClazz = createLocalServiceClass(classLoader, resourceName, serviceImplClass, methodBoost);
            T service = (T) newClazz.getDeclaredConstructor().newInstance();
            // --------------------------------------
            Service remoteService = null;
            {
                Class loop = newClazz;
                do {
                    RedkaleClassLoader.putReflectionDeclaredFields(loop.getName());
                    for (Field field : loop.getDeclaredFields()) {
                        int mod = field.getModifiers();
                        if (Modifier.isFinal(mod) || Modifier.isStatic(mod)) {
                            continue;
                        }
                        if (field.getAnnotation(RpcRemote.class) == null) {
                            continue;
                        }
                        if (!field.getType().isAssignableFrom(newClazz)) {
                            continue;
                        }
                        field.setAccessible(true);
                        RedkaleClassLoader.putReflectionField(loop.getName(), field);
                        if (remoteService == null && sncpRpcGroups != null && client != null) {
                            remoteService = createRemoteService(
                                    classLoader,
                                    resourceName,
                                    serviceImplClass,
                                    methodBoost,
                                    resourceFactory,
                                    sncpRpcGroups,
                                    client,
                                    agent,
                                    remoteGroup,
                                    conf);
                        }
                        if (remoteService != null) {
                            field.set(service, remoteService);
                        }
                    }
                } while ((loop = loop.getSuperclass()) != Object.class);
            }
            {
                Field c = newClazz.getDeclaredField(FIELDPREFIX + "_conf");
                c.setAccessible(true);
                c.set(service, conf);
            }
            {
                Field c = newClazz.getDeclaredField(FIELDPREFIX + "_mq");
                c.setAccessible(true);
                c.set(service, agent == null ? null : agent.getName());
            }
            if (methodBoost != null) {
                // 必须用servcie的ClassLoader， 因为service是动态ClassLoader会与doMethod里的动态ClassLoader不一致
                methodBoost.doInstance(classLoader, resourceFactory, service);
            }
            return service;
        } catch (RuntimeException rex) {
            throw rex;
        } catch (Exception ex) {
            throw new SncpException(ex);
        }
    }

    public static <T extends Service> T createSimpleRemoteService(
            RedkaleClassLoader classLoader,
            Class<T> serviceImplClass,
            ResourceFactory resourceFactory,
            SncpRpcGroups sncpRpcGroups,
            SncpClient client,
            String group) {
        if (sncpRpcGroups == null) {
            throw new SncpException(SncpRpcGroups.class.getSimpleName() + " is null");
        }
        if (client == null) {
            throw new SncpException(SncpClient.class.getSimpleName() + " is null");
        }
        return createRemoteService(
                classLoader, "", serviceImplClass, null, resourceFactory, sncpRpcGroups, client, null, group, null);
    }

    /**
     *
     *
     * <blockquote>
     *
     * <pre>
     * &#64;Resource(resourceName = "")
     * &#64;SncpDyn(remote = true)
     * &#64;ResourceType(TestService.class)
     * public final class _DynRemoteTestService extends TestService {
     *
     *      private AnyValue _redkale_conf;
     *
     *      private SncpClient _redkale_client;
     *
     *      private SncpRemoteInfo _redkale_sncp;
     *
     *      &#64;Override
     *      public void createSomeThing(TestBean bean){
     *          _redkale_client.remote(_redkale_sncp, 0, bean);
     *      }
     *
     *      &#64;Override
     *      public String findSomeThing(){
     *          return (String)_redkale_client.remote(_redkale_sncp, 1);
     *      }
     *
     *      &#64;Override
     *      public String updateSomeThing(String id){
     *          return (String)_redkale_client.remote(_redkale_sncp, 2, id);
     *      }
     * }
     * </pre>
     *
     * </blockquote>
     *
     * 创建远程模式的Service实例
     *
     * @param <T> Service泛型
     * @param classLoader ClassLoader
     * @param resourceName 资源名
     * @param serviceTypeOrImplClass Service类
     * @param methodBoost 方法扩展
     * @param resourceFactory ResourceFactory
     * @param sncpRpcGroups SncpRpcGroups
     * @param client SncpClient
     * @param agent MessageAgent
     * @param remoteGroup 所有的组节点
     * @param conf 启动配置项
     * @return Service的远程模式实例
     */
    @SuppressWarnings("unchecked")
    public static <T extends Service> T createRemoteService(
            final RedkaleClassLoader classLoader,
            final String resourceName,
            final Class<T> serviceTypeOrImplClass,
            final AsmMethodBoost methodBoost,
            final ResourceFactory resourceFactory,
            final SncpRpcGroups sncpRpcGroups,
            final SncpClient client,
            final MessageAgent agent,
            final String remoteGroup,
            final AnyValue conf) {
        if (serviceTypeOrImplClass == null) {
            throw new SncpException("Service implement class is null");
        }
        if (!Service.class.isAssignableFrom(serviceTypeOrImplClass)) {
            throw new SncpException(serviceTypeOrImplClass + " is not Service type");
        }
        if ((sncpRpcGroups == null || client == null) && agent == null) {
            throw new SncpException(SncpRpcGroups.class.getSimpleName() + "/" + SncpClient.class.getSimpleName()
                    + " and " + MessageAgent.class.getSimpleName() + " are both null");
        }
        ResourceFactory.checkResourceName(resourceName);
        final int mod = serviceTypeOrImplClass.getModifiers();
        boolean realed = !(java.lang.reflect.Modifier.isAbstract(mod) || serviceTypeOrImplClass.isInterface());
        if (!java.lang.reflect.Modifier.isPublic(mod)) {
            return null;
        }
        final SncpRemoteInfo info = createSncpRemoteInfo(
                resourceName,
                getResourceType(serviceTypeOrImplClass),
                serviceTypeOrImplClass,
                ProtobufConvert.root(),
                sncpRpcGroups,
                client,
                agent,
                remoteGroup);
        final String supDynName = serviceTypeOrImplClass.getName().replace('.', '/');
        final String sncpInfoName = SncpRemoteInfo.class.getName().replace('.', '/');
        final String resDesc = Type.getDescriptor(Resource.class);
        final String sncpInfoDesc = Type.getDescriptor(SncpRemoteInfo.class);
        final String sncpDynDesc = Type.getDescriptor(SncpDyn.class);
        final String anyValueDesc = Type.getDescriptor(AnyValue.class);
        String newDynName = "org/redkaledyn/service/remote/_DynRemoteService__"
                + serviceTypeOrImplClass.getName().replace('.', '_').replace('$', '_');
        if (!resourceName.isEmpty()) {
            boolean normal = true;
            for (char ch : resourceName.toCharArray()) {
                if (!((ch >= '0' && ch <= '9') || ch == '_' || (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z'))) {
                    normal = false;
                }
            }
            if (!normal) {
                throw new SncpException(serviceTypeOrImplClass + "'s resource name is illegal, must be 0-9 _ a-z A-Z");
            }
            newDynName += "_" + (normal ? resourceName : hash(resourceName));
        }
        try {
            Class newClazz = classLoader.loadClass(newDynName.replace('/', '.'));
            T service = (T) newClazz.getDeclaredConstructor().newInstance();
            {
                Field c = newClazz.getDeclaredField(FIELDPREFIX + "_conf");
                c.setAccessible(true);
                c.set(service, conf);
            }
            {
                Field c = newClazz.getDeclaredField(FIELDPREFIX + "_mq");
                c.setAccessible(true);
                c.set(service, agent == null ? null : agent.getName());
            }
            {
                Field c = newClazz.getDeclaredField(FIELDPREFIX + "_sncp");
                c.setAccessible(true);
                c.set(service, info);
            }
            if (methodBoost != null) {
                // 必须用servcie的ClassLoader， 因为service是动态ClassLoader会与doMethod里的动态ClassLoader不一致
                methodBoost.doInstance(classLoader, resourceFactory, service);
            }
            return service;
        } catch (Throwable ex) {
            // do nothing
        }
        // ------------------------------------------------------------------------------
        ClassWriter cw = new ClassWriter(COMPUTE_FRAMES);
        FieldVisitor fv;
        MethodDebugVisitor mv;
        AnnotationVisitor av0;

        cw.visit(
                V11,
                ACC_PUBLIC + ACC_SUPER,
                newDynName,
                null,
                serviceTypeOrImplClass.isInterface() ? "java/lang/Object" : supDynName,
                serviceTypeOrImplClass.isInterface() ? new String[] {supDynName} : null);
        { // 给动态生成的Service类标记上Resource
            av0 = cw.visitAnnotation(resDesc, true);
            av0.visit("name", resourceName);
            av0.visitEnd();
        }
        {
            av0 = cw.visitAnnotation(Type.getDescriptor(ResourceType.class), true);
            ResourceType rty = serviceTypeOrImplClass.getAnnotation(ResourceType.class);
            org.redkale.util.ResourceType rty2 =
                    serviceTypeOrImplClass.getAnnotation(org.redkale.util.ResourceType.class);
            av0.visit(
                    "value",
                    Type.getType(Type.getDescriptor(
                            rty != null ? rty.value() : (rty2 != null ? rty2.value() : serviceTypeOrImplClass))));
            av0.visitEnd();
        }
        {
            av0 = cw.visitAnnotation(sncpDynDesc, true);
            av0.visit("remote", Boolean.TRUE);
            av0.visit("type", Type.getType(Type.getDescriptor(serviceTypeOrImplClass)));
            av0.visitEnd();
        }
        { // 给新类加上原有的Annotation
            for (Annotation ann : serviceTypeOrImplClass.getAnnotations()) {
                if (ann instanceof Resource || ann instanceof SncpDyn || ann instanceof ResourceType) {
                    continue;
                }
                Asms.visitAnnotation(
                        cw.visitAnnotation(Type.getDescriptor(ann.annotationType()), true), ann.annotationType(), ann);
            }
        }
        {
            fv = cw.visitField(ACC_PRIVATE, FIELDPREFIX + "_conf", anyValueDesc, null, null);
            fv.visitEnd();
        }
        {
            fv = cw.visitField(ACC_PRIVATE, FIELDPREFIX + "_mq", Type.getDescriptor(String.class), null, null);
            fv.visitEnd();
        }
        {
            fv = cw.visitField(ACC_PRIVATE, FIELDPREFIX + "_sncp", sncpInfoDesc, null, null);
            fv.visitEnd();
        }
        { // init
            mv = new MethodDebugVisitor(cw.visitMethod(ACC_PUBLIC, "init", "(" + anyValueDesc + ")V", null, null));
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 2);
            mv.visitEnd();
        }
        { // destroy
            mv = new MethodDebugVisitor(cw.visitMethod(ACC_PUBLIC, "destroy", "(" + anyValueDesc + ")V", null, null));
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 2);
            mv.visitEnd();
        }
        //        { // toString()
        //            mv = new MethodDebugVisitor(cw.visitMethod(ACC_PUBLIC, "toString", "()Ljava/lang/String;", null,
        // null));
        //            mv.visitVarInsn(ALOAD, 0);
        //            mv.visitFieldInsn(GETFIELD, newDynName, FIELDPREFIX + "_sncp", sncpInfoDesc);
        //            Label l1 = new Label();
        //            mv.visitJumpInsn(IFNONNULL, l1);
        //            mv.visitVarInsn(ALOAD, 0);
        //            mv.visitCheckCast(INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false);
        //            mv.visitCheckCast(INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;", false);
        //            Label l2 = new Label();
        //            mv.visitJumpInsn(GOTO, l2);
        //            mv.visitLabel(l1);
        //            mv.visitVarInsn(ALOAD, 0);
        //            mv.visitFieldInsn(GETFIELD, newDynName, FIELDPREFIX + "_sncp", sncpInfoDesc);
        //            mv.visitCheckCast(INVOKEVIRTUAL, sncpInfoName, "toSimpleString", "()Ljava/lang/String;", false);
        //            mv.visitLabel(l2);
        //            mv.visitInsn(ARETURN);
        //            mv.visitMaxs(1, 1);
        //            mv.visitEnd();
        //        }
        Set<String> methodKeys = new HashSet<>();
        Map<String, AsmMethodBean> methodBeans = AsmMethodBoost.getMethodBeans(serviceTypeOrImplClass);
        for (final SncpRemoteAction entry : info.getActions()) {
            final java.lang.reflect.Method method = entry.method;
            String mk = Utility.methodKey(method);
            if (methodKeys.contains(mk)) {
                // 跳过已处理的继承方法
                continue;
            }
            methodKeys.add(mk);

            int acc = ACC_PUBLIC;
            AsmNewMethod newMethod = null;
            String newMethodName = null;
            if (methodBoost != null) {
                List<Class<? extends Annotation>> filterAnns = methodBoost.filterMethodAnnotations(method);
                newMethod = methodBoost.doMethod(
                        classLoader, cw, serviceTypeOrImplClass, newDynName, FIELDPREFIX, filterAnns, method, null);
            }
            if (newMethod != null) {
                acc = newMethod.getMethodAccs();
                newMethodName = newMethod.getMethodName();
            } else {
                newMethodName = method.getName();
            }
            mv = new MethodDebugVisitor(
                    cw.visitMethod(acc, newMethodName, Type.getMethodDescriptor(method), null, null));
            Label l0 = new Label();
            mv.visitLabel(l0);
            // mv.setDebug(true);
            { // 给参数加上 Annotation
                final Annotation[][] anns = method.getParameterAnnotations();
                for (int k = 0; k < anns.length; k++) {
                    for (Annotation ann : anns[k]) {
                        Asms.visitAnnotation(
                                mv.visitParameterAnnotation(k, Type.getDescriptor(ann.annotationType()), true),
                                ann.annotationType(),
                                ann);
                    }
                }
            }
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, newDynName, FIELDPREFIX + "_sncp", sncpInfoDesc);

            mv.visitLdcInsn(entry.actionid.toString());

            AsmMethodBean methodBean = AsmMethodBean.get(methodBeans, method);
            List<Integer> insns = new ArrayList<>();
            java.lang.reflect.Type[] paramTypes = entry.paramTypes;
            { // 传参数
                int paramlen = entry.paramTypes.length;
                Asms.visitInsn(mv, paramlen);
                mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
                int insn = 0;
                for (int j = 0; j < paramTypes.length; j++) {
                    final java.lang.reflect.Type pt = paramTypes[j];
                    mv.visitInsn(DUP);
                    insn++;
                    Asms.visitInsn(mv, j);
                    if (pt instanceof Class && ((Class) pt).isPrimitive()) {
                        if (pt == long.class) {
                            mv.visitVarInsn(LLOAD, insn++);
                        } else if (pt == float.class) {
                            mv.visitVarInsn(FLOAD, insn++);
                        } else if (pt == double.class) {
                            mv.visitVarInsn(DLOAD, insn++);
                        } else {
                            mv.visitVarInsn(ILOAD, insn);
                        }
                        Class bigclaz = TypeToken.primitiveToWrapper((Class) pt);
                        mv.visitMethodInsn(
                                INVOKESTATIC,
                                bigclaz.getName().replace('.', '/'),
                                "valueOf",
                                "(" + Type.getDescriptor((Class) pt) + ")" + Type.getDescriptor(bigclaz),
                                false);
                    } else {
                        mv.visitVarInsn(ALOAD, insn);
                    }
                    mv.visitInsn(AASTORE);
                    insns.add(insn);
                }
            }

            mv.visitMethodInsn(
                    INVOKEVIRTUAL,
                    sncpInfoName,
                    "remote",
                    "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;",
                    false);
            // mv.visitCheckCast(INVOKEVIRTUAL, convertName, "convertFrom", convertFromDesc, false);
            if (method.getGenericReturnType() == void.class) {
                mv.visitInsn(POP);
                mv.visitInsn(RETURN);
            } else {
                Class returnclz = method.getReturnType();
                Class bigPrimitiveClass = returnclz.isPrimitive() ? TypeToken.primitiveToWrapper(returnclz) : returnclz;
                mv.visitTypeInsn(
                        CHECKCAST,
                        (returnclz.isPrimitive() ? bigPrimitiveClass : returnclz)
                                .getName()
                                .replace('.', '/'));
                if (returnclz.isPrimitive()) {
                    String bigPrimitiveName = bigPrimitiveClass.getName().replace('.', '/');
                    try {
                        java.lang.reflect.Method pm = bigPrimitiveClass.getMethod(returnclz.getSimpleName() + "Value");
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL, bigPrimitiveName, pm.getName(), Type.getMethodDescriptor(pm), false);
                    } catch (Exception ex) {
                        throw new SncpException(ex); // 不可能会发生
                    }
                    if (returnclz == long.class) {
                        mv.visitInsn(LRETURN);
                    } else if (returnclz == float.class) {
                        mv.visitInsn(FRETURN);
                    } else if (returnclz == double.class) {
                        mv.visitInsn(DRETURN);
                    } else {
                        mv.visitInsn(IRETURN);
                    }
                } else {
                    mv.visitInsn(ARETURN);
                }
            }
            if (methodBean != null && paramTypes.length > 0) {
                Label l2 = new Label();
                mv.visitLabel(l2);
                // mv.visitLocalVariable("this", thisClassDesc, null, l0, l2, 0);
                List<AsmMethodParam> params = methodBean.getParams();
                for (int i = 0; i < paramTypes.length; i++) {
                    AsmMethodParam param = params.get(i);
                    mv.visitLocalVariable(
                            param.getName(),
                            param.description(paramTypes[i]),
                            param.signature(paramTypes[i]),
                            l0,
                            l2,
                            insns.get(i));
                }
            }
            mv.visitMaxs(20, 20);
            mv.visitEnd();
        }
        if (methodBoost != null) {
            createNewMethods(classLoader, serviceTypeOrImplClass, methodBoost, methodKeys, cw, newDynName, supDynName);
            methodBoost.doAfterMethods(classLoader, cw, newDynName, FIELDPREFIX);
        }
        { // 构造函数
            mv = new MethodDebugVisitor(cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null));
            // mv.setDebug(true);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(
                    INVOKESPECIAL,
                    serviceTypeOrImplClass.isInterface() ? "java/lang/Object" : supDynName,
                    "<init>",
                    "()V",
                    false);
            if (methodBoost != null) {
                methodBoost.doConstructorMethod(classLoader, cw, mv, newDynName, FIELDPREFIX, true);
            }
            mv.visitInsn(RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        cw.visitEnd();
        byte[] bytes = cw.toByteArray();
        final String newDynClass = newDynName.replace('/', '.');
        Class<?> newClazz = classLoader.loadClass(newDynClass, bytes);
        RedkaleClassLoader.putReflectionPublicConstructors(newClazz, newDynClass);
        RedkaleClassLoader.putReflectionDeclaredConstructors(newClazz, newDynClass);
        try {
            T service = (T) newClazz.getDeclaredConstructor().newInstance();
            {
                Field c = newClazz.getDeclaredField(FIELDPREFIX + "_conf");
                c.setAccessible(true);
                c.set(service, conf);
                RedkaleClassLoader.putReflectionField(newDynClass, c);
            }
            {
                Field c = newClazz.getDeclaredField(FIELDPREFIX + "_mq");
                c.setAccessible(true);
                c.set(service, agent == null ? null : agent.getName());
                RedkaleClassLoader.putReflectionField(newDynClass, c);
            }
            {
                Field c = newClazz.getDeclaredField(FIELDPREFIX + "_sncp");
                c.setAccessible(true);
                c.set(service, info);
                RedkaleClassLoader.putReflectionField(newDynClass, c);
            }
            if (methodBoost != null) {
                // 必须用servcie的ClassLoader， 因为service是动态ClassLoader会与doMethod里的动态ClassLoader不一致
                methodBoost.doInstance(classLoader, resourceFactory, service);
            }
            return service;
        } catch (Exception ex) {
            throw new SncpException(ex);
        }
    }
}
