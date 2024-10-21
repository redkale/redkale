/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import org.redkale.convert.ConvertColumn;
import org.redkale.net.http.WebSocketPacket.FrameType;

/**
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public final class WebSocketPacket {

    public static final Object MESSAGE_NIL = new Object();

    public static final WebSocketPacket DEFAULT_PING_PACKET = new WebSocketPacket(FrameType.PING, new byte[0]);

    public enum FrameType {
        SERIES(0x00),
        TEXT(0x01),
        BINARY(0x02),
        CLOSE(0x08),
        PING(0x09),
        PONG(0x0A);

        private final int value;

        private FrameType(int v) {
            this.value = v;
        }

        public int getValue() {
            return value;
        }

        public static FrameType valueOf(int v) {
            switch (v) {
                case 0x00:
                    return SERIES;
                case 0x01:
                    return TEXT;
                case 0x02:
                    return BINARY;
                case 0x08:
                    return CLOSE;
                case 0x09:
                    return PING;
                case 0x0A:
                    return PONG;
                default:
                    return null;
            }
        }
    }

    @ConvertColumn(index = 1)
    protected FrameType type;

    @ConvertColumn(index = 2)
    protected byte[] payload;

    @ConvertColumn(index = 3)
    protected boolean last = true;

    public WebSocketPacket() {}

    public WebSocketPacket(FrameType type, byte[] data) {
        this(type, data, true);
    }

    public WebSocketPacket(FrameType type, byte[] data, boolean last) {
        this.type = type;
        this.payload = data;
        this.last = last;
    }

    public WebSocketPacket(Serializable message, boolean last) {
        boolean bin = message != null && message.getClass() == byte[].class;
        if (bin) {
            this.type = FrameType.BINARY;
            this.payload = (byte[]) message;
        } else {
            this.type = FrameType.TEXT;
            this.payload = String.valueOf(message).getBytes(StandardCharsets.UTF_8);
        }
        this.last = last;
    }

    // 消息编码
    public byte[] encodeToBytes() {
        final byte opcode = (byte) (type.getValue() | 0x80);
        final byte[] content = getPayload();
        final int len = content.length;
        if (len <= 0x7D) { // 125
            byte[] data = new byte[2 + len];
            data[0] = opcode;
            data[1] = (byte) len;
            System.arraycopy(content, 0, data, 2, len);
            return data;
        } else if (len <= 0xFFFF) { // 65535
            byte[] data = new byte[4 + len];
            data[0] = opcode;
            data[1] = (byte) 0x7E; // 126
            data[2] = (byte) (len >> 8 & 0xFF);
            data[3] = (byte) (len & 0xFF);
            System.arraycopy(content, 0, data, 4, len);
            return data;
        } else {
            byte[] data = new byte[10 + len];
            data[0] = opcode;
            data[1] = (byte) 0x7F; // 127
            data[2] = (byte) (len >> 56 & 0xFF);
            data[3] = (byte) (len >> 48 & 0xFF);
            data[4] = (byte) (len >> 40 & 0xFF);
            data[5] = (byte) (len >> 32 & 0xFF);
            data[6] = (byte) (len >> 24 & 0xFF);
            data[7] = (byte) (len >> 16 & 0xFF);
            data[8] = (byte) (len >> 8 & 0xFF);
            data[9] = (byte) (len & 0xFF);
            System.arraycopy(content, 0, data, 10, len);
            return data;
        }
    }

    public byte[] getPayload() {
        return payload;
    }

    public boolean isLast() {
        return last;
    }

    public FrameType getType() {
        return type;
    }

    public void setType(FrameType type) {
        this.type = type;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

    public void setLast(boolean last) {
        this.last = last;
    }

    public String toSimpleString() {
        if (payload == null) {
            return null;
        }
        return type == FrameType.TEXT ? new String(payload, StandardCharsets.UTF_8) : ("bytes(" + payload.length + ")");
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "[type=" + type + ", last=" + last + ", payload=" + toSimpleString()
                + "]";
    }
}
