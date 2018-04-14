/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.logging.Logger;
import org.redkale.net.AsyncConnection;
import org.redkale.util.ObjectPool;

/**
 *
 * @author zhangjx
 */
public abstract class PoolTcpSource extends PoolSource<AsyncConnection> {

    //ByteBuffer池
    protected ObjectPool<ByteBuffer> bufferPool;

    //线程池
    protected ThreadPoolExecutor executor;

    //TCP Channel组
    protected AsynchronousChannelGroup group;

    public PoolTcpSource(String stype, Properties prop, Logger logger, ObjectPool<ByteBuffer> bufferPool,ThreadPoolExecutor executor) {
        super(stype, prop, logger);
        this.bufferPool = bufferPool;
        this.executor = executor;
        try {
            this.group = AsynchronousChannelGroup.withThreadPool(executor);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public final boolean isAysnc() {
        return true;
    }

    @Override
    public final AsyncConnection poll() {
        return pollAsync().join();
    }

    @Override
    public CompletableFuture<AsyncConnection> pollAsync() {
        return AsyncConnection.createTCP(group, this.addr, this.readTimeoutSeconds, this.writeTimeoutSeconds);
    }
}
