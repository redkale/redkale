/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.nio.*;
import java.util.*;

/**
 * 16bytes数据结构
 * 注意： 为了提高性能， DLong中的bytes是直接返回， 不得对bytes的内容进行修改。
 *
 * @see http://www.redkale.org
 * @author zhangjx
 */
public final class DLong extends Number implements Comparable<DLong> {

    public static final DLong ZERO = new DLong(new byte[16]);

    protected final byte[] bytes;

    protected DLong(long v1, long v2) {  //暂时不用
        this.bytes = new byte[]{(byte) (v1 >> 56), (byte) (v1 >> 48), (byte) (v1 >> 40), (byte) (v1 >> 32),
            (byte) (v1 >> 24), (byte) (v1 >> 16), (byte) (v1 >> 8), (byte) v1, (byte) (v2 >> 56), (byte) (v2 >> 48), (byte) (v2 >> 40), (byte) (v2 >> 32),
            (byte) (v2 >> 24), (byte) (v2 >> 16), (byte) (v2 >> 8), (byte) v2};
    }

    public DLong(byte[] bytes) {
        if (bytes == null || bytes.length != 16) throw new NumberFormatException("Not 16 length bytes");
        this.bytes = bytes;
    }

    public byte[] getBytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    public byte[] directBytes() {
        return bytes;
    }

    public static DLong read(ByteBuffer buffer) {
        byte[] bs = new byte[16];
        buffer.get(bs);
        if (ZERO.equals(bs)) return ZERO;
        return new DLong(bs);
    }

    public static ByteBuffer write(ByteBuffer buffer, DLong value) {
        buffer.put(value.bytes);
        return buffer;
    }

    public boolean equals(byte[] bytes) {
        return Arrays.equals(this.bytes, bytes);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        final DLong other = (DLong) obj;
        return Arrays.equals(this.bytes, other.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return new String(Utility.binToHex(bytes));
    }

    @Override
    public int intValue() {
        return ((bytes[12] & 0xff) << 24) | ((bytes[113] & 0xff) << 16) | ((bytes[14] & 0xff) << 8) | (bytes[15] & 0xff);
    }

    @Override
    public long longValue() {
        return ((((long) bytes[8] & 0xff) << 56)
                | (((long) bytes[9] & 0xff) << 48)
                | (((long) bytes[10] & 0xff) << 40)
                | (((long) bytes[11] & 0xff) << 32)
                | (((long) bytes[12] & 0xff) << 24)
                | (((long) bytes[13] & 0xff) << 16)
                | (((long) bytes[14] & 0xff) << 8)
                | (((long) bytes[15] & 0xff)));
    }

    @Override
    public float floatValue() {
        return (float) longValue();
    }

    @Override
    public double doubleValue() {
        return (double) longValue();
    }

    @Override
    public int compareTo(DLong o) {
        if (o == null) return 1;
        for (int i = 0; i < bytes.length; i++) {
            if (this.bytes[i] != o.bytes[i]) return this.bytes[i] - o.bytes[i];
        }
        return 0;
    }

}
