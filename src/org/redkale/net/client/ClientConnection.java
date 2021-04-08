/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.client;

import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.stream.*;
import org.redkale.net.AsyncConnection;
import org.redkale.util.ByteArray;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.3.0
 *
 * @param <R> 请求对象
 * @param <P> 响应对象
 */
public class ClientConnection<R extends ClientRequest, P> implements Consumer<AsyncConnection> {

    protected final int index;

    protected final Client<R, P> client;

    protected final AtomicInteger respCounter;

    protected final AsyncConnection channel;

    protected final ByteArray writeArray = new ByteArray();

    protected final ByteArray readArray = new ByteArray();

    protected final AtomicBoolean readPending = new AtomicBoolean();

    protected final AtomicBoolean writePending = new AtomicBoolean();

    protected final ConcurrentLinkedDeque<R> requestQueue = new ConcurrentLinkedDeque();

    protected final ConcurrentLinkedDeque<ClientFuture> responseQueue = new ConcurrentLinkedDeque();

    protected final CompletionHandler<Integer, Void> writeHandler = new CompletionHandler<Integer, Void>() {

        @Override
        public void completed(Integer result, Void attachment) {
            if (writeLastRequest != null && writeLastRequest == client.closeRequest) {
                if (closeFuture != null) closeFuture.complete(null);
                closeFuture = null;
                return;
            }

            if (continueWrite()) return;
            if (!writePending.compareAndSet(true, false)) {
                if (continueWrite()) return;
            }

            readChannel();
        }

        @Override
        public void failed(Throwable exc, Void attachment) {
            dispose(exc);
        }
    };

    private boolean continueWrite() {
        writeArray.clear();
        int pipelines = client.maxPipelines > 1 ? (client.maxPipelines - responseQueue.size()) : 1;
        currPipelineIndex = 0;
        for (int i = 0; i < pipelines; i++) {
            R r = requestQueue.poll();
            if (r == null) break;
            writeLastRequest = r;
            r.accept(ClientConnection.this, writeArray);
            currPipelineIndex++;
        }
        if (writeArray.length() > 0) {
            channel.write(writeArray, writeHandler);
            return true;
        }
        return false;
    }

    protected final CompletionHandler<Integer, ByteBuffer> readHandler = new CompletionHandler<Integer, ByteBuffer>() {

        ClientCodec<R, P> codec;

        @Override
        public void completed(Integer count, ByteBuffer attachment) {
            if (count < 1) {
                channel.setReadBuffer(attachment);
                dispose(new NonReadableChannelException());
                return;
            }
            try {
                if (codec == null) codec = client.codecCreator.create();
                attachment.flip();
                codecResponse(attachment);
            } catch (Exception e) {
                channel.setReadBuffer(attachment);
                dispose(e);
            }
        }

        public void codecResponse(ByteBuffer buffer) {
            Stream<R> reqstream = responseQueue.stream().map(r -> (R) r.request);
            List<R> requests = reqstream.collect(Collectors.toList());
            if (codec.codecResult(ClientConnection.this, requests, buffer, readArray)) { //成功了
                readArray.clear();
                List<ClientResult<P>> results = codec.removeResults();
                if (results != null) {
                    for (ClientResult<P> rs : results) {
                        ClientFuture respFuture = responseQueue.poll();
                        if (respFuture != null) {
                            respCounter.decrementAndGet();
                            if (isAuthenticated()) client.pollRespCounter.incrementAndGet();
                            try {
                                if (respFuture.timeout != null) respFuture.timeout.cancel(true);
                                if (rs.exc != null) {
                                    respFuture.completeExceptionally(rs.exc);
                                } else {
                                    respFuture.complete(rs.result);
                                }
                            } catch (Throwable t) {
                                t.printStackTrace();
                            }
                        }
                    }
                }
                {
                    CompletableFuture<ClientConnection> connFuture = client.connQueue.poll();
                    if (connFuture != null) connFuture.complete(ClientConnection.this);
                }
                if (buffer.hasRemaining()) {
                    codecResponse(buffer);
                } else if (responseQueue.isEmpty()) { //队列都已处理完了
                    buffer.clear();
                    channel.setReadBuffer(buffer);
                    if (readPending.compareAndSet(true, false)) {
                        CompletableFuture<ClientConnection> connFuture = client.connQueue.poll();
                        if (connFuture != null) connFuture.complete(ClientConnection.this);
                    } else {
                        channel.read(this);
                    }
                } else {
                    buffer.clear();
                    channel.setReadBuffer(buffer);
                    channel.read(this);
                }
            } else { //数据不全， 继续读
                buffer.clear();
                channel.setReadBuffer(buffer);
                channel.read(this);
            }
        }

        @Override
        public void failed(Throwable t, ByteBuffer attachment) {
            dispose(t);
        }
    };

    protected boolean authenticated;

    protected int currPipelineIndex;

    protected ClientFuture closeFuture;

    private R writeLastRequest;

    public ClientConnection(Client client, int index, AsyncConnection channel) {
        this.client = client;
        this.index = index;
        this.respCounter = client.connResps[index];
        this.channel = channel.beforeCloseListener(this);
    }

    protected CompletableFuture<P> writeChannel(R request) {
        ClientFuture respFuture = createClientFuture(request);
        if (request == client.closeRequest) {
            respFuture.request = null;
            closeFuture = respFuture;
        } else {
            int rts = this.channel.getReadTimeoutSeconds();
            if (rts > 0 && respFuture.request != null) {
                respFuture.responseQueue = responseQueue;
                respFuture.timeout = client.timeoutScheduler.schedule(respFuture, rts, TimeUnit.SECONDS);
            }
        }
        synchronized (requestQueue) { //保证顺序一致
            responseQueue.offer(respFuture.request == null ? ClientFuture.EMPTY : respFuture);
            requestQueue.offer(request);
            respCounter.incrementAndGet();
            if (isAuthenticated()) client.writeReqCounter.incrementAndGet();
        }
        if (writePending.compareAndSet(false, true)) {
            writeArray.clear();
            int pipelines = client.maxPipelines > 1 ? client.maxPipelines : 1; //pipelines必须大于0
            currPipelineIndex = 0;
            for (int i = 0; i < pipelines; i++) {
                R r = requestQueue.poll();
                if (r == null) break;
                r.accept(ClientConnection.this, writeArray);
                currPipelineIndex++;
            }
            channel.write(writeArray, writeHandler);
        }
        return respFuture;
    }

    protected ClientFuture createClientFuture(R request) {
        return new ClientFuture(request);
    }

    protected void readChannel() {
        if (readPending.compareAndSet(false, true)) {
            readArray.clear();
            channel.read(readHandler);
        }
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public AsyncConnection getChannel() {
        return channel;
    }

    public int currPipelineIndex() {
        return currPipelineIndex;
    }

    @Override //AsyncConnection.beforeCloseListener
    public void accept(AsyncConnection t) {
        respCounter.set(0);
        client.connFlags[index].set(false);
        client.connArray[index] = null; //必须connflags之后
    }

    public void dispose(Throwable exc) {
        channel.dispose();
        Throwable e = exc;
        CompletableFuture f;
        respCounter.set(0);
        while ((f = responseQueue.poll()) != null) {
            if (e == null) e = new ClosedChannelException();
            f.completeExceptionally(e);
        }
    }

    public int runningCount() {
        return respCounter.get();
    }

    public long getLastWriteTime() {
        return channel.getLastWriteTime();
    }

    public boolean isOpen() {
        return channel.isOpen();
    }

}
