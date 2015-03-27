/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.http;

import com.wentch.redkale.net.*;
import com.wentch.redkale.util.*;
import com.wentch.redkale.watch.*;
import java.net.*;
import java.nio.*;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;
import java.util.concurrent.atomic.*;

/**
 *
 * @author zhangjx
 */
public final class HttpServer extends Server {

    private final Map<SimpleEntry<HttpServlet, AnyValue>, String[]> servlets = new HashMap<>();

    private String contextPath;

    public HttpServer() {
        this(System.currentTimeMillis(), null);
    }

    public HttpServer(long serverStartTime, final WatchFactory watch) {
        super(serverStartTime, "TCP", watch);
    }

    @Override
    public void init(AnyValue config) throws Exception {
        super.init(config);
        AnyValue conf = config == null ? null : config.getAnyValue("servlets");
        this.contextPath = conf == null ? "" : conf.getValue("prefix", "");
    }

    public void addHttpServlet(HttpServlet servlet, AnyValue conf, String... mappings) {
        this.servlets.put(new SimpleEntry<>(servlet, conf), mappings);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Context createContext() {
        final int port = this.address.getPort();
        AtomicLong createBufferCounter = watch == null ? new AtomicLong() : watch.createWatchNumber("HTTP_" + port + ".Buffer.creatCounter");
        AtomicLong cycleBufferCounter = watch == null ? new AtomicLong() : watch.createWatchNumber("HTTP_" + port + ".Buffer.cycleCounter");
        int rcapacity = Math.max(this.capacity, 8 * 1024);
        ObjectPool<ByteBuffer> bufferPool = new ObjectPool<>(createBufferCounter, cycleBufferCounter, this.bufferPoolSize,
                (Object... params) -> ByteBuffer.allocateDirect(rcapacity), (e) -> {
                    if (e == null || e.isReadOnly() || e.capacity() != rcapacity) return false;
                    e.clear();
                    return true;
                });
        HttpPrepareServlet prepare = new HttpPrepareServlet();
        this.servlets.entrySet().stream().forEach((en) -> {
            prepare.addHttpServlet(en.getKey().getKey(), en.getKey().getValue(), en.getValue());
        });
        this.servlets.clear();
        String[][] defaultAddHeaders = null;
        String[][] defaultSetHeaders = null;
        HttpCookie defaultCookie = null;
        if (config != null) {
            AnyValue resps = config == null ? null : config.getAnyValue("response");
            if (resps != null) {
                AnyValue[] addHeaders = resps.getAnyValues("addheader");
                if (addHeaders.length > 0) {
                    defaultAddHeaders = new String[addHeaders.length][];
                    for (int i = 0; i < addHeaders.length; i++) {
                        String val = addHeaders[i].getValue("value");
                        if (val == null) continue;
                        if (val.startsWith("request.headers.")) {
                            defaultAddHeaders[i] = new String[]{addHeaders[i].getValue("name"), val, val.substring("request.headers.".length())};
                        } else if (val.startsWith("system.property.")) {
                            String v = System.getProperty(val.substring("system.property.".length()));
                            if (v != null) defaultAddHeaders[i] = new String[]{addHeaders[i].getValue("name"), v};
                        } else {
                            defaultAddHeaders[i] = new String[]{addHeaders[i].getValue("name"), val};
                        }
                    }
                }
                AnyValue[] setHeaders = resps.getAnyValues("setheader");
                if (setHeaders.length > 0) {
                    defaultSetHeaders = new String[setHeaders.length][];
                    for (int i = 0; i < setHeaders.length; i++) {
                        String val = setHeaders[i].getValue("value");
                        if (val != null && val.startsWith("request.headers.")) {
                            defaultSetHeaders[i] = new String[]{setHeaders[i].getValue("name"), val, val.substring("request.headers.".length())};
                        } else {
                            defaultSetHeaders[i] = new String[]{setHeaders[i].getValue("name"), val};
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
        final String[][] addHeaders = defaultAddHeaders;
        final String[][] setHeaders = defaultSetHeaders;
        final HttpCookie defCookie = defaultCookie;
        AtomicLong createResponseCounter = watch == null ? new AtomicLong() : watch.createWatchNumber("HTTP_" + port + ".Response.creatCounter");
        AtomicLong cycleResponseCounter = watch == null ? new AtomicLong() : watch.createWatchNumber("HTTP_" + port + ".Response.cycleCounter");
        ObjectPool<Response> responsePool = HttpResponse.createPool(createResponseCounter, cycleResponseCounter, this.responsePoolSize, null);
        HttpContext httpcontext = new HttpContext(this.serverStartTime, this.logger, executor, bufferPool, responsePool,
                this.maxbody, this.charset, this.address, prepare, this.watch, this.readTimeoutSecond, this.writeTimeoutSecond, contextPath);
        responsePool.setCreator((Object... params)
                -> new HttpResponse(httpcontext, new HttpRequest(httpcontext, httpcontext.jsonFactory), addHeaders, setHeaders, defCookie));
        return httpcontext;
    }

}
