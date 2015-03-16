/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.http;

import com.wentch.redkale.net.*;
import com.wentch.redkale.util.*;
import com.wentch.redkale.watch.*;
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
        AtomicLong createResponseCounter = watch == null ? new AtomicLong() : watch.createWatchNumber("HTTP_" + port + ".Response.creatCounter");
        AtomicLong cycleResponseCounter = watch == null ? new AtomicLong() : watch.createWatchNumber("HTTP_" + port + ".Response.cycleCounter");
        ObjectPool<Response> responsePool = HttpResponse.createPool(createResponseCounter, cycleResponseCounter, this.responsePoolSize, null);
        HttpContext httpcontext = new HttpContext(this.serverStartTime, this.logger, executor, bufferPool, responsePool,
                this.maxbody, this.charset, this.address, prepare, this.watch, this.readTimeoutSecond, this.writeTimeoutSecond, contextPath);
        responsePool.setCreator((Object... params) -> new HttpResponse(httpcontext, new HttpRequest(httpcontext, httpcontext.jsonFactory)));
        return httpcontext;
    }

}
