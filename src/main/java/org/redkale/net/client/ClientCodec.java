/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.client;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.logging.Level;
import org.redkale.convert.json.JsonConvert;
import org.redkale.net.*;
import org.redkale.util.*;

/**
 * 每个ClientConnection绑定一个独立的ClientCodec实例, 只会同一读线程ReadIOThread里运行
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.3.0
 * @param <R> ClientRequest
 * @param <P> 响应对象
 */
public abstract class ClientCodec<R extends ClientRequest, P extends ClientResult> implements CompletionHandler<Integer, ByteBuffer> {

    private final List<ClientResponse<R, P>> respResults = new ArrayList<>();

    private final ByteArray readArray = new ByteArray();

    private final ObjectPool<ClientResponse<R, P>> respPool = ObjectPool.createUnsafePool(256, t -> new ClientResponse(), ClientResponse::prepare, ClientResponse::recycle);

    protected final ClientConnection<R, P> connection;

    protected ClientMessageListener messageListener;

    protected ClientCodec(ClientConnection<R, P> connection) {
        Objects.requireNonNull(connection);
        this.connection = connection;
    }

    public abstract void decodeMessages(ByteBuffer buffer, ByteArray array);

    public ClientCodec<R, P> withMessageListener(ClientMessageListener listener) {
        this.messageListener = listener;
        return this;
    }

    @Override
    public final void completed(Integer count, ByteBuffer attachment) {
        AsyncConnection channel = connection.channel;
        if (count < 1) {
            channel.setReadBuffer(attachment);
            connection.dispose(new NonReadableChannelException());
            return;
        }
        try {
            attachment.flip();
            decodeResponse(attachment);
        } catch (Throwable e) {
            channel.setReadBuffer(attachment);
            connection.dispose(e);
        }
    }

    private void decodeResponse(ByteBuffer buffer) {
        AsyncConnection channel = connection.channel;
        connection.currRespIterator = null;
        decodeMessages(buffer, readArray);
        if (!respResults.isEmpty()) { //存在解析结果
            connection.currRespIterator = null;
            readArray.clear();
            boolean keepAlive = true;
            for (ClientResponse<R, P> cr : respResults) {
                connection.doneResponseCounter.increment();
                if (cr.isError()) {
                    connection.dispose(cr.cause);
                    return;
                } else if (messageListener != null) {
                    messageListener.onMessage(connection, cr);
                    respPool.accept(cr);
                } else {
                    ClientFuture respFuture = connection.pollRespFuture(cr.getRequestid());
                    if (respFuture != null) {
                        if (respFuture.request != cr.request) {
                            connection.dispose(new RedkaleException("request pipeline error"));
                            return;
                        }
                        responseComplete(false, respFuture, cr.message, cr.cause);
                        if (cr.message != null && !cr.message.isKeepAlive()) {
                            keepAlive = false;
                        }
                    }
                    respPool.accept(cr);
                }
            }
            respResults.clear();
            if (!keepAlive) {
                connection.dispose(null);
            }
            if (buffer.hasRemaining()) { //还有响应数据包
                decodeResponse(buffer);
            } else if (keepAlive) { //队列都已处理完了
                buffer.clear();
                channel.setReadBuffer(buffer);
                channel.readRegister(this);
            }
        } else { //数据不全， 继续读
            connection.currRespIterator = null;
            buffer.clear();
            channel.setReadBuffer(buffer);
            channel.read(this);
        }
    }

