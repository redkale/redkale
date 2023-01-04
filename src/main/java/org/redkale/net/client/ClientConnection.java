/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.client;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.AbstractMap.SimpleEntry;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
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
public abstract class ClientConnection<R extends ClientRequest, P> implements Consumer<AsyncConnection> {

    protected final int index; //从0开始， connArray的下坐标

    protected final Client<R, P> client;

    protected final ClientCodec<R, P> codec;

    protected final LongAdder respWaitingCounter;

    protected final AsyncConnection channel;

    protected final ByteArray writeArray = new ByteArray();

    protected final ByteArray readArray = new ByteArray();

    protected final AtomicBoolean pauseWriting = new AtomicBoolean();

    protected final AtomicBoolean readPending = new AtomicBoolean();

    protected final AtomicBoolean writePending = new AtomicBoolean();

    protected final Queue<SimpleEntry<R, ClientFuture<R>>> requestQueue = new ArrayDeque<>();

    final ArrayDeque<ClientFuture> responseQueue = new ArrayDeque<>();

    //key: requestid
    final HashMap<Serializable, ClientFuture> responseMap = new LinkedHashMap<>();

    protected final CompletionHandler<Integer, Void> writeHandler = new CompletionHandler<Integer, Void>() {

        @Override
        public void completed(Integer result, Void attachment) {
            if (writeLastRequest != null && writeLastRequest.isCloseType()) {
                if (closeFuture != null) {
                    channel.getWriteIOThread().runWork(() -> {
                        closeFuture.complete(null);
                    });
                }
                closeFuture = null;
                return;
            }
            if (sendWrite(false)) {
                return;
            }
            writePending.compareAndSet(true, false);
            readChannel();
        }

        @Override
        public void failed(Throwable exc, Void attachment) {
            dispose(exc);
        }
    };

    protected int maxPipelines; //最大并行处理数

    protected SimpleEntry<R, ClientFuture<R>> lastHalfEntry;

    protected ClientConnection setMaxPipelines(int maxPipelines) {
        this.maxPipelines = maxPipelines;
        return this;
    }

    protected ClientConnection resetMaxPipelines() {
        this.maxPipelines = client.maxPipelines;
        return this;
    }

    protected boolean isWaitingResponseEmpty() {
        return responseQueue.isEmpty() && responseMap.isEmpty();
    }

    protected void resumeWrite() {
        this.pauseWriting.set(false);
    }

    //有写入数据返回true，否则返回false
    private boolean sendWrite(boolean must) {
        ClientConnection conn = this;
        ByteArray rw = conn.writeArray;
        rw.clear();
        int pipelines = maxPipelines > 1 ? (maxPipelines - responseQueue.size() - responseMap.size()) : 1;
        if (must && pipelines < 1) {
            pipelines = 1;
        }
        int c = 0;
        AtomicBoolean pw = conn.pauseWriting;
        for (int i = 0; i < pipelines; i++) {
            if (pw.get()) {
                break;
            }
            SimpleEntry<R, ClientFuture<R>> entry;
            if (lastHalfEntry == null) {
                entry = requestQueue.poll();
            } else {
                entry = lastHalfEntry;
                lastHalfEntry = null;
            }
            if (entry == null) {
                break;
            }
            R req = entry.getKey();
            writeLastRequest = req;
            if (req.getRequestid() == null && req.canMerge(conn)) {
                SimpleEntry<R, ClientFuture<R>> r;
                while ((r = requestQueue.poll()) != null) {
                    i++;
                    if (!req.merge(conn, r.getKey())) {
                        break;
                    }
                    ClientFuture<R> f = entry.getValue();
                    if (f != null) {
                        f.incrMergeCount();
                    }
                    //req.respFuture.mergeCount++;
                }
                req.accept(conn, rw);
                if (r != null) {
                    r.getKey().accept(conn, rw);
                    req = r.getKey();
                }
            } else {
                req.accept(conn, rw);
            }
            c++;
            if (!req.isCompleted()) {
                lastHalfEntry = entry;
                this.pauseWriting.set(true);
                break;
            }
        }
        if (c > 0) { //当Client连接Server后先从Server读取数据时,会先发送一个EMPTY的request，这样writeArray.count就会为0
            channel.write(rw, writeHandler);
            return true;
        }
        if (pw.get()) {
            writePending.compareAndSet(true, false);
        }
        return false;
    }

