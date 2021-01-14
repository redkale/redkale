/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.cluster;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import javax.annotation.Resource;
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

    //可能被HttpMessageClient用到的服务 key: servicename
    protected final ConcurrentHashMap<String, Collection<InetSocketAddress>> httpAddressMap = new ConcurrentHashMap<>();

    //可能被mqtp用到的服务 key: servicename
    protected final ConcurrentHashMap<String, Collection<InetSocketAddress>> mqtpAddressMap = new ConcurrentHashMap<>();

    @Override
    public void init(AnyValue config) {
        super.init(config);
        this.sourceName = getSourceName();

        AnyValue[] properties = config.getAnyValues("property");
        for (AnyValue property : properties) {
            if ("ttls".equalsIgnoreCase(property.getValue("name"))) {
                this.ttls = Integer.parseInt(property.getValue("value", "").trim());
                if (this.ttls < 5) this.ttls = 10;
            }
        }
    }

    @Override
    public void destroy(AnyValue config) {
        if (scheduler != null) scheduler.shutdownNow();
    }

    public String getSourceName() {
        AnyValue[] properties = config.getAnyValues("property");
        for (AnyValue property : properties) {
            if ("source".equalsIgnoreCase(property.getValue("name"))
                && property.getValue("value") != null) {
                this.sourceName = property.getValue("value");
                return this.sourceName;
            }
        }
        return null;
    }

    @Override
    public String resourceName() {
        return sourceName;
    }

    @Override //ServiceLoader时判断配置是否符合当前实现类
    public boolean match(AnyValue config) {
        if (config == null) return false;
        AnyValue[] properties = config.getAnyValues("property");
        if (properties == null || properties.length == 0) return false;
        for (AnyValue property : properties) {
            if ("source".equalsIgnoreCase(property.getValue("name"))
                && property.getValue("value") != null) return true;
        }
        return false;
    }

    @Override
    public void start() {
        if (this.scheduler == null) {
            this.scheduler = new ScheduledThreadPoolExecutor(4, (Runnable r) -> {
                final Thread t = new Thread(r, CacheClusterAgent.class.getSimpleName() + "-Task-Thread");
                t.setDaemon(true);
                return t;
            });

            //delay为了错开请求
            this.scheduler.scheduleAtFixedRate(() -> {
                checkApplicationHealth();
                checkHttpAddressHealth();
            }, 18, Math.max(2000, ttls * 1000 - 168), TimeUnit.MILLISECONDS);

            this.scheduler.scheduleAtFixedRate(() -> {
                loadMqtpAddressHealth();
            }, 88 * 2, Math.max(2000, ttls * 1000 - 168), TimeUnit.MILLISECONDS);

            this.scheduler.scheduleAtFixedRate(() -> {
                localEntrys.values().stream().filter(e -> !e.canceled).forEach(entry -> {
                    checkLocalHealth(entry);
                });
            }, 128 * 3, Math.max(2000, ttls * 1000 - 168), TimeUnit.MILLISECONDS);

            this.scheduler.scheduleAtFixedRate(() -> {
                remoteEntrys.values().stream().filter(entry -> "SNCP".equalsIgnoreCase(entry.protocol)).forEach(entry -> {
                    updateSncpTransport(entry);
                });
            }, 188 * 4, Math.max(2000, ttls * 1000 - 168), TimeUnit.MILLISECONDS);
        }
    }

    protected void loadMqtpAddressHealth() {
        List<String> keys = source.queryKeysStartsWith("cluster.mqtp:");
        keys.forEach(servicename -> {
            try {
                this.mqtpAddressMap.put(servicename, queryAddress(servicename).get(3, TimeUnit.SECONDS));
            } catch (Exception e) {
                logger.log(Level.SEVERE, "loadMqtpAddressHealth check " + servicename + " error", e);
            }
        });
    }

    protected void checkHttpAddressHealth() {
        try {
            this.httpAddressMap.keySet().stream().forEach(servicename -> {
                try {
                    this.httpAddressMap.put(servicename, queryAddress(servicename).get(3, TimeUnit.SECONDS));
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "checkHttpAddressHealth check " + servicename + " error", e);
                }
            });
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "checkHttpAddressHealth check error", ex);
        }
    }

    protected void checkLocalHealth(final ClusterEntry entry) {
        AddressEntry oldaddr = (AddressEntry) source.hget(entry.checkname, entry.checkid, AddressEntry.class);
        AddressEntry newaddr = new AddressEntry();
        newaddr.addr = entry.address;
        newaddr.nodeid = this.nodeid;
        newaddr.time = System.currentTimeMillis();
        source.hset(entry.checkname, entry.checkid, AddressEntry.class, newaddr);
        boolean ok = oldaddr != null && (System.currentTimeMillis() - oldaddr.time) / 1000 < ttls;
        if (!ok) logger.log(Level.SEVERE, entry.checkid + " check error: " + oldaddr);
    }

    @Override //获取MQTP的HTTP远程服务的可用ip列表, key = servicename的后半段
    public CompletableFuture<Map<String, Collection<InetSocketAddress>>> queryMqtpAddress(String protocol, String module, String resname) {
        final Map<String, Collection<InetSocketAddress>> rsmap = new ConcurrentHashMap<>();
        final String servicenamprefix = generateHttpServiceName(protocol, module, null) + ":";
        mqtpAddressMap.keySet().stream().filter(k -> k.startsWith(servicenamprefix))
            .forEach(sn -> rsmap.put(sn.substring(servicenamprefix.length()), mqtpAddressMap.get(sn)));
        return CompletableFuture.completedFuture(rsmap);
    }

    @Override //获取HTTP远程服务的可用ip列表
    public CompletableFuture<Collection<InetSocketAddress>> queryHttpAddress(String protocol, String module, String resname) {
        final String servicename = generateHttpServiceName(protocol, module, resname);
        Collection<InetSocketAddress> rs = httpAddressMap.get(servicename);
        if (rs != null) return CompletableFuture.completedFuture(rs);
        return queryAddress(servicename).thenApply(t -> {
            httpAddressMap.put(servicename, t);
            return t;
        });
    }

    @Override
    protected CompletableFuture<Collection<InetSocketAddress>> queryAddress(final ClusterEntry entry) {
        return queryAddress(entry.servicename);
    }

    private CompletableFuture<Collection<InetSocketAddress>> queryAddress(final String servicename) {
        final CompletableFuture<Map<String, AddressEntry>> future = source.hmapAsync(servicename, AddressEntry.class, 0, 10000);
        return future.thenApply(map -> {
            final Set<InetSocketAddress> set = new HashSet<>();
            map.forEach((n, v) -> {
                if (v != null && (System.currentTimeMillis() - v.time) / 1000 < ttls) set.add(v.addr);
            });
            return set;
        });
    }

    protected boolean isApplicationHealth() {
        String servicename = generateApplicationServiceName();
        String serviceid = generateApplicationServiceId();
        AddressEntry entry = (AddressEntry) source.hget(servicename, serviceid, AddressEntry.class);
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
        if (isApplicationHealth()) throw new RuntimeException("application.nodeid=" + nodeid + " exists in cluster");
        deregister(application);

        String serviceid = generateApplicationServiceId();
        String servicename = generateApplicationServiceName();
        AddressEntry entry = new AddressEntry();
        entry.addr = this.appAddress;
        entry.nodeid = this.nodeid;
        entry.time = System.currentTimeMillis();
        source.hset(servicename, serviceid, AddressEntry.class, entry);
    }

    @Override
    public void deregister(Application application) {
        String servicename = generateApplicationServiceName();
        source.remove(servicename);
    }

    @Override
    protected void register(NodeServer ns, String protocol, Service service) {
        deregister(ns, protocol, service, false);
        //
        String serviceid = generateServiceId(ns, protocol, service);
        String servicename = generateServiceName(ns, protocol, service);
        InetSocketAddress address = ns.isSNCP() ? ns.getSncpAddress() : ns.getServer().getSocketAddress();
        AddressEntry entry = new AddressEntry();
        entry.addr = address;
        entry.nodeid = this.nodeid;
        entry.time = System.currentTimeMillis();
        source.hset(servicename, serviceid, AddressEntry.class, entry);
    }

    @Override
    protected void deregister(NodeServer ns, String protocol, Service service) {
        deregister(ns, protocol, service, true);
    }

    protected void deregister(NodeServer ns, String protocol, Service service, boolean realcanceled) {
        String servicename = generateServiceName(ns, protocol, service);
        String serviceid = generateServiceId(ns, protocol, service);
        ClusterEntry currEntry = null;
        for (final ClusterEntry entry : localEntrys.values()) {
            if (entry.servicename.equals(servicename) && entry.serviceid.equals(serviceid)) {
                currEntry = entry;
                break;
            }
        }
        if (currEntry == null) {
            for (final ClusterEntry entry : remoteEntrys.values()) {
                if (entry.servicename.equals(servicename) && entry.serviceid.equals(serviceid)) {
                    currEntry = entry;
                    break;
                }
            }
        }
        source.hremove(servicename, serviceid);
        if (realcanceled && currEntry != null) currEntry.canceled = true;
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
