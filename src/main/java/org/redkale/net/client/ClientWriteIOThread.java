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
 *
 * @author zhangjx
 */
public class ClientWriteIOThread extends AsyncIOThread {

    private final BlockingDeque<ClientEntity> requestQueue = new LinkedBlockingDeque<>();

    public ClientWriteIOThread(String name, int index, int threads, ExecutorService workExecutor, Selector selector,
        ObjectPool<ByteBuffer> unsafeBufferPool, ObjectPool<ByteBuffer> safeBufferPool) {
        super(name, index, threads, workExecutor, selector, unsafeBufferPool, safeBufferPool);
    }

    public void offerRequest(ClientConnection conn, ClientRequest request, ClientFuture respFuture) {
        requestQueue.offer(new ClientEntity(conn, request, respFuture));
    }

    public void sendHalfWrite(ClientConnection conn, Throwable halfRequestExc) {
        if (conn.pauseWriting.get()) {
            conn.pauseResuming.set(true);
            try {
                AtomicBoolean skipFirst = new AtomicBoolean(halfRequestExc != null);
                conn.pauseRequests.removeIf(e -> {
                    if (e != null) {
                        if (!skipFirst.compareAndSet(true, false)) {
                            requestQueue.offer((ClientEntity) e);
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
        final Map<ClientConnection, List<ClientEntity>> map = new HashMap<>();
        final ObjectPool<List> listPool = ObjectPool.createUnsafePool(Utility.cpus() * 2, () -> new ArrayList(), null, t -> {
            t.clear();
            return true;
        });
        while (!isClosed()) {
            ClientEntity entity;
            try {
                while ((entity = requestQueue.take()) != null) {
                    map.clear();
                    {
                        Serializable reqid = entity.request.getRequestid();
                        if (reqid == null) {
                            entity.conn.responseQueue.offer(entity.respFuture);
                        } else {
                            entity.conn.responseMap.put(reqid, entity.respFuture);
                        }
                    }
                    if (entity.conn.pauseWriting.get()) {
                        if (entity.conn.pauseResuming.get()) {
                            try {
                                synchronized (entity.conn.pauseRequests) {
                                    entity.conn.pauseRequests.wait(3_000);
                                }
                            } catch (InterruptedException ie) {
                            }
                        }
                        entity.conn.pauseRequests.add(entity);
                    } else {
                        map.computeIfAbsent(entity.conn, c -> listPool.get()).add(entity);
                    }
                    while ((entity = requestQueue.poll()) != null) {
                        Serializable reqid = entity.request.getRequestid();
                        if (reqid == null) {
                            entity.conn.responseQueue.offer(entity.respFuture);
                        } else {
                            entity.conn.responseMap.put(reqid, entity.respFuture);
                        }
                        if (entity.conn.pauseWriting.get()) {
                            if (entity.conn.pauseResuming.get()) {
                                try {
                                    synchronized (entity.conn.pauseRequests) {
                                        entity.conn.pauseRequests.wait(3_000);
                                    }
                                } catch (InterruptedException ie) {
                                }
                            }
                            entity.conn.pauseRequests.add(entity);
                        } else {
                            map.computeIfAbsent(entity.conn, c -> listPool.get()).add(entity);
                        }
                    }
                    map.forEach((conn, list) -> {
                        writeArray.clear();
                        int i = -1;
                        for (ClientEntity en : list) {
                            ++i;
                            ClientRequest request = en.request;
                            request.accept(conn, writeArray);
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

    protected static class ClientEntity {

        ClientConnection conn;

        ClientRequest request;

        ClientFuture respFuture;

        public ClientEntity(ClientConnection conn, ClientRequest request, ClientFuture respFuture) {
            this.conn = conn;
            this.request = request;
            this.respFuture = respFuture;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "_" + Objects.hash(this) + "{conn = " + conn + ", request = " + request + "}";
        }
    }
}
