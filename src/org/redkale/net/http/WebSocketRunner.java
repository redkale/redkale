/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import static org.redkale.net.http.WebSocket.*;
import org.redkale.net.http.WebSocketPacket.FrameType;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.logging.*;
import org.redkale.util.ByteArray;

/**
 * WebSocket的消息接收发送器, 一个WebSocket对应一个WebSocketRunner
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
class WebSocketRunner implements Runnable {

    private final WebSocketEngine engine;

    private final WebSocket webSocket;

    protected final HttpContext context;

    protected final boolean mergemsg;

    protected final Semaphore writeSemaphore = new Semaphore(1);

    protected final LinkedBlockingQueue<WriteEntry> writeQueue = new LinkedBlockingQueue();

    volatile boolean closed = false;

    FrameType currSeriesMergeFrameType;

    ByteArray currSeriesMergeMessage;

    private final BiConsumer<WebSocket, Object> restMessageConsumer;  //主要供RestWebSocket使用

    protected long lastSendTime;

    protected long lastReadTime;

    WebSocketRunner(HttpContext context, WebSocket webSocket, BiConsumer<WebSocket, Object> messageConsumer) {
        this.context = context;
        this.engine = webSocket._engine;
        this.webSocket = webSocket;
        this.mergemsg = webSocket._engine.mergemsg;
        this.restMessageConsumer = messageConsumer;
    }

    @Override
    public void run() {
        final boolean debug = context.getLogger().isLoggable(Level.FINEST);
        final WebSocketRunner self = this;
        try {
            CompletableFuture connectfFuture = webSocket.onConnected();
            if (connectfFuture != null) connectfFuture.join();
            webSocket._channel.setReadTimeoutSeconds(300); //读取超时5分钟
            if (webSocket._channel.isOpen()) {
                final int wsmaxbody = webSocket._engine.wsmaxbody;
                webSocket._channel.read(new CompletionHandler<Integer, ByteBuffer>() {

                    //尚未解析完的数据包
                    private WebSocketPacket unfinishPacket;

                    //当接收的数据流长度大于ByteBuffer长度时， 则需要额外的ByteBuffer 辅助;
                    private final List<ByteBuffer> exBuffers = new ArrayList<>();

                    private final SimpleEntry<String, byte[]> halfBytes = new SimpleEntry("", null);

                    @Override
                    public void completed(Integer count, ByteBuffer readBuffer) {
                        if (count < 1) {
                            if (debug) context.getLogger().log(Level.FINEST, "WebSocketRunner(userid=" + webSocket.getUserid() + ") abort on read buffer count, force to close channel, live " + (System.currentTimeMillis() - webSocket.getCreatetime()) / 1000 + " seconds");
                            closeRunner(CLOSECODE_ILLPACKET, "read buffer count is " + count);
                            return;
                        }
                        try {
                            lastReadTime = System.currentTimeMillis();
                            readBuffer.flip();

                            WebSocketPacket onePacket = null;
                            if (unfinishPacket != null) {
                                if (unfinishPacket.receiveBody(context.getLogger(), self, webSocket, readBuffer)) { //已经接收完毕
                                    onePacket = unfinishPacket;
                                    unfinishPacket = null;
                                    for (ByteBuffer b : exBuffers) {
                                        webSocket._channel.offerBuffer(b);
                                    }
                                    exBuffers.clear();
                                } else { //需要继续接收,  此处不能回收readBuffer
                                    webSocket._channel.read(this);
                                    return;
                                }
                            }

                            final List<WebSocketPacket> packets = new ArrayList<>();
                            if (onePacket != null) packets.add(onePacket);
                            try {
                                while (true) {
                                    WebSocketPacket packet = new WebSocketPacket().decodePacket(context.getLogger(), self, webSocket, wsmaxbody, halfBytes, readBuffer);
                                    if (packet == WebSocketPacket.NONE) break; //解析完毕但是buffer有多余字节
                                    if (packet != null && !packet.isReceiveFinished()) {
                                        unfinishPacket = packet;
                                        if (readBuffer.hasRemaining()) {
                                            exBuffers.add(readBuffer);
                                        }
                                        break;
                                    }
                                    packets.add(packet);
                                    if (packet == null || !readBuffer.hasRemaining()) break;
                                }
                            } catch (Exception e) {
                                context.getLogger().log(Level.SEVERE, "WebSocket parse message error", e);
                                webSocket.onOccurException(e, null);
                            }
                            //继续监听消息
                            if (readBuffer.hasRemaining()) { //exBuffers缓存了
                                readBuffer = webSocket._channel.pollReadBuffer();
                            } else {
                                readBuffer.clear();
                            }
                            if (halfBytes.getValue() != null) {
                                readBuffer.put(halfBytes.getValue());
                                halfBytes.setValue(null);
                            }
                            webSocket._channel.setReadBuffer(readBuffer);
                            webSocket._channel.read(this);

                            //消息处理
                            for (final WebSocketPacket packet : packets) {
                                if (packet == null) {
                                    if (debug) context.getLogger().log(Level.FINEST, "WebSocketRunner abort on decode WebSocketPacket, force to close channel, live " + (System.currentTimeMillis() - webSocket.getCreatetime()) / 1000 + " seconds");
                                    failed(null, readBuffer);
                                    return;
                                }
                                if (packet.receiveMessage == WebSocketPacket.MESSAGE_NIL) continue; //last=false && mergemsg=true 的粘包

                                if (packet.type == FrameType.TEXT) {
                                    try {
                                        if (packet.receiveType == WebSocketPacket.MessageType.STRING) {
                                            webSocket.onMessage((String) packet.receiveMessage, packet.last);
                                        } else {
                                            if (restMessageConsumer != null) { //主要供RestWebSocket使用
                                                restMessageConsumer.accept(webSocket, packet.receiveMessage);
                                            } else {
                                                webSocket.onMessage(packet.receiveMessage, packet.last);
                                            }
                                        }
                                    } catch (Throwable e) {
                                        context.getLogger().log(Level.SEVERE, "WebSocket onTextMessage error (" + packet + ")", e);
                                    }
                                } else if (packet.type == FrameType.BINARY) {
                                    try {
                                        if (packet.receiveType == WebSocketPacket.MessageType.BYTES) {
                                            webSocket.onMessage((byte[]) packet.receiveMessage, packet.last);
                                        } else {
                                            if (restMessageConsumer != null) { //主要供RestWebSocket使用
                                                restMessageConsumer.accept(webSocket, packet.receiveMessage);
                                            } else {
                                                webSocket.onMessage(packet.receiveMessage, packet.last);
                                            }
                                        }
                                    } catch (Throwable e) {
                                        context.getLogger().log(Level.SEVERE, "WebSocket onBinaryMessage error (" + packet + ")", e);
                                    }
                                } else if (packet.type == FrameType.PING) {
                                    try {
                                        webSocket.onPing((byte[]) packet.receiveMessage);
                                    } catch (Exception e) {
                                        context.getLogger().log(Level.SEVERE, "WebSocket onPing error (" + packet + ")", e);
                                    }
                                } else if (packet.type == FrameType.PONG) {
                                    try {
                                        //if (debug) context.getLogger().log(Level.FINEST, "WebSocketRunner onMessage by PONG FrameType : " + packet);
                                        webSocket.onPong((byte[]) packet.receiveMessage);
                                    } catch (Exception e) {
                                        context.getLogger().log(Level.SEVERE, "WebSocket onPong error (" + packet + ")", e);
                                    }
                                } else if (packet.type == FrameType.CLOSE) {
                                    if (debug) context.getLogger().log(Level.FINEST, "WebSocketRunner onMessage by CLOSE FrameType : " + packet);
                                    closeRunner(CLOSECODE_CLIENTCLOSE, "received CLOSE frame-type message");
                                    return;
                                } else {
                                    context.getLogger().log(Level.WARNING, "WebSocketRunner onMessage by unknown FrameType : " + packet);
                                    closeRunner(CLOSECODE_ILLPACKET, "received unknown frame-type message");
                                    return;
                                }
                            }
                        } catch (Exception e) {
                            context.getLogger().log(Level.WARNING, "WebSocketRunner(userid=" + webSocket.getUserid() + ") onMessage by received error", e);
                            closeRunner(CLOSECODE_WSEXCEPTION, "websocket-received error");
                        }
                    }

                    @Override
                    public void failed(Throwable exc, ByteBuffer attachment2) {
                        if (exc != null) {
                            if (debug) context.getLogger().log(Level.FINEST, "WebSocketRunner read WebSocketPacket failed, force to close channel, live " + (System.currentTimeMillis() - webSocket.getCreatetime()) / 1000 + " seconds", exc);
                            closeRunner(CLOSECODE_WSEXCEPTION, "read websocket-packet failed");
                        } else {
                            closeRunner(CLOSECODE_WSEXCEPTION, "decode websocket-packet error");
                        }
                    }
                });
            } else {
                if (debug) context.getLogger().log(Level.FINEST, "WebSocketRunner abort by AsyncConnection closed");
                closeRunner(RETCODE_WSOCKET_CLOSED, "webSocket channel is not opened");
            }
        } catch (Throwable e) {
            if (debug) context.getLogger().log(Level.FINEST, "WebSocketRunner abort on read bytes from channel, force to close channel, live " + (System.currentTimeMillis() - webSocket.getCreatetime()) / 1000 + " seconds", e);
            closeRunner(CLOSECODE_WSEXCEPTION, "read bytes from channel error");
        }
    }

    public CompletableFuture<Integer> sendMessage(WebSocketPacket packet) {
        if (packet == null) return CompletableFuture.completedFuture(RETCODE_SEND_ILLPACKET);
        if (closed) return CompletableFuture.completedFuture(RETCODE_WSOCKET_CLOSED);
        boolean debug = context.getLogger().isLoggable(Level.FINEST);
        //System.out.println("推送消息");        
        final CompletableFuture<Integer> futureResult = new CompletableFuture<>();
        try {
            if (packet.sendBuffers == null) packet.encodePacket(webSocket._channel.getBufferSupplier(), webSocket._channel.getBufferConsumer(), webSocket._engine.cryptor);
            ByteBuffer[] buffers = packet.duplicateSendBuffers();
            //if (debug) context.getLogger().log(Level.FINEST, "wsrunner.sending websocket message:  " + packet);
            CompletionHandler<Integer, ByteBuffer[]> handler = new CompletionHandler<Integer, ByteBuffer[]>() {

                private CompletableFuture<Integer> future = futureResult;

                @Override
                public void completed(Integer result, ByteBuffer[] attachments) {
                    if (attachments == null || closed) {
                        if (future != null) {
                            future.complete(RETCODE_WSOCKET_CLOSED);
                            future = null;
                            if (attachments != null) {
                                for (ByteBuffer buf : attachments) {
                                    webSocket._channel.offerBuffer(buf);
                                }
                            }
                        }
                        return;
                    }
                    try {
                        int index = -1;
                        for (int i = 0; i < attachments.length; i++) {
                            if (attachments[i].hasRemaining()) {
                                index = i;
                                break;
                            }
                        }
                        if (index >= 0) { //ByteBuffer[]统一回收的可以采用此写法
                            webSocket._channel.write(attachments, index, attachments.length - index, attachments, this);
                            return;
                        }
                        if (future != null) {
                            future.complete(0);
                            future = null;
                            if (attachments != null) {
                                for (ByteBuffer buf : attachments) {
                                    webSocket._channel.offerBuffer(buf);
                                }
                            }
                        }
                    } catch (Exception e) {
                        future.complete(RETCODE_SENDEXCEPTION);
                        closeRunner(RETCODE_SENDEXCEPTION, "websocket send message failed on rewrite");
                        context.getLogger().log(Level.WARNING, "WebSocket sendMessage abort on rewrite, force to close channel, live " + (System.currentTimeMillis() - webSocket.getCreatetime()) / 1000 + " seconds", e);
                    }
                }

                @Override
                public void failed(Throwable exc, ByteBuffer[] attachments) {
                    future.complete(RETCODE_SENDEXCEPTION);
                    closeRunner(RETCODE_SENDEXCEPTION, "websocket send message failed on CompletionHandler");
                    if (exc != null && context.getLogger().isLoggable(Level.FINER)) {
                        context.getLogger().log(Level.FINER, "WebSocket sendMessage on CompletionHandler failed, force to close channel, live " + (System.currentTimeMillis() - webSocket.getCreatetime()) / 1000 + " seconds", exc);
                    }

                }
            };
            this.lastSendTime = System.currentTimeMillis();
            if (writeSemaphore.tryAcquire()) {
                webSocket._channel.write(buffers, buffers, handler);
            } else {
                writeQueue.add(new WriteEntry(buffers, handler));
            }

        } catch (Exception t) {
            futureResult.complete(RETCODE_SENDEXCEPTION);
            closeRunner(RETCODE_SENDEXCEPTION, "websocket send message failed on channel.write");
            if (t != null && context.getLogger().isLoggable(Level.FINER)) {
                context.getLogger().log(Level.FINER, "WebSocket sendMessage abort, force to close channel, live " + (System.currentTimeMillis() - webSocket.getCreatetime()) / 1000 + " seconds", t);
            }

        }
        return futureResult.whenComplete((r, t) -> {
            WriteEntry entry = writeQueue.poll();
            if (entry != null) {
                webSocket._channel.write(entry.writeBuffers, entry.writeBuffers, entry.writeHandler);
            } else {
                writeSemaphore.release();
            }
        });
    }

    public boolean isClosed() {
        return closed;
    }

    public CompletableFuture<Void> closeRunner(int code, String reason) {
        if (closed) return null;
        synchronized (this) {
            if (closed) return null;
            closed = true;
            CompletableFuture<Void> future = engine.removeLocalThenClose(webSocket);
            webSocket._channel.dispose();
            CompletableFuture closeFuture = webSocket.onClose(code, reason);
            if (closeFuture == null) return future;
            return CompletableFuture.allOf(future, closeFuture);
        }
    }

    private static class WriteEntry {

        ByteBuffer[] writeBuffers;

        CompletionHandler writeHandler;

        public WriteEntry(ByteBuffer[] writeBuffers, CompletionHandler writeHandler) {
            this.writeBuffers = writeBuffers;
            this.writeHandler = writeHandler;
        }

    }
}
