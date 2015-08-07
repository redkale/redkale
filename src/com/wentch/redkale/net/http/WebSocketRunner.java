/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.http;

import com.wentch.redkale.net.AsyncConnection;
import com.wentch.redkale.net.Context;
import com.wentch.redkale.net.http.WebSocketPacket.PacketType;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.*;

/**
 *
 * @author zhangjx
 */
public class WebSocketRunner implements Runnable {

    private final WebSocketEngine engine;

    private final AsyncConnection channel;

    private final WebSocket webSocket;

    protected final Context context;

    private ByteBuffer readBuffer;

    private ByteBuffer writeBuffer;

    protected boolean closed = false;

    private AtomicBoolean writing = new AtomicBoolean();

    private final Coder coder = new Coder();

    private final BlockingQueue<byte[]> queue = new ArrayBlockingQueue(1024);

    private final boolean wsbinary;

    public WebSocketRunner(Context context, WebSocket webSocket, AsyncConnection channel, final boolean wsbinary) {
        this.context = context;
        this.engine = webSocket.engine;
        this.webSocket = webSocket;
        this.channel = channel;
        this.wsbinary = wsbinary;
        webSocket.runner = this;
        this.coder.logger = context.getLogger();
        this.coder.debugable = context.getLogger().isLoggable(Level.FINEST);
        this.readBuffer = context.pollBuffer();
        this.writeBuffer = context.pollBuffer();
    }

