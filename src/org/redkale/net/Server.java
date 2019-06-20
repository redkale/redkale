/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;
import javax.net.ssl.SSLContext;
import org.redkale.util.*;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <K> 请求ID的数据类型， 例如HTTP协议请求标识为url，请求ID的数据类型就是String
 * @param <C> Context
 * @param <R> Request
 * @param <P> Response
 * @param <S> Servlet
 */
public abstract class Server<K extends Serializable, C extends Context, R extends Request<C>, P extends Response<C, R>, S extends Servlet<C, R, P>> {

    public static final String RESNAME_SERVER_ROOT = "SERVER_ROOT";

    public static final String RESNAME_SERVER_EXECUTOR = "SERVER_EXECUTOR";

    public static final String RESNAME_SERVER_RESFACTORY = "SERVER_RESFACTORY";

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    //-------------------------------------------------------------
    //服务的启动时间
    protected final long serverStartTime;

    //服务的名称
    protected String name;

    //应用层协议名
    protected final String protocol;

    //依赖注入工厂类
    protected final ResourceFactory resourceFactory;

    //服务的根Servlet
    protected final PrepareServlet<K, C, R, P, S> prepare;

    //ClassLoader
    protected RedkaleClassLoader serverClassLoader;

    //SSL
    protected SSLContext sslContext;

    //服务的上下文对象
    protected C context;

    //服务的配置信息
    protected AnyValue config;

    //服务数据的编解码，null视为UTF-8
    protected Charset charset;

    //服务的监听端口
    protected InetSocketAddress address;

    //连接队列大小
    protected int backlog;

    //传输层协议的服务
    protected ProtocolServer serverChannel;

    //ByteBuffer的容量大小
    protected int bufferCapacity;

    //线程数
    protected int threads;

    //线程池
    protected ThreadPoolExecutor executor;

    //ByteBuffer池大小
    protected int bufferPoolSize;

    //Response池大小
    protected int responsePoolSize;

    //最大连接数, 为0表示没限制
    protected int maxconns;

    //请求包大小的上限，单位:字节
    protected int maxbody;

    //Keep-Alive IO读取的超时秒数，小于1视为不设置
    protected int aliveTimeoutSeconds;

    //IO读取的超时秒数，小于1视为不设置
    protected int readTimeoutSeconds;

    //IO写入 的超时秒数，小于1视为不设置
    protected int writeTimeoutSeconds;

    protected Server(long serverStartTime, String protocol, ResourceFactory resourceFactory, PrepareServlet<K, C, R, P, S> servlet) {
        this.serverStartTime = serverStartTime;
        this.protocol = protocol;
        this.resourceFactory = resourceFactory;
        this.prepare = servlet;
    }

    public void init(final AnyValue config) throws Exception {
        Objects.requireNonNull(config);
        this.config = config;
        this.address = new InetSocketAddress(config.getValue("host", "0.0.0.0"), config.getIntValue("port", 80));
        this.charset = Charset.forName(config.getValue("charset", "UTF-8"));
        this.maxconns = config.getIntValue("maxconns", 0);
        this.aliveTimeoutSeconds = config.getIntValue("aliveTimeoutSeconds", 30);
        this.readTimeoutSeconds = config.getIntValue("readTimeoutSeconds", 0);
        this.writeTimeoutSeconds = config.getIntValue("writeTimeoutSeconds", 0);
        this.backlog = parseLenth(config.getValue("backlog"), 8 * 1024);
        this.maxbody = parseLenth(config.getValue("maxbody"), 64 * 1024);
        int bufCapacity = parseLenth(config.getValue("bufferCapacity"), "UDP".equalsIgnoreCase(protocol) ? 1350 : 32 * 1024);
        this.bufferCapacity = "UDP".equalsIgnoreCase(protocol) ? bufCapacity : (bufCapacity < 8 * 1024 ? 8 * 1024 : bufCapacity);
        this.threads = config.getIntValue("threads", Runtime.getRuntime().availableProcessors() * 32);
        this.bufferPoolSize = config.getIntValue("bufferPoolSize", this.threads * 4);
        this.responsePoolSize = config.getIntValue("responsePoolSize", this.threads * 2);
        this.name = config.getValue("name", "Server-" + protocol + "-" + this.address.getPort());
        if (!this.name.matches("^[a-zA-Z][\\w_-]{1,64}$")) throw new RuntimeException("server.name (" + this.name + ") is illegal");
        AnyValue sslConf = config.getAnyValue("ssl");
        if (sslConf != null) {
            String creatorClass = sslConf.getValue("creator", SSLCreator.class.getName());
            SSLCreator creator = null;
            if (SSLCreator.class.getName().equals(creatorClass) || creatorClass.isEmpty()) {
                creator = new SSLCreator() {
                };
            } else {
                ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                creator = ((SSLCreator) classLoader.loadClass(creatorClass).getDeclaredConstructor().newInstance());
            }
            this.resourceFactory.inject(creator);
            this.sslContext = creator.create(this, sslConf);
        }
        final AtomicInteger counter = new AtomicInteger();
        final Format f = createFormat();
        final String n = name;
        this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(threads, (Runnable r) -> {
            Thread t = new WorkThread(executor, r);
            t.setName(n + "-ServletThread-" + f.format(counter.incrementAndGet()));
            return t;
        });
    }

