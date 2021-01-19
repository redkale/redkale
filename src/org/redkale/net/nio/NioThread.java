/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.nio;

import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.redkale.net.TcpNioAsyncConnection;
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
public class NioThread extends Thread {

    final Selector selector;

    private final ExecutorService executor;

    private final ObjectPool<ByteBuffer> bufferPool;

    private final ConcurrentLinkedQueue<Consumer<Selector>> registers = new ConcurrentLinkedQueue<>();

    private Thread localThread;

    private boolean closed;

    public NioThread(Selector selector, ExecutorService executor, ObjectPool<ByteBuffer> bufferPool) {
        super();
        this.selector = selector;
        this.executor = executor;
        this.bufferPool = bufferPool;
        this.setDaemon(true);
    }

    public void register(Consumer<Selector> consumer) {
        registers.offer(consumer);
        selector.wakeup();
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
                    TcpNioAsyncConnection conn = (TcpNioAsyncConnection) key.attachment();
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

    public boolean inSameThread() {
        return this.localThread == Thread.currentThread();
    }

    public boolean inSameThread(Thread thread) {
        return this.localThread == thread;
    }

    public void close() {
        this.closed = true;
        this.interrupt();
    }
}
