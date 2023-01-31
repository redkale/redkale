/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.cluster;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import org.redkale.annotation.*;
import org.redkale.annotation.ResourceListener;
import org.redkale.boot.*;
import org.redkale.convert.json.JsonConvert;
import org.redkale.service.Service;
import org.redkale.source.CacheSource;
import org.redkale.util.*;

/**
 * 使用CacheSource实现的第三方服务发现管理接口cluster
 *
 *
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.3.0
 */
public class CacheClusterAgent extends ClusterAgent implements Resourcable {

    @Resource(name = "$")
    private CacheSource source;

    private String sourceName;

    protected int ttls = 10; //定时检查的秒数

    protected ScheduledThreadPoolExecutor scheduler;

    protected ScheduledFuture taskFuture;

    //可能被HttpMessageClient用到的服务 key: serviceName
    protected final ConcurrentHashMap<String, Collection<InetSocketAddress>> httpAddressMap = new ConcurrentHashMap<>();

    //可能被mqtp用到的服务 key: serviceName
    protected final ConcurrentHashMap<String, Collection<InetSocketAddress>> mqtpAddressMap = new ConcurrentHashMap<>();

    @Override
    public void init(ResourceFactory factory, AnyValue config) {
        super.init(factory, config);
        this.sourceName = getSourceName();
        this.ttls = config.getIntValue("ttls", 10);
        if (this.ttls < 5) {
            this.ttls = 10;
        }
    }

