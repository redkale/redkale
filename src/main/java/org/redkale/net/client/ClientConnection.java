/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.client;

import java.io.Serializable;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.*;
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

    protected final LongAdder respWaitingCounter; //可能为null

    protected final LongAdder doneRequestCounter = new LongAdder();

    protected final LongAdder doneResponseCounter = new LongAdder();

    final AtomicBoolean pauseWriting = new AtomicBoolean();

    final ConcurrentLinkedQueue<ClientFuture> pauseRequests = new ConcurrentLinkedQueue<>();

    private final Client.AddressConnEntry connEntry;

    protected final AsyncConnection channel;

    private final ClientCodec<R, P> codec;

    private final ClientWriteIOThread writeThread;

    //respFutureQueue、respFutureMap二选一， SPSC队列模式
    private final Queue<ClientFuture<R, P>> respFutureQueue = new ConcurrentLinkedQueue<>(); //Utility.unsafe() != null ? new MpscGrowableArrayQueue<>(16, 1 << 16) : new ConcurrentLinkedQueue<>();

    //respFutureQueue、respFutureMap二选一, key: requestid， SPSC模式
    private final Map<Serializable, ClientFuture<R, P>> respFutureMap = new ConcurrentHashMap<>();

    Iterator<ClientFuture<R, P>> currRespIterator; //必须在调用decodeMessages之前重置为null

    private int maxPipelines; //最大并行处理数

    private boolean authenticated;

    @SuppressWarnings({"LeakingThisInConstructor", "OverridableMethodCallInConstructor"})
    public ClientConnection(Client<? extends ClientConnection<R, P>, R, P> client, int index, AsyncConnection channel) {
        this.client = client;
        this.codec = createCodec();
        this.index = index;
        this.connEntry = index >= 0 ? null : client.connAddrEntrys.get(channel.getRemoteAddress());
        this.respWaitingCounter = index >= 0 ? client.connRespWaitings[index] : this.connEntry.connRespWaiting;
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

    CompletableFuture<P> writeVirtualRequest(R request) {
        if (!request.isVirtualType()) {
            return CompletableFuture.failedFuture(new RuntimeException("ClientVirtualRequest must be virtualType = true"));
        }
        ClientFuture<R, P> respFuture = createClientFuture(request);
        respFutureQueue.offer(respFuture);
        readChannel();
        return respFuture;
    }

    protected void preComplete(P resp, R req, Throwable exc) {
    }

    protected ClientFuture<R, P> createClientFuture(R request) {
        return new ClientFuture(this, request);
    }

    protected ClientConnection readChannel() {
        channel.readInIOThread(codec);
        return this;
    }

    @Override //AsyncConnection.beforeCloseListener
    public void accept(AsyncConnection t) {
        respWaitingCounter.reset();
        if (index >= 0) {
            client.connOpenStates[index].set(false);
            client.connArray[index] = null; //必须connOpenStates之后
        } else if (connEntry != null) {
            connEntry.connOpenState.set(false);
        }
    }

    public void dispose(Throwable exc) {
        channel.dispose();
        Throwable e = exc == null ? new ClosedChannelException() : exc;
        CompletableFuture f;
        respWaitingCounter.reset();
        WorkThread thread = channel.getReadIOThread();
        if (!respFutureQueue.isEmpty()) {
            while ((f = respFutureQueue.poll()) != null) {
                CompletableFuture future = f;
                thread.runWork(() -> future.completeExceptionally(e));
            }
        }
        if (!respFutureMap.isEmpty()) {
            respFutureMap.forEach((key, future) -> {
                respFutureMap.remove(key);
                thread.runWork(() -> future.completeExceptionally(e));
            });
        }
    }

    void sendHalfWrite(R request, Throwable halfRequestExc) {
        writeThread.sendHalfWrite(this, request, halfRequestExc);
    }

    //只会在WriteIOThread中调用
    void offerRespFuture(ClientFuture<R, P> respFuture) {
        Serializable requestid = respFuture.request.getRequestid();
        if (requestid == null) {
            respFutureQueue.offer(respFuture);
        } else {
            respFutureMap.put(requestid, respFuture);
        }
    }

    //只会被Timeout在ReadIOThread中调用
    void removeRespFuture(Serializable requestid, ClientFuture<R, P> respFuture) {
        if (requestid == null) {
            respFutureQueue.remove(respFuture);
        } else {
            respFutureMap.remove(requestid);
        }
    }

    //只会被ClientCodec在ReadIOThread中调用
    ClientFuture<R, P> pollRespFuture(Serializable requestid) {
        if (requestid == null) {
            return respFutureQueue.poll();
        } else {
            return respFutureMap.remove(requestid);
        }
    }

    //只会被ClientCodec在ReadIOThread中调用
    R findRequest(Serializable requestid) {
        if (requestid == null) {
            if (currRespIterator == null) {
                currRespIterator = respFutureQueue.iterator();
            }
            ClientFuture<R, P> future = currRespIterator.hasNext() ? currRespIterator.next() : null;
            return future == null ? null : future.request;
        } else {
            ClientFuture<R, P> future = respFutureMap.get(requestid);
            return future == null ? null : future.request;
        }
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public AsyncConnection getChannel() {
        return channel;
    }

    public SocketAddress getRemoteAddress() {
        return channel.getRemoteAddress();
    }

    public long getDoneRequestCounter() {
        return doneRequestCounter.longValue();
    }

    public long getDoneResponseCounter() {
        return doneResponseCounter.longValue();
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
