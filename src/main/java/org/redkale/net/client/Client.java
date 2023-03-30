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
public abstract class Client<C extends ClientConnection<R, P>, R extends ClientRequest, P> implements Resourcable {

    public static final int DEFAULT_MAX_PIPELINES = 128;

    protected boolean debug;

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
    //------------------ LocalThreadMode模式 ------------------

    final ThreadLocal<C> localConnection = new ThreadLocal();

    //------------------ 可选项 ------------------
    //PING心跳的请求数据，为null且pingInterval<1表示不需要定时ping
    protected Supplier<R> pingRequestSupplier;

    //关闭请求的数据， 为null表示直接关闭
    protected Supplier<R> closeRequestSupplier;

    //创建连接后进行的登录鉴权操作
    protected Function<C, CompletableFuture<C>> authenticate;

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
        Function<C, CompletableFuture<C>> authenticate) {
        this(name, group, tcp, address, maxConns, DEFAULT_MAX_PIPELINES, null, null, authenticate);
    }

    protected Client(String name, AsyncGroup group, boolean tcp, ClientAddress address, int maxConns,
        Supplier<R> closeRequestSupplier, Function<C, CompletableFuture<C>> authenticate) {
        this(name, group, tcp, address, maxConns, DEFAULT_MAX_PIPELINES, null, closeRequestSupplier, authenticate);
    }

    @SuppressWarnings("OverridableMethodCallInConstructor")
    protected Client(String name, AsyncGroup group, boolean tcp, ClientAddress address, int maxConns,
        int maxPipelines, Supplier<R> pingRequestSupplier, Supplier<R> closeRequestSupplier, Function<C, CompletableFuture<C>> authenticate) {
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
                }
            }, pingIntervalSeconds(), pingIntervalSeconds(), TimeUnit.SECONDS);
        }
    }

    protected abstract C createClientConnection(final int index, AsyncConnection channel);

    //创建连接后先从服务器拉取数据构建的虚拟请求，返回null表示连上服务器后不读取数据
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
                if (conn == null) {
                    continue;
                }
                final R closeReq = closeRequestSupplier == null ? null : closeRequestSupplier.get();
                if (closeReq == null) {
                    conn.dispose(null);
                } else {
                    try {
                        conn.writeChannel(closeReq).get(1, TimeUnit.SECONDS);
                    } catch (Exception e) {
                    }
                    conn.dispose(null);
                }
            }
            for (AddressConnEntry<C> entry : this.connAddrEntrys.values()) {
                ClientConnection conn = entry.connection;
                if (conn == null) {
                    continue;
                }
                final R closeReq = closeRequestSupplier == null ? null : closeRequestSupplier.get();
                if (closeReq == null) {
                    conn.dispose(null);
                } else {
                    try {
                        conn.writeChannel(closeReq).get(1, TimeUnit.SECONDS);
                    } catch (Exception e) {
                    }
                    conn.dispose(null);
                }
            }
            this.connAddrEntrys.clear();
            group.close();
        }
    }

    public final CompletableFuture<P> sendAsync(R request) {
        if (request.workThread == null) {
            request.workThread = WorkThread.currWorkThread();
        }
        return connect().thenCompose(conn -> writeChannel(conn, request));
    }

    public final <T> CompletableFuture<T> sendAsync(R request, Function<P, T> respTransfer) {
        if (request.workThread == null) {
            request.workThread = WorkThread.currWorkThread();
        }
        return connect().thenCompose(conn -> writeChannel(conn, request, respTransfer));
    }

    public final CompletableFuture<P> sendAsync(SocketAddress addr, R request) {
        if (request.workThread == null) {
            request.workThread = WorkThread.currWorkThread();
        }
        return connect(addr).thenCompose(conn -> writeChannel(conn, request));
    }

    public final <T> CompletableFuture<T> sendAsync(SocketAddress addr, R request, Function<P, T> respTransfer) {
        if (request.workThread == null) {
            request.workThread = WorkThread.currWorkThread();
        }
        return connect(addr).thenCompose(conn -> writeChannel(conn, request, respTransfer));
    }

    protected CompletableFuture<P> writeChannel(ClientConnection conn, R request) {
        return conn.writeChannel(request);
    }

    protected <T> CompletableFuture<T> writeChannel(ClientConnection conn, R request, Function<P, T> respTransfer) {
        return conn.writeChannel(request, respTransfer);
    }

    //是否采用ThreadLocal连接池模式
    //支持ThreadLocal连接池模式的最基本要求: 
    //    1) 只能调用connect()获取连接，不能调用connect(SocketAddress addr)
    //    2) request必须一次性输出，不能出现写入request后request.isCompleted()=false的情况
    protected boolean isThreadLocalConnMode() {
        return false;
    }

    private C createConnection(int index, AsyncConnection channel) {
        C conn = createClientConnection(index, channel);
        if (!channel.isReadPending()) {
            channel.readRegister(conn.getCodec()); //不用readRegisterInIOThread，因executeRead可能会异步
        }
        return conn;
    }

    protected CompletableFuture<C> connect() {
        if (isThreadLocalConnMode()) {
            C conn = localConnection.get();
            if (conn == null || !conn.isOpen()) {
                try {
                    conn = connect1();
                } catch (Exception e) {
                    return CompletableFuture.failedFuture(e);
                }
                localConnection.set(conn);
            }
            return CompletableFuture.completedFuture(conn);
        } else {
            return connect0();
        }
    }

    protected C connect1() {
        CompletableFuture<C> future = group.createClient(tcp, this.address.randomAddress(), readTimeoutSeconds, writeTimeoutSeconds)
            .thenApply(c -> (C) createConnection(-2, c).setMaxPipelines(maxPipelines));
        R virtualReq = createVirtualRequestAfterConnect();
        if (virtualReq != null) {
            future = future.thenCompose(conn -> conn.writeVirtualRequest(virtualReq).thenApply(v -> conn));
        }
        if (authenticate != null) {
            future = future.thenCompose(authenticate);
        }
        return future.thenApply(c -> (C) c.setAuthenticated(true)).join();
    }

    protected CompletableFuture<C> connect0() {
        final int size = this.connArray.length;
        WorkThread workThread = WorkThread.currWorkThread();
        final int connIndex = (workThread != null && workThread.threads() == size) ? workThread.index() : (int) Math.abs(connIndexSeq.getAndIncrement()) % size;
        C cc = (C) this.connArray[connIndex];
        if (cc != null && cc.isOpen()) {
            return CompletableFuture.completedFuture(cc);
        }
        final Queue<CompletableFuture<C>> waitQueue = this.connAcquireWaitings[connIndex];
        if (this.connOpenStates[connIndex].compareAndSet(false, true)) {
            CompletableFuture<C> future = group.createClient(tcp, this.address.randomAddress(), readTimeoutSeconds, writeTimeoutSeconds)
                .thenApply(c -> (C) createConnection(connIndex, c).setMaxPipelines(maxPipelines));
            R virtualReq = createVirtualRequestAfterConnect();
            if (virtualReq != null) {
                future = future.thenCompose(conn -> conn.writeVirtualRequest(virtualReq).thenApply(v -> conn));
            }
            if (authenticate != null) {
                future = future.thenCompose(authenticate);
            }
            return future.thenApply(c -> {
                c.setAuthenticated(true);
                this.connArray[connIndex] = c;
                CompletableFuture<C> f;
                while ((f = waitQueue.poll()) != null) {
                    if (!f.isDone()) {
                        f.complete(c);
                    }
                }
                return c;
            }).whenComplete((r, t) -> {
                if (t != null) {
                    this.connOpenStates[connIndex].set(false);
                }
            });
        } else {
            CompletableFuture rs = Utility.orTimeout(new CompletableFuture(), 6, TimeUnit.SECONDS);
            waitQueue.offer(rs);
            return rs;
        }
    }

    //指定地址获取连接
    protected CompletableFuture<C> connect(final SocketAddress addr) {
        if (addr == null) {
            return connect();
        }
        final AddressConnEntry<C> entry = connAddrEntrys.computeIfAbsent(addr, a -> new AddressConnEntry());
        C ec = entry.connection;
        if (ec != null && ec.isOpen()) {
            return CompletableFuture.completedFuture(ec);
        }
        final Queue<CompletableFuture<C>> waitQueue = entry.connAcquireWaitings;
        if (entry.connOpenState.compareAndSet(false, true)) {
            CompletableFuture<C> future = group.createClient(tcp, addr, readTimeoutSeconds, writeTimeoutSeconds)
                .thenApply(c -> (C) createConnection(-1, c).setMaxPipelines(maxPipelines));
            R virtualReq = createVirtualRequestAfterConnect();
            if (virtualReq != null) {
                future = future.thenCompose(conn -> conn.writeVirtualRequest(virtualReq).thenApply(v -> conn));
            }
            if (authenticate != null) {
                future = future.thenCompose(authenticate);
            }
            return future.thenApply(c -> {
                c.setAuthenticated(true);
                entry.connection = c;
                CompletableFuture<C> f;
                while ((f = waitQueue.poll()) != null) {
                    if (!f.isDone()) {
                        f.complete(c);
                    }
                }
                return c;
            }).whenComplete((r, t) -> {
                if (t != null) {
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
