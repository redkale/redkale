/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.io.*;
import java.nio.charset.StandardCharsets;
import org.redkale.net.http.WebSocketPacket.FrameType;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public final class WebSocketPacket {

    public static final Object MESSAGE_NIL = new Object();

    static final WebSocketPacket NONE = new WebSocketPacket();

    public static final WebSocketPacket DEFAULT_PING_PACKET = new WebSocketPacket(FrameType.PING, new byte[0]);

    public static enum MessageType {
        STRING, BYTES, OBJECT;
    }

    public static enum FrameType {

        SERIES(0x00), TEXT(0x01), BINARY(0x02), CLOSE(0x08), PING(0x09), PONG(0x0A);

        private final int value;

        private FrameType(int v) {
            this.value = v;
        }

        public int getValue() {
            return value;
        }

        public static FrameType valueOf(int v) {
            switch (v) {
                case 0x00: return SERIES;
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

    protected byte[] payload;

    protected boolean last = true;

    public WebSocketPacket() {
    }

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
        if (payload == null) return null;
        return type == FrameType.TEXT ? new String(payload, StandardCharsets.UTF_8) : ("bytes(" + payload.length + ")");
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "[type=" + type + ", last=" + last + ", payload=" + toSimpleString() + "]";
    }

}
