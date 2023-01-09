/*
 *
 */
package org.redkale.net.client;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.util.concurrent.*;
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
                        conn.channel.write(buffer, null, conn.writeHandler);
                    } else {
                        conn.channel.write(rw, conn.writeHandler);
                    }
                }
            } catch (InterruptedException e) {
            }
        }
    }

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
