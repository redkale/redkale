/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.net.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.logging.*;
import org.redkale.convert.bson.*;
import org.redkale.convert.json.*;
import org.redkale.util.*;
import org.redkale.watch.*;

/**
 * 服务器上下文对象
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class Context {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    //服务启动时间
    protected final long serverStartTime;

    //Server的线程池
    protected final ExecutorService executor;

    //ByteBuffer的容量，默认8K
    protected final int bufferCapacity;

    //ByteBuffer对象池
    protected final ObjectPool<ByteBuffer> bufferPool;

    //Response对象池
    protected final ObjectPool<Response> responsePool;

    //服务的根Servlet
    protected final PrepareServlet prepare;

    //服务的监听地址
    private final InetSocketAddress address;

    //字符集
    protected final Charset charset;

    //请求内容的大小上限, 默认64K
    protected final int maxbody;

    //IO读取的超时时间
    protected final int readTimeoutSecond;

    //IO写入的超时时间
    protected final int writeTimeoutSecond;

    //日志Logger
    protected final Logger logger;

    //BSON操作工厂
    protected final BsonFactory bsonFactory;

    //JSON操作工厂
    protected final JsonFactory jsonFactory;

    //监控对象
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
