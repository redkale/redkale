/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.redkale.util.Utility;

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

    public static byte[] getBytes(byte[] value) {
        if (value == null) return MessageRecord.EMPTY_BYTES;
        return value;
    }

    public static byte[] getBytes(String value) {
        if (value == null || value.isEmpty()) return MessageRecord.EMPTY_BYTES;
        return value.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] getBytes(final Map<String, String> map) {
        if (map == null || map.isEmpty()) return new byte[2];
        final AtomicInteger len = new AtomicInteger(2);
        map.forEach((key, value) -> {
            len.addAndGet(2 + (key == null ? 0 : Utility.encodeUTF8Length(key)));
            len.addAndGet(4 + (value == null ? 0 : Utility.encodeUTF8Length(value)));
        });
        final byte[] bs = new byte[len.get()];
        final ByteBuffer buffer = ByteBuffer.wrap(bs);
        buffer.putChar((char) map.size());
        map.forEach((key, value) -> {
            putShortString(buffer, key);
            putLongString(buffer, value);
        });
        return bs;
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

    public static Map<String, String> getMap(ByteBuffer buffer) {
        int len = buffer.getChar();
        if (len == 0) return null;
        Map<String, String> map = new HashMap<>(len);
        for (int i = 0; i < len; i++) {
            map.put(getShortString(buffer), getLongString(buffer));
        }
        return map;
    }
}