    protected static int parseLenth(String value, int defValue) {
        return (int) parseLenth(value, defValue + 0L);
    }

    protected static long parseLenth(String value, long defValue) {
        if (value == null) return defValue;
        value = value.toUpperCase().replace("B", "");
        if (value.endsWith("G")) return Long.decode(value.replace("G", "")) * 1024 * 1024 * 1024;
        if (value.endsWith("M")) return Long.decode(value.replace("M", "")) * 1024 * 1024;
        if (value.endsWith("K")) return Long.decode(value.replace("K", "")) * 1024;
        return Long.decode(value);
    }

    protected static String formatLenth(long value) {
        if (value < 1) return "" + value;
        if (value % (1024 * 1024 * 1024) == 0) return value / (1024 * 1024 * 1024) + "G";
        if (value % (1024 * 1024) == 0) return value / (1024 * 1024) + "M";
        if (value % 1024 == 0) return value / (1024) + "K";
        return value + "B";
    }

    public void destroy(final AnyValue config) throws Exception {
        this.prepare.destroy(context, config);
    }

    public ResourceFactory getResourceFactory() {
        return resourceFactory;
    }

    public ThreadPoolExecutor getExecutor() {
        return executor;
    }

    public InetSocketAddress getSocketAddress() {
        return address;
    }

    public String getName() {
        return name;
    }

    public String getProtocol() {
        return protocol;
    }

    public Logger getLogger() {
        return this.logger;
    }

    public PrepareServlet<K, C, R, P, S> getPrepareServlet() {
        return this.prepare;
    }

    public C getContext() {
        return this.context;
    }

    public long getServerStartTime() {
        return serverStartTime;
    }

    public Charset getCharset() {
        return charset;
    }

    public int getBacklog() {
        return backlog;
    }

    public int getBufferCapacity() {
        return bufferCapacity;
    }

    public int getThreads() {
        return threads;
    }

    public int getBufferPoolSize() {
        return bufferPoolSize;
    }

    public int getResponsePoolSize() {
        return responsePoolSize;
    }

    public int getMaxbody() {
        return maxbody;
    }

    public int getAliveTimeoutSeconds() {
        return aliveTimeoutSeconds;
    }

    public int getReadTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    public int getWriteTimeoutSeconds() {
        return writeTimeoutSeconds;
    }

    public int getMaxconns() {
        return maxconns;
    }

    public void setThreads(int threads) {
        int oldthreads = this.threads;
        this.context.executor.setCorePoolSize(threads);
        this.threads = threads;
        logger.info("[" + Thread.currentThread().getName() + "] " + this.getClass().getSimpleName() + " change threads from " + oldthreads + " to " + threads);
    }

    @SuppressWarnings("unchecked")
    public void addServlet(S servlet, final Object attachment, AnyValue conf, K... mappings) {
        this.prepare.addServlet(servlet, attachment, conf, mappings);
    }

