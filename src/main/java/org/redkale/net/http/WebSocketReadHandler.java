/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.logging.*;
import org.redkale.convert.Convert;
import org.redkale.net.AsyncIOThread;
import static org.redkale.net.http.WebSocket.*;
import org.redkale.net.http.WebSocketPacket.FrameType;
import org.redkale.util.*;

/** @author zhangjx */
public class WebSocketReadHandler implements CompletionHandler<Integer, ByteBuffer> {

    protected final HttpContext context;

    protected final WebSocket webSocket;

    protected final BiConsumer<WebSocket, Object> restMessageConsumer; // 主要供RestWebSocket使用

    protected final Logger logger;

    protected final List<WebSocketPacket> currPackets = new ArrayList<>();

    protected ByteArray currSeriesMergeMessageBytes;

    protected FrameType currSeriesMergeMessageType;

    protected final ObjectPool<ByteArray> byteArrayPool;

    protected final ByteArray halfFrameBytes;

    protected byte halfFrameOpcode;

    protected byte halfFrameCrcode;

    protected byte[] halfFrameMasks;

    protected int halfFrameStart;

    protected int halfFrameLength = -1;

    protected AsyncIOThread ioReadThread;

    public WebSocketReadHandler(
            HttpContext context,
            WebSocket webSocket,
            ObjectPool<ByteArray> byteArrayPool,
            BiConsumer<WebSocket, Object> messageConsumer) {
        this.context = context;
        this.webSocket = webSocket;
        this.byteArrayPool = byteArrayPool;
        this.restMessageConsumer = messageConsumer;
        this.halfFrameBytes = byteArrayPool.get();
        this.ioReadThread = webSocket._channel.getReadIOThread();
        this.logger = context.getLogger();
    }

    public void startRead() {
        CompletableFuture connectFuture = webSocket.onConnected();
        if (connectFuture == null) {
            webSocket._channel.readInIOThread(this);
        } else {
            connectFuture.whenComplete((r, t) -> {
                webSocket._channel.readInIOThread(this);
            });
        }
    }

