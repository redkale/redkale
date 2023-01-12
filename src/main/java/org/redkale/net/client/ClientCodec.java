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
 * 每个ClientConnection绑定一个独立的ClientCodec实例
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

    private final List<ClientResponse<P>> repsResults = new ArrayList<>();

    private final ClientConnection connection;

    private final ByteArray readArray = new ByteArray();

    public ClientCodec(ClientConnection connection) {
        this.connection = connection;
    }

    //返回true: array会clear, 返回false: buffer会clear
    public abstract boolean decodeMessages(ClientConnection connection, ByteBuffer buffer, ByteArray array);

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
        if (decodeMessages(connection, buffer, readArray)) { //成功了
            readArray.clear();
            List<ClientResponse<P>> results = pollMessages();
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

    private void completeResponse(ClientResponse<P> rs, ClientFuture respFuture) {
        if (respFuture != null) {
            ClientRequest request = respFuture.request;
            if (!request.isCompleted()) {
                if (rs.exc == null) {
                    //request没有发送完，respFuture需要再次接收
                    Serializable reqid = request.getRequestid();
                    if (reqid == null) {
                        connection.responseQueue.offerFirst(respFuture);
                    } else {
                        connection.responseMap.put(reqid, respFuture);
                    }
                    connection.pauseWriting.set(false);
                    connection.wakeupWrite();
                    return;
                } else { //异常了需要清掉半包
                    connection.lastHalfEntry = null;
                    connection.pauseWriting.set(false);
                    connection.wakeupWrite();
                }
            }
            connection.respWaitingCounter.decrement();
            if (connection.isAuthenticated()) {
                connection.client.incrRespDoneCounter();
            }
            try {
                respFuture.cancelTimeout();
                //if (client.finest) client.logger.log(Level.FINEST, Utility.nowMillis() + ": " + Thread.currentThread().getName() + ": " + ClientConnection.this + ", 回调处理, req=" + request + ", message=" + rs.message);
                connection.preComplete(rs.message, (R) request, rs.exc);
                WorkThread workThread = request.workThread;
                request.workThread = null;
                if (workThread == null || workThread.getWorkExecutor() == null) {
                    workThread = connection.channel.getReadIOThread();
                }
                if (rs.exc != null) {
                    workThread.runWork(() -> {
                        Traces.currTraceid(request.traceid);
                        respFuture.completeExceptionally(rs.exc);
                    });
                } else {
                    workThread.runWork(() -> {
                        Traces.currTraceid(request.traceid);
                        respFuture.complete(rs.message);
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

    public List<ClientResponse<P>> pollMessages() {
        List<ClientResponse<P>> rs = new ArrayList<>(repsResults);
        this.repsResults.clear();
        return rs;
    }

    public ClientConnection getConnection() {
        return connection;
    }

    public void addMessage(P result) {
        this.repsResults.add(new ClientResponse<>(result));
    }

    public void addMessage(Throwable exc) {
        this.repsResults.add(new ClientResponse<>(exc));
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }

}
