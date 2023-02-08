/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.sncp;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.net.InetSocketAddress;
import java.nio.channels.CompletionHandler;
import java.util.*;
import org.redkale.annotation.*;
import org.redkale.annotation.ResourceType;
import static org.redkale.asm.ClassWriter.COMPUTE_FRAMES;
import org.redkale.asm.*;
import static org.redkale.asm.Opcodes.*;
import org.redkale.asm.Type;
import org.redkale.convert.Convert;
import org.redkale.mq.*;
import org.redkale.net.TransportFactory;
import org.redkale.net.http.WebSocketNode;
import org.redkale.net.sncp.SncpServiceInfo.SncpServiceAction;
import org.redkale.service.*;
import org.redkale.util.*;

/**
 * Service Node Communicate Protocol
 * 生成Service的本地模式或远程模式Service-Class的工具类
 *
 *
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public abstract class Sncp {

    private static final byte[] PING_BYTES = new SncpHeader(null, Uint128.ZERO, Uint128.ZERO).writeTo(new ByteArray(SncpHeader.HEADER_SIZE), null, 0, 0, 0).getBytes();

    private static final byte[] PONG_BYTES = Arrays.copyOf(PING_BYTES, PING_BYTES.length);

    static final String FIELDPREFIX = "_redkale";

    /**
     * 修饰由SNCP协议动态生成的class、和method
     * 本地模式：动态生成的_DynLocalXXXXService类会打上&#64;SncpDyn(remote = false) 的注解
     * 远程模式：动态生成的_DynRemoteXXXService类会打上&#64;SncpDyn(remote = true) 的注解
     *
     * <p>
     * 详情见: https://redkale.org
     *
     * @author zhangjx
     */
    @Inherited
    @Documented
    @Target({METHOD, TYPE})
    @Retention(RUNTIME)
    public static @interface SncpDyn {

        boolean remote();

        Class type(); //resourceServiceType

        int index() default 0;  //排列顺序， 主要用于Method
    }

    private Sncp() {
    }

    public static byte[] getPingBytes() {
        return Arrays.copyOf(PING_BYTES, PING_BYTES.length);
    }

    public static byte[] getPongBytes() {
        return Arrays.copyOf(PONG_BYTES, PONG_BYTES.length);
    }

    //key: actionid
    public static LinkedHashMap<Uint128, Method> loadMethodActions(final Class resourceServiceType) {
        final List<Method> list = new ArrayList<>();
        final List<Method> multis = new ArrayList<>();
        final Map<Uint128, Method> actionids = new LinkedHashMap<>();
        for (final java.lang.reflect.Method method : resourceServiceType.getMethods()) {
            if (method.isSynthetic()) {
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
            if (method.getName().equals("getClass") || method.getName().equals("toString")) {
                continue;
            }
            if (method.getName().equals("equals") || method.getName().equals("hashCode")) {
                continue;
            }
            if (method.getName().equals("notify") || method.getName().equals("notifyAll") || method.getName().equals("wait")) {
                continue;
            }
            if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == AnyValue.class) {
                if (method.getName().equals("init") || method.getName().equals("stop") || method.getName().equals("destroy")) {
                    continue;
                }
            }

            Uint128 actionid = Sncp.actionid(method);
            Method old = actionids.get(actionid);
            if (old != null) {
                if (old.getDeclaringClass().equals(method.getDeclaringClass())) {
                    throw new SncpException(resourceServiceType.getName() + " have one more same action(Method=" + method + ", " + old + ", actionid=" + actionid + ")");
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
        multis.sort((m1, m2) -> m1.getAnnotation(Sncp.SncpDyn.class).index() - m2.getAnnotation(Sncp.SncpDyn.class).index());
        list.sort((Method o1, Method o2) -> {
            if (!o1.getName().equals(o2.getName())) {
                return o1.getName().compareTo(o2.getName());
            }
            if (o1.getParameterCount() != o2.getParameterCount()) {
                return o1.getParameterCount() - o2.getParameterCount();
            }
            return 0;
        });
        //带SncpDyn必须排在前面
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

    public static <T extends Service> SncpServiceInfo createSncpServiceInfo(String resourceName,
        Class<T> resourceServiceType, T service, Convert convert, MessageAgent messageAgent, SncpMessageClient messageClient) {
        return new SncpServiceInfo(resourceName, resourceServiceType, service, convert, messageAgent, messageClient);
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
        StringBuilder sb = new StringBuilder(); //不能使用method.toString() 因为包含declaringClass信息导致接口与实现类的方法hash不一致
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

    public static Uint128 serviceid(String serviceResourceName, Class serviceResourceType) {
        return hash(serviceResourceType.getName() + ':' + serviceResourceName);
    }

    /**
     * 对类名或者name字符串进行hash。
     *
     * @param name String
     *
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

    public static int getVersion(Service service) {
        return -1; //暂不实现Version
    }

    public static String getResourceName(Service service) {
        Resource res = service.getClass().getAnnotation(Resource.class);
        return res != null ? res.name() : null;
    }

    public static Class getResourceType(Service service) {
        ResourceType type = service.getClass().getAnnotation(ResourceType.class);
        return type != null ? type.value() : service.getClass();
    }

    public static <T extends Service> Class getResourceType(Class<T> serviceImplClass) {
        ResourceType type = serviceImplClass.getAnnotation(ResourceType.class);
        return type != null ? type.value() : serviceImplClass;
    }
//
//    public static Class getServiceType(Service service) {
//        return isSncpDyn(service) && service.getClass().getSimpleName().startsWith("_Dyn") ? service.getClass().getSuperclass() : service.getClass();
//    }

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

    public static OldSncpClient getSncpOldClient(Service service) {
        if (service == null || !isSncpDyn(service)) {
            return null;
        }
        try {
            Field ts = service.getClass().getDeclaredField(FIELDPREFIX + "_client");
            ts.setAccessible(true);
            return (OldSncpClient) ts.get(service);
        } catch (Exception e) {
            throw new SncpException(service + " not found " + FIELDPREFIX + "_client");
        }
    }

    public static MessageAgent getMessageAgent(Service service) {
        if (service == null || !isSncpDyn(service)) {
            return null;
        }
        try {
            Field ts = service.getClass().getDeclaredField(FIELDPREFIX + "_messageagent");
            ts.setAccessible(true);
            return (MessageAgent) ts.get(service);
        } catch (Exception e) {
            throw new SncpException(service + " not found " + FIELDPREFIX + "_messageagent");
        }
    }

    public static void setMessageAgent(Service service, MessageAgent messageAgent) {
        if (service == null || !isSncpDyn(service)) {
            return;
        }
        try {
            Field ts = service.getClass().getDeclaredField(FIELDPREFIX + "_messageagent");
            ts.setAccessible(true);
            ts.set(service, messageAgent);
            if (service instanceof WebSocketNode) {
                Field c = WebSocketNode.class.getDeclaredField("messageAgent");
                c.setAccessible(true);
                c.set(service, messageAgent);
            }
        } catch (Exception e) {
            throw new SncpException(service + " not found " + FIELDPREFIX + "_messageagent");
        }
    }

    public static boolean updateTransport(Service service,
        final TransportFactory transportFactory, String name, String protocol, InetSocketAddress clientAddress,
        final Set<String> groups, final Collection<InetSocketAddress> addresses) {
        if (!isSncpDyn(service)) {
            return false;
        }
        OldSncpClient client = getSncpOldClient(service);
        client.setRemoteGroups(groups);
        if (client.getRemoteGroupTransport() != null) {
            client.getRemoteGroupTransport().updateRemoteAddresses(addresses);
        } else {
            client.setRemoteGroupTransport(transportFactory.createTransport(name, protocol, clientAddress, addresses));
        }
        return true;
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
            } else if (m.getName().equals("failed") && m.getParameterCount() == 2 && Modifier.isFinal(m.getModifiers())) {
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

    //获取一个clazz内所有未被实现的方法
    public static List<Method> loadNotImplMethods(Class clazz) {
        LinkedHashSet<Class> types = new LinkedHashSet<>();
        loadAllSubClasses(clazz, types);
        List<Method> methods = new ArrayList<>();
        Set<String> ms = new HashSet<>();
        for (Class c : types) {
            for (Method m : c.getDeclaredMethods()) {
                if (c.isInterface() || Modifier.isAbstract(m.getModifiers())) {
                    StringBuilder sb = new StringBuilder(); //不能使用method.toString() 因为包含declaringClass信息导致接口与实现类的方法hash不一致
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
     * <blockquote><pre>
     * public class TestService implements Service{
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
     * </pre></blockquote>
     *
     * <blockquote><pre>
     * &#64;Resource(name = "")
     * &#64;SncpDyn(remote = false)
     * &#64;ResourceType(TestService.class)
     * public final class _DynLocalTestService extends TestService{
     *
     * private AnyValue _redkale_conf;
     *
     * private OldSncpClient _redkale_client;
     *
     * &#64;Override
     *      public String toString() {
     *          return _redkale_selfstring == null ? super.toString() : _redkale_selfstring;
     *      }
     * }
     * </pre></blockquote>
     *
     * 创建Service的本地模式Class
     *
     * @param <T>              Service子类
     * @param classLoader      ClassLoader
     * @param name             资源名
     * @param serviceImplClass Service类
     *
     * @return Service实例
     */
    @SuppressWarnings("unchecked")
    protected static <T extends Service> Class<? extends T> createLocalServiceClass(ClassLoader classLoader, final String name, final Class<T> serviceImplClass) {
        Objects.requireNonNull(serviceImplClass);
        if (!Service.class.isAssignableFrom(serviceImplClass)) {
            throw new SncpException(serviceImplClass + " is not Service type");
        }
        ResourceFactory.checkResourceName(name);
        int mod = serviceImplClass.getModifiers();
        if (!java.lang.reflect.Modifier.isPublic(mod)) {
            throw new SncpException(serviceImplClass + " is not public");
        }
        if (java.lang.reflect.Modifier.isAbstract(mod)) {
            throw new SncpException(serviceImplClass + " is abstract");
        }
        final String supDynName = serviceImplClass.getName().replace('.', '/');
        final String clientName = OldSncpClient.class.getName().replace('.', '/');
        final String resDesc = Type.getDescriptor(Resource.class);
        final String clientDesc = Type.getDescriptor(OldSncpClient.class);
        final String anyValueDesc = Type.getDescriptor(AnyValue.class);
        final String sncpDynDesc = Type.getDescriptor(SncpDyn.class);
        ClassLoader loader = classLoader == null ? Thread.currentThread().getContextClassLoader() : classLoader;
        //String newDynName = supDynName.substring(0, supDynName.lastIndexOf('/') + 1) + LOCALPREFIX + serviceImplClass.getSimpleName();
        String newDynName = "org/redkaledyn/service/local/_DynLocalService__" + serviceImplClass.getName().replace('.', '_').replace('$', '_');
        if (!name.isEmpty()) {
            boolean normal = true;
            for (char ch : name.toCharArray()) {
                if (!((ch >= '0' && ch <= '9') || ch == '_' || (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z'))) {
                    normal = false;
                }
            }
            if (!normal) {
                throw new SncpException(serviceImplClass + "'s resource name is illegal, must be 0-9 _ a-z A-Z");
            }
            newDynName += "_" + (normal ? name : hash(name));
        }
        try {
            Class clz = RedkaleClassLoader.findDynClass(newDynName.replace('/', '.'));
            return (Class<T>) (clz == null ? loader.loadClass(newDynName.replace('/', '.')) : clz);
        } catch (ClassNotFoundException e) {
        } catch (Throwable t) {
            t.printStackTrace();
        }
        //------------------------------------------------------------------------------
        ClassWriter cw = new ClassWriter(COMPUTE_FRAMES);
        FieldVisitor fv;
        MethodDebugVisitor mv;
        AnnotationVisitor av0;

        cw.visit(V11, ACC_PUBLIC + ACC_FINAL + ACC_SUPER, newDynName, null, supDynName, null);
        { //给动态生成的Service类标记上Resource
            av0 = cw.visitAnnotation(resDesc, true);
            av0.visit("name", name);
            av0.visitEnd();
        }
        {
            av0 = cw.visitAnnotation(sncpDynDesc, true);
            av0.visit("remote", Boolean.FALSE);
            av0.visit("type", Type.getType(Type.getDescriptor(getResourceType(serviceImplClass))));
            av0.visitEnd();
        }
        { //给新类加上原有的Annotation
            for (Annotation ann : serviceImplClass.getAnnotations()) {
                if (ann instanceof Resource || ann instanceof SncpDyn || ann instanceof ResourceType) {
                    continue;
                }
                MethodDebugVisitor.visitAnnotation(cw.visitAnnotation(Type.getDescriptor(ann.annotationType()), true), ann);
            }
        }
        {
            av0 = cw.visitAnnotation(Type.getDescriptor(ResourceType.class), true);
            ResourceType rty = serviceImplClass.getAnnotation(ResourceType.class);
            org.redkale.util.ResourceType rty2 = serviceImplClass.getAnnotation(org.redkale.util.ResourceType.class);
            av0.visit("value", Type.getType(Type.getDescriptor(rty != null ? rty.value() : (rty2 != null ? rty2.value() : serviceImplClass))));
            av0.visitEnd();
        }
        {
            fv = cw.visitField(ACC_PRIVATE, FIELDPREFIX + "_conf", anyValueDesc, null, null);
            fv.visitEnd();
        }
        {
            fv = cw.visitField(ACC_PRIVATE, FIELDPREFIX + "_client", clientDesc, null, null);
            fv.visitEnd();
        }
        {
            fv = cw.visitField(ACC_PRIVATE, FIELDPREFIX + "_messageagent", Type.getDescriptor(MessageAgent.class), null, null);
            fv.visitEnd();
        }
        { //构造函数
            mv = new MethodDebugVisitor(cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null));
            //mv.setDebug(true);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, supDynName, "<init>", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        { // toString()
            mv = new MethodDebugVisitor(cw.visitMethod(ACC_PUBLIC, "toString", "()Ljava/lang/String;", null, null));
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, newDynName, FIELDPREFIX + "_client", clientDesc);
            Label l1 = new Label();
            mv.visitJumpInsn(IFNONNULL, l1);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;", false);
            Label l2 = new Label();
            mv.visitJumpInsn(GOTO, l2);
            mv.visitLabel(l1);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, newDynName, FIELDPREFIX + "_client", clientDesc);
            mv.visitMethodInsn(INVOKEVIRTUAL, clientName, "toSimpleString", "()Ljava/lang/String;", false);
            mv.visitLabel(l2);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        cw.visitEnd();
        byte[] bytes = cw.toByteArray();
        Class<?> newClazz = new ClassLoader(loader) {
            public final Class<?> loadClass(String name, byte[] b) {
                return defineClass(name, b, 0, b.length);
            }
        }.loadClass(newDynName.replace('/', '.'), bytes);
        RedkaleClassLoader.putDynClass(newDynName.replace('/', '.'), bytes, newClazz);
        RedkaleClassLoader.putReflectionPublicClasses(newDynName.replace('/', '.'));
        RedkaleClassLoader.putReflectionDeclaredConstructors(newClazz, newDynName.replace('/', '.'));
        try {
            Field c = newClazz.getDeclaredField(FIELDPREFIX + "_conf");
            RedkaleClassLoader.putReflectionField(newDynName.replace('/', '.'), c);
            c = newClazz.getDeclaredField(FIELDPREFIX + "_client");
            RedkaleClassLoader.putReflectionField(newDynName.replace('/', '.'), c);
            c = newClazz.getDeclaredField(FIELDPREFIX + "_messageagent");
            RedkaleClassLoader.putReflectionField(newDynName.replace('/', '.'), c);
        } catch (Exception e) {
        }
        return (Class<T>) newClazz;
    }

    public static <T extends Service> T createSimpleLocalService(final Class<T> serviceImplClass, final MessageAgent messageAgent,
        final ResourceFactory resourceFactory, final TransportFactory transportFactory, final InetSocketAddress clientSncpAddress, final String... groups) {
        return createLocalService(null, "", serviceImplClass, messageAgent, resourceFactory, transportFactory, clientSncpAddress, Utility.ofSet(groups), null);
    }

    /**
     *
     * 创建本地模式Service实例
     *
     * @param <T>               Service泛型
     * @param classLoader       ClassLoader
     * @param name              资源名
     * @param serviceImplClass  Service类
     * @param messageAgent      MQ管理器
     * @param resourceFactory   ResourceFactory
     * @param transportFactory  TransportFactory
     * @param clientSncpAddress 本地IP地址
     * @param groups            所有的组节点，包含自身
     * @param conf              启动配置项
     *
     * @return Service的本地模式实例
     */
    @SuppressWarnings("unchecked")
    public static <T extends Service> T createLocalService(
        final RedkaleClassLoader classLoader,
        final String name,
        final Class<T> serviceImplClass,
        final MessageAgent messageAgent,
        final ResourceFactory resourceFactory,
        final TransportFactory transportFactory,
        final InetSocketAddress clientSncpAddress,
        final Set<String> groups,
        final AnyValue conf) {
        try {
            final Class newClazz = createLocalServiceClass(classLoader, name, serviceImplClass);
            T service = (T) newClazz.getDeclaredConstructor().newInstance();
            //--------------------------------------            
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
                        if (remoteService == null && clientSncpAddress != null) {
                            remoteService = createRemoteService(classLoader, name, serviceImplClass, messageAgent, transportFactory, clientSncpAddress, groups, conf);
                        }
                        if (remoteService != null) {
                            field.set(service, remoteService);
                        }
                    }
                } while ((loop = loop.getSuperclass()) != Object.class);
            }
            OldSncpClient client = null;
            {
                try {
                    Field c = newClazz.getDeclaredField(FIELDPREFIX + "_client");
                    c.setAccessible(true);
                    client = new OldSncpClient(name, serviceImplClass, service, messageAgent, transportFactory, false, newClazz, clientSncpAddress);
                    c.set(service, client);
                    if (transportFactory != null) {
                        transportFactory.addSncpService(service);
                    }
                } catch (NoSuchFieldException ne) {
                    ne.printStackTrace();
                }
            }
            if (messageAgent != null) {
                Field c = newClazz.getDeclaredField(FIELDPREFIX + "_messageagent");
                c.setAccessible(true);
                c.set(service, messageAgent);
            }
            if (client == null) {
                return service;
            }
            {
                Field c = newClazz.getDeclaredField(FIELDPREFIX + "_conf");
                c.setAccessible(true);
                c.set(service, conf);
                if (service instanceof WebSocketNode) {
                    c = WebSocketNode.class.getDeclaredField("messageAgent");
                    c.setAccessible(true);
                    c.set(service, messageAgent);
                }
            }
            return service;
        } catch (RuntimeException rex) {
            throw rex;
        } catch (Exception ex) {
            throw new SncpException(ex);
        }
    }

    public static <T extends Service> T createSimpleRemoteService(final Class<T> serviceImplClass, final MessageAgent messageAgent,
        final TransportFactory transportFactory, final InetSocketAddress clientSncpAddress, final String... groups) {
        return createRemoteService(null, "", serviceImplClass, messageAgent, transportFactory, clientSncpAddress, Utility.ofSet(groups), null);
    }

    /**
     * <blockquote><pre>
     * &#64;Resource(name = "")
     * &#64;SncpDyn(remote = true)
     * &#64;ResourceType(TestService.class)
     * public final class _DynRemoteTestService extends TestService{
     *
     * private AnyValue _redkale_conf;
     *
     * private OldSncpClient _redkale_client;
     *
     * &#64;Override
     *      public void createSomeThing(TestBean bean){
     *          _redkale_client.remote(0, bean);
     *      }
     *
     *      &#64;Override
     *      public String findSomeThing(){
     *          return _redkale_client.remote(1);
     *      }
     *
     *      &#64;Override
     *      public String updateSomeThing(String id){
     *          return  _redkale_client.remote(2, id);
     *      }
     * }
     * </pre></blockquote>
     *
     * 创建远程模式的Service实例
     *
     * @param <T>                    Service泛型
     * @param classLoader            ClassLoader
     * @param name                   资源名
     * @param serviceTypeOrImplClass Service类
     * @param messageAgent           MQ管理器
     * @param transportFactory       TransportFactory
     * @param clientAddress          本地IP地址
     * @param groups0                所有的组节点，包含自身
     * @param conf                   启动配置项
     *
     * @return Service的远程模式实例
     */
    @SuppressWarnings("unchecked")

    public static <T extends Service> T createRemoteService(
        final ClassLoader classLoader,
        final String name,
        final Class<T> serviceTypeOrImplClass,
        final MessageAgent messageAgent,
        final TransportFactory transportFactory,
        final InetSocketAddress clientAddress,
        final Set<String> groups0,
        final AnyValue conf) {
        if (serviceTypeOrImplClass == null) {
            return null;
        }
        if (!Service.class.isAssignableFrom(serviceTypeOrImplClass)) {
            return null;
        }
        final Set<String> groups = groups0 == null ? new HashSet<>() : groups0;
        ResourceFactory.checkResourceName(name);
        final int mod = serviceTypeOrImplClass.getModifiers();
        boolean realed = !(java.lang.reflect.Modifier.isAbstract(mod) || serviceTypeOrImplClass.isInterface());
        if (!java.lang.reflect.Modifier.isPublic(mod)) {
            return null;
        }
        final String supDynName = serviceTypeOrImplClass.getName().replace('.', '/');
        final String clientName = OldSncpClient.class.getName().replace('.', '/');
        final String resDesc = Type.getDescriptor(Resource.class);
        final String clientDesc = Type.getDescriptor(OldSncpClient.class);
        final String sncpDynDesc = Type.getDescriptor(SncpDyn.class);
        final String anyValueDesc = Type.getDescriptor(AnyValue.class);
        final ClassLoader loader = classLoader == null ? Thread.currentThread().getContextClassLoader() : classLoader;
        //final String newDynName = supDynName.substring(0, supDynName.lastIndexOf('/') + 1) + REMOTEPREFIX + serviceTypeOrImplClass.getSimpleName();
        final String newDynName = "org/redkaledyn/service/remote/_DynRemoteService__" + serviceTypeOrImplClass.getName().replace('.', '_').replace('$', '_');
        try {
            Class clz = RedkaleClassLoader.findDynClass(newDynName.replace('/', '.'));
            Class newClazz = clz == null ? loader.loadClass(newDynName.replace('/', '.')) : clz;
            T service = (T) newClazz.getDeclaredConstructor().newInstance();
            OldSncpClient client = new OldSncpClient(name, serviceTypeOrImplClass, service, messageAgent, transportFactory, true, realed ? createLocalServiceClass(loader, name, serviceTypeOrImplClass) : serviceTypeOrImplClass, clientAddress);
            client.setRemoteGroups(groups);
            if (transportFactory != null) {
                client.setRemoteGroupTransport(transportFactory.loadTransport(clientAddress, groups));
            }
            {
                Field c = newClazz.getDeclaredField(FIELDPREFIX + "_client");
                c.setAccessible(true);
                c.set(service, client);
            }
            if (messageAgent != null) {
                Field c = newClazz.getDeclaredField(FIELDPREFIX + "_messageagent");
                c.setAccessible(true);
                c.set(service, messageAgent);
                if (service instanceof WebSocketNode) {
                    c = WebSocketNode.class.getDeclaredField("messageAgent");
                    c.setAccessible(true);
                    c.set(service, messageAgent);
                }
            }
            {
                Field c = newClazz.getDeclaredField(FIELDPREFIX + "_conf");
                c.setAccessible(true);
                c.set(service, conf);
            }
            if (transportFactory != null) {
                transportFactory.addSncpService(service);
            }
            return service;
        } catch (Throwable ex) {
        }
        //------------------------------------------------------------------------------
        ClassWriter cw = new ClassWriter(COMPUTE_FRAMES);
        FieldVisitor fv;
        MethodDebugVisitor mv;
        AnnotationVisitor av0;

        cw.visit(V11, ACC_PUBLIC + ACC_FINAL + ACC_SUPER, newDynName, null, serviceTypeOrImplClass.isInterface() ? "java/lang/Object" : supDynName, serviceTypeOrImplClass.isInterface() ? new String[]{supDynName} : null);
        { //给动态生成的Service类标记上Resource
            av0 = cw.visitAnnotation(resDesc, true);
            av0.visit("name", name);
            av0.visitEnd();
        }
        {
            av0 = cw.visitAnnotation(Type.getDescriptor(ResourceType.class), true);
            ResourceType rty = serviceTypeOrImplClass.getAnnotation(ResourceType.class);
            org.redkale.util.ResourceType rty2 = serviceTypeOrImplClass.getAnnotation(org.redkale.util.ResourceType.class);
            av0.visit("value", Type.getType(Type.getDescriptor(rty != null ? rty.value() : (rty2 != null ? rty2.value() : serviceTypeOrImplClass))));
            av0.visitEnd();
        }
        {
            av0 = cw.visitAnnotation(sncpDynDesc, true);
            av0.visit("remote", Boolean.TRUE);
            av0.visit("type", Type.getType(Type.getDescriptor(serviceTypeOrImplClass)));
            av0.visitEnd();
        }
        { //给新类加上原有的Annotation
            for (Annotation ann : serviceTypeOrImplClass.getAnnotations()) {
                if (ann instanceof Resource || ann instanceof SncpDyn || ann instanceof ResourceType) {
                    continue;
                }
                MethodDebugVisitor.visitAnnotation(cw.visitAnnotation(Type.getDescriptor(ann.annotationType()), true), ann);
            }
        }
        {
            fv = cw.visitField(ACC_PRIVATE, FIELDPREFIX + "_conf", anyValueDesc, null, null);
            fv.visitEnd();
        }
        {
            fv = cw.visitField(ACC_PRIVATE, FIELDPREFIX + "_client", clientDesc, null, null);
            fv.visitEnd();
        }
        {
            fv = cw.visitField(ACC_PRIVATE, FIELDPREFIX + "_messageagent", Type.getDescriptor(MessageAgent.class), null, null);
            fv.visitEnd();
        }
        { //构造函数
            mv = new MethodDebugVisitor(cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null));
            //mv.setDebug(true);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, serviceTypeOrImplClass.isInterface() ? "java/lang/Object" : supDynName, "<init>", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        { //init
            mv = new MethodDebugVisitor(cw.visitMethod(ACC_PUBLIC, "init", "(" + anyValueDesc + ")V", null, null));
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 2);
            mv.visitEnd();
        }
        { //stop
            mv = new MethodDebugVisitor(cw.visitMethod(ACC_PUBLIC, "stop", "(" + anyValueDesc + ")V", null, null));
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 2);
            mv.visitEnd();
        }
        { //destroy
            mv = new MethodDebugVisitor(cw.visitMethod(ACC_PUBLIC, "destroy", "(" + anyValueDesc + ")V", null, null));
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 2);
            mv.visitEnd();
        }
        { // toString()
            mv = new MethodDebugVisitor(cw.visitMethod(ACC_PUBLIC, "toString", "()Ljava/lang/String;", null, null));
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, newDynName, FIELDPREFIX + "_client", clientDesc);
            Label l1 = new Label();
            mv.visitJumpInsn(IFNONNULL, l1);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;", false);
            Label l2 = new Label();
            mv.visitJumpInsn(GOTO, l2);
            mv.visitLabel(l1);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, newDynName, FIELDPREFIX + "_client", clientDesc);
            mv.visitMethodInsn(INVOKEVIRTUAL, clientName, "toSimpleString", "()Ljava/lang/String;", false);
            mv.visitLabel(l2);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        int i = -1;
        Uint128 serviceid = serviceid(name, serviceTypeOrImplClass);
        final List<SncpServiceAction> serviceActions = new ArrayList<>();
        Class serviceImpClass = realed ? createLocalServiceClass(loader, name, serviceTypeOrImplClass) : serviceTypeOrImplClass;
        for (Map.Entry<Uint128, Method> en : loadMethodActions(serviceImpClass).entrySet()) {
            serviceActions.add(new SncpServiceAction(serviceImpClass, en.getValue(), serviceid, en.getKey()));
        }
        for (final SncpServiceAction entry : serviceActions) {
            final int index = ++i;
            final java.lang.reflect.Method method = entry.method;
            {
                mv = new MethodDebugVisitor(cw.visitMethod(ACC_PUBLIC, method.getName(), Type.getMethodDescriptor(method), null, null));
                //mv.setDebug(true);
                { //给参数加上 Annotation
                    final Annotation[][] anns = method.getParameterAnnotations();
                    for (int k = 0; k < anns.length; k++) {
                        for (Annotation ann : anns[k]) {
                            MethodDebugVisitor.visitAnnotation(mv.visitParameterAnnotation(k, Type.getDescriptor(ann.annotationType()), true), ann);
                        }
                    }
                }
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, newDynName, FIELDPREFIX + "_client", clientDesc);

                MethodDebugVisitor.pushInt(mv, index);

                {  //传参数
                    int paramlen = entry.paramTypes.length;
                    MethodDebugVisitor.pushInt(mv, paramlen);
                    mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
                    java.lang.reflect.Type[] paramtypes = entry.paramTypes;
                    int insn = 0;
                    for (int j = 0; j < paramtypes.length; j++) {
                        final java.lang.reflect.Type pt = paramtypes[j];
                        mv.visitInsn(DUP);
                        insn++;
                        MethodDebugVisitor.pushInt(mv, j);
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
                            mv.visitMethodInsn(INVOKESTATIC, bigclaz.getName().replace('.', '/'), "valueOf", "(" + Type.getDescriptor((Class) pt) + ")" + Type.getDescriptor(bigclaz), false);
                        } else {
                            mv.visitVarInsn(ALOAD, insn);
                        }
                        mv.visitInsn(AASTORE);
                    }
                }

                mv.visitMethodInsn(INVOKEVIRTUAL, clientName, "remote", "(I[Ljava/lang/Object;)Ljava/lang/Object;", false);
                //mv.visitMethodInsn(INVOKEVIRTUAL, convertName, "convertFrom", convertFromDesc, false);
                if (method.getGenericReturnType() == void.class) {
                    mv.visitInsn(POP);
                    mv.visitInsn(RETURN);
                } else {
                    Class returnclz = method.getReturnType();
                    Class bigPrimitiveClass = returnclz.isPrimitive() ? TypeToken.primitiveToWrapper(returnclz) : returnclz;
                    mv.visitTypeInsn(CHECKCAST, (returnclz.isPrimitive() ? bigPrimitiveClass : returnclz).getName().replace('.', '/'));
                    if (returnclz.isPrimitive()) {
                        String bigPrimitiveName = bigPrimitiveClass.getName().replace('.', '/');
                        try {
                            java.lang.reflect.Method pm = bigPrimitiveClass.getMethod(returnclz.getSimpleName() + "Value");
                            mv.visitMethodInsn(INVOKEVIRTUAL, bigPrimitiveName, pm.getName(), Type.getMethodDescriptor(pm), false);
                        } catch (Exception ex) {
                            throw new SncpException(ex); //不可能会发生
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
                mv.visitMaxs(20, 20);
                mv.visitEnd();
            }
        }
        cw.visitEnd();
        byte[] bytes = cw.toByteArray();
        Class<?> newClazz = new ClassLoader(loader) {
            public final Class<?> loadClass(String name, byte[] b) {
                return defineClass(name, b, 0, b.length);
            }
        }.loadClass(newDynName.replace('/', '.'), bytes);
        RedkaleClassLoader.putDynClass(newDynName.replace('/', '.'), bytes, newClazz);
        RedkaleClassLoader.putReflectionPublicConstructors(newClazz, newDynName.replace('/', '.'));
        RedkaleClassLoader.putReflectionDeclaredConstructors(newClazz, newDynName.replace('/', '.'));
        try {
            T service = (T) newClazz.getDeclaredConstructor().newInstance();
            OldSncpClient client = new OldSncpClient(name, serviceTypeOrImplClass, service, messageAgent, transportFactory, true, realed ? createLocalServiceClass(loader, name, serviceTypeOrImplClass) : serviceTypeOrImplClass, clientAddress);
            client.setRemoteGroups(groups);
            if (transportFactory != null) {
                client.setRemoteGroupTransport(transportFactory.loadTransport(clientAddress, groups));
            }
            {
                Field c = newClazz.getDeclaredField(FIELDPREFIX + "_client");
                c.setAccessible(true);
                c.set(service, client);
                RedkaleClassLoader.putReflectionField(newDynName.replace('/', '.'), c);
            }
            if (messageAgent != null) {
                Field c = newClazz.getDeclaredField(FIELDPREFIX + "_messageagent");
                c.setAccessible(true);
                c.set(service, messageAgent);
                RedkaleClassLoader.putReflectionField(newDynName.replace('/', '.'), c);
                if (service instanceof WebSocketNode) {
                    c = WebSocketNode.class.getDeclaredField("messageAgent");
                    c.setAccessible(true);
                    c.set(service, messageAgent);
                    RedkaleClassLoader.putReflectionField(newDynName.replace('/', '.'), c);
                }
            }
            {
                Field c = newClazz.getDeclaredField(FIELDPREFIX + "_conf");
                c.setAccessible(true);
                c.set(service, conf);
                RedkaleClassLoader.putReflectionField(newDynName.replace('/', '.'), c);
            }
            if (transportFactory != null) {
                transportFactory.addSncpService(service);
            }
            return service;
        } catch (Exception ex) {
            throw new SncpException(ex);
        }

    }
}
