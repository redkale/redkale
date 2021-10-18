/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.client;

import java.net.SocketAddress;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.logging.*;
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

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    protected final boolean finest = logger.isLoggable(Level.FINEST);

    protected final AsyncGroup group; //连接构造器

    protected final boolean tcp; //是否TCP协议

    protected final SocketAddress address;  //连接的地址

    protected final Creator<ClientCodec<R, P>> codecCreator;

    protected final ScheduledThreadPoolExecutor timeoutScheduler;

    protected final LongAdder writeReqCounter = new LongAdder();

    protected final LongAdder pollRespCounter = new LongAdder();

    private final AtomicInteger connSeqno = new AtomicInteger();

    private boolean closed;

    protected ScheduledFuture timeoutFuture;

    protected ClientConnection<R, P>[] connArray;  //连接池

    protected LongAdder[] connResps;  //连接当前处理数

    protected AtomicBoolean[] connOpens; //conns的标记组，当conn不存在或closed状态，标记为false

    protected final Queue<CompletableFuture<ClientConnection>>[] connWaits;  //连接等待池

    protected int connLimit = Utility.cpus();  //最大连接数

    protected int maxPipelines = 16; //单个连接最大并行处理数

    protected int readTimeoutSeconds;

    protected int writeTimeoutSeconds;

    protected String connectionContextName;

    //------------------ 可选项 ------------------
    //PING心跳的请求数据，为null且pingInterval<1表示不需要定时ping
    protected R pingRequest;

    //关闭请求的数据， 为null表示直接关闭
    protected R closeRequest;

    //创建连接后进行的登录鉴权操作
    protected Function<CompletableFuture<ClientConnection>, CompletableFuture<ClientConnection>> authenticate;

    protected Client(AsyncGroup group, SocketAddress address, Creator<ClientCodec<R, P>> responseCreator) {
        this(group, true, address, Utility.cpus(), 16, responseCreator, null, null, null);
    }

    protected Client(AsyncGroup group, boolean tcp, SocketAddress address, Creator<ClientCodec<R, P>> codecCreator) {
        this(group, tcp, address, Utility.cpus(), 16, codecCreator, null, null, null);
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

    @SuppressWarnings("OverridableMethodCallInConstructor")
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
        this.connOpens = new AtomicBoolean[connLimit];
        this.connResps = new LongAdder[connLimit];
        this.connWaits = new Queue[connLimit];
        for (int i = 0; i < connOpens.length; i++) {
            this.connOpens[i] = new AtomicBoolean();
            this.connResps[i] = new LongAdder();
            this.connWaits[i] = Utility.unsafe() != null ? new MpscGrowableArrayQueue<>(16, 1 << 10) : new ConcurrentLinkedDeque();
        }
        //timeoutScheduler 不仅仅给超时用， 还给write用
        this.timeoutScheduler = new ScheduledThreadPoolExecutor(1, (Runnable r) -> {
            final Thread t = new Thread(r, "Redkale-" + Client.this.getClass().getSimpleName() + "-Interval-Thread");
            t.setDaemon(true);
            return t;
        });
        if (pingRequest != null && this.timeoutFuture == null) {
            this.timeoutFuture = this.timeoutScheduler.scheduleAtFixedRate(() -> {
                try {
                    R req = pingRequest;
                    if (req == null) { //可能运行中进行重新赋值
                        timeoutFuture.cancel(true);
                        timeoutFuture = null;
                        return;
                    }
                    long now = System.currentTimeMillis();
                    for (ClientConnection<R, P> conn : this.connArray) {
                        if (conn == null) continue;
                        if (now - conn.getLastWriteTime() < 10_000) continue;
                        conn.writeChannel(req).thenAccept(p -> handlePingResult(conn, p));
                    }
                } catch (Throwable t) {
                }
            }, pingInterval(), pingInterval(), TimeUnit.SECONDS);
        }
    }

    protected int pingInterval() {
        return 30;
    }

    protected void handlePingResult(ClientConnection conn, P result) {
    }

    public synchronized void close() {
        if (this.closed) return;
        this.timeoutScheduler.shutdownNow();
        final R closereq = closeRequest;
        for (ClientConnection conn : this.connArray) {
            if (conn == null) continue;
            if (closereq == null) {
                conn.dispose(null);
            } else {
                try {
                    conn.writeChannel(closereq).get(1, TimeUnit.SECONDS);
                } catch (Exception e) {
                }
                conn.dispose(null);
            }
        }
        group.close();
        this.closed = true;
    }

    public final CompletableFuture<P> sendAsync(R request) {
        if (request.workThread == null) request.workThread = WorkThread.currWorkThread();
        return connect(null).thenCompose(conn -> writeChannel(conn, request));
    }

    public final CompletableFuture<P> sendAsync(ChannelContext context, R request) {
        if (request.workThread == null) request.workThread = WorkThread.currWorkThread();
        return connect(context).thenCompose(conn -> writeChannel(conn, request));
    }

    protected CompletableFuture<P> writeChannel(ClientConnection conn, R request) {
        return conn.writeChannel(request);
    }

    protected CompletableFuture<ClientConnection> connect() {
        return connect(null);
    }

    protected CompletableFuture<ClientConnection> connect(final ChannelContext context) {
        final boolean cflag = context != null && connectionContextName != null;
        if (cflag) {
            ClientConnection cc = context.getAttribute(connectionContextName);
            if (cc != null && cc.isOpen()) return CompletableFuture.completedFuture(cc);

        }
        int connIndex = -1;
        final int size = this.connArray.length;
        WorkThread workThread = WorkThread.currWorkThread();
        if (workThread != null && workThread.threads() == size) {
            connIndex = workThread.index();
        }
        if (connIndex >= 0) {
            ClientConnection cc = this.connArray[connIndex];
            if (cc != null && cc.isOpen()) {
                if (cflag) context.setAttribute(connectionContextName, cc);
                return CompletableFuture.completedFuture(cc);
            }
            final int index = connIndex;
            final Queue<CompletableFuture<ClientConnection>> waitQueue = this.connWaits[index];
            if (this.connOpens[index].compareAndSet(false, true)) {
                CompletableFuture<ClientConnection> future = group.createClient(tcp, address, readTimeoutSeconds, writeTimeoutSeconds)
                    .thenApply(c -> createClientConnection(index, c).setMaxPipelines(maxPipelines));
                return (authenticate == null ? future : authenticate.apply(future)).thenApply(c -> {
                    c.authenticated = true;
                    this.connArray[index] = c;
                    CompletableFuture<ClientConnection> f;
                    if (cflag) context.setAttribute(connectionContextName, c);
                    while ((f = waitQueue.poll()) != null) {
                        f.complete(c);
                    }
                    return c;
                }).whenComplete((r, t) -> {
                    if (t != null) this.connOpens[index].set(false);
                });
            } else {
                CompletableFuture rs = Utility.orTimeout(new CompletableFuture(), 6, TimeUnit.SECONDS);
                waitQueue.offer(rs);
                return rs;
            }
        }
        ClientConnection minRunningConn = null;
        for (int i = 0; i < size; i++) {
            final int index = i;
            final ClientConnection conn = this.connArray[index];
            if (conn == null || !conn.isOpen()) {
                if (this.connOpens[index].compareAndSet(false, true)) {
                    CompletableFuture<ClientConnection> future = group.createClient(tcp, address, readTimeoutSeconds, writeTimeoutSeconds)
                        .thenApply(c -> createClientConnection(index, c).setMaxPipelines(maxPipelines));
                    return (authenticate == null ? future : authenticate.apply(future)).thenApply(c -> {
                        c.authenticated = true;
                        this.connArray[index] = c;
                        return c;
                    }).whenComplete((r, t) -> {
                        if (t != null) this.connOpens[index].set(false);
                    });
                }
            } else if (conn.runningCount() < 1) {
                return CompletableFuture.completedFuture(conn);
            } else if (minRunningConn == null || minRunningConn.runningCount() > conn.runningCount()) {
                minRunningConn = conn;
            }
        }
        if (minRunningConn != null && minRunningConn.runningCount() < maxPipelines) {
            ClientConnection minConn = minRunningConn;
            return CompletableFuture.completedFuture(minConn);
        }
        return waitClientConnection();
    }

    protected CompletableFuture<ClientConnection> waitClientConnection() {
        CompletableFuture rs = Utility.orTimeout(new CompletableFuture(), 6, TimeUnit.SECONDS);
        connWaits[connSeqno.getAndIncrement() % this.connLimit].offer(rs);
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
