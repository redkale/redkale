/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.boot;

import com.wentch.redkale.net.sncp.ServiceEntry;
import com.wentch.redkale.net.Server;
import com.wentch.redkale.net.sncp.Sncp;
import com.wentch.redkale.service.Service;
import com.wentch.redkale.service.MultiService;
import com.wentch.redkale.util.AnyValue;
import com.wentch.redkale.util.Ignore;
import com.wentch.redkale.boot.ClassFilter.FilterEntry;
import com.wentch.redkale.util.AnyValue.DefaultAnyValue;
import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.*;

/**
 *
 * @author zhangjx
 */
public abstract class NodeServer {

    public static final String LINE_SEPARATOR = "\r\n";

    protected final Logger logger;

    protected final Application application;

    private final CountDownLatch servicecdl;

    private final Server server;

    protected Consumer<ServiceEntry> consumer;

    public NodeServer(Application application, CountDownLatch servicecdl, Server server) {
        this.application = application;
        this.servicecdl = servicecdl;
        this.server = server;
        this.logger = Logger.getLogger(this.getClass().getSimpleName());
    }

    public void prepare(final AnyValue config) throws Exception {
    }

    public void init(AnyValue config) throws Exception {
        //设置root文件夹
        String webroot = config.getValue("root", "root");
        File myroot = new File(webroot);
        if (!webroot.contains(":") && !webroot.startsWith("/")) {
            myroot = new File(System.getProperty(Application.RESNAME_HOME), webroot);
        }
        final String homepath = myroot.getCanonicalPath();
        Server.loadLib(logger, config.getValue("lib", "") + ";" + homepath + "/lib/*;" + homepath + "/classes");
    }

    public abstract InetSocketAddress getSocketAddress();

    public void start() throws IOException {
        server.start();
    }

    public void shutdown() throws IOException {
        server.shutdown();
    }

    protected void loadLocalService(final Set<FilterEntry<Service>> entrys) throws Exception {
        final HashMap<Class, ServiceEntry> localServices = application.localServices;
        for (final FilterEntry<Service> entry : entrys) {
            Class<? extends Service> serviceClass = entry.getType();
            if (serviceClass.getAnnotation(Ignore.class) != null) continue;
            final boolean multi = MultiService.class.isAssignableFrom(serviceClass);
            if (!multi) {  //单例模式
                synchronized (application.localServices) {
                    ServiceEntry old = localServices.get(serviceClass);
                    if (old == null) {
                        old = new ServiceEntry(serviceClass, (Service) serviceClass.newInstance(), entry.getProperty(), "");
                        localServices.put(serviceClass, old);
                    }
                    if (consumer != null) consumer.accept(old);
                    continue;
                }
            }
            String name = multi ? entry.getName() : "";
            synchronized (application.localServices) {
                ServiceEntry old = localServices.get(serviceClass);
                if (old != null && old.containsName(name)) {
                    if (consumer != null) consumer.accept(old);
                    continue;
                }
                final Service service = (Service) serviceClass.newInstance();
                if (old == null) {
                    old = new ServiceEntry(serviceClass, service, entry.getProperty(), name);
                    localServices.put(serviceClass, old);
                } else {
                    old.addName(name);
                }
                if (consumer != null) consumer.accept(old);
            }
        }
    }

