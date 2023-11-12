/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.client;

import java.net.SocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.logging.Logger;
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
 * @param <C> 连接对象
 * @param <R> 请求对象
 * @param <P> 响应对象
 */
public abstract class Client<C extends ClientConnection<R, P>, R extends ClientRequest, P extends ClientResult> implements Resourcable {

    public static final int DEFAULT_MAX_PIPELINES = 128;

    protected boolean debug = false;

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    protected final String name;

    protected final AsyncGroup group; //连接构造器

    protected final boolean tcp; //是否TCP协议

    protected final ClientAddress address;  //连接的地址

    protected final ScheduledThreadPoolExecutor timeoutScheduler;

    //结合ClientRequest.isCompleted()使用
    //使用场景：批量request提交时，后面的request需响应上一个request返回值来构建
    //例如： MySQL批量提交PrepareSQL场景
    protected final LongAdder reqWritedCounter = new LongAdder();

    protected final LongAdder respDoneCounter = new LongAdder();

    private final AtomicBoolean closed = new AtomicBoolean();

    protected ScheduledFuture timeoutFuture;

    //连随机地址模式
    final AtomicLong connIndexSeq = new AtomicLong();

    //连随机地址模式
    final ClientConnection<R, P>[] connArray;  //连接池

    //连随机地址模式
    final LongAdder[] connRespWaitings;  //连接当前处理数

    //连随机地址模式
    final AtomicBoolean[] connOpenStates; //conns的标记组，当conn不存在或closed状态，标记为false

    //连随机地址模式
    final int connLimit;  //最大连接数

    //连随机地址模式
    private final Queue<CompletableFuture<C>>[] connAcquireWaitings;  //连接等待池

    //连指定地址模式
    final ConcurrentHashMap<SocketAddress, AddressConnEntry> connAddrEntrys = new ConcurrentHashMap<>();

    protected int maxPipelines = DEFAULT_MAX_PIPELINES; //单个连接最大并行处理数

    protected int readTimeoutSeconds;

    protected int writeTimeoutSeconds;

    //------------------ 可选项 ------------------
    //PING心跳的请求数据，为null且pingInterval<1表示不需要定时ping
    protected Supplier<R> pingRequestSupplier;

    //关闭请求的数据， 为null表示直接关闭
    protected Supplier<R> closeRequestSupplier;

    //创建连接后进行的登录鉴权操作
    protected Function<String, Function<C, CompletableFuture<C>>> authenticate;

    protected Client(String name, AsyncGroup group, ClientAddress address) {
        this(name, group, true, address, Utility.cpus(), DEFAULT_MAX_PIPELINES, null, null, null);
    }

    protected Client(String name, AsyncGroup group, boolean tcp, ClientAddress address) {
        this(name, group, tcp, address, Utility.cpus(), DEFAULT_MAX_PIPELINES, null, null, null);
    }

    protected Client(String name, AsyncGroup group, boolean tcp, ClientAddress address, int maxConns) {
        this(name, group, tcp, address, maxConns, DEFAULT_MAX_PIPELINES, null, null, null);
    }

    protected Client(String name, AsyncGroup group, boolean tcp, ClientAddress address, int maxConns, int maxPipelines) {
        this(name, group, tcp, address, maxConns, maxPipelines, null, null, null);
    }

    protected Client(String name, AsyncGroup group, boolean tcp, ClientAddress address, int maxConns,
        Function<String, Function<C, CompletableFuture<C>>> authenticate) {
        this(name, group, tcp, address, maxConns, DEFAULT_MAX_PIPELINES, null, null, authenticate);
    }

    protected Client(String name, AsyncGroup group, boolean tcp, ClientAddress address, int maxConns,
        Supplier<R> closeRequestSupplier, Function<String, Function<C, CompletableFuture<C>>> authenticate) {
        this(name, group, tcp, address, maxConns, DEFAULT_MAX_PIPELINES, null, closeRequestSupplier, authenticate);
    }

