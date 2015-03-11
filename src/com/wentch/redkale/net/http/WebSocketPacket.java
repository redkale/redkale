/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.http;

import com.wentch.redkale.util.Utility;

/**
 *
 * @author zhangjx
 */
public final class WebSocketPacket {

    public static enum PacketType {

        TEXT(0x01), BINARY(0x02), CLOSE(0x08), PING(0x09), PONG(0x0A);

        private final int value;

        private PacketType(int v) {
            this.value = v;
        }

        public int getValue() {
            return value;
        }

        public static PacketType valueOf(int v) {
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

    protected PacketType type;

    protected String payload;

    protected byte[] bytes;

    protected boolean last = true;

    public WebSocketPacket() {
    }

    public WebSocketPacket(String payload) {
        this(payload, true);
    }

    public WebSocketPacket(String payload, boolean fin) {
        this.type = PacketType.TEXT;
        this.payload = payload;
        this.last = fin;
    }

    public WebSocketPacket(byte[] data) {
        this(PacketType.BINARY, data, true);
    }

    public WebSocketPacket(byte[] data, boolean fin) {
        this(PacketType.BINARY, data, fin);
    }

    public WebSocketPacket(PacketType type, byte[] data) {
        this(type, data, true);
    }

    public WebSocketPacket(PacketType type, byte[] data, boolean fin) {
        this.type = type;
        if (type == PacketType.TEXT) {
            this.payload = new String(Utility.decodeUTF8(data));
        } else {
            this.bytes = data;
        }
        this.last = fin;
    }

    public byte[] getContent() {
        if (this.type == PacketType.TEXT) return Utility.encodeUTF8(getPayload());
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

}
