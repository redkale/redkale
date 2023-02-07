/*
 *
 */
package org.redkale.net.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.Objects;
import java.util.concurrent.*;
import org.redkale.net.AsyncIOThread;
import org.redkale.util.*;

/**
 * WebSocket连接的IO写线程
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
public class WebSocketWriteIOThread extends AsyncIOThread {

    private final ScheduledThreadPoolExecutor timeoutExecutor;

    private final BlockingDeque<WebSocketFuture> requestQueue = new LinkedBlockingDeque<>();

    public WebSocketWriteIOThread(ScheduledThreadPoolExecutor timeoutExecutor, ThreadGroup g, String name, int index, int threads,
        ExecutorService workExecutor, ByteBufferPool safeBufferPool) throws IOException {
        super(g, name, index, threads, workExecutor, safeBufferPool);
        Objects.requireNonNull(timeoutExecutor);
        this.timeoutExecutor = timeoutExecutor;
    }

    public CompletableFuture<Integer> send(WebSocket websocket, WebSocketPacket... packets) {
        Objects.requireNonNull(websocket);
        Objects.requireNonNull(packets);
        WebSocketFuture future = new WebSocketFuture(this, websocket, packets);
        int wts = websocket._channel.getWriteTimeoutSeconds();
        if (wts > 0) {
            future.timeout = timeoutExecutor.schedule(future, wts, TimeUnit.SECONDS);
        }
        requestQueue.offer(future);
        return future;
    }

    @Override
    public void run() {
        final ByteBuffer buffer = getBufferSupplier().get();
        final int capacity = buffer.capacity();
        final ByteArray writeArray = new ByteArray();
        while (!isClosed()) {
            WebSocketFuture entry;
            try {
                while ((entry = requestQueue.take()) != null) {
                    if (!entry.isDone()) {
                        writeArray.clear();
                        for (WebSocketPacket packet : entry.packets) {
                            packet.writeEncode(writeArray);
                        }
                        if (writeArray.length() > 0) {
                            if (writeArray.length() <= capacity) {
                                buffer.clear();
                                buffer.put(writeArray.content(), 0, writeArray.length());
                                buffer.flip();
                                entry.websocket._channel.write(buffer, entry, writeHandler);
                            } else {
                                entry.websocket._channel.write(writeArray, entry, writeHandler);
                            }
                        }
                    }
                }
            } catch (InterruptedException e) {
            }
        }
    }

    protected final CompletionHandler<Integer, WebSocketFuture> writeHandler = new CompletionHandler<Integer, WebSocketFuture>() {

        @Override
        public void completed(Integer result, WebSocketFuture attachment) {
            attachment.cancelTimeout();
            attachment.workThread = null;
            attachment.websocket = null;
            attachment.packets = null;
            runWork(() -> {
                attachment.complete(0);
            });
        }

        @Override
        public void failed(Throwable exc, WebSocketFuture attachment) {
            attachment.cancelTimeout();
            attachment.websocket.close();
            attachment.workThread = null;
            attachment.websocket = null;
            attachment.packets = null;
            runWork(() -> {
                attachment.completeExceptionally(exc);
            });
        }
    };

}
