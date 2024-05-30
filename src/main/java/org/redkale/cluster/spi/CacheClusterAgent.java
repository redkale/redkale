/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.cluster.spi;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.Level;
import org.redkale.annotation.*;
import org.redkale.boot.*;
import org.redkale.convert.json.JsonConvert;
import org.redkale.inject.Resourcable;
import org.redkale.inject.ResourceEvent;
import org.redkale.service.Service;
import org.redkale.source.CacheSource;
import org.redkale.util.*;

/**
 * 使用CacheSource实现的第三方服务发现管理接口cluster
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.3.0
 */
public class CacheClusterAgent extends ClusterAgent implements Resourcable {

    @Resource(name = Resource.PARENT_NAME)
    private CacheSource source;

    private String sourceName;

    protected int ttls = 10; // 定时检查的秒数

    protected ScheduledThreadPoolExecutor scheduler;

    protected ScheduledFuture taskFuture;

    // 可能被HttpMessageClient用到的服务 key: serviceName，例如: cluster.service.http.user
    protected final ConcurrentHashMap<String, Set<InetSocketAddress>> httpAddressMap = new ConcurrentHashMap<>();

    // 可能被sncp用到的服务 key: serviceName, 例如: cluster.service.sncp.user
    protected final ConcurrentHashMap<String, Set<InetSocketAddress>> sncpAddressMap = new ConcurrentHashMap<>();

    @Override
    public void init(AnyValue config) {
        super.init(config);
        this.sourceName = getSourceName();
        this.ttls = config.getIntValue("ttls", 10);
        if (this.ttls < 5) { // 值不能太小
            this.ttls = 10;
        }
    }

    @Override
    @ResourceChanged
    public void onResourceChange(ResourceEvent[] events) {
        StringBuilder sb = new StringBuilder();
        int newTtls = this.ttls;
        for (ResourceEvent event : events) {
            if ("ttls".equals(event.name())) {
                newTtls = Integer.parseInt(event.newValue().toString());
                if (newTtls < 5) {
                    sb.append(CacheClusterAgent.class.getSimpleName())
                            .append("(name=")
                            .append(resourceName())
                            .append(") cannot change '")
                            .append(event.name())
                            .append("' to '")
                            .append(event.coverNewValue())
                            .append("'\r\n");
                } else {
                    sb.append(CacheClusterAgent.class.getSimpleName())
                            .append("(name=")
                            .append(resourceName())
                            .append(") change '")
                            .append(event.name())
                            .append("' to '")
                            .append(event.coverNewValue())
                            .append("'\r\n");
                }
            } else {
                sb.append(CacheClusterAgent.class.getSimpleName())
                        .append("(name=")
                        .append(resourceName())
                        .append(") skip change '")
                        .append(event.name())
                        .append("' to '")
                        .append(event.coverNewValue())
                        .append("'\r\n");
            }
        }
        if (newTtls != this.ttls) {
            this.ttls = newTtls;
            start();
        }
        if (sb.length() > 0) {
            logger.log(Level.INFO, sb.toString());
        }
    }

    @Override
    public void setConfig(AnyValue config) {
        super.setConfig(config);
        this.sourceName = getSourceName();
    }

