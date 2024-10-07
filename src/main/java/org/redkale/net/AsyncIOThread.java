/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;
import java.util.logging.*;
import org.redkale.util.*;

/**
 * 协议处理的IO线程类
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.1.0
 */
public class AsyncIOThread extends WorkThread {

    protected static final Logger logger = Logger.getLogger(AsyncIOThread.class.getSimpleName());

    final Selector selector;

    private final Supplier<ByteBuffer> bufferSupplier;

    private final Consumer<ByteBuffer> bufferConsumer;

    private final Queue<AsyncConnection> fastQueue = new ConcurrentLinkedQueue<>();

    private final Queue<Runnable> commandQueue = new ConcurrentLinkedQueue<>();

    private final Queue<Consumer<Selector>> registerQueue = new ConcurrentLinkedQueue<>();

    private final AtomicBoolean closed = new AtomicBoolean();

    public AsyncIOThread(
            ThreadGroup g,
            String name,
            int index,
            int threads,
            ExecutorService workExecutor,
            ByteBufferPool safeBufferPool)
            throws IOException {
        super(g, name, index, threads, workExecutor, null);
        this.selector = Selector.open();
        this.setDaemon(true);
        ByteBufferPool unsafeBufferPool = ByteBufferPool.createUnsafePool(this, 512, safeBufferPool);
        this.bufferSupplier = unsafeBufferPool;
        this.bufferConsumer = unsafeBufferPool;
    }

    protected boolean isClosed() {
        return closed.get();
    }

    /**
     * 当前IOThread线程，不是IOThread返回null
     *
     * @return IOThread线程
     */
    public static AsyncIOThread currentAsyncIOThread() {
        Thread t = Thread.currentThread();
        return t instanceof AsyncIOThread ? (AsyncIOThread) t : null;
    }

    public void interestOpsOr(SelectionKey key, int opt) {
        Objects.requireNonNull(key);
        if (key.selector() != selector) {
            throw new RedkaleException("NioThread.selector not the same to SelectionKey.selector");
        }
        if ((key.interestOps() & opt) != 0) {
            return;
        }
        key.interestOps(key.interestOps() | opt);
        // 非IO线程中
        if (!inCurrThread()) {
            key.selector().wakeup();
        }
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
    public final void execute(Runnable command) {
        commandQueue.offer(command);
        selector.wakeup();
    }

    /**
     * 不可重置， 防止IO操作不在IO线程中执行
     *
     * @param commands 操作
     */
    @Override
    public final void execute(Runnable... commands) {
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
    public final void execute(Collection<Runnable> commands) {
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

    public final void fastWrite(AsyncConnection conn) {
        fastQueue.add(Objects.requireNonNull(conn));
        selector.wakeup();
    }

    public Supplier<ByteBuffer> getBufferSupplier() {
        return bufferSupplier;
    }

    public Consumer<ByteBuffer> getBufferConsumer() {
        return bufferConsumer;
    }

    /** 运行线程 */
    @Override
    public void run() {
        final Queue<Runnable> commands = this.commandQueue;
        final Queue<Consumer<Selector>> registers = this.registerQueue;
        while (!isClosed()) {
            try {
                AsyncConnection fastConn;
                while ((fastConn = fastQueue.poll()) != null) {
                    fastConn.fastPrepare(selector);
                }

                Consumer<Selector> register;
                while ((register = registers.poll()) != null) {
                    try {
                        register.accept(selector);
                    } catch (Throwable t) {
                        if (!this.closed.get()) {
                            logger.log(Level.INFO, getName() + " register run failed", t);
                        }
                    }
                }

                Runnable command;
                while ((command = commands.poll()) != null) {
                    try {
                        command.run();
                    } catch (Throwable t) {
                        if (!this.closed.get()) {
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
                    if (conn.clientMode) {
                        if (key.isConnectable()) {
                            key.interestOps(key.interestOps() & ~SelectionKey.OP_CONNECT);
                            conn.doConnect();
                        } else if (conn.writeCompletionHandler != null && key.isWritable()) {
                            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
                            conn.doWrite();
                        } else if (conn.readCompletionHandler != null && key.isReadable()) {
                            key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
                            conn.doRead(true);
                        }
                    } else {
                        if (conn.readCompletionHandler != null && key.isReadable()) {
                            key.interestOps(key.interestOps()
                                    & ~SelectionKey.OP_READ); // 不放开这行，在CompletableFuture时容易ReadPending
                            conn.doRead(true);
                        } else if (conn.writeCompletionHandler != null && key.isWritable()) {
                            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
                            conn.doWrite();
                        } else if (key.isConnectable()) {
                            key.interestOps(key.interestOps() & ~SelectionKey.OP_CONNECT);
                            conn.doConnect();
                        }
                    }
                }
            } catch (Throwable ex) {
                if (!this.closed.get()) {
                    logger.log(Level.FINE, getName() + " selector run failed", ex);
                }
            }
        }
    }

    /** 关闭线程 */
    public void close() {
        if (this.closed.compareAndSet(false, true)) {
            try {
                this.selector.close();
            } catch (Exception e) {
                logger.log(Level.FINE, getName() + " selector close failed", e);
            }
            this.interrupt();
        }
    }
}
