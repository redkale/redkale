/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.net.InetSocketAddress;
import java.nio.charset.*;
import java.util.concurrent.ExecutorService;
import java.util.logging.*;
import javax.net.ssl.SSLContext;
import org.redkale.convert.bson.*;
import org.redkale.convert.json.*;
import org.redkale.inject.ResourceFactory;
import org.redkale.util.*;

/**
 * 服务器上下文对象
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class Context {

    // 服务启动时间
    protected final long serverStartTime;

    // Application节点id
    protected final String nodeid;

    // Server的线程池
    protected final ExecutorService workExecutor;

    // SSL
    protected final SSLBuilder sslBuilder;

    // SSL
    protected final SSLContext sslContext;

    // ByteBuffer的容量，默认8K
    protected final int bufferCapacity;

    // 服务的根Servlet
    protected final DispatcherServlet dispatcher;

    // 日志Logger
    protected final Logger logger;

    // BSON操作工厂
    protected final BsonFactory bsonFactory;

    // JSON操作工厂
    protected final JsonFactory jsonFactory;

    // 依赖注入工厂类
    protected final ResourceFactory resourceFactory;

    // 最大连接数, 为0表示没限制
    protected int maxConns;

    // 请求头的大小上限, 默认16K
    protected int maxHeader;

    // 请求内容的大小上限, 默认64K
    protected int maxBody;

    // keep alive IO读取的超时时间
    protected int aliveTimeoutSeconds;

    // IO读取的超时时间
    protected int readTimeoutSeconds;

    // IO写入的超时时间
    protected int writeTimeoutSeconds;

    // 服务的监听地址
    protected InetSocketAddress serverAddress;

    // 字符集
    protected Charset charset;

    public Context(ContextConfig config) {
        this(
                config.serverStartTime,
                config.nodeid,
                config.logger,
                config.workExecutor,
                config.sslBuilder,
                config.sslContext,
                config.bufferCapacity,
                config.maxConns,
                config.maxHeader,
                config.maxBody,
                config.charset,
                config.serverAddress,
                config.resourceFactory,
                config.dispatcher,
                config.aliveTimeoutSeconds,
                config.readTimeoutSeconds,
                config.writeTimeoutSeconds);
    }

    public Context(
            long serverStartTime,
            String nodeid,
            Logger logger,
            ExecutorService workExecutor,
            SSLBuilder sslBuilder,
            SSLContext sslContext,
            int bufferCapacity,
            final int maxConns,
            final int maxHeader,
            final int maxBody,
            Charset charset,
            InetSocketAddress address,
            ResourceFactory resourceFactory,
            DispatcherServlet dispatcher,
            int aliveTimeoutSeconds,
            int readTimeoutSeconds,
            int writeTimeoutSeconds) {
        this.serverStartTime = serverStartTime;
        this.nodeid = nodeid;
        this.logger = logger;
        this.workExecutor = workExecutor;
        this.sslBuilder = sslBuilder;
        this.sslContext = sslContext;
        this.bufferCapacity = bufferCapacity;
        this.maxConns = maxConns;
        this.maxHeader = maxHeader;
        this.maxBody = maxBody;
        this.charset = StandardCharsets.UTF_8.equals(charset) ? null : charset;
        this.serverAddress = address;
        this.dispatcher = dispatcher;
        this.resourceFactory = resourceFactory;
        this.aliveTimeoutSeconds = aliveTimeoutSeconds;
        this.readTimeoutSeconds = readTimeoutSeconds;
        this.writeTimeoutSeconds = writeTimeoutSeconds;
        this.jsonFactory = JsonFactory.root();
        this.bsonFactory = BsonFactory.root();
    }

    protected final void executeDispatch(Request request, Response response) {
        request.traceid = Traces.computeIfAbsent(request.getTraceid());
        dispatcher.dispatch(request, response);
    }

    public void execute(Servlet servlet, Request request, Response response) {
        if (workExecutor != null && response.inNonBlocking() && !servlet.isNonBlocking()) {
            response.updateNonBlocking(false);
            workExecutor.execute(() -> {
                try {
                    request.traceid = Traces.computeIfAbsent(request.getTraceid());
                    servlet.execute(request, response);
                } catch (Throwable t) {
                    response.context.logger.log(
                            Level.WARNING, "Execute servlet occur exception. request = " + request, t);
                    response.finishError(t);
                }
                Traces.removeTraceid();
            });
        } else {
            try {
                request.traceid = Traces.computeIfAbsent(request.getTraceid());
                servlet.execute(request, response);
            } catch (Throwable t) {
                response.context.logger.log(Level.WARNING, "Execute servlet occur exception. request = " + request, t);
                response.finishError(t);
            }
        }
    }

    protected void updateReadIOThread(AsyncConnection conn, AsyncIOThread ioReadThread) {
        conn.updateReadIOThread(ioReadThread);
    }

    protected void updateWriteIOThread(AsyncConnection conn, AsyncIOThread ioWriteThread) {
        conn.updateWriteIOThread(ioWriteThread);
    }

    protected void updateServerAddress(InetSocketAddress addr) {
        this.serverAddress = addr;
    }

    public ResourceFactory getResourceFactory() {
        return resourceFactory;
    }

    public SSLBuilder getSSLBuilder() {
        return sslBuilder;
    }

    public SSLContext getSSLContext() {
        return sslContext;
    }

    public int getMaxConns() {
        return maxConns;
    }

    public int getMaxHeader() {
        return maxHeader;
    }
    
    public int getMaxBody() {
        return maxBody;
    }

    public InetSocketAddress getServerAddress() {
        return serverAddress;
    }

    public long getServerStartTime() {
        return serverStartTime;
    }

    public String getNodeid() {
        return nodeid;
    }

    public Charset getCharset() {
        return charset;
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

        // 服务启动时间
        public long serverStartTime;

        // Application节点id
        public String nodeid;

        // Server的线程池
        public ExecutorService workExecutor;

        // SSL
        public SSLBuilder sslBuilder;

        // SSL
        public SSLContext sslContext;

        // ByteBuffer的容量，默认8K
        public int bufferCapacity;

        // 服务的根Servlet
        public DispatcherServlet dispatcher;

        // 服务的监听地址
        public InetSocketAddress serverAddress;

        // 字符集
        public Charset charset;

        // 请求头的大小上限, 默认16K
        public int maxHeader;

        // 请求内容的大小上限, 默认64K
        public int maxBody;

        // 最大连接数, 为0表示没限制
        public int maxConns;

        // keep alive IO读取的超时时间
        public int aliveTimeoutSeconds;

        // IO读取的超时时间
        public int readTimeoutSeconds;

        // IO写入的超时时间
        public int writeTimeoutSeconds;

        // 日志Logger
        public Logger logger;

        // 依赖注入工厂类
        public ResourceFactory resourceFactory;

        @Override
        public String toString() {
            return JsonConvert.root().convertTo(this);
        }
    }
}
