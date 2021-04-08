/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.client;

import java.net.SocketAddress;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import org.redkale.net.*;
import org.redkale.util.*;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.3.0
 *
 * @param <R> 请求对象
 * @param <P> 响应对象
 */
public abstract class Client<R extends ClientRequest, P> {

    protected final AsyncGroup group; //连接构造器

    protected final boolean tcp; //是否TCP协议

    protected final SocketAddress address;  //连接的地址

    protected final ConcurrentLinkedDeque<CompletableFuture<ClientConnection>> connQueue = new ConcurrentLinkedDeque();

    protected final Creator<ClientCodec<R, P>> codecCreator;

    protected final ScheduledThreadPoolExecutor timeoutScheduler;

    protected final AtomicLong writeReqCounter = new AtomicLong();

    protected final AtomicLong pollRespCounter = new AtomicLong();

    protected ScheduledFuture timeoutFuture;

    protected ClientConnection<R, P>[] connArray;  //连接池

    protected AtomicInteger[] connResps;  //连接当前处理数

    protected AtomicBoolean[] connFlags; //conns的标记组，当conn不存在或closed状态，标记为false

    protected int connLimit = Runtime.getRuntime().availableProcessors();  //最大连接数

    protected int maxPipelines = 16; //单个连接最大并行处理数

    protected int readTimeoutSeconds;

    protected int writeTimeoutSeconds;

    //------------------ 可选项 ------------------
    //PING心跳的请求数据，为null且pingInterval<1表示不需要定时ping
    protected R pingRequest;

    //关闭请求的数据， 为null表示直接关闭
    protected R closeRequest;

    //创建连接后进行的登录鉴权操作
    protected Function<CompletableFuture<ClientConnection>, CompletableFuture<ClientConnection>> authenticate;

    protected Client(AsyncGroup group, SocketAddress address, Creator<ClientCodec<R, P>> responseCreator) {
        this(group, true, address, Runtime.getRuntime().availableProcessors(), 16, responseCreator, null, null, null);
    }

    protected Client(AsyncGroup group, boolean tcp, SocketAddress address, Creator<ClientCodec<R, P>> codecCreator) {
        this(group, tcp, address, Runtime.getRuntime().availableProcessors(), 16, codecCreator, null, null, null);
    }

    protected Client(AsyncGroup group, boolean tcp, SocketAddress address, int maxconns, Creator<ClientCodec<R, P>> codecCreator) {
        this(group, tcp, address, maxconns, 16, codecCreator, null, null, null);
    }

    protected Client(AsyncGroup group, boolean tcp, SocketAddress address, int maxconns, int maxPipelines, Creator<ClientCodec<R, P>> codecCreator) {
        this(group, tcp, address, maxconns, maxPipelines, codecCreator, null, null, null);
    }

    protected Client(AsyncGroup group, boolean tcp, SocketAddress address, int maxconns, Creator<ClientCodec<R, P>> codecCreator,
        Function<CompletableFuture<ClientConnection>, CompletableFuture<ClientConnection>> authenticate) {
        this(group, tcp, address, maxconns, 16, codecCreator, null, null, authenticate);
    }

    protected Client(AsyncGroup group, boolean tcp, SocketAddress address, int maxconns, Creator<ClientCodec<R, P>> codecCreator,
        R closeRequest, Function<CompletableFuture<ClientConnection>, CompletableFuture<ClientConnection>> authenticate) {
        this(group, tcp, address, maxconns, 16, codecCreator, null, closeRequest, authenticate);
    }