    void responseComplete(boolean halfCompleted, ClientFuture<R, Object> respFuture, P message, Throwable exc) {
        R request = respFuture.request;
        Traces.currentTraceid(request.getTraceid());
        AsyncIOThread readThread = connection.channel.getReadIOThread();
        final WorkThread workThread = request.workThread;
        try {
            if (!halfCompleted && !request.isCompleted()) {
                if (exc == null) {
                    connection.sendHalfWriteInReadThread(request, exc);
                    //request没有发送完，respFuture需要再次接收
                    return;
                } else {
                    connection.sendHalfWriteInReadThread(request, exc);
                    //异常了需要清掉半包
                }
            }
            connection.respWaitingCounter.decrement();
            if (connection.isAuthenticated()) {
                connection.client.incrRespDoneCounter();
            }
            respFuture.cancelTimeout();
//            if (connection.client.debug) {
//                connection.client.logger.log(Level.FINEST, Utility.nowMillis() + ": " + Thread.currentThread().getName() + ": " + connection
//                    + ", 回调处理(" + (request != null ? (System.currentTimeMillis() - request.getCreateTime()) : -1) + "ms), req=" + request + ", message=" + message, exc);
//            }
            connection.preComplete(message, (R) request, exc);

            if (exc == null) {
                final Object rs = request.respTransfer == null ? message : request.respTransfer.apply(message);
                if (workThread == null) {
                    readThread.runWork(() -> {
                        Traces.currentTraceid(request.traceid);
                        respFuture.complete(rs);
                        Traces.removeTraceid();
                    });
                } else if (workThread.getState() == Thread.State.RUNNABLE) { //fullCache时state不是RUNNABLE
                    if (workThread.inIO()) {
                        Traces.currentTraceid(request.traceid);
                        respFuture.complete(rs);
                        Traces.removeTraceid();
                    } else {
                        workThread.execute(() -> {
                            Traces.currentTraceid(request.traceid);
                            respFuture.complete(rs);
                            Traces.removeTraceid();
                        });
                    }
                } else {
                    Utility.execute(() -> {
                        Traces.currentTraceid(request.traceid);
                        respFuture.complete(rs);
                        Traces.removeTraceid();
                    });
                }
            } else { //异常
                if (workThread == null) {
                    readThread.runWork(() -> {
                        Traces.currentTraceid(request.traceid);
                        respFuture.completeExceptionally(exc);
                        Traces.removeTraceid();
                    });
                } else if (workThread.getState() == Thread.State.RUNNABLE) { //fullCache时state不是RUNNABLE
                    if (workThread.inIO()) {
                        Traces.currentTraceid(request.traceid);
                        respFuture.completeExceptionally(exc);
                        Traces.removeTraceid();
                    } else {
                        workThread.execute(() -> {
                            Traces.currentTraceid(request.traceid);
                            respFuture.completeExceptionally(exc);
                            Traces.removeTraceid();
                        });
                    }
                } else {
                    Utility.execute(() -> {
                        Traces.currentTraceid(request.traceid);
                        respFuture.completeExceptionally(exc);
                        Traces.removeTraceid();
                    });
                }
            }
        } catch (Throwable t) {
            if (workThread == null) {
                readThread.runWork(() -> {
                    Traces.currentTraceid(request.traceid);
                    respFuture.completeExceptionally(t);
                    Traces.removeTraceid();
                });
            } else if (workThread.getState() == Thread.State.RUNNABLE) { //fullCache时state不是RUNNABLE
                if (workThread.inIO()) {
                    Traces.currentTraceid(request.traceid);
                    respFuture.completeExceptionally(t);
                    Traces.removeTraceid();
                } else {
                    workThread.execute(() -> {
                        Traces.currentTraceid(request.traceid);
                        respFuture.completeExceptionally(t);
                        Traces.removeTraceid();
                    });
                }
            } else {
                Utility.execute(() -> {
                    Traces.currentTraceid(request.traceid);
                    respFuture.completeExceptionally(t);
                    Traces.removeTraceid();
                });
            }
            connection.client.logger.log(Level.INFO, "Complete result error, request: " + respFuture.request, t);
        }
    }

    @Override
    public final void failed(Throwable t, ByteBuffer attachment) {
        connection.dispose(t);
    }

    public ClientMessageListener getMessageListener() {
        return messageListener;
    }

    protected R nextRequest() {
        return connection.findRequest(null);
    }

    protected R findRequest(Serializable requestid) {
        return connection.findRequest(requestid);
    }

    protected ClientResponse<R, P> getLastMessage() {
        List<ClientResponse<R, P>> results = this.respResults;
        int size = results.size();
        return size == 0 ? null : results.get(size - 1);
    }

    public void addMessage(R request, P result) {
        this.respResults.add(respPool.get().success(request, result));
    }

    public void addMessage(R request, Throwable exc) {
        this.respResults.add(respPool.get().fail(request, exc));
    }

    public void occurError(R request, Throwable exc) {
        this.respResults.add(new ClientResponse.ClientErrorResponse<>(request, exc));
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }

}
