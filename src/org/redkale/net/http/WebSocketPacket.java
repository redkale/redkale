/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import org.redkale.util.Utility;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.function.Supplier;
import java.util.logging.*;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public final class WebSocketPacket {

    public static final WebSocketPacket DEFAULT_PING_PACKET = new WebSocketPacket(FrameType.PING, new byte[0]);

    public static enum FrameType {

        TEXT(0x01), BINARY(0x02), CLOSE(0x08), PING(0x09), PONG(0x0A);

        private final int value;

        private FrameType(int v) {
            this.value = v;
        }

        public int getValue() {
            return value;
        }

        public static FrameType valueOf(int v) {
            switch (v) {
                case 0x01: return TEXT;
                case 0x02: return BINARY;
                case 0x08: return CLOSE;
                case 0x09: return PING;
                case 0x0A: return PONG;
                default: return null;
            }
        }
    }

    protected FrameType type;

    protected String payload;

    protected byte[] bytes;

    protected boolean last = true;

    public WebSocketPacket() {
    }

    public WebSocketPacket(String payload) {
        this(payload, true);
    }

    public WebSocketPacket(Serializable message, boolean fin) {
        boolean bin = message != null && message.getClass() == byte[].class;
        if (bin) {
            this.type = FrameType.BINARY;
            this.bytes = (byte[]) message;
        } else {
            this.type = FrameType.TEXT;
            this.payload = String.valueOf(message);
        }
        this.last = fin;
    }

    public WebSocketPacket(String payload, boolean fin) {
        this.type = FrameType.TEXT;
        this.payload = payload;
        this.last = fin;
    }

    public WebSocketPacket(byte[] data) {
        this(FrameType.BINARY, data, true);
    }

    public WebSocketPacket(byte[] data, boolean fin) {
        this(FrameType.BINARY, data, fin);
    }

    public WebSocketPacket(FrameType type, byte[] data) {
        this(type, data, true);
    }

    public WebSocketPacket(FrameType type, byte[] data, boolean fin) {
        this.type = type;
        if (type == FrameType.TEXT) {
            this.payload = new String(Utility.decodeUTF8(data));
        } else {
            this.bytes = data;
        }
        this.last = fin;
    }

    public byte[] getContent() {
        if (this.type == FrameType.TEXT) return Utility.encodeUTF8(getPayload());
        if (this.bytes == null) return new byte[0];
        return this.bytes;
    }

    public String getPayload() {
        return payload;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public boolean isLast() {
        return last;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "[type=" + type + ", last=" + last + (payload != null ? (", payload=" + payload) : "") + (bytes != null ? (", bytes=[" + bytes.length + ']') : "") + "]";
    }

    /**
     * 消息编码
     *
     * @param supplier Supplier
     *
     * @return ByteBuffer[]
     */
    ByteBuffer[] encode(final Supplier<ByteBuffer> supplier) {
        ByteBuffer buffer = supplier.get();  //确保ByteBuffer的capacity不能小于128

        final byte opcode = (byte) (this.type.getValue() | 0x80);
        final byte[] content = getContent();
        final int len = content.length;
        if (len <= 0x7D) { //125
            buffer.put(opcode);
            buffer.put((byte) len);
            buffer.put(content);
            buffer.flip();
            return new ByteBuffer[]{buffer};
        }
        if (len <= 0xFFFF) { // 65535
            buffer.put(opcode);
            buffer.put((byte) 0x7E); //126
            buffer.putChar((char) len);
        } else {
            buffer.put(opcode);
            buffer.put((byte) 0x7F); //127
            buffer.putInt(len);
        }
        int start = buffer.remaining();
        int pend = len - buffer.remaining();
        if (pend <= 0) {
            buffer.put(content);
            buffer.flip();
            return new ByteBuffer[]{buffer};
        }
        buffer.put(content, 0, buffer.remaining());
        buffer.flip();
        final int capacity = buffer.capacity();
        final ByteBuffer[] buffers = new ByteBuffer[pend / capacity + 1];
        buffers[0] = buffer;
        for (int i = 1; i < buffers.length; i++) {
            ByteBuffer buf = supplier.get();
            buffer.put(content, start, Math.min(pend, capacity));
            buffer.flip();
            buffers[i] = buf;
            start += capacity;
            pend -= capacity;
        }
        return buffers;
    }

    /**
     * 消息解码  <br>
     *
     * 0 1 2 3
     * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     * +-+-+-+-+-------+-+-------------+-------------------------------+
     * |F|R|R|R| opcode|M| Payload len | Extended payload length |
     * |I|S|S|S| (4) |A| (7) | (16/64) |
     * |N|V|V|V| |S| | (if payload len==126/127) |
     * | |1|2|3| |K| | |
     * +-+-+-+-+-------+-+-------------+ - - - - - - - - - - - - - - - +
     * | Extended payload length continued, if payload len == 127 |
     * + - - - - - - - - - - - - - - - +-------------------------------+
     * | |Masking-key, if MASK set to 1 |
     * +-------------------------------+-------------------------------+
     * | Masking-key (continued) | Payload Data |
     * +-------------------------------- - - - - - - - - - - - - - - - +
     * : Payload Data continued :
     * + - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - +
     * | Payload Data continued |
     * +-----------------------------------------------------------------------+
     *
     * @param buffer
     * @param exbuffers
     *
     * @return
     */
    WebSocketPacket decode(final Logger logger, final ByteBuffer buffer, ByteBuffer... exbuffers) {
        final boolean debug = true;
        if (debug) {
            int remain = buffer.remaining();
            if (exbuffers != null) {
                for (ByteBuffer b : exbuffers) {
                    remain += b == null ? 0 : b.remaining();
                }
            }
            logger.log(Level.FINEST, "read websocket message's length = " + remain);
        }
        if (buffer.remaining() < 2) return null;
        byte opcode = buffer.get();
        this.last = (opcode & 0b1000_0000) != 0;
        this.type = FrameType.valueOf(opcode & 0xF);
        if (type == FrameType.CLOSE) {
            if (debug) logger.log(Level.FINEST, " receive close command from websocket client");
            return null;
        }
        final boolean checkrsv = false;//暂时不校验
        if (checkrsv && (opcode & 0b0111_0000) != 0) {
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
        final boolean control = (opcode & 0b0000_1000) != 0; //是否控制帧
        byte lengthCode = buffer.get();
        final boolean masked = (lengthCode & 0x80) == 0x80;
        if (masked) lengthCode ^= 0x80; //mask
        int length;
        if (lengthCode <= 0x7D) { //125
            length = lengthCode;
        } else {
            if (control) {
                if (debug) logger.log(Level.FINE, " receive control command from websocket client");
                return null;
            }
            if (lengthCode == 0x7E) {//0x7E=126
                length = (int) buffer.getChar();
            } else {
                length = buffer.getInt();
            }
        }
        byte[] mask = null;
        if (masked) {
            mask = new byte[4];
            buffer.get(mask);
        }
        final byte[] data = new byte[length];
        if (buffer.remaining() >= length) {
            buffer.get(data);
        } else { //必须有 exbuffers
            int offset = buffer.remaining();
            buffer.get(data, 0, offset);
            for (ByteBuffer b : exbuffers) {
                int r = b.remaining();
                b.get(data, offset, r);
                offset += r;
                if (offset >= length) break;
            }
        }
        if (mask != null) {
            for (int i = 0; i < data.length; i++) {
                data[i] ^= mask[i % 4];
            }
        }
        if (type == FrameType.TEXT) {
            this.payload = new String(Utility.decodeUTF8(data));
        } else {
            this.bytes = data;
        }
        return this;
    }

}
