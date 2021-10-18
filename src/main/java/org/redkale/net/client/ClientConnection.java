/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.client;

import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.logging.Level;
import org.redkale.net.*;
import org.redkale.util.*;

/**
 * 注意: 要确保AsyncConnection的读写过程都必须在channel.ioThread中运行
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

    protected final int index; //从0开始， connArray的下坐标

    protected final Client<R, P> client;

    protected final LongAdder respCounter;

    protected final AsyncConnection channel;

    protected final ByteArray writeArray = new ByteArray();

    protected final ByteArray readArray = new ByteArray();

    protected final AtomicBoolean pauseWriting = new AtomicBoolean();

    protected final AtomicBoolean readPending = new AtomicBoolean();

    protected final AtomicBoolean writePending = new AtomicBoolean();

    protected final Queue<R> requestQueue = new ArrayDeque<>();

    protected final Queue<ClientFuture> responseQueue = new ArrayDeque<>();

    protected final CompletionHandler<Integer, Void> writeHandler = new CompletionHandler<Integer, Void>() {

        @Override
        public void completed(Integer result, Void attachment) {
            if (writeLastRequest != null && writeLastRequest == client.closeRequest) {
                if (closeFuture != null) closeFuture.complete(null);
                closeFuture = null;
                return;
            }
            if (continueWrite(false)) return;
            writePending.compareAndSet(true, false);
            readChannel();
        }

        @Override
        public void failed(Throwable exc, Void attachment) {
            dispose(exc);
        }
    };

    protected int maxPipelines; //最大并行处理数

    protected ClientConnection setMaxPipelines(int maxPipelines) {
        this.maxPipelines = maxPipelines;
        return this;
    }

    protected ClientConnection resetMaxPipelines() {
        this.maxPipelines = client.maxPipelines;
        return this;
    }

    protected void pauseWriting(boolean flag) {
        this.pauseWriting.set(flag);
    }

    private boolean continueWrite(boolean must) {
        writeArray.clear();
        int pipelines = maxPipelines > 1 ? (maxPipelines - responseQueue.size()) : 1;
        if (must && pipelines < 1) pipelines = 1;
        int c = 0;
        AtomicBoolean pw = this.pauseWriting;
        for (int i = 0; i < pipelines; i++) {
            if (pw.get()) break;
            R r = requestQueue.poll();
            if (r == null) break;
            writeLastRequest = r;
            r.accept(this, writeArray);
            c++;
        }
        if (c > 0) { //当Client连接Server时先从Server读取数据时,会先发送一个EMPTY的request，这样writeArray.count就会为0
            channel.write(writeArray, writeHandler);
            return true;
        }
        return false;
    }

    protected void preComplete(P resp, R req, Throwable exc) {
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
            } catch (Throwable e) {
                channel.setReadBuffer(attachment);
                dispose(e);
            }
        }

        public void codecResponse(ByteBuffer buffer) {
            if (codec.codecResult(ClientConnection.this, buffer, readArray)) { //成功了
                readArray.clear();
                List<ClientResult<P>> results = codec.removeResults();
                if (results != null) {
                    for (ClientResult<P> rs : results) {
                        ClientFuture respFuture = responseQueue.poll();
                        if (respFuture != null) {
                            respCounter.decrement();
                            if (isAuthenticated() && client.pollRespCounter != null) client.pollRespCounter.increment();
                            try {
                                if (respFuture.timeout != null) respFuture.timeout.cancel(true);
                                ClientRequest request = respFuture.request;
                                //if (client.finest) client.logger.log(Level.FINEST, Utility.nowMillis() + ": " + Thread.currentThread().getName() + ": " + ClientConnection.this + ", 回调处理, req=" + request + ", result=" + rs.result);
                                preComplete(rs.result, (R) request, rs.exc);
                                WorkThread workThread = null;
                                if (request != null) {
                                    workThread = request.workThread;
                                    request.workThread = null;
                                }
                                if (rs.exc != null) {
                                    if (workThread == null || workThread == Thread.currentThread()
                                        || workThread.getState() == Thread.State.BLOCKED
                                        || workThread.getState() == Thread.State.WAITING) {
                                        respFuture.completeExceptionally(rs.exc);
                                    } else {
                                        workThread.execute(() -> respFuture.completeExceptionally(rs.exc));
                                    }
                                } else {
                                    if (workThread == null || workThread == Thread.currentThread()
                                        || workThread.getState() == Thread.State.BLOCKED
                                        || workThread.getState() == Thread.State.WAITING) {
                                        respFuture.complete(rs.result);
                                    } else {
                                        workThread.execute(() -> respFuture.complete(rs.result));
                                    }
                                }
                            } catch (Throwable t) {
                                client.logger.log(Level.INFO, "complete result error, request: " + respFuture.request, t);
                            }
                        }
                    }
                }

                if (buffer.hasRemaining()) {
                    codecResponse(buffer);
                } else if (responseQueue.isEmpty()) { //队列都已处理完了
                    buffer.clear();
                    channel.setReadBuffer(buffer);
                    if (readPending.compareAndSet(true, false)) {
                        //无消息处理
                    } else {
                        channel.read(this);
                    }
                } else { //还有消息需要读取
                    if (!requestQueue.isEmpty() && writePending.compareAndSet(false, true)) {
                        //先写后读取
                        if (!continueWrite(true)) {
                            writePending.compareAndSet(true, false);
                        }
                    }
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

    protected ClientFuture closeFuture;

    private R writeLastRequest;

    @SuppressWarnings("LeakingThisInConstructor")
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
        if (channel.inCurrThread()) {
            writeChannelInThread(request, respFuture);
        } else {
            channel.execute(() -> writeChannelInThread(request, respFuture));
        }
        return respFuture;
    }

    private void writeChannelInThread(R request, ClientFuture respFuture) {
        { //保证顺序一致
            responseQueue.offer(client.closeRequest != null && respFuture.request == client.closeRequest ? ClientFuture.EMPTY : respFuture);
            requestQueue.offer(request);
            respCounter.increment();
            if (isAuthenticated() && client.writeReqCounter != null) client.writeReqCounter.increment();
        }
        if (writePending.compareAndSet(false, true)) {
            continueWrite(true);
        }
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

    @Override //AsyncConnection.beforeCloseListener
    public void accept(AsyncConnection t) {
        respCounter.reset();
        client.connOpens[index].set(false);
        client.connArray[index] = null; //必须connflags之后
    }

    public void dispose(Throwable exc) {
        channel.dispose();
        Throwable e = exc;
        CompletableFuture f;
        respCounter.reset();
        while ((f = responseQueue.poll()) != null) {
            if (e == null) e = new ClosedChannelException();
            f.completeExceptionally(e);
        }
    }

    public int runningCount() {
        return respCounter.intValue();
    }

    public long getLastWriteTime() {
        return channel.getLastWriteTime();
    }

    public long getLastReadTime() {
        return channel.getLastReadTime();
    }

    public boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    public String toString() {
        String s = super.toString();
        int pos = s.lastIndexOf('@');
        if (pos < 1) return s;
        int cha = pos + 10 - s.length();
        if (cha < 1) return s;
        for (int i = 0; i < cha; i++) s += ' ';
        return s;
    }
}