    @SuppressWarnings("OverridableMethodCallInConstructor")
    protected Client(String name, AsyncGroup group, boolean tcp, ClientAddress address, int maxConns,
        int maxPipelines, Supplier<R> pingRequestSupplier, Supplier<R> closeRequestSupplier, Function<String, Function<C, CompletableFuture<C>>> authenticate) {
        if (maxPipelines < 1) {
            throw new IllegalArgumentException("maxPipelines must bigger 0");
        }
        address.checkValid();
        this.name = name;
        this.group = group;
        this.tcp = tcp;
        this.address = address;
        this.connLimit = maxConns;
        this.maxPipelines = maxPipelines;
        this.pingRequestSupplier = pingRequestSupplier;
        this.closeRequestSupplier = closeRequestSupplier;
        this.authenticate = authenticate;
        this.connArray = new ClientConnection[connLimit];
        this.connOpenStates = new AtomicBoolean[connLimit];
        this.connRespWaitings = new LongAdder[connLimit];
        this.connAcquireWaitings = new Queue[connLimit];
        for (int i = 0; i < connOpenStates.length; i++) {
            this.connOpenStates[i] = new AtomicBoolean();
            this.connRespWaitings[i] = new LongAdder();
            this.connAcquireWaitings[i] = new ConcurrentLinkedDeque();
        }
        //timeoutScheduler 不仅仅给超时用， 还给write用
        this.timeoutScheduler = new ScheduledThreadPoolExecutor(1, (Runnable r) -> {
            final Thread t = new Thread(r, "Redkale-" + Client.this.getClass().getSimpleName() + "-" + resourceName() + "-Timeout-Thread");
            t.setDaemon(true);
            return t;
        });
        if (pingRequestSupplier != null && this.timeoutFuture == null) {
            this.timeoutFuture = this.timeoutScheduler.scheduleAtFixedRate(() -> {
                try {
                    R req = pingRequestSupplier.get();
                    if (req == null) { //可能运行中进行重新赋值
                        timeoutFuture.cancel(true);
                        timeoutFuture = null;
                        return;
                    }
                    long now = System.currentTimeMillis();
                    for (ClientConnection<R, P> conn : this.connArray) {
                        if (conn == null) {
                            continue;
                        }
                        if (now - conn.getLastWriteTime() < 10_000) {
                            continue;
                        }
                        conn.writeChannel(req).thenAccept(p -> handlePingResult((C) conn, p));
                    }
                } catch (Throwable t) {
                    //do nothing
                }
            }, pingIntervalSeconds(), pingIntervalSeconds(), TimeUnit.SECONDS);
        }
    }

    protected abstract C createClientConnection(final int index, AsyncConnection channel);

    //创建连接后会立马从服务器拉取数据构建的虚拟请求，返回null表示连上服务器后不会立马读取数据
    protected R createVirtualRequestAfterConnect() {
        return null;
    }

    protected int pingIntervalSeconds() {
        return 30;
    }

    protected void handlePingResult(C conn, P result) {
    }

    public void close() {
        if (closed.compareAndSet(false, true)) {
            this.timeoutScheduler.shutdownNow();
            for (ClientConnection conn : this.connArray) {
                closeConnection(conn);
            }
            for (AddressConnEntry<C> entry : this.connAddrEntrys.values()) {
                closeConnection(entry.connection);
            }
            this.connAddrEntrys.clear();
            group.close();
        }
    }

    private void closeConnection(ClientConnection conn) {
        if (conn == null) {
            return;
        }
        final R closeReq = closeRequestSupplier == null ? null : closeRequestSupplier.get();
        if (closeReq == null) {
            conn.dispose(null);
        } else {
            try {
                conn.writeChannel(closeReq).get(1, TimeUnit.SECONDS);
            } catch (Exception e) {
                //do nothing
            }
            conn.dispose(null);
        }
    }

    public final CompletableFuture<P> sendAsync(R request) {
        request.traceid = Traces.computeIfAbsent(request.traceid, Traces.currentTraceid());
        if (request.workThread == null) {
            request.workThread = WorkThread.currentWorkThread();
        }
        return connect().thenCompose(conn -> writeChannel(conn, request));
    }

    public final <T> CompletableFuture<T> sendAsync(R request, Function<P, T> respTransfer) {
        request.traceid = Traces.computeIfAbsent(request.traceid, Traces.currentTraceid());
        if (request.workThread == null) {
            request.workThread = WorkThread.currentWorkThread();
        }
        return connect().thenCompose(conn -> writeChannel(conn, request, respTransfer));
    }

    public final CompletableFuture<P> sendAsync(SocketAddress addr, R request) {
        request.traceid = Traces.computeIfAbsent(request.traceid, Traces.currentTraceid());
        if (request.workThread == null) {
            request.workThread = WorkThread.currentWorkThread();
        }
        return connect(addr).thenCompose(conn -> writeChannel(conn, request));
    }

    public final <T> CompletableFuture<T> sendAsync(SocketAddress addr, R request, Function<P, T> respTransfer) {
        request.traceid = Traces.computeIfAbsent(request.traceid, Traces.currentTraceid());
        if (request.workThread == null) {
            request.workThread = WorkThread.currentWorkThread();
        }
        return connect(addr).thenCompose(conn -> writeChannel(conn, request, respTransfer));
    }

    protected CompletableFuture<P> writeChannel(ClientConnection conn, R request) {
        return conn.writeChannel(request);
    }

    protected <T> CompletableFuture<T> writeChannel(ClientConnection conn, R request, Function<P, T> respTransfer) {
        return conn.writeChannel(request, respTransfer);
    }

