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
 * 每个ClientConnection绑定一个独立的ClientCodec实例, 只会同一读线程里运行
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.3.0
 * @param <R> ClientRequest
 * @param <P> 响应对象
 */
public abstract class ClientCodec<R extends ClientRequest, P> implements CompletionHandler<Integer, ByteBuffer> {

    protected final ClientConnection connection;

    private final List<ClientResponse<P>> respResults = new ArrayList<>();

    private final ByteArray readArray = new ByteArray();

    private final ObjectPool<ClientResponse> respPool = ObjectPool.createUnsafePool(256, t -> new ClientResponse(), ClientResponse::prepare, ClientResponse::recycle);

    public ClientCodec(ClientConnection connection) {
        Objects.requireNonNull(connection);
        this.connection = connection;
    }

    //返回true: array会clear, 返回false: buffer会clear
    public abstract boolean decodeMessages(ByteBuffer buffer, ByteArray array);

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
        Deque<ClientFuture> responseQueue = connection.responseQueue;
        Map<Serializable, ClientFuture> responseMap = connection.responseMap;
        if (decodeMessages(buffer, readArray)) { //成功了
            readArray.clear();
            for (ClientResponse<P> cr : respResults) {
                Serializable reqid = cr.getRequestid();
                ClientFuture respFuture = reqid == null ? responseQueue.poll() : responseMap.remove(reqid);
                if (respFuture != null) {
                    completeResponse(respFuture, cr.message, cr.exc);
                }
                respPool.accept(cr);
            }
            respResults.clear();

            if (buffer.hasRemaining()) {
                decodeResponse(buffer);
            } else { //队列都已处理完了
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

    private void completeResponse(ClientFuture respFuture, P message, Throwable exc) {
        if (respFuture != null) {
            ClientRequest request = respFuture.request;
            try {
                if (!request.isCompleted()) {
                    if (exc == null) {
                        connection.sendHalfWrite(exc);
                        //request没有发送完，respFuture需要再次接收
                        return;
                    } else { //异常了需要清掉半包
                        connection.sendHalfWrite(exc);
                    }
                }
                connection.respWaitingCounter.decrement();
                if (connection.isAuthenticated()) {
                    connection.client.incrRespDoneCounter();
                }
                respFuture.cancelTimeout();
                //if (client.finest) client.logger.log(Level.FINEST, Utility.nowMillis() + ": " + Thread.currentThread().getName() + ": " + ClientConnection.this + ", 回调处理, req=" + request + ", message=" + rs.message);
                connection.preComplete(message, (R) request, exc);
                WorkThread workThread = request.workThread;
                request.workThread = null;
                if (workThread == null || workThread.getWorkExecutor() == null) {
                    workThread = connection.channel.getReadIOThread();
                }
                if (exc != null) {
                    workThread.runWork(() -> {
                        Traces.currTraceid(request.traceid);
                        respFuture.completeExceptionally(exc);
                    });
                } else {
                    final Object rs = request.respTransfer == null ? message : request.respTransfer.apply(message);
                    workThread.runWork(() -> {
                        Traces.currTraceid(request.traceid);
                        respFuture.complete(rs);
                    });
                }
            } catch (Throwable t) {
                connection.client.logger.log(Level.INFO, "Complete result error, request: " + respFuture.request, t);
            }
        }
    }

    @Override
    public final void failed(Throwable t, ByteBuffer attachment) {
        connection.dispose(t);
    }

    protected Iterator<ClientFuture> responseIterator() {
        return connection.responseQueue.iterator();
    }

    protected List<ClientResponse<P>> pollMessages() {
        List<ClientResponse<P>> rs = new ArrayList<>(respResults);
        this.respResults.clear();
        return rs;
    }

    public void addMessage(R request, P result) {
        this.respResults.add(respPool.get().set(request, result));
    }

    public void addMessage(R request, Throwable exc) {
        this.respResults.add(respPool.get().set(request, exc));
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }

}