    public void start() throws IOException {
        this.context = this.createContext();
        this.prepare.init(this.context, config);
        this.serverChannel = ProtocolServer.create(this.protocol, context, this.serverClassLoader, config == null ? null : config.getValue("netimpl"));
        this.serverChannel.open(config);
        serverChannel.bind(address, backlog);
        serverChannel.accept(this);
        final String threadName = "[" + Thread.currentThread().getName() + "] ";
        logger.info(threadName + this.getClass().getSimpleName() + ("TCP".equalsIgnoreCase(protocol) ? "" : ("." + protocol)) + " listen: " + address
            + ", threads: " + threads + ", maxbody: " + formatLenth(context.maxbody) + ", bufferCapacity: " + formatLenth(bufferCapacity) + ", bufferPoolSize: " + bufferPoolSize + ", responsePoolSize: " + responsePoolSize
            + ", started in " + (System.currentTimeMillis() - context.getServerStartTime()) + " ms");
    }

    public void changeAddress(final InetSocketAddress addr) throws IOException {
        long s = System.currentTimeMillis();
        Objects.requireNonNull(addr);
        final InetSocketAddress oldAddress = context.address;
        final ProtocolServer oldServerChannel = this.serverChannel;
        context.address = addr;
        ProtocolServer newServerChannel = null;
        try {
            newServerChannel = ProtocolServer.create(this.protocol, context, this.serverClassLoader, config == null ? null : config.getValue("netimpl"));
            newServerChannel.open(config);
            newServerChannel.bind(addr, backlog);
            newServerChannel.accept(this);
        } catch (IOException e) {
            context.address = oldAddress;
            throw e;
        }
        this.address = context.address;
        this.serverChannel = newServerChannel;
        final String threadName = "[" + Thread.currentThread().getName() + "] ";
        logger.info(threadName + this.getClass().getSimpleName() + ("TCP".equalsIgnoreCase(protocol) ? "" : ("." + protocol))
            + " change address listen: " + address + ", started in " + (System.currentTimeMillis() - s) + " ms");
        if (oldServerChannel != null) {
            new Thread() {

                @Override
                public void run() {
                    try {
                        Thread.sleep(10_000);
                        oldServerChannel.close();
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Server.changeInetSocketAddress(addr=" + addr + ") error", e);
                    }
                }
            }.start();
        }
    }

    public void changeMaxconns(final int newmaxconns) {
        this.maxconns = newmaxconns;
        if (this.context != null) this.context.maxconns = newmaxconns;
        if (this.serverChannel != null) this.serverChannel.maxconns = newmaxconns;
    }

    public void changeCharset(final Charset newcharset) {
        this.charset = newcharset;
        if (this.context != null) this.context.charset = newcharset;
    }

    public void changeMaxbody(final int newmaxbody) {
        this.maxbody = newmaxbody;
        if (this.context != null) this.context.maxbody = newmaxbody;
    }

    public void changeReadTimeoutSeconds(final int newReadTimeoutSeconds) {
        this.readTimeoutSeconds = newReadTimeoutSeconds;
        if (this.context != null) this.context.readTimeoutSeconds = newReadTimeoutSeconds;
    }

    public void changeWriteTimeoutSeconds(final int newWriteTimeoutSeconds) {
        this.writeTimeoutSeconds = newWriteTimeoutSeconds;
        if (this.context != null) this.context.writeTimeoutSeconds = newWriteTimeoutSeconds;
    }

    public void changeAliveTimeoutSeconds(final int newAliveTimeoutSeconds) {
        this.aliveTimeoutSeconds = newAliveTimeoutSeconds;
        if (this.context != null) this.context.aliveTimeoutSeconds = newAliveTimeoutSeconds;
    }

    protected abstract C createContext();

    //必须在 createContext()之后调用
    protected abstract ObjectPool<ByteBuffer> createBufferPool(AtomicLong createCounter, AtomicLong cycleCounter, int bufferPoolSize);

