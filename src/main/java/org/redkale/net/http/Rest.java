/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.io.*;
import java.lang.annotation.*;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.reflect.*;
import java.net.InetSocketAddress;
import java.nio.channels.CompletionHandler;
import java.util.*;
import java.util.concurrent.CompletionStage;
import org.redkale.annotation.*;
import org.redkale.annotation.Comment;
import org.redkale.asm.*;
import static org.redkale.asm.ClassWriter.COMPUTE_FRAMES;
import static org.redkale.asm.Opcodes.*;
import org.redkale.asm.Type;
import org.redkale.convert.*;
import org.redkale.convert.json.*;
import org.redkale.inject.ResourceFactory;
import org.redkale.mq.spi.MessageAgent;
import org.redkale.net.*;
import org.redkale.net.sncp.Sncp;
import org.redkale.service.*;
import org.redkale.source.Flipper;
import org.redkale.util.*;
import static org.redkale.util.Utility.isEmpty;

/**
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@SuppressWarnings("unchecked")
public final class Rest {

    // 请求是否为rpc协议，值类型: 布尔，取值为true、false
    public static final String REST_HEADER_RPC = "Rest-Rpc";

    // traceid，值类型: 字符串
    public static final String REST_HEADER_TRACEID = "Rest-Traceid";

    // 当前用户ID值，值类型: 字符串
    public static final String REST_HEADER_CURRUSERID = "Rest-Curruserid";

    // 请求所需的RestService的资源名，值类型: 字符串
    public static final String REST_HEADER_RESNAME = "Rest-Resname";

    // 请求参数的反序列化种类，值类型: 字符串，取值为ConvertType枚举值名
    public static final String REST_HEADER_REQ_CONVERT = "Rest-Req-Convert";

    // 响应结果的序列化种类，值类型: 字符串，取值为ConvertType枚举值名
    public static final String REST_HEADER_RESP_CONVERT = "Rest-Resp-Convert";

    // ---------------------------------------------------------------------------------------------------
    static final String REST_TOSTRINGOBJ_FIELD_NAME = "_redkale_toStringSupplier";

    static final String REST_CONVERT_FIELD_PREFIX = "_redkale_restConvert_";

    static final String REST_SERVICE_FIELD_NAME = "_redkale_service";

    // 如果只有name=""的Service资源，则实例中_servicemap必须为null
    static final String REST_SERVICEMAP_FIELD_NAME = "_redkale_serviceMap";

    // 存在存在方法注解数组 Annotation[][] 第1维度是方法的下标， 第二维度是参数的下标
    private static final String REST_METHOD_ANNS_NAME = "_redkale_methodAnns";

    // 存在泛型的参数数组 Type[][] 第1维度是方法的下标， 第二维度是参数的下标
    private static final String REST_PARAMTYPES_FIELD_NAME = "_redkale_paramTypes";

    // 存在泛型的结果数组
    private static final String REST_RETURNTYPES_FIELD_NAME = "_redkale_returnTypes";

    private static final java.lang.reflect.Type TYPE_RETRESULT_STRING = new TypeToken<RetResult<String>>() {}.getType();

    private static final Set<String> EXCLUDERMETHODS = new HashSet<>();

    static {
        for (Method m : Object.class.getMethods()) {
            EXCLUDERMETHODS.add(m.getName());
        }
    }

    /** 用于标记由Rest.createRestServlet 方法创建的RestServlet */
    @Inherited
    @Documented
    @Target({TYPE})
    @Retention(RUNTIME)
    public static @interface RestDyn {

        // 是否不需要解析HttpHeader，对应HttpContext.lazyHeader
        boolean simple() default false;

        // 动态生成的类的子类需要关联一下，否则在运行过程中可能出现NoClassDefFoundError
        Class[] types() default {};
    }

    /** 用于标记由Rest.createRestServlet 方法创建的RestServlet */
    @Inherited
    @Documented
    @Target({TYPE})
    @Retention(RUNTIME)
    public static @interface RestDynSourceType {

        Class value();
    }

    private Rest() {}

    public static JsonFactory createJsonFactory(RestConvert[] converts, RestConvertCoder[] coders) {
        return createJsonFactory(-1, converts, coders);
    }

    public static JsonFactory createJsonFactory(int features, RestConvert[] converts, RestConvertCoder[] coders) {
        if (Utility.isEmpty(converts) && Utility.isEmpty(coders)) {
            return JsonFactory.root();
        }
        final JsonFactory childFactory = JsonFactory.create();
        if (features > -1) {
            childFactory.withFeatures(features);
        }
        List<Class> types = new ArrayList<>();
        Set<Class> reloadTypes = new HashSet<>();
        if (coders != null) {
            for (RestConvertCoder rcc : coders) {
                Creator<? extends SimpledCoder> creator = Creator.create(rcc.coder());
                childFactory.register(rcc.type(), rcc.field(), creator.create());
                reloadTypes.add(rcc.type());
            }
        }
        if (converts != null) {
            for (RestConvert rc : converts) {
                if (rc.type() == void.class || rc.type() == Void.class) {
                    childFactory.skipAllIgnore(true);
                    break;
                }
                if (types.contains(rc.type())) {
                    throw new RestException("@RestConvert type(" + rc.type() + ") repeat");
                }
                if (rc.skipIgnore()) {
                    childFactory.registerSkipIgnore(rc.type());
                    reloadTypes.add(rc.type());
                } else if (rc.onlyColumns().length > 0) {
                    childFactory.registerIgnoreAll(rc.type(), rc.onlyColumns());
                    reloadTypes.add(rc.type());
                } else {
                    childFactory.register(rc.type(), false, rc.convertColumns());
                    childFactory.register(rc.type(), true, rc.ignoreColumns());
                    reloadTypes.add(rc.type());
                }
                types.add(rc.type());
                if (rc.features() > -1) {
                    childFactory.withFeatures(rc.features());
                }
            }
        }
        for (Class type : reloadTypes) {
            childFactory.reloadCoder(type);
        }
        return childFactory;
    }

    static String getWebModuleNameLowerCase(Class<? extends Service> serviceType) {
        final RestService controller = serviceType.getAnnotation(RestService.class);
        if (controller == null) {
            return serviceType.getSimpleName().replaceAll("Service.*$", "").toLowerCase();
        }
        if (controller.ignore()) {
            return null;
        }
        return (!controller.name().isEmpty())
                ? controller.name().trim()
                : serviceType.getSimpleName().replaceAll("Service.*$", "").toLowerCase();
    }

    static String getWebModuleName(Class<? extends Service> serviceType) {
        final RestService controller = serviceType.getAnnotation(RestService.class);
        if (controller == null) {
            return serviceType.getSimpleName().replaceAll("Service.*$", "");
        }
        if (controller.ignore()) {
            return null;
        }
        return (!controller.name().isEmpty())
                ? controller.name().trim()
                : serviceType.getSimpleName().replaceAll("Service.*$", "");
    }

    /**
     * 判断HttpServlet是否为Rest动态生成的
     *
     * @param servlet 检测的HttpServlet
     * @return 是否是动态生成的RestHttpServlet
     */
    public static boolean isRestDyn(HttpServlet servlet) {
        return servlet.getClass().getAnnotation(RestDyn.class) != null;
    }

    /**
     * 判断HttpServlet是否为Rest动态生成的,且simple, 不需要读取http-header的方法视为simple=true
     *
     * @param servlet 检测的HttpServlet
     * @return 是否是动态生成的RestHttpServlet
     */
    static boolean isSimpleRestDyn(HttpServlet servlet) {
        RestDyn dyn = servlet.getClass().getAnnotation(RestDyn.class);
        return dyn != null && dyn.simple();
    }

    /**
     * 获取Rest动态生成HttpServlet里的Service对象，若不是Rest动态生成的HttpServlet，返回null
     *
     * @param servlet HttpServlet
     * @return Service
     */
    public static Service getService(HttpServlet servlet) {
        if (servlet == null) {
            return null;
        }
        if (!isRestDyn(servlet)) {
            return null;
        }
        try {
            Field ts = servlet.getClass().getDeclaredField(REST_SERVICE_FIELD_NAME);
            ts.setAccessible(true);
            return (Service) ts.get(servlet);
        } catch (Exception e) {
            return null;
        }
    }

    public static Map<String, Service> getServiceMap(HttpServlet servlet) {
        if (servlet == null) {
            return null;
        }
        try {
            Field ts = servlet.getClass().getDeclaredField(REST_SERVICEMAP_FIELD_NAME);
            ts.setAccessible(true);
            return (Map) ts.get(servlet);
        } catch (Exception e) {
            return null;
        }
    }

    public static void setServiceMap(HttpServlet servlet, Map<String, Service> map) {
        if (servlet == null) {
            return;
        }
        try {
            Field ts = servlet.getClass().getDeclaredField(REST_SERVICEMAP_FIELD_NAME);
            ts.setAccessible(true);
            ts.set(servlet, map);
        } catch (Exception e) {
            throw new RestException(e);
        }
    }

    public static String getRestModule(Service service) {
        final RestService controller = service.getClass().getAnnotation(RestService.class);
        if (controller != null && !controller.name().isEmpty()) {
            return controller.name();
        }
        final Class serviceType = Sncp.getResourceType(service);
        return serviceType.getSimpleName().replaceAll("Service.*$", "").toLowerCase();
    }

    // 格式: http.req.module.user
    public static String generateHttpReqTopic(String module, String nodeid) {
        return getHttpReqTopicPrefix() + "module." + module.toLowerCase();
    }

    // 格式: http.req.module.user
    public static String generateHttpReqTopic(String module, String resname, String nodeid) {
        return getHttpReqTopicPrefix() + "module." + module.toLowerCase()
                + (resname == null || resname.isEmpty() ? "" : ("-" + resname));
    }

    public static String generateHttpReqTopic(Service service, String nodeid) {
        String resname = Sncp.getResourceName(service);
        String module = getRestModule(service).toLowerCase();
        return getHttpReqTopicPrefix() + "module." + module + (resname.isEmpty() ? "" : ("-" + resname));
    }

    public static String getHttpReqTopicPrefix() {
        return "http.req.";
    }

    public static String getHttpRespTopicPrefix() {
        return "http.resp.";
    }

    // 仅供Rest动态构建里使用
    @ClassDepends
    public static void setRequestAnnotations(HttpRequest request, Annotation[] annotations) {
        request.setAnnotations(annotations);
    }

    // 仅供Rest动态构建里 currentUserid() 使用
    @ClassDepends
    public static <T> T orElse(T t, T defValue) {
        return t == null ? defValue : t;
    }

    public static <T extends WebSocketServlet> T createRestWebSocketServlet(
            RedkaleClassLoader classLoader, Class<? extends WebSocket> webSocketType, MessageAgent messageAgent) {
        if (webSocketType == null) {
            throw new RestException("Rest WebSocket Class is null on createRestWebSocketServlet");
        }
        if (Modifier.isAbstract(webSocketType.getModifiers())) {
            throw new RestException(
                    "Rest WebSocket Class(" + webSocketType + ") cannot abstract on createRestWebSocketServlet");
        }
        if (Modifier.isFinal(webSocketType.getModifiers())) {
            throw new RestException(
                    "Rest WebSocket Class(" + webSocketType + ") cannot final on createRestWebSocketServlet");
        }
        final RestWebSocket rws = webSocketType.getAnnotation(RestWebSocket.class);
        if (rws == null || rws.ignore()) {
            throw new RestException("Rest WebSocket Class(" + webSocketType
                    + ") have not @RestWebSocket or @RestWebSocket.ignore=true on createRestWebSocketServlet");
        }
        boolean valid = false;
        for (Constructor c : webSocketType.getDeclaredConstructors()) {
            if (c.getParameterCount() == 0
                    && (Modifier.isPublic(c.getModifiers()) || Modifier.isProtected(c.getModifiers()))) {
                valid = true;
                break;
            }
        }
        if (!valid) {
            throw new RestException("Rest WebSocket Class(" + webSocketType
                    + ") must have public or protected Constructor on createRestWebSocketServlet");
        }
        final String rwsname = ResourceFactory.getResourceName(rws.name());
        if (!checkName(rws.catalog())) {
            throw new RestException(webSocketType.getName() + " have illegal " + RestWebSocket.class.getSimpleName()
                    + ".catalog, only 0-9 a-z A-Z _ cannot begin 0-9");
        }
        if (!checkName(rwsname)) {
            throw new RestException(webSocketType.getName() + " have illegal " + RestWebSocket.class.getSimpleName()
                    + ".name, only 0-9 a-z A-Z _ cannot begin 0-9");
        }

        // ----------------------------------------------------------------------------------------
        final Set<Field> resourcesFieldSet = new LinkedHashSet<>();
        final Set<String> resourcesFieldNameSet = new HashSet<>();
        Class clzz = webSocketType;
        do {
            for (Field field : clzz.getDeclaredFields()) {
                if (field.getAnnotation(Resource.class) == null
                        && field.getAnnotation(javax.annotation.Resource.class) == null) {
                    continue;
                }
                if (resourcesFieldNameSet.contains(field.getName())) {
                    continue;
                }
                if (Modifier.isStatic(field.getModifiers())) {
                    throw new RestException(field + " cannot static on createRestWebSocketServlet");
                }
                if (Modifier.isFinal(field.getModifiers())) {
                    throw new RestException(field + " cannot final on createRestWebSocketServlet");
                }
                if (!Modifier.isPublic(field.getModifiers()) && !Modifier.isProtected(field.getModifiers())) {
                    throw new RestException(field + " must be public or protected on createRestWebSocketServlet");
                }
                resourcesFieldNameSet.add(field.getName());
                resourcesFieldSet.add(field);
            }
        } while ((clzz = clzz.getSuperclass()) != Object.class);

        // ----------------------------------------------------------------------------------------
        boolean namePresent = false;
        try {
            Method m0 = null;
            for (Method method : webSocketType.getMethods()) {
                if (method.getParameterCount() > 0) {
                    m0 = method;
                    break;
                }
            }
            namePresent = m0 == null || m0.getParameters()[0].isNamePresent();
        } catch (Exception e) {
            // do nothing
        }
        final Map<String, AsmMethodBean> asmParamMap =
                namePresent ? null : AsmMethodBoost.getMethodBeans(webSocketType);
        final Set<String> messageNames = new HashSet<>();
        Method wildcardMethod = null;
        List<Method> mmethods = new ArrayList<>();
        for (Method method : webSocketType.getMethods()) {
            RestOnMessage rom = method.getAnnotation(RestOnMessage.class);
            if (rom == null) {
                continue;
            }
            String name = rom.name();
            if (!"*".equals(name) && !checkName(name)) {
                throw new RestException("@RestOnMessage.name contains illegal characters on (" + method + ")");
            }
            if (Modifier.isFinal(method.getModifiers())) {
                throw new RestException("@RestOnMessage method can not final but (" + method + ")");
            }
            if (Modifier.isStatic(method.getModifiers())) {
                throw new RestException("@RestOnMessage method can not static but (" + method + ")");
            }
            if (method.getReturnType() != void.class) {
                throw new RestException("@RestOnMessage method must return void but (" + method + ")");
            }
            if (method.getExceptionTypes().length > 0) {
                throw new RestException("@RestOnMessage method can not throw exception but (" + method + ")");
            }
            if (name.isEmpty()) {
                throw new RestException(method + " RestOnMessage.name is empty createRestWebSocketServlet");
            }
            if (messageNames.contains(name)) {
                throw new RestException(method + " repeat RestOnMessage.name(" + name + ") createRestWebSocketServlet");
            }
            messageNames.add(name);
            if ("*".equals(name)) {
                wildcardMethod = method;
            } else {
                mmethods.add(method);
            }
        }
        final List<Method> messageMethods = new ArrayList<>();
        messageMethods.addAll(mmethods);
        // wildcardMethod 必须放最后, _DynRestOnMessageConsumer 是按messageMethods顺序来判断的
        if (wildcardMethod != null) {
            messageMethods.add(wildcardMethod);
        }
        // ----------------------------------------------------------------------------------------
        final String resDesc = Type.getDescriptor(Resource.class);
        final String wsDesc = Type.getDescriptor(WebSocket.class);
        final String wsParamDesc = Type.getDescriptor(WebSocketParam.class);
        final String jsonConvertDesc = Type.getDescriptor(JsonConvert.class);
        final String convertDisabledDesc = Type.getDescriptor(ConvertDisabled.class);
        final String webSocketParamName = Type.getInternalName(WebSocketParam.class);
        final String supDynName = WebSocketServlet.class.getName().replace('.', '/');
        final String webServletDesc = Type.getDescriptor(WebServlet.class);
        final String webSocketInternalName = Type.getInternalName(webSocketType);

        final String newDynName = "org/redkaledyn/http/restws/" + "_DynWebScoketServlet__"
                + webSocketType.getName().replace('.', '_').replace('$', '_');

        final String newDynWebSokcetSimpleName = "_Dyn" + webSocketType.getSimpleName();
        final String newDynWebSokcetFullName = newDynName + "$" + newDynWebSokcetSimpleName;

        final String newDynMessageSimpleName = "_Dyn" + webSocketType.getSimpleName() + "Message";
        final String newDynMessageFullName = newDynName + "$" + newDynMessageSimpleName;

        final String newDynConsumerSimpleName = "_DynRestOnMessageConsumer";
        final String newDynConsumerFullName = newDynName + "$" + newDynConsumerSimpleName;
        try {
            Class clz = classLoader.loadClass(newDynName.replace('/', '.'));
            T servlet = (T) clz.getDeclaredConstructor().newInstance();
            Map<String, Annotation[]> msgclassToAnnotations = new HashMap<>();
            for (int i = 0; i < messageMethods.size(); i++) { // _DyncXXXWebSocketMessage 子消息List
                Method method = messageMethods.get(i);
                String endfix = "_" + method.getName() + "_" + (i > 9 ? i : ("0" + i));
                String newDynSuperMessageFullName = newDynMessageFullName + (method == wildcardMethod ? "" : endfix);
                msgclassToAnnotations.put(newDynSuperMessageFullName, method.getAnnotations());
            }
            clz.getField("_redkale_annotations").set(null, msgclassToAnnotations);
            if (rws.cryptor() != Cryptor.class) {
                Cryptor cryptor = rws.cryptor().getDeclaredConstructor().newInstance();
                Field cryptorField = clz.getSuperclass().getDeclaredField("cryptor"); // WebSocketServlet
                cryptorField.setAccessible(true);
                cryptorField.set(servlet, cryptor);
            }
            if (messageAgent != null) {
                ((WebSocketServlet) servlet).messageAgent = messageAgent;
            }
            return servlet;
        } catch (Throwable e) {
            // do nothing
        }

        final List<Field> resourcesFields = new ArrayList<>(resourcesFieldSet);
        StringBuilder sb1 = new StringBuilder();
        StringBuilder sb2 = new StringBuilder();
        for (int i = 0; i < resourcesFields.size(); i++) {
            Field field = resourcesFields.get(i);
            sb1.append(Type.getDescriptor(field.getType()));
            sb2.append(Utility.getTypeDescriptor(field.getGenericType()));
        }
        final String resourceDescriptor = sb1.toString();
        final String resourceGenericDescriptor = sb1.length() == sb2.length() ? null : sb2.toString();
        // ----------------------------------------------------------------------------------------

        ClassWriter cw = new ClassWriter(COMPUTE_FRAMES);
        FieldVisitor fv;
        MethodDebugVisitor mv;
        AnnotationVisitor av0;
        cw.visit(V11, ACC_PUBLIC + ACC_SUPER, newDynName, null, supDynName, null);
        { // RestDyn
            av0 = cw.visitAnnotation(Type.getDescriptor(RestDyn.class), true);
            av0.visit("simple", false); // WebSocketServlet必须要解析http-header
            {
                AnnotationVisitor av1 = av0.visitArray("types");
                av1.visit(null, Type.getType("L" + newDynConsumerFullName.replace('.', '/') + ";"));
                av1.visit(null, Type.getType("L" + newDynWebSokcetFullName.replace('.', '/') + ";"));
                av1.visit(
                        null,
                        Type.getType("L" + newDynMessageFullName.replace('.', '/')
                                + ";")); // 位置固定第三个，下面用Message类进行loadDecoder会用到
                av1.visitEnd();
            }
            av0.visitEnd();
        }
        { // RestDynSourceType
            av0 = cw.visitAnnotation(Type.getDescriptor(RestDynSourceType.class), true);
            av0.visit("value", Type.getType(Type.getDescriptor(webSocketType)));
            av0.visitEnd();
        }
        { // 注入 @WebServlet 注解
            String urlpath = (rws.catalog().isEmpty() ? "/" : ("/" + rws.catalog() + "/")) + rwsname;
            av0 = cw.visitAnnotation(webServletDesc, true);
            {
                AnnotationVisitor av1 = av0.visitArray("value");
                av1.visit(null, urlpath);
                av1.visitEnd();
            }
            av0.visit("name", rwsname);
            av0.visit("moduleid", 0);
            av0.visit("repair", rws.repair());
            av0.visit("comment", rws.comment());
            av0.visitEnd();
        }
        { // 内部类
            cw.visitInnerClass(newDynConsumerFullName, newDynName, newDynConsumerSimpleName, ACC_PUBLIC + ACC_STATIC);

            cw.visitInnerClass(newDynWebSokcetFullName, newDynName, newDynWebSokcetSimpleName, ACC_PUBLIC + ACC_STATIC);

            cw.visitInnerClass(newDynMessageFullName, newDynName, newDynMessageSimpleName, ACC_PUBLIC + ACC_STATIC);

            for (int i = 0; i < messageMethods.size(); i++) {
                Method method = messageMethods.get(i);
                String endfix = "_" + method.getName() + "_" + (i > 9 ? i : ("0" + i));
                String newDynSuperMessageFullName = newDynMessageFullName + (method == wildcardMethod ? "" : endfix);
                cw.visitInnerClass(
                        newDynSuperMessageFullName,
                        newDynName,
                        newDynMessageSimpleName + endfix,
                        ACC_PUBLIC + ACC_STATIC);
            }
        }
        { // @Resource
            for (int i = 0; i < resourcesFields.size(); i++) {
                Field field = resourcesFields.get(i);
                Resource res = field.getAnnotation(Resource.class);
                javax.annotation.Resource res2 = field.getAnnotation(javax.annotation.Resource.class);
                java.lang.reflect.Type fieldType = field.getGenericType();
                fv = cw.visitField(
                        ACC_PRIVATE,
                        "_redkale_resource_" + i,
                        Type.getDescriptor(field.getType()),
                        fieldType == field.getType() ? null : Utility.getTypeDescriptor(fieldType),
                        null);
                {
                    av0 = fv.visitAnnotation(resDesc, true);
                    av0.visit("name", res != null ? res.name() : res2.name());
                    av0.visit("required", res == null || res.required());
                    av0.visitEnd();
                }
                fv.visitEnd();
            }
        }
        { // _redkale_annotations
            fv = cw.visitField(
                    ACC_PUBLIC + ACC_STATIC,
                    "_redkale_annotations",
                    "Ljava/util/Map;",
                    "Ljava/util/Map<Ljava/lang/String;[Ljava/lang/annotation/Annotation;>;",
                    null);
            fv.visitEnd();
        }
        { // _DynWebSocketServlet构造函数
            mv = new MethodDebugVisitor(cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null));
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, supDynName, "<init>", "()V", false);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitLdcInsn(Type.getObjectType(newDynName + "$" + newDynWebSokcetSimpleName + "Message"));
            mv.visitFieldInsn(PUTFIELD, newDynName, "messageRestType", "Ljava/lang/reflect/Type;");

            mv.visitVarInsn(ALOAD, 0);
            Asms.visitInsn(mv, rws.liveinterval());
            mv.visitFieldInsn(PUTFIELD, newDynName, "liveinterval", "I");

            mv.visitVarInsn(ALOAD, 0);
            Asms.visitInsn(mv, rws.wsmaxconns());
            mv.visitFieldInsn(PUTFIELD, newDynName, "wsmaxconns", "I");

            mv.visitVarInsn(ALOAD, 0);
            Asms.visitInsn(mv, rws.wsmaxbody());
            mv.visitFieldInsn(PUTFIELD, newDynName, "wsmaxbody", "I");

            mv.visitVarInsn(ALOAD, 0);
            mv.visitInsn(rws.single() ? ICONST_1 : ICONST_0);
            mv.visitFieldInsn(PUTFIELD, newDynName, "single", "Z");

            mv.visitVarInsn(ALOAD, 0);
            mv.visitInsn(rws.anyuser() ? ICONST_1 : ICONST_0);
            mv.visitFieldInsn(PUTFIELD, newDynName, "anyuser", "Z");

            mv.visitInsn(RETURN);
            mv.visitMaxs(3, 1);
            mv.visitEnd();
        }
        { // createWebSocket 方法
            mv = new MethodDebugVisitor(cw.visitMethod(
                    ACC_PROTECTED,
                    "createWebSocket",
                    "()" + wsDesc,
                    "<G::Ljava/io/Serializable;T:Ljava/lang/Object;>()L"
                            + WebSocket.class.getName().replace('.', '/') + "<TG;TT;>;",
                    null));
            mv.visitTypeInsn(NEW, newDynName + "$" + newDynWebSokcetSimpleName);
            mv.visitInsn(DUP);
            for (int i = 0; i < resourcesFields.size(); i++) {
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(
                        GETFIELD,
                        newDynName,
                        "_redkale_resource_" + i,
                        Type.getDescriptor(resourcesFields.get(i).getType()));
            }
            mv.visitMethodInsn(
                    INVOKESPECIAL, newDynWebSokcetFullName, "<init>", "(" + resourceDescriptor + ")V", false);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(2 + resourcesFields.size(), 1);
            mv.visitEnd();
        }
        { // createRestOnMessageConsumer
            mv = new MethodDebugVisitor(cw.visitMethod(
                    ACC_PROTECTED,
                    "createRestOnMessageConsumer",
                    "()Ljava/util/function/BiConsumer;",
                    "()Ljava/util/function/BiConsumer<" + wsDesc + "Ljava/lang/Object;>;",
                    null));
            mv.visitTypeInsn(NEW, newDynConsumerFullName);
            mv.visitInsn(DUP);
            mv.visitMethodInsn(INVOKESPECIAL, newDynConsumerFullName, "<init>", "()V", false);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(2, 1);
            mv.visitEnd();
        }
        { // resourceName
            mv = new MethodDebugVisitor(cw.visitMethod(ACC_PUBLIC, "resourceName", "()Ljava/lang/String;", null, null));
            mv.visitLdcInsn(rwsname);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }

        Map<String, Annotation[]> msgclassToAnnotations = new HashMap<>();
        for (int i = 0; i < messageMethods.size(); i++) { // _DyncXXXWebSocketMessage 子消息List
            final Method method = messageMethods.get(i);
            String endfix = "_" + method.getName() + "_" + (i > 9 ? i : ("0" + i));
            String newDynSuperMessageFullName = newDynMessageFullName + (method == wildcardMethod ? "" : endfix);
            msgclassToAnnotations.put(newDynSuperMessageFullName, method.getAnnotations());

            ClassWriter cw2 = new ClassWriter(COMPUTE_FRAMES);
            cw2.visit(
                    V11,
                    ACC_PUBLIC + ACC_FINAL + ACC_SUPER,
                    newDynSuperMessageFullName,
                    null,
                    "java/lang/Object",
                    new String[] {webSocketParamName, "java/lang/Runnable"});
            cw2.visitInnerClass(
                    newDynSuperMessageFullName, newDynName, newDynMessageSimpleName + endfix, ACC_PUBLIC + ACC_STATIC);
            Set<String> paramNames = new HashSet<>();
            AsmMethodBean methodBean = asmParamMap == null ? null : AsmMethodBean.get(asmParamMap, method);
            List<AsmMethodParam> names = methodBean == null ? null : methodBean.getParams();
            Parameter[] params = method.getParameters();
            final LinkedHashMap<String, Parameter> paramap = new LinkedHashMap(); // 必须使用LinkedHashMap确保顺序
            for (int j = 0; j < params.length; j++) { // 字段列表
                Parameter param = params[j];
                String paramName = param.getName();
                RestParam rp = param.getAnnotation(RestParam.class);
                Param pm = param.getAnnotation(Param.class);
                if (rp != null && !rp.name().isEmpty()) {
                    paramName = rp.name();
                } else if (pm != null && !pm.value().isEmpty()) {
                    paramName = pm.value();
                } else if (names != null && names.size() > j) {
                    paramName = names.get(j).getName();
                }
                if (paramNames.contains(paramName)) {
                    throw new RestException(method + " has same @RestParam.name");
                }
                paramNames.add(paramName);
                paramap.put(paramName, param);
                fv = cw2.visitField(
                        ACC_PUBLIC,
                        paramName,
                        Type.getDescriptor(param.getType()),
                        param.getType() == param.getParameterizedType()
                                ? null
                                : Utility.getTypeDescriptor(param.getParameterizedType()),
                        null);
                fv.visitEnd();
            }
            if (method == wildcardMethod) {
                for (int j = 0; j < messageMethods.size(); j++) {
                    Method method2 = messageMethods.get(j);
                    if (method2 == wildcardMethod) {
                        continue;
                    }
                    String endfix2 = "_" + method2.getName() + "_" + (j > 9 ? j : ("0" + j));
                    String newDynSuperMessageFullName2 =
                            newDynMessageFullName + (method2 == wildcardMethod ? "" : endfix2);
                    cw2.visitInnerClass(
                            newDynSuperMessageFullName2,
                            newDynName,
                            newDynMessageSimpleName + endfix2,
                            ACC_PUBLIC + ACC_STATIC);
                    fv = cw2.visitField(
                            ACC_PUBLIC,
                            method2.getAnnotation(RestOnMessage.class).name(),
                            "L" + newDynSuperMessageFullName2 + ";",
                            null,
                            null);
                    fv.visitEnd();
                }
            }
            { // _redkale_websocket
                fv = cw2.visitField(ACC_PUBLIC, "_redkale_websocket", "L" + newDynWebSokcetFullName + ";", null, null);
                av0 = fv.visitAnnotation(convertDisabledDesc, true);
                av0.visitEnd();
                fv.visitEnd();
            }
            { // 空构造函数
                mv = new MethodDebugVisitor(cw2.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null));
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            { // getNames
                mv = new MethodDebugVisitor(
                        cw2.visitMethod(ACC_PUBLIC, "getNames", "()[Ljava/lang/String;", null, null));
                av0 = mv.visitAnnotation(convertDisabledDesc, true);
                av0.visitEnd();
                Asms.visitInsn(mv, paramap.size());
                mv.visitTypeInsn(ANEWARRAY, "java/lang/String");
                int index = -1;
                for (Map.Entry<String, Parameter> en : paramap.entrySet()) {
                    mv.visitInsn(DUP);
                    Asms.visitInsn(mv, ++index);
                    mv.visitLdcInsn(en.getKey());
                    mv.visitInsn(AASTORE);
                }
                mv.visitInsn(ARETURN);
                mv.visitMaxs(paramap.size() + 2, 1);
                mv.visitEnd();
            }
            { // getValue
                mv = new MethodDebugVisitor(cw2.visitMethod(
                        ACC_PUBLIC,
                        "getValue",
                        "(Ljava/lang/String;)Ljava/lang/Object;",
                        "<T:Ljava/lang/Object;>(Ljava/lang/String;)TT;",
                        null));
                for (Map.Entry<String, Parameter> en : paramap.entrySet()) {
                    Class paramType = en.getValue().getType();
                    mv.visitLdcInsn(en.getKey());
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
                    Label l1 = new Label();
                    mv.visitJumpInsn(IFEQ, l1);
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, newDynSuperMessageFullName, en.getKey(), Type.getDescriptor(paramType));
                    if (paramType.isPrimitive()) {
                        Class bigclaz = TypeToken.primitiveToWrapper(paramType);
                        mv.visitMethodInsn(
                                INVOKESTATIC,
                                bigclaz.getName().replace('.', '/'),
                                "valueOf",
                                "(" + Type.getDescriptor(paramType) + ")" + Type.getDescriptor(bigclaz),
                                false);
                    }
                    mv.visitInsn(ARETURN);
                    mv.visitLabel(l1);
                }
                mv.visitInsn(ACONST_NULL);
                mv.visitInsn(ARETURN);
                mv.visitMaxs(2, 2);
                mv.visitEnd();
            }
            { // getAnnotations
                mv = new MethodDebugVisitor(cw2.visitMethod(
                        ACC_PUBLIC, "getAnnotations", "()[Ljava/lang/annotation/Annotation;", null, null));
                av0 = mv.visitAnnotation(convertDisabledDesc, true);
                av0.visitEnd();
                mv.visitFieldInsn(GETSTATIC, newDynName, "_redkale_annotations", "Ljava/util/Map;");
                mv.visitLdcInsn(newDynSuperMessageFullName);
                mv.visitMethodInsn(
                        INVOKEINTERFACE, "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
                mv.visitTypeInsn(CHECKCAST, "[Ljava/lang/annotation/Annotation;");
                mv.visitVarInsn(ASTORE, 1);
                mv.visitVarInsn(ALOAD, 1);
                Label l2 = new Label();
                mv.visitJumpInsn(IFNONNULL, l2);
                mv.visitInsn(ICONST_0);
                mv.visitTypeInsn(ANEWARRAY, "java/lang/annotation/Annotation");
                mv.visitInsn(ARETURN);
                mv.visitLabel(l2);
                mv.visitFrame(Opcodes.F_APPEND, 1, new Object[] {"[Ljava/lang/annotation/Annotation;"}, 0, null);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitInsn(ARRAYLENGTH);
                mv.visitMethodInsn(
                        INVOKESTATIC, "java/util/Arrays", "copyOf", "([Ljava/lang/Object;I)[Ljava/lang/Object;", false);
                mv.visitTypeInsn(CHECKCAST, "[Ljava/lang/annotation/Annotation;");
                mv.visitInsn(ARETURN);
                mv.visitMaxs(2, 2);
                mv.visitEnd();
            }
            { // execute
                mv = new MethodDebugVisitor(
                        cw2.visitMethod(ACC_PUBLIC, "execute", "(L" + newDynWebSokcetFullName + ";)V", null, null));
                mv.visitVarInsn(ALOAD, 0);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitFieldInsn(
                        PUTFIELD,
                        newDynSuperMessageFullName,
                        "_redkale_websocket",
                        "L" + newDynWebSokcetFullName + ";");
                mv.visitVarInsn(ALOAD, 1);
                mv.visitLdcInsn(method.getAnnotation(RestOnMessage.class).name());
                mv.visitVarInsn(ALOAD, 0);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(
                        INVOKEVIRTUAL,
                        newDynWebSokcetFullName,
                        "preOnMessage",
                        "(Ljava/lang/String;" + wsParamDesc + "Ljava/lang/Runnable;)V",
                        false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(4, 2);
                mv.visitEnd();
            }
            { // run
                mv = new MethodDebugVisitor(cw2.visitMethod(ACC_PUBLIC, "run", "()V", null, null));
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(
                        GETFIELD,
                        newDynSuperMessageFullName,
                        "_redkale_websocket",
                        "L" + newDynWebSokcetFullName + ";");

                for (Map.Entry<String, Parameter> en : paramap.entrySet()) {
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(
                            GETFIELD,
                            (newDynSuperMessageFullName),
                            en.getKey(),
                            Type.getDescriptor(en.getValue().getType()));
                }
                mv.visitMethodInsn(
                        INVOKEVIRTUAL,
                        newDynWebSokcetFullName,
                        method.getName(),
                        Type.getMethodDescriptor(method),
                        false);

                mv.visitInsn(RETURN);
                mv.visitMaxs(3, 1);
                mv.visitEnd();
            }
            { // toString
                mv = new MethodDebugVisitor(
                        cw2.visitMethod(ACC_PUBLIC, "toString", "()Ljava/lang/String;", null, null));
                mv.visitMethodInsn(
                        INVOKESTATIC,
                        JsonConvert.class.getName().replace('.', '/'),
                        "root",
                        "()" + jsonConvertDesc,
                        false);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(
                        INVOKEVIRTUAL,
                        JsonConvert.class.getName().replace('.', '/'),
                        "convertTo",
                        "(Ljava/lang/Object;)Ljava/lang/String;",
                        false);
                mv.visitInsn(ARETURN);
                mv.visitMaxs(2, 1);
                mv.visitEnd();
            }
            cw2.visitEnd();
            byte[] bytes = cw2.toByteArray();
            classLoader.loadClass((newDynSuperMessageFullName).replace('/', '.'), bytes);
        }

        if (wildcardMethod == null) { // _DynXXXWebSocketMessage class
            ClassWriter cw2 = new ClassWriter(COMPUTE_FRAMES);
            cw2.visit(V11, ACC_PUBLIC + ACC_FINAL + ACC_SUPER, newDynMessageFullName, null, "java/lang/Object", null);

            cw2.visitInnerClass(newDynMessageFullName, newDynName, newDynMessageSimpleName, ACC_PUBLIC + ACC_STATIC);

            for (int i = 0; i < messageMethods.size(); i++) {
                Method method = messageMethods.get(i);
                String endfix = "_" + method.getName() + "_" + (i > 9 ? i : ("0" + i));
                String newDynSuperMessageFullName = newDynMessageFullName + (method == wildcardMethod ? "" : endfix);
                cw2.visitInnerClass(
                        newDynSuperMessageFullName,
                        newDynName,
                        newDynMessageSimpleName + endfix,
                        ACC_PUBLIC + ACC_STATIC);

                fv = cw2.visitField(
                        ACC_PUBLIC,
                        method.getAnnotation(RestOnMessage.class).name(),
                        "L" + newDynSuperMessageFullName + ";",
                        null,
                        null);
                fv.visitEnd();
            }
            { // 构造函数
                mv = new MethodDebugVisitor(cw2.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null));
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            { // toString
                mv = new MethodDebugVisitor(
                        cw2.visitMethod(ACC_PUBLIC, "toString", "()Ljava/lang/String;", null, null));
                mv.visitMethodInsn(
                        INVOKESTATIC,
                        JsonConvert.class.getName().replace('.', '/'),
                        "root",
                        "()" + jsonConvertDesc,
                        false);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(
                        INVOKEVIRTUAL,
                        JsonConvert.class.getName().replace('.', '/'),
                        "convertTo",
                        "(Ljava/lang/Object;)Ljava/lang/String;",
                        false);
                mv.visitInsn(ARETURN);
                mv.visitMaxs(2, 1);
                mv.visitEnd();
            }
            cw2.visitEnd();
            byte[] bytes = cw2.toByteArray();
            classLoader.loadClass(newDynMessageFullName.replace('/', '.'), bytes);
        }

        { // _DynXXXWebSocket class
            ClassWriter cw2 = new ClassWriter(COMPUTE_FRAMES);
            cw2.visit(
                    V11,
                    ACC_PUBLIC + ACC_FINAL + ACC_SUPER,
                    newDynWebSokcetFullName,
                    null,
                    webSocketInternalName,
                    null);

            cw2.visitInnerClass(
                    newDynWebSokcetFullName, newDynName, newDynWebSokcetSimpleName, ACC_PUBLIC + ACC_STATIC);
            {
                mv = new MethodDebugVisitor(cw2.visitMethod(
                        ACC_PUBLIC,
                        "<init>",
                        "(" + resourceDescriptor + ")V",
                        resourceGenericDescriptor == null ? null : ("(" + resourceGenericDescriptor + ")V"),
                        null));
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, webSocketInternalName, "<init>", "()V", false);
                for (int i = 0; i < resourcesFields.size(); i++) {
                    Field field = resourcesFields.get(i);
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitVarInsn(ALOAD, i + 1);
                    mv.visitFieldInsn(
                            PUTFIELD, newDynWebSokcetFullName, field.getName(), Type.getDescriptor(field.getType()));
                }
                mv.visitInsn(RETURN);
                mv.visitMaxs(2, 1 + resourcesFields.size());
                mv.visitEnd();
            }
            { // RestDyn
                av0 = cw2.visitAnnotation(Type.getDescriptor(RestDyn.class), true);
                av0.visit("simple", false);
                av0.visitEnd();
            }
            cw2.visitEnd();
            byte[] bytes = cw2.toByteArray();
            classLoader.loadClass(newDynWebSokcetFullName.replace('/', '.'), bytes);
        }

        { // _DynRestOnMessageConsumer class
            ClassWriter cw2 = new ClassWriter(COMPUTE_FRAMES);
            cw2.visit(
                    V11,
                    ACC_PUBLIC + ACC_FINAL + ACC_SUPER,
                    newDynConsumerFullName,
                    "Ljava/lang/Object;Ljava/util/function/BiConsumer<" + wsDesc + "Ljava/lang/Object;>;",
                    "java/lang/Object",
                    new String[] {"java/util/function/BiConsumer"});

            cw2.visitInnerClass(newDynConsumerFullName, newDynName, newDynConsumerSimpleName, ACC_PUBLIC + ACC_STATIC);
            cw2.visitInnerClass(newDynMessageFullName, newDynName, newDynMessageSimpleName, ACC_PUBLIC + ACC_STATIC);
            for (int i = 0; i < messageMethods.size(); i++) {
                Method method = messageMethods.get(i);
                String endfix = "_" + method.getName() + "_" + (i > 9 ? i : ("0" + i));
                String newDynSuperMessageFullName = newDynMessageFullName + (method == wildcardMethod ? "" : endfix);
                cw2.visitInnerClass(
                        newDynSuperMessageFullName,
                        newDynName,
                        newDynMessageSimpleName + endfix,
                        ACC_PUBLIC + ACC_STATIC);
            }

            { // 构造函数
                mv = new MethodDebugVisitor(cw2.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null));
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }

            { // accept函数
                mv = new MethodDebugVisitor(
                        cw2.visitMethod(ACC_PUBLIC, "accept", "(" + wsDesc + "Ljava/lang/Object;)V", null, null));
                mv.visitVarInsn(ALOAD, 1);
                mv.visitTypeInsn(CHECKCAST, newDynWebSokcetFullName);
                mv.visitVarInsn(ASTORE, 3);

                mv.visitVarInsn(ALOAD, 2);
                mv.visitTypeInsn(CHECKCAST, newDynMessageFullName);
                mv.visitVarInsn(ASTORE, 4);

                for (int i = 0; i < messageMethods.size(); i++) {
                    final Method method = messageMethods.get(i);
                    String endfix = "_" + method.getName() + "_" + (i > 9 ? i : ("0" + i));
                    String newDynSuperMessageFullName =
                            newDynMessageFullName + (method == wildcardMethod ? "" : endfix);
                    final String messagename =
                            method.getAnnotation(RestOnMessage.class).name();
                    if (method == wildcardMethod) {
                        mv.visitVarInsn(ALOAD, 4);
                        mv.visitVarInsn(ALOAD, 3);
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL,
                                newDynSuperMessageFullName,
                                "execute",
                                "(L" + newDynWebSokcetFullName + ";)V",
                                false);
                    } else {
                        mv.visitVarInsn(ALOAD, 4);
                        mv.visitFieldInsn(
                                GETFIELD, newDynMessageFullName, messagename, "L" + newDynSuperMessageFullName + ";");
                        Label ifLabel = new Label();
                        mv.visitJumpInsn(IFNULL, ifLabel);

                        mv.visitVarInsn(ALOAD, 4);
                        mv.visitFieldInsn(
                                GETFIELD, newDynMessageFullName, messagename, "L" + newDynSuperMessageFullName + ";");
                        mv.visitVarInsn(ALOAD, 3);
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL,
                                newDynSuperMessageFullName,
                                "execute",
                                "(L" + newDynWebSokcetFullName + ";)V",
                                false);
                        mv.visitInsn(RETURN);
                        mv.visitLabel(ifLabel);
                    }
                }
                mv.visitInsn(RETURN);
                mv.visitMaxs(3, 3 + messageMethods.size());
                mv.visitEnd();
            }
            { // 虚拟accept函数
                mv = new MethodDebugVisitor(cw2.visitMethod(
                        ACC_PUBLIC + ACC_BRIDGE + ACC_SYNTHETIC,
                        "accept",
                        "(Ljava/lang/Object;Ljava/lang/Object;)V",
                        null,
                        null));
                mv.visitVarInsn(ALOAD, 0);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitTypeInsn(CHECKCAST, WebSocket.class.getName().replace('.', '/'));
                mv.visitVarInsn(ALOAD, 2);
                mv.visitTypeInsn(CHECKCAST, "java/lang/Object");
                mv.visitMethodInsn(
                        INVOKEVIRTUAL, newDynConsumerFullName, "accept", "(" + wsDesc + "Ljava/lang/Object;)V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(3, 3);
                mv.visitEnd();
            }
            cw2.visitEnd();
            byte[] bytes = cw2.toByteArray();
            classLoader.loadClass(newDynConsumerFullName.replace('/', '.'), bytes);
        }
        cw.visitEnd();

        byte[] bytes = cw.toByteArray();
        Class<?> newClazz = classLoader.loadClass(newDynName.replace('/', '.'), bytes);
        RedkaleClassLoader.putReflectionDeclaredConstructors(newClazz, newDynName.replace('/', '.'));
        JsonFactory.root().loadDecoder(newClazz.getAnnotation(RestDyn.class).types()[2]); // 固定Message类

        RedkaleClassLoader.putReflectionPublicMethods(webSocketType.getName());
        Class cwt = webSocketType;
        do {
            RedkaleClassLoader.putReflectionDeclaredFields(cwt.getName());
        } while ((cwt = cwt.getSuperclass()) != Object.class);
        RedkaleClassLoader.putReflectionDeclaredConstructors(webSocketType, webSocketType.getName());

        try {
            T servlet = (T) newClazz.getDeclaredConstructor().newInstance();
            Field field = newClazz.getField("_redkale_annotations");
            field.set(null, msgclassToAnnotations);
            RedkaleClassLoader.putReflectionField(newDynName.replace('/', '.'), field);
            if (rws.cryptor() != Cryptor.class) {
                RedkaleClassLoader.putReflectionDeclaredConstructors(
                        rws.cryptor(), rws.cryptor().getName());
                Cryptor cryptor = rws.cryptor().getDeclaredConstructor().newInstance();
                Field cryptorField = newClazz.getSuperclass().getDeclaredField("cryptor"); // WebSocketServlet
                RedkaleClassLoader.putReflectionField(newDynName.replace('/', '.'), cryptorField);
                cryptorField.setAccessible(true);
                cryptorField.set(servlet, cryptor);
            }
            if (messageAgent != null) {
                ((WebSocketServlet) servlet).messageAgent = messageAgent;
            }
            return servlet;
        } catch (Exception e) {
            throw new RestException(e);
        }
    }

    public static <T extends HttpServlet> T createRestServlet(
            final RedkaleClassLoader classLoader,
            final Class userType0,
            final Class<T> baseServletType,
            final Class<? extends Service> serviceType,
            String serviceResourceName) {

        if (baseServletType == null || serviceType == null) {
            throw new RestException(" Servlet or Service is null Class on createRestServlet");
        }
        if (!HttpServlet.class.isAssignableFrom(baseServletType)) {
            throw new RestException(baseServletType + " is not HttpServlet Class on createRestServlet");
        }
        int parentMod = baseServletType.getModifiers();
        if (!java.lang.reflect.Modifier.isPublic(parentMod)) {
            throw new RestException(baseServletType + " is not Public Class on createRestServlet");
        }
        Boolean parentNon0 = null;
        {
            NonBlocking snon = serviceType.getAnnotation(NonBlocking.class);
            parentNon0 = snon == null ? null : snon.value();
            if (HttpServlet.class != baseServletType) {
                Boolean preNonBlocking = null;
                Boolean authNonBlocking = null;
                RedkaleClassLoader.putReflectionDeclaredMethods(baseServletType.getName());
                for (Method m : baseServletType.getDeclaredMethods()) {
                    if (java.lang.reflect.Modifier.isAbstract(parentMod)
                            && java.lang.reflect.Modifier.isAbstract(m.getModifiers())) { // @since 2.4.0
                        throw new RestException(
                                baseServletType + " cannot contains a abstract Method on " + baseServletType);
                    }
                    Class[] paramTypes = m.getParameterTypes();
                    if (paramTypes.length != 2
                            || paramTypes[0] != HttpRequest.class
                            || paramTypes[1] != HttpResponse.class) {
                        continue;
                    }
                    // -----------------------------------------------
                    Class[] exps = m.getExceptionTypes();
                    if (exps.length > 0 && (exps.length != 1 || exps[0] != IOException.class)) {
                        continue;
                    }
                    // -----------------------------------------------
                    String methodName = m.getName();
                    if ("preExecute".equals(methodName)) {
                        if (preNonBlocking == null) {
                            NonBlocking non = m.getAnnotation(NonBlocking.class);
                            preNonBlocking = non != null && non.value();
                        }
                        continue;
                    }
                    if ("authenticate".equals(methodName)) {
                        if (authNonBlocking == null) {
                            NonBlocking non = m.getAnnotation(NonBlocking.class);
                            authNonBlocking = non != null && non.value();
                        }
                        continue;
                    }
                }
                if (preNonBlocking != null && !preNonBlocking) {
                    parentNon0 = false;
                } else if (authNonBlocking != null && !authNonBlocking) {
                    parentNon0 = false;
                } else {
                    NonBlocking bnon = baseServletType.getAnnotation(NonBlocking.class);
                    if (bnon != null && !bnon.value()) {
                        parentNon0 = false;
                    }
                }
            }
        }

        final String restInternalName = Type.getInternalName(Rest.class);
        final String serviceDesc = Type.getDescriptor(serviceType);
        final String webServletDesc = Type.getDescriptor(WebServlet.class);
        final String resDesc = Type.getDescriptor(Resource.class);
        final String reqDesc = Type.getDescriptor(HttpRequest.class);
        final String respDesc = Type.getDescriptor(HttpResponse.class);
        final String convertDesc = Type.getDescriptor(Convert.class);
        final String nonblockDesc = Type.getDescriptor(NonBlocking.class);
        final String typeDesc = Type.getDescriptor(java.lang.reflect.Type.class);
        final String retDesc = Type.getDescriptor(RetResult.class);
        final String httpResultDesc = Type.getDescriptor(HttpResult.class);
        final String httpScopeDesc = Type.getDescriptor(HttpScope.class);
        final String stageDesc = Type.getDescriptor(CompletionStage.class);
        final String httpHeadersDesc = Type.getDescriptor(HttpHeaders.class);
        final String httpParametersDesc = Type.getDescriptor(HttpParameters.class);
        final String flipperDesc = Type.getDescriptor(Flipper.class);
        final String httpServletName = HttpServlet.class.getName().replace('.', '/');
        final String actionEntryName = HttpServlet.ActionEntry.class.getName().replace('.', '/');
        final String attrDesc = Type.getDescriptor(org.redkale.util.Attribute.class);
        final String multiContextDesc = Type.getDescriptor(MultiContext.class);
        final String multiContextName = MultiContext.class.getName().replace('.', '/');
        final String mappingDesc = Type.getDescriptor(HttpMapping.class);
        final String restConvertDesc = Type.getDescriptor(RestConvert.class);
        final String restConvertsDesc = Type.getDescriptor(RestConvert.RestConverts.class);
        final String restConvertCoderDesc = Type.getDescriptor(RestConvertCoder.class);
        final String restConvertCodersDesc = Type.getDescriptor(RestConvertCoder.RestConvertCoders.class);
        final String httpParamDesc = Type.getDescriptor(HttpParam.class);
        final String httpParamsDesc = Type.getDescriptor(HttpParam.HttpParams.class);
        final String sourcetypeDesc = Type.getDescriptor(HttpParam.HttpParameterStyle.class);

        final String reqInternalName = Type.getInternalName(HttpRequest.class);
        final String respInternalName = Type.getInternalName(HttpResponse.class);
        final String attrInternalName = Type.getInternalName(org.redkale.util.Attribute.class);
        final String retInternalName = Type.getInternalName(RetResult.class);
        final String serviceTypeInternalName = Type.getInternalName(serviceType);

        HttpUserType hut = baseServletType.getAnnotation(HttpUserType.class);
        final Class userType =
                (userType0 == null || userType0 == Object.class) ? (hut == null ? null : hut.value()) : userType0;
        if (userType != null
                && (userType.isPrimitive()
                        || userType.getName().startsWith("java.")
                        || userType.getName().startsWith("javax."))) {
            throw new RestException(HttpUserType.class.getSimpleName() + " must be a JavaBean but found " + userType);
        }

        final String supDynName = baseServletType.getName().replace('.', '/');
        final RestService controller = serviceType.getAnnotation(RestService.class);
        if (controller != null && controller.ignore()) {
            throw new RestException(serviceType + " is ignore Rest Service Class"); // 标记为ignore=true不创建Servlet
        }
        final boolean serRpcOnly = controller != null && controller.rpcOnly();
        final Boolean parentNonBlocking = parentNon0;

        String stname = serviceType.getSimpleName();
        if (stname.startsWith("Service")) { // 类似ServiceWatchService这样的类保留第一个Service字样
            stname = "Service" + stname.substring("Service".length()).replaceAll("Service.*$", "");
        } else {
            stname = stname.replaceAll("Service.*$", "");
        }
        String namePostfix = Utility.isBlank(serviceResourceName) ? "" : serviceResourceName;
        for (char ch : namePostfix.toCharArray()) {
            if ((ch == '$'
                    || ch == '_'
                    || (ch >= '0' && ch <= '9')
                    || (ch >= 'a' && ch <= 'z')
                    || (ch >= 'A' && ch <= 'Z'))) {
                continue;
            }
            // 带特殊字符的值不能作为类名的后缀
            namePostfix = Utility.md5Hex(namePostfix);
            break;
        }
        // String newDynName = serviceTypeInternalName.substring(0, serviceTypeInternalName.lastIndexOf('/') + 1) +
        // "_Dyn" + stname + "RestServlet";
        final String newDynName = "org/redkaledyn/http/rest/" + "_Dyn" + stname + "RestServlet__"
                + serviceType.getName().replace('.', '_').replace('$', '_')
                + (namePostfix.isEmpty() ? "" : ("_" + namePostfix) + "DynServlet");

        try {
            Class newClazz = classLoader.loadClass(newDynName.replace('/', '.'));
            T obj = (T) newClazz.getDeclaredConstructor().newInstance();

            final String defModuleName = getWebModuleNameLowerCase(serviceType);
            final String bigModuleName = getWebModuleName(serviceType);
            final Map<String, Object> classMap = new LinkedHashMap<>();

            final List<MappingEntry> entrys = new ArrayList<>();
            final List<Annotation[]> methodAnns = new ArrayList<>();
            final List<java.lang.reflect.Type[]> paramTypes = new ArrayList<>();
            final List<java.lang.reflect.Type> retvalTypes = new ArrayList<>();

            final List<Object[]> restConverts = new ArrayList<>();
            final Map<java.lang.reflect.Type, String> typeRefs = new LinkedHashMap<>();
            final Map<String, Method> mappingUrlToMethod = new HashMap<>();
            final Map<String, org.redkale.util.Attribute> restAttributes = new LinkedHashMap<>();
            final Map<String, java.lang.reflect.Type> bodyTypes = new HashMap<>();

            { // entrys、paramTypes赋值
                final Method[] allMethods = serviceType.getMethods();
                Arrays.sort(allMethods, (m1, m2) -> { // 必须排序，否则paramTypes顺序容易乱
                    int s = m1.getName().compareTo(m2.getName());
                    if (s != 0) {
                        return s;
                    }
                    s = Arrays.toString(m1.getParameterTypes()).compareTo(Arrays.toString(m2.getParameterTypes()));
                    return s;
                });
                int methodIdex = 0;
                for (final Method method : allMethods) {
                    if (Modifier.isStatic(method.getModifiers())) {
                        continue;
                    }
                    if (method.isSynthetic()) {
                        continue;
                    }
                    if (EXCLUDERMETHODS.contains(method.getName())) {
                        continue;
                    }
                    if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == AnyValue.class) {
                        if ("init".equals(method.getName())) {
                            continue;
                        }
                        if ("destroy".equals(method.getName())) {
                            continue;
                        }
                    }
                    if (controller == null) {
                        continue;
                    }

                    List<MappingAnn> mappings = MappingAnn.paraseMappingAnns(controller, method);
                    if (mappings == null) {
                        continue;
                    }
                    methodAnns.add(method.getAnnotations());
                    java.lang.reflect.Type[] ptypes =
                            TypeToken.getGenericType(method.getGenericParameterTypes(), serviceType);
                    for (java.lang.reflect.Type t : ptypes) {
                        if (!TypeToken.isClassType(t)) {
                            throw new RedkaleException("param type (" + t + ") is not a class in method " + method
                                    + ", serviceType is " + serviceType.getName());
                        }
                    }
                    paramTypes.add(ptypes);
                    java.lang.reflect.Type rtype = formatRestReturnType(method, serviceType);
                    if (!TypeToken.isClassType(rtype)) {
                        throw new RedkaleException("return type (" + rtype + ") is not a class in method " + method
                                + ", serviceType is " + serviceType.getName());
                    }
                    retvalTypes.add(rtype);

                    if (mappings.isEmpty()) { // 没有Mapping，设置一个默认值
                        MappingEntry entry = new MappingEntry(
                                serRpcOnly,
                                methodIdex,
                                parentNonBlocking,
                                new MappingAnn(method, MappingEntry.DEFAULT__MAPPING),
                                bigModuleName,
                                method);
                        entrys.add(entry);
                    } else {
                        for (MappingAnn ann : mappings) {
                            MappingEntry entry = new MappingEntry(
                                    serRpcOnly, methodIdex, parentNonBlocking, ann, defModuleName, method);
                            entrys.add(entry);
                        }
                    }
                    methodIdex++;
                }
                Collections.sort(entrys);
            }
            { // restConverts、typeRefs、mappingUrlToMethod、restAttributes、bodyTypes赋值
                final int headIndex = 10;
                for (final MappingEntry entry : entrys) {
                    mappingUrlToMethod.put(entry.mappingurl, entry.mappingMethod);
                    final Method method = entry.mappingMethod;
                    final Class returnType = method.getReturnType();
                    final Parameter[] params = method.getParameters();
                    final RestConvert[] rcs = method.getAnnotationsByType(RestConvert.class);
                    final RestConvertCoder[] rcc = method.getAnnotationsByType(RestConvertCoder.class);
                    final boolean hasResConvert = Utility.isNotEmpty(rcs) || Utility.isNotEmpty(rcc);
                    if (hasResConvert) {
                        restConverts.add(new Object[] {rcs, rcc});
                    }
                    // 解析方法中的每个参数
                    List<Object[]> paramlist = new ArrayList<>();
                    for (int i = 0; i < params.length; i++) {
                        final Parameter param = params[i];
                        final Class ptype = param.getType();
                        String n = null;
                        String comment = "";
                        boolean required = true;
                        int radix = 10;
                        RestHeader annhead = param.getAnnotation(RestHeader.class);
                        if (annhead != null) {
                            n = annhead.name();
                            radix = annhead.radix();
                            comment = annhead.comment();
                            required = annhead.required();
                        }
                        RestCookie anncookie = param.getAnnotation(RestCookie.class);
                        if (anncookie != null) {
                            n = anncookie.name();
                            radix = anncookie.radix();
                            comment = anncookie.comment();
                        }
                        RestSessionid annsid = param.getAnnotation(RestSessionid.class);
                        RestAddress annaddr = param.getAnnotation(RestAddress.class);
                        if (annaddr != null) {
                            comment = annaddr.comment();
                        }
                        RestLocale annlocale = param.getAnnotation(RestLocale.class);
                        if (annlocale != null) {
                            comment = annlocale.comment();
                        }
                        RestBody annbody = param.getAnnotation(RestBody.class);
                        if (annbody != null) {
                            comment = annbody.comment();
                        }
                        RestUploadFile annfile = param.getAnnotation(RestUploadFile.class);
                        if (annfile != null) {
                            comment = annfile.comment();
                        }
                        RestPath annpath = param.getAnnotation(RestPath.class);
                        if (annpath != null) {
                            comment = annpath.comment();
                        }
                        RestUserid userid = param.getAnnotation(RestUserid.class);

                        if (userid != null) {
                            comment = "";
                        }
                        boolean annheaders = param.getType() == RestHeaders.class;
                        if (annheaders) {
                            comment = "";
                            n = "^"; // Http头信息类型特殊处理
                        }
                        boolean annparams = param.getType() == RestParams.class;
                        if (annparams) {
                            comment = "";
                            n = "?"; // Http参数类型特殊处理
                        }
                        RestParam annpara = param.getAnnotation(RestParam.class);
                        if (annpara != null) {
                            radix = annpara.radix();
                        }
                        if (annpara != null) {
                            comment = annpara.comment();
                        }
                        if (annpara != null) {
                            required = annpara.required();
                        }
                        if (n == null) {
                            n = (annpara == null || annpara.name().isEmpty()) ? null : annpara.name();
                        }
                        if (n == null && ptype == userType) {
                            n = "&"; // 用户类型特殊处理
                        }
                        if (n == null) {
                            if (param.isNamePresent()) {
                                n = param.getName();
                            } else if (ptype == Flipper.class) {
                                n = "flipper";
                            }
                        } // n maybe is null

                        java.lang.reflect.Type paramtype =
                                TypeToken.getGenericType(param.getParameterizedType(), serviceType);
                        paramlist.add(new Object[] {
                            param,
                            n,
                            ptype,
                            radix,
                            comment,
                            required,
                            annpara,
                            annsid,
                            annaddr,
                            annlocale,
                            annhead,
                            anncookie,
                            annbody,
                            annfile,
                            annpath,
                            userid,
                            annheaders,
                            annparams,
                            paramtype
                        });
                    }
                    for (Object[] ps :
                            paramlist) { // {param, n, ptype, radix, comment, required, annpara, annsid, annaddr,
                        // annlocale, annhead, anncookie, annbody, annfile, annpath, annuserid,
                        // annheaders, annparams, paramtype}
                        final boolean isuserid = ((RestUserid) ps[headIndex + 5]) != null; // 是否取userid
                        if ((ps[1] != null && ps[1].toString().indexOf('&') >= 0) || isuserid) {
                            continue; // @RestUserid 不需要生成 @HttpParam
                        }
                        if (((RestAddress) ps[8]) != null) {
                            continue; // @RestAddress 不需要生成 @HttpParam
                        }
                        java.lang.reflect.Type pgtype =
                                TypeToken.getGenericType(((Parameter) ps[0]).getParameterizedType(), serviceType);
                        if (pgtype != (Class) ps[2]) {
                            String refid = typeRefs.get(pgtype);
                            if (refid == null) {
                                refid = "_typeref_" + typeRefs.size();
                                typeRefs.put(pgtype, refid);
                            }
                        }

                        final Parameter param = (Parameter) ps[0]; // 参数类型
                        String pname = (String) ps[1]; // 参数名
                        Class ptype = (Class) ps[2]; // 参数类型
                        int radix = (Integer) ps[3];
                        String comment = (String) ps[4];
                        boolean required = (Boolean) ps[5];
                        RestParam annpara = (RestParam) ps[6];
                        RestSessionid annsid = (RestSessionid) ps[7];
                        RestAddress annaddr = (RestAddress) ps[8];
                        RestLocale annlocale = (RestLocale) ps[9];
                        RestHeader annhead = (RestHeader) ps[headIndex];
                        RestCookie anncookie = (RestCookie) ps[headIndex + 1];
                        RestBody annbody = (RestBody) ps[headIndex + 2];
                        RestUploadFile annfile = (RestUploadFile) ps[headIndex + 3];
                        RestPath annpath = (RestPath) ps[headIndex + 4];
                        RestUserid annuserid = (RestUserid) ps[headIndex + 5];
                        boolean annheaders = (Boolean) ps[headIndex + 6];
                        boolean annparams = (Boolean) ps[headIndex + 7];

                        if (CompletionHandler.class.isAssignableFrom(
                                ptype)) { // HttpResponse.createAsyncHandler() or HttpResponse.createAsyncHandler(Class)
                        } else if (annsid != null) { // HttpRequest.getSessionid(true|false)
                        } else if (annaddr != null) { // HttpRequest.getRemoteAddr
                        } else if (annlocale != null) { // HttpRequest.getLocale
                        } else if (annbody != null) { // HttpRequest.getBodyUTF8 / HttpRequest.getBody
                        } else if (annfile != null) { // MultiContext.partsFirstBytes / HttpRequest.partsFirstFile /
                            // HttpRequest.partsFiles
                        } else if (annpath != null) { // HttpRequest.getRequestPath
                        } else if (annuserid != null) { // HttpRequest.currentUserid
                        } else if (pname != null && pname.charAt(0) == '#') { // 从request.getPathParam 中去参数
                        } else if ("#".equals(pname)) { // 从request.getRequstURI 中取参数
                        } else if ("&".equals(pname) && ptype == userType) { // 当前用户对象的类名
                        } else if ("^".equals(pname) && annheaders) { // HttpRequest.getHeaders Http头信息
                        } else if ("?".equals(pname) && annparams) { // HttpRequest.getParameters Http参数信息
                        } else if (ptype.isPrimitive()) {
                            // do nothing
                        } else if (ptype == String.class) {
                            // do nothing
                        } else if (ptype == Flipper.class) {
                            // do nothing
                        } else { // 其他Json对象
                            // 构建 RestHeader、RestCookie、RestAddress 等赋值操作
                            Class loop = ptype;
                            Set<String> fields = new HashSet<>();
                            Map<String, Object[]> attrParaNames = new LinkedHashMap<>();
                            do {
                                if (loop == null || loop.isInterface()) {
                                    break; // 接口时getSuperclass可能会得到null
                                }
                                for (Field field : loop.getDeclaredFields()) {
                                    if (Modifier.isStatic(field.getModifiers())) {
                                        continue;
                                    }
                                    if (Modifier.isFinal(field.getModifiers())) {
                                        continue;
                                    }
                                    if (fields.contains(field.getName())) {
                                        continue;
                                    }
                                    RestHeader rh = field.getAnnotation(RestHeader.class);
                                    RestCookie rc = field.getAnnotation(RestCookie.class);
                                    RestSessionid rs = field.getAnnotation(RestSessionid.class);
                                    RestAddress ra = field.getAnnotation(RestAddress.class);
                                    RestLocale rl = field.getAnnotation(RestLocale.class);
                                    RestBody rb = field.getAnnotation(RestBody.class);
                                    RestUploadFile ru = field.getAnnotation(RestUploadFile.class);
                                    RestPath ri = field.getAnnotation(RestPath.class);
                                    if (rh == null
                                            && rc == null
                                            && ra == null
                                            && rl == null
                                            && rb == null
                                            && rs == null
                                            && ru == null
                                            && ri == null) {
                                        continue;
                                    }

                                    org.redkale.util.Attribute attr = org.redkale.util.Attribute.create(loop, field);
                                    String attrFieldName;
                                    String restname = "";
                                    if (rh != null) {
                                        attrFieldName = "_redkale_attr_header_"
                                                + (field.getType() != String.class ? "json_" : "")
                                                + restAttributes.size();
                                        restname = rh.name();
                                    } else if (rc != null) {
                                        attrFieldName = "_redkale_attr_cookie_" + restAttributes.size();
                                        restname = rc.name();
                                    } else if (rs != null) {
                                        attrFieldName = "_redkale_attr_sessionid_" + restAttributes.size();
                                        restname = rs.create() ? "1" : ""; // 用于下面区分create值
                                    } else if (ra != null) {
                                        attrFieldName = "_redkale_attr_address_" + restAttributes.size();
                                        // restname = "";
                                    } else if (rl != null) {
                                        attrFieldName = "_redkale_attr_locale_" + restAttributes.size();
                                        // restname = "";
                                    } else if (rb != null && field.getType() == String.class) {
                                        attrFieldName = "_redkale_attr_bodystring_" + restAttributes.size();
                                        // restname = "";
                                    } else if (rb != null && field.getType() == byte[].class) {
                                        attrFieldName = "_redkale_attr_bodybytes_" + restAttributes.size();
                                        // restname = "";
                                    } else if (rb != null
                                            && field.getType() != String.class
                                            && field.getType() != byte[].class) {
                                        attrFieldName = "_redkale_attr_bodyjson_" + restAttributes.size();
                                        // restname = "";
                                    } else if (ru != null && field.getType() == byte[].class) {
                                        attrFieldName = "_redkale_attr_uploadbytes_" + restAttributes.size();
                                        // restname = "";
                                    } else if (ru != null && field.getType() == File.class) {
                                        attrFieldName = "_redkale_attr_uploadfile_" + restAttributes.size();
                                        // restname = "";
                                    } else if (ru != null && field.getType() == File[].class) {
                                        attrFieldName = "_redkale_attr_uploadfiles_" + restAttributes.size();
                                        // restname = "";
                                    } else if (ri != null && field.getType() == String.class) {
                                        attrFieldName = "_redkale_attr_uri_" + restAttributes.size();
                                        // restname = "";
                                    } else {
                                        continue;
                                    }
                                    restAttributes.put(attrFieldName, attr);
                                    attrParaNames.put(
                                            attrFieldName,
                                            new Object[] {restname, field.getType(), field.getGenericType(), ru});
                                    fields.add(field.getName());
                                }
                            } while ((loop = loop.getSuperclass()) != Object.class);

                            if (!attrParaNames
                                    .isEmpty()) { // 参数存在 RestHeader、RestCookie、RestSessionid、RestAddress、RestBody字段
                                for (Map.Entry<String, Object[]> en : attrParaNames.entrySet()) {
                                    if (en.getKey().contains("_header_")) {
                                        String headerkey = en.getValue()[0].toString();
                                        if ("Host".equalsIgnoreCase(headerkey)) {
                                            // do nothing
                                        } else if ("Content-Type".equalsIgnoreCase(headerkey)) {
                                            // do nothing
                                        } else if ("Connection".equalsIgnoreCase(headerkey)) {
                                            // do nothing
                                        } else if ("Method".equalsIgnoreCase(headerkey)) {
                                            // do nothing
                                        } else if (en.getKey().contains("_header_json_")) {
                                            String typefieldname = "_redkale_body_jsontype_" + bodyTypes.size();
                                            bodyTypes.put(typefieldname, (java.lang.reflect.Type) en.getValue()[2]);
                                        }
                                    } else if (en.getKey().contains("_cookie_")) {
                                        // do nothing
                                    } else if (en.getKey().contains("_sessionid_")) {
                                        // do nothing
                                    } else if (en.getKey().contains("_address_")) {
                                        // do nothing
                                    } else if (en.getKey().contains("_locale_")) {
                                        // do nothing
                                    } else if (en.getKey().contains("_uri_")) {
                                        // do nothing
                                    } else if (en.getKey().contains("_bodystring_")) {
                                        // do nothing
                                    } else if (en.getKey().contains("_bodybytes_")) {
                                        // do nothing
                                    } else if (en.getKey().contains("_bodyjson_")) { // JavaBean 转 Json
                                        String typefieldname = "_redkale_body_jsontype_" + bodyTypes.size();
                                        bodyTypes.put(typefieldname, (java.lang.reflect.Type) en.getValue()[2]);
                                    } else if (en.getKey().contains("_uploadbytes_")) {
                                        // 只需mv.visitVarInsn(ALOAD, 4), 无需处理
                                    } else if (en.getKey().contains("_uploadfile_")) {
                                        // 只需mv.visitVarInsn(ALOAD, 4), 无需处理
                                    } else if (en.getKey().contains("_uploadfiles_")) {
                                        // 只需mv.visitVarInsn(ALOAD, 4), 无需处理
                                    }
                                }
                            }
                        }
                    }
                    java.lang.reflect.Type grt = TypeToken.getGenericType(method.getGenericReturnType(), serviceType);
                    Class rtc = returnType;
                    if (rtc == void.class) {
                        rtc = RetResult.class;
                        grt = TYPE_RETRESULT_STRING;
                    } else if (CompletionStage.class.isAssignableFrom(returnType)) {
                        ParameterizedType ptgrt = (ParameterizedType) grt;
                        grt = ptgrt.getActualTypeArguments()[0];
                        rtc = TypeToken.typeToClass(grt);
                        if (rtc == null) {
                            rtc = Object.class; // 应该不会发生吧?
                        }
                    } else if (Flows.maybePublisherClass(returnType)) {
                        java.lang.reflect.Type grt0 = Flows.maybePublisherSubType(grt);
                        if (grt0 != null) {
                            grt = grt0;
                        }
                    }
                    if (grt != rtc) {
                        String refid = typeRefs.get(grt);
                        if (refid == null) {
                            refid = "_typeref_" + typeRefs.size();
                            typeRefs.put(grt, refid);
                        }
                    }
                }
            }
            for (Map.Entry<java.lang.reflect.Type, String> en : typeRefs.entrySet()) {
                Field refField = newClazz.getDeclaredField(en.getValue());
                refField.setAccessible(true);
                refField.set(obj, en.getKey());
            }
            for (Map.Entry<String, org.redkale.util.Attribute> en : restAttributes.entrySet()) {
                Field attrField = newClazz.getDeclaredField(en.getKey());
                attrField.setAccessible(true);
                attrField.set(obj, en.getValue());
            }
            for (Map.Entry<String, java.lang.reflect.Type> en : bodyTypes.entrySet()) {
                Field genField = newClazz.getDeclaredField(en.getKey());
                genField.setAccessible(true);
                genField.set(obj, en.getValue());
            }
            for (int i = 0; i < restConverts.size(); i++) {
                Field genField = newClazz.getDeclaredField(REST_CONVERT_FIELD_PREFIX + (i + 1));
                genField.setAccessible(true);
                Object[] rc = restConverts.get(i);
                JsonFactory childFactory = createJsonFactory((RestConvert[]) rc[0], (RestConvertCoder[]) rc[1]);
                genField.set(obj, childFactory.getConvert());
            }
            Field annsfield = newClazz.getDeclaredField(REST_METHOD_ANNS_NAME);
            annsfield.setAccessible(true);
            Annotation[][] methodAnnArray = new Annotation[methodAnns.size()][];
            methodAnnArray = methodAnns.toArray(methodAnnArray);
            annsfield.set(obj, methodAnnArray);

            Field typesfield = newClazz.getDeclaredField(REST_PARAMTYPES_FIELD_NAME);
            typesfield.setAccessible(true);
            java.lang.reflect.Type[][] paramtypeArray = new java.lang.reflect.Type[paramTypes.size()][];
            paramtypeArray = paramTypes.toArray(paramtypeArray);
            typesfield.set(obj, paramtypeArray);

            Field retfield = newClazz.getDeclaredField(REST_RETURNTYPES_FIELD_NAME);
            retfield.setAccessible(true);
            java.lang.reflect.Type[] rettypeArray = new java.lang.reflect.Type[retvalTypes.size()];
            rettypeArray = retvalTypes.toArray(rettypeArray);
            retfield.set(obj, rettypeArray);

            Field tostringfield = newClazz.getDeclaredField(REST_TOSTRINGOBJ_FIELD_NAME);
            tostringfield.setAccessible(true);
            { // 注入 @WebServlet 注解
                String urlpath = "";
                final String defmodulename = getWebModuleNameLowerCase(serviceType);
                final int moduleid = controller == null ? 0 : controller.moduleid();
                boolean repair = controller == null || controller.repair();
                final String catalog = controller == null ? "" : controller.catalog();

                boolean pound = false;
                for (MappingEntry entry : entrys) {
                    if (entry.existsPound) {
                        pound = true;
                        break;
                    }
                }
                if (defmodulename.isEmpty() || (!pound && entrys.size() <= 2)) {
                    Set<String> startWiths = new HashSet<>();
                    for (MappingEntry entry : entrys) {
                        String suburl = (catalog.isEmpty() ? "/" : ("/" + catalog + "/"))
                                + (defmodulename.isEmpty() ? "" : (defmodulename + "/"))
                                + entry.name;
                        if ("//".equals(suburl)) {
                            suburl = "/";
                        } else if (suburl.length() > 2 && suburl.endsWith("/")) {
                            startWiths.add(suburl);
                            suburl += "*";
                        } else {
                            boolean match = false;
                            for (String s : startWiths) {
                                if (suburl.startsWith(s)) {
                                    match = true;
                                    break;
                                }
                            }
                            if (match) {
                                continue;
                            }
                        }
                        urlpath += "," + suburl;
                    }
                    if (urlpath.length() > 0) {
                        urlpath = urlpath.substring(1);
                    }
                } else {
                    urlpath = (catalog.isEmpty() ? "/" : ("/" + catalog + '/')) + defmodulename + "/*";
                }

                classMap.put("type", serviceType.getName());
                classMap.put("url", urlpath);
                classMap.put("moduleid", moduleid);
                classMap.put("repair", repair);
                // classMap.put("comment", comment); //不显示太多信息
            }
            java.util.function.Supplier<String> sSupplier =
                    () -> JsonConvert.root().convertTo(classMap);
            tostringfield.set(obj, sSupplier);

            Method restactMethod = newClazz.getDeclaredMethod("_createRestActionEntry");
            restactMethod.setAccessible(true);
            Field tmpEntrysField = HttpServlet.class.getDeclaredField("_actionmap");
            tmpEntrysField.setAccessible(true);
            HashMap<String, HttpServlet.ActionEntry> innerEntryMap = (HashMap) restactMethod.invoke(obj);
            for (Map.Entry<String, HttpServlet.ActionEntry> en : innerEntryMap.entrySet()) {
                Method m = mappingUrlToMethod.get(en.getKey());
                if (m != null) {
                    en.getValue().annotations = HttpServlet.ActionEntry.annotations(m);
                }
            }
            tmpEntrysField.set(obj, innerEntryMap);
            Field nonblockField = Servlet.class.getDeclaredField("_nonBlocking");
            nonblockField.setAccessible(true);
            nonblockField.set(obj, parentNonBlocking == null || parentNonBlocking);
            return obj;
        } catch (ClassNotFoundException e) {
            // do nothing
        } catch (Throwable e) {
            // do nothing
        }
        // ------------------------------------------------------------------------------
        final String defModuleName = getWebModuleNameLowerCase(serviceType);
        final String bigModuleName = getWebModuleName(serviceType);
        final String catalog = controller == null ? "" : controller.catalog();
        final String httpDesc = Type.getDescriptor(HttpServlet.class);
        if (!checkName(catalog)) {
            throw new RestException(serviceType.getName() + " have illegal " + RestService.class.getSimpleName()
                    + ".catalog, only 0-9 a-z A-Z _ cannot begin 0-9");
        }
        if (!checkName(defModuleName)) {
            throw new RestException(serviceType.getName() + " have illegal " + RestService.class.getSimpleName()
                    + ".value, only 0-9 a-z A-Z _ cannot begin 0-9");
        }
        ClassWriter cw = new ClassWriter(COMPUTE_FRAMES);
        FieldVisitor fv;
        MethodDebugVisitor mv;
        AnnotationVisitor av0;
        final List<MappingEntry> entrys = new ArrayList<>();
        final Map<String, org.redkale.util.Attribute> restAttributes = new LinkedHashMap<>();
        final Map<String, Object> classMap = new LinkedHashMap<>();
        final Map<java.lang.reflect.Type, String> typeRefs = new LinkedHashMap<>();
        final List<Annotation[]> methodAnns = new ArrayList<>();
        final List<java.lang.reflect.Type[]> paramTypes = new ArrayList<>();
        final List<java.lang.reflect.Type> retvalTypes = new ArrayList<>();
        final Map<String, java.lang.reflect.Type> bodyTypes = new HashMap<>();
        final List<Object[]> restConverts = new ArrayList<>();
        final Map<String, Method> mappingurlToMethod = new HashMap<>();

        cw.visit(V11, ACC_PUBLIC + ACC_SUPER, newDynName, null, supDynName, null);

        { // RestDynSourceType
            av0 = cw.visitAnnotation(Type.getDescriptor(RestDynSourceType.class), true);
            av0.visit("value", Type.getType(Type.getDescriptor(serviceType)));
            av0.visitEnd();
        }
        boolean dynsimple = baseServletType == HttpServlet.class; // 有自定义的BaseServlet会存在读取header的操作
        // 获取所有可以转换成HttpMapping的方法
        int methodidex = 0;
        final Method[] allMethods = serviceType.getMethods();
        Arrays.sort(allMethods, (m1, m2) -> { // 必须排序，否则paramTypes顺序容易乱
            int s = m1.getName().compareTo(m2.getName());
            if (s != 0) {
                return s;
            }
            s = Arrays.toString(m1.getParameterTypes()).compareTo(Arrays.toString(m2.getParameterTypes()));
            return s;
        });
        for (final Method method : allMethods) {
            if (Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (method.isSynthetic()) {
                continue;
            }
            if (EXCLUDERMETHODS.contains(method.getName())) {
                continue;
            }
            if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == AnyValue.class) {
                if ("init".equals(method.getName())) {
                    continue;
                }
                if ("destroy".equals(method.getName())) {
                    continue;
                }
            }
            if (controller == null) {
                continue;
            }

            List<MappingAnn> mappings = MappingAnn.paraseMappingAnns(controller, method);
            if (mappings == null) {
                continue;
            }
            Class[] extypes = method.getExceptionTypes();
            if (extypes.length > 0) {
                for (Class exp : extypes) {
                    if (!RuntimeException.class.isAssignableFrom(exp) && !IOException.class.isAssignableFrom(exp)) {
                        throw new RestException("@" + RestMapping.class.getSimpleName() + " only for method(" + method
                                + ") with throws IOException");
                    }
                }
            }
            methodAnns.add(method.getAnnotations());
            java.lang.reflect.Type[] ptypes = TypeToken.getGenericType(method.getGenericParameterTypes(), serviceType);
            for (java.lang.reflect.Type t : ptypes) {
                if (!TypeToken.isClassType(t)) {
                    throw new RedkaleException("param type (" + t + ") is not a class in method " + method
                            + ", serviceType is " + serviceType.getName());
                }
            }
            paramTypes.add(ptypes);
            java.lang.reflect.Type rtype = formatRestReturnType(method, serviceType);
            if (!TypeToken.isClassType(rtype)) {
                throw new RedkaleException("return type (" + rtype + ") is not a class in method " + method
                        + ", serviceType is " + serviceType.getName());
            }
            retvalTypes.add(rtype);
            if (mappings.isEmpty()) { // 没有Mapping，设置一个默认值
                MappingEntry entry = new MappingEntry(
                        serRpcOnly,
                        methodidex,
                        parentNonBlocking,
                        new MappingAnn(method, MappingEntry.DEFAULT__MAPPING),
                        bigModuleName,
                        method);
                if (entrys.contains(entry)) {
                    throw new RestException(serviceType.getName() + " on " + method.getName() + " 's mapping("
                            + entry.name + ") is repeat");
                }
                entrys.add(entry);
            } else {
                for (MappingAnn ann : mappings) {
                    MappingEntry entry =
                            new MappingEntry(serRpcOnly, methodidex, parentNonBlocking, ann, defModuleName, method);
                    if (entrys.contains(entry)) {
                        throw new RestException(serviceType.getName() + " on " + method.getName() + " 's mapping("
                                + entry.name + ") is repeat");
                    }
                    entrys.add(entry);
                }
            }
            methodidex++;
        }
        if (entrys.isEmpty()) {
            return null; // 没有可HttpMapping的方法
        }
        Collections.sort(entrys);
        final int moduleid = controller == null ? 0 : controller.moduleid();
        { // 注入 @WebServlet 注解
            String urlpath = "";
            boolean repair = controller == null || controller.repair();
            String comment = controller == null ? "" : controller.comment();
            av0 = cw.visitAnnotation(webServletDesc, true);
            {
                AnnotationVisitor av1 = av0.visitArray("value");
                boolean pound = false;
                for (MappingEntry entry : entrys) {
                    if (entry.existsPound) {
                        pound = true;
                        break;
                    }
                }
                if (isEmpty(defModuleName) || (!pound && entrys.size() <= 2)) {
                    Set<String> startWiths = new HashSet<>();
                    for (MappingEntry entry : entrys) {
                        String suburl = (isEmpty(catalog) ? "/" : ("/" + catalog + "/"))
                                + (isEmpty(defModuleName) ? "" : (defModuleName + "/"))
                                + entry.name;
                        if ("//".equals(suburl)) {
                            suburl = "/";
                        } else if (suburl.length() > 2 && suburl.endsWith("/")) {
                            startWiths.add(suburl);
                            suburl += "*";
                        } else {
                            boolean match = false;
                            for (String s : startWiths) {
                                if (suburl.startsWith(s)) {
                                    match = true;
                                    break;
                                }
                            }
                            if (match) {
                                continue;
                            }
                        }
                        urlpath += "," + suburl;
                        av1.visit(null, suburl);
                    }
                    if (urlpath.length() > 0) {
                        urlpath = urlpath.substring(1);
                    }
                } else {
                    urlpath = (catalog.isEmpty() ? "/" : ("/" + catalog + "/")) + defModuleName + "/*";
                    av1.visit(null, urlpath);
                }
                av1.visitEnd();
            }
            av0.visit("name", defModuleName);
            av0.visit("moduleid", moduleid);
            av0.visit("repair", repair);
            av0.visit("comment", comment);
            av0.visitEnd();
            classMap.put("type", serviceType.getName());
            classMap.put("url", urlpath);
            classMap.put("moduleid", moduleid);
            classMap.put("repair", repair);
            // classMap.put("comment", comment); //不显示太多信息
        }
        { // NonBlocking
            av0 = cw.visitAnnotation(nonblockDesc, true);
            av0.visit("value", true);
            av0.visitEnd();
        }
        { // 内部类
            cw.visitInnerClass(
                    actionEntryName,
                    httpServletName,
                    HttpServlet.ActionEntry.class.getSimpleName(),
                    ACC_PROTECTED + ACC_FINAL + ACC_STATIC);

            for (final MappingEntry entry : entrys) {
                cw.visitInnerClass(
                        newDynName + "$" + entry.newActionClassName,
                        newDynName,
                        entry.newActionClassName,
                        ACC_PRIVATE + ACC_STATIC);
            }
        }
        { // 注入 @Resource  private XXXService _service;
            fv = cw.visitField(ACC_PRIVATE, REST_SERVICE_FIELD_NAME, serviceDesc, null, null);
            av0 = fv.visitAnnotation(resDesc, true);
            av0.visit("name", Utility.isBlank(serviceResourceName) ? "" : serviceResourceName);
            av0.visitEnd();
            fv.visitEnd();
        }
        { // _serviceMap字段 Map<String, XXXService>
            fv = cw.visitField(
                    ACC_PRIVATE,
                    REST_SERVICEMAP_FIELD_NAME,
                    "Ljava/util/Map;",
                    "Ljava/util/Map<Ljava/lang/String;" + serviceDesc + ">;",
                    null);
            fv.visitEnd();
        }
        { // _redkale_toStringSupplier字段 Supplier<String>
            fv = cw.visitField(
                    ACC_PRIVATE,
                    REST_TOSTRINGOBJ_FIELD_NAME,
                    "Ljava/util/function/Supplier;",
                    "Ljava/util/function/Supplier<Ljava/lang/String;>;",
                    null);
            fv.visitEnd();
        }
        { // 构造函数
            mv = new MethodDebugVisitor(cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null));
            // mv.setDebug(true);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, supDynName, "<init>", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }

        // 将每个Service可转换的方法生成HttpServlet对应的HttpMapping方法
        boolean namePresent = false;
        try {
            Method m0 = null;
            for (final MappingEntry entry : entrys) {
                if (entry.mappingMethod.getParameterCount() > 0) {
                    m0 = entry.mappingMethod;
                    break;
                }
            }
            namePresent = m0 == null || m0.getParameters()[0].isNamePresent();
        } catch (Exception e) {
            // do nothing
        }
        final Map<String, AsmMethodBean> asmParamMap = namePresent ? null : AsmMethodBoost.getMethodBeans(serviceType);

        Map<String, byte[]> innerClassBytesMap = new LinkedHashMap<>();
        boolean containsMupload = false;
        for (final MappingEntry entry : entrys) {
            RestUploadFile mupload = null;
            Class muploadType = null;
            final Method method = entry.mappingMethod;
            final Class returnType = method.getReturnType();
            final java.lang.reflect.Type retvalType = formatRestReturnType(method, serviceType);
            final String methodDesc = Type.getMethodDescriptor(method);
            final Parameter[] params = method.getParameters();

            final RestConvert[] rcs = method.getAnnotationsByType(RestConvert.class);
            final RestConvertCoder[] rcc = method.getAnnotationsByType(RestConvertCoder.class);
            final boolean hasResConvert = Utility.isNotEmpty(rcs) || Utility.isNotEmpty(rcc);
            if (hasResConvert) {
                restConverts.add(new Object[] {rcs, rcc});
            }
            if (dynsimple && entry.rpcOnly) { // 需要读取http header
                dynsimple = false;
            }

            mv = new MethodDebugVisitor(cw.visitMethod(
                    ACC_PUBLIC, entry.newMethodName, "(" + reqDesc + respDesc + ")V", null, new String[] {
                        "java/io/IOException"
                    }));
            // mv.setDebug(true);
            mv.debugLine();

            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, newDynName, REST_SERVICEMAP_FIELD_NAME, "Ljava/util/Map;");
            Label lmapif = new Label();
            mv.visitJumpInsn(IFNONNULL, lmapif);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, newDynName, REST_SERVICE_FIELD_NAME, serviceDesc);
            Label lserif = new Label();
            mv.visitJumpInsn(GOTO, lserif);
            mv.visitLabel(lmapif);

            mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);

            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, newDynName, REST_SERVICEMAP_FIELD_NAME, "Ljava/util/Map;");
            mv.visitVarInsn(ALOAD, 1);
            mv.visitLdcInsn(REST_HEADER_RESNAME);
            mv.visitLdcInsn("");
            mv.visitMethodInsn(
                    INVOKEVIRTUAL,
                    reqInternalName,
                    "getHeader",
                    "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                    false);
            mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
            mv.visitTypeInsn(CHECKCAST, serviceTypeInternalName);
            mv.visitLabel(lserif);
            mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {serviceTypeInternalName});
            mv.visitVarInsn(ASTORE, 3);

            // 执行setRequestAnnotations
            mv.visitVarInsn(ALOAD, 1);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, newDynName, REST_METHOD_ANNS_NAME, "[[Ljava/lang/annotation/Annotation;");
            Asms.visitInsn(mv, entry.methodIdx); // 方法下标
            mv.visitInsn(AALOAD);
            mv.visitMethodInsn(
                    INVOKESTATIC,
                    restInternalName,
                    "setRequestAnnotations",
                    "(" + reqDesc + "[Ljava/lang/annotation/Annotation;)V",
                    false);

            final int maxStack = 3 + params.length;
            List<int[]> varInsns = new ArrayList<>();
            int maxLocals = 4;

            AsmMethodBean methodBean = asmParamMap == null ? null : AsmMethodBean.get(asmParamMap, method);
            List<AsmMethodParam> asmParamNames = methodBean == null ? null : methodBean.getParams();
            List<Object[]> paramlist = new ArrayList<>();
            // 解析方法中的每个参数
            for (int i = 0; i < params.length; i++) {
                final Parameter param = params[i];
                final Class ptype = param.getType();
                String n = null;
                String comment = "";
                boolean required = true;
                int radix = 10;

                RestHeader annhead = param.getAnnotation(RestHeader.class);
                if (annhead != null) {
                    if (ptype != String.class && ptype != InetSocketAddress.class) {
                        throw new RestException(
                                "@RestHeader must on String or InetSocketAddress Parameter in " + method);
                    }
                    n = annhead.name();
                    radix = annhead.radix();
                    comment = annhead.comment();
                    required = false;
                    if (n.isEmpty()) {
                        throw new RestException("@RestHeader.value is illegal in " + method);
                    }
                }
                RestCookie anncookie = param.getAnnotation(RestCookie.class);
                if (anncookie != null) {
                    if (annhead != null) {
                        throw new RestException(
                                "@RestCookie and @RestHeader cannot on the same Parameter in " + method);
                    }
                    if (ptype != String.class) {
                        throw new RestException("@RestCookie must on String Parameter in " + method);
                    }
                    n = anncookie.name();
                    radix = anncookie.radix();
                    comment = anncookie.comment();
                    required = false;
                    if (n.isEmpty()) {
                        throw new RestException("@RestCookie.value is illegal in " + method);
                    }
                }
                RestSessionid annsid = param.getAnnotation(RestSessionid.class);
                if (annsid != null) {
                    if (annhead != null) {
                        throw new RestException(
                                "@RestSessionid and @RestHeader cannot on the same Parameter in " + method);
                    }
                    if (anncookie != null) {
                        throw new RestException(
                                "@RestSessionid and @RestCookie cannot on the same Parameter in " + method);
                    }
                    if (ptype != String.class) {
                        throw new RestException("@RestSessionid must on String Parameter in " + method);
                    }
                    required = false;
                }
                RestAddress annaddr = param.getAnnotation(RestAddress.class);
                if (annaddr != null) {
                    if (annhead != null) {
                        throw new RestException(
                                "@RestAddress and @RestHeader cannot on the same Parameter in " + method);
                    }
                    if (anncookie != null) {
                        throw new RestException(
                                "@RestAddress and @RestCookie cannot on the same Parameter in " + method);
                    }
                    if (annsid != null) {
                        throw new RestException(
                                "@RestAddress and @RestSessionid cannot on the same Parameter in " + method);
                    }
                    if (ptype != String.class) {
                        throw new RestException("@RestAddress must on String Parameter in " + method);
                    }
                    comment = annaddr.comment();
                    required = false;
                }
                RestLocale annlocale = param.getAnnotation(RestLocale.class);
                if (annlocale != null) {
                    if (annhead != null) {
                        throw new RestException(
                                "@RestLocale and @RestHeader cannot on the same Parameter in " + method);
                    }
                    if (anncookie != null) {
                        throw new RestException(
                                "@RestLocale and @RestCookie cannot on the same Parameter in " + method);
                    }
                    if (annsid != null) {
                        throw new RestException(
                                "@RestLocale and @RestSessionid cannot on the same Parameter in " + method);
                    }
                    if (annaddr != null) {
                        throw new RestException(
                                "@RestLocale and @RestAddress cannot on the same Parameter in " + method);
                    }
                    if (ptype != String.class) {
                        throw new RestException("@RestAddress must on String Parameter in " + method);
                    }
                    comment = annlocale.comment();
                    required = false;
                }
                RestBody annbody = param.getAnnotation(RestBody.class);
                if (annbody != null) {
                    if (annhead != null) {
                        throw new RestException("@RestBody and @RestHeader cannot on the same Parameter in " + method);
                    }
                    if (anncookie != null) {
                        throw new RestException("@RestBody and @RestCookie cannot on the same Parameter in " + method);
                    }
                    if (annsid != null) {
                        throw new RestException(
                                "@RestBody and @RestSessionid cannot on the same Parameter in " + method);
                    }
                    if (annaddr != null) {
                        throw new RestException("@RestBody and @RestAddress cannot on the same Parameter in " + method);
                    }
                    if (annlocale != null) {
                        throw new RestException("@RestBody and @RestLocale cannot on the same Parameter in " + method);
                    }
                    if (ptype.isPrimitive()) {
                        throw new RestException("@RestBody cannot on primitive type Parameter in " + method);
                    }
                    comment = annbody.comment();
                }
                RestUploadFile annfile = param.getAnnotation(RestUploadFile.class);
                if (annfile != null) {
                    if (mupload != null) {
                        throw new RestException("@RestUploadFile repeat in " + method);
                    }
                    mupload = annfile;
                    muploadType = ptype;
                    if (annhead != null) {
                        throw new RestException(
                                "@RestUploadFile and @RestHeader cannot on the same Parameter in " + method);
                    }
                    if (anncookie != null) {
                        throw new RestException(
                                "@RestUploadFile and @RestCookie cannot on the same Parameter in " + method);
                    }
                    if (annsid != null) {
                        throw new RestException(
                                "@RestUploadFile and @RestSessionid cannot on the same Parameter in " + method);
                    }
                    if (annaddr != null) {
                        throw new RestException(
                                "@RestUploadFile and @RestAddress cannot on the same Parameter in " + method);
                    }
                    if (annlocale != null) {
                        throw new RestException(
                                "@RestUploadFile and @RestLocale cannot on the same Parameter in " + method);
                    }
                    if (annbody != null) {
                        throw new RestException(
                                "@RestUploadFile and @RestBody cannot on the same Parameter in " + method);
                    }
                    if (ptype != byte[].class && ptype != File.class && ptype != File[].class) {
                        throw new RestException(
                                "@RestUploadFile must on byte[] or File or File[] Parameter in " + method);
                    }
                    comment = annfile.comment();
                }

                RestPath annpath = param.getAnnotation(RestPath.class);
                if (annpath != null) {
                    if (annhead != null) {
                        throw new RestException("@RestPath and @RestHeader cannot on the same Parameter in " + method);
                    }
                    if (anncookie != null) {
                        throw new RestException("@RestPath and @RestCookie cannot on the same Parameter in " + method);
                    }
                    if (annsid != null) {
                        throw new RestException(
                                "@RestPath and @RestSessionid cannot on the same Parameter in " + method);
                    }
                    if (annaddr != null) {
                        throw new RestException("@RestPath and @RestAddress cannot on the same Parameter in " + method);
                    }
                    if (annlocale != null) {
                        throw new RestException("@RestPath and @RestLocale cannot on the same Parameter in " + method);
                    }
                    if (annbody != null) {
                        throw new RestException("@RestPath and @RestBody cannot on the same Parameter in " + method);
                    }
                    if (annfile != null) {
                        throw new RestException(
                                "@RestPath and @RestUploadFile cannot on the same Parameter in " + method);
                    }
                    if (ptype != String.class) {
                        throw new RestException("@RestPath must on String Parameter in " + method);
                    }
                    comment = annpath.comment();
                }

                RestUserid userid = param.getAnnotation(RestUserid.class);
                if (userid != null) {
                    if (annhead != null) {
                        throw new RestException(
                                "@RestUserid and @RestHeader cannot on the same Parameter in " + method);
                    }
                    if (anncookie != null) {
                        throw new RestException(
                                "@RestUserid and @RestCookie cannot on the same Parameter in " + method);
                    }
                    if (annsid != null) {
                        throw new RestException(
                                "@RestUserid and @RestSessionid cannot on the same Parameter in " + method);
                    }
                    if (annaddr != null) {
                        throw new RestException(
                                "@RestUserid and @RestAddress cannot on the same Parameter in " + method);
                    }
                    if (annlocale != null) {
                        throw new RestException(
                                "@RestUserid and @RestLocale cannot on the same Parameter in " + method);
                    }
                    if (annbody != null) {
                        throw new RestException("@RestUserid and @RestBody cannot on the same Parameter in " + method);
                    }
                    if (annfile != null) {
                        throw new RestException(
                                "@RestUserid and @RestUploadFile cannot on the same Parameter in " + method);
                    }
                    if (!ptype.isPrimitive() && !java.io.Serializable.class.isAssignableFrom(ptype)) {
                        throw new RestException("@RestUserid must on java.io.Serializable Parameter in " + method);
                    }
                    comment = "";
                    required = false;
                }

                boolean annparams = param.getType() == RestParams.class;
                boolean annheaders = param.getType() == RestHeaders.class;
                if (annparams) {
                    if (annhead != null) {
                        throw new RestException("@RestHeader cannot on the " + RestParams.class.getSimpleName()
                                + " Parameter in " + method);
                    }
                    if (anncookie != null) {
                        throw new RestException("@RestCookie cannot on the " + RestParams.class.getSimpleName()
                                + " Parameter in " + method);
                    }
                    if (annsid != null) {
                        throw new RestException("@RestSessionid cannot on the " + RestParams.class.getSimpleName()
                                + " Parameter in " + method);
                    }
                    if (annaddr != null) {
                        throw new RestException("@RestAddress cannot on the " + RestParams.class.getSimpleName()
                                + " Parameter in " + method);
                    }
                    if (annlocale != null) {
                        throw new RestException("@RestLocale cannot on the " + RestParams.class.getSimpleName()
                                + " Parameter in " + method);
                    }
                    if (annbody != null) {
                        throw new RestException("@RestBody cannot on the " + RestParams.class.getSimpleName()
                                + " Parameter in " + method);
                    }
                    if (annfile != null) {
                        throw new RestException("@RestUploadFile cannot on the " + RestParams.class.getSimpleName()
                                + " Parameter in " + method);
                    }
                    if (userid != null) {
                        throw new RestException("@RestUserid cannot on the " + RestParams.class.getSimpleName()
                                + " Parameter in " + method);
                    }
                    if (annheaders) {
                        throw new RestException("@RestHeaders cannot on the " + RestParams.class.getSimpleName()
                                + " Parameter in " + method);
                    }
                    comment = "";
                }

                if (annheaders) {
                    if (annhead != null) {
                        throw new RestException("@RestHeader cannot on the " + RestHeaders.class.getSimpleName()
                                + " Parameter in " + method);
                    }
                    if (anncookie != null) {
                        throw new RestException("@RestCookie cannot on the " + RestHeaders.class.getSimpleName()
                                + " Parameter in " + method);
                    }
                    if (annsid != null) {
                        throw new RestException("@RestSessionid cannot on the " + RestHeaders.class.getSimpleName()
                                + " Parameter in " + method);
                    }
                    if (annaddr != null) {
                        throw new RestException("@RestAddress cannot on the " + RestHeaders.class.getSimpleName()
                                + " Parameter in " + method);
                    }
                    if (annlocale != null) {
                        throw new RestException("@RestLocale cannot on the " + RestHeaders.class.getSimpleName()
                                + " Parameter in " + method);
                    }
                    if (annbody != null) {
                        throw new RestException("@RestBody cannot on the " + RestHeaders.class.getSimpleName()
                                + " Parameter in " + method);
                    }
                    if (annfile != null) {
                        throw new RestException("@RestUploadFile cannot on the " + RestHeaders.class.getSimpleName()
                                + " Parameter in " + method);
                    }
                    if (userid != null) {
                        throw new RestException("@RestUserid cannot on the " + RestHeaders.class.getSimpleName()
                                + " Parameter in " + method);
                    }
                    if (annparams) {
                        throw new RestException("@RestParams cannot on the " + RestHeaders.class.getSimpleName()
                                + " Parameter in " + method);
                    }
                    comment = "";
                    required = false;
                }

                RestParam annpara = param.getAnnotation(RestParam.class);
                if (annpara != null) {
                    radix = annpara.radix();
                }
                if (annpara != null) {
                    comment = annpara.comment();
                }
                if (annpara != null) {
                    required = annpara.required();
                }
                if (n == null) {
                    n = (annpara == null || annpara.name().isEmpty()) ? null : annpara.name();
                }
                if (n == null && ptype == userType) {
                    n = "&"; // 用户类型特殊处理
                }
                if (n == null && ptype == RestHeaders.class) {
                    n = "^"; // Http头信息类型特殊处理
                }
                if (n == null && ptype == RestParams.class) {
                    n = "?"; // Http参数类型特殊处理
                }
                if (n == null && asmParamNames != null && asmParamNames.size() > i) {
                    n = asmParamNames.get(i).getName();
                }
                if (n == null) {
                    if (param.isNamePresent()) {
                        n = param.getName();
                    } else if (ptype == Flipper.class) {
                        n = "flipper";
                    } else {
                        throw new RestException(
                                "Parameter " + param.getName() + " not found name by @RestParam  in " + method);
                    }
                }
                if (annhead == null
                        && anncookie == null
                        && annsid == null
                        && annaddr == null
                        && annlocale == null
                        && annbody == null
                        && annfile == null
                        && !ptype.isPrimitive()
                        && ptype != String.class
                        && ptype != Flipper.class
                        && !CompletionHandler.class.isAssignableFrom(ptype)
                        && !ptype.getName().startsWith("java")
                        && n.charAt(0) != '#'
                        && !"&".equals(n)) { // 判断Json对象是否包含@RestUploadFile
                    Class loop = ptype;
                    do {
                        if (loop == null || loop.isInterface()) {
                            break; // 接口时getSuperclass可能会得到null
                        }
                        for (Field field : loop.getDeclaredFields()) {
                            if (Modifier.isStatic(field.getModifiers())) {
                                continue;
                            }
                            if (Modifier.isFinal(field.getModifiers())) {
                                continue;
                            }
                            RestUploadFile ruf = field.getAnnotation(RestUploadFile.class);
                            if (ruf == null) {
                                continue;
                            }
                            if (mupload != null) {
                                throw new RestException("@RestUploadFile repeat in " + method + " or field " + field);
                            }
                            mupload = ruf;
                            muploadType = field.getType();
                        }
                    } while ((loop = loop.getSuperclass()) != Object.class);
                }
                java.lang.reflect.Type paramtype = TypeToken.getGenericType(param.getParameterizedType(), serviceType);
                paramlist.add(new Object[] {
                    param,
                    n,
                    ptype,
                    radix,
                    comment,
                    required,
                    annpara,
                    annsid,
                    annaddr,
                    annlocale,
                    annhead,
                    anncookie,
                    annbody,
                    annfile,
                    annpath,
                    userid,
                    annheaders,
                    annparams,
                    paramtype
                });
            }

            Map<String, Object> mappingMap = new LinkedHashMap<>();
            java.lang.reflect.Type returnGenericNoFutureType =
                    TypeToken.getGenericType(method.getGenericReturnType(), serviceType);
            { // 设置 Annotation HttpMapping
                boolean reqpath = false;
                for (Object[] ps : paramlist) {
                    if ("#".equals((String) ps[1])) {
                        reqpath = true;
                        break;
                    }
                }
                if (method.getAnnotation(Deprecated.class) != null) {
                    av0 = mv.visitAnnotation(Type.getDescriptor(Deprecated.class), true);
                    av0.visitEnd();
                }
                av0 = mv.visitAnnotation(mappingDesc, true);
                String url = (catalog.isEmpty() ? "/" : ("/" + catalog + "/"))
                        + (defModuleName.isEmpty() ? "" : (defModuleName + "/"))
                        + entry.name
                        + (reqpath ? "/" : "");
                if ("//".equals(url)) {
                    url = "/";
                }
                av0.visit("url", url);
                av0.visit("name", (defModuleName.isEmpty() ? "" : (defModuleName + "_")) + entry.name);
                av0.visit("example", entry.example);
                av0.visit("rpcOnly", entry.rpcOnly);
                av0.visit("auth", entry.auth);
                av0.visit("cacheSeconds", entry.cacheSeconds);
                av0.visit("actionid", entry.actionid);
                av0.visit("comment", entry.comment);

                AnnotationVisitor av1 = av0.visitArray("methods");
                for (String m : entry.methods) {
                    av1.visit(null, m);
                }
                av1.visitEnd();

                Class rtc = returnType;
                if (rtc == void.class) {
                    rtc = RetResult.class;
                    returnGenericNoFutureType = TYPE_RETRESULT_STRING;
                } else if (CompletionStage.class.isAssignableFrom(returnType)) {
                    ParameterizedType ptgrt = (ParameterizedType) returnGenericNoFutureType;
                    returnGenericNoFutureType = ptgrt.getActualTypeArguments()[0];
                    rtc = TypeToken.typeToClass(returnGenericNoFutureType);
                    if (rtc == null) {
                        rtc = Object.class; // 应该不会发生吧?
                    }
                }
                av0.visit("result", Type.getType(Type.getDescriptor(rtc)));
                if (returnGenericNoFutureType != rtc) {
                    String refid = typeRefs.get(returnGenericNoFutureType);
                    if (refid == null) {
                        refid = "_typeref_" + typeRefs.size();
                        typeRefs.put(returnGenericNoFutureType, refid);
                    }
                    av0.visit("resultRef", refid);
                }

                av0.visitEnd();
                mappingMap.put("url", url);
                mappingMap.put("rpcOnly", entry.rpcOnly);
                mappingMap.put("auth", entry.auth);
                mappingMap.put("cacheSeconds", entry.cacheSeconds);
                mappingMap.put("actionid", entry.actionid);
                mappingMap.put("comment", entry.comment);
                mappingMap.put("methods", entry.methods);
                mappingMap.put(
                        "result",
                        returnGenericNoFutureType == returnType
                                ? returnType.getName()
                                : String.valueOf(returnGenericNoFutureType));
                entry.mappingurl = url;
            }
            { // 设置 Annotation NonBlocking
                av0 = mv.visitAnnotation(nonblockDesc, true);
                av0.visit("value", entry.nonBlocking);
                av0.visitEnd();
            }
            if (rcs != null && rcs.length > 0) { // 设置 Annotation RestConvert
                av0 = mv.visitAnnotation(restConvertsDesc, true);
                AnnotationVisitor av1 = av0.visitArray("value");
                // 设置 RestConvert
                for (RestConvert rc : rcs) {
                    AnnotationVisitor av2 = av1.visitAnnotation(null, restConvertDesc);
                    av2.visit("features", rc.features());
                    av2.visit("skipIgnore", rc.skipIgnore());
                    av2.visit("type", Type.getType(Type.getDescriptor(rc.type())));
                    AnnotationVisitor av3 = av2.visitArray("onlyColumns");
                    for (String s : rc.onlyColumns()) {
                        av3.visit(null, s);
                    }
                    av3.visitEnd();
                    av3 = av2.visitArray("ignoreColumns");
                    for (String s : rc.ignoreColumns()) {
                        av3.visit(null, s);
                    }
                    av3.visitEnd();
                    av3 = av2.visitArray("convertColumns");
                    for (String s : rc.convertColumns()) {
                        av3.visit(null, s);
                    }
                    av3.visitEnd();
                    av2.visitEnd();
                }
                av1.visitEnd();
                av0.visitEnd();
            }
            if (rcc != null && rcc.length > 0) { // 设置 Annotation RestConvertCoder
                av0 = mv.visitAnnotation(restConvertCodersDesc, true);
                AnnotationVisitor av1 = av0.visitArray("value");
                // 设置 RestConvertCoder
                for (RestConvertCoder rc : rcc) {
                    AnnotationVisitor av2 = av1.visitAnnotation(null, restConvertCoderDesc);
                    av2.visit("type", Type.getType(Type.getDescriptor(rc.type())));
                    av2.visit("field", rc.field());
                    av2.visit("coder", Type.getType(Type.getDescriptor(rc.coder())));
                    av2.visitEnd();
                }
                av1.visitEnd();
                av0.visitEnd();
            }
            final int headIndex = 10;
            { // 设置 Annotation
                av0 = mv.visitAnnotation(httpParamsDesc, true);
                AnnotationVisitor av1 = av0.visitArray("value");
                // 设置 HttpParam
                for (Object[] ps :
                        paramlist) { // {param, n, ptype, radix, comment, required, annpara, annsid, annaddr, annlocale,
                    // annhead, anncookie, annbody, annfile, annpath, annuserid, annheaders, annparams,
                    // paramtype}
                    String n = ps[1].toString();
                    final boolean isuserid = ((RestUserid) ps[headIndex + 5]) != null; // 是否取userid
                    if (n.indexOf('&') >= 0 || isuserid) {
                        continue; // @RestUserid 不需要生成 @HttpParam
                    }
                    if (((RestAddress) ps[8]) != null) {
                        continue; // @RestAddress 不需要生成 @HttpParam
                    }
                    if (((RestLocale) ps[9]) != null) {
                        continue; // @RestLocale 不需要生成 @HttpParam
                    }
                    final boolean ishead = ((RestHeader) ps[headIndex]) != null; // 是否取getHeader 而不是 getParameter
                    final boolean iscookie = ((RestCookie) ps[headIndex + 1]) != null; // 是否取getCookie
                    final boolean isbody = ((RestBody) ps[headIndex + 2]) != null; // 是否取getBody
                    AnnotationVisitor av2 = av1.visitAnnotation(null, httpParamDesc);
                    av2.visit("name", (String) ps[1]);
                    if (((Parameter) ps[0]).getAnnotation(Deprecated.class) != null) {
                        av2.visit("deprecated", true);
                    }
                    av2.visit("type", Type.getType(Type.getDescriptor((Class) ps[2])));
                    java.lang.reflect.Type pgtype =
                            TypeToken.getGenericType(((Parameter) ps[0]).getParameterizedType(), serviceType);
                    if (pgtype != (Class) ps[2]) {
                        String refid = typeRefs.get(pgtype);
                        if (refid == null) {
                            refid = "_typeref_" + typeRefs.size();
                            typeRefs.put(pgtype, refid);
                        }
                        av2.visit("typeref", refid);
                    }
                    av2.visit("radix", (Integer) ps[3]);
                    if (ishead) {
                        av2.visitEnum("style", sourcetypeDesc, HttpParam.HttpParameterStyle.HEADER.name());
                        av2.visit("example", ((RestHeader) ps[headIndex]).example());
                    } else if (iscookie) {
                        av2.visitEnum("style", sourcetypeDesc, HttpParam.HttpParameterStyle.COOKIE.name());
                        av2.visit("example", ((RestCookie) ps[headIndex + 1]).example());
                    } else if (isbody) {
                        av2.visitEnum("style", sourcetypeDesc, HttpParam.HttpParameterStyle.BODY.name());
                        av2.visit("example", ((RestBody) ps[headIndex + 2]).example());
                    } else if (ps[6] != null) {
                        av2.visitEnum("style", sourcetypeDesc, HttpParam.HttpParameterStyle.QUERY.name());
                        av2.visit("example", ((RestParam) ps[6]).example());
                    }
                    av2.visit("comment", (String) ps[4]);
                    av2.visit("required", (Boolean) ps[5]);
                    av2.visitEnd();
                }
                av1.visitEnd();
                av0.visitEnd();
            }
            int uploadLocal = 0;
            if (mupload != null) { // 存在文件上传
                containsMupload = true;
                if (muploadType == byte[].class) {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitMethodInsn(
                            INVOKEVIRTUAL, reqInternalName, "getMultiContext", "()" + multiContextDesc, false);
                    mv.visitLdcInsn(mupload.maxLength());
                    mv.visitLdcInsn(mupload.fileNameRegex());
                    mv.visitLdcInsn(mupload.contentTypeRegex());
                    mv.visitMethodInsn(
                            INVOKEVIRTUAL,
                            multiContextName,
                            "partsFirstBytes",
                            "(JLjava/lang/String;Ljava/lang/String;)[B",
                            false);
                    mv.visitVarInsn(ASTORE, maxLocals);
                    uploadLocal = maxLocals;
                } else if (muploadType == File.class) {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitMethodInsn(
                            INVOKEVIRTUAL, reqInternalName, "getMultiContext", "()" + multiContextDesc, false);
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, newDynName, "_redkale_home", "Ljava/io/File;");
                    mv.visitLdcInsn(mupload.maxLength());
                    mv.visitLdcInsn(mupload.fileNameRegex());
                    mv.visitLdcInsn(mupload.contentTypeRegex());
                    mv.visitMethodInsn(
                            INVOKEVIRTUAL,
                            multiContextName,
                            "partsFirstFile",
                            "(Ljava/io/File;JLjava/lang/String;Ljava/lang/String;)Ljava/io/File;",
                            false);
                    mv.visitVarInsn(ASTORE, maxLocals);
                    uploadLocal = maxLocals;
                } else if (muploadType == File[].class) { // File[]
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitMethodInsn(
                            INVOKEVIRTUAL, reqInternalName, "getMultiContext", "()" + multiContextDesc, false);
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, newDynName, "_redkale_home", "Ljava/io/File;");
                    mv.visitLdcInsn(mupload.maxLength());
                    mv.visitLdcInsn(mupload.fileNameRegex());
                    mv.visitLdcInsn(mupload.contentTypeRegex());
                    mv.visitMethodInsn(
                            INVOKEVIRTUAL,
                            multiContextName,
                            "partsFiles",
                            "(Ljava/io/File;JLjava/lang/String;Ljava/lang/String;)[Ljava/io/File;",
                            false);
                    mv.visitVarInsn(ASTORE, maxLocals);
                    uploadLocal = maxLocals;
                }
                maxLocals++;
            }

            List<Map<String, Object>> paramMaps = new ArrayList<>();
            // 获取每个参数的值
            boolean hasAsyncHandler = false;
            for (Object[] ps : paramlist) {
                Map<String, Object> paramMap = new LinkedHashMap<>();
                final Parameter param = (Parameter) ps[0]; // 参数类型
                String pname = (String) ps[1]; // 参数名
                Class ptype = (Class) ps[2]; // 参数类型
                int radix = (Integer) ps[3];
                String comment = (String) ps[4];
                boolean required = (Boolean) ps[5];
                RestParam annpara = (RestParam) ps[6];
                RestSessionid annsid = (RestSessionid) ps[7];
                RestAddress annaddr = (RestAddress) ps[8];
                RestLocale annlocale = (RestLocale) ps[9];
                RestHeader annhead = (RestHeader) ps[headIndex];
                RestCookie anncookie = (RestCookie) ps[headIndex + 1];
                RestBody annbody = (RestBody) ps[headIndex + 2];
                RestUploadFile annfile = (RestUploadFile) ps[headIndex + 3];
                RestPath annpath = (RestPath) ps[headIndex + 4];
                RestUserid userid = (RestUserid) ps[headIndex + 5];
                boolean annheaders = (Boolean) ps[headIndex + 6];
                boolean annparams = (Boolean) ps[headIndex + 7];
                java.lang.reflect.Type pgentype = (java.lang.reflect.Type) ps[headIndex + 8];
                if (dynsimple
                        && (annsid != null
                                || annaddr != null
                                || annlocale != null
                                || annhead != null
                                || anncookie != null
                                || annfile != null
                                || annheaders)) {
                    dynsimple = false;
                }

                final boolean ishead = annhead != null; // 是否取getHeader 而不是 getParameter
                final boolean iscookie = anncookie != null; // 是否取getCookie

                paramMap.put("name", pname);
                paramMap.put("type", ptype.getName());
                if (CompletionHandler.class.isAssignableFrom(
                        ptype)) { // HttpResponse.createAsyncHandler() or HttpResponse.createAsyncHandler(Class)
                    if (ptype == CompletionHandler.class) {
                        mv.visitVarInsn(ALOAD, 2);
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL,
                                respInternalName,
                                "createAsyncHandler",
                                "()Ljava/nio/channels/CompletionHandler;",
                                false);
                        mv.visitVarInsn(ASTORE, maxLocals);
                        varInsns.add(new int[] {ALOAD, maxLocals});
                    } else {
                        mv.visitVarInsn(ALOAD, 3);
                        mv.visitVarInsn(ALOAD, 2);
                        mv.visitLdcInsn(Type.getType(Type.getDescriptor(ptype)));
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL,
                                respInternalName,
                                "createAsyncHandler",
                                "(Ljava/lang/Class;)Ljava/nio/channels/CompletionHandler;",
                                false);
                        mv.visitTypeInsn(CHECKCAST, ptype.getName().replace('.', '/'));
                        mv.visitVarInsn(ASTORE, maxLocals);
                        varInsns.add(new int[] {ALOAD, maxLocals});
                    }
                    hasAsyncHandler = true;
                } else if (annsid != null) { // HttpRequest.getSessionid(true|false)
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitInsn(annsid.create() ? ICONST_1 : ICONST_0);
                    mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, "getSessionid", "(Z)Ljava/lang/String;", false);
                    mv.visitVarInsn(ASTORE, maxLocals);
                    varInsns.add(new int[] {ALOAD, maxLocals});
                } else if (annaddr != null) { // HttpRequest.getRemoteAddr
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, "getRemoteAddr", "()Ljava/lang/String;", false);
                    mv.visitVarInsn(ASTORE, maxLocals);
                    varInsns.add(new int[] {ALOAD, maxLocals});
                } else if (annlocale != null) { // HttpRequest.getLocale
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, "getLocale", "()Ljava/lang/String;", false);
                    mv.visitVarInsn(ASTORE, maxLocals);
                    varInsns.add(new int[] {ALOAD, maxLocals});
                } else if (annheaders) { // HttpRequest.getHeaders
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, "getHeaders", "()" + httpHeadersDesc, false);
                    mv.visitVarInsn(ASTORE, maxLocals);
                    varInsns.add(new int[] {ALOAD, maxLocals});
                } else if (annparams) { // HttpRequest.getParameters
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitMethodInsn(
                            INVOKEVIRTUAL, reqInternalName, "getParameters", "()" + httpParametersDesc, false);
                    mv.visitVarInsn(ASTORE, maxLocals);
                    varInsns.add(new int[] {ALOAD, maxLocals});
                } else if (annbody != null) { // HttpRequest.getBodyUTF8 / HttpRequest.getBody
                    if (ptype == String.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL, reqInternalName, "getBodyUTF8", "()Ljava/lang/String;", false);
                        mv.visitVarInsn(ASTORE, maxLocals);
                        varInsns.add(new int[] {ALOAD, maxLocals});
                    } else if (ptype == byte[].class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, "getBody", "()[B", false);
                        mv.visitVarInsn(ASTORE, maxLocals);
                        varInsns.add(new int[] {ALOAD, maxLocals});
                    } else { // JavaBean 转 Json
                        String typefieldname = "_redkale_body_jsontype_" + bodyTypes.size();
                        bodyTypes.put(typefieldname, pgentype);
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitFieldInsn(GETFIELD, newDynName, typefieldname, "Ljava/lang/reflect/Type;");
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL,
                                reqInternalName,
                                "getBodyJson",
                                "(Ljava/lang/reflect/Type;)Ljava/lang/Object;",
                                false);
                        mv.visitTypeInsn(CHECKCAST, Type.getInternalName(ptype));
                        mv.visitVarInsn(ASTORE, maxLocals);
                        varInsns.add(new int[] {ALOAD, maxLocals});
                    }
                } else if (annfile
                        != null) { // MultiContext.partsFirstBytes / HttpRequest.partsFirstFile / HttpRequest.partsFiles
                    mv.visitVarInsn(ALOAD, 4);
                    mv.visitVarInsn(ASTORE, maxLocals);
                    varInsns.add(new int[] {ALOAD, maxLocals});
                } else if (annpath != null) { // HttpRequest.getRequestPath
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, "getRequestPath", "()Ljava/lang/String;", false);
                    mv.visitVarInsn(ASTORE, maxLocals);
                    varInsns.add(new int[] {ALOAD, maxLocals});
                } else if (userid != null) { // HttpRequest.currentUserid
                    mv.visitVarInsn(ALOAD, 1);
                    if (ptype == int.class) {
                        mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, "currentIntUserid", "()I", false);
                        mv.visitVarInsn(ISTORE, maxLocals);
                        varInsns.add(new int[] {ILOAD, maxLocals});
                    } else if (ptype == long.class) {
                        mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, "currentLongUserid", "()J", false);
                        mv.visitVarInsn(LSTORE, maxLocals);
                        varInsns.add(new int[] {LLOAD, maxLocals});
                        maxLocals++;
                    } else if (ptype == String.class) {
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL, reqInternalName, "currentStringUserid", "()Ljava/lang/String;", false);
                        mv.visitVarInsn(ASTORE, maxLocals);
                        varInsns.add(new int[] {ALOAD, maxLocals});
                    } else {
                        mv.visitLdcInsn(Type.getType(Type.getDescriptor(ptype)));
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL,
                                reqInternalName,
                                "currentUserid",
                                "(Ljava/lang/Class;)Ljava/io/Serializable;",
                                false);
                        mv.visitTypeInsn(CHECKCAST, Type.getInternalName(ptype));
                        mv.visitVarInsn(ASTORE, maxLocals);
                        varInsns.add(new int[] {ALOAD, maxLocals});
                    }
                } else if ("#".equals(pname)) { // 从request.getRequstURI 中取参数
                    if (ptype == boolean.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL, reqInternalName, "getPathLastParam", "()Ljava/lang/String;", false);
                        mv.visitMethodInsn(
                                INVOKESTATIC, "java/lang/Boolean", "parseBoolean", "(Ljava/lang/String;)Z", false);
                        mv.visitVarInsn(ISTORE, maxLocals);
                        varInsns.add(new int[] {ILOAD, maxLocals});
                    } else if (ptype == byte.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL, reqInternalName, "getPathLastParam", "()Ljava/lang/String;", false);
                        mv.visitIntInsn(BIPUSH, radix);
                        mv.visitMethodInsn(
                                INVOKESTATIC, "java/lang/Byte", "parseByte", "(Ljava/lang/String;I)B", false);
                        mv.visitVarInsn(ISTORE, maxLocals);
                        varInsns.add(new int[] {ILOAD, maxLocals});
                    } else if (ptype == short.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL, reqInternalName, "getPathLastParam", "()Ljava/lang/String;", false);
                        mv.visitIntInsn(BIPUSH, radix);
                        mv.visitMethodInsn(
                                INVOKESTATIC, "java/lang/Short", "parseShort", "(Ljava/lang/String;I)S", false);
                        mv.visitVarInsn(ISTORE, maxLocals);
                        varInsns.add(new int[] {ILOAD, maxLocals});
                    } else if (ptype == char.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL, reqInternalName, "getPathLastParam", "()Ljava/lang/String;", false);
                        mv.visitInsn(ICONST_0);
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false);
                        mv.visitVarInsn(ISTORE, maxLocals);
                        varInsns.add(new int[] {ILOAD, maxLocals});
                    } else if (ptype == int.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL, reqInternalName, "getPathLastParam", "()Ljava/lang/String;", false);
                        mv.visitIntInsn(BIPUSH, radix);
                        mv.visitMethodInsn(
                                INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;I)I", false);
                        mv.visitVarInsn(ISTORE, maxLocals);
                        varInsns.add(new int[] {ILOAD, maxLocals});
                    } else if (ptype == float.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL, reqInternalName, "getPathLastParam", "()Ljava/lang/String;", false);
                        mv.visitMethodInsn(
                                INVOKESTATIC, "java/lang/Float", "parseFloat", "(Ljava/lang/String;)F", false);
                        mv.visitVarInsn(FSTORE, maxLocals);
                        varInsns.add(new int[] {FLOAD, maxLocals});
                    } else if (ptype == long.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL, reqInternalName, "getPathLastParam", "()Ljava/lang/String;", false);
                        mv.visitIntInsn(BIPUSH, radix);
                        mv.visitMethodInsn(
                                INVOKESTATIC, "java/lang/Long", "parseLong", "(Ljava/lang/String;I)J", false);
                        mv.visitVarInsn(LSTORE, maxLocals);
                        varInsns.add(new int[] {LLOAD, maxLocals});
                        maxLocals++;
                    } else if (ptype == double.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL, reqInternalName, "getPathLastParam", "()Ljava/lang/String;", false);
                        mv.visitMethodInsn(
                                INVOKESTATIC, "java/lang/Double", "parseDouble", "(Ljava/lang/String;)D", false);
                        mv.visitVarInsn(DSTORE, maxLocals);
                        varInsns.add(new int[] {DLOAD, maxLocals});
                        maxLocals++;
                    } else if (ptype == String.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL, reqInternalName, "getPathLastParam", "()Ljava/lang/String;", false);
                        mv.visitVarInsn(ASTORE, maxLocals);
                        varInsns.add(new int[] {ALOAD, maxLocals});
                    } else {
                        throw new RestException(method + " only " + RestParam.class.getSimpleName()
                                + "(#) to Type(primitive class or String)");
                    }
                } else if (pname.charAt(0) == '#') { // 从request.getPathParam 中去参数
                    if (ptype == boolean.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitLdcInsn(pname.substring(1));
                        mv.visitLdcInsn("false");
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL,
                                reqInternalName,
                                "getPathParam",
                                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                                false);
                        mv.visitMethodInsn(
                                INVOKESTATIC, "java/lang/Boolean", "parseBoolean", "(Ljava/lang/String;)Z", false);
                        mv.visitVarInsn(ISTORE, maxLocals);
                        varInsns.add(new int[] {ILOAD, maxLocals});
                    } else if (ptype == byte.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitLdcInsn(pname.substring(1));
                        mv.visitLdcInsn("0");
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL,
                                reqInternalName,
                                "getPathParam",
                                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                                false);
                        mv.visitIntInsn(BIPUSH, radix);
                        mv.visitMethodInsn(
                                INVOKESTATIC, "java/lang/Byte", "parseByte", "(Ljava/lang/String;I)B", false);
                        mv.visitVarInsn(ISTORE, maxLocals);
                        varInsns.add(new int[] {ILOAD, maxLocals});
                    } else if (ptype == short.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitLdcInsn(pname.substring(1));
                        mv.visitLdcInsn("0");
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL,
                                reqInternalName,
                                "getPathParam",
                                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                                false);
                        mv.visitIntInsn(BIPUSH, radix);
                        mv.visitMethodInsn(
                                INVOKESTATIC, "java/lang/Short", "parseShort", "(Ljava/lang/String;I)S", false);
                        mv.visitVarInsn(ISTORE, maxLocals);
                        varInsns.add(new int[] {ILOAD, maxLocals});
                    } else if (ptype == char.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitLdcInsn(pname.substring(1));
                        mv.visitLdcInsn("0");
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL,
                                reqInternalName,
                                "getPathParam",
                                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                                false);
                        mv.visitInsn(ICONST_0);
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false);
                        mv.visitVarInsn(ISTORE, maxLocals);
                        varInsns.add(new int[] {ILOAD, maxLocals});
                    } else if (ptype == int.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitLdcInsn(pname.substring(1));
                        mv.visitLdcInsn("0");
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL,
                                reqInternalName,
                                "getPathParam",
                                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                                false);
                        mv.visitIntInsn(BIPUSH, radix);
                        mv.visitMethodInsn(
                                INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;I)I", false);
                        mv.visitVarInsn(ISTORE, maxLocals);
                        varInsns.add(new int[] {ILOAD, maxLocals});
                    } else if (ptype == float.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitLdcInsn(pname.substring(1));
                        mv.visitLdcInsn("0");
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL,
                                reqInternalName,
                                "getPathParam",
                                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                                false);
                        mv.visitMethodInsn(
                                INVOKESTATIC, "java/lang/Float", "parseFloat", "(Ljava/lang/String;)F", false);
                        mv.visitVarInsn(FSTORE, maxLocals);
                        varInsns.add(new int[] {FLOAD, maxLocals});
                    } else if (ptype == long.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitLdcInsn(pname.substring(1));
                        mv.visitLdcInsn("0");
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL,
                                reqInternalName,
                                "getPathParam",
                                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                                false);
                        mv.visitIntInsn(BIPUSH, radix);
                        mv.visitMethodInsn(
                                INVOKESTATIC, "java/lang/Long", "parseLong", "(Ljava/lang/String;I)J", false);
                        mv.visitVarInsn(LSTORE, maxLocals);
                        varInsns.add(new int[] {LLOAD, maxLocals});
                        maxLocals++;
                    } else if (ptype == double.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitLdcInsn(pname.substring(1));
                        mv.visitLdcInsn("0");
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL,
                                reqInternalName,
                                "getPathParam",
                                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                                false);
                        mv.visitMethodInsn(
                                INVOKESTATIC, "java/lang/Double", "parseDouble", "(Ljava/lang/String;)D", false);
                        mv.visitVarInsn(DSTORE, maxLocals);
                        varInsns.add(new int[] {DLOAD, maxLocals});
                        maxLocals++;
                    } else if (ptype == String.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitLdcInsn(pname.substring(1));
                        mv.visitLdcInsn("");
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL,
                                reqInternalName,
                                "getPathParam",
                                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                                false);
                        mv.visitVarInsn(ASTORE, maxLocals);
                        varInsns.add(new int[] {ALOAD, maxLocals});
                    } else {
                        throw new RestException(method + " only " + RestParam.class.getSimpleName()
                                + "(#) to Type(primitive class or String)");
                    }
                } else if ("&".equals(pname) && ptype == userType) { // 当前用户对象的类名
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, "currentUser", "()Ljava/lang/Object;", false);
                    mv.visitTypeInsn(CHECKCAST, Type.getInternalName(ptype));
                    mv.visitVarInsn(ASTORE, maxLocals);
                    varInsns.add(new int[] {ALOAD, maxLocals});
                } else if (ptype == boolean.class) {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitLdcInsn(pname);
                    mv.visitInsn(ICONST_0);
                    mv.visitMethodInsn(
                            INVOKEVIRTUAL,
                            reqInternalName,
                            ishead ? "getBooleanHeader" : "getBooleanParameter",
                            "(Ljava/lang/String;Z)Z",
                            false);
                    mv.visitVarInsn(ISTORE, maxLocals);
                    varInsns.add(new int[] {ILOAD, maxLocals});
                } else if (ptype == byte.class) {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitLdcInsn(pname);
                    mv.visitLdcInsn("0");
                    mv.visitMethodInsn(
                            INVOKEVIRTUAL,
                            reqInternalName,
                            ishead ? "getHeader" : "getParameter",
                            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                            false);
                    mv.visitIntInsn(BIPUSH, radix);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Byte", "parseByte", "(Ljava/lang/String;I)B", false);
                    mv.visitVarInsn(ISTORE, maxLocals);
                    varInsns.add(new int[] {ILOAD, maxLocals});
                } else if (ptype == short.class) {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitIntInsn(BIPUSH, radix);
                    mv.visitLdcInsn(pname);
                    mv.visitInsn(ICONST_0);
                    mv.visitMethodInsn(
                            INVOKEVIRTUAL,
                            reqInternalName,
                            ishead ? "getShortHeader" : "getShortParameter",
                            "(ILjava/lang/String;S)S",
                            false);
                    mv.visitVarInsn(ISTORE, maxLocals);
                    varInsns.add(new int[] {ILOAD, maxLocals});
                } else if (ptype == char.class) {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitLdcInsn(pname);
                    mv.visitLdcInsn("0");
                    mv.visitMethodInsn(
                            INVOKEVIRTUAL,
                            reqInternalName,
                            ishead ? "getHeader" : "getParameter",
                            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                            false);
                    mv.visitInsn(ICONST_0);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false);
                    mv.visitVarInsn(ISTORE, maxLocals);
                    varInsns.add(new int[] {ILOAD, maxLocals});
                } else if (ptype == int.class) {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitIntInsn(BIPUSH, radix);
                    mv.visitLdcInsn(pname);
                    mv.visitInsn(ICONST_0);
                    mv.visitMethodInsn(
                            INVOKEVIRTUAL,
                            reqInternalName,
                            ishead ? "getIntHeader" : "getIntParameter",
                            "(ILjava/lang/String;I)I",
                            false);
                    mv.visitVarInsn(ISTORE, maxLocals);
                    varInsns.add(new int[] {ILOAD, maxLocals});
                } else if (ptype == float.class) {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitLdcInsn(pname);
                    mv.visitInsn(FCONST_0);
                    mv.visitMethodInsn(
                            INVOKEVIRTUAL,
                            reqInternalName,
                            ishead ? "getFloatHeader" : "getFloatParameter",
                            "(Ljava/lang/String;F)F",
                            false);
                    mv.visitVarInsn(FSTORE, maxLocals);
                    varInsns.add(new int[] {FLOAD, maxLocals});
                } else if (ptype == long.class) {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitIntInsn(BIPUSH, radix);
                    mv.visitLdcInsn(pname);
                    mv.visitInsn(LCONST_0);
                    mv.visitMethodInsn(
                            INVOKEVIRTUAL,
                            reqInternalName,
                            ishead ? "getLongHeader" : "getLongParameter",
                            "(ILjava/lang/String;J)J",
                            false);
                    mv.visitVarInsn(LSTORE, maxLocals);
                    varInsns.add(new int[] {LLOAD, maxLocals});
                    maxLocals++;
                } else if (ptype == double.class) {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitLdcInsn(pname);
                    mv.visitInsn(DCONST_0);
                    mv.visitMethodInsn(
                            INVOKEVIRTUAL,
                            reqInternalName,
                            ishead ? "getDoubleHeader" : "getDoubleParameter",
                            "(Ljava/lang/String;D)D",
                            false);
                    mv.visitVarInsn(DSTORE, maxLocals);
                    varInsns.add(new int[] {DLOAD, maxLocals});
                    maxLocals++;
                } else if (ptype == String.class) {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitLdcInsn(pname);
                    mv.visitLdcInsn("");
                    mv.visitMethodInsn(
                            INVOKEVIRTUAL,
                            reqInternalName,
                            iscookie ? "getCookie" : (ishead ? "getHeader" : "getParameter"),
                            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                            false);
                    mv.visitVarInsn(ASTORE, maxLocals);
                    varInsns.add(new int[] {ALOAD, maxLocals});
                } else if (ptype == Flipper.class) {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, "getFlipper", "()" + flipperDesc, false);
                    mv.visitVarInsn(ASTORE, maxLocals);
                    varInsns.add(new int[] {ALOAD, maxLocals});
                } else { // 其他Json对象
                    mv.visitVarInsn(ALOAD, 1);
                    if (param.getType() == param.getParameterizedType()) {
                        mv.visitLdcInsn(Type.getType(Type.getDescriptor(ptype)));
                    } else {
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitFieldInsn(
                                GETFIELD, newDynName, REST_PARAMTYPES_FIELD_NAME, "[[Ljava/lang/reflect/Type;");
                        Asms.visitInsn(mv, entry.methodIdx); // 方法下标
                        mv.visitInsn(AALOAD);
                        int paramidx = -1;
                        for (int i = 0; i < params.length; i++) {
                            if (params[i] == param) {
                                paramidx = i;
                                break;
                            }
                        }
                        Asms.visitInsn(mv, paramidx); // 参数下标
                        mv.visitInsn(AALOAD);
                    }
                    mv.visitLdcInsn(pname);
                    mv.visitMethodInsn(
                            INVOKEVIRTUAL,
                            reqInternalName,
                            ishead ? "getJsonHeader" : "getJsonParameter",
                            "(Ljava/lang/reflect/Type;Ljava/lang/String;)Ljava/lang/Object;",
                            false);
                    mv.visitTypeInsn(CHECKCAST, ptype.getName().replace('.', '/'));
                    mv.visitVarInsn(ASTORE, maxLocals);
                    varInsns.add(new int[] {ALOAD, maxLocals});
                    JsonFactory.root().loadDecoder(pgentype);

                    // 构建 RestHeader、RestCookie、RestAddress 等赋值操作
                    Class loop = ptype;
                    Set<String> fields = new HashSet<>();
                    Map<String, Object[]> attrParaNames = new LinkedHashMap<>();
                    do {
                        if (loop == null || loop.isInterface()) {
                            break; // 接口时getSuperclass可能会得到null
                        }
                        for (Field field : loop.getDeclaredFields()) {
                            if (Modifier.isStatic(field.getModifiers())) {
                                continue;
                            }
                            if (Modifier.isFinal(field.getModifiers())) {
                                continue;
                            }
                            if (fields.contains(field.getName())) {
                                continue;
                            }
                            RestHeader rh = field.getAnnotation(RestHeader.class);
                            RestCookie rc = field.getAnnotation(RestCookie.class);
                            RestSessionid rs = field.getAnnotation(RestSessionid.class);
                            RestAddress ra = field.getAnnotation(RestAddress.class);
                            RestLocale rl = field.getAnnotation(RestLocale.class);
                            RestBody rb = field.getAnnotation(RestBody.class);
                            RestUploadFile ru = field.getAnnotation(RestUploadFile.class);
                            RestPath ri = field.getAnnotation(RestPath.class);
                            if (rh == null
                                    && rc == null
                                    && ra == null
                                    && rl == null
                                    && rb == null
                                    && rs == null
                                    && ru == null
                                    && ri == null) {
                                continue;
                            }
                            if (rh != null
                                    && field.getType() != String.class
                                    && field.getType() != InetSocketAddress.class) {
                                throw new RestException("@RestHeader must on String Field in " + field);
                            }
                            if (rc != null && field.getType() != String.class) {
                                throw new RestException("@RestCookie must on String Field in " + field);
                            }
                            if (rs != null && field.getType() != String.class) {
                                throw new RestException("@RestSessionid must on String Field in " + field);
                            }
                            if (ra != null && field.getType() != String.class) {
                                throw new RestException("@RestAddress must on String Field in " + field);
                            }
                            if (rl != null && field.getType() != String.class) {
                                throw new RestException("@RestLocale must on String Field in " + field);
                            }
                            if (rb != null && field.getType().isPrimitive()) {
                                throw new RestException("@RestBody must on cannot on primitive type Field in " + field);
                            }
                            if (ru != null
                                    && field.getType() != byte[].class
                                    && field.getType() != File.class
                                    && field.getType() != File[].class) {
                                throw new RestException(
                                        "@RestUploadFile must on byte[] or File or File[] Field in " + field);
                            }

                            if (ri != null && field.getType() != String.class) {
                                throw new RestException("@RestPath must on String Field in " + field);
                            }
                            org.redkale.util.Attribute attr = org.redkale.util.Attribute.create(loop, field);
                            String attrFieldName;
                            String restname = "";
                            if (rh != null) {
                                attrFieldName = "_redkale_attr_header_"
                                        + (field.getType() != String.class ? "json_" : "") + restAttributes.size();
                                restname = rh.name();
                            } else if (rc != null) {
                                attrFieldName = "_redkale_attr_cookie_" + restAttributes.size();
                                restname = rc.name();
                            } else if (rs != null) {
                                attrFieldName = "_redkale_attr_sessionid_" + restAttributes.size();
                                restname = rs.create() ? "1" : ""; // 用于下面区分create值
                            } else if (ra != null) {
                                attrFieldName = "_redkale_attr_address_" + restAttributes.size();
                                // restname = "";
                            } else if (rl != null) {
                                attrFieldName = "_redkale_attr_locale_" + restAttributes.size();
                                // restname = "";
                            } else if (rb != null && field.getType() == String.class) {
                                attrFieldName = "_redkale_attr_bodystring_" + restAttributes.size();
                                // restname = "";
                            } else if (rb != null && field.getType() == byte[].class) {
                                attrFieldName = "_redkale_attr_bodybytes_" + restAttributes.size();
                                // restname = "";
                            } else if (rb != null
                                    && field.getType() != String.class
                                    && field.getType() != byte[].class) {
                                attrFieldName = "_redkale_attr_bodyjson_" + restAttributes.size();
                                // restname = "";
                            } else if (ru != null && field.getType() == byte[].class) {
                                attrFieldName = "_redkale_attr_uploadbytes_" + restAttributes.size();
                                // restname = "";
                            } else if (ru != null && field.getType() == File.class) {
                                attrFieldName = "_redkale_attr_uploadfile_" + restAttributes.size();
                                // restname = "";
                            } else if (ru != null && field.getType() == File[].class) {
                                attrFieldName = "_redkale_attr_uploadfiles_" + restAttributes.size();
                                // restname = "";
                            } else if (ri != null && field.getType() == String.class) {
                                attrFieldName = "_redkale_attr_uri_" + restAttributes.size();
                                // restname = "";
                            } else {
                                continue;
                            }
                            restAttributes.put(attrFieldName, attr);
                            attrParaNames.put(
                                    attrFieldName,
                                    new Object[] {restname, field.getType(), field.getGenericType(), ru});
                            fields.add(field.getName());
                        }
                    } while ((loop = loop.getSuperclass()) != Object.class);

                    if (!attrParaNames
                            .isEmpty()) { // 参数存在 RestHeader、RestCookie、RestSessionid、RestAddress、RestLocale、RestBody字段
                        mv.visitVarInsn(ALOAD, maxLocals); // 加载JsonBean
                        Label lif = new Label();
                        mv.visitJumpInsn(IFNULL, lif); // if(bean != null) {
                        for (Map.Entry<String, Object[]> en : attrParaNames.entrySet()) {
                            RestUploadFile ru = (RestUploadFile) en.getValue()[3];
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitFieldInsn(GETFIELD, newDynName, en.getKey(), attrDesc);
                            mv.visitVarInsn(ALOAD, maxLocals);
                            mv.visitVarInsn(ALOAD, en.getKey().contains("_upload") ? uploadLocal : 1);
                            if (en.getKey().contains("_header_")) {
                                String headerkey = en.getValue()[0].toString();
                                if ("Host".equalsIgnoreCase(headerkey)) {
                                    mv.visitMethodInsn(
                                            INVOKEVIRTUAL, reqInternalName, "getHost", "()Ljava/lang/String;", false);
                                } else if ("Content-Type".equalsIgnoreCase(headerkey)) {
                                    mv.visitMethodInsn(
                                            INVOKEVIRTUAL,
                                            reqInternalName,
                                            "getContentType",
                                            "()Ljava/lang/String;",
                                            false);
                                } else if ("Connection".equalsIgnoreCase(headerkey)) {
                                    mv.visitMethodInsn(
                                            INVOKEVIRTUAL,
                                            reqInternalName,
                                            "getConnection",
                                            "()Ljava/lang/String;",
                                            false);
                                } else if ("Method".equalsIgnoreCase(headerkey)) {
                                    mv.visitMethodInsn(
                                            INVOKEVIRTUAL, reqInternalName, "getMethod", "()Ljava/lang/String;", false);
                                } else if (en.getKey().contains("_header_json_")) {
                                    String typefieldname = "_redkale_body_jsontype_" + bodyTypes.size();
                                    bodyTypes.put(typefieldname, (java.lang.reflect.Type) en.getValue()[2]);
                                    mv.visitVarInsn(ALOAD, 0);
                                    mv.visitFieldInsn(GETFIELD, newDynName, typefieldname, "Ljava/lang/reflect/Type;");
                                    mv.visitLdcInsn(headerkey);
                                    mv.visitMethodInsn(
                                            INVOKEVIRTUAL,
                                            reqInternalName,
                                            "getJsonHeader",
                                            "(Ljava/lang/reflect/Type;Ljava/lang/String;)Ljava/lang/Object;",
                                            false);
                                    mv.visitTypeInsn(CHECKCAST, Type.getInternalName((Class) en.getValue()[1]));
                                    JsonFactory.root().loadDecoder((java.lang.reflect.Type) en.getValue()[2]);
                                } else {
                                    mv.visitLdcInsn(headerkey);
                                    mv.visitLdcInsn("");
                                    mv.visitMethodInsn(
                                            INVOKEVIRTUAL,
                                            reqInternalName,
                                            "getHeader",
                                            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                                            false);
                                }
                            } else if (en.getKey().contains("_cookie_")) {
                                mv.visitLdcInsn(en.getValue()[0].toString());
                                mv.visitLdcInsn("");
                                mv.visitMethodInsn(
                                        INVOKEVIRTUAL,
                                        reqInternalName,
                                        "getCookie",
                                        "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                                        false);
                            } else if (en.getKey().contains("_sessionid_")) {
                                mv.visitInsn(en.getValue()[0].toString().isEmpty() ? ICONST_0 : ICONST_1);
                                mv.visitMethodInsn(
                                        INVOKEVIRTUAL, reqInternalName, "getSessionid", "(Z)Ljava/lang/String;", false);
                            } else if (en.getKey().contains("_address_")) {
                                mv.visitMethodInsn(
                                        INVOKEVIRTUAL, reqInternalName, "getRemoteAddr", "()Ljava/lang/String;", false);
                            } else if (en.getKey().contains("_locale_")) {
                                mv.visitMethodInsn(
                                        INVOKEVIRTUAL, reqInternalName, "getLocale", "()Ljava/lang/String;", false);
                            } else if (en.getKey().contains("_uri_")) {
                                mv.visitMethodInsn(
                                        INVOKEVIRTUAL, reqInternalName, "getPath", "()Ljava/lang/String;", false);
                            } else if (en.getKey().contains("_bodystring_")) {
                                mv.visitMethodInsn(
                                        INVOKEVIRTUAL, reqInternalName, "getBodyUTF8", "()Ljava/lang/String;", false);
                            } else if (en.getKey().contains("_bodybytes_")) {
                                mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, "getBody", "()[B", false);
                            } else if (en.getKey().contains("_bodyjson_")) { // JavaBean 转 Json
                                String typefieldname = "_redkale_body_jsontype_" + bodyTypes.size();
                                bodyTypes.put(typefieldname, (java.lang.reflect.Type) en.getValue()[2]);
                                mv.visitVarInsn(ALOAD, 0);
                                mv.visitFieldInsn(GETFIELD, newDynName, typefieldname, "Ljava/lang/reflect/Type;");
                                mv.visitMethodInsn(
                                        INVOKEVIRTUAL,
                                        reqInternalName,
                                        "getBodyJson",
                                        "(Ljava/lang/reflect/Type;)Ljava/lang/Object;",
                                        false);
                                mv.visitTypeInsn(CHECKCAST, Type.getInternalName((Class) en.getValue()[1]));
                                JsonFactory.root().loadDecoder((java.lang.reflect.Type) en.getValue()[2]);
                            } else if (en.getKey().contains("_uploadbytes_")) {
                                // 只需mv.visitVarInsn(ALOAD, 4), 无需处理
                            } else if (en.getKey().contains("_uploadfile_")) {
                                // 只需mv.visitVarInsn(ALOAD, 4), 无需处理
                            } else if (en.getKey().contains("_uploadfiles_")) {
                                // 只需mv.visitVarInsn(ALOAD, 4), 无需处理
                            }
                            mv.visitMethodInsn(
                                    INVOKEINTERFACE,
                                    attrInternalName,
                                    "set",
                                    "(Ljava/lang/Object;Ljava/lang/Object;)V",
                                    true);
                        }
                        mv.visitLabel(lif); // end if }
                        mv.visitFrame(
                                Opcodes.F_APPEND,
                                1,
                                new Object[] {ptype.getName().replace('.', '/')},
                                0,
                                null);
                    }
                }
                maxLocals++;
                paramMaps.add(paramMap);
            } // end params for each

            // mv.visitVarInsn(ALOAD, 0); //调用this
            // mv.visitFieldInsn(GETFIELD, newDynName, REST_SERVICE_FIELD_NAME, serviceDesc);
            mv.visitVarInsn(ALOAD, 3);
            for (int[] ins : varInsns) {
                mv.visitVarInsn(ins[0], ins[1]);
            }
            mv.visitMethodInsn(INVOKEVIRTUAL, serviceTypeInternalName, method.getName(), methodDesc, false);
            if (hasAsyncHandler) {
                mv.visitInsn(RETURN);
            } else if (returnType == void.class) {
                mv.visitVarInsn(ALOAD, 2);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, newDynName, REST_RETURNTYPES_FIELD_NAME, "[Ljava/lang/reflect/Type;");
                Asms.visitInsn(mv, entry.methodIdx); // 方法下标
                mv.visitInsn(AALOAD);
                mv.visitMethodInsn(INVOKESTATIC, retInternalName, "success", "()" + retDesc, false);
                mv.visitMethodInsn(
                        INVOKEVIRTUAL, respInternalName, "finishJson", "(" + typeDesc + "Ljava/lang/Object;)V", false);
                mv.visitInsn(RETURN);
            } else if (returnType == boolean.class) {
                mv.visitVarInsn(ISTORE, maxLocals);
                mv.visitVarInsn(ALOAD, 2); // response
                mv.visitVarInsn(ILOAD, maxLocals);
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", "(Z)Ljava/lang/String;", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, respInternalName, "finish", "(Ljava/lang/String;)V", false);
                mv.visitInsn(RETURN);
                maxLocals++;
            } else if (returnType == byte.class) {
                mv.visitVarInsn(ISTORE, maxLocals);
                mv.visitVarInsn(ALOAD, 2); // response
                mv.visitVarInsn(ILOAD, maxLocals);
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", "(I)Ljava/lang/String;", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, respInternalName, "finish", "(Ljava/lang/String;)V", false);
                mv.visitInsn(RETURN);
                maxLocals++;
            } else if (returnType == short.class) {
                mv.visitVarInsn(ISTORE, maxLocals);
                mv.visitVarInsn(ALOAD, 2); // response
                mv.visitVarInsn(ILOAD, maxLocals);
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", "(I)Ljava/lang/String;", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, respInternalName, "finish", "(Ljava/lang/String;)V", false);
                mv.visitInsn(RETURN);
                maxLocals++;
            } else if (returnType == char.class) {
                mv.visitVarInsn(ISTORE, maxLocals);
                mv.visitVarInsn(ALOAD, 2); // response
                mv.visitVarInsn(ILOAD, maxLocals);
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", "(C)Ljava/lang/String;", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, respInternalName, "finish", "(Ljava/lang/String;)V", false);
                mv.visitInsn(RETURN);
                maxLocals++;
            } else if (returnType == int.class) {
                mv.visitVarInsn(ISTORE, maxLocals);
                mv.visitVarInsn(ALOAD, 2); // response
                mv.visitVarInsn(ILOAD, maxLocals);
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", "(I)Ljava/lang/String;", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, respInternalName, "finish", "(Ljava/lang/String;)V", false);
                mv.visitInsn(RETURN);
                maxLocals++;
            } else if (returnType == float.class) {
                mv.visitVarInsn(FSTORE, maxLocals);
                mv.visitVarInsn(ALOAD, 2); // response
                mv.visitVarInsn(FLOAD, maxLocals);
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", "(F)Ljava/lang/String;", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, respInternalName, "finish", "(Ljava/lang/String;)V", false);
                mv.visitInsn(RETURN);
                maxLocals++;
            } else if (returnType == long.class) {
                mv.visitVarInsn(LSTORE, maxLocals);
                mv.visitVarInsn(ALOAD, 2); // response
                mv.visitVarInsn(LLOAD, maxLocals);
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", "(J)Ljava/lang/String;", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, respInternalName, "finish", "(Ljava/lang/String;)V", false);
                mv.visitInsn(RETURN);
                maxLocals += 2;
            } else if (returnType == double.class) {
                mv.visitVarInsn(DSTORE, maxLocals);
                mv.visitVarInsn(ALOAD, 2); // response
                mv.visitVarInsn(DLOAD, maxLocals);
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", "(D)Ljava/lang/String;", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, respInternalName, "finish", "(Ljava/lang/String;)V", false);
                mv.visitInsn(RETURN);
                maxLocals += 2;
            } else if (returnType == byte[].class) {
                mv.visitVarInsn(ASTORE, maxLocals);
                mv.visitVarInsn(ALOAD, 2); // response
                mv.visitVarInsn(ALOAD, maxLocals);
                mv.visitMethodInsn(INVOKEVIRTUAL, respInternalName, "finish", "([B)V", false);
                mv.visitInsn(RETURN);
                maxLocals++;
            } else if (returnType == String.class) {
                mv.visitVarInsn(ASTORE, maxLocals);
                mv.visitVarInsn(ALOAD, 2); // response
                mv.visitVarInsn(ALOAD, maxLocals);
                mv.visitMethodInsn(INVOKEVIRTUAL, respInternalName, "finish", "(Ljava/lang/String;)V", false);
                mv.visitInsn(RETURN);
                maxLocals++;
            } else if (returnType == File.class) {
                mv.visitVarInsn(ASTORE, maxLocals);
                mv.visitVarInsn(ALOAD, 2); // response
                mv.visitVarInsn(ALOAD, maxLocals);
                mv.visitMethodInsn(INVOKEVIRTUAL, respInternalName, "finish", "(Ljava/io/File;)V", false);
                mv.visitInsn(RETURN);
                maxLocals++;
            } else if (Number.class.isAssignableFrom(returnType)
                    || CharSequence.class.isAssignableFrom(returnType)) { // returnType == String.class 必须放在前面
                mv.visitVarInsn(ASTORE, maxLocals);
                mv.visitVarInsn(ALOAD, 2); // response
                mv.visitVarInsn(ALOAD, maxLocals);
                mv.visitMethodInsn(
                        INVOKESTATIC, "java/lang/String", "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, respInternalName, "finish", "(Ljava/lang/String;)V", false);
                mv.visitInsn(RETURN);
                maxLocals++;
            } else if (RetResult.class.isAssignableFrom(returnType)) {
                mv.visitVarInsn(ASTORE, maxLocals);
                mv.visitVarInsn(ALOAD, 2); // response
                if (hasResConvert) {
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(
                            GETFIELD, newDynName, REST_CONVERT_FIELD_PREFIX + restConverts.size(), convertDesc);
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, newDynName, REST_RETURNTYPES_FIELD_NAME, "[Ljava/lang/reflect/Type;");
                    Asms.visitInsn(mv, entry.methodIdx); // 方法下标
                    mv.visitInsn(AALOAD);
                    mv.visitVarInsn(ALOAD, maxLocals);
                    mv.visitMethodInsn(
                            INVOKEVIRTUAL,
                            respInternalName,
                            "finish",
                            "(" + convertDesc + typeDesc + retDesc + ")V",
                            false);
                } else {
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, newDynName, REST_RETURNTYPES_FIELD_NAME, "[Ljava/lang/reflect/Type;");
                    Asms.visitInsn(mv, entry.methodIdx); // 方法下标
                    mv.visitInsn(AALOAD);
                    mv.visitVarInsn(ALOAD, maxLocals);
                    mv.visitMethodInsn(
                            INVOKEVIRTUAL, respInternalName, "finish", "(" + typeDesc + retDesc + ")V", false);
                }
                mv.visitInsn(RETURN);
                maxLocals++;
            } else if (HttpResult.class.isAssignableFrom(returnType)) {
                mv.visitVarInsn(ASTORE, maxLocals);
                mv.visitVarInsn(ALOAD, 2); // response
                if (hasResConvert) {
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(
                            GETFIELD, newDynName, REST_CONVERT_FIELD_PREFIX + restConverts.size(), convertDesc);
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, newDynName, REST_RETURNTYPES_FIELD_NAME, "[Ljava/lang/reflect/Type;");
                    Asms.visitInsn(mv, entry.methodIdx); // 方法下标
                    mv.visitInsn(AALOAD);
                    mv.visitVarInsn(ALOAD, maxLocals);
                    mv.visitMethodInsn(
                            INVOKEVIRTUAL,
                            respInternalName,
                            "finish",
                            "(" + convertDesc + typeDesc + httpResultDesc + ")V",
                            false);
                } else {
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, newDynName, REST_RETURNTYPES_FIELD_NAME, "[Ljava/lang/reflect/Type;");
                    Asms.visitInsn(mv, entry.methodIdx); // 方法下标
                    mv.visitInsn(AALOAD);
                    mv.visitVarInsn(ALOAD, maxLocals);
                    mv.visitMethodInsn(
                            INVOKEVIRTUAL, respInternalName, "finish", "(" + typeDesc + httpResultDesc + ")V", false);
                }
                mv.visitInsn(RETURN);
                maxLocals++;
            } else if (HttpScope.class.isAssignableFrom(returnType)) {
                mv.visitVarInsn(ASTORE, maxLocals);
                mv.visitVarInsn(ALOAD, 2); // response
                if (hasResConvert) {
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(
                            GETFIELD, newDynName, REST_CONVERT_FIELD_PREFIX + restConverts.size(), convertDesc);
                    mv.visitVarInsn(ALOAD, maxLocals);
                    mv.visitMethodInsn(
                            INVOKEVIRTUAL, respInternalName, "finish", "(" + convertDesc + httpScopeDesc + ")V", false);
                } else {
                    mv.visitVarInsn(ALOAD, maxLocals);
                    mv.visitMethodInsn(INVOKEVIRTUAL, respInternalName, "finish", "(" + httpScopeDesc + ")V", false);
                }
                mv.visitInsn(RETURN);
                maxLocals++;
            } else if (CompletionStage.class.isAssignableFrom(returnType)) {
                mv.visitVarInsn(ASTORE, maxLocals);
                mv.visitVarInsn(ALOAD, 2); // response
                Class returnNoFutureType = TypeToken.typeToClassOrElse(returnGenericNoFutureType, Object.class);
                if (returnNoFutureType == HttpScope.class) {
                    if (hasResConvert) {
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitFieldInsn(
                                GETFIELD, newDynName, REST_CONVERT_FIELD_PREFIX + restConverts.size(), convertDesc);
                        mv.visitVarInsn(ALOAD, maxLocals);
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL,
                                respInternalName,
                                "finishScopeFuture",
                                "(" + convertDesc + stageDesc + ")V",
                                false);
                    } else {
                        mv.visitVarInsn(ALOAD, maxLocals);
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL, respInternalName, "finishScopeFuture", "(" + stageDesc + ")V", false);
                    }
                } else if (returnNoFutureType != byte[].class
                        && returnNoFutureType != RetResult.class
                        && returnNoFutureType != HttpResult.class
                        && returnNoFutureType != File.class
                        && !((returnGenericNoFutureType instanceof Class)
                                && (((Class) returnGenericNoFutureType).isPrimitive()
                                        || CharSequence.class.isAssignableFrom((Class) returnGenericNoFutureType)))) {
                    if (hasResConvert) {
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitFieldInsn(
                                GETFIELD, newDynName, REST_CONVERT_FIELD_PREFIX + restConverts.size(), convertDesc);
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitFieldInsn(
                                GETFIELD, newDynName, REST_RETURNTYPES_FIELD_NAME, "[Ljava/lang/reflect/Type;");
                        Asms.visitInsn(mv, entry.methodIdx); // 方法下标
                        mv.visitInsn(AALOAD);
                        mv.visitVarInsn(ALOAD, maxLocals);
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL,
                                respInternalName,
                                "finishJsonFuture",
                                "(" + convertDesc + typeDesc + stageDesc + ")V",
                                false);
                    } else {
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitFieldInsn(
                                GETFIELD, newDynName, REST_RETURNTYPES_FIELD_NAME, "[Ljava/lang/reflect/Type;");
                        Asms.visitInsn(mv, entry.methodIdx); // 方法下标
                        mv.visitInsn(AALOAD);
                        mv.visitVarInsn(ALOAD, maxLocals);
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL,
                                respInternalName,
                                "finishJsonFuture",
                                "(" + typeDesc + stageDesc + ")V",
                                false);
                    }
                } else {
                    if (hasResConvert) {
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitFieldInsn(
                                GETFIELD, newDynName, REST_CONVERT_FIELD_PREFIX + restConverts.size(), convertDesc);
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitFieldInsn(
                                GETFIELD, newDynName, REST_RETURNTYPES_FIELD_NAME, "[Ljava/lang/reflect/Type;");
                        Asms.visitInsn(mv, entry.methodIdx); // 方法下标
                        mv.visitInsn(AALOAD);
                        mv.visitVarInsn(ALOAD, maxLocals);
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL,
                                respInternalName,
                                "finishFuture",
                                "(" + convertDesc + typeDesc + stageDesc + ")V",
                                false);
                    } else {
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitFieldInsn(
                                GETFIELD, newDynName, REST_RETURNTYPES_FIELD_NAME, "[Ljava/lang/reflect/Type;");
                        Asms.visitInsn(mv, entry.methodIdx); // 方法下标
                        mv.visitInsn(AALOAD);
                        mv.visitVarInsn(ALOAD, maxLocals);
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL,
                                respInternalName,
                                "finishFuture",
                                "(" + typeDesc + stageDesc + ")V",
                                false);
                    }
                }
                mv.visitInsn(RETURN);
                maxLocals++;
            } else if (Flows.maybePublisherClass(returnType)) { // Flow.Publisher
                mv.visitVarInsn(ASTORE, maxLocals);
                mv.visitVarInsn(ALOAD, 2); // response
                if (hasResConvert) {
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(
                            GETFIELD, newDynName, REST_CONVERT_FIELD_PREFIX + restConverts.size(), convertDesc);
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, newDynName, REST_RETURNTYPES_FIELD_NAME, "[Ljava/lang/reflect/Type;");
                    Asms.visitInsn(mv, entry.methodIdx); // 方法下标
                    mv.visitInsn(AALOAD);
                    mv.visitVarInsn(ALOAD, maxLocals);
                    mv.visitMethodInsn(
                            INVOKEVIRTUAL,
                            respInternalName,
                            "finishPublisher",
                            "(" + convertDesc + typeDesc + "Ljava/lang/Object;)V",
                            false);
                } else {
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, newDynName, REST_RETURNTYPES_FIELD_NAME, "[Ljava/lang/reflect/Type;");
                    Asms.visitInsn(mv, entry.methodIdx); // 方法下标
                    mv.visitInsn(AALOAD);
                    mv.visitVarInsn(ALOAD, maxLocals);
                    mv.visitMethodInsn(
                            INVOKEVIRTUAL,
                            respInternalName,
                            "finishPublisher",
                            "(" + typeDesc + "Ljava/lang/Object;)V",
                            false);
                }
                mv.visitInsn(RETURN);
                maxLocals++;
            } else if (returnType == retvalType) { // 普通JavaBean或JavaBean[]
                mv.visitVarInsn(ASTORE, maxLocals);
                mv.visitVarInsn(ALOAD, 2); // response
                if (hasResConvert) {
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(
                            GETFIELD, newDynName, REST_CONVERT_FIELD_PREFIX + restConverts.size(), convertDesc);
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, newDynName, REST_RETURNTYPES_FIELD_NAME, "[Ljava/lang/reflect/Type;");
                    Asms.visitInsn(mv, entry.methodIdx); // 方法下标
                    mv.visitInsn(AALOAD);
                    mv.visitVarInsn(ALOAD, maxLocals);
                    mv.visitMethodInsn(
                            INVOKEVIRTUAL,
                            respInternalName,
                            "finishJson",
                            "(" + convertDesc + typeDesc + "Ljava/lang/Object;)V",
                            false);
                } else {
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, newDynName, REST_RETURNTYPES_FIELD_NAME, "[Ljava/lang/reflect/Type;");
                    Asms.visitInsn(mv, entry.methodIdx); // 方法下标
                    mv.visitInsn(AALOAD);
                    mv.visitVarInsn(ALOAD, maxLocals);
                    mv.visitMethodInsn(
                            INVOKEVIRTUAL,
                            respInternalName,
                            "finishJson",
                            "(" + typeDesc + "Ljava/lang/Object;)V",
                            false);
                }
                mv.visitInsn(RETURN);
                maxLocals++;
            } else {
                mv.visitVarInsn(ASTORE, maxLocals);
                mv.visitVarInsn(ALOAD, 2); // response
                if (hasResConvert) {
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(
                            GETFIELD, newDynName, REST_CONVERT_FIELD_PREFIX + restConverts.size(), convertDesc);
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, newDynName, REST_RETURNTYPES_FIELD_NAME, "[Ljava/lang/reflect/Type;");
                    Asms.visitInsn(mv, entry.methodIdx); // 方法下标
                    mv.visitInsn(AALOAD);
                    mv.visitVarInsn(ALOAD, maxLocals);
                    mv.visitMethodInsn(
                            INVOKEVIRTUAL,
                            respInternalName,
                            "finish",
                            "(" + convertDesc + typeDesc + "Ljava/lang/Object;)V",
                            false);
                } else {
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, newDynName, REST_RETURNTYPES_FIELD_NAME, "[Ljava/lang/reflect/Type;");
                    Asms.visitInsn(mv, entry.methodIdx); // 方法下标
                    mv.visitInsn(AALOAD);
                    mv.visitVarInsn(ALOAD, maxLocals);
                    mv.visitMethodInsn(
                            INVOKEVIRTUAL, respInternalName, "finish", "(" + typeDesc + "Ljava/lang/Object;)V", false);
                }
                mv.visitInsn(RETURN);
                maxLocals++;
            }
            mv.visitMaxs(maxStack, maxLocals);
            mappingMap.put("params", paramMaps);

            { // _Dync_XXX__HttpServlet.class
                ClassWriter cw2 = new ClassWriter(COMPUTE_FRAMES);
                cw2.visit(V11, ACC_SUPER, newDynName + "$" + entry.newActionClassName, null, httpServletName, null);

                cw2.visitInnerClass(
                        newDynName + "$" + entry.newActionClassName,
                        newDynName,
                        entry.newActionClassName,
                        ACC_PRIVATE + ACC_STATIC);
                { // 设置 Annotation NonBlocking
                    av0 = cw2.visitAnnotation(nonblockDesc, true);
                    av0.visit("value", entry.nonBlocking);
                    av0.visitEnd();
                }
                {
                    fv = cw2.visitField(0, "_parentServlet", "L" + newDynName + ";", null, null);
                    fv.visitEnd();
                }
                {
                    mv = new MethodDebugVisitor(cw2.visitMethod(0, "<init>", "(L" + newDynName + ";)V", null, null));
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitMethodInsn(INVOKESPECIAL, httpServletName, "<init>", "()V", false);
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitFieldInsn(
                            PUTFIELD,
                            newDynName + "$" + entry.newActionClassName,
                            "_parentServlet",
                            "L" + newDynName + ";");
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitInsn(entry.nonBlocking ? ICONST_1 : ICONST_0);
                    mv.visitFieldInsn(PUTFIELD, newDynName + "$" + entry.newActionClassName, "_nonBlocking", "Z");
                    mv.visitInsn(RETURN);
                    mv.visitMaxs(2, 2);
                    mv.visitEnd();
                }
                //                if (false) {
                //                    mv = new MethodDebugVisitor(cw2.visitMethod(ACC_SYNTHETIC, "<init>", "(L" +
                // newDynName + ";L" + newDynName + "$" + entry.newActionClassName + ";)V", null, null));
                //                    mv.visitVarInsn(ALOAD, 0);
                //                    mv.visitVarInsn(ALOAD, 1);
                //                    mv.visitCheckCast(INVOKESPECIAL, newDynName + "$" + entry.newActionClassName,
                // "<init>", "L" + newDynName + ";", false);
                //                    mv.visitInsn(RETURN);
                //                    mv.visitMaxs(2, 3);
                //                    mv.visitEnd();
                //                }
                {
                    mv = new MethodDebugVisitor(
                            cw2.visitMethod(ACC_PUBLIC, "execute", "(" + reqDesc + respDesc + ")V", null, new String[] {
                                "java/io/IOException"
                            }));
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(
                            GETFIELD,
                            newDynName + "$" + entry.newActionClassName,
                            "_parentServlet",
                            "L" + newDynName + ";");
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitMethodInsn(
                            INVOKEVIRTUAL, newDynName, entry.newMethodName, "(" + reqDesc + respDesc + ")V", false);
                    mv.visitInsn(RETURN);
                    mv.visitMaxs(3, 3);
                    mv.visitEnd();
                }
                cw2.visitEnd();
                byte[] bytes = cw2.toByteArray();
                classLoader.addDynClass((newDynName + "$" + entry.newActionClassName).replace('/', '.'), bytes);
                innerClassBytesMap.put((newDynName + "$" + entry.newActionClassName).replace('/', '.'), bytes);
            }
        } // end  for each

        if (containsMupload) { // 注入 @Resource(name = "APP_HOME")  private File _redkale_home;
            fv = cw.visitField(ACC_PRIVATE, "_redkale_home", Type.getDescriptor(File.class), null, null);
            av0 = fv.visitAnnotation(resDesc, true);
            av0.visit("name", "APP_HOME");
            av0.visitEnd();
            fv.visitEnd();
        }

        //        HashMap<String, ActionEntry> _createRestActionEntry() {
        //              HashMap<String, ActionEntry> map = new HashMap<>();
        //              map.put("asyncfind3", new ActionEntry(100000,200000,"asyncfind3", new
        // String[]{},null,false,false,0, new _Dync_asyncfind3_HttpServlet()));
        //              map.put("asyncfind2", new ActionEntry(1,2,"asyncfind2", new String[]{"GET",
        // "POST"},null,false,true,0, new _Dync_asyncfind2_HttpServlet()));
        //              return map;
        //          }
        { // _createRestActionEntry 方法
            mv = new MethodDebugVisitor(cw.visitMethod(
                    0,
                    "_createRestActionEntry",
                    "()Ljava/util/HashMap;",
                    "()Ljava/util/HashMap<Ljava/lang/String;L" + actionEntryName + ";>;",
                    null));
            // mv.setDebug(true);
            mv.visitTypeInsn(NEW, "java/util/HashMap");
            mv.visitInsn(DUP);
            mv.visitMethodInsn(INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false);
            mv.visitVarInsn(ASTORE, 1);

            for (final MappingEntry entry : entrys) {
                mappingurlToMethod.put(entry.mappingurl, entry.mappingMethod);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitLdcInsn(entry.mappingurl); // name
                mv.visitTypeInsn(NEW, actionEntryName); // new ActionEntry
                mv.visitInsn(DUP);
                Asms.visitInsn(mv, moduleid); // moduleid
                Asms.visitInsn(mv, entry.actionid); // actionid
                mv.visitLdcInsn(entry.mappingurl); // name
                Asms.visitInsn(mv, entry.methods.length); // methods
                mv.visitTypeInsn(ANEWARRAY, "java/lang/String");
                for (int i = 0; i < entry.methods.length; i++) {
                    mv.visitInsn(DUP);
                    Asms.visitInsn(mv, i);
                    mv.visitLdcInsn(entry.methods[i]);
                    mv.visitInsn(AASTORE);
                }
                mv.visitInsn(ACONST_NULL); // method
                mv.visitInsn(entry.rpcOnly ? ICONST_1 : ICONST_0); // rpcOnly
                mv.visitInsn(entry.auth ? ICONST_1 : ICONST_0); // auth
                Asms.visitInsn(mv, entry.cacheSeconds); // cacheSeconds
                mv.visitTypeInsn(NEW, newDynName + "$" + entry.newActionClassName);
                mv.visitInsn(DUP);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(
                        INVOKESPECIAL,
                        newDynName + "$" + entry.newActionClassName,
                        "<init>",
                        "(L" + newDynName + ";)V",
                        false);
                mv.visitMethodInsn(
                        INVOKESPECIAL,
                        actionEntryName,
                        "<init>",
                        "(IILjava/lang/String;[Ljava/lang/String;Ljava/lang/reflect/Method;ZZI" + httpDesc + ")V",
                        false);
                mv.visitMethodInsn(
                        INVOKEVIRTUAL,
                        "java/util/HashMap",
                        "put",
                        "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                        false);
                mv.visitInsn(POP);
            }
            mv.visitVarInsn(ALOAD, 1);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(2, 2);
            mv.visitEnd();
        }

        for (Map.Entry<String, java.lang.reflect.Type> en : bodyTypes.entrySet()) {
            fv = cw.visitField(ACC_PRIVATE, en.getKey(), "Ljava/lang/reflect/Type;", null, null);
            av0 = fv.visitAnnotation(Type.getDescriptor(Comment.class), true);
            av0.visit("value", en.getValue().toString());
            av0.visitEnd();
            fv.visitEnd();
        }

        for (Map.Entry<java.lang.reflect.Type, String> en : typeRefs.entrySet()) {
            fv = cw.visitField(ACC_PRIVATE, en.getValue(), "Ljava/lang/reflect/Type;", null, null);
            av0 = fv.visitAnnotation(Type.getDescriptor(Comment.class), true);
            av0.visit("value", en.getKey().toString());
            av0.visitEnd();
            fv.visitEnd();
        }

        for (Map.Entry<String, org.redkale.util.Attribute> en : restAttributes.entrySet()) {
            fv = cw.visitField(ACC_PRIVATE, en.getKey(), attrDesc, null, null);
            av0 = fv.visitAnnotation(Type.getDescriptor(Comment.class), true);
            av0.visit("value", en.getValue().toString());
            av0.visitEnd();
            fv.visitEnd();
        }

        for (int i = 1; i <= restConverts.size(); i++) {
            fv = cw.visitField(ACC_PRIVATE, REST_CONVERT_FIELD_PREFIX + i, convertDesc, null, null);
            fv.visitEnd();
        }

        { // _methodAnns字段 Annotation[][]
            fv = cw.visitField(ACC_PRIVATE, REST_METHOD_ANNS_NAME, "[[Ljava/lang/annotation/Annotation;", null, null);
            av0 = fv.visitAnnotation(Type.getDescriptor(Comment.class), true);
            StringBuilder sb = new StringBuilder().append('[');
            for (Annotation[] rs : methodAnns) {
                sb.append(Arrays.toString(rs)).append(',');
            }
            av0.visit("value", sb.append(']').toString());
            av0.visitEnd();
            fv.visitEnd();
        }
        { // _paramtypes字段 java.lang.reflect.Type[][]
            fv = cw.visitField(ACC_PRIVATE, REST_PARAMTYPES_FIELD_NAME, "[[Ljava/lang/reflect/Type;", null, null);
            av0 = fv.visitAnnotation(Type.getDescriptor(Comment.class), true);
            StringBuilder sb = new StringBuilder().append('[');
            for (java.lang.reflect.Type[] rs : paramTypes) {
                sb.append(Arrays.toString(rs)).append(',');
            }
            av0.visit("value", sb.append(']').toString());
            av0.visitEnd();
            fv.visitEnd();
        }
        { // _returntypes字段 java.lang.reflect.Type[]
            fv = cw.visitField(ACC_PRIVATE, REST_RETURNTYPES_FIELD_NAME, "[Ljava/lang/reflect/Type;", null, null);
            av0 = fv.visitAnnotation(Type.getDescriptor(Comment.class), true);
            av0.visit("value", retvalTypes.toString());
            av0.visitEnd();
            fv.visitEnd();
        }

        // classMap.put("mappings", mappingMaps); //不显示太多信息
        { // toString函数
            mv = new MethodDebugVisitor(cw.visitMethod(ACC_PUBLIC, "toString", "()Ljava/lang/String;", null, null));
            // mv.setDebug(true);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, newDynName, REST_TOSTRINGOBJ_FIELD_NAME, "Ljava/util/function/Supplier;");
            mv.visitMethodInsn(INVOKEINTERFACE, "java/util/function/Supplier", "get", "()Ljava/lang/Object;", true);
            mv.visitTypeInsn(CHECKCAST, "java/lang/String");
            mv.visitInsn(ARETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }

        { // RestDyn
            av0 = cw.visitAnnotation(Type.getDescriptor(RestDyn.class), true);
            av0.visit("simple", (Boolean) dynsimple);
            av0.visitEnd();
        }

        cw.visitEnd();
        byte[] bytes = cw.toByteArray();
        classLoader.addDynClass(newDynName.replace('/', '.'), bytes);
        try {
            Class<?> newClazz = classLoader.loadClass(newDynName.replace('/', '.'));
            innerClassBytesMap.forEach((n, bs) -> {
                try {
                    classLoader.loadClass(n, bs);
                    RedkaleClassLoader.putReflectionClass(n);
                } catch (Exception e) {
                    throw new RestException(e);
                }
            });
            RedkaleClassLoader.putReflectionDeclaredConstructors(newClazz, newDynName.replace('/', '.'));
            for (java.lang.reflect.Type t : retvalTypes) {
                JsonFactory.root().loadEncoder(t);
            }

            T obj = ((Class<T>) newClazz).getDeclaredConstructor().newInstance();
            {
                Field serviceField = newClazz.getDeclaredField(REST_SERVICE_FIELD_NAME);
                RedkaleClassLoader.putReflectionField(newDynName.replace('/', '.'), serviceField);
                Field servicemapField = newClazz.getDeclaredField(REST_SERVICEMAP_FIELD_NAME);
                RedkaleClassLoader.putReflectionField(newDynName.replace('/', '.'), servicemapField);
            }
            for (Map.Entry<java.lang.reflect.Type, String> en : typeRefs.entrySet()) {
                Field refField = newClazz.getDeclaredField(en.getValue());
                refField.setAccessible(true);
                refField.set(obj, en.getKey());
                RedkaleClassLoader.putReflectionField(newDynName.replace('/', '.'), refField);
            }
            for (Map.Entry<String, org.redkale.util.Attribute> en : restAttributes.entrySet()) {
                Field attrField = newClazz.getDeclaredField(en.getKey());
                attrField.setAccessible(true);
                attrField.set(obj, en.getValue());
                RedkaleClassLoader.putReflectionField(newDynName.replace('/', '.'), attrField);
            }
            for (Map.Entry<String, java.lang.reflect.Type> en : bodyTypes.entrySet()) {
                Field genField = newClazz.getDeclaredField(en.getKey());
                genField.setAccessible(true);
                genField.set(obj, en.getValue());
                RedkaleClassLoader.putReflectionField(newDynName.replace('/', '.'), genField);
            }
            for (int i = 0; i < restConverts.size(); i++) {
                Field genField = newClazz.getDeclaredField(REST_CONVERT_FIELD_PREFIX + (i + 1));
                genField.setAccessible(true);
                Object[] rc = restConverts.get(i);
                JsonFactory childFactory = createJsonFactory((RestConvert[]) rc[0], (RestConvertCoder[]) rc[1]);
                genField.set(obj, childFactory.getConvert());
                RedkaleClassLoader.putReflectionField(newDynName.replace('/', '.'), genField);
            }
            Field annsfield = newClazz.getDeclaredField(REST_METHOD_ANNS_NAME);
            annsfield.setAccessible(true);
            Annotation[][] methodAnnArray = new Annotation[methodAnns.size()][];
            methodAnnArray = methodAnns.toArray(methodAnnArray);
            annsfield.set(obj, methodAnnArray);
            RedkaleClassLoader.putReflectionField(newDynName.replace('/', '.'), annsfield);

            Field typesfield = newClazz.getDeclaredField(REST_PARAMTYPES_FIELD_NAME);
            typesfield.setAccessible(true);
            java.lang.reflect.Type[][] paramtypeArray = new java.lang.reflect.Type[paramTypes.size()][];
            paramtypeArray = paramTypes.toArray(paramtypeArray);
            typesfield.set(obj, paramtypeArray);
            RedkaleClassLoader.putReflectionField(newDynName.replace('/', '.'), typesfield);

            Field retfield = newClazz.getDeclaredField(REST_RETURNTYPES_FIELD_NAME);
            retfield.setAccessible(true);
            java.lang.reflect.Type[] rettypeArray = new java.lang.reflect.Type[retvalTypes.size()];
            rettypeArray = retvalTypes.toArray(rettypeArray);
            retfield.set(obj, rettypeArray);
            RedkaleClassLoader.putReflectionField(newDynName.replace('/', '.'), retfield);

            Field tostringfield = newClazz.getDeclaredField(REST_TOSTRINGOBJ_FIELD_NAME);
            tostringfield.setAccessible(true);
            java.util.function.Supplier<String> sSupplier =
                    () -> JsonConvert.root().convertTo(classMap);
            tostringfield.set(obj, sSupplier);
            RedkaleClassLoader.putReflectionField(newDynName.replace('/', '.'), tostringfield);

            Method restactMethod = newClazz.getDeclaredMethod("_createRestActionEntry");
            restactMethod.setAccessible(true);
            RedkaleClassLoader.putReflectionMethod(newDynName.replace('/', '.'), restactMethod);
            Field tmpEntrysField = HttpServlet.class.getDeclaredField("_actionmap");
            tmpEntrysField.setAccessible(true);
            HashMap<String, HttpServlet.ActionEntry> innerEntryMap = (HashMap) restactMethod.invoke(obj);
            for (Map.Entry<String, HttpServlet.ActionEntry> en : innerEntryMap.entrySet()) {
                Method m = mappingurlToMethod.get(en.getKey());
                if (m != null) {
                    en.getValue().annotations = HttpServlet.ActionEntry.annotations(m);
                }
            }
            tmpEntrysField.set(obj, innerEntryMap);
            RedkaleClassLoader.putReflectionField(HttpServlet.class.getName(), tmpEntrysField);

            Field nonblockField = Servlet.class.getDeclaredField("_nonBlocking");
            nonblockField.setAccessible(true);
            nonblockField.set(obj, parentNonBlocking == null || parentNonBlocking);
            RedkaleClassLoader.putReflectionField(Servlet.class.getName(), nonblockField);
            return obj;
        } catch (Throwable e) {
            throw new RestException(e);
        }
    }

    private static java.lang.reflect.Type formatRestReturnType(Method method, Class serviceType) {
        final Class returnType = method.getReturnType();
        java.lang.reflect.Type t = TypeToken.getGenericType(method.getGenericReturnType(), serviceType);
        if (method.getReturnType() == void.class) {
            return RetResult.TYPE_RET_STRING;
        } else if (HttpResult.class.isAssignableFrom(returnType)) {
            if (!(t instanceof ParameterizedType)) {
                return Object.class;
            }
            ParameterizedType pt = (ParameterizedType) t;
            return pt.getActualTypeArguments()[0];
        } else if (CompletionStage.class.isAssignableFrom(returnType)) {
            ParameterizedType pt = (ParameterizedType) t;
            java.lang.reflect.Type grt = pt.getActualTypeArguments()[0];
            Class gct = TypeToken.typeToClass(grt);
            if (HttpResult.class.isAssignableFrom(gct)) {
                if (!(grt instanceof ParameterizedType)) {
                    return Object.class;
                }
                ParameterizedType pt2 = (ParameterizedType) grt;
                return pt2.getActualTypeArguments()[0];
            } else if (gct == void.class || gct == Void.class) {
                return RetResult.TYPE_RET_STRING;
            } else {
                return grt;
            }
        } else if (Flows.maybePublisherClass(returnType)) {
            return Flows.maybePublisherSubType(t);
        }
        return t;
    }

    private static boolean checkName(String name) { // 只能是字母、数字和下划线，且不能以数字开头
        if (name.isEmpty()) {
            return true;
        }
        if (name.charAt(0) >= '0' && name.charAt(0) <= '9') {
            return false;
        }
        for (char ch : name.toCharArray()) {
            if (!((ch >= '0' && ch <= '9')
                    || ch == '_'
                    || (ch >= 'a' && ch <= 'z')
                    || (ch >= 'A' && ch <= 'Z'))) { // 不能含特殊字符
                return false;
            }
        }
        return true;
    }

    private static boolean checkName2(String name) { // 只能是字母、数字、短横、点和下划线，且不能以数字开头
        if (name.isEmpty()) {
            return true;
        }
        if (name.charAt(0) >= '0' && name.charAt(0) <= '9') {
            return false;
        }
        for (char ch : name.toCharArray()) {
            if (!((ch >= '0' && ch <= '9')
                    || ch == '_'
                    || ch == '-'
                    || ch == '.'
                    || (ch >= 'a' && ch <= 'z')
                    || (ch >= 'A' && ch <= 'Z'))) { // 不能含特殊字符
                return false;
            }
        }
        return true;
    }

    private static class MappingAnn {

        public final boolean ignore;

        public final String name;

        public final String example;

        public final String comment;

        public final boolean rpcOnly;

        public final boolean auth;

        public final int actionid;

        public final int cacheSeconds;

        public final String[] methods;

        public MappingAnn(Method method, RestDeleteMapping mapping) {
            this(
                    mapping.ignore(),
                    mapping.name().trim().isEmpty()
                            ? method.getName()
                            : mapping.name().trim(),
                    mapping.example(),
                    mapping.comment(),
                    mapping.rpcOnly(),
                    mapping.auth(),
                    mapping.actionid(),
                    mapping.cacheSeconds(),
                    new String[] {"DELETE"});
        }

        public MappingAnn(Method method, RestPatchMapping mapping) {
            this(
                    mapping.ignore(),
                    mapping.name().trim().isEmpty()
                            ? method.getName()
                            : mapping.name().trim(),
                    mapping.example(),
                    mapping.comment(),
                    mapping.rpcOnly(),
                    mapping.auth(),
                    mapping.actionid(),
                    mapping.cacheSeconds(),
                    new String[] {"PATCH"});
        }

        public MappingAnn(Method method, RestPutMapping mapping) {
            this(
                    mapping.ignore(),
                    mapping.name().trim().isEmpty()
                            ? method.getName()
                            : mapping.name().trim(),
                    mapping.example(),
                    mapping.comment(),
                    mapping.rpcOnly(),
                    mapping.auth(),
                    mapping.actionid(),
                    mapping.cacheSeconds(),
                    new String[] {"PUT"});
        }

        public MappingAnn(Method method, RestPostMapping mapping) {
            this(
                    mapping.ignore(),
                    mapping.name().trim().isEmpty()
                            ? method.getName()
                            : mapping.name().trim(),
                    mapping.example(),
                    mapping.comment(),
                    mapping.rpcOnly(),
                    mapping.auth(),
                    mapping.actionid(),
                    mapping.cacheSeconds(),
                    new String[] {"POST"});
        }

        public MappingAnn(Method method, RestGetMapping mapping) {
            this(
                    mapping.ignore(),
                    mapping.name().trim().isEmpty()
                            ? method.getName()
                            : mapping.name().trim(),
                    mapping.example(),
                    mapping.comment(),
                    mapping.rpcOnly(),
                    mapping.auth(),
                    mapping.actionid(),
                    mapping.cacheSeconds(),
                    new String[] {"GET"});
        }

        public MappingAnn(Method method, RestMapping mapping) {
            this(
                    mapping.ignore(),
                    mapping.name().trim().isEmpty()
                            ? method.getName()
                            : mapping.name().trim(),
                    mapping.example(),
                    mapping.comment(),
                    mapping.rpcOnly(),
                    mapping.auth(),
                    mapping.actionid(),
                    mapping.cacheSeconds(),
                    mapping.methods());
        }

        public MappingAnn(
                boolean ignore,
                String name,
                String example,
                String comment,
                boolean rpcOnly,
                boolean auth,
                int actionid,
                int cacheSeconds,
                String[] methods) {
            this.ignore = ignore;
            this.name = name;
            this.example = example;
            this.comment = comment;
            this.rpcOnly = rpcOnly;
            this.auth = auth;
            this.actionid = actionid;
            this.cacheSeconds = cacheSeconds;
            this.methods = methods;
        }

        public static List<MappingAnn> paraseMappingAnns(RestService controller, Method method) {
            RestMapping[] mappings = method.getAnnotationsByType(RestMapping.class);
            RestGetMapping[] mappings2 = method.getAnnotationsByType(RestGetMapping.class);
            RestPostMapping[] mappings3 = method.getAnnotationsByType(RestPostMapping.class);
            RestPutMapping[] mappings4 = method.getAnnotationsByType(RestPutMapping.class);
            RestPatchMapping[] mappings5 = method.getAnnotationsByType(RestPatchMapping.class);
            RestDeleteMapping[] mappings6 = method.getAnnotationsByType(RestDeleteMapping.class);
            int len = mappings.length
                    + mappings2.length
                    + mappings3.length
                    + mappings4.length
                    + mappings5.length
                    + mappings6.length;
            if (!controller.autoMapping() && len < 1) {
                return null;
            }
            boolean ignore = false;
            for (RestMapping mapping : mappings) {
                if (mapping.ignore()) {
                    ignore = true;
                    break;
                }
            }
            if (!ignore) {
                for (RestGetMapping mapping : mappings2) {
                    if (mapping.ignore()) {
                        ignore = true;
                        break;
                    }
                }
            }
            if (!ignore) {
                for (RestPostMapping mapping : mappings3) {
                    if (mapping.ignore()) {
                        ignore = true;
                        break;
                    }
                }
            }
            if (!ignore) {
                for (RestPutMapping mapping : mappings4) {
                    if (mapping.ignore()) {
                        ignore = true;
                        break;
                    }
                }
            }
            if (!ignore) {
                for (RestPatchMapping mapping : mappings5) {
                    if (mapping.ignore()) {
                        ignore = true;
                        break;
                    }
                }
            }
            if (!ignore) {
                for (RestDeleteMapping mapping : mappings6) {
                    if (mapping.ignore()) {
                        ignore = true;
                        break;
                    }
                }
            }
            if (ignore) {
                return null;
            }
            List<MappingAnn> list = new ArrayList<>();
            for (RestMapping mapping : mappings) {
                list.add(new MappingAnn(method, mapping));
            }
            for (RestGetMapping mapping : mappings2) {
                list.add(new MappingAnn(method, mapping));
            }
            for (RestPostMapping mapping : mappings3) {
                list.add(new MappingAnn(method, mapping));
            }
            for (RestPutMapping mapping : mappings4) {
                list.add(new MappingAnn(method, mapping));
            }
            for (RestPatchMapping mapping : mappings5) {
                list.add(new MappingAnn(method, mapping));
            }
            for (RestDeleteMapping mapping : mappings6) {
                list.add(new MappingAnn(method, mapping));
            }
            return list;
        }
    }

    private static class MappingEntry implements Comparable<MappingEntry> {

        static final RestMapping DEFAULT__MAPPING;

        static {
            try {
                DEFAULT__MAPPING =
                        MappingEntry.class.getDeclaredMethod("mapping").getAnnotation(RestMapping.class);
            } catch (Exception e) {
                throw new Error(e);
            }
        }

        private static String formatMappingName(String name) {
            if (name.isEmpty()) {
                return name;
            }
            boolean normal = true; // 是否包含特殊字符
            for (char ch : name.toCharArray()) {
                if (ch >= '0' && ch <= '9') {
                    continue;
                }
                if (ch >= 'a' && ch <= 'z') {
                    continue;
                }
                if (ch >= 'A' && ch <= 'Z') {
                    continue;
                }
                if (ch == '_' || ch == '$') {
                    continue;
                }
                normal = false;
                break;
            }
            return normal ? name : Utility.md5Hex(name);
        }

        public MappingEntry(
                final boolean serRpcOnly,
                int methodIndex,
                Boolean typeNonBlocking,
                MappingAnn mapping,
                final String defModuleName,
                Method method) {
            this.methodIdx = methodIndex;
            this.mappingMethod = method;
            this.ignore = mapping.ignore;
            this.name = mapping.name;
            this.example = mapping.example;
            this.methods = mapping.methods;
            this.auth = mapping.auth;
            this.rpcOnly = serRpcOnly || mapping.rpcOnly;
            this.actionid = mapping.actionid;
            this.cacheSeconds = mapping.cacheSeconds;
            this.comment = mapping.comment;
            boolean pound = false;
            Parameter[] params = method.getParameters();
            for (Parameter param : params) {
                RestParam rp = param.getAnnotation(RestParam.class);
                String pn = null;
                if (rp != null && !rp.name().isEmpty()) {
                    pn = rp.name();
                } else {
                    Param pm = param.getAnnotation(Param.class);
                    if (pm != null && !pm.value().isEmpty()) {
                        pn = pm.value();
                    }
                }
                if (pn != null && pn.charAt(0) == '#') {
                    pound = true;
                    break;
                }
            }
            this.existsPound = pound;
            this.newMethodName = formatMappingName(
                    this.name.replace('/', '$').replace('.', '_').replace('-', '_'));
            this.newActionClassName = "_Dyn_" + this.newMethodName + "_ActionHttpServlet";

            NonBlocking non = method.getAnnotation(NonBlocking.class);
            Boolean nonFlag = non == null ? typeNonBlocking : (Boolean) non.value(); // 显注在方法优先级大于类
            if (nonFlag == null) {
                if (CompletionStage.class.isAssignableFrom(method.getReturnType())) {
                    nonFlag = true;
                } else {
                    for (Parameter mp : method.getParameters()) {
                        if (CompletionHandler.class.isAssignableFrom(mp.getType())) {
                            nonFlag = true;
                            break;
                        }
                    }
                }
            }
            this.nonBlocking = nonFlag != null && nonFlag;
        }

        public final int methodIdx; // _paramtypes 的下标，从0开始

        public final Method mappingMethod;

        public final String newMethodName;

        public final String newActionClassName;

        public final boolean nonBlocking;

        public final boolean existsPound; // 是否包含#的参数

        public final boolean ignore;

        public final String name;

        public final String example;

        public final String comment;

        public final boolean rpcOnly;

        public final boolean auth;

        public final int actionid;

        public final int cacheSeconds;

        public final String[] methods;

        String mappingurl; // 在生成方法时赋值， 供 _createRestActionEntry 使用

        @RestMapping()
        void mapping() { // 用于获取Mapping 默认值
        }

        @Override
        public int hashCode() {
            return this.name.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            return this.name.equals(((MappingEntry) obj).name);
        }

        @Override
        public int compareTo(MappingEntry o) {
            return this.name.compareTo(o.name);
        }
    }
}
