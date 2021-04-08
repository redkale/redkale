/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;
import java.util.logging.*;
import org.redkale.util.*;

/**
 * 协议处理的IO线程类
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.1.0
 */
public class AsyncIOThread extends WorkThread {

    protected static final Logger logger = Logger.getLogger(AsyncIOThread.class.getSimpleName());

    final Selector selector;

    final AtomicInteger connCounter = new AtomicInteger();

    private final Supplier<ByteBuffer> bufferSupplier;

    private final Consumer<ByteBuffer> bufferConsumer;

    private final ConcurrentLinkedQueue<Consumer<Selector>> registers = new ConcurrentLinkedQueue<>();

    private boolean closed;

    int invoker = 0;

    public AsyncIOThread(final boolean readable, String name, ExecutorService workExecutor, Selector selector,
        ObjectPool<ByteBuffer> unsafeBufferPool, ObjectPool<ByteBuffer> safeBufferPool) {
        super(name, workExecutor, null);
        this.selector = selector;
        this.setDaemon(true);
        this.bufferSupplier = () -> (inCurrThread() ? unsafeBufferPool : safeBufferPool).get();
        this.bufferConsumer = (v) -> (inCurrThread() ? unsafeBufferPool : safeBufferPool).accept(v);
    }

    public void register(Consumer<Selector> consumer) {
        registers.offer(consumer);
        selector.wakeup();
    }

    public Supplier<ByteBuffer> getBufferSupplier() {
        return bufferSupplier;
    }

    public Consumer<ByteBuffer> getBufferConsumer() {
        return bufferConsumer;
    }

    public int currConnections() {
        return connCounter.get();
    }

    @Override
    public void run() {
        this.localThread = Thread.currentThread();
        while (!this.closed) {
            try {
                Consumer<Selector> register;
                while ((register = registers.poll()) != null) {
                    register.accept(selector);
                }
                int count = selector.select();
                if (count == 0) continue;
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> it = keys.iterator();
                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove();
                    invoker = 0;
                    if (!key.isValid()) continue;
                    AsyncNioConnection conn = (AsyncNioConnection) key.attachment();
                    if (conn.client) {
                        if (key.isConnectable()) {
                            key.interestOps(key.interestOps() & ~SelectionKey.OP_CONNECT);
                            conn.doConnect();
                        } else if (key.isReadable()) {
                            conn.currReadInvoker = 0;
                            key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
                            conn.doRead(true);
                        } else if (key.isWritable()) {
                            conn.currWriteInvoker = 0;
                            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
                            conn.doWrite(true);
                        }
                    } else {
                        if (key.isReadable()) {
                            conn.currReadInvoker = 0;
                            //key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
                            conn.doRead(true);
                        } else if (conn.writeCompletionHandler != null && key.isWritable()) {
                            conn.currWriteInvoker = 0;
                            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
                            conn.doWrite(true);
                        } else if (key.isConnectable()) {
                            key.interestOps(key.interestOps() & ~SelectionKey.OP_CONNECT);
                            conn.doConnect();
                        }
                    }
                }
            } catch (Exception ex) {
                if (!this.closed) logger.log(Level.FINE, getName() + " selector run failed", ex);
            }
        }
    }

    public void close() {
        this.closed = true;
        this.interrupt();
        try {
            this.selector.close();
        } catch (Exception e) {
            logger.log(Level.FINE, getName() + " selector close failed", e);
        }
    }
}
