/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.*;
import javax.net.ssl.SSLContext;
import org.redkale.boot.Application;
import org.redkale.inject.ResourceFactory;
import static org.redkale.net.AsyncGroup.UDP_BUFFER_CAPACITY;
import org.redkale.net.Filter;
import org.redkale.util.*;

/**
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <K> 请求ID的数据类型， 例如HTTP协议请求标识为url，请求ID的数据类型就是String
 * @param <C> Context
 * @param <R> Request
 * @param <P> Response
 * @param <S> Servlet
 */
public abstract class Server<
        K extends Serializable,
        C extends Context,
        R extends Request<C>,
        P extends Response<C, R>,
        S extends Servlet<C, R, P>> {

    public static final String RESNAME_SERVER_ROOT = "SERVER_ROOT";

    // @Deprecated  //@deprecated 2.3.0 使用RESNAME_APP_EXECUTOR
    // public static final String RESNAME_SERVER_EXECUTOR2 = "SERVER_EXECUTOR";
    // public static final String RESNAME_SERVER_RESFACTORY = "SERVER_RESFACTORY";
    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    // -------------------------------------------------------------
    // Application
    protected Application application;

    // 服务的启动时间
    protected final long serverStartTime;

    // 服务的名称
    protected String name;

    // 应用层协议名
    protected final String netprotocol;

    // 依赖注入工厂类
    protected final ResourceFactory resourceFactory;

    // 服务的根Servlet
    protected final DispatcherServlet<K, C, R, P, S> dispatcher;

    // ClassLoader
    protected RedkaleClassLoader serverClassLoader;

    // SSL
    protected SSLBuilder sslBuilder;

    // SSL
    protected SSLContext sslContext;

    // 服务的上下文对象
    protected C context;

    // 服务的配置信息
    protected AnyValue config;

    // 服务数据的编解码，null视为UTF-8
    protected Charset charset;

    // 服务的监听端口
    protected InetSocketAddress address;

    // 连接队列大小
    protected int backlog;

    // 传输层协议的服务
    protected ProtocolServer serverChannel;

    // ByteBuffer的容量大小
    protected int bufferCapacity;

    // ByteBuffer池大小
    protected int bufferPoolSize;

    // Response池大小
    protected int responsePoolSize;

    // 最大连接数, 为0表示没限制
    protected int maxConns;

    // 请求头大小的上限，单位:字节
    protected int maxHeader;

    // 请求包大小的上限，单位:字节
    protected int maxBody;

    // Keep-Alive IO读取的超时秒数，小于1视为不设置
    protected int aliveTimeoutSeconds;

    // IO读取的超时秒数，小于1视为不设置
    protected int readTimeoutSeconds;

    // IO写入 的超时秒数，小于1视为不设置
    protected int writeTimeoutSeconds;

    protected Server(
            Application application,
            long serverStartTime,
            String netprotocol,
            ResourceFactory resourceFactory,
            DispatcherServlet<K, C, R, P, S> servlet) {
        this.application = application;
        this.serverStartTime = serverStartTime;
        this.netprotocol = netprotocol;
        this.resourceFactory = resourceFactory;
        this.dispatcher = servlet;
        this.dispatcher.application = application;
    }

    public void init(final AnyValue config) throws Exception {
        Objects.requireNonNull(config);
        this.config = config;
        this.address = new InetSocketAddress(config.getValue("host", "0.0.0.0"), config.getIntValue("port", 80));
        this.aliveTimeoutSeconds = config.getIntValue("aliveTimeoutSeconds", 30);
        this.readTimeoutSeconds = config.getIntValue("readTimeoutSeconds", 0);
        this.writeTimeoutSeconds = config.getIntValue("writeTimeoutSeconds", 0);
        this.backlog = parseLenth(config.getValue("backlog"), 1024);
        this.charset = Charset.forName(config.getValue("charset", "UTF-8"));
        this.maxConns = config.getIntValue("maxConns", 0);
        this.maxHeader = parseLenth(config.getValue("maxHeader"), 16 * 1024);
        this.maxBody =
                parseLenth(config.getValue("maxBody"), "UDP".equalsIgnoreCase(netprotocol) ? 16 * 1024 : 256 * 1024);
        int bufCapacity = parseLenth(
                config.getValue("bufferCapacity"),
                "UDP".equalsIgnoreCase(netprotocol) ? UDP_BUFFER_CAPACITY : 32 * 1024);
        this.bufferCapacity =
                "UDP".equalsIgnoreCase(netprotocol) ? bufCapacity : (bufCapacity < 1024 ? 1024 : bufCapacity);
        this.bufferPoolSize = config.getIntValue("bufferPoolSize", ByteBufferPool.DEFAULT_BUFFER_POOL_SIZE);
        this.responsePoolSize = config.getIntValue("responsePoolSize", 1024);
        this.name = config.getValue(
                "name",
                "Server-"
                        + config.getValue("protocol", netprotocol)
                                .replaceFirst("\\..+", "")
                                .toUpperCase() + "-" + this.address.getPort());
        if (!this.name.matches("^[a-zA-Z][\\w_-]{1,64}$")) {
            throw new RedkaleException("server.name (" + this.name + ") is illegal");
        }
        AnyValue sslConf = config.getAnyValue("ssl");
        if (sslConf != null) {
            String builderClass = sslConf.getValue("builder", SSLBuilder.class.getName());
            SSLBuilder builder = null;
            if (SSLBuilder.class.getName().equals(builderClass) || builderClass.isEmpty()) {
                builder = new SSLBuilder();
            } else {
                ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                Class clazz = classLoader.loadClass(builderClass);
                RedkaleClassLoader.putReflectionDeclaredConstructors(clazz, clazz.getName());
                builder = ((SSLBuilder) classLoader
                        .loadClass(builderClass)
                        .getDeclaredConstructor()
                        .newInstance());
            }
            this.resourceFactory.inject(builder);
            SSLContext sslc = builder.createSSLContext(this, sslConf);
            if (sslc != null) {
                this.sslBuilder = builder;
                this.sslContext = sslc;
                final boolean dtls = sslc.getProtocol().toUpperCase().startsWith("DTLS");
                // SSL模式下， size必须大于 5+16+16384+256+48+(isDTLS?0:16384) = 16k*1/2+325 = 16709/33093  见:
                // sun.security.ssl.SSLRecord.maxLargeRecordSize
                int maxLen = dtls ? 16709 : 33093;
                if (maxLen > this.bufferCapacity) {
                    int newLen = dtls ? (17 * 1024) : (33 * 1024); // 取个1024的整倍数
                    logger.info(this.getClass().getSimpleName() + " change bufferCapacity " + this.bufferCapacity
                            + " to " + newLen + " for SSL size " + maxLen);
                    this.bufferCapacity = newLen;
                }
            }
        }
        this.context = this.createContext();
    }

    protected static int parseLenth(String value, int defValue) {
        return (int) parseLenth(value, defValue + 0L);
    }

    protected static long parseLenth(String value, long defValue) {
        if (value == null) {
            return defValue;
        }
        value = value.toUpperCase().replace("B", "");
        if (value.indexOf('.') >= 0) {
            if (value.endsWith("G")) {
                return (long) (Float.parseFloat(value.replace("G", "")) * 1024 * 1024 * 1024);
            }
            if (value.endsWith("M")) {
                return (long) (Float.parseFloat(value.replace("M", "")) * 1024 * 1024);
            }
            if (value.endsWith("K")) {
                return (long) (Float.parseFloat(value.replace("K", "")) * 1024);
            }
            return (long) Float.parseFloat(value);
        }
        if (value.endsWith("G")) {
            return Long.decode(value.replace("G", "")) * 1024 * 1024 * 1024;
        }
        if (value.endsWith("M")) {
            return Long.decode(value.replace("M", "")) * 1024 * 1024;
        }
        if (value.endsWith("K")) {
            return Long.decode(value.replace("K", "")) * 1024;
        }
        return Long.decode(value);
    }

    protected static String formatLenth(long value) {
        if (value < 1) {
            return "" + value;
        }
        if (value % (1024 * 1024 * 1024) == 0) {
            return value / (1024 * 1024 * 1024) + "G";
        }
        if (value % (1024 * 1024) == 0) {
            return value / (1024 * 1024) + "M";
        }
        if (value % 1024 == 0) {
            return value / (1024) + "K";
        }
        if (value >= 1000) {
            return "" + value;
        }
        return value + "B";
    }

    public void destroy(final AnyValue config) throws Exception {
        this.dispatcher.destroy(context, config);
    }

    public ResourceFactory getResourceFactory() {
        return resourceFactory;
    }

    public InetSocketAddress getSocketAddress() {
        return address;
    }

    public String getName() {
        return name;
    }

    public String getNetprotocol() {
        return netprotocol;
    }

    public Logger getLogger() {
        return this.logger;
    }

    public DispatcherServlet<K, C, R, P, S> getDispatcherServlet() {
        return this.dispatcher;
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

    public int getBufferPoolSize() {
        return bufferPoolSize;
    }

    public int getResponsePoolSize() {
        return responsePoolSize;
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

    public int getMaxConns() {
        return maxConns;
    }

    public int getMaxHeader() {
        return maxHeader;
    }

    public int getMaxBody() {
        return maxBody;
    }

    @SuppressWarnings("unchecked")
    public void addServlet(S servlet, final Object attachment, AnyValue conf, K... mappings) {
        this.dispatcher.addServlet(servlet, attachment, conf, mappings);
    }

    public void start() throws IOException {
        this.dispatcher.init(
                this.context, config); // 不能在init方法内执行，因Server.init执行后会调用loadService,loadServlet, 再执行Server.start
        this.postPrepareInit();
        this.serverChannel = ProtocolServer.create(this.netprotocol, context, this.serverClassLoader);
        this.resourceFactory.inject(this.serverChannel);
        this.serverChannel.open(config);
        serverChannel.bind(address, backlog);
        SocketAddress localAddress = serverChannel.getLocalAddress();
        if (localAddress instanceof InetSocketAddress && !Objects.equals(localAddress, this.address)) {
            this.address = (InetSocketAddress) localAddress;
            // this.context.updateServerAddress(this.address);
        }
        serverChannel.accept(application, this);
        postStart();
        ExecutorService workExecutor = context.workExecutor;
        int workThreads = 0;
        if (workExecutor instanceof ThreadPoolExecutor) {
            workThreads = ((ThreadPoolExecutor) workExecutor).getCorePoolSize();
        } else if (workExecutor != null) { // virtual thread pool
            workThreads = -1;
        }
        logger.info(this.getClass().getSimpleName() + ("TCP".equalsIgnoreCase(netprotocol) ? "" : ("." + netprotocol))
                + " listen: " + (address.getHostString() + ":" + address.getPort())
                + ", cpu: " + Utility.cpus() + ", workThreads: " + (workThreads >= 0 ? workThreads : "[virtual]")
                + ", responsePoolSize: " + responsePoolSize + ", bufferPoolSize: " + bufferPoolSize
                + ", bufferCapacity: " + formatLenth(bufferCapacity) + ", maxbody: " + formatLenth(context.maxBody)
                + startExtLog()
                + ", started in " + (System.currentTimeMillis() - context.getServerStartTime()) + " ms\r\n");
    }

    protected String startExtLog() {
        return "";
    }

    protected void postPrepareInit() {}

    protected void postStart() {}

    public void changeAddress(Application application, final InetSocketAddress addr) throws IOException {
        long s = System.currentTimeMillis();
        Objects.requireNonNull(addr);
        final ProtocolServer oldServerChannel = this.serverChannel;
        ProtocolServer newServerChannel = null;
        InetSocketAddress addr0 = addr;
        try {
            newServerChannel = ProtocolServer.create(this.netprotocol, context, this.serverClassLoader);
            this.resourceFactory.inject(newServerChannel);
            newServerChannel.open(config);
            newServerChannel.bind(addr, backlog);
            SocketAddress localAddress = newServerChannel.getLocalAddress();
            if (localAddress instanceof InetSocketAddress) {
                addr0 = (InetSocketAddress) localAddress;
            }
            newServerChannel.accept(application, this);
        } catch (IOException e) {
            throw e;
        }
        context.updateServerAddress(addr0);
        this.address = context.serverAddress;
        this.serverChannel = newServerChannel;
        logger.info(this.getClass().getSimpleName() + ("TCP".equalsIgnoreCase(netprotocol) ? "" : ("." + netprotocol))
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

    public void changeCharset(final Charset newCharset) {
        this.charset = newCharset;
        if (this.context != null) {
            this.context.charset = newCharset;
        }
    }

    public void changeMaxconns(final int newMaxConns) {
        this.maxConns = newMaxConns;
        if (this.context != null) {
            this.context.maxConns = newMaxConns;
        }
        if (this.serverChannel != null) {
            this.serverChannel.maxConns = newMaxConns;
        }
    }

    public void changeMaxHeader(final int newMaxHeader) {
        this.maxHeader = newMaxHeader;
        if (this.context != null) {
            this.context.maxHeader = newMaxHeader;
        }
    }

    public void changeMaxBody(final int newMaxBody) {
        this.maxBody = newMaxBody;
        if (this.context != null) {
            this.context.maxBody = newMaxBody;
        }
    }

    public void changeReadTimeoutSeconds(final int newReadTimeoutSeconds) {
        this.readTimeoutSeconds = newReadTimeoutSeconds;
        if (this.context != null) {
            this.context.readTimeoutSeconds = newReadTimeoutSeconds;
        }
    }

    public void changeWriteTimeoutSeconds(final int newWriteTimeoutSeconds) {
        this.writeTimeoutSeconds = newWriteTimeoutSeconds;
        if (this.context != null) {
            this.context.writeTimeoutSeconds = newWriteTimeoutSeconds;
        }
    }

    public void changeAliveTimeoutSeconds(final int newAliveTimeoutSeconds) {
        this.aliveTimeoutSeconds = newAliveTimeoutSeconds;
        if (this.context != null) {
            this.context.aliveTimeoutSeconds = newAliveTimeoutSeconds;
        }
    }

    protected abstract C createContext();

    protected void initContextConfig(Context.ContextConfig contextConfig) {
        if (application != null) {
            contextConfig.nodeid = application.getNodeid();
            contextConfig.workExecutor = application.getWorkExecutor();
        }
        contextConfig.serverStartTime = this.serverStartTime;
        contextConfig.logger = this.logger;
        contextConfig.sslBuilder = this.sslBuilder;
        contextConfig.sslContext = this.sslContext;
        contextConfig.bufferCapacity = this.bufferCapacity;
        contextConfig.maxConns = this.maxConns;
        contextConfig.maxHeader = this.maxHeader;
        contextConfig.maxBody = this.maxBody;
        contextConfig.charset = this.charset;
        contextConfig.serverAddress = this.address;
        contextConfig.dispatcher = this.dispatcher;
        contextConfig.resourceFactory = this.resourceFactory;
        contextConfig.aliveTimeoutSeconds = this.aliveTimeoutSeconds;
        contextConfig.readTimeoutSeconds = this.readTimeoutSeconds;
        contextConfig.writeTimeoutSeconds = this.writeTimeoutSeconds;
    }

    // 必须在 createContext()之后调用
    protected abstract ByteBufferPool createSafeBufferPool(
            LongAdder createCounter, LongAdder cycleCounter, int bufferPoolSize);

    // 必须在 createContext()之后调用
    protected abstract ObjectPool<P> createSafeResponsePool(
            LongAdder createCounter, LongAdder cycleCounter, int responsePoolSize);

    public void shutdown() throws IOException {
        long s = System.currentTimeMillis();
        logger.info(this.getClass().getSimpleName() + "-" + this.netprotocol + " shutdowning");
        try {
            this.serverChannel.close();
        } catch (Exception e) {
            // do nothing
        }
        logger.info(this.getClass().getSimpleName() + "-" + this.netprotocol + " shutdow prepare servlet");
        this.dispatcher.destroy(this.context, config);
        long e = System.currentTimeMillis() - s;
        logger.info(this.getClass().getSimpleName() + "-" + this.netprotocol + " shutdown in " + e + " ms");
    }

    public RedkaleClassLoader getServerClassLoader() {
        return serverClassLoader;
    }

    public void setServerClassLoader(RedkaleClassLoader serverClassLoader) {
        this.serverClassLoader = serverClassLoader;
    }

    // 必须在Server.start执行后才能调用此方法
    public AsyncGroup getAsyncGroup() {
        if (this.serverChannel == null) {
            throw new RedkaleException("Server is not running");
        }
        return this.serverChannel.getAsyncGroup();
    }

    /**
     * 判断是否存在Filter
     *
     * @param <T> 泛型
     * @param filterClass Filter类
     * @return boolean
     */
    public <T extends Filter> boolean containsFilter(Class<T> filterClass) {
        return this.dispatcher.containsFilter(filterClass);
    }

    /**
     * 判断是否存在Filter
     *
     * @param filterClassName Filter类
     * @return boolean
     */
    public boolean containsFilter(String filterClassName) {
        return this.dispatcher.containsFilter(filterClassName);
    }

    /**
     * 销毁Servlet
     *
     * @param <T> 泛型
     * @param filter Filter
     */
    public <T extends Filter> void destroyFilter(T filter) {
        filter.destroy(context, this.dispatcher.getFilterConf(filter));
    }

    /**
     * 判断是否存在Servlet
     *
     * @param servletClass Servlet类
     * @return boolean
     */
    public boolean containsServlet(Class<? extends S> servletClass) {
        return this.dispatcher.containsServlet(servletClass);
    }

    /**
     * 判断是否存在Servlet
     *
     * @param servletClassName Servlet类
     * @return boolean
     */
    public boolean containsServlet(String servletClassName) {
        return this.dispatcher.containsServlet(servletClassName);
    }

    /**
     * 销毁Servlet
     *
     * @param servlet Servlet
     */
    public void destroyServlet(S servlet) {
        servlet.destroy(context, this.dispatcher.getServletConf(servlet));
    }

    // 创建数
    public long getCreateConnectionCount() {
        return serverChannel == null ? -1 : serverChannel.getCreateConnectionCount();
    }

    // 关闭数
    public long getClosedConnectionCount() {
        return serverChannel == null ? -1 : serverChannel.getClosedConnectionCount();
    }

    // 在线数
    public long getLivingConnectionCount() {
        return serverChannel == null ? -1 : serverChannel.getLivingConnectionCount();
    }

    public static URI[] loadLib(final RedkaleClassLoader classLoader, final Logger logger, final String lib) {
        if (lib == null || lib.isEmpty()) {
            return new URI[0];
        }
        final Set<URI> set = new HashSet<>();
        for (String s : lib.split(";")) {
            if (s.isEmpty()) {
                continue;
            }
            if (s.endsWith("*")) {
                File root = new File(s.substring(0, s.length() - 1));
                if (root.isDirectory()) {
                    File[] lfs = root.listFiles();
                    if (lfs == null) {
                        throw new RedkaleException("File(" + root + ") cannot listFiles()");
                    }
                    for (File f : lfs) {
                        set.add(f.toURI());
                    }
                }
            } else {
                File f = new File(s);
                if (f.canRead()) {
                    set.add(f.toURI());
                }
            }
        }
        if (set.isEmpty()) {
            return new URI[0];
        }
        for (URI uri : set) {
            classLoader.addURI(uri);
        }
        List<URI> list = new ArrayList<>(set);
        list.sort((URI o1, URI o2) -> o1.toASCIIString().compareTo(o2.toASCIIString()));
        return list.toArray(new URI[list.size()]);
    }
}