    protected void preComplete(P resp, R req, Throwable exc) {
    }

    protected final CompletionHandler<Integer, ByteBuffer> readHandler = new CompletionHandler<Integer, ByteBuffer>() {

        @Override
        public void completed(Integer count, ByteBuffer attachment) {
            if (count < 1) {
                channel.setReadBuffer(attachment);
                dispose(new NonReadableChannelException());
                return;
            }
            try {
                attachment.flip();
                decodeResponse(attachment);
            } catch (Throwable e) {
                channel.setReadBuffer(attachment);
                dispose(e);
            }
        }

        protected void completeResponse(ClientResponse<P> rs, ClientFuture respFuture) {
            if (respFuture != null) {
                if (!respFuture.request.isCompleted()) {
                    if (rs.exc == null) {
                        Serializable reqid = respFuture.request.getRequestid();
                        if (reqid == null) {
                            responseQueue.offerFirst(respFuture);
                        } else {
                            responseMap.put(reqid, respFuture);
                        }
                        pauseWriting.set(false);
                        return;
                    } else { //异常了需要清掉半包
                        lastHalfEntry = null;
                        pauseWriting.set(false);
                    }
                }
                respWaitingCounter.decrement();
                if (isAuthenticated() && client.respDoneCounter != null) {
                    client.respDoneCounter.increment();
                }
                try {
                    respFuture.cancelTimeout();
                    ClientRequest request = respFuture.request;
                    //if (client.finest) client.logger.log(Level.FINEST, Utility.nowMillis() + ": " + Thread.currentThread().getName() + ": " + ClientConnection.this + ", 回调处理, req=" + request + ", message=" + rs.message);
                    preComplete(rs.message, (R) request, rs.exc);
                    WorkThread workThread = null;
                    if (request != null) {
                        workThread = request.workThread;
                        request.workThread = null;
                    }
//                    if (rs.exc != null) {
//                        if (workThread == null || workThread == Thread.currentThread() || workThread.inIO()
//                            || workThread.getState() != Thread.State.RUNNABLE) {
//                            if (request != null) {
//                                Traces.currTraceid(request.traceid);
//                            }
//                            respFuture.completeExceptionally(rs.exc);
//                        } else {
//                            workThread.execute(() -> {
//                                if (request != null) {
//                                    Traces.currTraceid(request.traceid);
//                                }
//                                respFuture.completeExceptionally(rs.exc);
//                            });
//                        }
//                    } else {
//                        if (workThread == null || workThread == Thread.currentThread() || workThread.inIO()
//                            || workThread.getState() != Thread.State.RUNNABLE) {
//                            if (request != null) {
//                                Traces.currTraceid(request.traceid);
//                            }
//                            respFuture.complete(rs.message);
//                        } else {
//                            workThread.execute(() -> {
//                                if (request != null) {
//                                    Traces.currTraceid(request.traceid);
//                                }
//                                respFuture.complete(rs.message);
//                            });
//                        }
//                    }
                    if (workThread == null || workThread.getWorkExecutor() == null) {
                        workThread = channel.getReadIOThread();
                    }
                    if (rs.exc != null) {
                        workThread.runWork(() -> {
                            if (request != null) {
                                Traces.currTraceid(request.traceid);
                            }
                            respFuture.completeExceptionally(rs.exc);
                        });
                    } else {
                        workThread.runWork(() -> {
                            if (request != null) {
                                Traces.currTraceid(request.traceid);
                            }
                            respFuture.complete(rs.message);
                        });
                    }
                } catch (Throwable t) {
                    client.logger.log(Level.INFO, "Complete result error, request: " + respFuture.request, t);
                }
            }
        }

        public void decodeResponse(ByteBuffer buffer) {
            if (codec.decodeMessages(buffer, readArray)) { //成功了
                readArray.clear();
                List<ClientResponse<P>> results = codec.pollMessages();
                if (results != null) {
                    for (ClientResponse<P> rs : results) {
                        Serializable reqid = rs.getRequestid();
                        ClientFuture respFuture = reqid == null ? responseQueue.poll() : responseMap.remove(reqid);
                        if (respFuture != null) {
                            int mergeCount = respFuture.getMergeCount();
                            completeResponse(rs, respFuture);
                            if (mergeCount > 0) {
                                for (int i = 0; i < mergeCount; i++) {
                                    respFuture = reqid == null ? responseQueue.poll() : responseMap.remove(reqid);
                                    if (respFuture != null) {
                                        completeResponse(rs, respFuture);
                                    }
                                }
                            }
                        }
                    }
                }

                if (buffer.hasRemaining()) {
                    decodeResponse(buffer);
                } else if (isWaitingResponseEmpty()) { //队列都已处理完了
                    buffer.clear();
                    channel.setReadBuffer(buffer);
                    if (readPending.compareAndSet(true, false)) {
                        //无消息处理
                    } else {
                        channel.read(this);
                    }
                } else { //还有消息需要读取
                    if ((!requestQueue.isEmpty() || lastHalfEntry != null) && writePending.compareAndSet(false, true)) {
                        //先写后读取
                        if (!sendWrite(true)) {
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

    @SuppressWarnings({"LeakingThisInConstructor", "OverridableMethodCallInConstructor"})
    public ClientConnection(Client client, int index, AsyncConnection channel) {
        this.client = client;
        this.codec = createCodec();
        this.index = index;
        this.respWaitingCounter = client.connRespWaitings[index];
        this.channel = channel.beforeCloseListener(this);
    }

    protected abstract ClientCodec createCodec();

    protected final CompletableFuture<P> writeChannel(R request) {
        ClientFuture respFuture;
        if (request.isCloseType()) {
            respFuture = createClientFuture(null);
            closeFuture = respFuture;
        } else {
            respFuture = createClientFuture(request);
            int rts = this.channel.getReadTimeoutSeconds();
            if (rts > 0 && respFuture.request != null) {
                respFuture.setConn(this);
                respFuture.setTimeout(client.timeoutScheduler.schedule(respFuture, rts, TimeUnit.SECONDS));
            }
        }
        respWaitingCounter.increment(); //放在writeChannelInThread计数会延迟，导致不准确
        if (channel.inCurrWriteThread()) {
            writeChannelInThread(request, respFuture);
        } else {
            channel.executeWrite(() -> writeChannelInThread(request, respFuture));
        }
        return respFuture;
    }

    private void writeChannelInThread(R request, ClientFuture<R> respFuture) {
        Serializable reqid = request.getRequestid();
        //保证顺序一致
        ClientFuture future;
        if (request.isCloseType()) {
            future = null;
            responseQueue.offer(ClientFuture.EMPTY);
        } else {
            future = respFuture;
            if (reqid == null) {
                responseQueue.offer(respFuture);
            } else {
                responseMap.put(reqid, respFuture);
            }
        }
        requestQueue.offer(new SimpleEntry<>(request, future));
        if (isAuthenticated() && client.reqWritedCounter != null) {
            client.reqWritedCounter.increment();
        }
        if (writePending.compareAndSet(false, true)) {
            sendWrite(true);
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

    public ClientCodec<R, P> getCodec() {
        return codec;
    }

    @Override //AsyncConnection.beforeCloseListener
    public void accept(AsyncConnection t) {
        respWaitingCounter.reset();
        client.connOpenStates[index].set(false);
        client.connArray[index] = null; //必须connflags之后
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
