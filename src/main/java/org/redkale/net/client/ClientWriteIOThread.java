/*
 *
 */
package org.redkale.net.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.*;
import java.util.concurrent.*;
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

    private final BlockingQueue<ClientFuture> requestQueue = new LinkedBlockingQueue<>();

    public ClientWriteIOThread(ThreadGroup g, String name, int index, int threads,
        ExecutorService workExecutor, ObjectPool<ByteBuffer> safeBufferPool) throws IOException {
        super(g, name, index, threads, workExecutor, safeBufferPool);
    }

    public void offerRequest(ClientConnection conn, ClientRequest request, ClientFuture respFuture) {
        requestQueue.offer(respFuture);
    }

    public void sendHalfWrite(ClientConnection conn, ClientRequest request, Throwable halfRequestExc) {
        ClientFuture respFuture = conn.createClientFuture(request);
        respFuture.resumeHalfRequestFlag = true;
        if (halfRequestExc != null) {  //halfRequestExc不为null时需要把当前halfRequest移除
            conn.pauseRequests.poll();
        }
        requestQueue.offer(respFuture);
    }

    @Override
    public void run() {
        final ByteBuffer buffer = getBufferSupplier().get();
        final int capacity = buffer.capacity();
        final ByteArray writeArray = new ByteArray();
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
                    if (entry.resumeHalfRequestFlag != null) { //将暂停的pauseRequests写入list
                        List<ClientFuture> cl = map.computeIfAbsent(entry.conn, c -> listPool.get());
                        for (ClientFuture f : (List<ClientFuture>) entry.conn.pauseRequests) {
                            if (!f.isDone()) {
                                entry.conn.offerRespFuture(f);
                                cl.add(f);
                            }
                        }
                        entry.conn.pauseRequests.clear();
                        entry.conn.pauseWriting.set(false);
                    } else if (!entry.isDone()) {
                        entry.conn.offerRespFuture(entry);
                        if (entry.conn.pauseWriting.get()) {
                            entry.conn.pauseRequests.add(entry);
                        } else {
                            map.computeIfAbsent(entry.conn, c -> listPool.get()).add(entry);
                        }
                    }
                    while ((entry = requestQueue.poll()) != null) {
                        if (entry.resumeHalfRequestFlag != null) { //将暂停的pauseRequests写入list
                            List<ClientFuture> cl = map.computeIfAbsent(entry.conn, c -> listPool.get());
                            for (ClientFuture f : (List<ClientFuture>) entry.conn.pauseRequests) {
                                if (!f.isDone()) {
                                    entry.conn.offerRespFuture(f);
                                    cl.add(f);
                                }
                            }
                            entry.conn.pauseRequests.clear();
                            entry.conn.pauseWriting.set(false);
                        } else if (!entry.isDone()) {
                            entry.conn.offerRespFuture(entry);
                            if (entry.conn.pauseWriting.get()) {
                                entry.conn.pauseRequests.add(entry);
                            } else {
                                map.computeIfAbsent(entry.conn, c -> listPool.get()).add(entry);
                            }
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
