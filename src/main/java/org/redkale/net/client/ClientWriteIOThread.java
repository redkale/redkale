/*
 *
 */
package org.redkale.net.client;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.redkale.net.AsyncIOThread;
import org.redkale.util.*;

/**
 * 客户端IO写线程
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
public class ClientWriteIOThread extends AsyncIOThread {

    private final BlockingDeque<ClientFuture> requestQueue = new LinkedBlockingDeque<>();

    public ClientWriteIOThread(String name, int index, int threads, ExecutorService workExecutor, Selector selector,
        ObjectPool<ByteBuffer> unsafeBufferPool, ObjectPool<ByteBuffer> safeBufferPool) {
        super(name, index, threads, workExecutor, selector, unsafeBufferPool, safeBufferPool);
    }

    public void offerRequest(ClientConnection conn, ClientRequest request, ClientFuture respFuture) {
        requestQueue.offer(respFuture);
    }

    public void sendHalfWrite(ClientConnection conn, Throwable halfRequestExc) {
        if (conn.pauseWriting.get()) {
            conn.pauseResuming.set(true);
            try {
                AtomicBoolean skipFirst = new AtomicBoolean(halfRequestExc != null);
                conn.pauseRequests.removeIf(e -> {
                    if (e != null) {
                        if (!skipFirst.compareAndSet(true, false)) {
                            requestQueue.offer((ClientFuture) e);
                        }
                    }
                    return true;
                });
            } finally {
                conn.pauseResuming.set(false);
                conn.pauseWriting.set(false);
                synchronized (conn.pauseRequests) {
                    conn.pauseRequests.notify();
                }
            }
        }
    }

    @Override
    public void run() {
        final ByteBuffer buffer = getBufferSupplier().get();
        final int capacity = buffer.capacity();
        final ByteArray writeArray = new ByteArray(1024 * 32);
        final Map<ClientConnection, List<ClientFuture>> map = new HashMap<>();
        final ObjectPool<List> listPool = ObjectPool.createUnsafePool(Utility.cpus() * 2, () -> new ArrayList(), null, t -> {
            t.clear();
            return true;
        });
        while (!isClosed()) {
            ClientFuture entry;
            try {
                while ((entry = requestQueue.take()) != null) {
                    map.clear();
                    {
                        Serializable reqid = entry.request.getRequestid();
                        if (reqid == null) {
                            entry.conn.responseQueue.offer(entry);
                        } else {
                            entry.conn.responseMap.put(reqid, entry);
                        }
                    }
                    if (entry.conn.pauseWriting.get()) {
                        if (entry.conn.pauseResuming.get()) {
                            try {
                                synchronized (entry.conn.pauseRequests) {
                                    entry.conn.pauseRequests.wait(3_000);
                                }
                            } catch (InterruptedException ie) {
                            }
                        }
                        entry.conn.pauseRequests.add(entry);
                    } else {
                        map.computeIfAbsent(entry.conn, c -> listPool.get()).add(entry);
                    }
                    while ((entry = requestQueue.poll()) != null) {
                        Serializable reqid = entry.request.getRequestid();
                        if (reqid == null) {
                            entry.conn.responseQueue.offer(entry);
                        } else {
                            entry.conn.responseMap.put(reqid, entry);
                        }
                        if (entry.conn.pauseWriting.get()) {
                            if (entry.conn.pauseResuming.get()) {
                                try {
                                    synchronized (entry.conn.pauseRequests) {
                                        entry.conn.pauseRequests.wait(3_000);
                                    }
                                } catch (InterruptedException ie) {
                                }
                            }
                            entry.conn.pauseRequests.add(entry);
                        } else {
                            map.computeIfAbsent(entry.conn, c -> listPool.get()).add(entry);
                        }
                    }
                    map.forEach((conn, list) -> {
                        writeArray.clear();
                        int i = -1;
                        for (ClientFuture en : list) {
                            ++i;
                            ClientRequest request = en.request;
                            request.writeTo(conn, writeArray);
                            if (!request.isCompleted()) {
                                conn.pauseWriting.set(true);
                                conn.pauseRequests.addAll(list.subList(i, list.size()));
                                break;
                            }
                        }
                        listPool.accept(list);
                        //channel.write
                        if (writeArray.length() > 0) {
                            if (writeArray.length() <= capacity) {
                                buffer.clear();
                                buffer.put(writeArray.content(), 0, writeArray.length());
                                buffer.flip();
                                conn.channel.write(buffer, conn, writeHandler);
                            } else {
                                conn.channel.write(writeArray, conn, writeHandler);
                            }
                        }
                    });
                }
            } catch (InterruptedException e) {
            }
        }
    }

    protected final CompletionHandler<Integer, ClientConnection> writeHandler = new CompletionHandler<Integer, ClientConnection>() {

        @Override
        public void completed(Integer result, ClientConnection attachment) {
        }

        @Override
        public void failed(Throwable exc, ClientConnection attachment) {
            attachment.dispose(exc);
        }
    };

}