    /**
     * 消息解码 <br>
     * 0 1 2 3 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     * +-+-+-+-+-------+-+-------------+-------------------------------+ |F|R|R|R| opcode|M| Payload len | Extended
     * payload length | |I|S|S|S| (4) |A| (7) | (16/64) | |N|V|V|V| |S| | (if payload len==126/127) | | |1|2|3| |K| | |
     * +-+-+-+-+-------+-+-------------+ - - - - - - - - - - - - - - - + | Extended payload length continued, if payload
     * len == 127 | + - - - - - - - - - - - - - - - +-------------------------------+ | |Masking-key, if MASK set to 1 |
     * +-------------------------------+-------------------------------+ | Masking-key (continued) | Payload Data |
     * +-------------------------------- - - - - - - - - - - - - - - - + : Payload Data continued : + - - - - - - - - -
     * - - - - - - - - - - - - - - - - - - - - - - + | Payload Data continued |
     * +-----------------------------------------------------------------------+
     *
     * @param realbuf ByteBuffer
     */
    protected void readDecode(final ByteBuffer realbuf) {
        boolean debug = context.getLogger().isLoggable(Level.FINEST);
        if (debug && realbuf.remaining() > 6) {
            logger.log(Level.FINEST, "read websocket message's length = " + realbuf.remaining());
        }
        if (!realbuf.hasRemaining()) {
            return;
        }
        ByteBuffer buffer = realbuf;
        byte frameOpcode;
        byte frameCrcode;
        byte[] frameMasks;
        int frameLength;
        // System.out.println("realbuf读到的长度: " + realbuf.remaining() + ", halfFrameBytes=" + (halfFrameBytes == null ?
        // -1 : halfFrameBytes.length()));
        if (halfFrameBytes.length() > 0) { // 存在半包
            int remain = realbuf.remaining();
            if (halfFrameLength == -1) {
                int cha = 2 - halfFrameBytes.length();
                if (remain < cha) { // 还是不够2字节
                    halfFrameBytes.put(realbuf);
                    return;
                }
                final byte opcode0 = halfFrameBytes.get(0); // 第一个字节
                final byte crcode0 = halfFrameBytes.get(1); // 第二个字节
                byte lengthCode = crcode0;
                final boolean masked = (lengthCode & 0x80) == 0x80;
                if (masked) {
                    lengthCode ^= 0x80; // mask
                }
                int minLength = ((lengthCode <= 0x7D) ? 0 : (lengthCode == 0x7E ? 2 : 8)) + (masked ? 4 : 0);
                cha = minLength + 2 - halfFrameBytes.length();
                if (remain < cha) { // 还不够读取长度值和mask
                    halfFrameBytes.put(realbuf);
                    return;
                }
                int length;
                if (lengthCode <= 0x7D) { // 125  长度<=125
                    length = lengthCode;
                } else if (lengthCode == 0x7E) { // 0x7E=126  长度:126~65535
                    length = realbuf.getChar();
                } else if (lengthCode == 0x7F) { // 0x7E=127   长度>65535
                    length = (int) realbuf.getLong();
                } else {
                    throw new HttpException("read webSocket packet lengthCode (" + (int) lengthCode + ") error");
                }
                byte[] masks0 = null;
                if (masked) {
                    masks0 = new byte[4];
                    realbuf.get(masks0);
                }
                this.halfFrameOpcode = opcode0;
                this.halfFrameCrcode = crcode0;
                this.halfFrameMasks = masks0;
                this.halfFrameStart = 2 + minLength;
                this.halfFrameLength = length;
            }
            // 此时必然有halfFrameLength
            int bulen = halfFrameLength + halfFrameStart - halfFrameBytes.length(); // 还差多少字节
            if (bulen > remain) { // 不够，继续读取
                halfFrameBytes.put(realbuf);
                return;
            }
            halfFrameBytes.put(realbuf, bulen);
            // 此时halfFrameBytes是完整的frame数据
            buffer = ByteBuffer.wrap(halfFrameBytes.content(), halfFrameStart, halfFrameBytes.length());

            frameOpcode = this.halfFrameOpcode;
            frameCrcode = this.halfFrameCrcode;
            frameMasks = this.halfFrameMasks;
            frameLength = this.halfFrameLength;

            this.halfFrameBytes.clear();
            this.halfFrameLength = -1;
        } else { // 第一次就只有几个字节buffer
            int remain = realbuf.remaining();
            if (remain < 2) {
                this.halfFrameBytes.put(realbuf);
                return;
            }
            final byte opcode0 = realbuf.get(); // 第一个字节
            final byte crcode0 = realbuf.get(); // 第二个字节
            byte lengthCode = crcode0;
            final boolean masked = (lengthCode & 0x80) == 0x80;
            if (masked) {
                lengthCode ^= 0x80; // mask
            }
            int minLength = ((lengthCode <= 0x7D) ? 0 : (lengthCode == 0x7E ? 2 : 8)) + (masked ? 4 : 0);
            if (remain < minLength + 2) { // 还不够读取长度值
                this.halfFrameBytes.put(opcode0, crcode0);
                this.halfFrameBytes.put(realbuf);
                return;
            }
            int length;
            if (lengthCode <= 0x7D) { // 125  长度<=125
                length = lengthCode;
            } else if (lengthCode == 0x7E) { // 0x7E=126  长度:126~65535
                length = realbuf.getChar();
            } else if (lengthCode == 0x7F) { // 0x7E=127   长度>65535
                length = (int) realbuf.getLong();
            } else {
                throw new HttpException("read webSocket packet lengthCode (" + (int) lengthCode + ") error");
            }
            byte[] masks0 = null;
            if (masked) {
                masks0 = new byte[4];
                realbuf.get(masks0);
            }
            int bulen = length + minLength + 2; // 还差多少字节
            if (bulen > remain) { // 不够，继续读取
                this.halfFrameBytes.put(opcode0, crcode0);
                if (lengthCode <= 0x7D) { // 125  长度<=125
                    this.halfFrameBytes.put((byte) length);
                } else if (lengthCode == 0x7E) { // 0x7E=126  长度:126~65535
                    this.halfFrameBytes.putChar((char) length);
                } else if (lengthCode == 0x7F) { // 0x7E=127   长度>65535
                    this.halfFrameBytes.putLong((long) length);
                }
                if (masks0 != null) {
                    this.halfFrameBytes.put(masks0);
                }
                this.halfFrameBytes.put(realbuf);
                this.halfFrameOpcode = opcode0;
                this.halfFrameCrcode = crcode0;
                this.halfFrameMasks = masks0;
                this.halfFrameStart = 2 + minLength;
                this.halfFrameLength = length;
                return;
            }
            frameOpcode = opcode0;
            frameCrcode = crcode0;
            frameMasks = masks0;
            frameLength = length;
        }

        final boolean last = (frameOpcode & 0B1000_0000) != 0;
        final FrameType type = FrameType.valueOf(frameOpcode & 0B0000_1111);
        // 0x00 表示一个后续帧
        // 0x01 表示一个文本帧
        // 0x02 表示一个二进制帧
        // 0x03-07 为以后的非控制帧保留
        // 0x8 表示一个连接关闭
        // 0x9 表示一个ping
        // 0xA 表示一个pong
        // 0x0B-0F 为以后的控制帧保留
        final boolean control = (frameOpcode & 0B0000_1000) != 0; // 是否控制帧
        // this.receiveCompress = !control && webSocket.inflater != null && (opcode & 0B0100_0000) != 0; //rsv1 为 1

        if (type == FrameType.CLOSE) {
            if (debug) {
                logger.log(Level.FINEST, " receive close command from websocket client");
            }
        }
        if (type == null) {
            logger.log(
                    Level.SEVERE,
                    " receive unknown frametype(opcode=" + (frameOpcode & 0B0000_1111) + ") from websocket client");
        }
        final boolean checkrsv = false; // 暂时不校验
        if (checkrsv && (frameOpcode & 0B0111_0000) != 0) {
            if (debug) {
                logger.log(Level.FINE, "rsv1 rsv2 rsv3 must be 0, but not (" + frameOpcode + ")");
            }
            return; // rsv1 rsv2 rsv3 must be 0
        }
        byte[] content = new byte[frameLength];
        if (frameLength > 0) {
            buffer.get(content);
            int mi = 0;
            if (frameMasks != null) {
                for (int i = 0; i < content.length; i++) {
                    content[i] = (byte) (content[i] ^ frameMasks[mi++ % 4]);
                }
            }
        }
        if (!last && (type == FrameType.TEXT || type == FrameType.BINARY)) {
            this.currSeriesMergeMessageBytes = new ByteArray();
            this.currSeriesMergeMessageBytes.put(content);
            this.currSeriesMergeMessageType = type;
        } else if (type == FrameType.SERIES) {
            this.currSeriesMergeMessageBytes.put(content);
        } else if (last && this.currSeriesMergeMessageBytes != null) {
            this.currSeriesMergeMessageBytes.put(content);
            byte[] bs = this.currSeriesMergeMessageBytes.getBytes();
            FrameType t = this.currSeriesMergeMessageType;
            this.currSeriesMergeMessageBytes = null;
            this.currSeriesMergeMessageType = null;
            currPackets.add(new WebSocketPacket(t, bs, last));
        } else {
            currPackets.add(new WebSocketPacket(type, content, last));
        }
        buffer = realbuf;
        while (buffer.hasRemaining()) {
            readDecode(realbuf);
        }
    }