    @Override
    @ResourceListener
    public void onResourceChange(ResourceEvent[] events) {
        StringBuilder sb = new StringBuilder();
        int newTtls = this.ttls;
        for (ResourceEvent event : events) {
            if ("ttls".equals(event.name())) {
                newTtls = Integer.parseInt(event.newValue().toString());
                if (newTtls < 5) {
                    sb.append(CacheClusterAgent.class.getSimpleName()).append("(name=").append(resourceName()).append(") cannot change '").append(event.name()).append("' to '").append(event.coverNewValue()).append("'\r\n");
                } else {
                    sb.append(CacheClusterAgent.class.getSimpleName()).append("(name=").append(resourceName()).append(") change '").append(event.name()).append("' to '").append(event.coverNewValue()).append("'\r\n");
                }
            } else {
                sb.append(CacheClusterAgent.class.getSimpleName()).append("(name=").append(resourceName()).append(") skip change '").append(event.name()).append("' to '").append(event.coverNewValue()).append("'\r\n");
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

    @Override //ServiceLoader时判断配置是否符合当前实现类
    public boolean acceptsConf(AnyValue config) {
        if (config == null) {
            return false;
        }
        return config.getValue("source") != null;
    }

    @Override
    public void start() {
        if (this.scheduler == null) {
            AtomicInteger counter = new AtomicInteger();
            this.scheduler = new ScheduledThreadPoolExecutor(1, (Runnable r) -> {
                final Thread t = new Thread(r, "Redkale-" + CacheClusterAgent.class.getSimpleName() + "-Check-Thread-" + counter.incrementAndGet());
                t.setDaemon(true);
                return t;
            });
        }
        if (this.taskFuture != null) {
            this.taskFuture.cancel(true);
        }
        this.taskFuture = this.scheduler.scheduleAtFixedRate(newTask(), Math.max(2000, ttls * 1000), Math.max(2000, ttls * 1000), TimeUnit.MILLISECONDS);
    }

    private Runnable newTask() {
        return () -> {
            try {
                checkApplicationHealth();
                checkHttpAddressHealth();
                loadMqtpAddressHealth();
                localEntrys.values().stream().filter(e -> !e.canceled).forEach(entry -> {
                    checkLocalHealth(entry);
                });
                remoteEntrys.values().stream().filter(entry -> "SNCP".equalsIgnoreCase(entry.protocol)).forEach(entry -> {
                    updateSncpTransport(entry);
                });
            } catch (Exception e) {
                logger.log(Level.SEVERE, "scheduleAtFixedRate check error", e instanceof CompletionException ? ((CompletionException) e).getCause() : e);
            }
        };
    }

    protected void loadMqtpAddressHealth() {
        List<String> keys = source.keysStartsWith("cluster.mqtp:");
        keys.forEach(serviceName -> {
            try {
                this.mqtpAddressMap.put(serviceName, queryAddress(serviceName).get(3, TimeUnit.SECONDS));
            } catch (Exception e) {
                logger.log(Level.SEVERE, "loadMqtpAddressHealth check " + serviceName + " error", e);
            }
        });
    }

    protected void checkHttpAddressHealth() {
        try {
            this.httpAddressMap.keySet().stream().forEach(serviceName -> {
                try {
                    this.httpAddressMap.put(serviceName, queryAddress(serviceName).get(3, TimeUnit.SECONDS));
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

    @Override //获取MQTP的HTTP远程服务的可用ip列表, key = serviceName的后半段
    public CompletableFuture<Map<String, Collection<InetSocketAddress>>> queryMqtpAddress(String protocol, String module, String resname) {
        final Map<String, Collection<InetSocketAddress>> rsmap = new ConcurrentHashMap<>();
        final String servicenamprefix = generateHttpServiceName(protocol, module, null) + ":";
        mqtpAddressMap.keySet().stream().filter(k -> k.startsWith(servicenamprefix))
            .forEach(sn -> rsmap.put(sn.substring(servicenamprefix.length()), mqtpAddressMap.get(sn)));
        return CompletableFuture.completedFuture(rsmap);
    }

    @Override //获取HTTP远程服务的可用ip列表
    public CompletableFuture<Collection<InetSocketAddress>> queryHttpAddress(String protocol, String module, String resname) {
        final String serviceName = generateHttpServiceName(protocol, module, resname);
        Collection<InetSocketAddress> rs = httpAddressMap.get(serviceName);
        if (rs != null) {
            return CompletableFuture.completedFuture(rs);
        }
        return queryAddress(serviceName).thenApply(t -> {
            httpAddressMap.put(serviceName, t);
            return t;
        });
    }

    @Override
    protected CompletableFuture<Collection<InetSocketAddress>> queryAddress(final ClusterEntry entry) {
        return queryAddress(entry.serviceName);
    }

    private CompletableFuture<Collection<InetSocketAddress>> queryAddress(final String serviceName) {
        final CompletableFuture<Map<String, AddressEntry>> future = source.hmapAsync(serviceName, AddressEntry.class, 0, 10000);
        return future.thenApply(map -> {
            final Set<InetSocketAddress> set = new HashSet<>();
            map.forEach((n, v) -> {
                if (v != null && (System.currentTimeMillis() - v.time) / 1000 < ttls) {
                    set.add(v.addr);
                }
            });
            return set;
        });
    }

    protected boolean isApplicationHealth() {
        String serviceName = generateApplicationServiceName();
        String serviceid = generateApplicationServiceId();
        AddressEntry entry = (AddressEntry) source.hget(serviceName, serviceid, AddressEntry.class);
        return entry != null && (System.currentTimeMillis() - entry.time) / 1000 < ttls;
    }

    protected void checkApplicationHealth() {
        String checkname = generateApplicationServiceName();
        String checkid = generateApplicationCheckId();
        AddressEntry entry = new AddressEntry();
        entry.addr = this.appAddress;
        entry.nodeid = this.nodeid;
        entry.time = System.currentTimeMillis();
        source.hset(checkname, checkid, AddressEntry.class, entry);
    }

    @Override
    public void register(Application application) {
        if (isApplicationHealth()) {
            throw new RuntimeException("application.nodeid=" + nodeid + " exists in cluster");
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
        String serviceName = generateApplicationServiceName();
        source.del(serviceName);
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

    protected void deregister(NodeServer ns, String protocol, Service service, boolean realcanceled) {
        String serviceName = generateServiceName(ns, protocol, service);
        String serviceid = generateServiceId(ns, protocol, service);
        ClusterEntry currEntry = null;
        for (final ClusterEntry entry : localEntrys.values()) {
            if (entry.serviceName.equals(serviceName) && entry.serviceid.equals(serviceid)) {
                currEntry = entry;
                break;
            }
        }
        if (currEntry == null) {
            for (final ClusterEntry entry : remoteEntrys.values()) {
                if (entry.serviceName.equals(serviceName) && entry.serviceid.equals(serviceid)) {
                    currEntry = entry;
                    break;
                }
            }
        }
        source.hdel(serviceName, serviceid);
        if (realcanceled && currEntry != null) {
            currEntry.canceled = true;
        }
        if (!"mqtp".equals(protocol) && currEntry != null && currEntry.submqtp) {
            deregister(ns, "mqtp", service, realcanceled);
        }
    }

    @Override
    protected String generateApplicationServiceName() {
        return "cluster." + super.generateApplicationServiceName();
    }

    @Override
    protected String generateServiceName(NodeServer ns, String protocol, Service service) {
        return "cluster." + super.generateServiceName(ns, protocol, service);
    }

    @Override
    public String generateHttpServiceName(String protocol, String module, String resname) {
        return "cluster." + super.generateHttpServiceName(protocol, module, resname);
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

        public int nodeid;

        public long time;

        public String resname;

        public AddressEntry() {
        }

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