    protected void loadRemoteService(final Set<FilterEntry<Service>> entrys) throws Exception {
        for (FilterEntry<Service> entry : entrys) {
            Class<Service> serviceClass = entry.getType();
            if (serviceClass.getAnnotation(Ignore.class) != null) continue;
            String remote = entry.getAttachment().toString();
            Service service = Sncp.createRemoteService(entry.getName(), serviceClass, remote);
            synchronized (application.remoteServices) {
                application.remoteServices.add(new ServiceEntry(serviceClass, service, entry.getProperty(), entry.getName()));
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected void loadService(final AnyValue servicesConf, ClassFilter serviceFilter) throws Exception {
        if (serviceFilter == null) return;
        final String threadName = "[" + Thread.currentThread().getName() + "] ";
        final Set<FilterEntry<Service>> entrys = serviceFilter.getFilterEntrys();
        final Set<FilterEntry<Service>> localentrys = new HashSet<>(entrys.size());
        final Set<FilterEntry<Service>> remotentrys = new HashSet<>(entrys.size());
        final String defremote = getRemoteName(servicesConf, "LOCAL");
        for (FilterEntry<Service> entry : entrys) { //service实现类
            final Class type = entry.getType();
            if (type.isInterface()) continue;
            if (Modifier.isFinal(type.getModifiers())) continue;
            if (!Modifier.isPublic(type.getModifiers())) continue;
            if (Modifier.isAbstract(type.getModifiers())) continue;
            if (type.getAnnotation(Ignore.class) != null) continue;
            final String remote = getRemoteName(entry.getProperty(), defremote);
            if ("LOCAL".equals(remote)) { //本地模式
                localentrys.add(entry);
            } else { //远程模式  
                entry.setAttachment(remote);
                remotentrys.add(entry);
            }
        }
        loadLocalService(localentrys);
        loadRemoteService(remotentrys);
        servicecdl.countDown();
        servicecdl.await();

        final StringBuilder sb = logger.isLoggable(Level.INFO) ? new StringBuilder() : null;
        synchronized (application) {
            if (!application.serviceInited) {
                application.serviceInited = true;
                //--------------- register ---------------
                application.localServices.forEach((x, y) -> {
                    y.getNames().forEach(n -> application.factory.register(n, y.getServiceClass(), y.getService()));
                });
                application.remoteServices.forEach(y -> {
                    y.getNames().forEach(n -> application.factory.register(n, y.getServiceClass(), y.getService()));
                });
                //---------------- inject ----------------
                application.localServices.forEach((x, y) -> {
                    application.factory.inject(y.getService());
                });
                application.remoteServices.forEach(y -> {
                    application.factory.inject(y.getService());
                });
                //----------------- init -----------------
                application.localServices.entrySet().parallelStream().forEach(k -> {
                    Class x = k.getKey();
                    ServiceEntry y = k.getValue();
                    long s = System.currentTimeMillis();
                    y.getService().init(y.getServiceConf());
                    long e = System.currentTimeMillis() - s;
                    if (e > 2 && sb != null) {
                        sb.append(threadName).append("LocalService(").append(y.getNames()).append("|").append(y.getServiceClass()).append(") init ").append(e).append("ms").append(LINE_SEPARATOR);
                    }
                });
                if (sb != null && sb.length() > 0) logger.log(Level.INFO, sb.toString());
            }
        }
    }

    private String getRemoteName(AnyValue av, String remote) {
        remote = (remote == null || remote.trim().isEmpty()) ? "LOCAL" : remote.trim();
        if (av == null) return remote;
        String r = av.getValue("remote");
        if ("LOCAL".equalsIgnoreCase(r)) return "LOCAL";
        if (r != null && !r.trim().isEmpty()) return r.trim();
        return remote;
    }

    protected static ClassFilter<Service> createServiceClassFilter(final String localNode, final AnyValue config) {
        return createClassFilter(localNode, config, null, Service.class, Annotation.class, "services", "service");
    }

    protected static ClassFilter createClassFilter(final String localNode, final AnyValue config, Class<? extends Annotation> ref,
            Class inter, Class<? extends Annotation> ref2, String properties, String property) {
        ClassFilter filter = new ClassFilter(ref, inter);
        if (properties == null && properties == null) return filter;
        AnyValue list = config == null ? null : config.getAnyValue(properties);
        if (list == null) return filter;
        if ("services".equals(properties)) {
            for (AnyValue group : list.getAnyValues("group")) {
                String remotenames = group.getValue("remotenames");
                for (AnyValue propnode : group.getAnyValues(property)) {
                    boolean hasremote = false;
                    if (remotenames != null) {
                        for (String rn : remotenames.split(";")) {
                            rn = rn.trim();
                            if (rn.isEmpty() || localNode.equals(rn)) continue;
                            DefaultAnyValue s = new DefaultAnyValue();
                            s.addValue("value", propnode.getValue("value"));
                            s.addValue("name", rn);
                            s.addValue("remote", rn);
                            ((DefaultAnyValue) list).addValue(property, s);
                            hasremote = true;
                        }
                    }
                    if (hasremote) {
                        ((DefaultAnyValue) propnode).setValue("name", localNode);
                        ((DefaultAnyValue) propnode).setValue("remote", "");
                        ((DefaultAnyValue) list).addValue(property, propnode);
                    }
                }
            }
        }
        for (AnyValue av : list.getAnyValues(property)) {
            for (AnyValue prop : av.getAnyValues("property")) {
                ((DefaultAnyValue) av).addValue(prop.getValue("name"), prop.getValue("value"));
            }
            filter.filter(av, av.getValue("value"), false);
        }
        if (list.getBoolValue("autoload", true)) {
            String includes = list.getValue("includes", "");
            String excludes = list.getValue("excludes", "");
            filter.setIncludePatterns(includes.split(";"));
            filter.setExcludePatterns(excludes.split(";"));
        } else {
            if (ref2 == null || ref2 == Annotation.class) {
                filter.setRefused(true);
            } else if (ref2 != Annotation.class) {
                filter.setAnnotationClass(ref2);
            }
        }
        return filter;
    }

}
