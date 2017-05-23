/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.lang.reflect.Field;
import java.net.HttpCookie;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.redkale.net.*;
import org.redkale.service.Service;
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

    public HttpServer() {
        this(System.currentTimeMillis());
    }

    public HttpServer(long serverStartTime) {
        super(serverStartTime, "TCP", new HttpPrepareServlet());
    }

    @Override
    public void init(AnyValue config) throws Exception {
        super.init(config);
    }

    /**
     * 获取静态资源HttpServlet
     *
     * @return HttpServlet
     */
    public HttpServlet getResourceServlet() {
        return ((HttpPrepareServlet) this.prepare).resourceHttpServlet;
    }

    /**
     * 删除HttpFilter
     *
     * @param filterName HttpFilter名称
     *
     * @return HttpFilter
     */
    public HttpFilter removeFilter(String filterName) {
        return (HttpFilter) this.prepare.removeFilter(filterName);
    }

    /**
     * 屏蔽请求URL的正则表达式
     *
     * @param urlreg 正则表达式
     *
     * @return 是否成功
     */
    public boolean addForbidURIReg(final String urlreg) {
        return ((HttpPrepareServlet) this.prepare).addForbidURIReg(urlreg);
    }

    /**
     * 删除屏蔽请求URL的正则表达式
     *
     * @param urlreg 正则表达式
     *
     * @return 是否成功
     */
    public boolean removeForbidURIReg(final String urlreg) {
        return ((HttpPrepareServlet) this.prepare).removeForbidURIReg(urlreg);
    }

    /**
     * 删除HttpFilter
     *
     * @param <T>         泛型
     * @param filterClass HttpFilter类
     *
     * @return HttpFilter
     */
    public <T extends HttpFilter> T removeFilter(Class<T> filterClass) {
        return (T) this.prepare.removeFilter(filterClass);
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
        this.prepare.addFilter(filter, conf);
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
        this.prepare.addServlet(servlet, prefix, null, mappings);
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
        this.prepare.addServlet(servlet, null, null, mappings);
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
        this.prepare.addServlet(servlet, prefix, conf, mappings);
        return this;
    }

    /**
     * 添加RestServlet
     *
     * @param <S>              Service
     * @param <T>              RestServlet
     * @param name             Service的资源名
     * @param serviceType      Service的类型
     * @param service          Service对象
     * @param baseServletClass RestServlet基类
     * @param prefix           url前缀
     *
     * @return RestServlet
     */
    public <S extends Service, T extends HttpServlet> T addRestServlet(String name, Class<S> serviceType, S service, Class<T> baseServletClass, String prefix) {
        return addRestServlet(name, serviceType, service, null, baseServletClass, prefix, null);
    }

    /**
     * 添加RestServlet
     *
     * @param <S>              Service
     * @param <T>              RestServlet
     * @param name             Service的资源名
     * @param serviceType      Service的类型
     * @param service          Service对象
     * @param userType         用户数据类型
     * @param baseServletClass RestServlet基类
     * @param prefix           url前缀
     *
     * @return RestServlet
     */
    public <S extends Service, T extends HttpServlet> T addRestServlet(String name, Class<S> serviceType, S service, final Class userType, Class<T> baseServletClass, String prefix) {
        return addRestServlet(name, serviceType, service, userType, baseServletClass, prefix, null);
    }

    /**
     * 添加WebSocketServlet
     *
     * @param <S>           WebSocket
     * @param <T>           HttpServlet
     * @param webSocketType WebSocket的类型
     * @param prefix        url前缀
     * @param conf          配置信息
     *
     * @return RestServlet
     */
    public <S extends WebSocket, T extends HttpServlet> T addRestWebSocketServlet(final Class<S> webSocketType, final String prefix, final AnyValue conf) {
        T servlet = Rest.createRestWebSocketServlet(webSocketType);
        if (servlet != null) this.prepare.addServlet(servlet, prefix, conf);
        return servlet;
    }

    /**
     * 添加RestServlet
     *
     * @param <S>             Service
     * @param <T>             HttpServlet
     * @param name            Service的资源名
     * @param serviceType     Service的类型
     * @param service         Service对象
     * @param userType        用户数据类型
     * @param baseServletType RestServlet基类
     * @param prefix          url前缀
     * @param conf            配置信息
     *
     * @return RestServlet
     */
    public <S extends Service, T extends HttpServlet> T addRestServlet(final String name, final Class<S> serviceType,
        final S service, final Class userType, final Class<T> baseServletType, final String prefix, final AnyValue conf) {
        T servlet = null;
        for (final HttpServlet item : ((HttpPrepareServlet) this.prepare).getServlets()) {
            if (!(item instanceof HttpServlet)) continue;
            if (item.getClass().getAnnotation(Rest.RestDynamic.class) == null) continue;
            try {
                Field field = item.getClass().getDeclaredField(Rest.REST_SERVICE_FIELD_NAME);
                if (serviceType.equals(field.getType())) {
                    servlet = (T) item;
                    break;
                }
            } catch (NoSuchFieldException | SecurityException e) {
                System.err.println("serviceType = " + serviceType + ", servletClass = " + item.getClass());
                e.printStackTrace();
            }
        }
        final boolean first = servlet == null;
        if (servlet == null) servlet = Rest.createRestServlet(userType, baseServletType, serviceType);
        if (servlet == null) return null; //没有HttpMapping方法的HttpServlet调用Rest.createRestServlet就会返回null 
        try { //若提供动态变更Service服务功能，则改Rest服务无法做出相应更新
            Field field = servlet.getClass().getDeclaredField(Rest.REST_SERVICE_FIELD_NAME);
            field.setAccessible(true);

            Field mapfield = servlet.getClass().getDeclaredField(Rest.REST_SERVICEMAP_FIELD_NAME);
            mapfield.setAccessible(true);

            Service firstService = (Service) field.get(servlet);
            if (name.isEmpty()) {
                field.set(servlet, service);
                firstService = service;
            }
            Map map = (Map) mapfield.get(servlet);
            if (map == null && !name.isEmpty()) map = new HashMap();
            if (map != null) {
                map.put(name, service);
                if (firstService != null) map.put("", firstService);
            }
            mapfield.set(servlet, map);
        } catch (Exception e) {
            throw new RuntimeException(serviceType + " generate rest servlet error", e);
        }
        if (first) this.prepare.addServlet(servlet, prefix, conf);
        return servlet;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected HttpContext createContext() {
        final int port = this.address.getPort();
        AtomicLong createBufferCounter = new AtomicLong();
        AtomicLong cycleBufferCounter = new AtomicLong();
        this.bufferCapacity = Math.max(this.bufferCapacity, 16 * 1024 + 16); //兼容 HTTP 2.0;
        final int rcapacity = this.bufferCapacity;
        ObjectPool<ByteBuffer> bufferPool = new ObjectPool<>(createBufferCounter, cycleBufferCounter, this.bufferPoolSize,
            (Object... params) -> ByteBuffer.allocateDirect(rcapacity), null, (e) -> {
                if (e == null || e.isReadOnly() || e.capacity() != rcapacity) return false;
                e.clear();
                return true;
            });
        final List<String[]> defaultAddHeaders = new ArrayList<>();
        final List<String[]> defaultSetHeaders = new ArrayList<>();
        HttpCookie defaultCookie = null;
        String remoteAddrHeader = null;
        if (config != null) {
            AnyValue reqs = config == null ? null : config.getAnyValue("request");
            if (reqs != null) {
                AnyValue raddr = reqs.getAnyValue("remoteaddr");
                remoteAddrHeader = raddr == null ? null : raddr.getValue("value");
                if (remoteAddrHeader != null) {
                    if (remoteAddrHeader.startsWith("request.headers.")) {
                        remoteAddrHeader = remoteAddrHeader.substring("request.headers.".length());
                    } else {
                        remoteAddrHeader = null;
                    }
                }
            }

            AnyValue resps = config == null ? null : config.getAnyValue("response");
            if (resps != null) {
                AnyValue[] addHeaders = resps.getAnyValues("addheader");
                if (addHeaders.length > 0) {
                    for (AnyValue addHeader : addHeaders) {
                        String val = addHeader.getValue("value");
                        if (val == null) continue;
                        if (val.startsWith("request.parameters.")) {
                            defaultAddHeaders.add(new String[]{addHeader.getValue("name"), val, val.substring("request.parameters.".length()), null});
                        } else if (val.startsWith("request.headers.")) {
                            defaultAddHeaders.add(new String[]{addHeader.getValue("name"), val, val.substring("request.headers.".length())});
                        } else if (val.startsWith("system.property.")) {
                            String v = System.getProperty(val.substring("system.property.".length()));
                            if (v != null) defaultAddHeaders.add(new String[]{addHeader.getValue("name"), v});
                        } else {
                            defaultAddHeaders.add(new String[]{addHeader.getValue("name"), val});
                        }
                    }
                }
                AnyValue[] setHeaders = resps.getAnyValues("setheader");
                if (setHeaders.length > 0) {
                    for (AnyValue setHeader : setHeaders) {
                        String val = setHeader.getValue("value");
                        if (val == null) continue;
                        if (val.startsWith("request.parameters.")) {
                            defaultSetHeaders.add(new String[]{setHeader.getValue("name"), val, val.substring("request.parameters.".length()), null});
                        } else if (val.startsWith("request.headers.")) {
                            defaultSetHeaders.add(new String[]{setHeader.getValue("name"), val, val.substring("request.headers.".length())});
                        } else if (val.startsWith("system.property.")) {
                            String v = System.getProperty(val.substring("system.property.".length()));
                            if (v != null) defaultSetHeaders.add(new String[]{setHeader.getValue("name"), v});
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
            }
        }
        final String[][] addHeaders = defaultAddHeaders.isEmpty() ? null : defaultAddHeaders.toArray(new String[defaultAddHeaders.size()][]);
        final String[][] setHeaders = defaultSetHeaders.isEmpty() ? null : defaultSetHeaders.toArray(new String[defaultSetHeaders.size()][]);
        final HttpCookie defCookie = defaultCookie;
        final String addrHeader = remoteAddrHeader;
        AtomicLong createResponseCounter = new AtomicLong();
        AtomicLong cycleResponseCounter = new AtomicLong();
        ObjectPool<Response> responsePool = HttpResponse.createPool(createResponseCounter, cycleResponseCounter, this.responsePoolSize, null);
        HttpContext httpcontext = new HttpContext(this.serverStartTime, this.logger, executor, rcapacity, bufferPool, responsePool,
            this.maxbody, this.charset, this.address, this.prepare, this.readTimeoutSecond, this.writeTimeoutSecond);
        responsePool.setCreator((Object... params) -> new HttpResponse(httpcontext, new HttpRequest(httpcontext, addrHeader), addHeaders, setHeaders, defCookie));
        return httpcontext;
    }

}
