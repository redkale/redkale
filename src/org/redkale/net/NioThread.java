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
import java.util.function.*;
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
public class NioThread extends WorkThread {

    final Selector selector;

    private final Supplier<ByteBuffer> bufferSupplier;

    private final Consumer<ByteBuffer> bufferConsumer;

    private final Supplier<Response> responseSupplier;

    private final Consumer<Response> responseConsumer;

    private final ConcurrentLinkedQueue<Consumer<Selector>> registers = new ConcurrentLinkedQueue<>();

    private boolean closed;

    public NioThread(String name, ExecutorService workExecutor, Selector selector,
        ObjectPool<ByteBuffer> unsafeBufferPool, ObjectPool<ByteBuffer> safeBufferPool,
        ObjectPool<Response> unsafeResponsePool, ObjectPool<Response> safeResponsePool) {
        super(name, workExecutor, null);
        this.selector = selector;
        this.setDaemon(true);
        this.bufferSupplier = () -> inCurrThread() ? unsafeBufferPool.get() : safeBufferPool.get();
        this.bufferConsumer = (v) -> {
            if (inCurrThread()) {
                unsafeBufferPool.accept(v);
            } else {
                safeBufferPool.accept(v);
            }
        };
        this.responseSupplier = () -> inCurrThread() ? unsafeResponsePool.get() : safeResponsePool.get();
        this.responseConsumer = (v) -> {
            if (inCurrThread()) {
                unsafeResponsePool.accept(v);
            } else {
                safeResponsePool.accept(v);
            }
        };
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

    public Supplier<Response> getResponseSupplier() {
        return responseSupplier;
    }

    public Consumer<Response> getResponseConsumer() {
        return responseConsumer;
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
                    if (!key.isValid()) continue;
                    NioTcpAsyncConnection conn = (NioTcpAsyncConnection) key.attachment();
                    if (key.isWritable()) {
                        //key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
                        conn.doWrite();
                    } else if (key.isReadable()) {
                        conn.doRead();
                    } else if (key.isConnectable()) {
                        conn.doConnect();
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public void close() {
        this.closed = true;
        this.interrupt();
    }
}
