/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net;

import com.wentch.redkale.convert.bson.*;
import com.wentch.redkale.convert.json.*;
import com.wentch.redkale.util.*;
import com.wentch.redkale.watch.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.logging.*;

/**
 *
 * @author zhangjx
 */
public class Context {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    protected final long serverStartTime;

    protected final ExecutorService executor;

    protected final int bufferCapacity;

    protected final ObjectPool<ByteBuffer> bufferPool;

    protected final ObjectPool<Response> responsePool;

    protected final PrepareServlet prepare;

    private final InetSocketAddress address;

    protected final Charset charset;

    protected final int maxbody;

    protected final int readTimeoutSecond;

    protected final int writeTimeoutSecond;

    protected final Logger logger;

    protected final BsonFactory bsonFactory;

    protected final JsonFactory jsonFactory;

    protected final WatchFactory watch;

    public Context(long serverStartTime, Logger logger, ExecutorService executor, int bufferCapacity, ObjectPool<ByteBuffer> bufferPool, ObjectPool<Response> responsePool,
            final int maxbody, Charset charset, InetSocketAddress address, final PrepareServlet prepare, final WatchFactory watch,
            final int readTimeoutSecond, final int writeTimeoutSecond) {
        this.serverStartTime = serverStartTime;
        this.logger = logger;
        this.executor = executor;
        this.bufferCapacity = bufferCapacity;
        this.bufferPool = bufferPool;
        this.responsePool = responsePool;
        this.maxbody = maxbody;
        this.charset = UTF8.equals(charset) ? null : charset;
        this.address = address;
        this.prepare = prepare;
        this.watch = watch;
        this.readTimeoutSecond = readTimeoutSecond;
        this.writeTimeoutSecond = writeTimeoutSecond;
        this.jsonFactory = JsonFactory.root();
        this.bsonFactory = BsonFactory.root();
    }

    public int getMaxbody() {
        return maxbody;
    }

    public InetSocketAddress getServerAddress() {
        return address;
    }

    public long getServerStartTime() {
        return serverStartTime;
    }

    public Charset getCharset() {
        return charset;
    }

    public void submit(Runnable r) {
        executor.submit(r);
    }

    public int getBufferCapacity() {
        return bufferCapacity;
    }

    public Supplier<ByteBuffer> getBufferSupplier() {
        return bufferPool;
    }

    public ByteBuffer pollBuffer() {
        return bufferPool.get();
    }

    public void offerBuffer(ByteBuffer buffer) {
        bufferPool.offer(buffer);
    }

    public Logger getLogger() {
        return logger;
    }

    public int getReadTimeoutSecond() {
        return readTimeoutSecond;
    }

    public int getWriteTimeoutSecond() {
        return writeTimeoutSecond;
    }

    public JsonConvert getJsonConvert() {
        return jsonFactory.getConvert();
    }

    public BsonConvert getBsonConvert() {
        return bsonFactory.getConvert();
    }
}