    public final CompletableFuture<List<P>> sendAsync(R[] requests) {
        requests[0].traceid = Traces.computeIfAbsent(requests[0].traceid, Traces.currentTraceid());
        for (R request : requests) {
            if (request.workThread == null) {
                request.workThread = WorkThread.currentWorkThread();
            }
        }
        return connect().thenCompose(conn -> writeChannel(conn, requests));
    }

    public final <T> CompletableFuture<List<T>> sendAsync(R[] requests, Function<P, T> respTransfer) {
        requests[0].traceid = Traces.computeIfAbsent(requests[0].traceid, Traces.currentTraceid());
        for (R request : requests) {
            if (request.workThread == null) {
                request.workThread = WorkThread.currentWorkThread();
            }
        }
        return connect().thenCompose(conn -> writeChannel(conn, requests, respTransfer));
    }

    public final CompletableFuture<List<P>> sendAsync(SocketAddress addr, R[] requests) {
        requests[0].traceid = Traces.computeIfAbsent(requests[0].traceid, Traces.currentTraceid());
        for (R request : requests) {
            if (request.workThread == null) {
                request.workThread = WorkThread.currentWorkThread();
            }
        }
        return connect(addr).thenCompose(conn -> writeChannel(conn, requests));
    }

    public final <T> CompletableFuture<List<T>> sendAsync(SocketAddress addr, R[] requests, Function<P, T> respTransfer) {
        requests[0].traceid = Traces.computeIfAbsent(requests[0].traceid, Traces.currentTraceid());
        for (R request : requests) {
            if (request.workThread == null) {
                request.workThread = WorkThread.currentWorkThread();
            }
        }
        return connect(addr).thenCompose(conn -> writeChannel(conn, requests, respTransfer));
    }

    protected CompletableFuture<List<P>> writeChannelBatch(ClientConnection conn, R... requests) {
        requests[0].traceid = Traces.computeIfAbsent(requests[0].traceid, Traces.currentTraceid());
        return conn.writeChannel(requests);
    }

    protected CompletableFuture<List<P>> writeChannel(ClientConnection conn, R[] requests) {
        return conn.writeChannel(requests);
    }

    protected <T> CompletableFuture<List<T>> writeChannel(ClientConnection conn, R[] requests, Function<P, T> respTransfer) {
        return conn.writeChannel(requests, respTransfer);
    }

    public final CompletableFuture<C> connect() {
        return connect(true);
    }

    public final CompletableFuture<C> newConnection() {
        return connect(false);
    }

    private CompletableFuture<C> connect(final boolean pool) {
        final String traceid = Traces.currentTraceid();
        final int size = this.connArray.length;
        WorkThread workThread = WorkThread.currentWorkThread();
        final int connIndex = (workThread != null && workThread.threads() == size) ? workThread.index() : (int) Math.abs(connIndexSeq.getAndIncrement()) % size;
        C cc = (C) this.connArray[connIndex];
        if (pool && cc != null && cc.isOpen()) {
            return CompletableFuture.completedFuture(cc);
        }
        long s = System.currentTimeMillis();
        final Queue<CompletableFuture<C>> waitQueue = this.connAcquireWaitings[connIndex];
        if (!pool || this.connOpenStates[connIndex].compareAndSet(false, true)) {
            CompletableFuture<C> future = group.createClient(tcp, this.address.randomAddress(), readTimeoutSeconds, writeTimeoutSeconds)
                .thenApply(c -> {
                    Traces.currentTraceid(traceid);
                    C rs = (C) createClientConnection(connIndex, c).setMaxPipelines(maxPipelines);
//                    if (debug) {
//                        logger.log(Level.FINEST, Utility.nowMillis() + ": " + Thread.currentThread().getName() + ": " + rs
//                            + ", " + rs.channel + ", 创建TCP连接耗时: (" + (System.currentTimeMillis() - s) + "ms)");
//                    }
                    return rs;
                });
            R virtualReq = createVirtualRequestAfterConnect();
            if (virtualReq != null) {
                virtualReq.traceid = traceid;
                future = future.thenCompose(conn -> {
                    Traces.currentTraceid(traceid);
                    return conn.writeVirtualRequest(virtualReq).thenApply(v -> conn);
                });
            } else {
                future = future.thenApply(conn -> {
                    Traces.currentTraceid(traceid);
                    conn.channel.readRegister(conn.getCodec()); //不用readRegisterInIOThread，因executeRead可能会异步
                    return conn;
                });
            }
            if (authenticate != null) {
                future = future.thenCompose(authenticate.apply(traceid));
            }
            return future.thenApply(c -> {
                Traces.currentTraceid(traceid);
                c.setAuthenticated(true);
                if (pool) {
                    this.connArray[connIndex] = c;
                    CompletableFuture<C> f;
                    while ((f = waitQueue.poll()) != null) {
                        if (!f.isDone()) {
                            if (workThread != null) {
                                CompletableFuture<C> fs = f;
                                workThread.execute(() -> {
                                    Traces.currentTraceid(traceid);
                                    fs.complete(c);
                                });
                            } else {
                                f.complete(c);
                            }
                        }
                    }
                }
                return c;
            }).whenComplete((r, t) -> {
                if (pool && t != null) {
                    this.connOpenStates[connIndex].set(false);
                }
            });
        } else {
            CompletableFuture rs = Utility.orTimeout(new CompletableFuture(), readTimeoutSeconds, TimeUnit.SECONDS);
            waitQueue.offer(rs);
            return rs;
        }
    }

