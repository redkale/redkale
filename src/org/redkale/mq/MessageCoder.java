/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * 将MessageRecord.content内容加解密
 *
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <T> 泛型
 */
public interface MessageCoder<T> {

    //编码
    public byte[] encode(T data);

    //解码
    public T decode(byte[] data);

    public static byte[] getBytes(String value) {
        if (value == null || value.isEmpty()) return MessageRecord.EMPTY_BYTES;
        return value.getBytes(StandardCharsets.UTF_8);
    }

    public static void putLongString(ByteBuffer buffer, String value) {
        if (value == null || value.isEmpty()) {
            buffer.putInt(0);
        } else {
            byte[] bs = value.getBytes(StandardCharsets.UTF_8);
            buffer.putInt(bs.length);
            buffer.put(bs);
        }
    }

    public static String getLongString(ByteBuffer buffer) {
        int len = buffer.getInt();
        if (len == 0) return null;
        byte[] bs = new byte[len];
        buffer.get(bs);
        return new String(bs, StandardCharsets.UTF_8);
    }

    public static void putShortString(ByteBuffer buffer, String value) {
        if (value == null || value.isEmpty()) {
            buffer.putChar((char) 0);
        } else {
            byte[] bs = value.getBytes(StandardCharsets.UTF_8);
            buffer.putChar((char) bs.length);
            buffer.put(bs);
        }
    }

    public static String getShortString(ByteBuffer buffer) {
        int len = buffer.getChar();
        if (len == 0) return null;
        byte[] bs = new byte[len];
        buffer.get(bs);
        return new String(bs, StandardCharsets.UTF_8);
    }
}
