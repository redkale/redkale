/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import static java.lang.classfile.ClassFile.*;
import static java.lang.constant.ConstantDescs.*;

import java.io.*;
import java.lang.annotation.*;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.ExceptionsAttribute;
import java.lang.constant.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.logging.*;
import org.redkale.annotation.*;
import org.redkale.boot.*;
import org.redkale.net.*;
import org.redkale.util.*;

/**
 * HTTP版的Servlet， 执行顺序 execute --&#62; preExecute --&#62; authenticate --&#62; HttpMapping对应的方法
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class HttpServlet extends Servlet<HttpContext, HttpRequest, HttpResponse> {

    // 只给HttpActionServlet使用，_actionSimpleMappingUrl不能包含正则表达式，比如: /json、/createRecord,
    // 不能是/user/**
    String _actionSimpleMappingUrl;

    // 当前HttpServlet的path前缀
    String _prefix = "";

    // 根据RestService+MQ生成的值 @since 2.5.0
    String _reqtopic;

    // Rest生成时赋值, 字段名Rest有用到
    HashMap<String, ActionEntry> _actionmap;

    // 字段名Rest有用到
    private Map.Entry<String, ActionEntry>[] mappings;

    // 这里不能直接使用HttpServlet，会造成死循环初始化HttpServlet
    private final Servlet<HttpContext, HttpRequest, HttpResponse> authSuccessServlet =
            new Servlet<HttpContext, HttpRequest, HttpResponse>() {
                {
                    this._nonBlocking = true;
                }

                @Override
                public void execute(HttpRequest request, HttpResponse response) throws IOException {
                    ActionEntry entry = request.actionEntry;
                    if (entry.rpcOnly) {
                        if (!request.rpc) {
                            finish404(request, response);
                            return;
                        } else if (request.rpcAuthenticator != null) {
                            if (!request.rpcAuthenticator.auth(request, response)) {
                                return;
                            }
                        }
                    }
                    if (entry.cacheSeconds > 0) { // 有缓存设置
                        CacheEntry ce = entry.modeOneCache ? entry.oneCache : entry.cache.get(request.getRequestPath());
                        if (ce != null && ce.time + entry.cacheSeconds * 1000 > System.currentTimeMillis()) { // 缓存有效
                            response.setStatus(ce.status);
                            response.setContentType(ce.contentType);
                            response.skipHeader();
                            response.finish(ce.getBytes());
                            return;
                        }
                        response.cacheHandler(entry.cacheHandler);
                    }
                    if (response.inNonBlocking()) {
                        if (entry.nonBlocking) {
                            entry.servlet.execute(request, response);
                        } else {
                            response.updateNonBlocking(false);
                            response.getWorkExecutor().execute(() -> {
                                try {
                                    Traces.computeIfAbsent(request.getTraceid());
                                    entry.servlet.execute(request, response);
                                } catch (Throwable t) {
                                    response.getContext()
                                            .getLogger()
                                            .log(Level.WARNING, "Servlet occur exception. request = " + request, t);
                                    response.finishError(t);
                                }
                                Traces.removeTraceid();
                            });
                        }
                    } else {
                        entry.servlet.execute(request, response);
                    }
                }
            };

    // preExecute运行完后执行的Servlet
    private final Servlet<HttpContext, HttpRequest, HttpResponse> preSuccessServlet =
            new Servlet<HttpContext, HttpRequest, HttpResponse>() {
                {
                    this._nonBlocking = true;
                }

                @Override
                public void execute(HttpRequest request, HttpResponse response) throws IOException {
                    if (request.actionEntry != null) {
                        ActionEntry entry = request.actionEntry;
                        if (!entry.checkMethod(request.getMethod())) {
                            finish405(request, response);
                            return;
                        }
                        request.moduleid = entry.moduleid;
                        request.actionid = entry.actionid;
                        request.setAnnotations(entry.annotations);
                        if (entry.auth) {
                            response.thenEvent(authSuccessServlet);
                            authenticate(request, response);
                        } else {
                            authSuccessServlet.execute(request, response);
                        }
                        return;
                    }
                    for (Map.Entry<String, ActionEntry> en : mappings) {
                        if (request.getRequestPath().startsWith(en.getKey())) {
                            ActionEntry entry = en.getValue();
                            if (!entry.checkMethod(request.getMethod())) {
                                finish405(request, response);
                                return;
                            }
                            request.actionEntry = entry;
                            request.moduleid = entry.moduleid;
                            request.actionid = entry.actionid;
                            request.setAnnotations(entry.annotations);
                            if (entry.auth) {
                                response.thenEvent(authSuccessServlet);
                                authenticate(request, response);
                            } else {
                                authSuccessServlet.execute(request, response);
                            }
                            return;
                        }
                    }
                    finish404(request, response);
                }
            };

    @SuppressWarnings("unchecked")
    void preInit(Application application, HttpContext context, AnyValue config) {
        if (this.mappings != null) {
            return; // 无需重复preInit
        }
        String path = _prefix == null ? "" : _prefix;
        WebServlet ws = this.getClass().getAnnotation(WebServlet.class);
        if (ws != null && !ws.repair()) {
            path = "";
        }
        // 设置整个HttpServlet是否非阻塞式
        this._nonBlocking = isNonBlocking(getClass());
        // RestServlet会填充_actionmap
        HashMap<String, ActionEntry> map =
                this._actionmap != null ? this._actionmap : loadActionEntry(this._nonBlocking);
        this.mappings = new Map.Entry[map.size()];
        int i = -1;
        for (Map.Entry<String, ActionEntry> en : map.entrySet()) {
            mappings[++i] = new AbstractMap.SimpleEntry<>(path + en.getKey(), en.getValue());
        }
        // 必须要倒排序, /query /query1 /query12  确保含子集的优先匹配 /query12  /query1  /query
        Arrays.sort(mappings, (o1, o2) -> o2.getKey().compareTo(o1.getKey()));
    }

    void postDestroy(Application application, HttpContext context, AnyValue config) {
        // do nothing
    }

    // Server执行start后运行此方法
    public void postStart(HttpContext context, AnyValue config) {
        // do nothing
    }

    /**
     * 提供404状态码的可定制接口
     *
     * @since 2.8.0
     * @param request HttpRequest
     * @param response HttpResponse
     * @throws IOException IOException
     */
    protected void finish404(HttpRequest request, HttpResponse response) throws IOException {
        response.finish404();
    }

    /**
     * 提供405状态码的可定制接口
     *
     * @since 2.8.0
     * @param request HttpRequest
     * @param response HttpResponse
     * @throws IOException IOException
     */
    protected void finish405(HttpRequest request, HttpResponse response) throws IOException {
        response.finish405();
    }

    /**
     * 预执行方法，在execute方法之前运行，设置当前用户信息，或者加入常规统计和基础检测，例如 : <br>
     *
     * <blockquote>
     *
     * <pre>
     *      &#64;Override
     *      public void preExecute(final HttpRequest request, final HttpResponse response) throws IOException {
     *          //设置当前用户信息
     *          final String sessionid = request.getSessionid(false);
     *          if (sessionid != null) request.setCurrentUserid(userService.currentUserid(sessionid));
     *
     *          if (finer) response.recycleListener((req, resp) -&#62; {  //记录处理时间比较长的请求
     *              long e = System.currentTimeMillis() - ((HttpRequest) req).getCreateTime();
     *              if (e &#62; 200) logger.finer("http-execute-cost-time: " + e + " ms. request = " + req);
     *          });
     *          response.nextEvent();
     *      }
     * </pre>
     *
     * </blockquote>
     *
     * <p>
     *
     * @param request HttpRequest
     * @param response HttpResponse
     * @throws IOException IOException
     */
    @NonBlocking
    protected void preExecute(HttpRequest request, HttpResponse response) throws IOException {
        response.nextEvent();
    }

    /**
     * 用户登录或权限验证， 注解为&#64;HttpMapping.auth == true 的方法会执行authenticate方法, 若验证成功则必须调用response.nextEvent();进行下一步操作, 例如:
     * <br>
     *
     * <blockquote>
     *
     * <pre>
     *      &#64;Override
     *      public void authenticate(HttpRequest request, HttpResponse response) throws IOException {
     *          Serializable userid = request.currentUserid();
     *          if (userid == null) {
     *              response.finishJson(RET_UNLOGIN);
     *              return;
     *          }
     *          response.nextEvent();
     *      }
     * </pre>
     *
     * </blockquote>
     *
     * <p>
     *
     * @param request HttpRequest
     * @param response HttpResponse
     * @throws IOException IOException
     */
    @NonBlocking
    protected void authenticate(HttpRequest request, HttpResponse response) throws IOException {
        response.nextEvent();
    }

    @Override
    @NonBlocking
    public void execute(HttpRequest request, HttpResponse response) throws IOException {
        response.thenEvent(preSuccessServlet);
        preExecute(request, response);
    }

    static Boolean isNonBlocking(Class<? extends HttpServlet> servletClass) {
        Class clz = servletClass;
        Boolean preNonBlocking = null;
        Boolean authNonBlocking = null;
        Boolean exeNonBlocking = null;
        do {
            if (java.lang.reflect.Modifier.isAbstract(clz.getModifiers())) {
                break;
            }
            RedkaleClassLoader.putReflectionDeclaredMethods(clz.getName());
            for (final Method method : clz.getDeclaredMethods()) {
                String methodName = method.getName();
                // -----------------------------------------------
                Class[] paramTypes = method.getParameterTypes();
                if (paramTypes.length != 2
                        || paramTypes[0] != HttpRequest.class
                        || paramTypes[1] != HttpResponse.class) {
                    continue;
                }
                // -----------------------------------------------
                Class[] exps = method.getExceptionTypes();
                if (exps.length > 0 && (exps.length != 1 || exps[0] != IOException.class)) {
                    continue;
                }
                // -----------------------------------------------
                if ("preExecute".equals(methodName)) {
                    if (preNonBlocking == null) {
                        NonBlocking non = method.getAnnotation(NonBlocking.class);
                        preNonBlocking = non != null && non.value();
                    }
                    continue;
                }
                if ("authenticate".equals(methodName)) {
                    if (authNonBlocking == null) {
                        NonBlocking non = method.getAnnotation(NonBlocking.class);
                        authNonBlocking = non != null && non.value();
                    }
                    continue;
                }
                if ("execute".equals(methodName)) {
                    if (exeNonBlocking == null) {
                        NonBlocking non = method.getAnnotation(NonBlocking.class);
                        exeNonBlocking = non != null && non.value();
                    }
                }
            }
        } while ((clz = clz.getSuperclass()) != HttpServlet.class);

        NonBlocking non = servletClass.getAnnotation(NonBlocking.class);
        if (non == null) {
            return (preNonBlocking != null && preNonBlocking)
                    && (authNonBlocking != null && authNonBlocking)
                    && (exeNonBlocking != null && exeNonBlocking);
        } else {
            return non.value()
                    && (preNonBlocking == null || preNonBlocking)
                    && (authNonBlocking == null || authNonBlocking)
                    && (exeNonBlocking == null || exeNonBlocking);
        }
    }

    private HashMap<String, ActionEntry> loadActionEntry(boolean typeNonBlocking) {
        WebServlet module = this.getClass().getAnnotation(WebServlet.class);
        final int serviceid = module == null ? 0 : module.moduleid();
        final HashMap<String, ActionEntry> map = new HashMap<>();
        HashMap<String, Class> nameset = new HashMap<>();
        final Class selfClz = this.getClass();
        Class clz = this.getClass();
        do {
            if (java.lang.reflect.Modifier.isAbstract(clz.getModifiers())) {
                break;
            }
            RedkaleClassLoader.putReflectionDeclaredMethods(clz.getName());
            for (final Method method : clz.getDeclaredMethods()) {
                String methodName = method.getName();
                // -----------------------------------------------
                Class[] paramTypes = method.getParameterTypes();
                if (paramTypes.length != 2
                        || paramTypes[0] != HttpRequest.class
                        || paramTypes[1] != HttpResponse.class) {
                    continue;
                }
                // -----------------------------------------------
                Class[] exps = method.getExceptionTypes();
                if (exps.length > 0 && (exps.length != 1 || exps[0] != IOException.class)) {
                    continue;
                }
                // -----------------------------------------------
                if ("preExecute".equals(methodName)
                        || "authenticate".equals(methodName)
                        || "execute".equals(methodName)
                        || "service".equals(methodName)) {
                    continue;
                }
                if (!Modifier.isPublic(method.getModifiers())) {
                    continue;
                }
                // -----------------------------------------------

                final HttpMapping mapping = method.getAnnotation(HttpMapping.class);
                if (mapping == null) {
                    continue;
                }
                final boolean inherited = mapping.inherited();
                if (!inherited && selfClz != clz) {
                    continue; // 忽略不被继承的方法
                }
                final int actionid = mapping.actionid();
                final String name = mapping.url().trim();
                final String[] methods = mapping.methods();
                if (nameset.containsKey(name)) {
                    if (nameset.get(name) != clz) {
                        continue;
                    }
                    throw new HttpException(this.getClass().getSimpleName() + " have two same "
                            + HttpMapping.class.getSimpleName() + "(" + name + ")");
                }
                nameset.put(name, clz);
                map.put(
                        name,
                        new ActionEntry(
                                serviceid,
                                actionid,
                                name,
                                methods,
                                method,
                                createActionServlet(typeNonBlocking, method)));
            }
        } while ((clz = clz.getSuperclass()) != HttpServlet.class);
        return map;
    }

    protected static final class ActionEntry {

        ActionEntry(int moduleid, int actionid, String name, String[] methods, Method method, HttpServlet servlet) {
            this(
                    moduleid,
                    actionid,
                    name,
                    methods,
                    method,
                    rpcOnly(method),
                    auth(method),
                    cacheSeconds(method),
                    servlet);
            this.annotations = annotations(method);
        }

        // 供Rest类使用，参数不能随便更改
        public ActionEntry(
                int moduleid,
                int actionid,
                String name,
                String[] methods,
                Method method,
                boolean rpcOnly,
                boolean auth,
                int cacheSeconds,
                HttpServlet servlet) {
            this.moduleid = moduleid;
            this.actionid = actionid;
            this.name = name;
            this.methods = methods;
            this.method = method; // rest构建会为null
            this.servlet = servlet;
            this.rpcOnly = rpcOnly;
            this.auth = auth;
            this.cacheSeconds = cacheSeconds;
            if (Utility.contains(name, '*', '{', '[', '(', '|', '^', '$', '+', '?', '\\')
                    || name.endsWith("/")) { // 是否是正则表达式
                this.modeOneCache = false;
                this.cache = cacheSeconds > 0 ? new ConcurrentHashMap<>() : null;
                this.cacheHandler = cacheSeconds > 0
                        ? (HttpResponse response, byte[] content) -> {
                            int status = response.getStatus();
                            if (status != 200) {
                                return;
                            }
                            CacheEntry ce = new CacheEntry(response.getStatus(), response.getContentType(), content);
                            cache.put(response.getRequest().getRequestPath(), ce);
                        }
                        : null;
            } else { // 单一url
                this.modeOneCache = true;
                this.cache = null;
                this.cacheHandler = cacheSeconds > 0
                        ? (HttpResponse response, byte[] content) -> {
                            int status = response.getStatus();
                            if (status != 200) {
                                return;
                            }
                            oneCache = new CacheEntry(response.getStatus(), response.getContentType(), content);
                        }
                        : null;
            }
            this.nonBlocking = servlet._nonBlocking;
        }

        protected static boolean auth(Method method) {
            HttpMapping mapping = method.getAnnotation(HttpMapping.class);
            return mapping == null || mapping.auth();
        }

        protected static boolean rpcOnly(Method method) {
            HttpMapping mapping = method.getAnnotation(HttpMapping.class);
            return mapping == null || mapping.rpcOnly();
        }

        protected static int cacheSeconds(Method method) {
            HttpMapping mapping = method.getAnnotation(HttpMapping.class);
            return mapping == null ? 0 : mapping.cacheSeconds();
        }

        // Rest.class会用到此方法
        protected static Annotation[] annotations(Method method) {
            return method.getAnnotations();
        }

        boolean checkMethod(final String reqMethod) {
            if (methods.length == 0) {
                return true;
            }
            for (String m : methods) {
                if (reqMethod.equalsIgnoreCase(m)) {
                    return true;
                }
            }
            return false;
        }

        final BiConsumer<HttpResponse, byte[]> cacheHandler;

        final ConcurrentHashMap<String, CacheEntry> cache;

        final boolean modeOneCache;

        final int cacheSeconds;

        final boolean rpcOnly;

        final boolean nonBlocking;

        final boolean auth;

        final int moduleid;

        final int actionid;

        final String name;

        final String[] methods; // 不能为null，长度为0表示容许所有method

        final HttpServlet servlet;

        Method method;

        CacheEntry oneCache;

        Annotation[] annotations;
    }

    private HttpServlet createActionServlet(final boolean typeNonBlocking, final Method method) {
        // ------------------------------------------------------------------------------
        final ClassDesc superDesc = ClassDesc.ofDescriptor(HttpServlet.class.descriptorString());
        final ClassDesc servletDesc = ClassDesc.ofDescriptor(this.getClass().descriptorString());
        final ClassDesc requestSupDesc = ClassDesc.ofDescriptor(Request.class.descriptorString());
        final ClassDesc responseSupDesc = ClassDesc.ofDescriptor(Response.class.descriptorString());
        final ClassDesc reqDesc = ClassDesc.ofDescriptor(HttpRequest.class.descriptorString());
        final ClassDesc respDesc = ClassDesc.ofDescriptor(HttpResponse.class.descriptorString());
        final String factfield = "_factServlet";
        StringBuilder tmpps = new StringBuilder();
        for (Class cz : method.getParameterTypes()) {
            tmpps.append("__").append(cz.getName().replace('.', '_'));
        }
        RedkaleClassLoader classLoader = RedkaleClassLoader.currentClassLoader();
        final String newDynName = "org/redkaledyn/http/servlet/action/_DynHttpActionServlet__"
                + this.getClass().getName().replace('.', '_').replace('$', '_') + "__" + method.getName() + tmpps;
        try {
            HttpServlet instance = (HttpServlet) classLoader
                    .loadClass(newDynName.replace('/', '.'))
                    .getDeclaredConstructor()
                    .newInstance();
            instance.getClass().getField("_factServlet").set(instance, this);
            return instance;
        } catch (Throwable ex) {
            // do nothing
        }
        // ------------------------------------------------------------------------------
        final ClassDesc dynDesc = ClassDesc.ofInternalName(newDynName);
        final MethodTypeDesc executeDesc = MethodTypeDesc.of(CD_void, reqDesc, respDesc);
        final ClassDesc ioExceptionDesc = ClassDesc.ofDescriptor(IOException.class.descriptorString());
        byte[] bytes = ClassFile.of().build(dynDesc, cb -> {
            cb.withVersion(JAVA_11_VERSION, 0)
                    .withFlags(ACC_PUBLIC | ACC_FINAL | ACC_SUPER)
                    .withSuperclass(superDesc);
            cb.withField(factfield, servletDesc, ACC_PUBLIC);
            cb.withMethodBody(
                    "<init>",
                    MethodTypeDesc.of(CD_void),
                    ACC_PUBLIC,
                    code -> code.aload(0)
                            .invokespecial(superDesc, "<init>", MethodTypeDesc.of(CD_void))
                            .return_());
            cb.withMethod("execute", executeDesc, ACC_PUBLIC, mb -> {
                mb.with(ExceptionsAttribute.ofSymbols(ioExceptionDesc));
                mb.withCode(code -> {
                    code.aload(0)
                            .getfield(dynDesc, factfield, servletDesc)
                            .aload(1)
                            .aload(2)
                            .invokevirtual(servletDesc, method.getName(), executeDesc)
                            .return_();
                    code.localVariable(0, "this", dynDesc, code.startLabel(), code.endLabel());
                    code.localVariable(1, "req", reqDesc, code.startLabel(), code.endLabel());
                    code.localVariable(2, "resp", respDesc, code.startLabel(), code.endLabel());
                });
            });
            cb.withMethod(
                    "execute",
                    MethodTypeDesc.of(CD_void, requestSupDesc, responseSupDesc),
                    ACC_PUBLIC | ACC_BRIDGE | ACC_SYNTHETIC,
                    mb -> {
                        mb.with(ExceptionsAttribute.ofSymbols(ioExceptionDesc));
                        mb.withCode(code -> code.aload(0)
                                .aload(1)
                                .checkcast(reqDesc)
                                .aload(2)
                                .checkcast(respDesc)
                                .invokevirtual(dynDesc, "execute", executeDesc)
                                .return_());
                    });
        });
        // ------------------------------------------------------------------------------
        Class<?> newClazz = classLoader.loadClass(newDynName.replace('/', '.'), bytes);
        RedkaleClassLoader.putReflectionDeclaredConstructors(newClazz, newDynName.replace('/', '.'));
        try {
            HttpServlet instance =
                    (HttpServlet) newClazz.getDeclaredConstructor().newInstance();
            java.lang.reflect.Field field = instance.getClass().getField(factfield);
            field.set(instance, this);
            RedkaleClassLoader.putReflectionField(newDynName.replace('/', '.'), field);
            NonBlocking non = method.getAnnotation(NonBlocking.class);
            instance._nonBlocking = typeNonBlocking && (non == null ? typeNonBlocking : non.value());
            return instance;
        } catch (Exception ex) {
            throw new HttpException(ex);
        }
    }

    private static final class CacheEntry {

        public final long time = System.currentTimeMillis();

        private final byte[] cacheBytes;

        private final int status;

        private final String contentType;

        public CacheEntry(int status, String contentType, byte[] cacheBytes) {
            this.status = status;
            this.contentType = contentType;
            this.cacheBytes = cacheBytes;
        }

        public byte[] getBytes() {
            return cacheBytes;
        }
    }

    static class HttpActionServlet extends HttpServlet {

        final ActionEntry action;

        final HttpServlet servlet;

        public HttpActionServlet(ActionEntry actionEntry, HttpServlet servlet, String actionSimpleMappingUrl) {
            this.action = actionEntry;
            this.servlet = servlet;
            if (actionSimpleMappingUrl != null
                    && !Utility.contains(actionSimpleMappingUrl, '*', '{', '[', '(', '|', '^', '$', '+', '?', '\\')) {
                this._actionSimpleMappingUrl = actionSimpleMappingUrl;
            }
            this._nonBlocking = actionEntry.nonBlocking;
        }

        @Override
        public void execute(HttpRequest request, HttpResponse response) throws IOException {
            request.actionEntry = action;
            servlet.execute(request, response);
        }
    }
}
