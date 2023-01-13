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

    //如果有read/write两IOThread，只记readThread
    final AtomicInteger connCounter = new AtomicInteger();

    private final Supplier<ByteBuffer> bufferSupplier;

    private final Consumer<ByteBuffer> bufferConsumer;

    private final Queue<Runnable> commandQueue = Utility.unsafe() != null ? new MpscGrowableArrayQueue<>(16, 1 << 16) : new ConcurrentLinkedQueue<>();

    private final Queue<Consumer<Selector>> registerQueue = Utility.unsafe() != null ? new MpscGrowableArrayQueue<>(16, 1 << 16) : new ConcurrentLinkedQueue<>();

    private boolean closed;

    public AsyncIOThread(String name, int index, int threads, ExecutorService workExecutor, Selector selector,
        ObjectPool<ByteBuffer> unsafeBufferPool, ObjectPool<ByteBuffer> safeBufferPool) {
        super(name, index, threads, workExecutor, null);
        this.selector = selector;
        this.setDaemon(true);
        this.bufferSupplier = () -> (inCurrThread() ? unsafeBufferPool : safeBufferPool).get();
        this.bufferConsumer = (v) -> (inCurrThread() ? unsafeBufferPool : safeBufferPool).accept(v);
    }

    protected boolean isClosed() {
        return closed;
    }

    public static AsyncIOThread currAsyncIOThread() {
        Thread t = Thread.currentThread();
        return t instanceof AsyncIOThread ? (AsyncIOThread) t : null;
    }

    /**
     * 是否IO线程
     *
     * @return boolean
     */
    @Override
    public final boolean inIO() {
        return true;
    }

    /**
     * 不可重置， 防止IO操作不在IO线程中执行
     *
     * @param command 操作
     */
    @Override
    public void execute(Runnable command) {
        commandQueue.offer(command);
        selector.wakeup();
    }

    /**
     * 不可重置， 防止IO操作不在IO线程中执行
     *
     * @param commands 操作
     */
    @Override
    public void execute(Runnable... commands) {
        for (Runnable command : commands) {
            commandQueue.offer(command);
        }
        selector.wakeup();
    }

    /**
     * 不可重置， 防止IO操作不在IO线程中执行
     *
     * @param commands 操作
     */
    @Override
    public void execute(Collection<Runnable> commands) {
        if (commands != null) {
            for (Runnable command : commands) {
                commandQueue.offer(command);
            }
            selector.wakeup();
        }
    }

    public final void register(Consumer<Selector> consumer) {
        registerQueue.offer(consumer);
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
        final Queue<Runnable> commands = this.commandQueue;
        final Queue<Consumer<Selector>> registers = this.registerQueue;
        while (!isClosed()) {
            try {
                Consumer<Selector> register;
                while ((register = registers.poll()) != null) {
                    try {
                        register.accept(selector);
                    } catch (Throwable t) {
                        if (!this.closed) {
                            logger.log(Level.INFO, getName() + " register run failed", t);
                        }
                    }
                }
                Runnable command;
                while ((command = commands.poll()) != null) {
                    try {
                        command.run();
                    } catch (Throwable t) {
                        if (!this.closed) {
                            logger.log(Level.INFO, getName() + " command run failed", t);
                        }
                    }
                }
                int count = selector.select();
                if (count == 0) {
                    continue;
                }

                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> it = keys.iterator();
                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove();
                    if (!key.isValid()) {
                        continue;
                    }
                    AsyncNioConnection conn = (AsyncNioConnection) key.attachment();
                    if (conn.client) {
                        if (key.isConnectable()) {
                            key.interestOps(key.interestOps() & ~SelectionKey.OP_CONNECT);
                            conn.doConnect();
                        } else if (conn.readCompletionHandler != null && key.isReadable()) {
                            conn.currReadInvoker = 0;
                            key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
                            conn.doRead(true);
                        } else if (conn.writeCompletionHandler != null && key.isWritable()) {
                            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
                            conn.doWrite(true);
                        }
                    } else {
                        if (conn.readCompletionHandler != null && key.isReadable()) {
                            conn.currReadInvoker = 0;
                            key.interestOps(key.interestOps() & ~SelectionKey.OP_READ); //不放开这行，在CompletableFuture时容易ReadPending
                            conn.doRead(true);
                        } else if (conn.writeCompletionHandler != null && key.isWritable()) {
                            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
                            conn.doWrite(true);
                        } else if (key.isConnectable()) {
                            key.interestOps(key.interestOps() & ~SelectionKey.OP_CONNECT);
                            conn.doConnect();
                        }
                    }
                }
            } catch (Throwable ex) {
                if (!this.closed) {
                    logger.log(Level.FINE, getName() + " selector run failed", ex);
                }
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
