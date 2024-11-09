/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import java.util.stream.Collectors;
import javax.net.ssl.SSLContext;
import org.redkale.annotation.Comment;
import org.redkale.service.Service;
import org.redkale.util.*;

/**
 * 被net.client模块代替
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
@Deprecated(since = "2.8.0")
public class TransportFactory {

    @Comment("默认TCP读取超时秒数")
    public static int DEFAULT_READTIMEOUTSECONDS = 6;

    @Comment("默认TCP写入超时秒数")
    public static int DEFAULT_WRITETIMEOUTSECONDS = 6;

    public static final String NAME_POOLMAXCONNS = "poolmaxconns";

    public static final String NAME_PINGINTERVAL = "pinginterval";

    public static final String NAME_CHECKINTERVAL = "checkinterval";

    protected static final Logger logger = Logger.getLogger(TransportFactory.class.getSimpleName());

    // 传输端的AsyncGroup
    protected final AsyncGroup asyncGroup;

    // 每个地址对应的Group名
    protected final Map<InetSocketAddress, String> groupAddrs = new HashMap<>();

    // 协议地址的Group集合
    protected final Map<String, TransportGroupInfo> groupInfos = new HashMap<>();

    protected final List<WeakReference<Service>> services = new CopyOnWriteArrayList<>();

    protected final List<WeakReference<Transport>> transportReferences = new CopyOnWriteArrayList<>();

    // 连接池大小
    protected int poolMaxConns = Integer.getInteger(
            "redkale.net.transport.pool.maxconns", Math.max(100, Utility.cpus() * 16)); // 最少是wsthreads的两倍

    // 检查不可用地址周期， 单位：秒
    protected int checkInterval = Integer.getInteger("redkale.net.transport.check.interval", 30);

    // 心跳周期， 单位：秒
    protected int pinginterval;

    // TCP读取超时秒数
    protected int readTimeoutSeconds;

    // TCP写入超时秒数
    protected int writeTimeoutSeconds;

    // ping和检查的定时器
    private ScheduledThreadPoolExecutor scheduler;

    protected SSLContext sslContext;

    // ping的内容
    private ByteBuffer pingBuffer;

    // pong的数据长度, 小于0表示不进行判断
    protected int pongLength;

    // 是否TCP
    protected String netprotocol = "TCP";

    // 负载均衡策略
    protected final TransportStrategy strategy;

    protected TransportFactory(
            AsyncGroup asyncGroup,
            SSLContext sslContext,
            String netprotocol,
            int readTimeoutSeconds,
            int writeTimeoutSeconds,
            final TransportStrategy strategy) {
        this.asyncGroup = asyncGroup;
        this.sslContext = sslContext;
        this.netprotocol = netprotocol;
        this.readTimeoutSeconds = readTimeoutSeconds;
        this.writeTimeoutSeconds = writeTimeoutSeconds;
        this.strategy = strategy;
    }

    protected TransportFactory(
            AsyncGroup asyncGroup,
            SSLContext sslContext,
            String netprotocol,
            int readTimeoutSeconds,
            int writeTimeoutSeconds) {
        this(asyncGroup, sslContext, netprotocol, readTimeoutSeconds, writeTimeoutSeconds, null);
    }

    public void init(AnyValue conf, ByteBuffer pingBuffer, int pongLength) {
        if (conf != null) {
            this.poolMaxConns = conf.getIntValue(NAME_POOLMAXCONNS, this.poolMaxConns);
            this.pinginterval = conf.getIntValue(NAME_PINGINTERVAL, this.pinginterval);
            this.checkInterval = conf.getIntValue(NAME_CHECKINTERVAL, this.checkInterval);
            if (this.poolMaxConns < 2) {
                this.poolMaxConns = 2;
            }
            if (this.pinginterval < 2) {
                this.pinginterval = 2;
            }
            if (this.checkInterval < 2) {
                this.checkInterval = 2;
            }
        }
        this.scheduler = new ScheduledThreadPoolExecutor(1, (Runnable r) -> {
            final Thread t = new Thread(r, "Redkale-" + this.getClass().getSimpleName() + "-Schedule-Thread");
            t.setDaemon(true);
            return t;
        });
        this.scheduler.scheduleAtFixedRate(
                () -> {
                    try {
                        checks();
                    } catch (Throwable t) {
                        logger.log(
                                Level.SEVERE,
                                "TransportFactory schedule(interval=" + checkInterval + "s) check error",
                                t);
                    }
                },
                checkInterval,
                checkInterval,
                TimeUnit.SECONDS);

        if (this.pinginterval > 0) {
            if (pingBuffer != null) {
                this.pingBuffer = pingBuffer.asReadOnlyBuffer();
                this.pongLength = pongLength;

                scheduler.scheduleAtFixedRate(
                        () -> {
                            pings();
                        },
                        pinginterval,
                        pinginterval,
                        TimeUnit.SECONDS);
            }
        }
    }

    public static TransportFactory create(AsyncGroup asyncGroup, int readTimeoutSeconds, int writeTimeoutSeconds) {
        return new TransportFactory(asyncGroup, null, "TCP", readTimeoutSeconds, writeTimeoutSeconds, null);
    }

    public static TransportFactory create(
            AsyncGroup asyncGroup,
            SSLContext sslContext,
            int readTimeoutSeconds,
            int writeTimeoutSeconds,
            final TransportStrategy strategy) {
        return new TransportFactory(asyncGroup, sslContext, "TCP", readTimeoutSeconds, writeTimeoutSeconds, strategy);
    }

    public static TransportFactory create(
            AsyncGroup asyncGroup, String netprotocol, int readTimeoutSeconds, int writeTimeoutSeconds) {
        return new TransportFactory(asyncGroup, null, netprotocol, readTimeoutSeconds, writeTimeoutSeconds, null);
    }

    public static TransportFactory create(
            AsyncGroup asyncGroup,
            SSLContext sslContext,
            String netprotocol,
            int readTimeoutSeconds,
            int writeTimeoutSeconds,
            final TransportStrategy strategy) {
        return new TransportFactory(
                asyncGroup, sslContext, netprotocol, readTimeoutSeconds, writeTimeoutSeconds, strategy);
    }

    public Transport createTransportTCP(
            String name, final InetSocketAddress clientAddress, final Collection<InetSocketAddress> addresses) {
        return new Transport(name, "TCP", this, this.asyncGroup, this.sslContext, clientAddress, addresses, strategy);
    }

    public Transport createTransport(
            String name,
            String netprotocol,
            final InetSocketAddress clientAddress,
            final Collection<InetSocketAddress> addresses) {
        return new Transport(
                name, netprotocol, this, this.asyncGroup, this.sslContext, clientAddress, addresses, strategy);
    }

    public String findGroupName(InetSocketAddress addr) {
        if (addr == null) {
            return null;
        }
        return groupAddrs.get(addr);
    }

    public TransportGroupInfo findGroupInfo(String group) {
        if (group == null) {
            return null;
        }
        return groupInfos.get(group);
    }

    public boolean addGroupInfo(String groupName, InetSocketAddress... addrs) {
        addGroupInfo(new TransportGroupInfo(groupName, addrs));
        return true;
    }

    public boolean removeGroupInfo(String groupName, InetSocketAddress addr) {
        if (groupName == null || groupName.isEmpty() || addr == null) {
            return false;
        }
        if (!groupName.equals(groupAddrs.get(addr))) {
            return false;
        }
        TransportGroupInfo group = groupInfos.get(groupName);
        if (group == null) {
            return false;
        }
        group.removeAddress(addr);
        groupAddrs.remove(addr);
        return true;
    }

    public TransportFactory addGroupInfo(String name, Set<InetSocketAddress> addrs) {
        addGroupInfo(new TransportGroupInfo(name, addrs));
        return this;
    }

    public boolean addGroupInfo(TransportGroupInfo info) {
        if (info == null) {
            throw new RedkaleException("TransportGroupInfo can not null");
        }
        if (info.addresses == null) {
            throw new RedkaleException("TransportGroupInfo.addresses can not null");
        }
        if (!checkName(info.name)) {
            throw new RedkaleException("Transport.group.name only 0-9 a-z A-Z _ cannot begin 0-9");
        }
        TransportGroupInfo old = groupInfos.get(info.name);
        if (old != null && !old.protocol.equals(info.protocol)) {
            throw new RedkaleException("Transport.group.name repeat but protocol is different");
        }
        for (InetSocketAddress addr : info.addresses) {
            if (!groupAddrs.getOrDefault(addr, info.name).equals(info.name)) {
                throw new RedkaleException(addr + " repeat but different group.name");
            }
        }
        if (old == null) {
            groupInfos.put(info.name, info);
        } else {
            old.putAddress(info.addresses);
        }
        for (InetSocketAddress addr : info.addresses) {
            groupAddrs.put(addr, info.name);
        }
        return true;
    }

    public Transport loadTransport(InetSocketAddress sncpAddress, final Set<String> groups) {
        if (groups == null) {
            return null;
        }
        Set<InetSocketAddress> addresses = new HashSet<>();
        TransportGroupInfo info = null;
        for (String group : groups) {
            info = groupInfos.get(group);
            if (info == null) {
                continue;
            }
            addresses.addAll(info.addresses);
        }
        if (info == null) {
            info = new TransportGroupInfo(netprotocol);
        } else {
            info.protocol = netprotocol;
        }
        if (sncpAddress != null) {
            addresses.remove(sncpAddress);
        }
        return new Transport(
                groups.stream().sorted().collect(Collectors.joining(";")),
                info.protocol,
                this,
                this.asyncGroup,
                this.sslContext,
                sncpAddress,
                addresses,
                this.strategy);
    }

    public List<TransportGroupInfo> getGroupInfos() {
        return new ArrayList<>(this.groupInfos.values());
    }

    public Logger getLogger() {
        return logger;
    }

    public void addSncpService(Service service) {
        if (service == null) {
            return;
        }
        services.add(new WeakReference<>(service));
    }

    public List<Service> getServices() {
        List<Service> rs = new ArrayList<>();
        for (WeakReference<Service> ref : services) {
            Service service = ref.get();
            if (service != null) {
                rs.add(service);
            }
        }
        return rs;
    }

    public void shutdownNow() {
        if (this.scheduler != null) {
            this.scheduler.shutdownNow();
        }
    }

    private void checks() {
        List<WeakReference> nulllist = new ArrayList<>();
        for (WeakReference<Transport> ref : transportReferences) {
            Transport transport = ref.get();
            if (transport == null) {
                nulllist.add(ref);
                continue;
            }
            Transport.TransportNode[] nodes = transport.getTransportNodes();
            for (final Transport.TransportNode node : nodes) {
                if (node.disabletime < 1) {
                    continue; // 可用
                }
                CompletableFuture<AsyncConnection> future = Utility.orTimeout(
                        asyncGroup.createTCPClientConnection(node.address), null, 2, TimeUnit.SECONDS);
                future.whenComplete((r, t) -> {
                    node.disabletime = t == null ? 0 : System.currentTimeMillis();
                    if (r != null) {
                        r.dispose();
                    }
                });
            }
        }
        for (WeakReference ref : nulllist) {
            transportReferences.remove(ref);
        }
    }

    private void pings() {
        long timex = System.currentTimeMillis()
                - (this.pinginterval < 15 ? this.pinginterval : (this.pinginterval - 3)) * 1000;
        for (WeakReference<Transport> ref : transportReferences) {
            Transport transport = ref.get();
            if (transport == null) {
                continue;
            }
            Transport.TransportNode[] nodes = transport.getTransportNodes();
            for (final Transport.TransportNode node : nodes) {
                final BlockingQueue<AsyncConnection> queue = node.connQueue;
                AsyncConnection conn;
                while ((conn = queue.poll()) != null) {
                    if (conn.getLastWriteTime() > timex && false) { // 最近几秒内已经进行过IO操作
                        queue.offer(conn);
                    } else { // 超过一定时间的连接需要进行ping处理
                        ByteBuffer sendBuffer = pingBuffer.duplicate();
                        final AsyncConnection localconn = conn;
                        final BlockingQueue<AsyncConnection> localqueue = queue;
                        localconn.writeInIOThread(sendBuffer, sendBuffer, new CompletionHandler<Integer, ByteBuffer>() {
                            @Override
                            public void completed(Integer result, ByteBuffer wbuffer) {
                                localconn.read(new CompletionHandler<Integer, ByteBuffer>() {
                                    int counter = 0;

                                    @Override
                                    public void completed(Integer result, ByteBuffer pongBuffer) {
                                        if (counter > 3) {
                                            localconn.offerWriteBuffer(pongBuffer);
                                            localconn.dispose();
                                            return;
                                        }
                                        if (pongLength > 0 && pongBuffer.position() < pongLength) {
                                            counter++;
                                            localconn.setReadBuffer(pongBuffer);
                                            localconn.read(this);
                                            return;
                                        }
                                        localconn.offerWriteBuffer(pongBuffer);
                                        localqueue.offer(localconn);
                                    }

                                    @Override
                                    public void failed(Throwable exc, ByteBuffer pongBuffer) {
                                        localconn.offerWriteBuffer(pongBuffer);
                                        localconn.dispose();
                                    }
                                });
                            }

                            @Override
                            public void failed(Throwable exc, ByteBuffer buffer) {
                                localconn.dispose();
                            }
                        });
                    }
                }
            }
        }
    }

    private static boolean checkName(String name) { // 不能含特殊字符
        if (name.isEmpty()) {
            return false;
        }
        if (name.charAt(0) >= '0' && name.charAt(0) <= '9') {
            return false;
        }
        for (char ch : name.toCharArray()) {
            if (!((ch >= '0' && ch <= '9')
                    || ch == '_'
                    || (ch >= 'a' && ch <= 'z')
                    || (ch >= 'A' && ch <= 'Z'))) { // 不能含特殊字符
                return false;
            }
        }
        return true;
    }
}
