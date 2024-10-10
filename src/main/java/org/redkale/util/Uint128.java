/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * 16bytes数据结构 注意： 为了提高性能， Uint128中的bytes是直接返回， 不得对bytes的内容进行修改。
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
public final class Uint128 extends Number implements Comparable<Uint128> {

    public static final Uint128 ZERO = new Uint128(new byte[16]);

    final byte[] value;

    //    private Uint128(long v1, long v2) {  //暂时不用
    //        this.value = new byte[]{(byte) (v1 >> 56), (byte) (v1 >> 48), (byte) (v1 >> 40), (byte) (v1 >> 32),
    //            (byte) (v1 >> 24), (byte) (v1 >> 16), (byte) (v1 >> 8), (byte) v1, (byte) (v2 >> 56), (byte) (v2 >>
    // 48), (byte) (v2 >> 40), (byte) (v2 >> 32),
    //            (byte) (v2 >> 24), (byte) (v2 >> 16), (byte) (v2 >> 8), (byte) v2};
    //    }
    private Uint128(byte[] bytes) {
        if (bytes == null || bytes.length != 16) {
            throw new NumberFormatException("Not 16 length bytes");
        }
        this.value = bytes;
    }

    public byte[] getBytes() {
        return Arrays.copyOf(value, value.length);
    }

    public static Uint128 create(byte[] bs) {
        if (bs[15] == 0
                && bs[14] == 0
                && bs[13] == 0
                && bs[12] == 0
                && bs[11] == 0
                && bs[10] == 0
                && bs[9] == 0
                && bs[8] == 0
                && bs[7] == 0
                && bs[6] == 0
                && bs[5] == 0
                && bs[4] == 0
                && bs[3] == 0
                && bs[2] == 0
                && bs[1] == 0
                && bs[0] == 0) {
            return ZERO;
        }
        return new Uint128(bs);
    }

    public static Uint128 read(ByteBuffer buffer) {
        byte[] bs = new byte[16];
        buffer.get(bs);
        if (bs[15] == 0
                && bs[14] == 0
                && bs[13] == 0
                && bs[12] == 0
                && bs[11] == 0
                && bs[10] == 0
                && bs[9] == 0
                && bs[8] == 0
                && bs[7] == 0
                && bs[6] == 0
                && bs[5] == 0
                && bs[4] == 0
                && bs[3] == 0
                && bs[2] == 0
                && bs[1] == 0
                && bs[0] == 0) {
            return ZERO;
        }
        return new Uint128(bs);
    }

    public static ByteBuffer write(ByteBuffer buffer, Uint128 dlong) {
        buffer.put(dlong.value);
        return buffer;
    }

    public boolean equals(byte[] bytes) {
        return Arrays.equals(this.value, bytes);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Uint128 other = (Uint128) obj;
        return Arrays.equals(this.value, other.value);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(value);
    }

    @Override
    public String toString() {
        if (this == ZERO) {
            return "0";
        }
        return Utility.binToHexString(value);
    }

    @Override
    public int intValue() {
        return ((value[12] & 0xff) << 24) | ((value[13] & 0xff) << 16) | ((value[14] & 0xff) << 8) | (value[15] & 0xff);
    }

    @Override
    public long longValue() {
        return ((((long) value[8] & 0xff) << 56)
                | (((long) value[9] & 0xff) << 48)
                | (((long) value[10] & 0xff) << 40)
                | (((long) value[11] & 0xff) << 32)
                | (((long) value[12] & 0xff) << 24)
                | (((long) value[13] & 0xff) << 16)
                | (((long) value[14] & 0xff) << 8)
                | ((long) value[15] & 0xff));
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
    public int compareTo(Uint128 o) {
        if (o == null) {
            return 1;
        }
        for (int i = 0; i < value.length; i++) {
            if (this.value[i] != o.value[i]) {
                return this.value[i] - o.value[i];
            }
        }
        return 0;
    }
}
