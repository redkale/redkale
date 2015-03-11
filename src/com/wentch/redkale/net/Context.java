/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net;

import com.wentch.redkale.watch.WatchFactory;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.logging.*;

/**
 *
 * @author zhangjx
 */
public class Context {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    protected final long serverStartTime;

    protected final ExecutorService executor;

    protected final BufferPool bufferPool;

    protected final ResponsePool responsePool;

    protected final PrepareServlet prepare;

    private final InetSocketAddress address;

    protected final Charset charset;

    protected final int maxbody;

    protected final int readTimeoutSecond;

    protected final int writeTimeoutSecond;

    protected final Logger logger;

    protected final WatchFactory watch;

    public Context(long serverStartTime, Logger logger, ExecutorService executor, BufferPool bufferPool, ResponsePool responsePool,
            final int maxbody, Charset charset, InetSocketAddress address, final PrepareServlet prepare, final WatchFactory watch,
            final int readTimeoutSecond, final int writeTimeoutSecond) {
        this.serverStartTime = serverStartTime;
        this.logger = logger;
        this.executor = executor;
        this.bufferPool = bufferPool;
        this.responsePool = responsePool;
        this.maxbody = maxbody;
        this.charset = UTF8.equals(charset) ? null : charset;
        this.address = address;
        this.prepare = prepare;
        this.watch = watch;
        this.readTimeoutSecond = readTimeoutSecond;
        this.writeTimeoutSecond = writeTimeoutSecond;
    }

    public int getMaxbody() {
        return maxbody;
    }

    public InetSocketAddress getServerAddress() {
        return address;
    }

    public long getServerStartTime() {
        return serverStartTime;
    }

    public Charset getCharset() {
        return charset;
    }

    public void submit(Runnable r) {
        executor.submit(r);
    }

    public ByteBuffer pollBuffer() {
        return bufferPool.poll();
    }

    public void offerBuffer(ByteBuffer buffer) {
        bufferPool.offer(buffer);
    }

    public Logger getLogger() {
        return logger;
    }

    public int getReadTimeoutSecond() {
        return readTimeoutSecond;
    }

    public int getWriteTimeoutSecond() {
        return writeTimeoutSecond;
    }

}
