/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.client;

import java.io.Serializable;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.*;
import java.util.logging.Level;
import org.redkale.annotation.*;
import org.redkale.net.*;
import org.redkale.util.*;

/**
 * 注意: 要确保AsyncConnection的读写过程都必须在channel.ioThread中运行
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.3.0
 * @param <R> 请求对象
 * @param <P> 响应对象
 */
public abstract class ClientConnection<R extends ClientRequest, P extends ClientResult>
        implements Consumer<AsyncConnection> {

    protected final Client client;

    @Nonnull
    protected LongAdder respWaitingCounter;

    protected final LongAdder doneRequestCounter = new LongAdder();

    protected final LongAdder doneResponseCounter = new LongAdder();

    protected final AtomicBoolean writePending = new AtomicBoolean();

    protected final ReentrantLock writeLock = new ReentrantLock();

    protected final ByteArray writeArray = new ByteArray();

    protected final ThreadLocal<ByteArray> arrayThreadLocal = Utility.withInitialThreadLocal(ByteArray::new);

    protected final ByteBuffer writeBuffer;

    protected final CompletionHandler<Integer, ClientConnection> writeHandler =
            new CompletionHandler<Integer, ClientConnection>() {

                @Override
                public void completed(Integer result, ClientConnection attachment) {
                    // do nothing
                }

                @Override
                public void failed(Throwable exc, ClientConnection attachment) {
                    writePending.set(false);
                    attachment.dispose(exc);
                }
            };

    final AtomicBoolean pauseWriting = new AtomicBoolean();

    final ConcurrentLinkedQueue<ClientFuture> pauseRequests = new ConcurrentLinkedQueue<>();

    // pauseWriting=true，此字段才会有值; pauseWriting=false，此字段值为null
    ClientFuture currHalfWriteFuture;

    @Nonnull
    private Client.AddressConnEntry connEntry;

    protected final AsyncConnection channel;

    private final ClientCodec<R, P> codec;

    // respFutureQueue、respFutureMap二选一， SPSC队列模式
    private final ConcurrentLinkedDeque<ClientFuture<R, P>> respFutureQueue = new ConcurrentLinkedDeque<>();

    // respFutureQueue、respFutureMap二选一, key: requestid， SPSC模式
    private final ConcurrentHashMap<Serializable, ClientFuture<R, P>> respFutureMap = new ConcurrentHashMap<>();

    private final AtomicBoolean closed = new AtomicBoolean();

    Iterator<ClientFuture<R, P>> currRespIterator; // 必须在调用decodeMessages之前重置为null

    private int maxPipelines; // 最大并行处理数

    private boolean authenticated;

    @SuppressWarnings({"LeakingThisInConstructor", "OverridableMethodCallInConstructor"})
    public ClientConnection(Client<? extends ClientConnection<R, P>, R, P> client, AsyncConnection channel) {
        this.client = client;
        this.codec = createCodec();
        this.channel = channel.beforeCloseListener(this); // .fastHandler(writeHandler);
        this.writeBuffer = channel.pollWriteBuffer();
    }

    ClientConnection setConnEntry(Client.AddressConnEntry entry) {
        this.connEntry = entry;
        this.respWaitingCounter = entry.connRespWaiting;
        return this;
    }

    protected abstract ClientCodec createCodec();

    protected final CompletableFuture<P> writeChannel(R request) {
        return writeChannel(request, null);
    }

    protected final CompletableFuture<List<P>> writeChannel(R[] requests) {
        return writeChannel(requests, null);
    }

    // respTransfer只会在ClientCodec的读线程里调用
    protected final <T> CompletableFuture<T> writeChannel(R request, Function<P, T> respTransfer) {
        request.respTransfer = respTransfer;
        ClientFuture respFuture = createClientFuture(request);
        if (client.debug) {
            client.logger.log(
                    Level.FINEST,
                    Times.nowMillis() + ": " + Thread.currentThread().getName() + ": " + this + ", sendRequest: "
                            + request + ", respFuture: " + respFuture);
        }
        respWaitingCounter.increment(); // 放在writeChannelInWriteThread计数会延迟，导致不准确
        writeLock.lock();
        try {
            offerRespFuture(respFuture);
            if (pauseWriting.get()) {
                pauseRequests.add(respFuture);
            } else {
                sendRequestInLocking(request, respFuture);
            }
        } finally {
            writeLock.unlock();
        }
        if (client.debug) {
            return respFuture.whenComplete((v, t) -> {
                client.logger.log(
                        Level.FINEST,
                        Times.nowMillis() + ": " + Thread.currentThread().getName() + ": " + this + ", respResult: "
                                + (t != null ? t : v));
            });
        }
        return respFuture;
    }

    protected void sendRequestInLocking(R request, ClientFuture respFuture) {
        // 发送请求数据包
        writeArray.clear();
        request.writeTo(this, writeArray);
        if (request.isCompleted()) {
            doneRequestCounter.increment();
        } else { // 还剩半包没发送完
            pauseWriting.set(true);
            currHalfWriteFuture = respFuture;
        }
        if (writeArray.length() > 0) {
            if (writeBuffer.capacity() >= writeArray.length()) {
                writeBuffer.clear();
                writeBuffer.put(writeArray.content(), 0, writeArray.length());
                writeBuffer.flip();
                channel.write(writeBuffer, this, writeHandler);
            } else {
                channel.write(writeArray, this, writeHandler);
            }
        }
    }

    // respTransfer只会在ClientCodec的读线程里调用
    protected final <T> CompletableFuture<List<T>> writeChannel(R[] requests, Function<P, T> respTransfer) {
        if (client.debug) {
            client.logger.log(
                    Level.FINEST,
                    Times.nowMillis() + ": " + Thread.currentThread().getName() + ": " + this + ", 发送请求: "
                            + Arrays.toString(requests));
        }
        ClientFuture[] respFutures = new ClientFuture[requests.length];
        int rts = this.client.getReadTimeoutSeconds();
        for (int i = 0; i < respFutures.length; i++) {
            R request = requests[i];
            request.respTransfer = respTransfer;
            respFutures[i] = createClientFuture(requests[i]);
        }
        respWaitingCounter.add(respFutures.length); // 放在writeChannelInWriteThread计数会延迟，导致不准确

        writeLock.lock();
        try {
            if (pauseWriting.get()) {
                for (ClientFuture respFuture : respFutures) {
                    offerRespFuture(respFuture);
                    pauseRequests.add(respFuture);
                }
            } else {
                for (ClientFuture respFuture : respFutures) {
                    offerRespFuture(respFuture);
                }
                sendRequestInLocking(respFutures);
            }
        } finally {
            writeLock.unlock();
        }
        return Utility.allOfFutures(respFutures);
    }

    protected void sendRequestInLocking(ClientFuture[] respFutures) {
        // 发送请求数据包
        writeArray.clear();
        for (ClientFuture respFuture : respFutures) {
            if (pauseWriting.get()) {
                pauseRequests.add(respFuture);
            } else {
                ClientRequest request = respFuture.request;
                request.writeTo(this, writeArray);
                if (request.isCompleted()) {
                    doneRequestCounter.increment();
                } else { // 还剩半包没发送完
                    pauseWriting.set(true);
                    currHalfWriteFuture = respFuture;
                }
            }
        }
        if (writeArray.length() > 0) {
            if (writeBuffer.capacity() >= writeArray.length()) {
                writeBuffer.clear();
                writeBuffer.put(writeArray.content(), 0, writeArray.length());
                writeBuffer.flip();
                channel.write(writeBuffer, this, writeHandler);
            } else {
                channel.write(writeArray, this, writeHandler);
            }
        }
    }

    //    private void sendFastRequestInLocking(R request, ClientFuture respFuture) {
    //        ByteArray array = arrayThreadLocal.get();
    //        array.clear();
    //        request.writeTo(this, array);
    //        if (request.isCompleted()) {
    //            doneRequestCounter.increment();
    //        } else { //还剩半包没发送完
    //            pauseWriting.set(true);
    //            currHalfWriteFuture = respFuture;
    //        }
    //        channel.fastWrite(array.getBytes());
    //    }
    //
    //    private void sendFastRequestInLocking(ClientFuture[] respFutures) {
    //        ByteArray array = arrayThreadLocal.get();
    //        array.clear();
    //        for (ClientFuture respFuture : respFutures) {
    //            if (pauseWriting.get()) {
    //                pauseRequests.add(respFuture);
    //            } else {
    //                ClientRequest request = respFuture.request;
    //                request.writeTo(this, array);
    //                if (request.isCompleted()) {
    //                    doneRequestCounter.increment();
    //                } else { //还剩半包没发送完
    //                    pauseWriting.set(true);
    //                    currHalfWriteFuture = respFuture;
    //                }
    //            }
    //        }
    //        channel.fastWrite(array.getBytes());
    //    }
    // 发送半包和积压的请求数据包
    void sendHalfWriteInReadThread(R request, Throwable halfRequestExc) {
        writeLock.lock();
        try {
            pauseWriting.set(false);
            ClientFuture respFuture = this.currHalfWriteFuture;
            if (respFuture != null) {
                this.currHalfWriteFuture = null;
                if (halfRequestExc == null) {
                    offerFirstRespFuture(respFuture);
                    sendRequestInLocking(request, respFuture);
                } else {
                    codec.responseComplete(true, respFuture, null, halfRequestExc);
                }
            }
            while (!pauseWriting.get() && (respFuture = pauseRequests.poll()) != null) {
                sendRequestInLocking((R) respFuture.getRequest(), respFuture);
            }
        } finally {
            writeLock.unlock();
        }
    }

    CompletableFuture<P> writeVirtualRequest(R request) {
        if (!request.isVirtualType()) {
            return CompletableFuture.failedFuture(
                    new RuntimeException("ClientVirtualRequest must be virtualType = true"));
        }
        ClientFuture<R, P> respFuture = createClientFuture(request);
        writeLock.lock();
        try {
            offerRespFuture(respFuture);
        } finally {
            writeLock.unlock();
        }
        channel.readRegister(getCodec()); // 不能在创建连接时注册读事件
        return respFuture;
    }

    protected void preComplete(P resp, R req, Throwable exc) {}

    protected ClientFuture<R, P> createClientFuture(R request) {
        ClientFuture respFuture = new ClientFuture(this, request);
        int rts = this.client.getReadTimeoutSeconds();
        if (rts > 0 && !request.isCloseType()) {
            respFuture.setTimeout(client.timeoutScheduler.schedule(respFuture, rts, TimeUnit.SECONDS));
        }
        return respFuture;
    }

    @Override // AsyncConnection.beforeCloseListener
    public void accept(AsyncConnection t) {
        respWaitingCounter.reset();
        if (connEntry != null) { // index=-1
            connEntry.connection = null;
            connEntry.connOpenState.set(false);
        }
        ClientMessageListener listener = getCodec().getMessageListener();
        if (listener != null) {
            listener.onClose(this);
        }
    }

    public void dispose(Throwable exc) {
        if (closed.compareAndSet(false, true)) {
            channel.offerWriteBuffer(writeBuffer);
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
    }

    // 只会在WriteIOThread中调用, 必须在writeLock内执行
    void offerFirstRespFuture(ClientFuture<R, P> respFuture) {
        Serializable requestid = respFuture.request.getRequestid();
        if (requestid == null) {
            respFutureQueue.offerFirst(respFuture);
        } else {
            respFutureMap.put(requestid, respFuture);
        }
    }

    // 必须在writeLock内执行
    void offerRespFuture(ClientFuture<R, P> respFuture) {
        Serializable requestid = respFuture.request.getRequestid();
        if (requestid == null) {
            respFutureQueue.offer(respFuture);
        } else {
            respFutureMap.put(requestid, respFuture);
        }
    }

    // 只会被Timeout在ReadIOThread中调用
    void removeRespFuture(Serializable requestid, ClientFuture<R, P> respFuture) {
        if (requestid == null) {
            respFutureQueue.remove(respFuture);
        } else {
            respFutureMap.remove(requestid);
        }
    }

    // 只会被ClientCodec在ReadIOThread中调用
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

    // 只会被ClientCodec在ReadIOThread中调用
    protected ClientFuture<R, P> pollRespFuture(Serializable requestid) {
        if (requestid == null) {
            return respFutureQueue.poll();
        } else {
            return respFutureMap.remove(requestid);
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
