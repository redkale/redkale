/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import org.redkale.net.AsyncConnection;
import org.redkale.net.Context;
import static org.redkale.net.http.WebSocket.*;
import org.redkale.net.http.WebSocketPacket.FrameType;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.logging.*;
import org.redkale.convert.Convert;
import org.redkale.util.Utility;

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

    private final AsyncConnection channel;

    private final WebSocket webSocket;

    protected final Context context;

    private ByteBuffer readBuffer;

    volatile boolean closed = false;

    private AtomicBoolean writing = new AtomicBoolean();

    private final BlockingQueue<QueueEntry> queue = new ArrayBlockingQueue(1024);

    private final BiConsumer<WebSocket, Object> restMessageConsumer;  //主要供RestWebSocket使用

    protected long lastSendTime;

    WebSocketRunner(Context context, WebSocket webSocket, BiConsumer<WebSocket, Object> messageConsumer, AsyncConnection channel) {
        this.context = context;
        this.engine = webSocket._engine;
        this.webSocket = webSocket;
        this.restMessageConsumer = messageConsumer;
        this.channel = channel;
        this.readBuffer = context.pollBuffer();
    }

    @Override
    public void run() {
        final boolean debug = true;
        try {
            final int wsmaxbody = webSocket._engine.wsmaxbody;
            webSocket.onConnected();
            channel.setReadTimeoutSecond(300); //读取超时5分钟
            if (channel.isOpen()) {
                channel.read(readBuffer, null, new CompletionHandler<Integer, Void>() {

                    private ByteBuffer recentExBuffer;
                    
                    private final List<WebSocketPacket> packets  = new ArrayList<>();

                    //当接收的数据流长度大于ByteBuffer长度时， 则需要额外的ByteBuffer 辅助;
                    private final List<ByteBuffer> readBuffers = new ArrayList<>();

                    @Override
                    public void completed(Integer count, Void attachment1) {
                        if (count < 1 && readBuffers.isEmpty()) {
                            closeRunner(0);
                            if (debug) context.getLogger().log(Level.FINEST, "WebSocketRunner abort on read buffer count, force to close channel, live " + (System.currentTimeMillis() - webSocket.getCreatetime()) / 1000 + " seconds");
                            return;
                        }
                        if (readBuffer == null) return;
                        if (!readBuffer.hasRemaining() && (recentExBuffer == null || !recentExBuffer.hasRemaining())) {
                            final ByteBuffer buffer = context.pollBuffer();
                            recentExBuffer = buffer;
                            readBuffers.add(buffer);
                            channel.read(buffer, null, this);
                            return;
                        }
                        readBuffer.flip();

                        ByteBuffer[] exBuffers = null;
                        if (!readBuffers.isEmpty()) {
                            exBuffers = readBuffers.toArray(new ByteBuffer[readBuffers.size()]);
                            readBuffers.clear();
                            recentExBuffer = null;
                            for (ByteBuffer b : exBuffers) {
                                b.flip();
                            }
                        }

                        try {
                            WebSocketPacket packet;
                            try {
                                packet = new WebSocketPacket().decode(context.getLogger(), readBuffer, exBuffers);
                            } catch (Exception e) { //接收的消息体解析失败
                                webSocket.onOccurException(e, Utility.append(new ByteBuffer[]{readBuffer}, exBuffers == null ? new ByteBuffer[0] : exBuffers));
                                if (readBuffer != null) {
                                    readBuffer.clear();
                                    channel.read(readBuffer, null, this);
                                }
                                return;
                            }
                            if (packet == null) {
                                failed(null, attachment1);
                                if (debug) context.getLogger().log(Level.FINEST, "WebSocketRunner abort on decode WebSocketPacket, force to close channel, live " + (System.currentTimeMillis() - webSocket.getCreatetime()) / 1000 + " seconds");
                                return;
                            }

                            if (packet.type == FrameType.TEXT) {
                                Convert textConvert = webSocket.getTextConvert();
                                if (textConvert == null) {
                                    byte[] message = packet.getReceiveBytes();
                                    if (readBuffer != null) {
                                        readBuffer.clear();
                                        channel.read(readBuffer, null, this);
                                    }
                                    try {
                                        webSocket.onMessage(new String(message, "UTF-8"), packet.last);
                                    } catch (Exception e) {
                                        context.getLogger().log(Level.SEVERE, "WebSocket onBinaryMessage error (" + packet + ")", e);
                                    }
                                } else {
                                    Object message;
                                    try {
                                        message = textConvert.convertFrom(webSocket._messageTextType, packet.receiveMasker, packet.receiveBuffers);
                                    } catch (Exception e) { //接收的消息体解析失败
                                        webSocket.onOccurException(e, packet.receiveBuffers);
                                        if (readBuffer != null) {
                                            readBuffer.clear();
                                            channel.read(readBuffer, null, this);
                                        }
                                        return;
                                    }
                                    if (readBuffer != null) {
                                        readBuffer.clear();
                                        channel.read(readBuffer, null, this);
                                    }
                                    try {
                                        if (restMessageConsumer != null) { //主要供RestWebSocket使用
                                            restMessageConsumer.accept(webSocket, message);
                                        } else {
                                            webSocket.onMessage(message, packet.last);
                                        }
                                    } catch (Exception e) {
                                        context.getLogger().log(Level.SEVERE, "WebSocket onTextMessage error (" + packet + ")", e);
                                    }
                                }
                            } else if (packet.type == FrameType.BINARY) {
                                Convert binaryConvert = webSocket.getBinaryConvert();
                                if (binaryConvert == null) {
                                    byte[] message = packet.getReceiveBytes();
                                    if (readBuffer != null) {
                                        readBuffer.clear();
                                        channel.read(readBuffer, null, this);
                                    }
                                    try {
                                        webSocket.onMessage(message, packet.last);
                                    } catch (Exception e) {
                                        context.getLogger().log(Level.SEVERE, "WebSocket onBinaryMessage error (" + packet + ")", e);
                                    }
                                } else {
                                    Object message;
                                    try {
                                        message = binaryConvert.convertFrom(webSocket._messageTextType, packet.receiveMasker, packet.receiveBuffers);
                                    } catch (Exception e) {  //接收的消息体解析失败
                                        webSocket.onOccurException(e, packet.receiveBuffers);
                                        if (readBuffer != null) {
                                            readBuffer.clear();
                                            channel.read(readBuffer, null, this);
                                        }
                                        return;
                                    }
                                    if (readBuffer != null) {
                                        readBuffer.clear();
                                        channel.read(readBuffer, null, this);
                                    }
                                    try {
                                        if (restMessageConsumer != null) { //主要供RestWebSocket使用
                                            restMessageConsumer.accept(webSocket, message);
                                        } else {
                                            webSocket.onMessage(message, packet.last);
                                        }
                                    } catch (Exception e) {
                                        context.getLogger().log(Level.SEVERE, "WebSocket onTextMessage error (" + packet + ")", e);
                                    }
                                }
                            } else if (packet.type == FrameType.PONG) {
                                byte[] message = packet.getReceiveBytes();
                                if (readBuffer != null) {
                                    readBuffer.clear();
                                    channel.read(readBuffer, null, this);
                                }
                                try {
                                    webSocket.onPong(message);
                                } catch (Exception e) {
                                    context.getLogger().log(Level.SEVERE, "WebSocket onPong error (" + packet + ")", e);
                                }
                            } else if (packet.type == FrameType.PING) {
                                byte[] message = packet.getReceiveBytes();
                                if (readBuffer != null) {
                                    readBuffer.clear();
                                    channel.read(readBuffer, null, this);
                                }
                                try {
                                    webSocket.onPing(message);
                                } catch (Exception e) {
                                    context.getLogger().log(Level.SEVERE, "WebSocket onPing error (" + packet + ")", e);
                                }
                            } else if (packet.type == FrameType.CLOSE) {
                                Logger logger = context.getLogger();
                                if (logger.isLoggable(Level.FINEST)) logger.log(Level.FINEST, "WebSocketRunner onMessage by CLOSE FrameType : " + packet);
                                closeRunner(0);
                            } else {
                                context.getLogger().log(Level.WARNING, "WebSocketRunner onMessage by unknown FrameType : " + packet);
                                if (readBuffer != null) {
                                    readBuffer.clear();
                                    channel.read(readBuffer, null, this);
                                }
                            }
                        } catch (Throwable t) {
                            closeRunner(0);
                            if (debug) context.getLogger().log(Level.FINEST, "WebSocketRunner abort on read WebSocketPacket, force to close channel, live " + (System.currentTimeMillis() - webSocket.getCreatetime()) / 1000 + " seconds", t);
                        } finally {
                            if (exBuffers != null) {
                                for (ByteBuffer b : exBuffers) {
                                    context.offerBuffer(b);
                                }
                            }
                        }
                    }

                    @Override
                    public void failed(Throwable exc, Void attachment2) {
                        closeRunner(0);
                        if (exc != null) {
                            context.getLogger().log(Level.FINEST, "WebSocketRunner read WebSocketPacket failed, force to close channel, live " + (System.currentTimeMillis() - webSocket.getCreatetime()) / 1000 + " seconds", exc);
                        }
                    }
                });
            } else {
                closeRunner(0);
                context.getLogger().log(Level.FINEST, "WebSocketRunner abort by AsyncConnection closed");
            }
        } catch (Exception e) {
            closeRunner(0);
            context.getLogger().log(Level.FINEST, "WebSocketRunner abort on read bytes from channel, force to close channel, live " + (System.currentTimeMillis() - webSocket.getCreatetime()) / 1000 + " seconds", e);
        }
    }

    public CompletableFuture<Integer> sendMessage(WebSocketPacket packet) {
        if (packet == null) return CompletableFuture.completedFuture(RETCODE_SEND_ILLPACKET);
        if (closed) return CompletableFuture.completedFuture(RETCODE_WSOCKET_CLOSED);
        boolean debug = true;
        //System.out.println("推送消息");
        //if (debug) context.getLogger().log(Level.FINEST, "send web socket message:  " + packet);
        final CompletableFuture<Integer> futureResult = new CompletableFuture<>();
        if (writing.getAndSet(true)) {
            queue.add(new QueueEntry(futureResult, packet));
            return futureResult;
        }
        ByteBuffer[] buffers = packet.sendBuffers != null ? packet.duplicateSendBuffers() : packet.encode(this.context.getBufferSupplier());
        try {
            this.lastSendTime = System.currentTimeMillis();
            channel.write(buffers, buffers, new CompletionHandler<Integer, ByteBuffer[]>() {

                private CompletableFuture<Integer> future = futureResult;

                @Override
                public void completed(Integer result, ByteBuffer[] attachments) {
                    if (attachments == null || closed) {
                        if (future != null) {
                            future.complete(RETCODE_WSOCKET_CLOSED);
                            future = null;
                            if (attachments != null) {
                                for (ByteBuffer buf : attachments) {
                                    context.offerBuffer(buf);
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
                        if (index >= 0) {
                            channel.write(attachments, index, attachments.length - index, attachments, this);
                            return;
                        }
                        if (future != null) {
                            future.complete(0);
                            future = null;
                            if (attachments != null) {
                                for (ByteBuffer buf : attachments) {
                                    context.offerBuffer(buf);
                                }
                            }
                        }
                        QueueEntry entry = queue.poll();
                        if (entry != null) {
                            future = entry.future;
                            ByteBuffer[] buffers = entry.packet.sendBuffers != null ? entry.packet.duplicateSendBuffers() : entry.packet.encode(context.getBufferSupplier());
                            lastSendTime = System.currentTimeMillis();
                            channel.write(buffers, buffers, this);
                        }
                    } catch (Exception e) {
                        closeRunner(0);
                        context.getLogger().log(Level.WARNING, "WebSocket sendMessage abort on rewrite, force to close channel, live " + (System.currentTimeMillis() - webSocket.getCreatetime()) / 1000 + " seconds", e);
                    }
                    writing.set(false);
                }

                @Override
                public void failed(Throwable exc, ByteBuffer[] attachments) {
                    writing.set(false);
                    closeRunner(0);
                    if (exc != null) {
                        context.getLogger().log(Level.FINE, "WebSocket sendMessage on CompletionHandler failed, force to close channel, live " + (System.currentTimeMillis() - webSocket.getCreatetime()) / 1000 + " seconds", exc);
                    }
                }
            });
        } catch (Exception t) {
            writing.set(false);
            closeRunner(0);
            context.getLogger().log(Level.FINE, "WebSocket sendMessage abort, force to close channel, live " + (System.currentTimeMillis() - webSocket.getCreatetime()) / 1000 + " seconds", t);
            futureResult.complete(RETCODE_SENDEXCEPTION);
        }
        return futureResult;
    }

    public void closeRunner(int code) {
        if (closed) return;
        synchronized (this) {
            if (closed) return;
            closed = true;
            try {
                channel.close();
            } catch (Throwable t) {
            }
            context.offerBuffer(readBuffer);
            readBuffer = null;
            engine.remove(webSocket);
            webSocket.onClose(code, null);
        }
    }

    private static final class QueueEntry {

        public final CompletableFuture<Integer> future;

        public final WebSocketPacket packet;

        public QueueEntry(CompletableFuture<Integer> future, WebSocketPacket packet) {
            this.future = future;
            this.packet = packet;
        }

    }

}