    //指定地址获取连接
    public final CompletableFuture<C> connect(final SocketAddress addr) {
        return connect(true, addr);
    }

    //指定地址获取连接
    public final CompletableFuture<C> newConnection(final SocketAddress addr) {
        return connect(false, addr);
    }

    //指定地址获取连接
    private CompletableFuture<C> connect(final boolean pool, final SocketAddress addr) {
        final String traceid = Traces.currentTraceid();
        if (addr == null) {
            return connect();
        }
        final AddressConnEntry<C> entry = connAddrEntrys.computeIfAbsent(addr, a -> new AddressConnEntry());
        C ec = entry.connection;
        if (pool && ec != null && ec.isOpen()) {
            return CompletableFuture.completedFuture(ec);
        }
        WorkThread workThread = WorkThread.currentWorkThread();
        final Queue<CompletableFuture<C>> waitQueue = entry.connAcquireWaitings;
        if (!pool || entry.connOpenState.compareAndSet(false, true)) {
            long s = System.currentTimeMillis();
            CompletableFuture<C> future = group.createClient(tcp, addr, readTimeoutSeconds, writeTimeoutSeconds)
                .thenApply(c -> (C) createClientConnection(-1, c).setMaxPipelines(maxPipelines));
            R virtualReq = createVirtualRequestAfterConnect();
            if (virtualReq != null) {
                virtualReq.traceid = traceid;
                future = future.thenCompose(conn -> {
                    Traces.currentTraceid(traceid);
                    return conn.writeVirtualRequest(virtualReq).thenApply(v -> conn);
                });
            } else {
                future = future.thenApply(conn -> {
//                    if (debug) {
//                        logger.log(Level.FINEST, Utility.nowMillis() + ": " + Thread.currentThread().getName() + ": " + conn
//                            + ", 注册读操作: (" + (System.currentTimeMillis() - s) + "ms) " + conn.channel);
//                    }
                    conn.channel.readRegister(conn.getCodec()); //不用readRegisterInIOThread，因executeRead可能会异步
                    return conn;
                });
            }
            if (authenticate != null) {
                future = future.thenCompose(authenticate.apply(traceid));
            }
            return future.thenApply(c -> {
                c.setAuthenticated(true);
                if (pool) {
                    entry.connection = c;
                    CompletableFuture<C> f;
                    Traces.currentTraceid(traceid);
                    while ((f = waitQueue.poll()) != null) {
                        if (!f.isDone()) {
                            if (workThread != null) {
                                CompletableFuture<C> fs = f;
                                workThread.execute(() -> {
                                    Traces.currentTraceid(traceid);
                                    fs.complete(c);
                                });
                            } else {
                                f.complete(c);
                            }
                        }
                    }
                }
                return c;
            }).whenComplete((r, t) -> {
                if (pool && t != null) {
                    entry.connOpenState.set(false);
                }
            });
        } else {
            CompletableFuture rs = Utility.orTimeout(new CompletableFuture(), 6, TimeUnit.SECONDS);
            waitQueue.offer(rs);
            return rs;
        }
    }

    protected long getRespWaitingCount() {
        long s = 0;
        for (LongAdder a : connRespWaitings) {
            s += a.longValue();
        }
        return s;
    }

    protected void incrReqWritedCounter() {
        reqWritedCounter.increment();
    }

    protected void incrRespDoneCounter() {
        respDoneCounter.increment();
    }

    @Override
    public String resourceName() {
        return name;
    }

    public String getName() {
        return name;
    }

    public int getMaxConns() {
        return connLimit;
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

    protected static class AddressConnEntry<C> {

        public C connection;

        public final LongAdder connRespWaiting = new LongAdder();

        public final AtomicBoolean connOpenState = new AtomicBoolean();

        public final Queue<CompletableFuture<C>> connAcquireWaitings = new ConcurrentLinkedDeque();

        AddressConnEntry() {
        }

    }
}