    @Override
    public void completed(Integer count, ByteBuffer readBuffer) {
        boolean debug = context.getLogger().isLoggable(Level.FINEST);
        if (count < 1) {
            if (debug) {
                logger.log(
                        Level.FINEST,
                        "WebSocket(" + webSocket + ") abort on read buffer count, force to close channel, live "
                                + (System.currentTimeMillis() - webSocket.getCreateTime()) / 1000 + " seconds");
            }
            webSocket.kill(CLOSECODE_ILLPACKET, "read buffer count is " + count);
            return;
        }
        try {
            webSocket.lastReadTime = System.currentTimeMillis();
            currPackets.clear();

            readBuffer.flip();
            readDecode(readBuffer);
            readBuffer.clear();
            webSocket._channel.setReadBuffer(readBuffer);
            try {
                // 消息处理
                for (final WebSocketPacket packet : currPackets) {
                    if (packet.type == FrameType.TEXT) {
                        ioReadThread.runWork(() -> {
                            try {
                                Convert convert = webSocket.getTextConvert();
                                if (restMessageConsumer != null && convert != null) { // 主要供RestWebSocket使用
                                    restMessageConsumer.accept(
                                            webSocket,
                                            convert.convertFrom(webSocket._messageRestType, packet.getPayload()));
                                } else {
                                    webSocket.onMessage(
                                            packet.getPayload() == null
                                                    ? null
                                                    : new String(packet.getPayload(), StandardCharsets.UTF_8),
                                            packet.last);
                                }
                            } catch (Throwable e) {
                                logger.log(Level.SEVERE, "WebSocket onTextMessage error (" + packet + ")", e);
                            }
                        });
                    } else if (packet.type == FrameType.BINARY) {
                        ioReadThread.runWork(() -> {
                            try {
                                Convert convert = webSocket.getBinaryConvert();
                                if (restMessageConsumer != null && convert != null) { // 主要供RestWebSocket使用
                                    restMessageConsumer.accept(
                                            webSocket,
                                            convert.convertFrom(webSocket._messageRestType, packet.getPayload()));
                                } else {
                                    webSocket.onMessage(packet.getPayload(), packet.last);
                                }
                            } catch (Throwable e) {
                                logger.log(Level.SEVERE, "WebSocket onBinaryMessage error (" + packet + ")", e);
                            }
                        });
                    } else if (packet.type == FrameType.PING) {
                        ioReadThread.runWork(() -> {
                            try {
                                webSocket.onPing(packet.getPayload());
                            } catch (Exception e) {
                                logger.log(Level.SEVERE, "WebSocket onPing error (" + packet + ")", e);
                            }
                        });
                    } else if (packet.type == FrameType.PONG) {
                        ioReadThread.runWork(() -> {
                            try {
                                // if (debug) logger.log(Level.FINEST, "WebSocket onMessage by PONG FrameType : " +
                                // packet);
                                webSocket.onPong(packet.getPayload());
                            } catch (Exception e) {
                                logger.log(
                                        Level.SEVERE, "WebSocket(" + webSocket + ") onPong error (" + packet + ")", e);
                            }
                        });
                    } else if (packet.type == FrameType.CLOSE) {
                        webSocket.initiateClosed = true;
                        if (debug) {
                            logger.log(
                                    Level.FINEST,
                                    "WebSocket(" + webSocket + ") onMessage by CLOSE FrameType : " + packet);
                        }
                        webSocket.kill(CLOSECODE_CLIENTCLOSE, "received CLOSE frame-type message");
                        return;
                    } else {
                        logger.log(
                                Level.WARNING,
                                "WebSocket(" + webSocket + ") onMessage by unknown FrameType : " + packet);
                        webSocket.kill(CLOSECODE_ILLPACKET, "received unknown frame-type message");
                        return;
                    }
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "WebSocket(" + webSocket + ") onMessage error", t);
            }
            webSocket._channel.read(this);
        } catch (Throwable e) {
            logger.log(Level.WARNING, "WebSocket(" + webSocket + ") onMessage by received error", e);
            webSocket.kill(CLOSECODE_WSEXCEPTION, "websocket-received error");
        }
    }

    @Override
    public void failed(Throwable exc, ByteBuffer attachment2) {
        if (webSocket.initiateClosed) {
            return;
        }
        if (exc != null) {
            if (context.getLogger().isLoggable(Level.FINEST)) {
                context.getLogger()
                        .log(
                                Level.FINEST,
                                "WebSocket(" + webSocket
                                        + ") read WebSocketPacket failed, force to close channel, live "
                                        + (System.currentTimeMillis() - webSocket.getCreateTime()) / 1000 + " seconds",
                                exc);
            }
            webSocket.kill(CLOSECODE_WSEXCEPTION, "read websocket-packet failed");
        } else {
            webSocket.kill(CLOSECODE_WSEXCEPTION, "decode websocket-packet error");
        }
    }
}
