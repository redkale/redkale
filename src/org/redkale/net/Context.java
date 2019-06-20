/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.net.*;
import java.nio.charset.*;
import java.util.concurrent.*;
import java.util.logging.*;
import javax.net.ssl.SSLContext;
import org.redkale.convert.bson.*;
import org.redkale.convert.json.*;
import org.redkale.util.*;

/**
 * 服务器上下文对象
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class Context {

    //服务启动时间
    protected final long serverStartTime;

    //Server的线程池
    protected final ThreadPoolExecutor executor;

    //SSL
    protected final SSLContext sslContext;

    //ByteBuffer的容量，默认8K
    protected final int bufferCapacity;

    //服务的根Servlet
    protected final PrepareServlet prepare;

    //日志Logger
    protected final Logger logger;

    //BSON操作工厂
    protected final BsonFactory bsonFactory;

    //JSON操作工厂
    protected final JsonFactory jsonFactory;

    //依赖注入工厂类
    protected final ResourceFactory resourceFactory;

    //最大连接数, 为0表示没限制
    protected int maxconns;

    //请求内容的大小上限, 默认64K
    protected int maxbody;

    //keep alive IO读取的超时时间
    protected int aliveTimeoutSeconds;

    //IO读取的超时时间
    protected int readTimeoutSeconds;

    //IO写入的超时时间
    protected int writeTimeoutSeconds;

    //服务的监听地址
    protected InetSocketAddress address;

    //字符集
    protected Charset charset;

    public Context(ContextConfig config) {
        this(config.serverStartTime, config.logger, config.executor, config.sslContext,
            config.bufferCapacity, config.maxconns, config.maxbody, config.charset, config.address, config.resourceFactory,
            config.prepare, config.aliveTimeoutSeconds, config.readTimeoutSeconds, config.writeTimeoutSeconds);
    }

    public Context(long serverStartTime, Logger logger, ThreadPoolExecutor executor, SSLContext sslContext,
        int bufferCapacity, final int maxconns, final int maxbody, Charset charset, InetSocketAddress address,
        ResourceFactory resourceFactory, PrepareServlet prepare, int aliveTimeoutSeconds, int readTimeoutSeconds, int writeTimeoutSeconds) {
        this.serverStartTime = serverStartTime;
        this.logger = logger;
        this.executor = executor;
        this.sslContext = sslContext;
        this.bufferCapacity = bufferCapacity;
        this.maxconns = maxconns;
        this.maxbody = maxbody;
        this.charset = StandardCharsets.UTF_8.equals(charset) ? null : charset;
        this.address = address;
        this.prepare = prepare;
        this.resourceFactory = resourceFactory;
        this.aliveTimeoutSeconds = aliveTimeoutSeconds;
        this.readTimeoutSeconds = readTimeoutSeconds;
        this.writeTimeoutSeconds = writeTimeoutSeconds;
        this.jsonFactory = JsonFactory.root();
        this.bsonFactory = BsonFactory.root();
    }

    public ResourceFactory getResourceFactory() {
        return resourceFactory;
    }

    public SSLContext getSSLContext() {
        return sslContext;
    }

    public int getMaxconns() {
        return maxconns;
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

    public Future<?> submitAsync(Runnable r) {
        return executor.submit(r);
    }

    public void runAsync(Runnable r) {
        executor.execute(r);
    }

    public int getCorePoolSize() {
        return executor.getCorePoolSize();
    }

    public ThreadFactory getThreadFactory() {
        return executor.getThreadFactory();
    }

    public int getBufferCapacity() {
        return bufferCapacity;
    }

    public Logger getLogger() {
        return logger;
    }

    public int getAliveTimeoutSeconds() {
        return aliveTimeoutSeconds;
    }

    public int getReadTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    public int getWriteTimeoutSeconds() {
        return writeTimeoutSeconds;
    }

    public JsonConvert getJsonConvert() {
        return jsonFactory.getConvert();
    }

    public BsonConvert getBsonConvert() {
        return bsonFactory.getConvert();
    }

    public static class ContextConfig {

        //服务启动时间
        public long serverStartTime;

        //Server的线程池
        public ThreadPoolExecutor executor;

        //SSL
        public SSLContext sslContext;

        //ByteBuffer的容量，默认8K
        public int bufferCapacity;

        //服务的根Servlet
        public PrepareServlet prepare;

        //服务的监听地址
        public InetSocketAddress address;

        //字符集
        public Charset charset;

        //请求内容的大小上限, 默认64K
        public int maxbody;

        //最大连接数, 为0表示没限制
        public int maxconns;

        //keep alive IO读取的超时时间
        public int aliveTimeoutSeconds;

        //IO读取的超时时间
        public int readTimeoutSeconds;

        //IO写入的超时时间
        public int writeTimeoutSeconds;

        //日志Logger
        public Logger logger;

        //依赖注入工厂类
        public ResourceFactory resourceFactory;

        @Override
        public String toString() {
            return JsonConvert.root().convertTo(this);
        }
    }
}
