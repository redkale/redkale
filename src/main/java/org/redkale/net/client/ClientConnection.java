/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.client;

import java.io.Serializable;
import java.nio.channels.ClosedChannelException;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import org.redkale.net.*;

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
public abstract class ClientConnection<R extends ClientRequest, P> implements Consumer<AsyncConnection> {

    protected final int index; //从0开始， connArray的下坐标

    protected final Client client;

    protected final LongAdder respWaitingCounter;

    protected final AtomicBoolean pauseWriting = new AtomicBoolean();

    protected final AtomicBoolean pauseResuming = new AtomicBoolean();

    protected final List<ClientFuture> pauseRequests = new CopyOnWriteArrayList<>();

    protected final AsyncConnection channel;

    private final ClientCodec<R, P> codec;

    private final ClientWriteIOThread writeThread;

    //responseQueue、responseMap二选一
    final ConcurrentLinkedQueue<ClientFuture> responseQueue = new ConcurrentLinkedQueue<>();

    //responseQueue、responseMap二选一, key: requestid
    final ConcurrentHashMap<Serializable, ClientFuture> responseMap = new ConcurrentHashMap<>();

    private int maxPipelines; //最大并行处理数

    private boolean authenticated;

    @SuppressWarnings({"LeakingThisInConstructor", "OverridableMethodCallInConstructor"})
    public ClientConnection(Client<? extends ClientConnection<R, P>, R, P> client, int index, AsyncConnection channel) {
        this.client = client;
        this.codec = createCodec();
        this.index = index;
        this.respWaitingCounter = client.connRespWaitings[index];
        this.channel = channel.beforeCloseListener(this);
        this.writeThread = (ClientWriteIOThread) channel.getWriteIOThread();
    }

    protected abstract ClientCodec createCodec();

    protected final CompletableFuture<P> writeChannel(R request) {
        return writeChannel(request, null);
    }

    //respTransfer只会在ClientCodec的读线程里调用
    protected final <T> CompletableFuture<T> writeChannel(R request, Function<P, T> respTransfer) {
        request.respTransfer = respTransfer;
        ClientFuture respFuture = createClientFuture(request);
        int rts = this.channel.getReadTimeoutSeconds();
        if (rts > 0 && !request.isCloseType()) {
            respFuture.setTimeout(client.timeoutScheduler.schedule(respFuture, rts, TimeUnit.SECONDS));
        }
        respWaitingCounter.increment(); //放在writeChannelUnsafe计数会延迟，导致不准确
        writeThread.offerRequest(this, request, respFuture);
        return respFuture;
    }

    CompletableFuture writeVirtualRequest(R request) {
        if (!request.isVirtualType()) {
            return CompletableFuture.failedFuture(new RuntimeException("ClientVirtualRequest must be virtualType = true"));
        }
        ClientFuture respFuture = createClientFuture(request);
        responseQueue.offer(respFuture);
        readChannel();
        return respFuture;
    }

    protected void preComplete(P resp, R req, Throwable exc) {
    }

    protected ClientFuture createClientFuture(R request) {
        return new ClientFuture(this, request);
    }

    protected ClientConnection readChannel() {
        channel.readInIOThread(codec);
        return this;
    }

    @Override //AsyncConnection.beforeCloseListener
    public void accept(AsyncConnection t) {
        respWaitingCounter.reset();
        client.connOpenStates[index].set(false);
        client.connArray[index] = null; //必须connOpenStates之后
    }

    public void dispose(Throwable exc) {
        channel.dispose();
        Throwable e = exc == null ? new ClosedChannelException() : exc;
        CompletableFuture f;
        respWaitingCounter.reset();
        WorkThread thread = channel.getReadIOThread();
        if (!responseQueue.isEmpty()) {
            while ((f = responseQueue.poll()) != null) {
                CompletableFuture future = f;
                thread.runWork(() -> future.completeExceptionally(e));
            }
        }
        if (!responseMap.isEmpty()) {
            responseMap.forEach((key, future) -> {
                responseMap.remove(key);
                thread.runWork(() -> future.completeExceptionally(e));
            });
        }
    }

    void sendHalfWrite(Throwable halfRequestExc) {
        writeThread.sendHalfWrite(this, halfRequestExc);
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public AsyncConnection getChannel() {
        return channel;
    }

    public <C extends ClientCodec<R, P>> C getCodec() {
        return (C) codec;
    }

    public int getMaxPipelines() {
        return maxPipelines;
    }

    protected ClientConnection setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
        return this;
    }

    protected ClientConnection setMaxPipelines(int maxPipelines) {
        this.maxPipelines = maxPipelines;
        return this;
    }

    protected ClientConnection resetMaxPipelines() {
        this.maxPipelines = client.maxPipelines;
        return this;
    }

    public int runningCount() {
        return respWaitingCounter.intValue();
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
        if (pos < 1) {
            return s;
        }
        int cha = pos + 10 - s.length();
        if (cha < 1) {
            return s;
        }
        for (int i = 0; i < cha; i++) s += ' ';
        return s;
    }
}