    //必须在 createContext()之后调用
    protected abstract ObjectPool<Response> createResponsePool(AtomicLong createCounter, AtomicLong cycleCounter, int responsePoolSize);

    //必须在 createResponsePool()之后调用
    protected abstract Creator<Response> createResponseCreator(ObjectPool<ByteBuffer> bufferPool, ObjectPool<Response> responsePool);

    public void shutdown() throws IOException {
        long s = System.currentTimeMillis();
        logger.info(this.getClass().getSimpleName() + "-" + this.protocol + " shutdowning");
        try {
            this.serverChannel.close();
        } catch (Exception e) {
        }
        logger.info(this.getClass().getSimpleName() + "-" + this.protocol + " shutdow prepare servlet");
        this.prepare.destroy(this.context, config);
        long e = System.currentTimeMillis() - s;
        logger.info(this.getClass().getSimpleName() + " shutdown in " + e + " ms");
    }

    public RedkaleClassLoader getServerClassLoader() {
        return serverClassLoader;
    }

    public void setServerClassLoader(RedkaleClassLoader serverClassLoader) {
        this.serverClassLoader = serverClassLoader;
    }

    /**
     * 判断是否存在Filter
     *
     * @param <T>         泛型
     * @param filterClass Filter类
     *
     * @return boolean
     */
    public <T extends Filter> boolean containsFilter(Class<T> filterClass) {
        return this.prepare.containsFilter(filterClass);
    }

    /**
     * 判断是否存在Filter
     *
     * @param <T>             泛型
     * @param filterClassName Filter类
     *
     * @return boolean
     */
    public <T extends Filter> boolean containsFilter(String filterClassName) {
        return this.prepare.containsFilter(filterClassName);
    }

    /**
     * 判断是否存在Servlet
     *
     * @param servletClass Servlet类
     *
     * @return boolean
     */
    public boolean containsServlet(Class<? extends S> servletClass) {
        return this.prepare.containsServlet(servletClass);
    }

    /**
     * 判断是否存在Servlet
     *
     * @param servletClassName Servlet类
     *
     * @return boolean
     */
    public boolean containsServlet(String servletClassName) {
        return this.prepare.containsServlet(servletClassName);
    }

    /**
     * 销毁Servlet
     *
     * @param servlet Servlet
     */
    public void destroyServlet(S servlet) {
        servlet.destroy(context, this.prepare.getServletConf(servlet));
    }

    //创建数
    public long getCreateConnectionCount() {
        return serverChannel == null ? -1 : serverChannel.getCreateCount();
    }

    //关闭数
    public long getClosedConnectionCount() {
        return serverChannel == null ? -1 : serverChannel.getClosedCount();
    }

    //在线数
    public long getLivingConnectionCount() {
        return serverChannel == null ? -1 : serverChannel.getLivingCount();
    }

    protected Format createFormat() {
        String sf = "0";
        if (this.threads > 10) sf = "00";
        if (this.threads > 100) sf = "000";
        if (this.threads > 1000) sf = "0000";
        return new DecimalFormat(sf);
    }

    public static URL[] loadLib(final RedkaleClassLoader classLoader, final Logger logger, final String lib) throws Exception {
        if (lib == null || lib.isEmpty()) return new URL[0];
        final Set<URL> set = new HashSet<>();
        for (String s : lib.split(";")) {
            if (s.isEmpty()) continue;
            if (s.endsWith("*")) {
                File root = new File(s.substring(0, s.length() - 1));
                if (root.isDirectory()) {
                    File[] lfs = root.listFiles();
                    if (lfs == null) throw new RuntimeException("File(" + root + ") cannot listFiles()");
                    for (File f : lfs) {
                        set.add(f.toURI().toURL());
                    }
                }
            } else {
                File f = new File(s);
                if (f.canRead()) set.add(f.toURI().toURL());
            }
        }
        if (set.isEmpty()) return new URL[0];
        for (URL url : set) {
            classLoader.addURL(url);
        }
        List<URL> list = new ArrayList<>(set);
        list.sort((URL o1, URL o2) -> o1.getFile().compareTo(o2.getFile()));
        return list.toArray(new URL[list.size()]);
    }

}
