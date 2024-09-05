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
import org.redkale.annotation.Nonnull;
import org.redkale.annotation.Nullable;
import org.redkale.inject.Resourcable;
import org.redkale.net.*;
import org.redkale.util.*;

/**
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.3.0
 * @param <C> 连接对象
 * @param <R> 请求对象
 * @param <P> 响应对象
 */
public abstract class Client<C extends ClientConnection<R, P>, R extends ClientRequest, P extends ClientResult>
        implements Resourcable {

    public static final int DEFAULT_MAX_PIPELINES = 128;

    protected boolean debug = false;

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    protected final String name;

    protected final AsyncGroup group; // 连接构造器

    protected final boolean tcp; // 是否TCP协议

    protected final ScheduledThreadPoolExecutor timeoutScheduler;

    protected final Random random = new Random();

    // 结合ClientRequest.isCompleted()使用
    // 使用场景：批量request提交时，后面的request需响应上一个request返回值来构建
    // 例如： MySQL批量提交PrepareSQL场景
    protected final LongAdder reqWritedCounter = new LongAdder();

    protected final LongAdder respDoneCounter = new LongAdder();

    private final AtomicBoolean closed = new AtomicBoolean();

    // 不可protected、public
    private final ClientAddress address; // 连接的地址

    // 连随机地址模式
    private final int connLimit; // 最大连接数

    // 连指定地址模式
    private final ConcurrentHashMap<SocketAddress, AddressConnEntry[]> connAddrEntrys = new ConcurrentHashMap<>();

    protected ScheduledFuture timeoutFuture;

    protected int maxPipelines = DEFAULT_MAX_PIPELINES; // 单个连接最大并行处理数

    protected int connectTimeoutSeconds;

    protected int readTimeoutSeconds;

    protected int writeTimeoutSeconds;

    // ------------------ 可选项 ------------------
    // PING心跳的请求数据，为null且pingInterval<1表示不需要定时ping
    protected Supplier<R> pingRequestSupplier;

    // 关闭请求的数据， 为null表示直接关闭
    protected Supplier<R> closeRequestSupplier;

    // 创建连接后进行的登录鉴权操作
    protected BiFunction<WorkThread, String, Function<C, CompletableFuture<C>>> authenticate;

    protected Client(String name, AsyncGroup group, ClientAddress address) {
        this(name, group, true, address, Utility.cpus(), DEFAULT_MAX_PIPELINES, null, null, null);
    }

    protected Client(String name, AsyncGroup group, boolean tcp, ClientAddress address) {
        this(name, group, tcp, address, Utility.cpus(), DEFAULT_MAX_PIPELINES, null, null, null);
    }

    protected Client(String name, AsyncGroup group, boolean tcp, ClientAddress address, int maxConns) {
        this(name, group, tcp, address, maxConns, DEFAULT_MAX_PIPELINES, null, null, null);
    }

    protected Client(
            String name, AsyncGroup group, boolean tcp, ClientAddress address, int maxConns, int maxPipelines) {
        this(name, group, tcp, address, maxConns, maxPipelines, null, null, null);
    }

    protected Client(
            String name,
            AsyncGroup group,
            boolean tcp,
            ClientAddress address,
            int maxConns,
            BiFunction<WorkThread, String, Function<C, CompletableFuture<C>>> authenticate) {
        this(name, group, tcp, address, maxConns, DEFAULT_MAX_PIPELINES, null, null, authenticate);
    }

    protected Client(
            String name,
            AsyncGroup group,
            boolean tcp,
            ClientAddress address,
            int maxConns,
            Supplier<R> closeRequestSupplier,
            BiFunction<WorkThread, String, Function<C, CompletableFuture<C>>> authenticate) {
        this(name, group, tcp, address, maxConns, DEFAULT_MAX_PIPELINES, null, closeRequestSupplier, authenticate);
    }

    @SuppressWarnings("OverridableMethodCallInConstructor")
    protected Client(
            String name,
            AsyncGroup group,
            boolean tcp,
            ClientAddress address,
            int maxConns,
            int maxPipelines,
            Supplier<R> pingRequestSupplier,
            Supplier<R> closeRequestSupplier,
            BiFunction<WorkThread, String, Function<C, CompletableFuture<C>>> authenticate) {
        if (maxPipelines < 1) {
            throw new IllegalArgumentException("maxPipelines must bigger 0");
        }
        this.name = name;
        this.group = group;
        this.tcp = tcp;
        this.address = Objects.requireNonNull(address);
        this.connLimit = maxConns;
        this.maxPipelines = maxPipelines;
        this.pingRequestSupplier = pingRequestSupplier;
        this.closeRequestSupplier = closeRequestSupplier;
        this.authenticate = authenticate;
        // timeoutScheduler 不仅仅给超时用， 还给write用
        this.timeoutScheduler = new ScheduledThreadPoolExecutor(1, (Runnable r) -> {
            final Thread t = new Thread(
                    r, "Redkale-" + Client.this.getClass().getSimpleName() + "-" + resourceName() + "-Timeout-Thread");
            t.setDaemon(true);
            return t;
        });
        this.timeoutScheduler.setRemoveOnCancelPolicy(true);
        int pingSeconds = pingIntervalSeconds();
        if (pingRequestSupplier != null && pingSeconds > 0 && this.timeoutFuture == null) {
            this.timeoutFuture = this.timeoutScheduler.scheduleAtFixedRate(
                    () -> {
                        try {
                            R req = pingRequestSupplier.get();
                            if (req == null) { // 可能运行中进行重新赋值
                                if (timeoutFuture != null && !timeoutFuture.isDone()) {
                                    timeoutFuture.cancel(true);
                                    timeoutFuture = null;
                                }
                                return;
                            }
                            long now = System.currentTimeMillis();
                            for (AddressConnEntry<ClientConnection<R, P>>[] entrys : this.connAddrEntrys.values()) {
                                for (AddressConnEntry<ClientConnection<R, P>> entry : entrys) {
                                    if (entry == null) {
                                        continue;
                                    }
                                    ClientConnection<R, P> conn = entry.connection;
                                    if (conn == null) {
                                        continue;
                                    }
                                    if (now - conn.getLastWriteTime() < 10_000) {
                                        continue;
                                    }
                                    conn.writeChannel(req).thenAccept(p -> handlePingResult((C) conn, p));
                                }
                            }
                        } catch (Throwable t) {
                            // do nothing
                        }
                    },
                    pingSeconds,
                    pingSeconds,
                    TimeUnit.SECONDS);
        }
    }

    protected abstract C createClientConnection(AsyncConnection channel);

    // 创建连接后会立马从服务器拉取数据构建的虚拟请求，返回null表示连上服务器后不会立马读取数据
    protected R createVirtualRequestAfterConnect() {
        return null;
    }

    // 更新地址列表
    protected void updateClientAddress(List<SocketAddress> addrs) {
        Set<SocketAddress> newAddrs = new HashSet<>(addrs);
        Set<SocketAddress> delAddrs = new HashSet<>();
        for (SocketAddress addr : this.address.getAddresses()) {
            if (!newAddrs.contains(addr)) {
                delAddrs.add(addr);
            }
        }
        this.address.updateAddress(addrs);

        for (SocketAddress addr : delAddrs) {
            AddressConnEntry<C>[] entrys = this.connAddrEntrys.remove(addr);
            if (entrys != null) {
                for (AddressConnEntry<C> entry : entrys) {
                    if (entry != null && entry.connection != null) {
                        entry.connection.dispose(null);
                    }
                }
            }
        }
    }

    protected int pingIntervalSeconds() {
        return 30;
    }

    protected void handlePingResult(C conn, P result) {}

    public void close() {
        if (closed.compareAndSet(false, true)) {
            this.timeoutScheduler.shutdownNow();
            for (AddressConnEntry<C>[] entrys : this.connAddrEntrys.values()) {
                for (AddressConnEntry<C> entry : entrys) {
                    if (entry != null) {
                        closeConnection(entry.connection);
                    }
                }
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
                conn.writeChannel(closeReq).get(3, TimeUnit.SECONDS);
            } catch (Exception e) {
                // do nothing
            }
            conn.dispose(null);
        }
    }

    protected <T> CompletableFuture<T> writeChannel(ClientConnection conn, Function<P, T> respTransfer, R request) {
        return conn.writeChannel(respTransfer, request);
    }

    public final CompletableFuture<P> sendAsync(R request) {
        return sendAsync(getAddress(request), (Function) null, request);
    }

    public final <T> CompletableFuture<T> sendAsync(Function<P, T> respTransfer, R request) {
        return sendAsync(getAddress(request), respTransfer, request);
    }

    public final CompletableFuture<P> sendAsync(SocketAddress addr, R request) {
        return sendAsync(addr, (Function) null, request);
    }

    public final <T> CompletableFuture<T> sendAsync(SocketAddress addr, Function<P, T> respTransfer, R request) {
        request.traceid = Traces.computeIfAbsent(request.traceid, Traces.currentTraceid());
        request.computeWorkThreadIfAbsent();
        return connect(request.workThread, addr).thenCompose(conn -> writeChannel(conn, respTransfer, request));
    }

    protected CompletableFuture<P> writeChannel(ClientConnection conn, R request) {
        return conn.writeChannel(request);
    }

    protected CompletableFuture<P>[] writeChannel(ClientConnection conn, R... requests) {
        requests[0].traceid = Traces.computeIfAbsent(requests[0].traceid, Traces.currentTraceid());
        return conn.writeChannel(requests);
    }

    protected <T> CompletableFuture<T>[] writeChannel(
            ClientConnection conn, Function<P, T> respTransfer, R[] requests) {
        return conn.writeChannel(respTransfer, requests);
    }

    // 根据请求获取地址
    protected SocketAddress getAddress(@Nullable R request) {
        return address.randomAddress();
    }

    public final CompletableFuture<C> newConnection() {
        return connect(getAddress(null), WorkThread.currentWorkThread(), false);
    }

    // 指定地址获取连接
    public final CompletableFuture<C> newConnection(final SocketAddress addr) {
        return connect(addr, WorkThread.currentWorkThread(), false);
    }

    public final CompletableFuture<C> connect() {
        return connect(getAddress(null), WorkThread.currentWorkThread(), true);
    }

    protected CompletableFuture<C> connect(R request) {
        return connect(getAddress(request), request.workThread, true);
    }

    // 指定地址获取连接
    public final CompletableFuture<C> connect(final SocketAddress addr) {
        return connect(addr, WorkThread.currentWorkThread(), true);
    }

    // 指定地址获取连接
    protected CompletableFuture<C> connect(WorkThread workThread, final SocketAddress addr) {
        return connect(addr, workThread, true);
    }

    // 指定地址获取连接
    private CompletableFuture<C> connect(@Nonnull SocketAddress addr, @Nullable WorkThread workThread, boolean pool) {
        if (addr == null) {
            return CompletableFuture.failedFuture(new NullPointerException("address is empty"));
        }
        final String traceid = Traces.currentTraceid();
        final AddressConnEntry<C> entry = getAddressConnEntry(addr, workThread);
        C ec = entry.connection;
        if (pool && ec != null && ec.isOpen()) {
            return CompletableFuture.completedFuture(ec);
        }
        final Queue<CompletableFuture<C>> waitQueue = entry.connAcquireWaitings;
        if (!pool || entry.connOpenState.compareAndSet(false, true)) {
            CompletableFuture<C> future = group.createClient(tcp, addr, connectTimeoutSeconds)
                    .thenApply(c ->
                            (C) createClientConnection(c).setConnEntry(entry).setMaxPipelines(maxPipelines));
            R virtualReq = createVirtualRequestAfterConnect();
            if (virtualReq != null) {
                virtualReq.traceid = traceid;
                if (virtualReq.workThread == null) {
                    virtualReq.workThread = workThread;
                }
                future = future.thenCompose(conn -> {
                    Traces.currentTraceid(traceid);
                    return conn.writeVirtualRequest(virtualReq).thenApply(v -> conn);
                });
            } else {
                future = future.thenApply(conn -> {
                    conn.channel.readRegister(conn.getCodec()); // 不用readRegisterInIOThread，因executeRead可能会异步
                    return conn;
                });
            }
            if (authenticate != null) {
                future = future.thenCompose(authenticate.apply(workThread, traceid));
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
                                        workThread.runWork(() -> {
                                            Traces.currentTraceid(traceid);
                                            fs.complete(c);
                                        });
                                    } else {
                                        CompletableFuture<C> fs = f;
                                        Utility.execute(() -> fs.complete(c));
                                    }
                                }
                            }
                        }
                        return c;
                    })
                    .whenComplete((r, t) -> {
                        if (pool && t != null) {
                            entry.connOpenState.set(false);
                        }
                    });
        } else {
            int seconds = connectTimeoutSeconds > 0 ? connectTimeoutSeconds : 3;
            CompletableFuture rs = new CompletableFuture();
            waitQueue.offer(rs);
            return Utility.orTimeout(rs, () -> addr + " connect timeout", seconds, TimeUnit.SECONDS);
        }
    }

    private AddressConnEntry<C> getAddressConnEntry(SocketAddress addr, WorkThread workThread) {
        final AddressConnEntry<C>[] entrys = connAddrEntrys.computeIfAbsent(addr, a -> {
            AddressConnEntry<C>[] array = new AddressConnEntry[connLimit];
            for (int i = 0; i < array.length; i++) {
                array[i] = new AddressConnEntry<>();
            }
            return array;
        });
        if (workThread != null && workThread.threads() == entrys.length && workThread.index() > -1) {
            return entrys[workThread.index()];
        }
        int index = workThread == null || workThread.index() < 0
                ? random.nextInt(entrys.length)
                : workThread.index() % entrys.length;
        return entrys[index];
    }

    protected ClientFuture<R, P> createClientFuture(ClientConnection conn, R request) {
        ClientFuture respFuture = new ClientFuture(conn, request);
        int rts = getReadTimeoutSeconds();
        if (rts > 0 && !request.isCloseType()) {
            respFuture.setTimeout(timeoutScheduler.schedule(respFuture, rts, TimeUnit.SECONDS));
        }
        return respFuture;
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

        AddressConnEntry() {}
    }
}
