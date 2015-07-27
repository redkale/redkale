/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net;

import com.wentch.redkale.util.*;
import com.wentch.redkale.watch.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;

/**
 *
 * @author zhangjx
 */
public abstract class Server {

    public static final String RESNAME_ROOT = "SER_ROOT";

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    //-------------------------------------------------------------
    protected final long serverStartTime;

    protected final WatchFactory watch;

    protected final String protocol;

    protected AnyValue config;

    protected Charset charset;

    protected InetSocketAddress address;

    protected Context context;

    protected int backlog;

    protected ProtocolServer transport;

    protected int capacity;

    protected int threads;

    protected ExecutorService executor;

    protected int bufferPoolSize;

    protected int responsePoolSize;

    protected int maxbody;

    protected int readTimeoutSecond;

    protected int writeTimeoutSecond;

    private ScheduledThreadPoolExecutor scheduler;

    protected Server(long serverStartTime, String protocol, final WatchFactory watch) {
        this.serverStartTime = serverStartTime;
        this.protocol = protocol;
        this.watch = watch;
    }

    public void init(final AnyValue config) throws Exception {
        Objects.requireNonNull(config);
        this.config = config;
        this.address = new InetSocketAddress(config.getValue("host", "0.0.0.0"), config.getIntValue("port", 80));
        this.charset = Charset.forName(config.getValue("charset", "UTF-8"));
        this.backlog = config.getIntValue("backlog", 10240);
        this.readTimeoutSecond = config.getIntValue("readTimeoutSecond", 0);
        this.writeTimeoutSecond = config.getIntValue("writeTimeoutSecond", 0);
        this.capacity = config.getIntValue("capacity", 8 * 1024);
        this.maxbody = config.getIntValue("maxbody", 64 * 1024);
        this.threads = config.getIntValue("threads", Runtime.getRuntime().availableProcessors() * 16);
        this.bufferPoolSize = config.getIntValue("bufferPoolSize", Runtime.getRuntime().availableProcessors() * 512);
        this.responsePoolSize = config.getIntValue("responsePoolSize", Runtime.getRuntime().availableProcessors() * 256);
        final int port = this.address.getPort();
        final AtomicInteger counter = new AtomicInteger();
        final Format f = createFormat();
        this.executor = Executors.newFixedThreadPool(threads, (Runnable r) -> {
            Thread t = new WorkThread(executor, r);
            t.setName("Servlet-HTTP-" + port + "-Thread-" + f.format(counter.incrementAndGet()));
            return t;
        });
    }

    public void destroy(final AnyValue config) throws Exception {
        if (scheduler != null) scheduler.shutdownNow();
    }

    public InetSocketAddress getSocketAddress() {
        return address;
    }

    public Logger getLogger() {
        return this.logger;
    }

    public void start() throws IOException {
        this.context = this.createContext();
        this.context.prepare.init(this.context, config);
        if (this.watch != null) this.watch.inject(this.context.prepare);
        this.transport = ProtocolServer.create(this.protocol, context);
        this.transport.open();
        transport.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        transport.setOption(StandardSocketOptions.SO_RCVBUF, 16 * 1024 + 8);
        transport.bind(address, backlog);
        logger.info(this.getClass().getSimpleName() + " listen: " + address);
        logger.info(this.getClass().getSimpleName() + " threads: " + threads + ", bufferPoolSize: " + bufferPoolSize + ", responsePoolSize: " + responsePoolSize);
        transport.accept();
        logger.info(this.getClass().getSimpleName() + " started in " + (System.currentTimeMillis() - context.getServerStartTime()) + " ms");
    }

    protected abstract Context createContext();

    public void shutdown() throws IOException {
        long s = System.currentTimeMillis();
        logger.info(this.getClass().getSimpleName() + "-" + this.protocol + " shutdowning");
        try {
            this.transport.close();
        } catch (Exception e) {
        }
        logger.info(this.getClass().getSimpleName() + "-" + this.protocol + " shutdow prepare servlet");
        this.context.prepare.destroy(this.context, config);
        long e = System.currentTimeMillis() - s;
        logger.info(this.getClass().getSimpleName() + " shutdown in " + e + " ms");
    }

    protected Format createFormat() {
        String sf = "0";
        if (this.threads > 10) sf = "00";
        if (this.threads > 100) sf = "000";
        if (this.threads > 1000) sf = "0000";
        return new DecimalFormat(sf);
    }

    public static void loadLib(final Logger logger, final String lib) throws Exception {
        if (lib == null || lib.isEmpty()) return;
        final Set<URL> set = new HashSet<>();
        for (String s : lib.split(";")) {
            if (s.isEmpty()) continue;
            if (s.endsWith("*")) {
                File root = new File(s.substring(0, s.length() - 1));
                if (root.isDirectory()) {
                    for (File f : root.listFiles()) {
                        set.add(f.toURI().toURL());
                    }
                }
            } else {
                File f = new File(s);
                if (f.canRead()) set.add(f.toURI().toURL());
            }
        }
        if (set.isEmpty()) return;
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl instanceof URLClassLoader) {
            URLClassLoader loader = (URLClassLoader) cl;
            final Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            method.setAccessible(true);
            for (URL url : set) {
                method.invoke(loader, url);
                //if (logger != null) logger.log(Level.INFO, "Server found ClassPath({0})", url);
            }
        } else {
            Thread.currentThread().setContextClassLoader(new URLClassLoader(set.toArray(new URL[set.size()]), cl));
        }
    }

}
