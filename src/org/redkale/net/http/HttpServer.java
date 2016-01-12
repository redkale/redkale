/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.net.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.redkale.net.*;
import org.redkale.util.*;
import org.redkale.watch.*;

/**
 *
 * <p> 详情见: http://www.redkale.org
 * @author zhangjx
 */
public final class HttpServer extends Server {

    private String contextPath;

    public HttpServer() {
        this(System.currentTimeMillis(), null);
    }

    public HttpServer(long serverStartTime, final WatchFactory watch) {
        super(serverStartTime, "TCP", new HttpPrepareServlet(), watch);
    }

    @Override
    public void init(AnyValue config) throws Exception {
        super.init(config);
        AnyValue conf = config == null ? null : config.getAnyValue("servlets");
        this.contextPath = conf == null ? "" : conf.getValue("path", "");
    }

    public void addHttpServlet(HttpServlet servlet, AnyValue conf, String... mappings) {
        ((HttpPrepareServlet) this.prepare).addHttpServlet(servlet, conf, mappings);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Context createContext() {
        final int port = this.address.getPort();
        AtomicLong createBufferCounter = watch == null ? new AtomicLong() : watch.createWatchNumber("HTTP_" + port + ".Buffer.creatCounter");
        AtomicLong cycleBufferCounter = watch == null ? new AtomicLong() : watch.createWatchNumber("HTTP_" + port + ".Buffer.cycleCounter");
        final int rcapacity = Math.max(this.bufferCapacity, 16 * 1024 + 8); //兼容 HTTP 2.0
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
                    for (int i = 0; i < addHeaders.length; i++) {
                        String val = addHeaders[i].getValue("value");
                        if (val == null) continue;
                        if (val.startsWith("request.headers.")) {
                            defaultAddHeaders.add(new String[]{addHeaders[i].getValue("name"), val, val.substring("request.headers.".length())});
                        } else if (val.startsWith("system.property.")) {
                            String v = System.getProperty(val.substring("system.property.".length()));
                            if (v != null) defaultAddHeaders.add(new String[]{addHeaders[i].getValue("name"), v});
                        } else {
                            defaultAddHeaders.add(new String[]{addHeaders[i].getValue("name"), val});
                        }
                    }
                }
                AnyValue[] setHeaders = resps.getAnyValues("setheader");
                if (setHeaders.length > 0) {
                    for (int i = 0; i < setHeaders.length; i++) {
                        String val = setHeaders[i].getValue("value");
                        if (val != null && val.startsWith("request.headers.")) {
                            defaultSetHeaders.add(new String[]{setHeaders[i].getValue("name"), val, val.substring("request.headers.".length())});
                        } else {
                            defaultSetHeaders.add(new String[]{setHeaders[i].getValue("name"), val});
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
        AtomicLong createResponseCounter = watch == null ? new AtomicLong() : watch.createWatchNumber("HTTP_" + port + ".Response.creatCounter");
        AtomicLong cycleResponseCounter = watch == null ? new AtomicLong() : watch.createWatchNumber("HTTP_" + port + ".Response.cycleCounter");
        ObjectPool<Response> responsePool = HttpResponse.createPool(createResponseCounter, cycleResponseCounter, this.responsePoolSize, null);
        HttpContext httpcontext = new HttpContext(this.serverStartTime, this.logger, executor, rcapacity, bufferPool, responsePool,
                this.maxbody, this.charset, this.address, this.prepare, this.watch, this.readTimeoutSecond, this.writeTimeoutSecond, contextPath);
        responsePool.setCreator((Object... params) -> new HttpResponse(httpcontext, new HttpRequest(httpcontext, addrHeader), addHeaders, setHeaders, defCookie));
        return httpcontext;
    }

}