    @Override
    public void run() {
        final boolean debug = this.coder.debugable;
        try {
            if (webSocket.node != null) webSocket.node.connect(webSocket.groupid, webSocket.engine.getEngineid());
            webSocket.onConnected();
            channel.setReadTimeoutSecond(300); //读取超时5分钟
            if (channel.isOpen()) {
                if (wsbinary) {
                    webSocket.onRead(channel);
                    return;
                }
                channel.read(readBuffer, null, new CompletionHandler<Integer, Void>() {

                    private ByteBuffer recentExBuffer;

                    //当接收的数据流长度大于ByteBuffer长度时， 则需要额外的ByteBuffer 辅助;
                    private final List<ByteBuffer> readBuffers = new ArrayList<>();

                    @Override
                    public void completed(Integer count, Void attachment1) {
                        if (count < 1 && readBuffers.isEmpty()) {
                            if (debug) context.getLogger().log(Level.FINEST, "WebSocketRunner abort on read buffer count, force to close channel");
                            closeRunner();
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
                        try {
                            ByteBuffer[] exBuffers = null;
                            if (!readBuffers.isEmpty()) {
                                exBuffers = readBuffers.toArray(new ByteBuffer[readBuffers.size()]);
                                readBuffers.clear();
                                recentExBuffer = null;
                                for (ByteBuffer b : exBuffers) {
                                    b.flip();
                                }
                            }
                            WebSocketPacket packet = coder.decode(readBuffer, exBuffers);
                            if (exBuffers != null) {
                                for (ByteBuffer b : exBuffers) {
                                    context.offerBuffer(b);
                                }
                            }
                            if (packet == null) {
                                if (debug) context.getLogger().log(Level.FINEST, "WebSocketRunner abort on decode WebSocketPacket, force to close channel");
                                failed(null, attachment1);
                                return;
                            }
                            if (readBuffer != null) {
                                readBuffer.clear();
                                channel.read(readBuffer, null, this);
                            }
                            webSocket.group.setRecentWebSocket(webSocket);
                            if (packet.type == PacketType.TEXT) {
                                webSocket.onMessage(packet.getPayload());
                            } else if (packet.type == PacketType.BINARY) {
                                webSocket.onMessage(packet.getBytes());
                            }
                        } catch (Exception e) {
                            closeRunner();
                            if (debug) context.getLogger().log(Level.FINEST, "WebSocketRunner abort on read WebSocketPacket, force to close channel", e);
                        }
                    }

                    @Override
                    public void failed(Throwable exc, Void attachment2) {
                        closeRunner();
                        if (exc != null) {
                            context.getLogger().log(Level.FINEST, "WebSocketRunner read WebSocketPacket failed, force to close channel", exc);
                        }
                    }
                });
            } else {
                context.getLogger().log(Level.FINEST, "WebSocketRunner abort by AsyncConnection closed");
                closeRunner();
            }
        } catch (Exception e) {
            context.getLogger().log(Level.FINEST, "WebSocketRunner abort on read bytes from channel, force to close channel", e);
            closeRunner();
        }
    }

    public void sendMessage(WebSocketPacket packet) {
        if (packet == null || closed) return;

        final boolean debug = this.coder.debugable;
        //System.out.println("推送消息");
        final byte[] bytes = coder.encode(packet);
        if (debug) context.getLogger().log(Level.FINEST, "send web socket message's length = " + bytes.length);
        if (writing.getAndSet(true)) {
            queue.add(bytes);
            return;
        }
        if (writeBuffer == null) return;
        ByteBuffer sendBuffer = null;
        if (bytes.length <= writeBuffer.capacity()) {
            writeBuffer.clear();
            writeBuffer.put(bytes);
            writeBuffer.flip();
            sendBuffer = writeBuffer;
        } else {
            sendBuffer = ByteBuffer.wrap(bytes);
        }
        try {
            channel.write(sendBuffer, sendBuffer, new CompletionHandler<Integer, ByteBuffer>() {

                @Override
                public void completed(Integer result, ByteBuffer attachment) {
                    if (attachment == null || closed) return;
                    try {
                        if (attachment.hasRemaining()) {
                            if (debug) context.getLogger().log(Level.FINEST, "WebSocketRunner write completed reemaining: " + attachment.remaining());
                            channel.write(attachment, attachment, this);
                            return;
                        }
                        byte[] bs = queue.poll();
                        if (bs != null && writeBuffer != null) {
                            ByteBuffer sendBuffer;
                            if (bs.length <= writeBuffer.capacity()) {
                                writeBuffer.clear();
                                writeBuffer.put(bs);
                                writeBuffer.flip();
                                sendBuffer = writeBuffer;
                            } else {
                                sendBuffer = ByteBuffer.wrap(bs);
                            }
                            channel.write(sendBuffer, sendBuffer, this);
                            return;
                        }
                    } catch (NullPointerException e) {
                    } catch (Exception e) {
                        context.getLogger().log(Level.WARNING, "WebSocket sendMessage abort on rewrite, force to close channel", e);
                        closeRunner();
                    }
                    writing.set(false);
                }

                @Override
                public void failed(Throwable exc, ByteBuffer attachment) {
                    writing.set(false);
                    closeRunner();
                    if (exc != null) {
                        context.getLogger().log(Level.FINE, "WebSocket sendMessage on CompletionHandler failed, force to close channel", exc);
                    }
                }
            });
        } catch (Exception t) {
            writing.set(false);
            context.getLogger().log(Level.FINE, "WebSocket sendMessage abort, force to close channel", t);
        }
    }

    public void closeRunner() {
        if (closed) return;
        closed = true;
        try {
            channel.close();
        } catch (Throwable t) {
        }
        context.offerBuffer(readBuffer);
        context.offerBuffer(writeBuffer);
        readBuffer = null;
        writeBuffer = null;
        engine.remove(webSocket);
        if (webSocket.node != null) {
            WebSocketGroup group = webSocket.getWebSocketGroup();
            if (group == null || group.isEmpty()) webSocket.node.disconnect(webSocket.groupid, webSocket.engine.getEngineid());
        }
        webSocket.onClose(0, null);
    }

    private static final class Masker {

        public static final int MASK_SIZE = 4;

        private ByteBuffer buffer;

        private ByteBuffer[] exbuffers;

        private byte[] mask;

        private int index = 0;

        public Masker(ByteBuffer buffer, ByteBuffer... exbuffers) {
            this.buffer = buffer;
            this.exbuffers = exbuffers == null || exbuffers.length == 0 ? null : exbuffers;
        }

        public Masker() {
            generateMask();
        }

        public int remaining() {
            int r = buffer.remaining();
            if (exbuffers != null) {
                for (ByteBuffer b : exbuffers) {
                    r += b.remaining();
                }
            }
            return r;
        }

        public byte get() {
            return buffer.get();
        }

        public byte[] get(final int size) {
            byte[] bytes = new byte[size];
            if (buffer.remaining() >= size) {
                buffer.get(bytes);
            } else { //必须有 exbuffers
                int offset = buffer.remaining();
                buffer.get(bytes, 0, buffer.remaining());
                for (ByteBuffer b : exbuffers) {
                    b.get(bytes, offset, b.remaining());
                    offset += b.remaining();
                }
            }
            return bytes;
        }

        public byte unmask() {
            final byte b = get();
            return mask == null ? b : (byte) (b ^ mask[index++ % MASK_SIZE]);
        }

        public byte[] unmask(int count) {
            byte[] bytes = get(count);
            if (mask != null) {
                for (int i = 0; i < bytes.length; i++) {
                    bytes[i] ^= mask[index++ % MASK_SIZE];
                }
            }

            return bytes;
        }

        public void generateMask() {
            mask = new byte[MASK_SIZE];
            new SecureRandom().nextBytes(mask);
        }

        public void mask(byte[] bytes, int location, byte b) {
            bytes[location] = mask == null ? b : (byte) (b ^ mask[index++ % MASK_SIZE]);
        }

        public void mask(byte[] target, int location, byte[] bytes) {
            if (bytes != null && target != null) {
                for (int i = 0; i < bytes.length; i++) {
                    target[location + i] = mask == null
                            ? bytes[i]
                            : (byte) (bytes[i] ^ mask[index++ % MASK_SIZE]);
                }
            }
        }

        public byte[] maskAndPrepend(byte[] packet) {
            byte[] masked = new byte[packet.length + MASK_SIZE];
            System.arraycopy(getMask(), 0, masked, 0, MASK_SIZE);
            mask(masked, MASK_SIZE, packet);
            return masked;
        }

        public void setBuffer(ByteBuffer buffer) {
            this.buffer = buffer;
        }

        public byte[] getMask() {
            return mask;
        }

        public void readMask() {
            mask = get(MASK_SIZE);
        }
    }

    private static final class Coder {

        protected byte inFragmentedType;

        protected byte outFragmentedType;

        protected final boolean maskData = false;

        protected boolean processingFragment;

        private boolean debugable;

        private Logger logger;

        public WebSocketPacket decode(final ByteBuffer buffer, ByteBuffer... exbuffers) {
            final boolean debug = this.debugable;
            if (debug) {
                int remain = buffer.remaining();
                if (exbuffers != null) {
                    for (ByteBuffer b : exbuffers) {
                        remain += b == null ? 0 : b.remaining();
                    }
                }
                logger.log(Level.FINEST, "read web socket message's length = " + remain);
            }
            if (buffer.remaining() < 2) return null;
            byte opcode = buffer.get();
            final boolean last = (opcode & 0b1000000) != 0;
            if (false && (opcode & 0b01110000) != 0) {  //暂时不校验
                if (debug) logger.log(Level.FINE, "rsv1 rsv2 rsv3 must be 0, but not (" + opcode + ")");
                return null; //rsv1 rsv2 rsv3 must be 0     
            }
            //0x00 表示一个后续帧 
            //0x01 表示一个文本帧 
            //0x02 表示一个二进制帧 
            //0x03-07 为以后的非控制帧保留
            //0x8 表示一个连接关闭
            //0x9 表示一个ping
            //0xA 表示一个pong
            //0x0B-0F 为以后的控制帧保留
            final boolean control = (opcode & 0x08) == 0x08; //是否控制帧
            //final boolean continuation = opcode == 0;
            PacketType type = PacketType.valueOf(opcode & 0xf);
            if (type == PacketType.CLOSE) {
                if (debug) logger.log(Level.FINEST, " receive close command from websocket client");
                return null;
            }
            byte lengthCode = buffer.get();
            final Masker masker = new Masker(buffer, exbuffers);
            final boolean masked = (lengthCode & 0x80) == 0x80;
            if (masked) lengthCode ^= 0x80; //mask
            int length;
            if (lengthCode <= 125) {
                length = lengthCode;
            } else {
                if (control) {
                    if (debug) logger.log(Level.FINE, " receive control command from websocket client");
                    return null;
                }

                final int lengthBytes = lengthCode == 126 ? 2 : 8;
                if (buffer.remaining() < lengthBytes) {
                    if (debug) logger.log(Level.FINE, " read illegal message length from websocket, expect " + lengthBytes + " but " + buffer.remaining());
                    return null;
                }
                length = toInt(masker.unmask(lengthBytes));
            }
            if (masked) {
                if (buffer.remaining() < Masker.MASK_SIZE) {
                    if (debug) logger.log(Level.FINE, " read illegal masker length from websocket, expect " + Masker.MASK_SIZE + " but " + buffer.remaining());
                    return null;
                }
                masker.readMask();
            }
            if (masker.remaining() < length) {
                if (debug) logger.log(Level.FINE, " read illegal remaining length from websocket, expect " + length + " but " + masker.remaining());
                return null;
            }
            final byte[] data = masker.unmask(length);
            if (data.length != length) {
                if (debug) logger.log(Level.FINE, " read illegal unmask length from websocket, expect " + length + " but " + data.length);
                return null;
            }
            return new WebSocketPacket(type, data, last);
        }

        public byte[] encode(WebSocketPacket frame) {
            byte opcode = (byte) (frame.type.getValue() | 0x80);
            final byte[] bytes = frame.getContent();
            final byte[] lengthBytes = encodeLength(bytes.length);

            int length = 1 + lengthBytes.length + bytes.length + (maskData ? Masker.MASK_SIZE : 0);
            int payloadStart = 1 + lengthBytes.length + (maskData ? Masker.MASK_SIZE : 0);
            final byte[] packet = new byte[length];
            packet[0] = opcode;
            System.arraycopy(lengthBytes, 0, packet, 1, lengthBytes.length);
            if (maskData) {
                Masker masker = new Masker();
                packet[1] |= 0x80;
                masker.mask(packet, payloadStart, bytes);
                System.arraycopy(masker.getMask(), 0, packet, payloadStart - Masker.MASK_SIZE, Masker.MASK_SIZE);
            } else {
                System.arraycopy(bytes, 0, packet, payloadStart, bytes.length);
            }
            return packet;
        }

        private static byte[] encodeLength(final long length) {
            byte[] lengthBytes;
            if (length <= 125) {
                lengthBytes = new byte[1];
                lengthBytes[0] = (byte) length;
            } else {
                byte[] b = toArray(length);
                if (length <= 0xFFFF) {
                    lengthBytes = new byte[3];
                    lengthBytes[0] = 126;
                    System.arraycopy(b, 6, lengthBytes, 1, 2);
                } else {
                    lengthBytes = new byte[9];
                    lengthBytes[0] = 127;
                    System.arraycopy(b, 0, lengthBytes, 1, 8);
                }
            }
            return lengthBytes;
        }

        private static byte[] toArray(long length) {
            long value = length;
            byte[] b = new byte[8];
            for (int i = 7; i >= 0 && value > 0; i--) {
                b[i] = (byte) (value & 0xFF);
                value >>= 8;
            }
            return b;
        }

        private static int toInt(byte[] bytes) {
            int value = 0;
            for (int i = 0; i < bytes.length; i++) {
                value <<= 8;
                value ^= (int) bytes[i] & 0xFF;
            }
            return value;
        }

    }

}
