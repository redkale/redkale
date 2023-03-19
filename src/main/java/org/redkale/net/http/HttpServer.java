/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.lang.reflect.*;
import java.net.*;
import java.text.*;
import java.time.*;
import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;
import java.util.function.*;
import java.util.logging.*;
import org.redkale.boot.*;
import org.redkale.mq.*;
import org.redkale.net.*;
import org.redkale.net.http.HttpContext.HttpContextConfig;
import org.redkale.net.http.HttpResponse.HttpResponseConfig;
import org.redkale.net.sncp.*;
import org.redkale.service.*;
import org.redkale.util.*;

/**
 * Http服务器
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class HttpServer extends Server<String, HttpContext, HttpRequest, HttpResponse, HttpServlet> {

    private ScheduledThreadPoolExecutor dateScheduler;

    private HttpResponseConfig respConfig;

    private ByteBufferPool safeBufferPool;

    private final ReentrantLock groupLock = new ReentrantLock();

    private WebSocketAsyncGroup asyncGroup;

    //配置<executor threads="0"> APP_EXECUTOR资源为null
    //RESNAME_APP_EXECUTOR
    protected ExecutorService workExecutor;

    public HttpServer() {
        this(null, System.currentTimeMillis(), ResourceFactory.create());
    }

    public HttpServer(ResourceFactory resourceFactory) {
        this(null, System.currentTimeMillis(), resourceFactory);
    }

    public HttpServer(Application application, long serverStartTime, ResourceFactory resourceFactory) {
        super(application, serverStartTime, "TCP", resourceFactory, new HttpDispatcherServlet());
        this.workExecutor = application == null ? null : application.getWorkExecutor();
    }

    @Override
    public void init(AnyValue config) throws Exception {
        super.init(config);
        if (context.rpcAuthenticator != null) {
            context.rpcAuthenticator.init(context.rpcAuthenticatorConfig);
        }
    }

    @Override
    protected void postStart() {
        ((HttpDispatcherServlet) this.dispatcher).postStart(this.context, config);
    }

    @Override
    public void destroy(final AnyValue config) throws Exception {
        super.destroy(config);
        if (this.dateScheduler != null) {
            this.dateScheduler.shutdownNow();
            this.dateScheduler = null;
        }
        if (asyncGroup != null) {
            asyncGroup.close();
        }
        if (context.rpcAuthenticator != null) {
            context.rpcAuthenticator.destroy(context.rpcAuthenticatorConfig);
        }
        HttpResponseConfig respConf = this.respConfig;
        HttpRender httpRender = respConf.httpRender;
        if (httpRender != null) {
            httpRender.destroy(context, respConf.renderConfig);
        }
    }

    public List<HttpServlet> getHttpServlets() {
        return this.dispatcher.getServlets();
    }

    public List<HttpFilter> getHttpFilters() {
        return this.dispatcher.getFilters();
    }

    public HttpResponseConfig getResponseConfig() {
        return respConfig;
    }

    /**
     * 获取静态资源HttpServlet
     *
     * @return HttpServlet
     */
    public HttpResourceServlet getResourceServlet() {
        return (HttpResourceServlet) ((HttpDispatcherServlet) this.dispatcher).resourceHttpServlet;
    }

    /**
     * 删除HttpServlet
     *
     * @param service Service
     *
     * @return HttpServlet
     */
    public HttpServlet removeHttpServlet(Service service) {
        return ((HttpDispatcherServlet) this.dispatcher).removeHttpServlet(service);
    }

    /**
     * 删除HttpServlet
     *
     * @param <T>                    泛型
     * @param websocketOrServletType Class
     *
     * @return HttpServlet
     */
    public <T extends WebSocket> HttpServlet removeHttpServlet(Class<T> websocketOrServletType) {
        return ((HttpDispatcherServlet) this.dispatcher).removeHttpServlet(websocketOrServletType);
    }

    /**
     * 屏蔽请求URL的正则表达式
     *
     * @param urlreg 正则表达式
     *
     * @return 是否成功
     */
    public boolean addForbidURIReg(final String urlreg) {
        return ((HttpDispatcherServlet) this.dispatcher).addForbidURIRegx(urlreg);
    }

    /**
     * 删除屏蔽请求URL的正则表达式
     *
     * @param urlreg 正则表达式
     *
     * @return 是否成功
     */
    public boolean removeForbidURIReg(final String urlreg) {
        return ((HttpDispatcherServlet) this.dispatcher).removeForbidURIReg(urlreg);
    }

    /**
     * 删除HttpFilter
     *
     * @param <T>         泛型
     * @param filterClass HttpFilter类
     *
     * @return HttpFilter
     */
    public <T extends HttpFilter> T removeHttpFilter(Class<T> filterClass) {
        return (T) this.dispatcher.removeFilter(filterClass);
    }

    /**
     * 添加HttpFilter
     *
     * @param filter HttpFilter
     * @param conf   AnyValue
     *
     * @return HttpServer
     */
    public HttpServer addHttpFilter(HttpFilter filter, AnyValue conf) {
        this.dispatcher.addFilter(filter, conf);
        return this;
    }

    /**
     * 添加HttpServlet
     *
     * @param prefix   url前缀
     * @param servlet  HttpServlet
     * @param mappings 匹配规则
     *
     * @return HttpServer
     */
    public HttpServer addHttpServlet(String prefix, HttpServlet servlet, String... mappings) {
        this.dispatcher.addServlet(servlet, prefix, null, mappings);
        return this;
    }

    /**
     * 添加HttpServlet
     *
     * @param servlet  HttpServlet
     * @param mappings 匹配规则
     *
     * @return HttpServer
     */
    public HttpServer addHttpServlet(HttpServlet servlet, String... mappings) {
        this.dispatcher.addServlet(servlet, null, null, mappings);
        return this;
    }

    /**
     * 添加HttpServlet
     *
     * @param prefix   url前缀
     * @param servlet  HttpServlet
     * @param conf     配置信息
     * @param mappings 匹配规则
     *
     * @return HttpServer
     */
    public HttpServer addHttpServlet(HttpServlet servlet, final String prefix, AnyValue conf, String... mappings) {
        this.dispatcher.addServlet(servlet, prefix, conf, mappings);
        return this;
    }

    /**
     * 添加WebSocketServlet
     *
     * @param <S>           WebSocket
     * @param <T>           HttpServlet
     * @param classLoader   ClassLoader
     * @param webSocketType WebSocket的类型
     * @param messageAgent  MessageAgent
     * @param prefix        url前缀
     * @param conf          配置信息
     *
     * @return RestServlet
     */
    public <S extends WebSocket, T extends WebSocketServlet> T addRestWebSocketServlet(final ClassLoader classLoader, final Class<S> webSocketType, MessageAgent messageAgent, final String prefix, final AnyValue conf) {
        T servlet = Rest.createRestWebSocketServlet(classLoader, webSocketType, messageAgent);
        if (servlet != null) {
            this.dispatcher.addServlet(servlet, prefix, conf);
        }
        return servlet;
    }

    /**
     * 添加RestServlet
     *
     * @param <S>             Service
     * @param <T>             HttpServlet
     * @param classLoader     ClassLoader
     * @param service         Service对象
     * @param userType        用户数据类型
     * @param baseServletType RestServlet基类
     * @param prefix          url前缀
     *
     * @return RestServlet
     */
    public <S extends Service, T extends HttpServlet> T addRestServlet(final ClassLoader classLoader, final S service, final Class userType, final Class<T> baseServletType, final String prefix) {
        return addRestServlet(classLoader, null, service, userType, baseServletType, prefix);
    }

    /**
     * 添加RestServlet
     *
     * @param <S>             Service
     * @param <T>             HttpServlet
     * @param classLoader     ClassLoader
     * @param name            资源名
     * @param service         Service对象
     * @param userType        用户数据类型
     * @param baseServletType RestServlet基类
     * @param prefix          url前缀
     *
     * @return RestServlet
     */
    @SuppressWarnings("unchecked")
    public <S extends Service, T extends HttpServlet> T addRestServlet(final ClassLoader classLoader, final String name, final S service, final Class userType, final Class<T> baseServletType, final String prefix) {
        T servlet = null;
        final boolean sncp = Sncp.isSncpDyn(service);
        final String resname = name == null ? (sncp ? Sncp.getResourceName(service) : "") : name;
        final Class<S> serviceType = Sncp.getResourceType(service);
        if (name != null) {
            for (final HttpServlet item : ((HttpDispatcherServlet) this.dispatcher).getServlets()) {
                if (!(item instanceof HttpServlet)) {
                    continue;
                }
                if (item.getClass().getAnnotation(Rest.RestDyn.class) == null) {
                    continue;
                }
                try {
                    Field field = item.getClass().getDeclaredField(Rest.REST_SERVICE_FIELD_NAME);
                    if (serviceType.equals(field.getType())) {
                        servlet = (T) item;
                        break;
                    }
                } catch (NoSuchFieldException | SecurityException e) {
                    logger.log(Level.SEVERE, "serviceType = " + serviceType + ", servletClass = " + item.getClass(), e);
                }
            }
        }
        final boolean first = servlet == null;
        if (servlet == null) {
            servlet = Rest.createRestServlet(classLoader, userType, baseServletType, serviceType);
            if (servlet != null) {
                servlet._reqtopic = MessageAgent.generateHttpReqTopic(Rest.getRestModule(service));
                if (serviceType.getAnnotation(MessageMultiConsumer.class) != null) {
                    MessageMultiConsumer mmc = serviceType.getAnnotation(MessageMultiConsumer.class);
                    servlet._mmctopic = MessageAgent.generateHttpReqTopic(mmc.module(), resname);
                }
            }
        }
        if (servlet == null) {
            return null; //没有HttpMapping方法的HttpServlet调用Rest.createRestServlet就会返回null 
        }
        try { //若提供动态变更Service服务功能，则改Rest服务无法做出相应更新
            Field field = servlet.getClass().getDeclaredField(Rest.REST_SERVICE_FIELD_NAME);
            field.setAccessible(true);

            Field mapfield = servlet.getClass().getDeclaredField(Rest.REST_SERVICEMAP_FIELD_NAME);
            mapfield.setAccessible(true);

            Service firstService = (Service) field.get(servlet);
            if (resname.isEmpty()) {
                field.set(servlet, service);
                firstService = service;
            }
            Map map = (Map) mapfield.get(servlet);
            if (map == null && !resname.isEmpty()) {
                map = new HashMap();
            }
            if (map != null) {
                map.put(resname, service);
                if (firstService != null) {
                    map.put("", firstService);
                }
            }
            mapfield.set(servlet, map);
        } catch (Exception e) {
            throw new HttpException(serviceType + " generate rest servlet error", e);
        }
        if (first) {
            this.dispatcher.addServlet(servlet, prefix, sncp ? Sncp.getResourceConf(service) : null);
        }
        return servlet;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected HttpContext createContext() {
        final int port = this.address.getPort();
        //this.bufferCapacity = Math.max(this.bufferCapacity, 16 * 1024 + 16); //兼容 HTTP 2.0;
        this.bufferCapacity = Math.max(this.bufferCapacity, 1024);
        final List<String[]> defaultAddHeaders = new ArrayList<>();
        final List<String[]> defaultSetHeaders = new ArrayList<>();
        boolean autoOptions = false;
        int datePeriod = 0;
        String plainContentType = null;
        String jsonContentType = null;
        HttpCookie defaultCookie = null;
        String remoteAddrHeader = null;
        String localHeader = null;
        String localParameter = null;
        AnyValue rpcAuthenticatorConfig = null;

        if (config != null) {
            AnyValue reqs = config.getAnyValue("request");
            if (reqs != null) {
                rpcAuthenticatorConfig = reqs.getAnyValue("rpc");
                AnyValue raddr = reqs.getAnyValue("remoteaddr");
                remoteAddrHeader = raddr == null ? null : raddr.getValue("value");
                if (remoteAddrHeader != null) {
                    if (remoteAddrHeader.startsWith("request.headers.")) {
                        remoteAddrHeader = remoteAddrHeader.substring("request.headers.".length());
                    } else {
                        remoteAddrHeader = null;
                    }
                }
                AnyValue rlocale = reqs.getAnyValue("locale");
                String vlocale = rlocale == null ? null : rlocale.getValue("value");
                if (vlocale != null && !vlocale.isEmpty()) {
                    if (vlocale.startsWith("request.headers.")) {
                        localHeader = vlocale.substring("request.headers.".length());
                    } else if (vlocale.startsWith("request.parameters.")) {
                        localParameter = vlocale.substring("request.parameters.".length());
                    } else {
                        logger.log(Level.SEVERE, "request config locale.value not start with request.headers. or request.parameters. but " + vlocale);
                    }
                }
            }

            AnyValue resps = config.getAnyValue("response");
            if (resps != null) {
                AnyValue contenttypes = resps.getAnyValue("content-type");
                if (contenttypes == null) {
                    contenttypes = resps.getAnyValue("contenttype"); //兼容旧的
                }
                if (contenttypes != null) {
                    plainContentType = contenttypes.getValue("plain");
                    jsonContentType = contenttypes.getValue("json");
                }
                AnyValue[] addHeaders = resps.getAnyValues("addheader");
                if (addHeaders.length > 0) {
                    for (AnyValue addHeader : addHeaders) {
                        String val = addHeader.getValue("value");
                        if (val == null) {
                            continue;
                        }
                        if (val.startsWith("request.parameters.")) {
                            defaultAddHeaders.add(new String[]{addHeader.getValue("name"), val, val.substring("request.parameters.".length()), null});
                        } else if (val.startsWith("request.headers.")) {
                            defaultAddHeaders.add(new String[]{addHeader.getValue("name"), val, val.substring("request.headers.".length())});
                        } else if (val.startsWith("system.property.")) {
                            String v = System.getProperty(val.substring("system.property.".length()));
                            if (v != null) {
                                defaultAddHeaders.add(new String[]{addHeader.getValue("name"), v});
                            }
                        } else {
                            defaultAddHeaders.add(new String[]{addHeader.getValue("name"), val});
                        }
                    }
                }
                AnyValue[] setHeaders = resps.getAnyValues("setheader");
                if (setHeaders.length > 0) {
                    for (AnyValue setHeader : setHeaders) {
                        String val = setHeader.getValue("value");
                        if (val == null) {
                            continue;
                        }
                        if (val.startsWith("request.parameters.")) {
                            defaultSetHeaders.add(new String[]{setHeader.getValue("name"), val, val.substring("request.parameters.".length()), null});
                        } else if (val.startsWith("request.headers.")) {
                            defaultSetHeaders.add(new String[]{setHeader.getValue("name"), val, val.substring("request.headers.".length())});
                        } else if (val.startsWith("system.property.")) {
                            String v = System.getProperty(val.substring("system.property.".length()));
                            if (v != null) {
                                defaultSetHeaders.add(new String[]{setHeader.getValue("name"), v});
                            }
                        } else {
                            defaultSetHeaders.add(new String[]{setHeader.getValue("name"), val});
                        }
                    }
                }
                AnyValue defcookieValue = resps.getAnyValue("defcookie");
                if (defcookieValue != null) {
                    String domain = defcookieValue.getValue("domain");
                    String path = defcookieValue.getValue("path");
                    if (domain != null || path != null) {
                        defaultCookie = new HttpCookie("DEFAULTCOOKIE", "");
                        defaultCookie.setDomain(domain);
                        defaultCookie.setPath(path);
                    }
                }
                AnyValue options = resps.getAnyValue("options");
                autoOptions = options != null && options.getBoolValue("auto", false);

                AnyValue dates = resps.getAnyValue("date");
                datePeriod = dates == null ? 0 : dates.getIntValue("period", 0);
            }

        }
        Supplier<byte[]> dateSupplier = null;
        if (datePeriod == 0) {
            final ZoneId gmtZone = ZoneId.of("GMT");
            dateSupplier = () -> ("Date: " + RFC_1123_DATE_TIME.format(java.time.ZonedDateTime.now(gmtZone)) + "\r\n").getBytes();
        } else if (datePeriod > 0) {
            if (this.dateScheduler == null) {
                this.dateScheduler = new ScheduledThreadPoolExecutor(1, (Runnable r) -> {
                    final Thread t = new Thread(r, "Redkale-HTTP:" + port + "-DateSchedule-Thread");
                    t.setDaemon(true);
                    return t;
                });
                final ObjectReference<byte[]> dateRef = new ObjectReference<>();
                final DateFormat gmtDateFormat = new SimpleDateFormat("EEE, d MMM y HH:mm:ss z", Locale.ENGLISH);
                gmtDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
                dateRef.set(("Date: " + gmtDateFormat.format(new Date()) + "\r\n").getBytes());
                final int dp = datePeriod;
                this.dateScheduler.scheduleAtFixedRate(() -> {
                    try {
                        dateRef.set(("Date: " + gmtDateFormat.format(new Date()) + "\r\n").getBytes());
                    } catch (Throwable t) {
                        logger.log(Level.SEVERE, "HttpServer schedule(interval=" + dp + "ms) date-format error", t);
                    }
                }, 1000 - System.currentTimeMillis() % 1000, datePeriod, TimeUnit.MILLISECONDS);
                dateSupplier = () -> dateRef.get();
            }
        }
        HttpRender httpRender = null;
        AnyValue renderConfig = null;
        { //设置TemplateEngine            
            renderConfig = config.getAnyValue("render");
            if (renderConfig != null) {
                String renderType = renderConfig.getValue("value");
                try {
                    Class clazz = Thread.currentThread().getContextClassLoader().loadClass(renderType);
                    RedkaleClassLoader.putReflectionDeclaredConstructors(clazz, clazz.getName());
                    HttpRender render = (HttpRender) clazz.getDeclaredConstructor().newInstance();
                    getResourceFactory().inject(render);
                    httpRender = render;
                } catch (Throwable e) {
                    logger.log(Level.WARNING, "init HttpRender(" + renderType + ") error", e);
                }
            }
        }

        final String addrHeader = remoteAddrHeader;

        this.respConfig = new HttpResponseConfig();
        respConfig.plainContentType = plainContentType;
        respConfig.jsonContentType = jsonContentType;
        respConfig.defaultAddHeaders = defaultAddHeaders.isEmpty() ? null : defaultAddHeaders.toArray(new String[defaultAddHeaders.size()][]);
        respConfig.defaultSetHeaders = defaultSetHeaders.isEmpty() ? null : defaultSetHeaders.toArray(new String[defaultSetHeaders.size()][]);
        respConfig.defaultCookie = defaultCookie;
        respConfig.autoOptions = autoOptions;
        respConfig.dateSupplier = dateSupplier;
        respConfig.httpRender = httpRender;
        respConfig.renderConfig = renderConfig;
        respConfig.init(config);

        final HttpContextConfig contextConfig = new HttpContextConfig();
        initContextConfig(contextConfig);
        contextConfig.remoteAddrHeader = addrHeader;
        contextConfig.localHeader = localHeader;
        contextConfig.localParameter = localParameter;
        contextConfig.rpcAuthenticatorConfig = rpcAuthenticatorConfig;
        if (rpcAuthenticatorConfig != null) {
            String impl = rpcAuthenticatorConfig.getValue("authenticator", "").trim();
            if (impl.isEmpty()) {
                throw new HttpException("init HttpRpcAuthenticator(" + impl + ") error");
            }
            try {
                Class implClass = serverClassLoader.loadClass(impl);
                if (!HttpRpcAuthenticator.class.isAssignableFrom(implClass)) {
                    throw new HttpException("" + impl + " not HttpRpcAuthenticator implement class");
                }
                RedkaleClassLoader.putReflectionPublicConstructors(implClass, implClass.getName());
                contextConfig.rpcAuthenticator = (HttpRpcAuthenticator) implClass.getConstructor().newInstance();
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception e) {
                throw new HttpException("init HttpRpcAuthenticator(" + impl + ") error", e);
            }
        }
        HttpContext rs = new HttpContext(contextConfig);
        rs.webSocketWriterIOThreadFunc = ws -> {
            if (asyncGroup == null) {
                groupLock.lock();
                try {
                    if (asyncGroup == null) {
                        WebSocketAsyncGroup g = new WebSocketAsyncGroup("Redkale-HTTP:" + address.getPort() + "-WebSocketWriteIOThread-%s", workExecutor, safeBufferPool);
                        g.start();
                        asyncGroup = g;
                    }
                } finally {
                    groupLock.unlock();
                }
            }
            return (WebSocketWriteIOThread) asyncGroup.nextWriteIOThread();
        };
        return rs;
    }

    @Override
    protected void postPrepareInit() {
        HttpRender httpRender = this.respConfig.httpRender;
        if (httpRender != null) {
            httpRender.init(context, this.respConfig.renderConfig);
        }
    }

    @Override
    protected ByteBufferPool createSafeBufferPool(LongAdder createCounter, LongAdder cycleCounter, int bufferPoolSize) {
        this.safeBufferPool = ByteBufferPool.createSafePool(createCounter, cycleCounter, bufferPoolSize, this.bufferCapacity);
        return this.safeBufferPool;
    }

    @Override
    protected ObjectPool<HttpResponse> createSafeResponsePool(LongAdder createCounter, LongAdder cycleCounter, int responsePoolSize) {
        Creator<HttpResponse> creator = (Object... params) -> new HttpResponse(this.context, new HttpRequest(this.context), this.respConfig);
        ObjectPool<HttpResponse> pool = ObjectPool.createSafePool(createCounter, cycleCounter, responsePoolSize, creator, HttpResponse::prepare, HttpResponse::recycle);
        return pool;
    }
}