    @Override
    public void destroy(AnyValue config) {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    public String getSourceName() {
        return config.getValue("source");
    }

    @Override
    public String resourceName() {
        return sourceName;
    }

    @Override // ServiceLoader时判断配置是否符合当前实现类
    public boolean acceptsConf(AnyValue config) {
        if (config == null) {
            return false;
        }
        return config.getValue("source") != null;
    }

    @Override
    public void start() {
        if (this.scheduler == null) {
            this.scheduler = Utility.newScheduledExecutor(
                    1, "Redkale-" + CacheClusterAgent.class.getSimpleName() + "-Check-Thread-%s");
        }
        if (this.taskFuture != null) {
            this.taskFuture.cancel(true);
        }
        this.taskFuture = this.scheduler.scheduleAtFixedRate(newTask(), ttls, ttls, TimeUnit.SECONDS);
    }

    private Runnable newTask() {
        return () -> {
            try {
                long s = System.currentTimeMillis();
                localEntrys.values().stream().filter(e -> !e.canceled).forEach(this::checkLocalHealth);
                remoteEntrys.values().stream()
                        .filter(entry -> "SNCP".equalsIgnoreCase(entry.protocol))
                        .forEach(this::updateSncpAddress);
                checkApplicationHealth();
                checkHttpAddressHealth();
                loadSncpAddressHealth();
                long e = System.currentTimeMillis() - s;
                if (e >= ttls * 9 / 10) {
                    logger.log(Level.WARNING, getClass().getSimpleName() + ".schedule check-slower cost " + e + " ms");
                } else if (e >= ttls / 2) {
                    logger.log(Level.FINE, getClass().getSimpleName() + ".schedule check-slowly cost " + e + " ms");
                } else {
                    logger.log(Level.FINEST, getClass().getSimpleName() + ".schedule check cost " + e + " ms");
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, getClass().getSimpleName() + ".schedule check error", e);
            }
        };
    }

    protected void loadSncpAddressHealth() {
        List<String> keys = source.keysStartsWith("cluster.sncp:");
        keys.forEach(serviceName -> {
            try {
                this.sncpAddressMap.put(serviceName, queryAddress(serviceName).get(3, TimeUnit.SECONDS));
            } catch (Exception e) {
                logger.log(Level.SEVERE, "loadSncpAddressHealth check " + serviceName + " error", e);
            }
        });
    }

    protected void checkHttpAddressHealth() {
        try {
            this.httpAddressMap.keySet().stream().forEach(serviceName -> {
                try {
                    this.httpAddressMap.put(
                            serviceName, queryAddress(serviceName).get(3, TimeUnit.SECONDS));
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "checkHttpAddressHealth check " + serviceName + " error", e);
                }
            });
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "checkHttpAddressHealth check error", ex);
        }
    }

    protected void checkLocalHealth(final ClusterEntry entry) {
        AddressEntry newaddr = new AddressEntry();
        newaddr.addr = entry.address;
        newaddr.resname = entry.resourceName;
        newaddr.nodeid = this.nodeid;
        newaddr.time = System.currentTimeMillis();
        source.hset(entry.checkName, entry.checkid, AddressEntry.class, newaddr);
    }

    @Override // 获取SNCP远程服务的可用ip列表
    public CompletableFuture<Set<InetSocketAddress>> querySncpAddress(String protocol, String module, String resname) {
        final String serviceName = generateSncpServiceName(protocol, module, resname);
        Set<InetSocketAddress> rs = sncpAddressMap.get(serviceName);
        if (rs != null) {
            return CompletableFuture.completedFuture(rs);
        }
        return queryAddress(serviceName).thenApply(t -> {
            sncpAddressMap.put(serviceName, t);
            return t;
        });
    }

    @Override // 获取HTTP远程服务的可用ip列表
    public CompletableFuture<Set<InetSocketAddress>> queryHttpAddress(String protocol, String module, String resname) {
        final String serviceName = generateHttpServiceName(protocol, module, resname);
        Set<InetSocketAddress> rs = httpAddressMap.get(serviceName);
        if (rs != null) {
            return CompletableFuture.completedFuture(rs);
        }
        return queryAddress(serviceName).thenApply(t -> {
            httpAddressMap.put(serviceName, t);
            return t;
        });
    }

    @Override
    protected CompletableFuture<Set<InetSocketAddress>> queryAddress(final ClusterEntry entry) {
        return queryAddress(entry.serviceName);
    }

    private CompletableFuture<Set<InetSocketAddress>> queryAddress(final String serviceName) {
        return queryAddress0(serviceName, new HashSet<>(), new AtomicLong());
    }

    private CompletableFuture<Set<InetSocketAddress>> queryAddress0(
            String serviceName, Set<InetSocketAddress> set, AtomicLong cursor) {
        final CompletableFuture<Map<String, AddressEntry>> future =
                source.hscanAsync(serviceName, AddressEntry.class, cursor, 10000);
        return future.thenCompose(map -> {
            map.forEach((n, v) -> {
                if (v != null && (System.currentTimeMillis() - v.time) / 1000 <= ttls) {
                    set.add(v.addr);
                }
            });
            if (cursor.get() == 0) {
                return CompletableFuture.completedFuture(set);
            } else {
                return queryAddress0(serviceName, set, cursor);
            }
        });
    }

    protected boolean isApplicationHealth() {
        String serviceName = generateApplicationServiceName();
        String serviceid = generateApplicationServiceId();
        AddressEntry entry = (AddressEntry) source.hget(serviceName, serviceid, AddressEntry.class);
        return entry != null && (System.currentTimeMillis() - entry.time) / 1000 <= ttls;
    }

    protected void checkApplicationHealth() {
        String checkName = generateApplicationServiceName();
        String checkid = generateApplicationCheckId();
        AddressEntry entry = new AddressEntry();
        entry.addr = this.appAddress;
        entry.nodeid = this.nodeid;
        entry.time = System.currentTimeMillis();
        source.hset(checkName, checkid, AddressEntry.class, entry);
    }

    @Override
    public void register(Application application) {
        if (isApplicationHealth()) {
            throw new RedkaleException("application.nodeid=" + nodeid + " exists in cluster");
        }
        deregister(application);

        String serviceid = generateApplicationServiceId();
        String serviceName = generateApplicationServiceName();
        AddressEntry entry = new AddressEntry();
        entry.addr = this.appAddress;
        entry.nodeid = this.nodeid;
        entry.time = System.currentTimeMillis();
        source.hset(serviceName, serviceid, AddressEntry.class, entry);
    }

    @Override
    public void deregister(Application application) {
        String serviceid = generateApplicationServiceId();
        String serviceName = generateApplicationServiceName();
        source.hdel(serviceName, serviceid);
    }

    @Override
    protected ClusterEntry register(NodeServer ns, String protocol, Service service) {
        deregister(ns, protocol, service, false);
        //
        ClusterEntry clusterEntry = new ClusterEntry(ns, protocol, service);
        AddressEntry entry = new AddressEntry();
        entry.addr = clusterEntry.address;
        entry.resname = clusterEntry.resourceName;
        entry.nodeid = this.nodeid;
        entry.time = System.currentTimeMillis();
        source.hset(clusterEntry.serviceName, clusterEntry.serviceid, AddressEntry.class, entry);
        return clusterEntry;
    }

    @Override
    protected void deregister(NodeServer ns, String protocol, Service service) {
        deregister(ns, protocol, service, true);
    }

    protected void deregister(NodeServer ns, String protocol, Service service, boolean realCanceled) {
        String serviceName = generateServiceName(ns, protocol, service);
        String serviceid = generateServiceId(ns, protocol, service);
        ClusterEntry currEntry = null;
        for (final ClusterEntry entry : localEntrys.values()) {
            if (Objects.equals(entry.serviceName, serviceName) && Objects.equals(entry.serviceid, serviceid)) {
                currEntry = entry;
                break;
            }
        }
        if (currEntry == null) {
            for (final ClusterEntry entry : remoteEntrys.values()) {
                if (Objects.equals(entry.serviceName, serviceName) && Objects.equals(entry.serviceid, serviceid)) {
                    currEntry = entry;
                    break;
                }
            }
        }
        source.hdel(serviceName, serviceid);
        if (realCanceled && currEntry != null) {
            currEntry.canceled = true;
        }
    }

    @Override
    protected String generateApplicationServiceName() {
        return "cluster:app:" + super.generateApplicationServiceName();
    }

    @Override
    protected String generateServiceName(NodeServer ns, String protocol, Service service) {
        return "cluster:service:" + super.generateServiceName(ns, protocol, service);
    }

    @Override
    public String generateHttpServiceName(String protocol, String module, String resname) {
        return "cluster:service:" + super.generateHttpServiceName(protocol, module, resname);
    }

    @Override
    public String generateSncpServiceName(String protocol, String restype, String resname) {
        return "cluster:service:" + super.generateSncpServiceName(protocol, restype, resname);
    }

    @Override
    protected String generateApplicationCheckName() {
        return generateApplicationServiceName();
    }

    @Override
    protected String generateApplicationCheckId() {
        return generateApplicationServiceId();
    }

    @Override
    protected String generateCheckName(NodeServer ns, String protocol, Service service) {
        return generateServiceName(ns, protocol, service);
    }

    @Override
    protected String generateCheckId(NodeServer ns, String protocol, Service service) {
        return generateServiceId(ns, protocol, service);
    }

    public static class AddressEntry {

        public InetSocketAddress addr;

        public String nodeid;

        public long time;

        public String resname;

        public AddressEntry refresh() {
            this.time = System.currentTimeMillis();
            return this;
        }

        @Override
        public String toString() {
            return JsonConvert.root().convertTo(this);
        }
    }
}
