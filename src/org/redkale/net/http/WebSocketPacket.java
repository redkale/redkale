/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import org.redkale.util.Utility;
import java.io.*;

/**
 *
 * @see http://www.redkale.org
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
}
