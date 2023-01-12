/*
 *
 */
package org.redkale.net.client;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.redkale.util.*;

/**
 *
 * @author zhangjx
 */
public class ClientWriteIOThread extends ClientIOThread {

    private final BlockingQueue<ClientEntity> requestQueue = new LinkedBlockingQueue<>();

    public ClientWriteIOThread(String name, int index, int threads, ExecutorService workExecutor, Selector selector,
        ObjectPool<ByteBuffer> unsafeBufferPool, ObjectPool<ByteBuffer> safeBufferPool) {
        super(name, index, threads, workExecutor, selector, unsafeBufferPool, safeBufferPool);
    }

    public void offerRequest(ClientConnection conn, ClientRequest request, ClientFuture respFuture) {
        requestQueue.offer(new ClientEntity(conn, request, respFuture));
    }

    public void wakeupWrite() {
        synchronized (writeHandler) {
            writeHandler.notify();
        }
    }

    @Override
    public void run() {
        final ByteBuffer buffer = getBufferSupplier().get();
        final int capacity = buffer.capacity();
        while (!isClosed()) {
            ClientEntity entity;
            try {
                while ((entity = requestQueue.take()) != null) {
                    ClientConnection conn = entity.conn;
                    ClientRequest request = entity.request;
                    ClientFuture respFuture = entity.respFuture;
                    AtomicBoolean pw = conn.pauseWriting;
                    Serializable reqid = request.getRequestid();
                    if (reqid == null) {
                        conn.responseQueue.offer(respFuture);
                    } else {
                        conn.responseMap.put(reqid, respFuture);
                    }
                    ByteArray rw = conn.writeArray;
                    rw.clear();
                    request.accept(conn, rw);
                    if (rw.length() <= capacity) {
                        buffer.clear();
                        buffer.put(rw.content(), 0, rw.length());
                        buffer.flip();
                        conn.channel.write(buffer, conn, writeHandler);
                    } else {
                        conn.channel.write(rw, conn, writeHandler);
                    }
                    if (pw.get()) {
                        synchronized (writeHandler) {
                            writeHandler.wait(30_000);
                        }
                    }
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

    }
}
