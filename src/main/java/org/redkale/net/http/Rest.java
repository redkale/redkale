/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.lang.classfile.ClassFile.*;
import static org.redkale.util.Utility.isEmpty;

import java.io.*;
import java.lang.annotation.*;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassSignature;
import java.lang.classfile.Label;
import java.lang.classfile.MethodSignature;
import java.lang.classfile.Signature;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.*;
import java.lang.constant.*;
import java.lang.reflect.*;
import java.net.InetSocketAddress;
import java.nio.channels.CompletionHandler;
import java.util.*;
import java.util.concurrent.CompletionStage;
import org.redkale.annotation.*;
import org.redkale.annotation.Comment;
import org.redkale.bytecode.ByteCodes;
import org.redkale.bytecode.CodeMethodBean;
import org.redkale.bytecode.CodeMethodBoost;
import org.redkale.bytecode.CodeMethodParam;
import org.redkale.convert.*;
import org.redkale.convert.json.*;
import org.redkale.inject.ResourceFactory;
import org.redkale.mq.spi.MessageAgent;
import org.redkale.net.*;
import org.redkale.net.sncp.Sncp;
import org.redkale.service.*;
import org.redkale.source.Flipper;
import org.redkale.util.*;

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
                if (field.getAnnotation(Resource.class) == null) {
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
        final Map<String, CodeMethodBean> methodBeanMap =
                namePresent ? null : CodeMethodBoost.getMethodBeans(webSocketType);
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
        final String resDesc = ByteCodes.descriptor(Resource.class);
        final String wsDesc = ByteCodes.descriptor(WebSocket.class);
        final String wsParamDesc = ByteCodes.descriptor(WebSocketParam.class);
        final String jsonConvertDesc = ByteCodes.descriptor(JsonConvert.class);
        final String convertDisabledDesc = ByteCodes.descriptor(ConvertDisabled.class);
        final String webSocketParamName = ByteCodes.internalName(WebSocketParam.class);
        final String supDynName = WebSocketServlet.class.getName().replace('.', '/');
        final String webServletDesc = ByteCodes.descriptor(WebServlet.class);
        final String webSocketInternalName = ByteCodes.internalName(webSocketType);

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
            sb1.append(ByteCodes.descriptor(field.getType()));
            sb2.append(Utility.getTypeDescriptor(field.getGenericType()));
        }
        final String serviceParamsDesc = sb1.toString();
        final String serviceParamsGenericDesc = sb1.equals(sb2) ? null : sb2.toString();
        // ----------------------------------------------------------------------------------------

        Map<String, Annotation[]> msgclassToAnnotations = new HashMap<>();
        final Method wildcard = wildcardMethod;
        byte[] classBytes = ClassFile.of().build(ByteCodes.classDesc(newDynName), cw -> {
            cw.withVersion(JAVA_11_VERSION, 0)
                    .withFlags(ACC_PUBLIC + ACC_SUPER)
                    .withSuperclass(ByteCodes.classDesc(supDynName));
            List<java.lang.classfile.Annotation> cwAnnotations = new ArrayList<>();
            List<InnerClassInfo> cwInnerClasses = new ArrayList<>();

            { // RestDyn
                {
                    List<AnnotationElement> av0 = new ArrayList<>();
                    av0.add(AnnotationElement.of(
                            "simple", ByteCodes.annotationValue(false))); // WebSocketServlet必须要解析http-header
                    {
                        {
                            List<AnnotationValue> av1 = new ArrayList<>();
                            av1.add(ByteCodes.annotationValue(
                                    ByteCodes.constantType("L" + newDynConsumerFullName.replace('.', '/') + ";")));
                            av1.add(ByteCodes.annotationValue(
                                    ByteCodes.constantType("L" + newDynWebSokcetFullName.replace('.', '/') + ";")));
                            av1.add(ByteCodes.annotationValue(
                                    ByteCodes.constantType("L" + newDynMessageFullName.replace('.', '/')
                                            + ";"))); // 位置固定第三个，下面用Message类进行loadDecoder会用到
                            av0.add(AnnotationElement.of("types", AnnotationValue.ofArray(av1)));
                        }
                    }
                    cwAnnotations.add(java.lang.classfile.Annotation.of(
                            ClassDesc.ofDescriptor(ByteCodes.descriptor(RestDyn.class)), av0));
                }
            }
            { // RestDynSourceType
                {
                    List<AnnotationElement> av0 = new ArrayList<>();
                    av0.add(AnnotationElement.of(
                            "value",
                            ByteCodes.annotationValue(ByteCodes.constantType(ByteCodes.descriptor(webSocketType)))));
                    cwAnnotations.add(java.lang.classfile.Annotation.of(
                            ClassDesc.ofDescriptor(ByteCodes.descriptor(RestDynSourceType.class)), av0));
                }
            }
            { // 注入 @WebServlet 注解
                String urlpath = (rws.catalog().isEmpty() ? "/" : ("/" + rws.catalog() + "/")) + rwsname;
                {
                    List<AnnotationElement> av0 = new ArrayList<>();
                    {
                        {
                            List<AnnotationValue> av1 = new ArrayList<>();
                            av1.add(ByteCodes.annotationValue(urlpath));
                            av0.add(AnnotationElement.of("value", AnnotationValue.ofArray(av1)));
                        }
                    }
                    av0.add(AnnotationElement.of("name", ByteCodes.annotationValue(rwsname)));
                    av0.add(AnnotationElement.of("moduleid", ByteCodes.annotationValue(0)));
                    av0.add(AnnotationElement.of("repair", ByteCodes.annotationValue(rws.repair())));
                    av0.add(AnnotationElement.of("comment", ByteCodes.annotationValue(rws.comment())));
                    cwAnnotations.add(java.lang.classfile.Annotation.of(ClassDesc.ofDescriptor(webServletDesc), av0));
                }
            }
            { // 内部类
                cwInnerClasses.add(InnerClassInfo.of(
                        ByteCodes.classDesc(newDynConsumerFullName),
                        Optional.ofNullable(newDynName).map(ByteCodes::classDesc),
                        Optional.ofNullable(newDynConsumerSimpleName),
                        ACC_PUBLIC + ACC_STATIC));

                cwInnerClasses.add(InnerClassInfo.of(
                        ByteCodes.classDesc(newDynWebSokcetFullName),
                        Optional.ofNullable(newDynName).map(ByteCodes::classDesc),
                        Optional.ofNullable(newDynWebSokcetSimpleName),
                        ACC_PUBLIC + ACC_STATIC));

                cwInnerClasses.add(InnerClassInfo.of(
                        ByteCodes.classDesc(newDynMessageFullName),
                        Optional.ofNullable(newDynName).map(ByteCodes::classDesc),
                        Optional.ofNullable(newDynMessageSimpleName),
                        ACC_PUBLIC + ACC_STATIC));

                for (int i = 0; i < messageMethods.size(); i++) {
                    Method method = messageMethods.get(i);
                    if (method == wildcard) continue;
                    String endfix = "_" + method.getName() + "_" + (i > 9 ? i : ("0" + i));
                    String newDynSuperMessageFullName = newDynMessageFullName + (method == wildcard ? "" : endfix);
                    cwInnerClasses.add(InnerClassInfo.of(
                            ByteCodes.classDesc(newDynSuperMessageFullName),
                            Optional.ofNullable(newDynName).map(ByteCodes::classDesc),
                            Optional.ofNullable(newDynMessageSimpleName + (method == wildcard ? "" : endfix)),
                            ACC_PUBLIC + ACC_STATIC));
                }
            }
            { // @Resource
                for (int i = 0; i < resourcesFields.size(); i++) {
                    Field field = resourcesFields.get(i);
                    Resource res = field.getAnnotation(Resource.class);
                    java.lang.reflect.Type fieldType = field.getGenericType();
                    cw.withField(
                            "_redkale_resource_" + i,
                            ClassDesc.ofDescriptor(ByteCodes.descriptor(field.getType())),
                            fv -> {
                                fv.withFlags(ACC_PRIVATE);
                                List<java.lang.classfile.Annotation> fvAnnotations = new ArrayList<>();
                                if (fieldType != field.getType())
                                    fv.with(SignatureAttribute.of(
                                            Signature.parseFrom(Utility.getTypeDescriptor(fieldType))));
                                {
                                    {
                                        List<AnnotationElement> av0 = new ArrayList<>();
                                        av0.add(AnnotationElement.of("name", ByteCodes.annotationValue(res.name())));
                                        av0.add(AnnotationElement.of(
                                                "required", ByteCodes.annotationValue(res == null || res.required())));
                                        fvAnnotations.add(java.lang.classfile.Annotation.of(
                                                ClassDesc.ofDescriptor(resDesc), av0));
                                    }
                                }

                                if (!fvAnnotations.isEmpty())
                                    fv.with(RuntimeVisibleAnnotationsAttribute.of(fvAnnotations));
                            });
                }
            }
            { // _redkale_annotations
                cw.withField("_redkale_annotations", ClassDesc.ofDescriptor("Ljava/util/Map;"), fv -> {
                    fv.withFlags(ACC_PUBLIC + ACC_STATIC);

                    fv.with(SignatureAttribute.of(Signature.parseFrom(
                            "Ljava/util/Map<Ljava/lang/String;[Ljava/lang/annotation/Annotation;>;")));
                });
            }
            { // _DynWebSocketServlet构造函数
                cw.withMethodBody("<init>", MethodTypeDesc.ofDescriptor("()V"), ACC_PUBLIC, mv -> {
                    mv.aload(0);
                    mv.invokespecial(
                            ByteCodes.classDesc(supDynName), "<init>", MethodTypeDesc.ofDescriptor("()V"), false);
                    mv.aload(0);
                    mv.loadConstant(ByteCodes.classDesc(newDynName + "$" + newDynWebSokcetSimpleName + "Message"));
                    mv.putfield(
                            ByteCodes.classDesc(newDynName),
                            "messageRestType",
                            ClassDesc.ofDescriptor("Ljava/lang/reflect/Type;"));

                    mv.aload(0);
                    mv.loadConstant(rws.liveinterval());
                    mv.putfield(ByteCodes.classDesc(newDynName), "liveinterval", ClassDesc.ofDescriptor("I"));

                    mv.aload(0);
                    mv.loadConstant(rws.wsmaxconns());
                    mv.putfield(ByteCodes.classDesc(newDynName), "wsmaxconns", ClassDesc.ofDescriptor("I"));

                    mv.aload(0);
                    mv.loadConstant(rws.wsmaxbody());
                    mv.putfield(ByteCodes.classDesc(newDynName), "wsmaxbody", ClassDesc.ofDescriptor("I"));

                    mv.aload(0);
                    mv.loadConstant(rws.single() ? 1 : 0);
                    mv.putfield(ByteCodes.classDesc(newDynName), "single", ClassDesc.ofDescriptor("Z"));

                    mv.aload(0);
                    mv.loadConstant(rws.anyuser() ? 1 : 0);
                    mv.putfield(ByteCodes.classDesc(newDynName), "anyuser", ClassDesc.ofDescriptor("Z"));

                    mv.return_();
                });
            }
            { // createWebSocket 方法
                cw.withMethod("createWebSocket", MethodTypeDesc.ofDescriptor("()" + wsDesc), ACC_PROTECTED, mb -> {
                    mb.with(SignatureAttribute.of(
                            MethodSignature.parseFrom("<G::Ljava/io/Serializable;T:Ljava/lang/Object;>()L"
                                    + WebSocket.class.getName().replace('.', '/') + "<TG;>;")));

                    mb.withCode(mv -> {
                        mv.new_(ByteCodes.classDesc(newDynName + "$" + newDynWebSokcetSimpleName));
                        mv.dup();
                        for (int i = 0; i < resourcesFields.size(); i++) {
                            mv.aload(0);
                            mv.getfield(
                                    ByteCodes.classDesc(newDynName),
                                    "_redkale_resource_" + i,
                                    ClassDesc.ofDescriptor(ByteCodes.descriptor(
                                            resourcesFields.get(i).getType())));
                        }
                        mv.invokespecial(
                                ByteCodes.classDesc(newDynWebSokcetFullName),
                                "<init>",
                                MethodTypeDesc.ofDescriptor("(" + serviceParamsDesc + ")V"),
                                false);
                        mv.areturn();
                    });
                });
            }
            { // createRestOnMessageConsumer
                cw.withMethod(
                        "createRestOnMessageConsumer",
                        MethodTypeDesc.ofDescriptor("()Ljava/util/function/BiConsumer;"),
                        ACC_PROTECTED,
                        mb -> {
                            mb.with(SignatureAttribute.of(MethodSignature.parseFrom(
                                    "()Ljava/util/function/BiConsumer<" + wsDesc + "Ljava/lang/Object;>;")));

                            mb.withCode(mv -> {
                                mv.new_(ByteCodes.classDesc(newDynConsumerFullName));
                                mv.dup();
                                mv.invokespecial(
                                        ByteCodes.classDesc(newDynConsumerFullName),
                                        "<init>",
                                        MethodTypeDesc.ofDescriptor("()V"),
                                        false);
                                mv.areturn();
                            });
                        });
            }
            { // resourceName
                cw.withMethodBody(
                        "resourceName", MethodTypeDesc.ofDescriptor("()Ljava/lang/String;"), ACC_PUBLIC, mv -> {
                            mv.loadConstant(rwsname);
                            mv.areturn();
                        });
            }

            for (int i = 0; i < messageMethods.size(); i++) { // _DyncXXXWebSocketMessage 子消息List
                final Method method = messageMethods.get(i);
                String endfix = "_" + method.getName() + "_" + (i > 9 ? i : ("0" + i));
                String newDynSuperMessageFullName = newDynMessageFullName + (method == wildcard ? "" : endfix);
                msgclassToAnnotations.put(newDynSuperMessageFullName, method.getAnnotations());

                byte[] innerBytes = ClassFile.of().build(ByteCodes.classDesc(newDynSuperMessageFullName), cw2 -> {
                    cw2.withVersion(JAVA_11_VERSION, 0)
                            .withFlags(ACC_PUBLIC + ACC_FINAL + ACC_SUPER)
                            .withSuperclass(ByteCodes.classDesc("java/lang/Object"));

                    List<InnerClassInfo> cw2InnerClasses = new ArrayList<>();
                    cw2.withInterfaceSymbols(Arrays.stream(new String[] {webSocketParamName, "java/lang/Runnable"})
                            .map(ByteCodes::classDesc)
                            .toList());
                    cw2InnerClasses.add(InnerClassInfo.of(
                            ByteCodes.classDesc(newDynSuperMessageFullName),
                            Optional.ofNullable(newDynName).map(ByteCodes::classDesc),
                            Optional.ofNullable(newDynMessageSimpleName + (method == wildcard ? "" : endfix)),
                            ACC_PUBLIC + ACC_STATIC));
                    Set<String> paramNames = new HashSet<>();
                    CodeMethodBean methodBean =
                            methodBeanMap == null ? null : CodeMethodBean.get(methodBeanMap, method);
                    List<CodeMethodParam> names = methodBean == null ? null : methodBean.getParams();
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
                        cw2.withField(paramName, ClassDesc.ofDescriptor(ByteCodes.descriptor(param.getType())), fv -> {
                            fv.withFlags(ACC_PUBLIC);

                            if (param.getType() != param.getParameterizedType())
                                fv.with(SignatureAttribute.of(
                                        Signature.parseFrom(Utility.getTypeDescriptor(param.getParameterizedType()))));
                        });
                    }
                    if (method == wildcard) {
                        for (int j = 0; j < messageMethods.size(); j++) {
                            Method method2 = messageMethods.get(j);
                            if (method2 == wildcard) {
                                continue;
                            }
                            String endfix2 = "_" + method2.getName() + "_" + (j > 9 ? j : ("0" + j));
                            String newDynSuperMessageFullName2 =
                                    newDynMessageFullName + (method2 == wildcard ? "" : endfix2);
                            cw2InnerClasses.add(InnerClassInfo.of(
                                    ByteCodes.classDesc(newDynSuperMessageFullName2),
                                    Optional.ofNullable(newDynName).map(ByteCodes::classDesc),
                                    Optional.ofNullable(newDynMessageSimpleName + endfix2),
                                    ACC_PUBLIC + ACC_STATIC));
                            cw2.withField(
                                    method2.getAnnotation(RestOnMessage.class).name(),
                                    ClassDesc.ofDescriptor("L" + newDynSuperMessageFullName2 + ";"),
                                    ACC_PUBLIC);
                        }
                    }
                    { // _redkale_websocket
                        cw2.withField(
                                "_redkale_websocket",
                                ClassDesc.ofDescriptor("L" + newDynWebSokcetFullName + ";"),
                                fv -> {
                                    fv.withFlags(ACC_PUBLIC);
                                    List<java.lang.classfile.Annotation> fvAnnotations = new ArrayList<>();

                                    {
                                        List<AnnotationElement> av0 = new ArrayList<>();
                                        fvAnnotations.add(java.lang.classfile.Annotation.of(
                                                ClassDesc.ofDescriptor(convertDisabledDesc), av0));
                                    }

                                    if (!fvAnnotations.isEmpty())
                                        fv.with(RuntimeVisibleAnnotationsAttribute.of(fvAnnotations));
                                });
                    }
                    { // 空构造函数
                        cw2.withMethodBody("<init>", MethodTypeDesc.ofDescriptor("()V"), ACC_PUBLIC, mv -> {
                            mv.aload(0);
                            mv.invokespecial(
                                    ByteCodes.classDesc("java/lang/Object"),
                                    "<init>",
                                    MethodTypeDesc.ofDescriptor("()V"),
                                    false);
                            mv.return_();
                        });
                    }
                    { // getNames
                        cw2.withMethod(
                                "getNames", MethodTypeDesc.ofDescriptor("()[Ljava/lang/String;"), ACC_PUBLIC, mb -> {
                                    List<java.lang.classfile.Annotation> mvAnnotations = new ArrayList<>();
                                    mb.withCode(mv -> {
                                        {
                                            List<AnnotationElement> av0 = new ArrayList<>();
                                            mvAnnotations.add(java.lang.classfile.Annotation.of(
                                                    ClassDesc.ofDescriptor(convertDisabledDesc), av0));
                                        }
                                        mv.loadConstant(paramap.size());
                                        mv.anewarray(ByteCodes.classDesc("java/lang/String"));
                                        int index = -1;
                                        for (Map.Entry<String, Parameter> en : paramap.entrySet()) {
                                            mv.dup();
                                            mv.loadConstant(++index);
                                            mv.loadConstant(en.getKey());
                                            mv.aastore();
                                        }
                                        mv.areturn();
                                    });
                                    if (!mvAnnotations.isEmpty())
                                        mb.with(RuntimeVisibleAnnotationsAttribute.of(mvAnnotations));
                                });
                    }
                    { // getValue
                        cw2.withMethod(
                                "getValue",
                                MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)Ljava/lang/Object;"),
                                ACC_PUBLIC,
                                mb -> {
                                    mb.with(SignatureAttribute.of(MethodSignature.parseFrom(
                                            "<T:Ljava/lang/Object;>(Ljava/lang/String;)TT;")));

                                    mb.withCode(mv -> {
                                        Label label0 = mv.newLabel();
                                        mv.labelBinding(label0);
                                        for (Map.Entry<String, Parameter> en : paramap.entrySet()) {
                                            Class paramType = en.getValue().getType();
                                            mv.loadConstant(en.getKey());
                                            mv.aload(1);
                                            mv.invokevirtual(
                                                    ByteCodes.classDesc("java/lang/String"),
                                                    "equals",
                                                    MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;)Z"));
                                            Label l1 = mv.newLabel();
                                            mv.ifeq(l1);
                                            mv.aload(0);
                                            mv.getfield(
                                                    ByteCodes.classDesc(newDynSuperMessageFullName),
                                                    en.getKey(),
                                                    ClassDesc.ofDescriptor(ByteCodes.descriptor(paramType)));
                                            if (paramType.isPrimitive()) {
                                                Class bigclaz = TypeToken.primitiveToWrapper(paramType);
                                                mv.invokestatic(
                                                        ByteCodes.classDesc(bigclaz.getName()
                                                                .replace('.', '/')),
                                                        "valueOf",
                                                        MethodTypeDesc.ofDescriptor(
                                                                "(" + ByteCodes.descriptor(paramType) + ")"
                                                                        + ByteCodes.descriptor(bigclaz)),
                                                        false);
                                            }
                                            mv.areturn();
                                            mv.labelBinding(l1);
                                        }
                                        mv.aconst_null();
                                        mv.areturn();
                                        Label label2 = mv.newLabel();
                                        mv.labelBinding(label2);
                                        mv.localVariable(
                                                0,
                                                "this",
                                                ClassDesc.ofDescriptor("L" + newDynSuperMessageFullName + ";"),
                                                label0,
                                                label2);
                                        mv.localVariable(
                                                1,
                                                "name",
                                                ClassDesc.ofDescriptor("Ljava/lang/String;"),
                                                label0,
                                                label2);
                                    });
                                });
                    }
                    { // getAnnotations
                        cw2.withMethod(
                                "getAnnotations",
                                MethodTypeDesc.ofDescriptor("()[Ljava/lang/annotation/Annotation;"),
                                ACC_PUBLIC,
                                mb -> {
                                    List<java.lang.classfile.Annotation> mvAnnotations = new ArrayList<>();
                                    mb.withCode(mv -> {
                                        {
                                            List<AnnotationElement> av0 = new ArrayList<>();
                                            mvAnnotations.add(java.lang.classfile.Annotation.of(
                                                    ClassDesc.ofDescriptor(convertDisabledDesc), av0));
                                        }
                                        mv.getstatic(
                                                ByteCodes.classDesc(newDynName),
                                                "_redkale_annotations",
                                                ClassDesc.ofDescriptor("Ljava/util/Map;"));
                                        mv.loadConstant(newDynSuperMessageFullName);
                                        mv.invokeinterface(
                                                ByteCodes.classDesc("java/util/Map"),
                                                "get",
                                                MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;)Ljava/lang/Object;"));
                                        mv.checkcast(ByteCodes.classDesc("[Ljava/lang/annotation/Annotation;"));
                                        mv.astore(1);
                                        mv.aload(1);
                                        Label l2 = mv.newLabel();
                                        mv.ifnonnull(l2);
                                        mv.iconst_0();
                                        mv.anewarray(ByteCodes.classDesc("java/lang/annotation/Annotation"));
                                        mv.areturn();
                                        mv.labelBinding(l2);

                                        mv.aload(1);
                                        mv.aload(1);
                                        mv.arraylength();
                                        mv.invokestatic(
                                                ByteCodes.classDesc("java/util/Arrays"),
                                                "copyOf",
                                                MethodTypeDesc.ofDescriptor(
                                                        "([Ljava/lang/Object;I)[Ljava/lang/Object;"),
                                                false);
                                        mv.checkcast(ByteCodes.classDesc("[Ljava/lang/annotation/Annotation;"));
                                        mv.areturn();
                                    });
                                    if (!mvAnnotations.isEmpty())
                                        mb.with(RuntimeVisibleAnnotationsAttribute.of(mvAnnotations));
                                });
                    }
                    { // execute
                        cw2.withMethodBody(
                                "execute",
                                MethodTypeDesc.ofDescriptor("(L" + newDynWebSokcetFullName + ";)V"),
                                ACC_PUBLIC,
                                mv -> {
                                    Label label0 = mv.newLabel();
                                    mv.labelBinding(label0);
                                    mv.aload(0);
                                    mv.aload(1);
                                    mv.putfield(
                                            ByteCodes.classDesc(newDynSuperMessageFullName),
                                            "_redkale_websocket",
                                            ClassDesc.ofDescriptor("L" + newDynWebSokcetFullName + ";"));
                                    mv.aload(1);
                                    mv.loadConstant(method.getAnnotation(RestOnMessage.class)
                                            .name());
                                    mv.aload(0);
                                    mv.aload(0);
                                    mv.invokevirtual(
                                            ByteCodes.classDesc(newDynWebSokcetFullName),
                                            "preOnMessage",
                                            MethodTypeDesc.ofDescriptor(
                                                    "(Ljava/lang/String;" + wsParamDesc + "Ljava/lang/Runnable;)V"));
                                    mv.return_();
                                    Label label2 = mv.newLabel();
                                    mv.labelBinding(label2);
                                    mv.localVariable(
                                            0,
                                            "this",
                                            ClassDesc.ofDescriptor("L" + newDynSuperMessageFullName + ";"),
                                            label0,
                                            label2);
                                    mv.localVariable(
                                            1,
                                            "websocket",
                                            ClassDesc.ofDescriptor("L" + newDynWebSokcetFullName + ";"),
                                            label0,
                                            label2);
                                });
                    }
                    { // run
                        cw2.withMethodBody("run", MethodTypeDesc.ofDescriptor("()V"), ACC_PUBLIC, mv -> {
                            mv.aload(0);
                            mv.getfield(
                                    ByteCodes.classDesc(newDynSuperMessageFullName),
                                    "_redkale_websocket",
                                    ClassDesc.ofDescriptor("L" + newDynWebSokcetFullName + ";"));

                            for (Map.Entry<String, Parameter> en : paramap.entrySet()) {
                                mv.aload(0);
                                mv.getfield(
                                        ByteCodes.classDesc((newDynSuperMessageFullName)),
                                        en.getKey(),
                                        ClassDesc.ofDescriptor(ByteCodes.descriptor(
                                                en.getValue().getType())));
                            }
                            mv.invokevirtual(
                                    ByteCodes.classDesc(newDynWebSokcetFullName),
                                    method.getName(),
                                    MethodTypeDesc.ofDescriptor(ByteCodes.methodDescriptor(method)));

                            mv.return_();
                        });
                    }
                    { // toString
                        cw2.withMethodBody(
                                "toString", MethodTypeDesc.ofDescriptor("()Ljava/lang/String;"), ACC_PUBLIC, mv -> {
                                    mv.invokestatic(
                                            ByteCodes.classDesc(
                                                    JsonConvert.class.getName().replace('.', '/')),
                                            "root",
                                            MethodTypeDesc.ofDescriptor("()" + jsonConvertDesc),
                                            false);
                                    mv.aload(0);
                                    mv.invokevirtual(
                                            ByteCodes.classDesc(
                                                    JsonConvert.class.getName().replace('.', '/')),
                                            "convertTo",
                                            MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;)Ljava/lang/String;"));
                                    mv.areturn();
                                });
                    }

                    if (!cw2InnerClasses.isEmpty()) cw2.with(InnerClassesAttribute.of(cw2InnerClasses));
                });
                byte[] bytes = innerBytes;
                classLoader.loadClass((newDynSuperMessageFullName).replace('/', '.'), bytes);
            }

            if (wildcard == null) { // _DynXXXWebSocketMessage class

                byte[] innerBytes = ClassFile.of().build(ByteCodes.classDesc(newDynMessageFullName), cw2 -> {
                    cw2.withVersion(JAVA_11_VERSION, 0)
                            .withFlags(ACC_PUBLIC + ACC_FINAL + ACC_SUPER)
                            .withSuperclass(ByteCodes.classDesc("java/lang/Object"));

                    List<InnerClassInfo> cw2InnerClasses = new ArrayList<>();

                    cw2InnerClasses.add(InnerClassInfo.of(
                            ByteCodes.classDesc(newDynMessageFullName),
                            Optional.ofNullable(newDynName).map(ByteCodes::classDesc),
                            Optional.ofNullable(newDynMessageSimpleName),
                            ACC_PUBLIC + ACC_STATIC));

                    for (int i = 0; i < messageMethods.size(); i++) {
                        Method method = messageMethods.get(i);
                        String endfix = "_" + method.getName() + "_" + (i > 9 ? i : ("0" + i));
                        String newDynSuperMessageFullName = newDynMessageFullName + (method == wildcard ? "" : endfix);
                        cw2InnerClasses.add(InnerClassInfo.of(
                                ByteCodes.classDesc(newDynSuperMessageFullName),
                                Optional.ofNullable(newDynName).map(ByteCodes::classDesc),
                                Optional.ofNullable(newDynMessageSimpleName + (method == wildcard ? "" : endfix)),
                                ACC_PUBLIC + ACC_STATIC));

                        cw2.withField(
                                method.getAnnotation(RestOnMessage.class).name(),
                                ClassDesc.ofDescriptor("L" + newDynSuperMessageFullName + ";"),
                                ACC_PUBLIC);
                    }
                    { // 构造函数
                        cw2.withMethodBody("<init>", MethodTypeDesc.ofDescriptor("()V"), ACC_PUBLIC, mv -> {
                            mv.aload(0);
                            mv.invokespecial(
                                    ByteCodes.classDesc("java/lang/Object"),
                                    "<init>",
                                    MethodTypeDesc.ofDescriptor("()V"),
                                    false);
                            mv.return_();
                        });
                    }
                    { // toString
                        cw2.withMethodBody(
                                "toString", MethodTypeDesc.ofDescriptor("()Ljava/lang/String;"), ACC_PUBLIC, mv -> {
                                    mv.invokestatic(
                                            ByteCodes.classDesc(
                                                    JsonConvert.class.getName().replace('.', '/')),
                                            "root",
                                            MethodTypeDesc.ofDescriptor("()" + jsonConvertDesc),
                                            false);
                                    mv.aload(0);
                                    mv.invokevirtual(
                                            ByteCodes.classDesc(
                                                    JsonConvert.class.getName().replace('.', '/')),
                                            "convertTo",
                                            MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;)Ljava/lang/String;"));
                                    mv.areturn();
                                });
                    }

                    if (!cw2InnerClasses.isEmpty()) cw2.with(InnerClassesAttribute.of(cw2InnerClasses));
                });
                byte[] bytes = innerBytes;
                classLoader.loadClass(newDynMessageFullName.replace('/', '.'), bytes);
            }

            { // _DynXXXWebSocket class
                byte[] innerBytes = ClassFile.of().build(ByteCodes.classDesc(newDynWebSokcetFullName), cw2 -> {
                    cw2.withVersion(JAVA_11_VERSION, 0)
                            .withFlags(ACC_PUBLIC + ACC_FINAL + ACC_SUPER)
                            .withSuperclass(ByteCodes.classDesc(webSocketInternalName));
                    List<java.lang.classfile.Annotation> cw2Annotations = new ArrayList<>();
                    List<InnerClassInfo> cw2InnerClasses = new ArrayList<>();

                    cw2InnerClasses.add(InnerClassInfo.of(
                            ByteCodes.classDesc(newDynWebSokcetFullName),
                            Optional.ofNullable(newDynName).map(ByteCodes::classDesc),
                            Optional.ofNullable(newDynWebSokcetSimpleName),
                            ACC_PUBLIC + ACC_STATIC));
                    {
                        String resSignature =
                                serviceParamsGenericDesc == null ? null : ("(" + serviceParamsGenericDesc + ")V");
                        cw2.withMethod(
                                "<init>",
                                MethodTypeDesc.ofDescriptor("(" + serviceParamsDesc + ")V"),
                                ACC_PUBLIC,
                                mb -> {
                                    if (resSignature != null)
                                        mb.with(SignatureAttribute.of(MethodSignature.parseFrom(resSignature)));

                                    mb.withCode(mv -> {
                                        Label sublabel0 = mv.newLabel();
                                        mv.labelBinding(sublabel0);
                                        mv.aload(0);
                                        mv.invokespecial(
                                                ByteCodes.classDesc(webSocketInternalName),
                                                "<init>",
                                                MethodTypeDesc.ofDescriptor("()V"),
                                                false);
                                        for (int i = 0; i < resourcesFields.size(); i++) {
                                            Field field = resourcesFields.get(i);
                                            mv.aload(0);
                                            mv.aload(i + 1);
                                            mv.putfield(
                                                    ByteCodes.classDesc(newDynWebSokcetFullName),
                                                    field.getName(),
                                                    ClassDesc.ofDescriptor(ByteCodes.descriptor(field.getType())));
                                        }
                                        mv.return_();
                                        Label sublabel2 = mv.newLabel();
                                        mv.labelBinding(sublabel2);
                                        mv.localVariable(
                                                0,
                                                "this",
                                                ClassDesc.ofDescriptor("L" + newDynWebSokcetFullName + ";"),
                                                sublabel0,
                                                sublabel2);
                                        for (int i = 0; i < resourcesFields.size(); i++) {
                                            Field field = resourcesFields.get(i);
                                            String fieldDesc = ByteCodes.descriptor(field.getType());
                                            String fieldSignature = Utility.getTypeDescriptor(field.getGenericType());
                                            if (fieldDesc.equals(fieldSignature)) {
                                                fieldSignature = null;
                                            }
                                            mv.localVariable(
                                                    1 + i,
                                                    field.getName(),
                                                    ClassDesc.ofDescriptor(fieldDesc),
                                                    sublabel0,
                                                    sublabel2);
                                            if (fieldSignature != null)
                                                mv.localVariableType(
                                                        1 + i,
                                                        field.getName(),
                                                        Signature.parseFrom(fieldSignature),
                                                        sublabel0,
                                                        sublabel2);
                                        }
                                    });
                                });
                    }
                    { // RestDyn
                        {
                            List<AnnotationElement> av0 = new ArrayList<>();
                            av0.add(AnnotationElement.of("simple", ByteCodes.annotationValue(false)));
                            cw2Annotations.add(java.lang.classfile.Annotation.of(
                                    ClassDesc.ofDescriptor(ByteCodes.descriptor(RestDyn.class)), av0));
                        }
                    }
                    if (!cw2Annotations.isEmpty()) cw2.with(RuntimeVisibleAnnotationsAttribute.of(cw2Annotations));
                    if (!cw2InnerClasses.isEmpty()) cw2.with(InnerClassesAttribute.of(cw2InnerClasses));
                });
                byte[] bytes = innerBytes;
                classLoader.loadClass(newDynWebSokcetFullName.replace('/', '.'), bytes);
            }

            { // _DynRestOnMessageConsumer class
                byte[] innerBytes = ClassFile.of().build(ByteCodes.classDesc(newDynConsumerFullName), cw2 -> {
                    cw2.withVersion(JAVA_11_VERSION, 0)
                            .withFlags(ACC_PUBLIC + ACC_FINAL + ACC_SUPER)
                            .withSuperclass(ByteCodes.classDesc("java/lang/Object"));

                    List<InnerClassInfo> cw2InnerClasses = new ArrayList<>();
                    cw2.with(SignatureAttribute.of(ClassSignature.parseFrom(
                            "Ljava/lang/Object;Ljava/util/function/BiConsumer<" + wsDesc + "Ljava/lang/Object;>;")));
                    cw2.withInterfaceSymbols(Arrays.stream(new String[] {"java/util/function/BiConsumer"})
                            .map(ByteCodes::classDesc)
                            .toList());

                    cw2InnerClasses.add(InnerClassInfo.of(
                            ByteCodes.classDesc(newDynConsumerFullName),
                            Optional.ofNullable(newDynName).map(ByteCodes::classDesc),
                            Optional.ofNullable(newDynConsumerSimpleName),
                            ACC_PUBLIC + ACC_STATIC));
                    cw2InnerClasses.add(InnerClassInfo.of(
                            ByteCodes.classDesc(newDynMessageFullName),
                            Optional.ofNullable(newDynName).map(ByteCodes::classDesc),
                            Optional.ofNullable(newDynMessageSimpleName),
                            ACC_PUBLIC + ACC_STATIC));
                    for (int i = 0; i < messageMethods.size(); i++) {
                        Method method = messageMethods.get(i);
                        if (method == wildcard) continue;
                        String endfix = "_" + method.getName() + "_" + (i > 9 ? i : ("0" + i));
                        String newDynSuperMessageFullName = newDynMessageFullName + (method == wildcard ? "" : endfix);
                        cw2InnerClasses.add(InnerClassInfo.of(
                                ByteCodes.classDesc(newDynSuperMessageFullName),
                                Optional.ofNullable(newDynName).map(ByteCodes::classDesc),
                                Optional.ofNullable(newDynMessageSimpleName + (method == wildcard ? "" : endfix)),
                                ACC_PUBLIC + ACC_STATIC));
                    }

                    { // 构造函数
                        cw2.withMethodBody("<init>", MethodTypeDesc.ofDescriptor("()V"), ACC_PUBLIC, mv -> {
                            mv.aload(0);
                            mv.invokespecial(
                                    ByteCodes.classDesc("java/lang/Object"),
                                    "<init>",
                                    MethodTypeDesc.ofDescriptor("()V"),
                                    false);
                            mv.return_();
                        });
                    }

                    { // accept函数
                        cw2.withMethodBody(
                                "accept",
                                MethodTypeDesc.ofDescriptor("(" + wsDesc + "Ljava/lang/Object;)V"),
                                ACC_PUBLIC,
                                mv -> {
                                    Label label0 = mv.newLabel();
                                    mv.labelBinding(label0);

                                    mv.aload(1);
                                    mv.checkcast(ByteCodes.classDesc(newDynWebSokcetFullName));
                                    mv.astore(3);
                                    Label label3 = mv.newLabel();
                                    mv.labelBinding(label3);

                                    mv.aload(2);
                                    mv.checkcast(ByteCodes.classDesc(newDynMessageFullName));
                                    mv.astore(4);
                                    Label label4 = mv.newLabel();
                                    mv.labelBinding(label4);

                                    for (int i = 0; i < messageMethods.size(); i++) {
                                        final Method method = messageMethods.get(i);
                                        String endfix = "_" + method.getName() + "_" + (i > 9 ? i : ("0" + i));
                                        String newDynSuperMessageFullName =
                                                newDynMessageFullName + (method == wildcard ? "" : endfix);
                                        final String messagename = method.getAnnotation(RestOnMessage.class)
                                                .name();
                                        if (method == wildcard) {
                                            mv.aload(4);
                                            mv.aload(3);
                                            mv.invokevirtual(
                                                    ByteCodes.classDesc(newDynSuperMessageFullName),
                                                    "execute",
                                                    MethodTypeDesc.ofDescriptor(
                                                            "(L" + newDynWebSokcetFullName + ";)V"));
                                        } else {
                                            mv.aload(4);
                                            mv.getfield(
                                                    ByteCodes.classDesc(newDynMessageFullName),
                                                    messagename,
                                                    ClassDesc.ofDescriptor("L" + newDynSuperMessageFullName + ";"));
                                            Label ifLabel = mv.newLabel();
                                            mv.ifnull(ifLabel);

                                            mv.aload(4);
                                            mv.getfield(
                                                    ByteCodes.classDesc(newDynMessageFullName),
                                                    messagename,
                                                    ClassDesc.ofDescriptor("L" + newDynSuperMessageFullName + ";"));
                                            mv.aload(3);
                                            mv.invokevirtual(
                                                    ByteCodes.classDesc(newDynSuperMessageFullName),
                                                    "execute",
                                                    MethodTypeDesc.ofDescriptor(
                                                            "(L" + newDynWebSokcetFullName + ";)V"));
                                            mv.return_();
                                            mv.labelBinding(ifLabel);
                                        }
                                    }
                                    mv.return_();
                                    Label label2 = mv.newLabel();
                                    mv.labelBinding(label2);
                                    mv.localVariable(
                                            0,
                                            "this",
                                            ClassDesc.ofDescriptor("L" + newDynConsumerFullName + ";"),
                                            label0,
                                            label2);
                                    mv.localVariable(1, "websocket", ClassDesc.ofDescriptor(wsDesc), label0, label2);
                                    mv.localVariable(
                                            2, "message", ClassDesc.ofDescriptor("Ljava/lang/Object;"), label0, label2);
                                    mv.localVariable(
                                            3,
                                            "ws",
                                            ClassDesc.ofDescriptor("L" + newDynWebSokcetFullName + ";"),
                                            label3,
                                            label2);
                                    mv.localVariable(
                                            4,
                                            "msg",
                                            ClassDesc.ofDescriptor("L" + newDynMessageFullName + ";"),
                                            label4,
                                            label2);
                                });
                    }
                    { // 虚拟accept函数
                        cw2.withMethodBody(
                                "accept",
                                MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;Ljava/lang/Object;)V"),
                                ACC_PUBLIC + ACC_BRIDGE + ACC_SYNTHETIC,
                                mv -> {
                                    mv.aload(0);
                                    mv.aload(1);
                                    mv.checkcast(ByteCodes.classDesc(
                                            WebSocket.class.getName().replace('.', '/')));
                                    mv.aload(2);
                                    mv.checkcast(ByteCodes.classDesc("java/lang/Object"));
                                    mv.invokevirtual(
                                            ByteCodes.classDesc(newDynConsumerFullName),
                                            "accept",
                                            MethodTypeDesc.ofDescriptor("(" + wsDesc + "Ljava/lang/Object;)V"));
                                    mv.return_();
                                });
                    }

                    if (!cw2InnerClasses.isEmpty()) cw2.with(InnerClassesAttribute.of(cw2InnerClasses));
                });
                byte[] bytes = innerBytes;
                classLoader.loadClass(newDynConsumerFullName.replace('/', '.'), bytes);
            }
            if (!cwAnnotations.isEmpty()) cw.with(RuntimeVisibleAnnotationsAttribute.of(cwAnnotations));
            if (!cwInnerClasses.isEmpty()) cw.with(InnerClassesAttribute.of(cwInnerClasses));
        });

        byte[] bytes = classBytes;
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

        final String restInternalName = ByteCodes.internalName(Rest.class);
        final String serviceDesc = ByteCodes.descriptor(serviceType);
        final String webServletDesc = ByteCodes.descriptor(WebServlet.class);
        final String resDesc = ByteCodes.descriptor(Resource.class);
        final String reqDesc = ByteCodes.descriptor(HttpRequest.class);
        final String respDesc = ByteCodes.descriptor(HttpResponse.class);
        final String convertDesc = ByteCodes.descriptor(Convert.class);
        final String nonblockDesc = ByteCodes.descriptor(NonBlocking.class);
        final String typeDesc = ByteCodes.descriptor(java.lang.reflect.Type.class);
        final String retDesc = ByteCodes.descriptor(RetResult.class);
        final String httpResultDesc = ByteCodes.descriptor(HttpResult.class);
        final String httpScopeDesc = ByteCodes.descriptor(HttpScope.class);
        final String stageDesc = ByteCodes.descriptor(CompletionStage.class);
        final String httpHeadersDesc = ByteCodes.descriptor(HttpHeaders.class);
        final String httpParametersDesc = ByteCodes.descriptor(HttpParameters.class);
        final String flipperDesc = ByteCodes.descriptor(Flipper.class);
        final String httpServletName = HttpServlet.class.getName().replace('.', '/');
        final String actionEntryName = HttpServlet.ActionEntry.class.getName().replace('.', '/');
        final String attrDesc = ByteCodes.descriptor(org.redkale.util.Attribute.class);
        final String multiContextDesc = ByteCodes.descriptor(MultiContext.class);
        final String multiContextName = MultiContext.class.getName().replace('.', '/');
        final String mappingDesc = ByteCodes.descriptor(HttpMapping.class);
        final String restConvertDesc = ByteCodes.descriptor(RestConvert.class);
        final String restConvertsDesc = ByteCodes.descriptor(RestConvert.RestConverts.class);
        final String restConvertCoderDesc = ByteCodes.descriptor(RestConvertCoder.class);
        final String restConvertCodersDesc = ByteCodes.descriptor(RestConvertCoder.RestConvertCoders.class);
        final String httpParamDesc = ByteCodes.descriptor(HttpParam.class);
        final String httpParamsDesc = ByteCodes.descriptor(HttpParam.HttpParams.class);
        final String sourcetypeDesc = ByteCodes.descriptor(HttpParam.HttpParameterStyle.class);

        final String reqInternalName = ByteCodes.internalName(HttpRequest.class);
        final String respInternalName = ByteCodes.internalName(HttpResponse.class);
        final String attrInternalName = ByteCodes.internalName(org.redkale.util.Attribute.class);
        final String retInternalName = ByteCodes.internalName(RetResult.class);
        final String serviceTypeInternalName = ByteCodes.internalName(serviceType);

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
                        if (n != null && n.startsWith("#")) { // 不再支持/name:value路径, path里包含参数会增加网关限流复杂度
                            throw new RedkaleException("found illegal @RestParam name (" + n + ") in method " + method
                                    + ", serviceType is " + serviceType.getName());
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

                                    } else if (en.getKey().contains("_uploadfile_")) {

                                    } else if (en.getKey().contains("_uploadfiles_")) {

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
        final String httpDesc = ByteCodes.descriptor(HttpServlet.class);
        if (!checkName(catalog)) {
            throw new RestException(serviceType.getName() + " have illegal " + RestService.class.getSimpleName()
                    + ".catalog, only 0-9 a-z A-Z _ cannot begin 0-9");
        }
        if (!checkName(defModuleName)) {
            throw new RestException(serviceType.getName() + " have illegal " + RestService.class.getSimpleName()
                    + ".value, only 0-9 a-z A-Z _ cannot begin 0-9");
        }

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

        Map<String, byte[]> innerClassBytesMap = new LinkedHashMap<>();
        boolean[] dynsimple = {baseServletType == HttpServlet.class}; // 有自定义的BaseServlet会存在读取header的操作
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
        byte[] classBytes = ClassFile.of().build(ByteCodes.classDesc(newDynName), cw -> {
            cw.withVersion(JAVA_11_VERSION, 0)
                    .withFlags(ACC_PUBLIC + ACC_SUPER)
                    .withSuperclass(ByteCodes.classDesc(supDynName));
            List<java.lang.classfile.Annotation> cwAnnotations = new ArrayList<>();
            List<InnerClassInfo> cwInnerClasses = new ArrayList<>();

            { // RestDynSourceType
                {
                    List<AnnotationElement> av0 = new ArrayList<>();
                    av0.add(AnnotationElement.of(
                            "value",
                            ByteCodes.annotationValue(ByteCodes.constantType(ByteCodes.descriptor(serviceType)))));
                    cwAnnotations.add(java.lang.classfile.Annotation.of(
                            ClassDesc.ofDescriptor(ByteCodes.descriptor(RestDynSourceType.class)), av0));
                }
            }

            final int moduleid = controller == null ? 0 : controller.moduleid();
            { // 注入 @WebServlet 注解
                String urlpath = "";
                boolean repair = controller == null || controller.repair();
                String comment = controller == null ? "" : controller.comment();
                {
                    List<AnnotationElement> av0 = new ArrayList<>();
                    {
                        {
                            List<AnnotationValue> av1 = new ArrayList<>();
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
                                    av1.add(ByteCodes.annotationValue(suburl));
                                }
                                if (urlpath.length() > 0) {
                                    urlpath = urlpath.substring(1);
                                }
                            } else {
                                urlpath = (catalog.isEmpty() ? "/" : ("/" + catalog + "/")) + defModuleName + "/*";
                                av1.add(ByteCodes.annotationValue(urlpath));
                            }
                            av0.add(AnnotationElement.of("value", AnnotationValue.ofArray(av1)));
                        }
                    }
                    av0.add(AnnotationElement.of("name", ByteCodes.annotationValue(defModuleName)));
                    av0.add(AnnotationElement.of("moduleid", ByteCodes.annotationValue(moduleid)));
                    av0.add(AnnotationElement.of("repair", ByteCodes.annotationValue(repair)));
                    av0.add(AnnotationElement.of("comment", ByteCodes.annotationValue(comment)));
                    cwAnnotations.add(java.lang.classfile.Annotation.of(ClassDesc.ofDescriptor(webServletDesc), av0));
                }
                classMap.put("type", serviceType.getName());
                classMap.put("url", urlpath);
                classMap.put("moduleid", moduleid);
                classMap.put("repair", repair);
                // classMap.put("comment", comment); //不显示太多信息
            }
            { // NonBlocking
                {
                    List<AnnotationElement> av0 = new ArrayList<>();
                    av0.add(AnnotationElement.of("value", ByteCodes.annotationValue(true)));
                    cwAnnotations.add(java.lang.classfile.Annotation.of(ClassDesc.ofDescriptor(nonblockDesc), av0));
                }
            }
            { // 内部类
                cwInnerClasses.add(InnerClassInfo.of(
                        ByteCodes.classDesc(actionEntryName),
                        Optional.ofNullable(httpServletName).map(ByteCodes::classDesc),
                        Optional.ofNullable(HttpServlet.ActionEntry.class.getSimpleName()),
                        ACC_PROTECTED + ACC_FINAL + ACC_STATIC));

                for (final MappingEntry entry : entrys) {
                    cwInnerClasses.add(InnerClassInfo.of(
                            ByteCodes.classDesc(newDynName + "$" + entry.newActionClassName),
                            Optional.ofNullable(newDynName).map(ByteCodes::classDesc),
                            Optional.ofNullable(entry.newActionClassName),
                            ACC_PRIVATE + ACC_STATIC));
                }
            }
            { // 注入 @Resource  private XXXService _service;
                cw.withField(REST_SERVICE_FIELD_NAME, ClassDesc.ofDescriptor(serviceDesc), fv -> {
                    fv.withFlags(ACC_PRIVATE);
                    List<java.lang.classfile.Annotation> fvAnnotations = new ArrayList<>();

                    {
                        List<AnnotationElement> av0 = new ArrayList<>();
                        av0.add(AnnotationElement.of(
                                "name",
                                ByteCodes.annotationValue(
                                        Utility.isBlank(serviceResourceName) ? "" : serviceResourceName)));
                        fvAnnotations.add(java.lang.classfile.Annotation.of(ClassDesc.ofDescriptor(resDesc), av0));
                    }

                    if (!fvAnnotations.isEmpty()) fv.with(RuntimeVisibleAnnotationsAttribute.of(fvAnnotations));
                });
            }
            { // _serviceMap字段 Map<String, XXXService>
                cw.withField(REST_SERVICEMAP_FIELD_NAME, ClassDesc.ofDescriptor("Ljava/util/Map;"), fv -> {
                    fv.withFlags(ACC_PRIVATE);

                    fv.with(SignatureAttribute.of(
                            Signature.parseFrom("Ljava/util/Map<Ljava/lang/String;" + serviceDesc + ">;")));
                });
            }
            { // _redkale_toStringSupplier字段 Supplier<String>
                cw.withField(
                        REST_TOSTRINGOBJ_FIELD_NAME, ClassDesc.ofDescriptor("Ljava/util/function/Supplier;"), fv -> {
                            fv.withFlags(ACC_PRIVATE);

                            fv.with(SignatureAttribute.of(
                                    Signature.parseFrom("Ljava/util/function/Supplier<Ljava/lang/String;>;")));
                        });
            }
            { // 构造函数
                cw.withMethodBody("<init>", MethodTypeDesc.ofDescriptor("()V"), ACC_PUBLIC, mv -> {
                    mv.aload(0);
                    mv.invokespecial(
                            ByteCodes.classDesc(supDynName), "<init>", MethodTypeDesc.ofDescriptor("()V"), false);
                    mv.return_();
                });
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
            final Map<String, CodeMethodBean> methodBeanMap =
                    namePresent ? null : CodeMethodBoost.getMethodBeans(serviceType);

            boolean[] containsMupload = {false};
            for (final MappingEntry entry : entrys) {
                final Method method = entry.mappingMethod;
                final Class returnType = method.getReturnType();
                final java.lang.reflect.Type retvalType = formatRestReturnType(method, serviceType);
                final String methodDesc = ByteCodes.methodDescriptor(method);
                final Parameter[] params = method.getParameters();

                final RestConvert[] rcs = method.getAnnotationsByType(RestConvert.class);
                final RestConvertCoder[] rcc = method.getAnnotationsByType(RestConvertCoder.class);
                final boolean hasResConvert = Utility.isNotEmpty(rcs) || Utility.isNotEmpty(rcc);
                if (hasResConvert) {
                    restConverts.add(new Object[] {rcs, rcc});
                }
                if (dynsimple[0] && entry.rpcOnly) { // 需要读取http header
                    dynsimple[0] = false;
                }

                cw.withMethod(
                        entry.newMethodName,
                        MethodTypeDesc.ofDescriptor("(" + reqDesc + respDesc + ")V"),
                        ACC_PUBLIC,
                        mb -> {
                            mb.with(ExceptionsAttribute.ofSymbols(Arrays.stream(new String[] {"java/io/IOException"})
                                    .map(ByteCodes::classDesc)
                                    .toList()));
                            List<java.lang.classfile.Annotation> mvAnnotations = new ArrayList<>();
                            mb.withCode(mv -> {
                                RestUploadFile mupload = null;
                                Class muploadType = null;

                                Label label0 = mv.newLabel();
                                mv.labelBinding(label0);

                                mv.aload(0);
                                mv.getfield(
                                        ByteCodes.classDesc(newDynName),
                                        REST_SERVICEMAP_FIELD_NAME,
                                        ClassDesc.ofDescriptor("Ljava/util/Map;"));
                                Label lmapif = mv.newLabel();
                                mv.ifnonnull(lmapif);
                                mv.aload(0);
                                mv.getfield(
                                        ByteCodes.classDesc(newDynName),
                                        REST_SERVICE_FIELD_NAME,
                                        ClassDesc.ofDescriptor(serviceDesc));
                                Label lserif = mv.newLabel();
                                mv.goto_(lserif);
                                mv.labelBinding(lmapif);

                                mv.aload(0);
                                mv.getfield(
                                        ByteCodes.classDesc(newDynName),
                                        REST_SERVICEMAP_FIELD_NAME,
                                        ClassDesc.ofDescriptor("Ljava/util/Map;"));
                                mv.aload(1);
                                mv.loadConstant(REST_HEADER_RESNAME);
                                mv.loadConstant("");
                                mv.invokevirtual(
                                        ByteCodes.classDesc(reqInternalName),
                                        "getHeader",
                                        MethodTypeDesc.ofDescriptor(
                                                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"));
                                mv.invokeinterface(
                                        ByteCodes.classDesc("java/util/Map"),
                                        "get",
                                        MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;)Ljava/lang/Object;"));
                                mv.checkcast(ByteCodes.classDesc(serviceTypeInternalName));
                                mv.labelBinding(lserif);

                                mv.astore(3);

                                // 执行setRequestAnnotations
                                mv.aload(1);
                                mv.aload(0);
                                mv.getfield(
                                        ByteCodes.classDesc(newDynName),
                                        REST_METHOD_ANNS_NAME,
                                        ClassDesc.ofDescriptor("[[Ljava/lang/annotation/Annotation;"));
                                mv.loadConstant(entry.methodIdx); // 方法下标
                                mv.aaload();
                                mv.invokestatic(
                                        ByteCodes.classDesc(restInternalName),
                                        "setRequestAnnotations",
                                        MethodTypeDesc.ofDescriptor(
                                                "(" + reqDesc + "[Ljava/lang/annotation/Annotation;)V"),
                                        false);

                                final int maxStack = 3 + params.length;
                                List<int[]> varInsns = new ArrayList<>();
                                int maxLocals = 4;

                                CodeMethodBean methodBean =
                                        methodBeanMap == null ? null : CodeMethodBean.get(methodBeanMap, method);
                                List<CodeMethodParam> methodParams = methodBean == null ? null : methodBean.getParams();
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
                                                    "@RestHeader must on String or InetSocketAddress Parameter in "
                                                            + method);
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
                                                    "@RestCookie and @RestHeader cannot on the same Parameter in "
                                                            + method);
                                        }
                                        if (ptype != String.class) {
                                            throw new RestException(
                                                    "@RestCookie must on String Parameter in " + method);
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
                                                    "@RestSessionid and @RestHeader cannot on the same Parameter in "
                                                            + method);
                                        }
                                        if (anncookie != null) {
                                            throw new RestException(
                                                    "@RestSessionid and @RestCookie cannot on the same Parameter in "
                                                            + method);
                                        }
                                        if (ptype != String.class) {
                                            throw new RestException(
                                                    "@RestSessionid must on String Parameter in " + method);
                                        }
                                        required = false;
                                    }
                                    RestAddress annaddr = param.getAnnotation(RestAddress.class);
                                    if (annaddr != null) {
                                        if (annhead != null) {
                                            throw new RestException(
                                                    "@RestAddress and @RestHeader cannot on the same Parameter in "
                                                            + method);
                                        }
                                        if (anncookie != null) {
                                            throw new RestException(
                                                    "@RestAddress and @RestCookie cannot on the same Parameter in "
                                                            + method);
                                        }
                                        if (annsid != null) {
                                            throw new RestException(
                                                    "@RestAddress and @RestSessionid cannot on the same Parameter in "
                                                            + method);
                                        }
                                        if (ptype != String.class) {
                                            throw new RestException(
                                                    "@RestAddress must on String Parameter in " + method);
                                        }
                                        comment = annaddr.comment();
                                        required = false;
                                    }
                                    RestLocale annlocale = param.getAnnotation(RestLocale.class);
                                    if (annlocale != null) {
                                        if (annhead != null) {
                                            throw new RestException(
                                                    "@RestLocale and @RestHeader cannot on the same Parameter in "
                                                            + method);
                                        }
                                        if (anncookie != null) {
                                            throw new RestException(
                                                    "@RestLocale and @RestCookie cannot on the same Parameter in "
                                                            + method);
                                        }
                                        if (annsid != null) {
                                            throw new RestException(
                                                    "@RestLocale and @RestSessionid cannot on the same Parameter in "
                                                            + method);
                                        }
                                        if (annaddr != null) {
                                            throw new RestException(
                                                    "@RestLocale and @RestAddress cannot on the same Parameter in "
                                                            + method);
                                        }
                                        if (ptype != String.class) {
                                            throw new RestException(
                                                    "@RestAddress must on String Parameter in " + method);
                                        }
                                        comment = annlocale.comment();
                                        required = false;
                                    }
                                    RestBody annbody = param.getAnnotation(RestBody.class);
                                    if (annbody != null) {
                                        if (annhead != null) {
                                            throw new RestException(
                                                    "@RestBody and @RestHeader cannot on the same Parameter in "
                                                            + method);
                                        }
                                        if (anncookie != null) {
                                            throw new RestException(
                                                    "@RestBody and @RestCookie cannot on the same Parameter in "
                                                            + method);
                                        }
                                        if (annsid != null) {
                                            throw new RestException(
                                                    "@RestBody and @RestSessionid cannot on the same Parameter in "
                                                            + method);
                                        }
                                        if (annaddr != null) {
                                            throw new RestException(
                                                    "@RestBody and @RestAddress cannot on the same Parameter in "
                                                            + method);
                                        }
                                        if (annlocale != null) {
                                            throw new RestException(
                                                    "@RestBody and @RestLocale cannot on the same Parameter in "
                                                            + method);
                                        }
                                        if (ptype.isPrimitive()) {
                                            throw new RestException(
                                                    "@RestBody cannot on primitive type Parameter in " + method);
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
                                                    "@RestUploadFile and @RestHeader cannot on the same Parameter in "
                                                            + method);
                                        }
                                        if (anncookie != null) {
                                            throw new RestException(
                                                    "@RestUploadFile and @RestCookie cannot on the same Parameter in "
                                                            + method);
                                        }
                                        if (annsid != null) {
                                            throw new RestException(
                                                    "@RestUploadFile and @RestSessionid cannot on the same Parameter in "
                                                            + method);
                                        }
                                        if (annaddr != null) {
                                            throw new RestException(
                                                    "@RestUploadFile and @RestAddress cannot on the same Parameter in "
                                                            + method);
                                        }
                                        if (annlocale != null) {
                                            throw new RestException(
                                                    "@RestUploadFile and @RestLocale cannot on the same Parameter in "
                                                            + method);
                                        }
                                        if (annbody != null) {
                                            throw new RestException(
                                                    "@RestUploadFile and @RestBody cannot on the same Parameter in "
                                                            + method);
                                        }
                                        if (ptype != byte[].class && ptype != File.class && ptype != File[].class) {
                                            throw new RestException(
                                                    "@RestUploadFile must on byte[] or File or File[] Parameter in "
                                                            + method);
                                        }
                                        comment = annfile.comment();
                                    }

                                    RestPath annpath = param.getAnnotation(RestPath.class);
                                    if (annpath != null) {
                                        if (annhead != null) {
                                            throw new RestException(
                                                    "@RestPath and @RestHeader cannot on the same Parameter in "
                                                            + method);
                                        }
                                        if (anncookie != null) {
                                            throw new RestException(
                                                    "@RestPath and @RestCookie cannot on the same Parameter in "
                                                            + method);
                                        }
                                        if (annsid != null) {
                                            throw new RestException(
                                                    "@RestPath and @RestSessionid cannot on the same Parameter in "
                                                            + method);
                                        }
                                        if (annaddr != null) {
                                            throw new RestException(
                                                    "@RestPath and @RestAddress cannot on the same Parameter in "
                                                            + method);
                                        }
                                        if (annlocale != null) {
                                            throw new RestException(
                                                    "@RestPath and @RestLocale cannot on the same Parameter in "
                                                            + method);
                                        }
                                        if (annbody != null) {
                                            throw new RestException(
                                                    "@RestPath and @RestBody cannot on the same Parameter in "
                                                            + method);
                                        }
                                        if (annfile != null) {
                                            throw new RestException(
                                                    "@RestPath and @RestUploadFile cannot on the same Parameter in "
                                                            + method);
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
                                                    "@RestUserid and @RestHeader cannot on the same Parameter in "
                                                            + method);
                                        }
                                        if (anncookie != null) {
                                            throw new RestException(
                                                    "@RestUserid and @RestCookie cannot on the same Parameter in "
                                                            + method);
                                        }
                                        if (annsid != null) {
                                            throw new RestException(
                                                    "@RestUserid and @RestSessionid cannot on the same Parameter in "
                                                            + method);
                                        }
                                        if (annaddr != null) {
                                            throw new RestException(
                                                    "@RestUserid and @RestAddress cannot on the same Parameter in "
                                                            + method);
                                        }
                                        if (annlocale != null) {
                                            throw new RestException(
                                                    "@RestUserid and @RestLocale cannot on the same Parameter in "
                                                            + method);
                                        }
                                        if (annbody != null) {
                                            throw new RestException(
                                                    "@RestUserid and @RestBody cannot on the same Parameter in "
                                                            + method);
                                        }
                                        if (annfile != null) {
                                            throw new RestException(
                                                    "@RestUserid and @RestUploadFile cannot on the same Parameter in "
                                                            + method);
                                        }
                                        if (!ptype.isPrimitive()
                                                && !java.io.Serializable.class.isAssignableFrom(ptype)) {
                                            throw new RestException(
                                                    "@RestUserid must on java.io.Serializable Parameter in " + method);
                                        }
                                        comment = "";
                                        required = false;
                                    }

                                    boolean annparams = param.getType() == RestParams.class;
                                    boolean annheaders = param.getType() == RestHeaders.class;
                                    if (annparams) {
                                        if (annhead != null) {
                                            throw new RestException("@RestHeader cannot on the "
                                                    + RestParams.class.getSimpleName() + " Parameter in " + method);
                                        }
                                        if (anncookie != null) {
                                            throw new RestException("@RestCookie cannot on the "
                                                    + RestParams.class.getSimpleName() + " Parameter in " + method);
                                        }
                                        if (annsid != null) {
                                            throw new RestException("@RestSessionid cannot on the "
                                                    + RestParams.class.getSimpleName() + " Parameter in " + method);
                                        }
                                        if (annaddr != null) {
                                            throw new RestException("@RestAddress cannot on the "
                                                    + RestParams.class.getSimpleName() + " Parameter in " + method);
                                        }
                                        if (annlocale != null) {
                                            throw new RestException("@RestLocale cannot on the "
                                                    + RestParams.class.getSimpleName() + " Parameter in " + method);
                                        }
                                        if (annbody != null) {
                                            throw new RestException("@RestBody cannot on the "
                                                    + RestParams.class.getSimpleName() + " Parameter in " + method);
                                        }
                                        if (annfile != null) {
                                            throw new RestException("@RestUploadFile cannot on the "
                                                    + RestParams.class.getSimpleName() + " Parameter in " + method);
                                        }
                                        if (userid != null) {
                                            throw new RestException("@RestUserid cannot on the "
                                                    + RestParams.class.getSimpleName() + " Parameter in " + method);
                                        }
                                        if (annheaders) {
                                            throw new RestException("@RestHeaders cannot on the "
                                                    + RestParams.class.getSimpleName() + " Parameter in " + method);
                                        }
                                        comment = "";
                                    }

                                    if (annheaders) {
                                        if (annhead != null) {
                                            throw new RestException("@RestHeader cannot on the "
                                                    + RestHeaders.class.getSimpleName() + " Parameter in " + method);
                                        }
                                        if (anncookie != null) {
                                            throw new RestException("@RestCookie cannot on the "
                                                    + RestHeaders.class.getSimpleName() + " Parameter in " + method);
                                        }
                                        if (annsid != null) {
                                            throw new RestException("@RestSessionid cannot on the "
                                                    + RestHeaders.class.getSimpleName() + " Parameter in " + method);
                                        }
                                        if (annaddr != null) {
                                            throw new RestException("@RestAddress cannot on the "
                                                    + RestHeaders.class.getSimpleName() + " Parameter in " + method);
                                        }
                                        if (annlocale != null) {
                                            throw new RestException("@RestLocale cannot on the "
                                                    + RestHeaders.class.getSimpleName() + " Parameter in " + method);
                                        }
                                        if (annbody != null) {
                                            throw new RestException("@RestBody cannot on the "
                                                    + RestHeaders.class.getSimpleName() + " Parameter in " + method);
                                        }
                                        if (annfile != null) {
                                            throw new RestException("@RestUploadFile cannot on the "
                                                    + RestHeaders.class.getSimpleName() + " Parameter in " + method);
                                        }
                                        if (userid != null) {
                                            throw new RestException("@RestUserid cannot on the "
                                                    + RestHeaders.class.getSimpleName() + " Parameter in " + method);
                                        }
                                        if (annparams) {
                                            throw new RestException("@RestParams cannot on the "
                                                    + RestHeaders.class.getSimpleName() + " Parameter in " + method);
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
                                    if (n == null && methodParams != null && methodParams.size() > i) {
                                        n = methodParams.get(i).getName();
                                    }
                                    if (n == null) {
                                        if (param.isNamePresent()) {
                                            n = param.getName();
                                        } else if (ptype == Flipper.class) {
                                            n = "flipper";
                                        } else {
                                            throw new RestException("Parameter " + param.getName()
                                                    + " not found name by @RestParam  in " + method);
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
                                                    throw new RestException("@RestUploadFile repeat in " + method
                                                            + " or field " + field);
                                                }
                                                mupload = ruf;
                                                muploadType = field.getType();
                                            }
                                        } while ((loop = loop.getSuperclass()) != Object.class);
                                    }
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

                                Map<String, Object> mappingMap = new LinkedHashMap<>();
                                java.lang.reflect.Type returnGenericNoFutureType =
                                        TypeToken.getGenericType(method.getGenericReturnType(), serviceType);
                                { // 设置 Annotation HttpMapping
                                    boolean reqpath = false;
                                    for (Object[] ps : paramlist) {
                                        if ("#".equals(ps[1])) {
                                            reqpath = true;
                                            break;
                                        }
                                    }
                                    if (method.getAnnotation(Deprecated.class) != null) {
                                        {
                                            List<AnnotationElement> av0 = new ArrayList<>();
                                            mvAnnotations.add(java.lang.classfile.Annotation.of(
                                                    ClassDesc.ofDescriptor(ByteCodes.descriptor(Deprecated.class)),
                                                    av0));
                                        }
                                    }
                                    String url;
                                    {
                                        List<AnnotationElement> av0 = new ArrayList<>();
                                        url = (catalog.isEmpty() ? "/" : ("/" + catalog + "/"))
                                                + (defModuleName.isEmpty() ? "" : (defModuleName + "/"))
                                                + entry.name
                                                + (reqpath ? "/" : "");
                                        if ("//".equals(url)) {
                                            url = "/";
                                        }
                                        av0.add(AnnotationElement.of("url", ByteCodes.annotationValue(url)));
                                        av0.add(AnnotationElement.of(
                                                "name",
                                                ByteCodes.annotationValue(
                                                        (defModuleName.isEmpty() ? "" : (defModuleName + "_"))
                                                                + entry.name)));
                                        av0.add(AnnotationElement.of(
                                                "example", ByteCodes.annotationValue(entry.example)));
                                        av0.add(AnnotationElement.of(
                                                "rpcOnly", ByteCodes.annotationValue(entry.rpcOnly)));
                                        av0.add(AnnotationElement.of("auth", ByteCodes.annotationValue(entry.auth)));
                                        av0.add(AnnotationElement.of(
                                                "cacheSeconds", ByteCodes.annotationValue(entry.cacheSeconds)));
                                        av0.add(AnnotationElement.of(
                                                "actionid", ByteCodes.annotationValue(entry.actionid)));
                                        av0.add(AnnotationElement.of(
                                                "comment", ByteCodes.annotationValue(entry.comment)));

                                        {
                                            List<AnnotationValue> av1 = new ArrayList<>();
                                            for (String m : entry.methods) {
                                                av1.add(ByteCodes.annotationValue(m));
                                            }
                                            av0.add(AnnotationElement.of("methods", AnnotationValue.ofArray(av1)));
                                        }

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
                                        av0.add(AnnotationElement.of(
                                                "result",
                                                ByteCodes.annotationValue(
                                                        ByteCodes.constantType(ByteCodes.descriptor(rtc)))));
                                        if (returnGenericNoFutureType != rtc) {
                                            String refid = typeRefs.get(returnGenericNoFutureType);
                                            if (refid == null) {
                                                refid = "_typeref_" + typeRefs.size();
                                                typeRefs.put(returnGenericNoFutureType, refid);
                                            }
                                            av0.add(AnnotationElement.of(
                                                    "resultRef", ByteCodes.annotationValue(refid)));
                                        }

                                        mvAnnotations.add(java.lang.classfile.Annotation.of(
                                                ClassDesc.ofDescriptor(mappingDesc), av0));
                                    }
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
                                    {
                                        List<AnnotationElement> av0 = new ArrayList<>();
                                        av0.add(AnnotationElement.of(
                                                "value", ByteCodes.annotationValue(entry.nonBlocking)));
                                        mvAnnotations.add(java.lang.classfile.Annotation.of(
                                                ClassDesc.ofDescriptor(nonblockDesc), av0));
                                    }
                                }
                                if (rcs != null && rcs.length > 0) { // 设置 Annotation RestConvert
                                    {
                                        List<AnnotationElement> av0 = new ArrayList<>();
                                        {
                                            List<AnnotationValue> av1 = new ArrayList<>();
                                            // 设置 RestConvert
                                            for (RestConvert rc : rcs) {
                                                {
                                                    List<AnnotationElement> av2 = new ArrayList<>();
                                                    av2.add(AnnotationElement.of(
                                                            "features", ByteCodes.annotationValue(rc.features())));
                                                    av2.add(AnnotationElement.of(
                                                            "skipIgnore", ByteCodes.annotationValue(rc.skipIgnore())));
                                                    av2.add(AnnotationElement.of(
                                                            "type",
                                                            ByteCodes.annotationValue(ByteCodes.constantType(
                                                                    ByteCodes.descriptor(rc.type())))));
                                                    {
                                                        List<AnnotationValue> av3 = new ArrayList<>();
                                                        for (String s : rc.onlyColumns()) {
                                                            av3.add(ByteCodes.annotationValue(s));
                                                        }
                                                        av2.add(AnnotationElement.of(
                                                                "onlyColumns", AnnotationValue.ofArray(av3)));
                                                    }
                                                    {
                                                        List<AnnotationValue> av3 = new ArrayList<>();
                                                        for (String s : rc.ignoreColumns()) {
                                                            av3.add(ByteCodes.annotationValue(s));
                                                        }
                                                        av2.add(AnnotationElement.of(
                                                                "ignoreColumns", AnnotationValue.ofArray(av3)));
                                                    }
                                                    {
                                                        List<AnnotationValue> av3 = new ArrayList<>();
                                                        for (String s : rc.convertColumns()) {
                                                            av3.add(ByteCodes.annotationValue(s));
                                                        }
                                                        av2.add(AnnotationElement.of(
                                                                "convertColumns", AnnotationValue.ofArray(av3)));
                                                    }
                                                    av1.add(AnnotationValue.ofAnnotation(
                                                            java.lang.classfile.Annotation.of(
                                                                    ClassDesc.ofDescriptor(restConvertDesc), av2)));
                                                }
                                            }
                                            av0.add(AnnotationElement.of("value", AnnotationValue.ofArray(av1)));
                                        }
                                        mvAnnotations.add(java.lang.classfile.Annotation.of(
                                                ClassDesc.ofDescriptor(restConvertsDesc), av0));
                                    }
                                }
                                if (rcc != null && rcc.length > 0) { // 设置 Annotation RestConvertCoder
                                    {
                                        List<AnnotationElement> av0 = new ArrayList<>();
                                        {
                                            List<AnnotationValue> av1 = new ArrayList<>();
                                            // 设置 RestConvertCoder
                                            for (RestConvertCoder rc : rcc) {
                                                {
                                                    List<AnnotationElement> av2 = new ArrayList<>();
                                                    av2.add(AnnotationElement.of(
                                                            "type",
                                                            ByteCodes.annotationValue(ByteCodes.constantType(
                                                                    ByteCodes.descriptor(rc.type())))));
                                                    av2.add(AnnotationElement.of(
                                                            "field", ByteCodes.annotationValue(rc.field())));
                                                    av2.add(AnnotationElement.of(
                                                            "coder",
                                                            ByteCodes.annotationValue(ByteCodes.constantType(
                                                                    ByteCodes.descriptor(rc.coder())))));
                                                    av1.add(AnnotationValue.ofAnnotation(
                                                            java.lang.classfile.Annotation.of(
                                                                    ClassDesc.ofDescriptor(restConvertCoderDesc),
                                                                    av2)));
                                                }
                                            }
                                            av0.add(AnnotationElement.of("value", AnnotationValue.ofArray(av1)));
                                        }
                                        mvAnnotations.add(java.lang.classfile.Annotation.of(
                                                ClassDesc.ofDescriptor(restConvertCodersDesc), av0));
                                    }
                                }
                                final int headIndex = 10;
                                { // 设置 Annotation
                                    {
                                        List<AnnotationElement> av0 = new ArrayList<>();
                                        {
                                            List<AnnotationValue> av1 = new ArrayList<>();
                                            // 设置 HttpParam
                                            for (Object[] ps :
                                                    paramlist) { // {param, n, ptype, radix, comment, required, annpara,
                                                // annsid, annaddr, annlocale,
                                                // annhead, anncookie, annbody, annfile, annpath, annuserid, annheaders,
                                                // annparams,
                                                // paramtype}
                                                String n = ps[1].toString();
                                                final boolean isuserid =
                                                        ((RestUserid) ps[headIndex + 5]) != null; // 是否取userid
                                                if (n.indexOf('&') >= 0 || isuserid) {
                                                    continue; // @RestUserid 不需要生成 @HttpParam
                                                }
                                                if (((RestAddress) ps[8]) != null) {
                                                    continue; // @RestAddress 不需要生成 @HttpParam
                                                }
                                                if (((RestLocale) ps[9]) != null) {
                                                    continue; // @RestLocale 不需要生成 @HttpParam
                                                }
                                                final boolean ishead = ((RestHeader) ps[headIndex])
                                                        != null; // 是否取getHeader 而不是 getParameter
                                                final boolean iscookie =
                                                        ((RestCookie) ps[headIndex + 1]) != null; // 是否取getCookie
                                                final boolean isbody =
                                                        ((RestBody) ps[headIndex + 2]) != null; // 是否取getBody
                                                {
                                                    List<AnnotationElement> av2 = new ArrayList<>();
                                                    av2.add(AnnotationElement.of(
                                                            "name", ByteCodes.annotationValue((String) ps[1])));
                                                    if (((Parameter) ps[0]).getAnnotation(Deprecated.class) != null) {
                                                        av2.add(AnnotationElement.of(
                                                                "deprecated", ByteCodes.annotationValue(true)));
                                                    }
                                                    av2.add(AnnotationElement.of(
                                                            "type",
                                                            ByteCodes.annotationValue(ByteCodes.constantType(
                                                                    ByteCodes.descriptor((Class) ps[2])))));
                                                    java.lang.reflect.Type pgtype = TypeToken.getGenericType(
                                                            ((Parameter) ps[0]).getParameterizedType(), serviceType);
                                                    if (pgtype != (Class) ps[2]) {
                                                        String refid = typeRefs.get(pgtype);
                                                        if (refid == null) {
                                                            refid = "_typeref_" + typeRefs.size();
                                                            typeRefs.put(pgtype, refid);
                                                        }
                                                        av2.add(AnnotationElement.of(
                                                                "typeref", ByteCodes.annotationValue(refid)));
                                                    }
                                                    av2.add(AnnotationElement.of(
                                                            "radix", ByteCodes.annotationValue((Integer) ps[3])));
                                                    if (ishead) {
                                                        av2.add(AnnotationElement.of(
                                                                "style",
                                                                AnnotationValue.ofEnum(
                                                                        ClassDesc.ofDescriptor(sourcetypeDesc),
                                                                        HttpParam.HttpParameterStyle.HEADER.name())));
                                                        av2.add(AnnotationElement.of(
                                                                "example",
                                                                ByteCodes.annotationValue(
                                                                        ((RestHeader) ps[headIndex]).example())));
                                                    } else if (iscookie) {
                                                        av2.add(AnnotationElement.of(
                                                                "style",
                                                                AnnotationValue.ofEnum(
                                                                        ClassDesc.ofDescriptor(sourcetypeDesc),
                                                                        HttpParam.HttpParameterStyle.COOKIE.name())));
                                                        av2.add(AnnotationElement.of(
                                                                "example",
                                                                ByteCodes.annotationValue(
                                                                        ((RestCookie) ps[headIndex + 1]).example())));
                                                    } else if (isbody) {
                                                        av2.add(AnnotationElement.of(
                                                                "style",
                                                                AnnotationValue.ofEnum(
                                                                        ClassDesc.ofDescriptor(sourcetypeDesc),
                                                                        HttpParam.HttpParameterStyle.BODY.name())));
                                                        av2.add(AnnotationElement.of(
                                                                "example",
                                                                ByteCodes.annotationValue(
                                                                        ((RestBody) ps[headIndex + 2]).example())));
                                                    } else if (ps[6] != null) {
                                                        av2.add(AnnotationElement.of(
                                                                "style",
                                                                AnnotationValue.ofEnum(
                                                                        ClassDesc.ofDescriptor(sourcetypeDesc),
                                                                        HttpParam.HttpParameterStyle.QUERY.name())));
                                                        av2.add(AnnotationElement.of(
                                                                "example",
                                                                ByteCodes.annotationValue(
                                                                        ((RestParam) ps[6]).example())));
                                                    }
                                                    av2.add(AnnotationElement.of(
                                                            "comment", ByteCodes.annotationValue((String) ps[4])));
                                                    av2.add(AnnotationElement.of(
                                                            "required", ByteCodes.annotationValue((Boolean) ps[5])));
                                                    av1.add(AnnotationValue.ofAnnotation(
                                                            java.lang.classfile.Annotation.of(
                                                                    ClassDesc.ofDescriptor(httpParamDesc), av2)));
                                                }
                                            }
                                            av0.add(AnnotationElement.of("value", AnnotationValue.ofArray(av1)));
                                        }
                                        mvAnnotations.add(java.lang.classfile.Annotation.of(
                                                ClassDesc.ofDescriptor(httpParamsDesc), av0));
                                    }
                                }
                                int uploadLocal = 0;
                                if (mupload != null) { // 存在文件上传
                                    containsMupload[0] = true;
                                    if (muploadType == byte[].class) {
                                        mv.aload(1);
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(reqInternalName),
                                                "getMultiContext",
                                                MethodTypeDesc.ofDescriptor("()" + multiContextDesc));
                                        mv.loadConstant(mupload.maxLength());
                                        mv.loadConstant(mupload.fileNameRegex());
                                        mv.loadConstant(mupload.contentTypeRegex());
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(multiContextName),
                                                "partsFirstBytes",
                                                MethodTypeDesc.ofDescriptor(
                                                        "(JLjava/lang/String;Ljava/lang/String;)[B"));
                                        mv.astore(maxLocals);
                                        uploadLocal = maxLocals;
                                    } else if (muploadType == File.class) {
                                        mv.aload(1);
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(reqInternalName),
                                                "getMultiContext",
                                                MethodTypeDesc.ofDescriptor("()" + multiContextDesc));
                                        mv.aload(0);
                                        mv.getfield(
                                                ByteCodes.classDesc(newDynName),
                                                "_redkale_home",
                                                ClassDesc.ofDescriptor("Ljava/io/File;"));
                                        mv.loadConstant(mupload.maxLength());
                                        mv.loadConstant(mupload.fileNameRegex());
                                        mv.loadConstant(mupload.contentTypeRegex());
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(multiContextName),
                                                "partsFirstFile",
                                                MethodTypeDesc.ofDescriptor(
                                                        "(Ljava/io/File;JLjava/lang/String;Ljava/lang/String;)Ljava/io/File;"));
                                        mv.astore(maxLocals);
                                        uploadLocal = maxLocals;
                                    } else if (muploadType == File[].class) { // File[]
                                        mv.aload(1);
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(reqInternalName),
                                                "getMultiContext",
                                                MethodTypeDesc.ofDescriptor("()" + multiContextDesc));
                                        mv.aload(0);
                                        mv.getfield(
                                                ByteCodes.classDesc(newDynName),
                                                "_redkale_home",
                                                ClassDesc.ofDescriptor("Ljava/io/File;"));
                                        mv.loadConstant(mupload.maxLength());
                                        mv.loadConstant(mupload.fileNameRegex());
                                        mv.loadConstant(mupload.contentTypeRegex());
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(multiContextName),
                                                "partsFiles",
                                                MethodTypeDesc.ofDescriptor(
                                                        "(Ljava/io/File;JLjava/lang/String;Ljava/lang/String;)[Ljava/io/File;"));
                                        mv.astore(maxLocals);
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
                                    if (dynsimple[0]
                                            && (annsid != null
                                                    || annaddr != null
                                                    || annlocale != null
                                                    || annhead != null
                                                    || anncookie != null
                                                    || annfile != null
                                                    || annheaders)) {
                                        dynsimple[0] = false;
                                    }

                                    final boolean ishead = annhead != null; // 是否取getHeader 而不是 getParameter
                                    final boolean iscookie = anncookie != null; // 是否取getCookie

                                    paramMap.put("name", pname);
                                    paramMap.put("type", ptype.getName());
                                    if (CompletionHandler.class.isAssignableFrom(
                                            ptype)) { // HttpResponse.createAsyncHandler() or
                                        // HttpResponse.createAsyncHandler(Class)
                                        if (ptype == CompletionHandler.class) {
                                            mv.aload(2);
                                            mv.invokevirtual(
                                                    ByteCodes.classDesc(respInternalName),
                                                    "createAsyncHandler",
                                                    MethodTypeDesc.ofDescriptor(
                                                            "()Ljava/nio/channels/CompletionHandler;"));
                                            mv.astore(maxLocals);
                                            varInsns.add(new int[] {TypeKind.REFERENCE.ordinal(), maxLocals});
                                        } else {
                                            mv.aload(3);
                                            mv.aload(2);
                                            mv.loadConstant(ByteCodes.constantType(ByteCodes.descriptor(ptype)));
                                            mv.invokevirtual(
                                                    ByteCodes.classDesc(respInternalName),
                                                    "createAsyncHandler",
                                                    MethodTypeDesc.ofDescriptor(
                                                            "(Ljava/lang/Class;)Ljava/nio/channels/CompletionHandler;"));
                                            mv.checkcast(ByteCodes.classDesc(
                                                    ptype.getName().replace('.', '/')));
                                            mv.astore(maxLocals);
                                            varInsns.add(new int[] {TypeKind.REFERENCE.ordinal(), maxLocals});
                                        }
                                        hasAsyncHandler = true;
                                    } else if (annsid != null) { // HttpRequest.getSessionid(true|false)
                                        mv.aload(1);
                                        mv.loadConstant(annsid.create() ? 1 : 0);
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(reqInternalName),
                                                "getSessionid",
                                                MethodTypeDesc.ofDescriptor("(Z)Ljava/lang/String;"));
                                        mv.astore(maxLocals);
                                        varInsns.add(new int[] {TypeKind.REFERENCE.ordinal(), maxLocals});
                                    } else if (annaddr != null) { // HttpRequest.getRemoteAddr
                                        mv.aload(1);
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(reqInternalName),
                                                "getRemoteAddr",
                                                MethodTypeDesc.ofDescriptor("()Ljava/lang/String;"));
                                        mv.astore(maxLocals);
                                        varInsns.add(new int[] {TypeKind.REFERENCE.ordinal(), maxLocals});
                                    } else if (annlocale != null) { // HttpRequest.getLocale
                                        mv.aload(1);
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(reqInternalName),
                                                "getLocale",
                                                MethodTypeDesc.ofDescriptor("()Ljava/lang/String;"));
                                        mv.astore(maxLocals);
                                        varInsns.add(new int[] {TypeKind.REFERENCE.ordinal(), maxLocals});
                                    } else if (annheaders) { // HttpRequest.getHeaders
                                        mv.aload(1);
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(reqInternalName),
                                                "getHeaders",
                                                MethodTypeDesc.ofDescriptor("()" + httpHeadersDesc));
                                        mv.astore(maxLocals);
                                        varInsns.add(new int[] {TypeKind.REFERENCE.ordinal(), maxLocals});
                                    } else if (annparams) { // HttpRequest.getParameters
                                        mv.aload(1);
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(reqInternalName),
                                                "getParameters",
                                                MethodTypeDesc.ofDescriptor("()" + httpParametersDesc));
                                        mv.astore(maxLocals);
                                        varInsns.add(new int[] {TypeKind.REFERENCE.ordinal(), maxLocals});
                                    } else if (annbody != null) { // HttpRequest.getBodyUTF8 / HttpRequest.getBody
                                        if (ptype == String.class) {
                                            mv.aload(1);
                                            mv.invokevirtual(
                                                    ByteCodes.classDesc(reqInternalName),
                                                    "getBodyUTF8",
                                                    MethodTypeDesc.ofDescriptor("()Ljava/lang/String;"));
                                            mv.astore(maxLocals);
                                            varInsns.add(new int[] {TypeKind.REFERENCE.ordinal(), maxLocals});
                                        } else if (ptype == byte[].class) {
                                            mv.aload(1);
                                            mv.invokevirtual(
                                                    ByteCodes.classDesc(reqInternalName),
                                                    "getBody",
                                                    MethodTypeDesc.ofDescriptor("()[B"));
                                            mv.astore(maxLocals);
                                            varInsns.add(new int[] {TypeKind.REFERENCE.ordinal(), maxLocals});
                                        } else { // JavaBean 转 Json
                                            String typefieldname = "_redkale_body_jsontype_" + bodyTypes.size();
                                            bodyTypes.put(typefieldname, pgentype);
                                            mv.aload(1);
                                            mv.aload(0);
                                            mv.getfield(
                                                    ByteCodes.classDesc(newDynName),
                                                    typefieldname,
                                                    ClassDesc.ofDescriptor("Ljava/lang/reflect/Type;"));
                                            mv.invokevirtual(
                                                    ByteCodes.classDesc(reqInternalName),
                                                    "getBodyJson",
                                                    MethodTypeDesc.ofDescriptor(
                                                            "(Ljava/lang/reflect/Type;)Ljava/lang/Object;"));
                                            mv.checkcast(ByteCodes.classDesc(ByteCodes.internalName(ptype)));
                                            mv.astore(maxLocals);
                                            varInsns.add(new int[] {TypeKind.REFERENCE.ordinal(), maxLocals});
                                        }
                                    } else if (annfile
                                            != null) { // MultiContext.partsFirstBytes / HttpRequest.partsFirstFile /
                                        // HttpRequest.partsFiles
                                        mv.aload(4);
                                        mv.astore(maxLocals);
                                        varInsns.add(new int[] {TypeKind.REFERENCE.ordinal(), maxLocals});
                                    } else if (annpath != null) { // HttpRequest.getRequestPath
                                        mv.aload(1);
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(reqInternalName),
                                                "getRequestPath",
                                                MethodTypeDesc.ofDescriptor("()Ljava/lang/String;"));
                                        mv.astore(maxLocals);
                                        varInsns.add(new int[] {TypeKind.REFERENCE.ordinal(), maxLocals});
                                    } else if (userid != null) { // HttpRequest.currentUserid
                                        mv.aload(1);
                                        if (ptype == int.class) {
                                            mv.invokevirtual(
                                                    ByteCodes.classDesc(reqInternalName),
                                                    "currentIntUserid",
                                                    MethodTypeDesc.ofDescriptor("()I"));
                                            mv.istore(maxLocals);
                                            varInsns.add(new int[] {TypeKind.INT.ordinal(), maxLocals});
                                        } else if (ptype == long.class) {
                                            mv.invokevirtual(
                                                    ByteCodes.classDesc(reqInternalName),
                                                    "currentLongUserid",
                                                    MethodTypeDesc.ofDescriptor("()J"));
                                            mv.lstore(maxLocals);
                                            varInsns.add(new int[] {TypeKind.LONG.ordinal(), maxLocals});
                                            maxLocals++;
                                        } else if (ptype == String.class) {
                                            mv.invokevirtual(
                                                    ByteCodes.classDesc(reqInternalName),
                                                    "currentStringUserid",
                                                    MethodTypeDesc.ofDescriptor("()Ljava/lang/String;"));
                                            mv.astore(maxLocals);
                                            varInsns.add(new int[] {TypeKind.REFERENCE.ordinal(), maxLocals});
                                        } else {
                                            mv.loadConstant(ByteCodes.constantType(ByteCodes.descriptor(ptype)));
                                            mv.invokevirtual(
                                                    ByteCodes.classDesc(reqInternalName),
                                                    "currentUserid",
                                                    MethodTypeDesc.ofDescriptor(
                                                            "(Ljava/lang/Class;)Ljava/io/Serializable;"));
                                            mv.checkcast(ByteCodes.classDesc(ByteCodes.internalName(ptype)));
                                            mv.astore(maxLocals);
                                            varInsns.add(new int[] {TypeKind.REFERENCE.ordinal(), maxLocals});
                                        }
                                    } else if ("#".equals(pname)) { // 从request.getRequstURI 中取参数
                                        if (ptype == boolean.class) {
                                            mv.aload(1);
                                            mv.invokevirtual(
                                                    ByteCodes.classDesc(reqInternalName),
                                                    "getPathLastParam",
                                                    MethodTypeDesc.ofDescriptor("()Ljava/lang/String;"));
                                            mv.invokestatic(
                                                    ByteCodes.classDesc("java/lang/Boolean"),
                                                    "parseBoolean",
                                                    MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)Z"),
                                                    false);
                                            mv.istore(maxLocals);
                                            varInsns.add(new int[] {TypeKind.INT.ordinal(), maxLocals});
                                        } else if (ptype == byte.class) {
                                            mv.aload(1);
                                            mv.invokevirtual(
                                                    ByteCodes.classDesc(reqInternalName),
                                                    "getPathLastParam",
                                                    MethodTypeDesc.ofDescriptor("()Ljava/lang/String;"));
                                            mv.loadConstant(radix);
                                            mv.invokestatic(
                                                    ByteCodes.classDesc("java/lang/Byte"),
                                                    "parseByte",
                                                    MethodTypeDesc.ofDescriptor("(Ljava/lang/String;I)B"),
                                                    false);
                                            mv.istore(maxLocals);
                                            varInsns.add(new int[] {TypeKind.INT.ordinal(), maxLocals});
                                        } else if (ptype == short.class) {
                                            mv.aload(1);
                                            mv.invokevirtual(
                                                    ByteCodes.classDesc(reqInternalName),
                                                    "getPathLastParam",
                                                    MethodTypeDesc.ofDescriptor("()Ljava/lang/String;"));
                                            mv.loadConstant(radix);
                                            mv.invokestatic(
                                                    ByteCodes.classDesc("java/lang/Short"),
                                                    "parseShort",
                                                    MethodTypeDesc.ofDescriptor("(Ljava/lang/String;I)S"),
                                                    false);
                                            mv.istore(maxLocals);
                                            varInsns.add(new int[] {TypeKind.INT.ordinal(), maxLocals});
                                        } else if (ptype == char.class) {
                                            mv.aload(1);
                                            mv.invokevirtual(
                                                    ByteCodes.classDesc(reqInternalName),
                                                    "getPathLastParam",
                                                    MethodTypeDesc.ofDescriptor("()Ljava/lang/String;"));
                                            mv.iconst_0();
                                            mv.invokevirtual(
                                                    ByteCodes.classDesc("java/lang/String"),
                                                    "charAt",
                                                    MethodTypeDesc.ofDescriptor("(I)C"));
                                            mv.istore(maxLocals);
                                            varInsns.add(new int[] {TypeKind.INT.ordinal(), maxLocals});
                                        } else if (ptype == int.class) {
                                            mv.aload(1);
                                            mv.invokevirtual(
                                                    ByteCodes.classDesc(reqInternalName),
                                                    "getPathLastParam",
                                                    MethodTypeDesc.ofDescriptor("()Ljava/lang/String;"));
                                            mv.loadConstant(radix);
                                            mv.invokestatic(
                                                    ByteCodes.classDesc("java/lang/Integer"),
                                                    "parseInt",
                                                    MethodTypeDesc.ofDescriptor("(Ljava/lang/String;I)I"),
                                                    false);
                                            mv.istore(maxLocals);
                                            varInsns.add(new int[] {TypeKind.INT.ordinal(), maxLocals});
                                        } else if (ptype == float.class) {
                                            mv.aload(1);
                                            mv.invokevirtual(
                                                    ByteCodes.classDesc(reqInternalName),
                                                    "getPathLastParam",
                                                    MethodTypeDesc.ofDescriptor("()Ljava/lang/String;"));
                                            mv.invokestatic(
                                                    ByteCodes.classDesc("java/lang/Float"),
                                                    "parseFloat",
                                                    MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)F"),
                                                    false);
                                            mv.fstore(maxLocals);
                                            varInsns.add(new int[] {TypeKind.FLOAT.ordinal(), maxLocals});
                                        } else if (ptype == long.class) {
                                            mv.aload(1);
                                            mv.invokevirtual(
                                                    ByteCodes.classDesc(reqInternalName),
                                                    "getPathLastParam",
                                                    MethodTypeDesc.ofDescriptor("()Ljava/lang/String;"));
                                            mv.loadConstant(radix);
                                            mv.invokestatic(
                                                    ByteCodes.classDesc("java/lang/Long"),
                                                    "parseLong",
                                                    MethodTypeDesc.ofDescriptor("(Ljava/lang/String;I)J"),
                                                    false);
                                            mv.lstore(maxLocals);
                                            varInsns.add(new int[] {TypeKind.LONG.ordinal(), maxLocals});
                                            maxLocals++;
                                        } else if (ptype == double.class) {
                                            mv.aload(1);
                                            mv.invokevirtual(
                                                    ByteCodes.classDesc(reqInternalName),
                                                    "getPathLastParam",
                                                    MethodTypeDesc.ofDescriptor("()Ljava/lang/String;"));
                                            mv.invokestatic(
                                                    ByteCodes.classDesc("java/lang/Double"),
                                                    "parseDouble",
                                                    MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)D"),
                                                    false);
                                            mv.dstore(maxLocals);
                                            varInsns.add(new int[] {TypeKind.DOUBLE.ordinal(), maxLocals});
                                            maxLocals++;
                                        } else if (ptype == String.class) {
                                            mv.aload(1);
                                            mv.invokevirtual(
                                                    ByteCodes.classDesc(reqInternalName),
                                                    "getPathLastParam",
                                                    MethodTypeDesc.ofDescriptor("()Ljava/lang/String;"));
                                            mv.astore(maxLocals);
                                            varInsns.add(new int[] {TypeKind.REFERENCE.ordinal(), maxLocals});
                                        } else {
                                            throw new RestException(method + " only " + RestParam.class.getSimpleName()
                                                    + "(#) to Type(primitive class or String)");
                                        }
                                    } else if (pname.charAt(0) == '#') { // 从request.getPathParam 中去参数
                                        if (ptype == boolean.class) {
                                            mv.aload(1);
                                            mv.loadConstant(pname.substring(1));
                                            mv.loadConstant("false");
                                            mv.invokevirtual(
                                                    ByteCodes.classDesc(reqInternalName),
                                                    "getPathParam",
                                                    MethodTypeDesc.ofDescriptor(
                                                            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"));
                                            mv.invokestatic(
                                                    ByteCodes.classDesc("java/lang/Boolean"),
                                                    "parseBoolean",
                                                    MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)Z"),
                                                    false);
                                            mv.istore(maxLocals);
                                            varInsns.add(new int[] {TypeKind.INT.ordinal(), maxLocals});
                                        } else if (ptype == byte.class) {
                                            mv.aload(1);
                                            mv.loadConstant(pname.substring(1));
                                            mv.loadConstant("0");
                                            mv.invokevirtual(
                                                    ByteCodes.classDesc(reqInternalName),
                                                    "getPathParam",
                                                    MethodTypeDesc.ofDescriptor(
                                                            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"));
                                            mv.loadConstant(radix);
                                            mv.invokestatic(
                                                    ByteCodes.classDesc("java/lang/Byte"),
                                                    "parseByte",
                                                    MethodTypeDesc.ofDescriptor("(Ljava/lang/String;I)B"),
                                                    false);
                                            mv.istore(maxLocals);
                                            varInsns.add(new int[] {TypeKind.INT.ordinal(), maxLocals});
                                        } else if (ptype == short.class) {
                                            mv.aload(1);
                                            mv.loadConstant(pname.substring(1));
                                            mv.loadConstant("0");
                                            mv.invokevirtual(
                                                    ByteCodes.classDesc(reqInternalName),
                                                    "getPathParam",
                                                    MethodTypeDesc.ofDescriptor(
                                                            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"));
                                            mv.loadConstant(radix);
                                            mv.invokestatic(
                                                    ByteCodes.classDesc("java/lang/Short"),
                                                    "parseShort",
                                                    MethodTypeDesc.ofDescriptor("(Ljava/lang/String;I)S"),
                                                    false);
                                            mv.istore(maxLocals);
                                            varInsns.add(new int[] {TypeKind.INT.ordinal(), maxLocals});
                                        } else if (ptype == char.class) {
                                            mv.aload(1);
                                            mv.loadConstant(pname.substring(1));
                                            mv.loadConstant("0");
                                            mv.invokevirtual(
                                                    ByteCodes.classDesc(reqInternalName),
                                                    "getPathParam",
                                                    MethodTypeDesc.ofDescriptor(
                                                            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"));
                                            mv.iconst_0();
                                            mv.invokevirtual(
                                                    ByteCodes.classDesc("java/lang/String"),
                                                    "charAt",
                                                    MethodTypeDesc.ofDescriptor("(I)C"));
                                            mv.istore(maxLocals);
                                            varInsns.add(new int[] {TypeKind.INT.ordinal(), maxLocals});
                                        } else if (ptype == int.class) {
                                            mv.aload(1);
                                            mv.loadConstant(pname.substring(1));
                                            mv.loadConstant("0");
                                            mv.invokevirtual(
                                                    ByteCodes.classDesc(reqInternalName),
                                                    "getPathParam",
                                                    MethodTypeDesc.ofDescriptor(
                                                            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"));
                                            mv.loadConstant(radix);
                                            mv.invokestatic(
                                                    ByteCodes.classDesc("java/lang/Integer"),
                                                    "parseInt",
                                                    MethodTypeDesc.ofDescriptor("(Ljava/lang/String;I)I"),
                                                    false);
                                            mv.istore(maxLocals);
                                            varInsns.add(new int[] {TypeKind.INT.ordinal(), maxLocals});
                                        } else if (ptype == float.class) {
                                            mv.aload(1);
                                            mv.loadConstant(pname.substring(1));
                                            mv.loadConstant("0");
                                            mv.invokevirtual(
                                                    ByteCodes.classDesc(reqInternalName),
                                                    "getPathParam",
                                                    MethodTypeDesc.ofDescriptor(
                                                            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"));
                                            mv.invokestatic(
                                                    ByteCodes.classDesc("java/lang/Float"),
                                                    "parseFloat",
                                                    MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)F"),
                                                    false);
                                            mv.fstore(maxLocals);
                                            varInsns.add(new int[] {TypeKind.FLOAT.ordinal(), maxLocals});
                                        } else if (ptype == long.class) {
                                            mv.aload(1);
                                            mv.loadConstant(pname.substring(1));
                                            mv.loadConstant("0");
                                            mv.invokevirtual(
                                                    ByteCodes.classDesc(reqInternalName),
                                                    "getPathParam",
                                                    MethodTypeDesc.ofDescriptor(
                                                            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"));
                                            mv.loadConstant(radix);
                                            mv.invokestatic(
                                                    ByteCodes.classDesc("java/lang/Long"),
                                                    "parseLong",
                                                    MethodTypeDesc.ofDescriptor("(Ljava/lang/String;I)J"),
                                                    false);
                                            mv.lstore(maxLocals);
                                            varInsns.add(new int[] {TypeKind.LONG.ordinal(), maxLocals});
                                            maxLocals++;
                                        } else if (ptype == double.class) {
                                            mv.aload(1);
                                            mv.loadConstant(pname.substring(1));
                                            mv.loadConstant("0");
                                            mv.invokevirtual(
                                                    ByteCodes.classDesc(reqInternalName),
                                                    "getPathParam",
                                                    MethodTypeDesc.ofDescriptor(
                                                            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"));
                                            mv.invokestatic(
                                                    ByteCodes.classDesc("java/lang/Double"),
                                                    "parseDouble",
                                                    MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)D"),
                                                    false);
                                            mv.dstore(maxLocals);
                                            varInsns.add(new int[] {TypeKind.DOUBLE.ordinal(), maxLocals});
                                            maxLocals++;
                                        } else if (ptype == String.class) {
                                            mv.aload(1);
                                            mv.loadConstant(pname.substring(1));
                                            mv.loadConstant("");
                                            mv.invokevirtual(
                                                    ByteCodes.classDesc(reqInternalName),
                                                    "getPathParam",
                                                    MethodTypeDesc.ofDescriptor(
                                                            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"));
                                            mv.astore(maxLocals);
                                            varInsns.add(new int[] {TypeKind.REFERENCE.ordinal(), maxLocals});
                                        } else {
                                            throw new RestException(method + " only " + RestParam.class.getSimpleName()
                                                    + "(#) to Type(primitive class or String)");
                                        }
                                    } else if ("&".equals(pname) && ptype == userType) { // 当前用户对象的类名
                                        mv.aload(1);
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(reqInternalName),
                                                "currentUser",
                                                MethodTypeDesc.ofDescriptor("()Ljava/lang/Object;"));
                                        mv.checkcast(ByteCodes.classDesc(ByteCodes.internalName(ptype)));
                                        mv.astore(maxLocals);
                                        varInsns.add(new int[] {TypeKind.REFERENCE.ordinal(), maxLocals});
                                    } else if (ptype == boolean.class) {
                                        mv.aload(1);
                                        mv.loadConstant(pname);
                                        mv.iconst_0();
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(reqInternalName),
                                                ishead ? "getBooleanHeader" : "getBooleanParameter",
                                                MethodTypeDesc.ofDescriptor("(Ljava/lang/String;Z)Z"));
                                        mv.istore(maxLocals);
                                        varInsns.add(new int[] {TypeKind.INT.ordinal(), maxLocals});
                                    } else if (ptype == byte.class) {
                                        mv.aload(1);
                                        mv.loadConstant(pname);
                                        mv.loadConstant("0");
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(reqInternalName),
                                                ishead ? "getHeader" : "getParameter",
                                                MethodTypeDesc.ofDescriptor(
                                                        "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"));
                                        mv.loadConstant(radix);
                                        mv.invokestatic(
                                                ByteCodes.classDesc("java/lang/Byte"),
                                                "parseByte",
                                                MethodTypeDesc.ofDescriptor("(Ljava/lang/String;I)B"),
                                                false);
                                        mv.istore(maxLocals);
                                        varInsns.add(new int[] {TypeKind.INT.ordinal(), maxLocals});
                                    } else if (ptype == short.class) {
                                        mv.aload(1);
                                        mv.loadConstant(radix);
                                        mv.loadConstant(pname);
                                        mv.iconst_0();
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(reqInternalName),
                                                ishead ? "getShortHeader" : "getShortParameter",
                                                MethodTypeDesc.ofDescriptor("(ILjava/lang/String;S)S"));
                                        mv.istore(maxLocals);
                                        varInsns.add(new int[] {TypeKind.INT.ordinal(), maxLocals});
                                    } else if (ptype == char.class) {
                                        mv.aload(1);
                                        mv.loadConstant(pname);
                                        mv.loadConstant("0");
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(reqInternalName),
                                                ishead ? "getHeader" : "getParameter",
                                                MethodTypeDesc.ofDescriptor(
                                                        "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"));
                                        mv.iconst_0();
                                        mv.invokevirtual(
                                                ByteCodes.classDesc("java/lang/String"),
                                                "charAt",
                                                MethodTypeDesc.ofDescriptor("(I)C"));
                                        mv.istore(maxLocals);
                                        varInsns.add(new int[] {TypeKind.INT.ordinal(), maxLocals});
                                    } else if (ptype == int.class) {
                                        mv.aload(1);
                                        mv.loadConstant(radix);
                                        mv.loadConstant(pname);
                                        mv.iconst_0();
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(reqInternalName),
                                                ishead ? "getIntHeader" : "getIntParameter",
                                                MethodTypeDesc.ofDescriptor("(ILjava/lang/String;I)I"));
                                        mv.istore(maxLocals);
                                        varInsns.add(new int[] {TypeKind.INT.ordinal(), maxLocals});
                                    } else if (ptype == float.class) {
                                        mv.aload(1);
                                        mv.loadConstant(pname);
                                        mv.fconst_0();
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(reqInternalName),
                                                ishead ? "getFloatHeader" : "getFloatParameter",
                                                MethodTypeDesc.ofDescriptor("(Ljava/lang/String;F)F"));
                                        mv.fstore(maxLocals);
                                        varInsns.add(new int[] {TypeKind.FLOAT.ordinal(), maxLocals});
                                    } else if (ptype == long.class) {
                                        mv.aload(1);
                                        mv.loadConstant(radix);
                                        mv.loadConstant(pname);
                                        mv.lconst_0();
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(reqInternalName),
                                                ishead ? "getLongHeader" : "getLongParameter",
                                                MethodTypeDesc.ofDescriptor("(ILjava/lang/String;J)J"));
                                        mv.lstore(maxLocals);
                                        varInsns.add(new int[] {TypeKind.LONG.ordinal(), maxLocals});
                                        maxLocals++;
                                    } else if (ptype == double.class) {
                                        mv.aload(1);
                                        mv.loadConstant(pname);
                                        mv.dconst_0();
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(reqInternalName),
                                                ishead ? "getDoubleHeader" : "getDoubleParameter",
                                                MethodTypeDesc.ofDescriptor("(Ljava/lang/String;D)D"));
                                        mv.dstore(maxLocals);
                                        varInsns.add(new int[] {TypeKind.DOUBLE.ordinal(), maxLocals});
                                        maxLocals++;
                                    } else if (ptype == String.class) {
                                        mv.aload(1);
                                        mv.loadConstant(pname);
                                        mv.loadConstant("");
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(reqInternalName),
                                                iscookie ? "getCookie" : (ishead ? "getHeader" : "getParameter"),
                                                MethodTypeDesc.ofDescriptor(
                                                        "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"));
                                        mv.astore(maxLocals);
                                        varInsns.add(new int[] {TypeKind.REFERENCE.ordinal(), maxLocals});
                                    } else if (ptype == Flipper.class) {
                                        mv.aload(1);
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(reqInternalName),
                                                "getFlipper",
                                                MethodTypeDesc.ofDescriptor("()" + flipperDesc));
                                        mv.astore(maxLocals);
                                        varInsns.add(new int[] {TypeKind.REFERENCE.ordinal(), maxLocals});
                                    } else { // 其他Json对象
                                        mv.aload(1);
                                        if (param.getType() == param.getParameterizedType()) {
                                            mv.loadConstant(ByteCodes.constantType(ByteCodes.descriptor(ptype)));
                                        } else {
                                            mv.aload(0);
                                            mv.getfield(
                                                    ByteCodes.classDesc(newDynName),
                                                    REST_PARAMTYPES_FIELD_NAME,
                                                    ClassDesc.ofDescriptor("[[Ljava/lang/reflect/Type;"));
                                            mv.loadConstant(entry.methodIdx); // 方法下标
                                            mv.aaload();
                                            int paramidx = -1;
                                            for (int i = 0; i < params.length; i++) {
                                                if (params[i] == param) {
                                                    paramidx = i;
                                                    break;
                                                }
                                            }
                                            mv.loadConstant(paramidx); // 参数下标
                                            mv.aaload();
                                        }
                                        mv.loadConstant(pname);
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(reqInternalName),
                                                ishead ? "getJsonHeader" : "getJsonParameter",
                                                MethodTypeDesc.ofDescriptor(
                                                        "(Ljava/lang/reflect/Type;Ljava/lang/String;)Ljava/lang/Object;"));
                                        mv.checkcast(ByteCodes.classDesc(
                                                ptype.getName().replace('.', '/')));
                                        mv.astore(maxLocals);
                                        varInsns.add(new int[] {TypeKind.REFERENCE.ordinal(), maxLocals});
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
                                                    throw new RestException(
                                                            "@RestHeader must on String Field in " + field);
                                                }
                                                if (rc != null && field.getType() != String.class) {
                                                    throw new RestException(
                                                            "@RestCookie must on String Field in " + field);
                                                }
                                                if (rs != null && field.getType() != String.class) {
                                                    throw new RestException(
                                                            "@RestSessionid must on String Field in " + field);
                                                }
                                                if (ra != null && field.getType() != String.class) {
                                                    throw new RestException(
                                                            "@RestAddress must on String Field in " + field);
                                                }
                                                if (rl != null && field.getType() != String.class) {
                                                    throw new RestException(
                                                            "@RestLocale must on String Field in " + field);
                                                }
                                                if (rb != null
                                                        && field.getType().isPrimitive()) {
                                                    throw new RestException(
                                                            "@RestBody must on cannot on primitive type Field in "
                                                                    + field);
                                                }
                                                if (ru != null
                                                        && field.getType() != byte[].class
                                                        && field.getType() != File.class
                                                        && field.getType() != File[].class) {
                                                    throw new RestException(
                                                            "@RestUploadFile must on byte[] or File or File[] Field in "
                                                                    + field);
                                                }

                                                if (ri != null && field.getType() != String.class) {
                                                    throw new RestException(
                                                            "@RestPath must on String Field in " + field);
                                                }
                                                org.redkale.util.Attribute attr =
                                                        org.redkale.util.Attribute.create(loop, field);
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
                                                    attrFieldName =
                                                            "_redkale_attr_uploadbytes_" + restAttributes.size();
                                                    // restname = "";
                                                } else if (ru != null && field.getType() == File.class) {
                                                    attrFieldName = "_redkale_attr_uploadfile_" + restAttributes.size();
                                                    // restname = "";
                                                } else if (ru != null && field.getType() == File[].class) {
                                                    attrFieldName =
                                                            "_redkale_attr_uploadfiles_" + restAttributes.size();
                                                    // restname = "";
                                                } else if (ri != null && field.getType() == String.class) {
                                                    attrFieldName = "_redkale_attr_uri_" + restAttributes.size();
                                                    // restname = "";
                                                } else {
                                                    continue;
                                                }
                                                restAttributes.put(attrFieldName, attr);
                                                attrParaNames.put(attrFieldName, new Object[] {
                                                    restname, field.getType(), field.getGenericType(), ru
                                                });
                                                fields.add(field.getName());
                                            }
                                        } while ((loop = loop.getSuperclass()) != Object.class);

                                        if (!attrParaNames.isEmpty()) { // 参数存在
                                            // RestHeader、RestCookie、RestSessionid、RestAddress、RestLocale、RestBody字段
                                            mv.aload(maxLocals); // 加载JsonBean
                                            Label lif = mv.newLabel();
                                            mv.ifnull(lif); // if(bean != null) {
                                            for (Map.Entry<String, Object[]> en : attrParaNames.entrySet()) {
                                                RestUploadFile ru = (RestUploadFile) en.getValue()[3];
                                                mv.aload(0);
                                                mv.getfield(
                                                        ByteCodes.classDesc(newDynName),
                                                        en.getKey(),
                                                        ClassDesc.ofDescriptor(attrDesc));
                                                mv.aload(maxLocals);
                                                mv.aload(en.getKey().contains("_upload") ? uploadLocal : 1);
                                                if (en.getKey().contains("_header_")) {
                                                    String headerkey = en.getValue()[0].toString();
                                                    if ("Host".equalsIgnoreCase(headerkey)) {
                                                        mv.invokevirtual(
                                                                ByteCodes.classDesc(reqInternalName),
                                                                "getHost",
                                                                MethodTypeDesc.ofDescriptor("()Ljava/lang/String;"));
                                                    } else if ("Content-Type".equalsIgnoreCase(headerkey)) {
                                                        mv.invokevirtual(
                                                                ByteCodes.classDesc(reqInternalName),
                                                                "getContentType",
                                                                MethodTypeDesc.ofDescriptor("()Ljava/lang/String;"));
                                                    } else if ("Connection".equalsIgnoreCase(headerkey)) {
                                                        mv.invokevirtual(
                                                                ByteCodes.classDesc(reqInternalName),
                                                                "getConnection",
                                                                MethodTypeDesc.ofDescriptor("()Ljava/lang/String;"));
                                                    } else if ("Method".equalsIgnoreCase(headerkey)) {
                                                        mv.invokevirtual(
                                                                ByteCodes.classDesc(reqInternalName),
                                                                "getMethod",
                                                                MethodTypeDesc.ofDescriptor("()Ljava/lang/String;"));
                                                    } else if (en.getKey().contains("_header_json_")) {
                                                        String typefieldname =
                                                                "_redkale_body_jsontype_" + bodyTypes.size();
                                                        bodyTypes.put(typefieldname, (java.lang.reflect.Type)
                                                                en.getValue()[2]);
                                                        mv.aload(0);
                                                        mv.getfield(
                                                                ByteCodes.classDesc(newDynName),
                                                                typefieldname,
                                                                ClassDesc.ofDescriptor("Ljava/lang/reflect/Type;"));
                                                        mv.loadConstant(headerkey);
                                                        mv.invokevirtual(
                                                                ByteCodes.classDesc(reqInternalName),
                                                                "getJsonHeader",
                                                                MethodTypeDesc.ofDescriptor(
                                                                        "(Ljava/lang/reflect/Type;Ljava/lang/String;)Ljava/lang/Object;"));
                                                        mv.checkcast(ByteCodes.classDesc(
                                                                ByteCodes.internalName((Class) en.getValue()[1])));
                                                        JsonFactory.root()
                                                                .loadDecoder((java.lang.reflect.Type) en.getValue()[2]);
                                                    } else {
                                                        mv.loadConstant(headerkey);
                                                        mv.loadConstant("");
                                                        mv.invokevirtual(
                                                                ByteCodes.classDesc(reqInternalName),
                                                                "getHeader",
                                                                MethodTypeDesc.ofDescriptor(
                                                                        "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"));
                                                    }
                                                } else if (en.getKey().contains("_cookie_")) {
                                                    mv.loadConstant(en.getValue()[0].toString());
                                                    mv.loadConstant("");
                                                    mv.invokevirtual(
                                                            ByteCodes.classDesc(reqInternalName),
                                                            "getCookie",
                                                            MethodTypeDesc.ofDescriptor(
                                                                    "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"));
                                                } else if (en.getKey().contains("_sessionid_")) {
                                                    mv.loadConstant(
                                                            en.getValue()[0]
                                                                            .toString()
                                                                            .isEmpty()
                                                                    ? 0
                                                                    : 1);
                                                    mv.invokevirtual(
                                                            ByteCodes.classDesc(reqInternalName),
                                                            "getSessionid",
                                                            MethodTypeDesc.ofDescriptor("(Z)Ljava/lang/String;"));
                                                } else if (en.getKey().contains("_address_")) {
                                                    mv.invokevirtual(
                                                            ByteCodes.classDesc(reqInternalName),
                                                            "getRemoteAddr",
                                                            MethodTypeDesc.ofDescriptor("()Ljava/lang/String;"));
                                                } else if (en.getKey().contains("_locale_")) {
                                                    mv.invokevirtual(
                                                            ByteCodes.classDesc(reqInternalName),
                                                            "getLocale",
                                                            MethodTypeDesc.ofDescriptor("()Ljava/lang/String;"));
                                                } else if (en.getKey().contains("_uri_")) {
                                                    mv.invokevirtual(
                                                            ByteCodes.classDesc(reqInternalName),
                                                            "getPath",
                                                            MethodTypeDesc.ofDescriptor("()Ljava/lang/String;"));
                                                } else if (en.getKey().contains("_bodystring_")) {
                                                    mv.invokevirtual(
                                                            ByteCodes.classDesc(reqInternalName),
                                                            "getBodyUTF8",
                                                            MethodTypeDesc.ofDescriptor("()Ljava/lang/String;"));
                                                } else if (en.getKey().contains("_bodybytes_")) {
                                                    mv.invokevirtual(
                                                            ByteCodes.classDesc(reqInternalName),
                                                            "getBody",
                                                            MethodTypeDesc.ofDescriptor("()[B"));
                                                } else if (en.getKey().contains("_bodyjson_")) { // JavaBean 转 Json
                                                    String typefieldname = "_redkale_body_jsontype_" + bodyTypes.size();
                                                    bodyTypes.put(
                                                            typefieldname, (java.lang.reflect.Type) en.getValue()[2]);
                                                    mv.aload(0);
                                                    mv.getfield(
                                                            ByteCodes.classDesc(newDynName),
                                                            typefieldname,
                                                            ClassDesc.ofDescriptor("Ljava/lang/reflect/Type;"));
                                                    mv.invokevirtual(
                                                            ByteCodes.classDesc(reqInternalName),
                                                            "getBodyJson",
                                                            MethodTypeDesc.ofDescriptor(
                                                                    "(Ljava/lang/reflect/Type;)Ljava/lang/Object;"));
                                                    mv.checkcast(ByteCodes.classDesc(
                                                            ByteCodes.internalName((Class) en.getValue()[1])));
                                                    JsonFactory.root()
                                                            .loadDecoder((java.lang.reflect.Type) en.getValue()[2]);
                                                } else if (en.getKey().contains("_uploadbytes_")) {

                                                } else if (en.getKey().contains("_uploadfile_")) {

                                                } else if (en.getKey().contains("_uploadfiles_")) {

                                                }
                                                mv.invokeinterface(
                                                        ByteCodes.classDesc(attrInternalName),
                                                        "set",
                                                        MethodTypeDesc.ofDescriptor(
                                                                "(Ljava/lang/Object;Ljava/lang/Object;)V"));
                                            }
                                            mv.labelBinding(lif); // end if }
                                        }
                                    }
                                    maxLocals++;
                                    paramMaps.add(paramMap);
                                } // end params for each

                                mv.aload(3);
                                for (int[] ins : varInsns) {
                                    mv.loadLocal(TypeKind.values()[ins[0]], ins[1]);
                                }
                                mv.invokevirtual(
                                        ByteCodes.classDesc(serviceTypeInternalName),
                                        method.getName(),
                                        MethodTypeDesc.ofDescriptor(methodDesc));
                                if (hasAsyncHandler) {
                                    mv.return_();
                                } else if (returnType == void.class) {
                                    mv.aload(2);
                                    mv.aload(0);
                                    mv.getfield(
                                            ByteCodes.classDesc(newDynName),
                                            REST_RETURNTYPES_FIELD_NAME,
                                            ClassDesc.ofDescriptor("[Ljava/lang/reflect/Type;"));
                                    mv.loadConstant(entry.methodIdx); // 方法下标
                                    mv.aaload();
                                    mv.invokestatic(
                                            ByteCodes.classDesc(retInternalName),
                                            "success",
                                            MethodTypeDesc.ofDescriptor("()" + retDesc),
                                            false);
                                    mv.invokevirtual(
                                            ByteCodes.classDesc(respInternalName),
                                            "finishJson",
                                            MethodTypeDesc.ofDescriptor("(" + typeDesc + "Ljava/lang/Object;)V"));
                                    mv.return_();
                                } else if (returnType == boolean.class) {
                                    mv.istore(maxLocals);
                                    mv.aload(2); // response
                                    mv.iload(maxLocals);
                                    mv.invokestatic(
                                            ByteCodes.classDesc("java/lang/String"),
                                            "valueOf",
                                            MethodTypeDesc.ofDescriptor("(Z)Ljava/lang/String;"),
                                            false);
                                    mv.invokevirtual(
                                            ByteCodes.classDesc(respInternalName),
                                            "finish",
                                            MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)V"));
                                    mv.return_();
                                    maxLocals++;
                                } else if (returnType == byte.class) {
                                    mv.istore(maxLocals);
                                    mv.aload(2); // response
                                    mv.iload(maxLocals);
                                    mv.invokestatic(
                                            ByteCodes.classDesc("java/lang/String"),
                                            "valueOf",
                                            MethodTypeDesc.ofDescriptor("(I)Ljava/lang/String;"),
                                            false);
                                    mv.invokevirtual(
                                            ByteCodes.classDesc(respInternalName),
                                            "finish",
                                            MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)V"));
                                    mv.return_();
                                    maxLocals++;
                                } else if (returnType == short.class) {
                                    mv.istore(maxLocals);
                                    mv.aload(2); // response
                                    mv.iload(maxLocals);
                                    mv.invokestatic(
                                            ByteCodes.classDesc("java/lang/String"),
                                            "valueOf",
                                            MethodTypeDesc.ofDescriptor("(I)Ljava/lang/String;"),
                                            false);
                                    mv.invokevirtual(
                                            ByteCodes.classDesc(respInternalName),
                                            "finish",
                                            MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)V"));
                                    mv.return_();
                                    maxLocals++;
                                } else if (returnType == char.class) {
                                    mv.istore(maxLocals);
                                    mv.aload(2); // response
                                    mv.iload(maxLocals);
                                    mv.invokestatic(
                                            ByteCodes.classDesc("java/lang/String"),
                                            "valueOf",
                                            MethodTypeDesc.ofDescriptor("(C)Ljava/lang/String;"),
                                            false);
                                    mv.invokevirtual(
                                            ByteCodes.classDesc(respInternalName),
                                            "finish",
                                            MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)V"));
                                    mv.return_();
                                    maxLocals++;
                                } else if (returnType == int.class) {
                                    mv.istore(maxLocals);
                                    mv.aload(2); // response
                                    mv.iload(maxLocals);
                                    mv.invokestatic(
                                            ByteCodes.classDesc("java/lang/String"),
                                            "valueOf",
                                            MethodTypeDesc.ofDescriptor("(I)Ljava/lang/String;"),
                                            false);
                                    mv.invokevirtual(
                                            ByteCodes.classDesc(respInternalName),
                                            "finish",
                                            MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)V"));
                                    mv.return_();
                                    maxLocals++;
                                } else if (returnType == float.class) {
                                    mv.fstore(maxLocals);
                                    mv.aload(2); // response
                                    mv.fload(maxLocals);
                                    mv.invokestatic(
                                            ByteCodes.classDesc("java/lang/String"),
                                            "valueOf",
                                            MethodTypeDesc.ofDescriptor("(F)Ljava/lang/String;"),
                                            false);
                                    mv.invokevirtual(
                                            ByteCodes.classDesc(respInternalName),
                                            "finish",
                                            MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)V"));
                                    mv.return_();
                                    maxLocals++;
                                } else if (returnType == long.class) {
                                    mv.lstore(maxLocals);
                                    mv.aload(2); // response
                                    mv.lload(maxLocals);
                                    mv.invokestatic(
                                            ByteCodes.classDesc("java/lang/String"),
                                            "valueOf",
                                            MethodTypeDesc.ofDescriptor("(J)Ljava/lang/String;"),
                                            false);
                                    mv.invokevirtual(
                                            ByteCodes.classDesc(respInternalName),
                                            "finish",
                                            MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)V"));
                                    mv.return_();
                                    maxLocals += 2;
                                } else if (returnType == double.class) {
                                    mv.dstore(maxLocals);
                                    mv.aload(2); // response
                                    mv.dload(maxLocals);
                                    mv.invokestatic(
                                            ByteCodes.classDesc("java/lang/String"),
                                            "valueOf",
                                            MethodTypeDesc.ofDescriptor("(D)Ljava/lang/String;"),
                                            false);
                                    mv.invokevirtual(
                                            ByteCodes.classDesc(respInternalName),
                                            "finish",
                                            MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)V"));
                                    mv.return_();
                                    maxLocals += 2;
                                } else if (returnType == byte[].class) {
                                    mv.astore(maxLocals);
                                    mv.aload(2); // response
                                    mv.aload(maxLocals);
                                    mv.invokevirtual(
                                            ByteCodes.classDesc(respInternalName),
                                            "finish",
                                            MethodTypeDesc.ofDescriptor("([B)V"));
                                    mv.return_();
                                    maxLocals++;
                                } else if (returnType == String.class) {
                                    mv.astore(maxLocals);
                                    mv.aload(2); // response
                                    mv.aload(maxLocals);
                                    mv.invokevirtual(
                                            ByteCodes.classDesc(respInternalName),
                                            "finish",
                                            MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)V"));
                                    mv.return_();
                                    maxLocals++;
                                } else if (returnType == File.class) {
                                    mv.astore(maxLocals);
                                    mv.aload(2); // response
                                    mv.aload(maxLocals);
                                    mv.invokevirtual(
                                            ByteCodes.classDesc(respInternalName),
                                            "finish",
                                            MethodTypeDesc.ofDescriptor("(Ljava/io/File;)V"));
                                    mv.return_();
                                    maxLocals++;
                                } else if (Number.class.isAssignableFrom(returnType)
                                        || CharSequence.class.isAssignableFrom(
                                                returnType)) { // returnType == String.class 必须放在前面
                                    mv.astore(maxLocals);
                                    mv.aload(2); // response
                                    mv.aload(maxLocals);
                                    mv.invokestatic(
                                            ByteCodes.classDesc("java/lang/String"),
                                            "valueOf",
                                            MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;)Ljava/lang/String;"),
                                            false);
                                    mv.invokevirtual(
                                            ByteCodes.classDesc(respInternalName),
                                            "finish",
                                            MethodTypeDesc.ofDescriptor("(Ljava/lang/String;)V"));
                                    mv.return_();
                                    maxLocals++;
                                } else if (RetResult.class.isAssignableFrom(returnType)) {
                                    mv.astore(maxLocals);
                                    mv.aload(2); // response
                                    if (hasResConvert) {
                                        mv.aload(0);
                                        mv.getfield(
                                                ByteCodes.classDesc(newDynName),
                                                REST_CONVERT_FIELD_PREFIX + restConverts.size(),
                                                ClassDesc.ofDescriptor(convertDesc));
                                        mv.aload(0);
                                        mv.getfield(
                                                ByteCodes.classDesc(newDynName),
                                                REST_RETURNTYPES_FIELD_NAME,
                                                ClassDesc.ofDescriptor("[Ljava/lang/reflect/Type;"));
                                        mv.loadConstant(entry.methodIdx); // 方法下标
                                        mv.aaload();
                                        mv.aload(maxLocals);
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(respInternalName),
                                                "finish",
                                                MethodTypeDesc.ofDescriptor(
                                                        "(" + convertDesc + typeDesc + retDesc + ")V"));
                                    } else {
                                        mv.aload(0);
                                        mv.getfield(
                                                ByteCodes.classDesc(newDynName),
                                                REST_RETURNTYPES_FIELD_NAME,
                                                ClassDesc.ofDescriptor("[Ljava/lang/reflect/Type;"));
                                        mv.loadConstant(entry.methodIdx); // 方法下标
                                        mv.aaload();
                                        mv.aload(maxLocals);
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(respInternalName),
                                                "finish",
                                                MethodTypeDesc.ofDescriptor("(" + typeDesc + retDesc + ")V"));
                                    }
                                    mv.return_();
                                    maxLocals++;
                                } else if (HttpResult.class.isAssignableFrom(returnType)) {
                                    mv.astore(maxLocals);
                                    mv.aload(2); // response
                                    if (hasResConvert) {
                                        mv.aload(0);
                                        mv.getfield(
                                                ByteCodes.classDesc(newDynName),
                                                REST_CONVERT_FIELD_PREFIX + restConverts.size(),
                                                ClassDesc.ofDescriptor(convertDesc));
                                        mv.aload(0);
                                        mv.getfield(
                                                ByteCodes.classDesc(newDynName),
                                                REST_RETURNTYPES_FIELD_NAME,
                                                ClassDesc.ofDescriptor("[Ljava/lang/reflect/Type;"));
                                        mv.loadConstant(entry.methodIdx); // 方法下标
                                        mv.aaload();
                                        mv.aload(maxLocals);
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(respInternalName),
                                                "finish",
                                                MethodTypeDesc.ofDescriptor(
                                                        "(" + convertDesc + typeDesc + httpResultDesc + ")V"));
                                    } else {
                                        mv.aload(0);
                                        mv.getfield(
                                                ByteCodes.classDesc(newDynName),
                                                REST_RETURNTYPES_FIELD_NAME,
                                                ClassDesc.ofDescriptor("[Ljava/lang/reflect/Type;"));
                                        mv.loadConstant(entry.methodIdx); // 方法下标
                                        mv.aaload();
                                        mv.aload(maxLocals);
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(respInternalName),
                                                "finish",
                                                MethodTypeDesc.ofDescriptor("(" + typeDesc + httpResultDesc + ")V"));
                                    }
                                    mv.return_();
                                    maxLocals++;
                                } else if (HttpScope.class.isAssignableFrom(returnType)) {
                                    mv.astore(maxLocals);
                                    mv.aload(2); // response
                                    if (hasResConvert) {
                                        mv.aload(0);
                                        mv.getfield(
                                                ByteCodes.classDesc(newDynName),
                                                REST_CONVERT_FIELD_PREFIX + restConverts.size(),
                                                ClassDesc.ofDescriptor(convertDesc));
                                        mv.aload(maxLocals);
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(respInternalName),
                                                "finish",
                                                MethodTypeDesc.ofDescriptor("(" + convertDesc + httpScopeDesc + ")V"));
                                    } else {
                                        mv.aload(maxLocals);
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(respInternalName),
                                                "finish",
                                                MethodTypeDesc.ofDescriptor("(" + httpScopeDesc + ")V"));
                                    }
                                    mv.return_();
                                    maxLocals++;
                                } else if (CompletionStage.class.isAssignableFrom(returnType)) {
                                    mv.astore(maxLocals);
                                    mv.aload(2); // response
                                    Class returnNoFutureType =
                                            TypeToken.typeToClassOrElse(returnGenericNoFutureType, Object.class);
                                    if (returnNoFutureType == HttpScope.class) {
                                        if (hasResConvert) {
                                            mv.aload(0);
                                            mv.getfield(
                                                    ByteCodes.classDesc(newDynName),
                                                    REST_CONVERT_FIELD_PREFIX + restConverts.size(),
                                                    ClassDesc.ofDescriptor(convertDesc));
                                            mv.aload(maxLocals);
                                            mv.invokevirtual(
                                                    ByteCodes.classDesc(respInternalName),
                                                    "finishScopeFuture",
                                                    MethodTypeDesc.ofDescriptor("(" + convertDesc + stageDesc + ")V"));
                                        } else {
                                            mv.aload(maxLocals);
                                            mv.invokevirtual(
                                                    ByteCodes.classDesc(respInternalName),
                                                    "finishScopeFuture",
                                                    MethodTypeDesc.ofDescriptor("(" + stageDesc + ")V"));
                                        }
                                    } else if (returnNoFutureType != byte[].class
                                            && returnNoFutureType != RetResult.class
                                            && returnNoFutureType != HttpResult.class
                                            && returnNoFutureType != File.class
                                            && !((returnGenericNoFutureType instanceof Class)
                                                    && (((Class) returnGenericNoFutureType).isPrimitive()
                                                            || CharSequence.class.isAssignableFrom(
                                                                    (Class) returnGenericNoFutureType)))) {
                                        if (hasResConvert) {
                                            mv.aload(0);
                                            mv.getfield(
                                                    ByteCodes.classDesc(newDynName),
                                                    REST_CONVERT_FIELD_PREFIX + restConverts.size(),
                                                    ClassDesc.ofDescriptor(convertDesc));
                                            mv.aload(0);
                                            mv.getfield(
                                                    ByteCodes.classDesc(newDynName),
                                                    REST_RETURNTYPES_FIELD_NAME,
                                                    ClassDesc.ofDescriptor("[Ljava/lang/reflect/Type;"));
                                            mv.loadConstant(entry.methodIdx); // 方法下标
                                            mv.aaload();
                                            mv.aload(maxLocals);
                                            mv.invokevirtual(
                                                    ByteCodes.classDesc(respInternalName),
                                                    "finishJsonFuture",
                                                    MethodTypeDesc.ofDescriptor(
                                                            "(" + convertDesc + typeDesc + stageDesc + ")V"));
                                        } else {
                                            mv.aload(0);
                                            mv.getfield(
                                                    ByteCodes.classDesc(newDynName),
                                                    REST_RETURNTYPES_FIELD_NAME,
                                                    ClassDesc.ofDescriptor("[Ljava/lang/reflect/Type;"));
                                            mv.loadConstant(entry.methodIdx); // 方法下标
                                            mv.aaload();
                                            mv.aload(maxLocals);
                                            mv.invokevirtual(
                                                    ByteCodes.classDesc(respInternalName),
                                                    "finishJsonFuture",
                                                    MethodTypeDesc.ofDescriptor("(" + typeDesc + stageDesc + ")V"));
                                        }
                                    } else {
                                        if (hasResConvert) {
                                            mv.aload(0);
                                            mv.getfield(
                                                    ByteCodes.classDesc(newDynName),
                                                    REST_CONVERT_FIELD_PREFIX + restConverts.size(),
                                                    ClassDesc.ofDescriptor(convertDesc));
                                            mv.aload(0);
                                            mv.getfield(
                                                    ByteCodes.classDesc(newDynName),
                                                    REST_RETURNTYPES_FIELD_NAME,
                                                    ClassDesc.ofDescriptor("[Ljava/lang/reflect/Type;"));
                                            mv.loadConstant(entry.methodIdx); // 方法下标
                                            mv.aaload();
                                            mv.aload(maxLocals);
                                            mv.invokevirtual(
                                                    ByteCodes.classDesc(respInternalName),
                                                    "finishFuture",
                                                    MethodTypeDesc.ofDescriptor(
                                                            "(" + convertDesc + typeDesc + stageDesc + ")V"));
                                        } else {
                                            mv.aload(0);
                                            mv.getfield(
                                                    ByteCodes.classDesc(newDynName),
                                                    REST_RETURNTYPES_FIELD_NAME,
                                                    ClassDesc.ofDescriptor("[Ljava/lang/reflect/Type;"));
                                            mv.loadConstant(entry.methodIdx); // 方法下标
                                            mv.aaload();
                                            mv.aload(maxLocals);
                                            mv.invokevirtual(
                                                    ByteCodes.classDesc(respInternalName),
                                                    "finishFuture",
                                                    MethodTypeDesc.ofDescriptor("(" + typeDesc + stageDesc + ")V"));
                                        }
                                    }
                                    mv.return_();
                                    maxLocals++;
                                } else if (Flows.maybePublisherClass(returnType)) { // Flow.Publisher
                                    mv.astore(maxLocals);
                                    mv.aload(2); // response
                                    if (hasResConvert) {
                                        mv.aload(0);
                                        mv.getfield(
                                                ByteCodes.classDesc(newDynName),
                                                REST_CONVERT_FIELD_PREFIX + restConverts.size(),
                                                ClassDesc.ofDescriptor(convertDesc));
                                        mv.aload(0);
                                        mv.getfield(
                                                ByteCodes.classDesc(newDynName),
                                                REST_RETURNTYPES_FIELD_NAME,
                                                ClassDesc.ofDescriptor("[Ljava/lang/reflect/Type;"));
                                        mv.loadConstant(entry.methodIdx); // 方法下标
                                        mv.aaload();
                                        mv.aload(maxLocals);
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(respInternalName),
                                                "finishPublisher",
                                                MethodTypeDesc.ofDescriptor(
                                                        "(" + convertDesc + typeDesc + "Ljava/lang/Object;)V"));
                                    } else {
                                        mv.aload(0);
                                        mv.getfield(
                                                ByteCodes.classDesc(newDynName),
                                                REST_RETURNTYPES_FIELD_NAME,
                                                ClassDesc.ofDescriptor("[Ljava/lang/reflect/Type;"));
                                        mv.loadConstant(entry.methodIdx); // 方法下标
                                        mv.aaload();
                                        mv.aload(maxLocals);
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(respInternalName),
                                                "finishPublisher",
                                                MethodTypeDesc.ofDescriptor("(" + typeDesc + "Ljava/lang/Object;)V"));
                                    }
                                    mv.return_();
                                    maxLocals++;
                                } else if (returnType == retvalType) { // 普通JavaBean或JavaBean[]
                                    mv.astore(maxLocals);
                                    mv.aload(2); // response
                                    if (hasResConvert) {
                                        mv.aload(0);
                                        mv.getfield(
                                                ByteCodes.classDesc(newDynName),
                                                REST_CONVERT_FIELD_PREFIX + restConverts.size(),
                                                ClassDesc.ofDescriptor(convertDesc));
                                        mv.aload(0);
                                        mv.getfield(
                                                ByteCodes.classDesc(newDynName),
                                                REST_RETURNTYPES_FIELD_NAME,
                                                ClassDesc.ofDescriptor("[Ljava/lang/reflect/Type;"));
                                        mv.loadConstant(entry.methodIdx); // 方法下标
                                        mv.aaload();
                                        mv.aload(maxLocals);
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(respInternalName),
                                                "finishJson",
                                                MethodTypeDesc.ofDescriptor(
                                                        "(" + convertDesc + typeDesc + "Ljava/lang/Object;)V"));
                                    } else {
                                        mv.aload(0);
                                        mv.getfield(
                                                ByteCodes.classDesc(newDynName),
                                                REST_RETURNTYPES_FIELD_NAME,
                                                ClassDesc.ofDescriptor("[Ljava/lang/reflect/Type;"));
                                        mv.loadConstant(entry.methodIdx); // 方法下标
                                        mv.aaload();
                                        mv.aload(maxLocals);
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(respInternalName),
                                                "finishJson",
                                                MethodTypeDesc.ofDescriptor("(" + typeDesc + "Ljava/lang/Object;)V"));
                                    }
                                    mv.return_();
                                    maxLocals++;
                                } else {
                                    mv.astore(maxLocals);
                                    mv.aload(2); // response
                                    if (hasResConvert) {
                                        mv.aload(0);
                                        mv.getfield(
                                                ByteCodes.classDesc(newDynName),
                                                REST_CONVERT_FIELD_PREFIX + restConverts.size(),
                                                ClassDesc.ofDescriptor(convertDesc));
                                        mv.aload(0);
                                        mv.getfield(
                                                ByteCodes.classDesc(newDynName),
                                                REST_RETURNTYPES_FIELD_NAME,
                                                ClassDesc.ofDescriptor("[Ljava/lang/reflect/Type;"));
                                        mv.loadConstant(entry.methodIdx); // 方法下标
                                        mv.aaload();
                                        mv.aload(maxLocals);
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(respInternalName),
                                                "finish",
                                                MethodTypeDesc.ofDescriptor(
                                                        "(" + convertDesc + typeDesc + "Ljava/lang/Object;)V"));
                                    } else {
                                        mv.aload(0);
                                        mv.getfield(
                                                ByteCodes.classDesc(newDynName),
                                                REST_RETURNTYPES_FIELD_NAME,
                                                ClassDesc.ofDescriptor("[Ljava/lang/reflect/Type;"));
                                        mv.loadConstant(entry.methodIdx); // 方法下标
                                        mv.aaload();
                                        mv.aload(maxLocals);
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(respInternalName),
                                                "finish",
                                                MethodTypeDesc.ofDescriptor("(" + typeDesc + "Ljava/lang/Object;)V"));
                                    }
                                    mv.return_();
                                    maxLocals++;
                                }
                                Label label2 = mv.newLabel();
                                mv.labelBinding(label2);
                                mv.localVariable(
                                        0, "this", ClassDesc.ofDescriptor("L" + newDynName + ";"), label0, label2);
                                mv.localVariable(1, "req", ClassDesc.ofDescriptor(reqDesc), label0, label2);
                                mv.localVariable(2, "resp", ClassDesc.ofDescriptor(respDesc), label0, label2);

                                mappingMap.put("params", paramMaps);
                            });
                            if (!mvAnnotations.isEmpty()) mb.with(RuntimeVisibleAnnotationsAttribute.of(mvAnnotations));
                        });

                { // _Dync_XXX__HttpServlet.class
                    byte[] innerBytes = ClassFile.of()
                            .build(ByteCodes.classDesc(newDynName + "$" + entry.newActionClassName), cw2 -> {
                                cw2.withVersion(JAVA_11_VERSION, 0)
                                        .withFlags(ACC_SUPER)
                                        .withSuperclass(ByteCodes.classDesc(httpServletName));
                                List<java.lang.classfile.Annotation> cw2Annotations = new ArrayList<>();
                                List<InnerClassInfo> cw2InnerClasses = new ArrayList<>();

                                cw2InnerClasses.add(InnerClassInfo.of(
                                        ByteCodes.classDesc(newDynName + "$" + entry.newActionClassName),
                                        Optional.ofNullable(newDynName).map(ByteCodes::classDesc),
                                        Optional.ofNullable(entry.newActionClassName),
                                        ACC_PRIVATE + ACC_STATIC));
                                { // 设置 Annotation NonBlocking
                                    {
                                        List<AnnotationElement> av0 = new ArrayList<>();
                                        av0.add(AnnotationElement.of(
                                                "value", ByteCodes.annotationValue(entry.nonBlocking)));
                                        cw2Annotations.add(java.lang.classfile.Annotation.of(
                                                ClassDesc.ofDescriptor(nonblockDesc), av0));
                                    }
                                }
                                {
                                    cw2.withField("_parentServlet", ClassDesc.ofDescriptor("L" + newDynName + ";"), 0);
                                }
                                {
                                    cw2.withMethodBody(
                                            "<init>", MethodTypeDesc.ofDescriptor("(L" + newDynName + ";)V"), 0, mv -> {
                                                Label sublabel0 = mv.newLabel();
                                                mv.labelBinding(sublabel0);
                                                mv.aload(0);
                                                mv.invokespecial(
                                                        ByteCodes.classDesc(httpServletName),
                                                        "<init>",
                                                        MethodTypeDesc.ofDescriptor("()V"),
                                                        false);
                                                mv.aload(0);
                                                mv.aload(1);
                                                mv.putfield(
                                                        ByteCodes.classDesc(
                                                                newDynName + "$" + entry.newActionClassName),
                                                        "_parentServlet",
                                                        ClassDesc.ofDescriptor("L" + newDynName + ";"));
                                                mv.aload(0);
                                                mv.loadConstant(entry.nonBlocking ? 1 : 0);
                                                mv.putfield(
                                                        ByteCodes.classDesc(
                                                                newDynName + "$" + entry.newActionClassName),
                                                        "_nonBlocking",
                                                        ClassDesc.ofDescriptor("Z"));
                                                mv.return_();
                                                Label sublabel2 = mv.newLabel();
                                                mv.labelBinding(sublabel2);
                                                mv.localVariable(
                                                        0,
                                                        "this",
                                                        ClassDesc.ofDescriptor("L" + newDynName + ";"),
                                                        sublabel0,
                                                        sublabel2);
                                                mv.localVariable(
                                                        1,
                                                        "parentServlet",
                                                        ClassDesc.ofDescriptor("L" + newDynName + "$"
                                                                + entry.newActionClassName + ";"),
                                                        sublabel0,
                                                        sublabel2);
                                            });
                                }
                                //                if (false) {

                                // newDynName + ";L" + newDynName + "$" + entry.newActionClassName + ";)V", null,
                                // null));

                                // "<init>", "L" + newDynName + ";", false);

                                //                }
                                {
                                    cw2.withMethod(
                                            "execute",
                                            MethodTypeDesc.ofDescriptor("(" + reqDesc + respDesc + ")V"),
                                            ACC_PUBLIC,
                                            mb -> {
                                                mb.with(ExceptionsAttribute.ofSymbols(
                                                        Arrays.stream(new String[] {"java/io/IOException"})
                                                                .map(ByteCodes::classDesc)
                                                                .toList()));

                                                mb.withCode(mv -> {
                                                    Label sublabel0 = mv.newLabel();
                                                    mv.labelBinding(sublabel0);
                                                    mv.aload(0);
                                                    mv.getfield(
                                                            ByteCodes.classDesc(
                                                                    newDynName + "$" + entry.newActionClassName),
                                                            "_parentServlet",
                                                            ClassDesc.ofDescriptor("L" + newDynName + ";"));
                                                    mv.aload(1);
                                                    mv.aload(2);
                                                    mv.invokevirtual(
                                                            ByteCodes.classDesc(newDynName),
                                                            entry.newMethodName,
                                                            MethodTypeDesc.ofDescriptor(
                                                                    "(" + reqDesc + respDesc + ")V"));
                                                    mv.return_();
                                                    Label sublabel2 = mv.newLabel();
                                                    mv.labelBinding(sublabel2);
                                                    mv.localVariable(
                                                            0,
                                                            "this",
                                                            ClassDesc.ofDescriptor("L" + newDynName + ";"),
                                                            sublabel0,
                                                            sublabel2);
                                                    mv.localVariable(
                                                            1,
                                                            "req",
                                                            ClassDesc.ofDescriptor(reqDesc),
                                                            sublabel0,
                                                            sublabel2);
                                                    mv.localVariable(
                                                            2,
                                                            "resp",
                                                            ClassDesc.ofDescriptor(respDesc),
                                                            sublabel0,
                                                            sublabel2);
                                                });
                                            });
                                }
                                if (!cw2Annotations.isEmpty())
                                    cw2.with(RuntimeVisibleAnnotationsAttribute.of(cw2Annotations));
                                if (!cw2InnerClasses.isEmpty()) cw2.with(InnerClassesAttribute.of(cw2InnerClasses));
                            });
                    byte[] bytes = innerBytes;
                    innerClassBytesMap.put((newDynName + "$" + entry.newActionClassName).replace('/', '.'), bytes);
                }
            } // end  for each

            if (containsMupload[0]) { // 注入 @Resource(name = "APP_HOME")  private File _redkale_home;
                cw.withField("_redkale_home", ClassDesc.ofDescriptor(ByteCodes.descriptor(File.class)), fv -> {
                    fv.withFlags(ACC_PRIVATE);
                    List<java.lang.classfile.Annotation> fvAnnotations = new ArrayList<>();

                    {
                        List<AnnotationElement> av0 = new ArrayList<>();
                        av0.add(AnnotationElement.of("name", ByteCodes.annotationValue("APP_HOME")));
                        fvAnnotations.add(java.lang.classfile.Annotation.of(ClassDesc.ofDescriptor(resDesc), av0));
                    }

                    if (!fvAnnotations.isEmpty()) fv.with(RuntimeVisibleAnnotationsAttribute.of(fvAnnotations));
                });
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
                cw.withMethod("_createRestActionEntry", MethodTypeDesc.ofDescriptor("()Ljava/util/HashMap;"), 0, mb -> {
                    mb.with(SignatureAttribute.of(MethodSignature.parseFrom(
                            "()Ljava/util/HashMap<Ljava/lang/String;L" + actionEntryName + ";>;")));

                    mb.withCode(mv -> {
                        mv.new_(ByteCodes.classDesc("java/util/HashMap"));
                        mv.dup();
                        mv.invokespecial(
                                ByteCodes.classDesc("java/util/HashMap"),
                                "<init>",
                                MethodTypeDesc.ofDescriptor("()V"),
                                false);
                        mv.astore(1);

                        for (final MappingEntry entry : entrys) {
                            mappingurlToMethod.put(entry.mappingurl, entry.mappingMethod);
                            mv.aload(1);
                            mv.loadConstant(entry.mappingurl); // name
                            mv.new_(ByteCodes.classDesc(actionEntryName)); // new ActionEntry
                            mv.dup();
                            mv.loadConstant(moduleid); // moduleid
                            mv.loadConstant(entry.actionid); // actionid
                            mv.loadConstant(entry.mappingurl); // name
                            mv.loadConstant(entry.methods.length); // methods
                            mv.anewarray(ByteCodes.classDesc("java/lang/String"));
                            for (int i = 0; i < entry.methods.length; i++) {
                                mv.dup();
                                mv.loadConstant(i);
                                mv.loadConstant(entry.methods[i]);
                                mv.aastore();
                            }
                            mv.aconst_null(); // method
                            mv.loadConstant(entry.rpcOnly ? 1 : 0); // rpcOnly
                            mv.loadConstant(entry.auth ? 1 : 0); // auth
                            mv.loadConstant(entry.cacheSeconds); // cacheSeconds
                            mv.new_(ByteCodes.classDesc(newDynName + "$" + entry.newActionClassName));
                            mv.dup();
                            mv.aload(0);
                            mv.invokespecial(
                                    ByteCodes.classDesc(newDynName + "$" + entry.newActionClassName),
                                    "<init>",
                                    MethodTypeDesc.ofDescriptor("(L" + newDynName + ";)V"),
                                    false);
                            mv.invokespecial(
                                    ByteCodes.classDesc(actionEntryName),
                                    "<init>",
                                    MethodTypeDesc.ofDescriptor(
                                            "(IILjava/lang/String;[Ljava/lang/String;Ljava/lang/reflect/Method;ZZI"
                                                    + httpDesc + ")V"),
                                    false);
                            mv.invokevirtual(
                                    ByteCodes.classDesc("java/util/HashMap"),
                                    "put",
                                    MethodTypeDesc.ofDescriptor(
                                            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"));
                            mv.pop();
                        }
                        mv.aload(1);
                        mv.areturn();
                    });
                });
            }

            for (Map.Entry<String, java.lang.reflect.Type> en : bodyTypes.entrySet()) {
                cw.withField(en.getKey(), ClassDesc.ofDescriptor("Ljava/lang/reflect/Type;"), fv -> {
                    fv.withFlags(ACC_PRIVATE);
                    List<java.lang.classfile.Annotation> fvAnnotations = new ArrayList<>();

                    {
                        List<AnnotationElement> av0 = new ArrayList<>();
                        av0.add(AnnotationElement.of(
                                "value", ByteCodes.annotationValue(en.getValue().toString())));
                        fvAnnotations.add(java.lang.classfile.Annotation.of(
                                ClassDesc.ofDescriptor(ByteCodes.descriptor(Comment.class)), av0));
                    }

                    if (!fvAnnotations.isEmpty()) fv.with(RuntimeVisibleAnnotationsAttribute.of(fvAnnotations));
                });
            }

            for (Map.Entry<java.lang.reflect.Type, String> en : typeRefs.entrySet()) {
                cw.withField(en.getValue(), ClassDesc.ofDescriptor("Ljava/lang/reflect/Type;"), fv -> {
                    fv.withFlags(ACC_PRIVATE);
                    List<java.lang.classfile.Annotation> fvAnnotations = new ArrayList<>();

                    {
                        List<AnnotationElement> av0 = new ArrayList<>();
                        av0.add(AnnotationElement.of(
                                "value", ByteCodes.annotationValue(en.getKey().toString())));
                        fvAnnotations.add(java.lang.classfile.Annotation.of(
                                ClassDesc.ofDescriptor(ByteCodes.descriptor(Comment.class)), av0));
                    }

                    if (!fvAnnotations.isEmpty()) fv.with(RuntimeVisibleAnnotationsAttribute.of(fvAnnotations));
                });
            }

            for (Map.Entry<String, org.redkale.util.Attribute> en : restAttributes.entrySet()) {
                cw.withField(en.getKey(), ClassDesc.ofDescriptor(attrDesc), fv -> {
                    fv.withFlags(ACC_PRIVATE);
                    List<java.lang.classfile.Annotation> fvAnnotations = new ArrayList<>();

                    {
                        List<AnnotationElement> av0 = new ArrayList<>();
                        av0.add(AnnotationElement.of(
                                "value", ByteCodes.annotationValue(en.getValue().toString())));
                        fvAnnotations.add(java.lang.classfile.Annotation.of(
                                ClassDesc.ofDescriptor(ByteCodes.descriptor(Comment.class)), av0));
                    }

                    if (!fvAnnotations.isEmpty()) fv.with(RuntimeVisibleAnnotationsAttribute.of(fvAnnotations));
                });
            }

            for (int i = 1; i <= restConverts.size(); i++) {
                cw.withField(REST_CONVERT_FIELD_PREFIX + i, ClassDesc.ofDescriptor(convertDesc), ACC_PRIVATE);
            }

            { // _methodAnns字段 Annotation[][]
                cw.withField(
                        REST_METHOD_ANNS_NAME, ClassDesc.ofDescriptor("[[Ljava/lang/annotation/Annotation;"), fv -> {
                            fv.withFlags(ACC_PRIVATE);
                            List<java.lang.classfile.Annotation> fvAnnotations = new ArrayList<>();

                            {
                                List<AnnotationElement> av0 = new ArrayList<>();
                                StringBuilder sb = new StringBuilder().append('[');
                                for (Annotation[] rs : methodAnns) {
                                    sb.append(Arrays.toString(rs)).append(',');
                                }
                                av0.add(AnnotationElement.of(
                                        "value",
                                        ByteCodes.annotationValue(sb.append(']').toString())));
                                fvAnnotations.add(java.lang.classfile.Annotation.of(
                                        ClassDesc.ofDescriptor(ByteCodes.descriptor(Comment.class)), av0));
                            }

                            if (!fvAnnotations.isEmpty()) fv.with(RuntimeVisibleAnnotationsAttribute.of(fvAnnotations));
                        });
            }
            { // _paramtypes字段 java.lang.reflect.Type[][]
                cw.withField(REST_PARAMTYPES_FIELD_NAME, ClassDesc.ofDescriptor("[[Ljava/lang/reflect/Type;"), fv -> {
                    fv.withFlags(ACC_PRIVATE);
                    List<java.lang.classfile.Annotation> fvAnnotations = new ArrayList<>();

                    {
                        List<AnnotationElement> av0 = new ArrayList<>();
                        StringBuilder sb = new StringBuilder().append('[');
                        for (java.lang.reflect.Type[] rs : paramTypes) {
                            sb.append(Arrays.toString(rs)).append(',');
                        }
                        av0.add(AnnotationElement.of(
                                "value",
                                ByteCodes.annotationValue(sb.append(']').toString())));
                        fvAnnotations.add(java.lang.classfile.Annotation.of(
                                ClassDesc.ofDescriptor(ByteCodes.descriptor(Comment.class)), av0));
                    }

                    if (!fvAnnotations.isEmpty()) fv.with(RuntimeVisibleAnnotationsAttribute.of(fvAnnotations));
                });
            }
            { // _returntypes字段 java.lang.reflect.Type[]
                cw.withField(REST_RETURNTYPES_FIELD_NAME, ClassDesc.ofDescriptor("[Ljava/lang/reflect/Type;"), fv -> {
                    fv.withFlags(ACC_PRIVATE);
                    List<java.lang.classfile.Annotation> fvAnnotations = new ArrayList<>();

                    {
                        List<AnnotationElement> av0 = new ArrayList<>();
                        av0.add(AnnotationElement.of("value", ByteCodes.annotationValue(retvalTypes.toString())));
                        fvAnnotations.add(java.lang.classfile.Annotation.of(
                                ClassDesc.ofDescriptor(ByteCodes.descriptor(Comment.class)), av0));
                    }

                    if (!fvAnnotations.isEmpty()) fv.with(RuntimeVisibleAnnotationsAttribute.of(fvAnnotations));
                });
            }

            // classMap.put("mappings", mappingMaps); //不显示太多信息
            { // toString函数
                cw.withMethodBody("toString", MethodTypeDesc.ofDescriptor("()Ljava/lang/String;"), ACC_PUBLIC, mv -> {
                    mv.aload(0);
                    mv.getfield(
                            ByteCodes.classDesc(newDynName),
                            REST_TOSTRINGOBJ_FIELD_NAME,
                            ClassDesc.ofDescriptor("Ljava/util/function/Supplier;"));
                    mv.invokeinterface(
                            ByteCodes.classDesc("java/util/function/Supplier"),
                            "get",
                            MethodTypeDesc.ofDescriptor("()Ljava/lang/Object;"));
                    mv.checkcast(ByteCodes.classDesc("java/lang/String"));
                    mv.areturn();
                });
            }

            { // RestDyn
                {
                    List<AnnotationElement> av0 = new ArrayList<>();
                    av0.add(AnnotationElement.of("simple", ByteCodes.annotationValue((Boolean) dynsimple[0])));
                    cwAnnotations.add(java.lang.classfile.Annotation.of(
                            ClassDesc.ofDescriptor(ByteCodes.descriptor(RestDyn.class)), av0));
                }
            }

            if (!cwAnnotations.isEmpty()) cw.with(RuntimeVisibleAnnotationsAttribute.of(cwAnnotations));
            if (!cwInnerClasses.isEmpty()) cw.with(InnerClassesAttribute.of(cwInnerClasses));
        });
        byte[] bytes = classBytes;
        try {
            Class<?> newClazz = classLoader.loadClass(newDynName.replace('/', '.'), bytes, innerClassBytesMap);
            innerClassBytesMap.forEach((n, bs) -> RedkaleClassLoader.putReflectionClass(n));
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