    protected Client(AsyncGroup group, boolean tcp, SocketAddress address, int maxconns,
        int maxPipelines, Creator<ClientCodec<R, P>> codecCreator, R pingRequest, R closeRequest,
        Function<CompletableFuture<ClientConnection>, CompletableFuture<ClientConnection>> authenticate) {
        if (maxPipelines < 1) throw new IllegalArgumentException("maxPipelines must bigger 0");
        this.group = group;
        this.tcp = tcp;
        this.address = address;
        this.connLimit = maxconns;
        this.maxPipelines = maxPipelines;
        this.pingRequest = pingRequest;
        this.closeRequest = closeRequest;
        this.codecCreator = codecCreator;
        this.authenticate = authenticate;
        this.connArray = new ClientConnection[connLimit];
        this.connFlags = new AtomicBoolean[connLimit];
        this.connResps = new AtomicInteger[connLimit];
        for (int i = 0; i < connFlags.length; i++) {
            this.connFlags[i] = new AtomicBoolean();
            this.connResps[i] = new AtomicInteger();
        }
        //timeoutScheduler 不仅仅给超时用， 还给write用
        this.timeoutScheduler = new ScheduledThreadPoolExecutor(1, (Runnable r) -> {
            final Thread t = new Thread(r, "Redkale-" + Client.this.getClass().getSimpleName() + "-Interval-Thread");
            t.setDaemon(true);
            return t;
        });
        if (pingRequest != null) {
            this.timeoutFuture = this.timeoutScheduler.scheduleAtFixedRate(() -> {
                try {
                    ClientRequest req = pingRequest;
                    if (req == null) { //可能运行中进行重新赋值
                        timeoutFuture.cancel(true);
                        timeoutFuture = null;
                        return;
                    }
                    long now = System.currentTimeMillis();
                    for (ClientConnection conn : this.connArray) {
                        if (conn == null) continue;
                        if (now - conn.getLastWriteTime() > 10_000) conn.writeChannel(req);
                    }
                } catch (Throwable t) {
                }
            }, 10, 10, TimeUnit.SECONDS);
        }
    }

    public void close() {
        this.timeoutScheduler.shutdownNow();
        final R closereq = closeRequest;
        for (ClientConnection conn : this.connArray) {
            if (conn == null) continue;
            if (closereq == null) {
                conn.dispose(null);
            } else {
                try {
                    conn.writeChannel(closereq).get(100, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                }
                conn.dispose(null);
            }
        }
    }

    public CompletableFuture<P> sendAsync(R request) {
        return connect().thenCompose(conn -> conn.writeChannel(request));
    }

    protected CompletableFuture<P> writeChannel(ClientConnection conn, R request) {
        return conn.writeChannel(request);
    }

    protected CompletableFuture<ClientConnection> connect() {
        ClientConnection minRunningConn = null;
        for (int i = 0; i < this.connArray.length; i++) {
            final int index = i;
            final ClientConnection conn = this.connArray[index];
            if (conn == null || !conn.isOpen()) {
                if (this.connFlags[index].compareAndSet(false, true)) {
                    CompletableFuture<ClientConnection> future = group.create(tcp, address, readTimeoutSeconds, writeTimeoutSeconds).thenApply(c -> createClientConnection(index, c));
                    return (authenticate == null ? future : authenticate.apply(future)).thenApply(c -> {
                        c.authenticated = true;
                        this.connArray[index] = c;
                        return c;
                    }).whenComplete((r, t) -> {
                        if (t != null) this.connFlags[index].set(false);
                    });
                }
            } else if (conn.runningCount() < 1) {
                return CompletableFuture.completedFuture(conn);
            } else if (minRunningConn == null || minRunningConn.runningCount() > conn.runningCount()) {
                minRunningConn = conn;
            }
        }
        if (minRunningConn != null && minRunningConn.runningCount() < maxPipelines) return CompletableFuture.completedFuture(minRunningConn);
        return waitClientConnection();
    }

    protected CompletableFuture<ClientConnection> waitClientConnection() {
        CompletableFuture rs = Utility.orTimeout(new CompletableFuture(), 6, TimeUnit.SECONDS);
        connQueue.offer(rs);
        return rs;
    }

    protected ClientConnection createClientConnection(final int index, AsyncConnection channel) {
        return new ClientConnection(this, index, channel);
    }

    public int getReadTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    public void setReadTimeoutSeconds(int readTimeoutSeconds) {
        this.readTimeoutSeconds = readTimeoutSeconds;
    }

    public int getWriteTimeoutSeconds() {
        return writeTimeoutSeconds;
    }

    public void setWriteTimeoutSeconds(int writeTimeoutSeconds) {
        this.writeTimeoutSeconds = writeTimeoutSeconds;
    }

}
