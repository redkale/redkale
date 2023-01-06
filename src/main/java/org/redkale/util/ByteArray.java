/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.Arrays;

/**
 * 简单的byte[]操作类。
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public final class ByteArray implements ByteTuple {

    private byte[] content;

    private int count;

    public ByteArray() {
        this(1024);
    }

    public ByteArray(int size) {
        content = new byte[Math.max(1, size)];
    }

    public ByteArray(ByteTuple tuple) {
        content = tuple.content();
        count = tuple.length();
    }

    public ByteArray(byte[] bs) {
        content = bs;
        count = 0;
    }

    /**
     * 清空数据,将count置为0,并不清掉byte[]的内容
     *
     * @return ByteArray 是否相同
     */
    public ByteArray clear() {
        this.count = 0;
        return this;
    }

    /**
     * 生成一个OutputStream
     *
     * @return OutputStream
     */
    public OutputStream newOutputStream() {
        return new OutputStream() {

            @Override
            public void write(int b) throws IOException {
                put((byte) b);
            }

            @Override
            public void write(byte b[], int off, int len) throws IOException {
                put(b, off, len);
            }
        };
    }

    /**
     * 比较内容是否相同
     *
     * @param bytes 待比较内容
     *
     * @return 是否相同
     */
    public boolean equal(final byte[] bytes) {
        if (bytes == null) {
            return false;
        }
        int len = count;
        if (len != bytes.length) {
            return false;
        }
        byte[] ba = content;
        for (int i = 0; i < len; i++) {
            if (ba[i] != bytes[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 比较内容是否相同
     *
     * @param bytes  待比较内容
     * @param offset 偏移量
     * @param length 长度
     *
     * @return 是否相同
     */
    public boolean equal(final byte[] bytes, int offset, int length) {
        if (bytes == null) {
            return false;
        }
        if (count != length) {
            return false;
        }
        byte[] ba = content;
        int len = count;
        int off = offset;
        for (int i = 0; i < len; i++) {
            if (ba[i] != bytes[off + i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 比较内容是否相同
     *
     * @param bytes 待比较内容
     *
     * @return 是否相同
     */
    public boolean equal(final ByteArray bytes) {
        if (bytes == null) {
            return false;
        }
        if (count != bytes.count) {
            return false;
        }
        byte[] ba1 = content;
        byte[] ba2 = bytes.content;
        int len = count;
        for (int i = 0; i < len; i++) {
            if (ba1[i] != ba2[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断内容是否为空
     *
     * @return 是否为空
     */
    public boolean isEmpty() {
        return count == 0;
    }

    /**
     * 获取字节长度
     *
     * @return 长度
     */
    @Override
    public int length() {
        return count;
    }

    @Override
    public int offset() {
        return 0;
    }

    /**
     * 获取指定位置的byte值,须确保0 &lt;= index &lt; length
     *
     * @param index 位置
     *
     * @return byte值
     */
    public byte get(int index) {
        return content[index];
    }

    /**
     * 获取指定位置的char值,须确保0 &lt;= offset+2 &lt; length
     *
     * @param offset 位置
     *
     * @return short值
     */
    public char getChar(int offset) {
        return (char) ((0xff00 & (content[offset] << 8)) | (0xff & content[offset + 1]));
    }

    /**
     * 获取指定位置的short值,须确保0 &lt;= offset+2 &lt; length
     *
     * @param offset 位置
     *
     * @return short值
     */
    public int getShort(int offset) {
        return (short) ((0xff00 & (content[offset] << 8)) | (0xff & content[offset + 1]));
    }

    /**
     * 获取指定位置的int值,须确保0 &lt;= offset+4 &lt; length
     *
     * @param offset 位置
     *
     * @return int值
     */
    public int getInt(int offset) {
        return ((content[offset] & 0xff) << 24) | ((content[offset + 1] & 0xff) << 16) | ((content[offset + 2] & 0xff) << 8) | (content[offset + 3] & 0xff);
    }

    /**
     * 获取指定位置的float值,须确保0 &lt;= offset+4 &lt; length
     *
     * @param offset 位置
     *
     * @return float值
     */
    public float getFloat(int offset) {
        return Float.intBitsToFloat(getInt(offset));
    }

    /**
     * 获取指定位置的long值,须确保0 &lt;= offset+8 &lt; length
     *
     * @param offset 位置
     *
     * @return long值
     */
    public long getLong(int offset) {
        return (((long) content[offset] & 0xff) << 56) | (((long) content[offset + 1] & 0xff) << 48) | (((long) content[offset + 2] & 0xff) << 40) | (((long) content[offset + 3] & 0xff) << 32)
            | (((long) content[offset + 4] & 0xff) << 24) | (((long) content[offset + 5] & 0xff) << 16) | (((long) content[offset + 6] & 0xff) << 8) | ((long) content[offset + 7] & 0xff);
    }

    /**
     * 获取指定位置的double值,须确保0 &lt;= offset+8 &lt; length
     *
     * @param offset 位置
     *
     * @return double值
     */
    public double getDouble(int offset) {
        return Double.longBitsToDouble(getLong(offset));
    }

    /**
     * 获取最后一个字节值,调用前须保证count大于0
     *
     * @return byte值
     */
    public byte getLastByte() {
        return content[count - 1];
    }

    /**
     * count减一,调用前须保证count大于0
     *
     */
    public void backCount() {
        count--;
    }

    /**
     * 将buf内容覆盖到本对象内容中
     *
     * @param buf 目标容器
     */
    public void copyTo(byte[] buf) {
        System.arraycopy(this.content, 0, buf, 0, count);
    }

    /**
     * 将ByteBuffer的内容读取到本对象中
     *
     * @param buffer ByteBuffer
     */
    public void put(ByteBuffer buffer) {
        if (buffer == null) {
            return;
        }
        int remain = buffer.remaining();
        if (remain == 0) {
            return;
        }
        int l = this.content.length - count;
        if (remain > l) {
            byte[] ns = new byte[this.content.length + remain];
            if (count > 0) {
                System.arraycopy(content, 0, ns, 0, count);
            }
            this.content = ns;
        }
        buffer.get(content, count, remain);
        count += remain;
    }

    /**
     * 将array的内容引用给本对象
     *
     * @param array ByteArray
     */
    public void directFrom(ByteArray array) {
        if (array != null) {
            this.content = array.content;
            this.count = array.count;
        }
    }

    /**
     * 将content的内容直接给本对象
     *
     * @param content 内容
     * @param count   长度
     */
    public void directFrom(byte[] content, int count) {
        this.content = content;
        this.count = count;
    }

    /**
     * 将本对象的内容引用复制给array
     *
     * @param array ByteArray
     */
    public void directTo(ByteArray array) {
        if (array != null) {
            array.content = this.content;
            array.count = this.count;
        }
    }

    /**
     * 直接获取全部数据, 实际数据需要根据length长度来截取
     *
     * @return byte[]
     */
    @Override
    public byte[] content() {
        return content;
    }

    /**
     * 获取byte[]
     *
     * @return byte[]
     */
    public byte[] getBytes() {
        return Arrays.copyOf(content, count);
    }

    /**
     * 获取byte[]
     *
     * @param offset 偏移位
     * @param length 长度
     *
     * @return byte[]
     */
    public byte[] getBytes(int offset, int length) {
        if (length < 1) {
            return new byte[0];
        }
        byte[] bs = new byte[length];
        System.arraycopy(this.content, offset, bs, 0, length);
        return bs;
    }

    /**
     * 查找指定值第一次出现的位置,没有返回-1
     *
     * @param value 查询值
     *
     * @return 所在位置
     */
    public int find(byte value) {
        return find(0, value);
    }

    /**
     * 从指定的起始位置查询value值出现的位置,没有返回-1
     *
     * @param offset 起始位置
     * @param value  查询值
     *
     * @return 所在位置
     */
    public int find(int offset, char value) {
        return find(offset, (byte) value);
    }

    /**
     * 从指定的起始位置查询value值出现的位置,没有返回-1
     *
     * @param offset 起始位置
     * @param value  查询值
     *
     * @return 所在位置
     */
    public int find(int offset, byte value) {
        return find(offset, -1, value);
    }

    /**
     * 从指定的起始位置和长度查询value值出现的位置,没有返回-1
     *
     * @param offset 起始位置
     * @param limit  长度限制
     * @param value  查询值
     *
     * @return 所在位置
     */
    public int find(int offset, int limit, char value) {
        return find(offset, limit, (byte) value);
    }

    /**
     * 从指定的起始位置和长度查询value值出现的位置,没有返回-1
     *
     * @param offset 起始位置
     * @param limit  长度限制
     * @param value  查询值
     *
     * @return 所在位置
     */
    public int find(int offset, int limit, byte value) {
        byte[] bytes = this.content;
        int end = limit > 0 ? limit : count;
        for (int i = offset; i < end; i++) {
            if (bytes[i] == value) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 移除最后一个字节
     *
     * @return ByteArray
     */
    public ByteArray removeLastByte() {
        if (count > 0) {
            count--;
        }
        return this;
    }

    /**
     * 写入一个char值
     *
     * @param value int值
     *
     * @return ByteArray
     */
    public ByteArray putChar(char value) {
        return this.put((byte) (value >> 8 & 0xFF),
            (byte) (value & 0xFF));
    }

    /**
     * 写入一个char值， content.length 必须不能小于offset+2
     *
     * @param offset 偏移量
     * @param value  char值
     *
     * @return ByteArray
     */
    public ByteArray putChar(int offset, char value) {
        return this.put(offset, (byte) (value >> 8 & 0xFF),
            (byte) (value & 0xFF));
    }

    /**
     * 写入一个short值
     *
     * @param value short值
     *
     * @return ByteArray
     */
    public ByteArray putShort(short value) {
        return this.put((byte) (value >> 8 & 0xFF),
            (byte) (value & 0xFF));
    }

    /**
     * 写入一个short值
     *
     * @param value short值
     *
     * @return ByteArray
     */
    public ByteArray putShort(char value) {
        int v = value;
        return this.put((byte) (v >> 8 & 0xFF),
            (byte) (v & 0xFF));
    }

    /**
     * 写入一个short值
     *
     * @param value short值
     *
     * @return ByteArray
     */
    public ByteArray putShort(int value) {
        return this.put((byte) (value >> 8 & 0xFF),
            (byte) (value & 0xFF));
    }

    /**
     * 写入一个short值
     *
     * @param value short值
     *
     * @return ByteArray
     */
    public ByteArray putUnsignedShort(int value) {
        return this.put((byte) (value >> 8 & 0xFF),
            (byte) (value & 0xFF));
    }

    /**
     * 写入一个无符号short值， content.length 必须不能小于offset+2
     *
     * @param offset 偏移量
     * @param value  short值
     *
     * @return ByteArray
     */
    public ByteArray putUnsignedShort(int offset, int value) {
        return this.put(offset, (byte) (value >> 8 & 0xFF),
            (byte) (value & 0xFF));
    }

    /**
     * 写入一个short值， content.length 必须不能小于offset+2
     *
     * @param offset 偏移量
     * @param value  short值
     *
     * @return ByteArray
     */
    public ByteArray putShort(int offset, short value) {
        return this.put(offset, (byte) (value >> 8 & 0xFF),
            (byte) (value & 0xFF));
    }

    /**
     * 写入一个3字节的int值
     *
     * @param value int值
     *
     * @return ByteArray
     */
    public ByteArray putMedium(int value) {
        return this.put((byte) (value >> 16 & 0xFF),
            (byte) (value >> 8 & 0xFF),
            (byte) (value & 0xFF));
    }

    /**
     * 指定位置写入一个3字节的int值， content.length 必须不能小于offset+3
     *
     * @param offset 偏移量
     * @param value  int值
     *
     * @return ByteArray
     */
    public ByteArray putMedium(int offset, int value) {
        content[offset] = (byte) (value >> 16 & 0xFF);
        content[offset + 1] = (byte) (value >> 8 & 0xFF);
        content[offset + 2] = (byte) (value & 0xFF);
        return this;
    }

    /**
     * 写入一个3字节的int值
     *
     * @param value int值
     *
     * @return ByteArray
     */
    public ByteArray putUnsignedMedium(int value) {
        return this.put((byte) (value >> 16 & 0xFF),
            (byte) (value >> 8 & 0xFF),
            (byte) (value & 0xFF));
    }

    /**
     * 指定位置写入一个3字节的int值， content.length 必须不能小于offset+3
     *
     * @param offset 偏移量
     * @param value  int值
     *
     * @return ByteArray
     */
    public ByteArray putUnsignedMedium(int offset, int value) {
        content[offset] = (byte) (value >> 16 & 0xFF);
        content[offset + 1] = (byte) (value >> 8 & 0xFF);
        content[offset + 2] = (byte) (value & 0xFF);
        return this;
    }

    /**
     * 写入一个int值
     *
     * @param value int值
     *
     * @return ByteArray
     */
    public ByteArray putInt(int value) {
        return this.put((byte) (value >> 24 & 0xFF),
            (byte) (value >> 16 & 0xFF),
            (byte) (value >> 8 & 0xFF),
            (byte) (value & 0xFF));
    }

    /**
     * 写入一个int值
     *
     * @param value int值
     *
     * @return ByteArray
     */
    public ByteArray putUnsignedInt(long value) {
        return this.put((byte) (value >> 24 & 0xFF),
            (byte) (value >> 16 & 0xFF),
            (byte) (value >> 8 & 0xFF),
            (byte) (value & 0xFF));
    }

    /**
     * 指定位置写入一个int值， content.length 必须不能小于offset+4
     *
     * @param offset 偏移量
     * @param value  int值
     *
     * @return ByteArray
     */
    public ByteArray putInt(int offset, int value) {
        content[offset] = (byte) (value >> 24 & 0xFF);
        content[offset + 1] = (byte) (value >> 16 & 0xFF);
        content[offset + 2] = (byte) (value >> 8 & 0xFF);
        content[offset + 3] = (byte) (value & 0xFF);
        return this;
    }

    /**
     * 指定位置写入一个 无符号int值， content.length 必须不能小于offset+4
     *
     * @param offset 偏移量
     * @param value  int值
     *
     * @return ByteArray
     */
    public ByteArray putUnsignedInt(int offset, long value) {
        content[offset] = (byte) (value >> 24 & 0xFF);
        content[offset + 1] = (byte) (value >> 16 & 0xFF);
        content[offset + 2] = (byte) (value >> 8 & 0xFF);
        content[offset + 3] = (byte) (value & 0xFF);
        return this;
    }

    /**
     * 写入一个float值
     *
     * @param value float值
     *
     * @return ByteArray
     */
    public ByteArray putFloat(float value) {
        return this.putInt(Float.floatToIntBits(value));
    }

    /**
     * 指定位置写入一个float值， content.length 必须不能小于offset+4
     *
     * @param offset 偏移量
     * @param value  float值
     *
     * @return ByteArray
     */
    public ByteArray putFloat(int offset, float value) {
        return this.putInt(offset, Float.floatToIntBits(value));
    }

    /**
     * 写入一个long值
     *
     * @param value long值
     *
     * @return ByteArray
     */
    public ByteArray putLong(long value) {
        return this.put((byte) (value >> 56 & 0xFF),
            (byte) (value >> 48 & 0xFF),
            (byte) (value >> 40 & 0xFF),
            (byte) (value >> 32 & 0xFF),
            (byte) (value >> 24 & 0xFF),
            (byte) (value >> 16 & 0xFF),
            (byte) (value >> 8 & 0xFF),
            (byte) (value & 0xFF));
    }

    /**
     * 指定位置写入一个long值， content.length 必须不能小于offset+8
     *
     * @param offset 偏移量
     * @param value  long值
     *
     * @return ByteArray
     */
    public ByteArray putLong(int offset, long value) {
        return this.put(offset, (byte) (value >> 56 & 0xFF),
            (byte) (value >> 48 & 0xFF),
            (byte) (value >> 40 & 0xFF),
            (byte) (value >> 32 & 0xFF),
            (byte) (value >> 24 & 0xFF),
            (byte) (value >> 16 & 0xFF),
            (byte) (value >> 8 & 0xFF),
            (byte) (value & 0xFF));
    }

    /**
     * 写入一个double值
     *
     * @param value double值
     *
     * @return ByteArray
     */
    public ByteArray putDouble(double value) {
        return this.putLong(Double.doubleToLongBits(value));
    }

    /**
     * 指定位置写入一个double值， content.length 必须不能小于offset+8
     *
     * @param offset 偏移量
     * @param value  double值
     *
     * @return ByteArray
     */
    public ByteArray putDouble(int offset, double value) {
        return this.putLong(offset, Double.doubleToLongBits(value));
    }

    public ByteArray putByte(short value) {
        return put((byte) value);
    }

    public ByteArray putByte(char value) {
        return put((byte) value);
    }

    public ByteArray putByte(int value) {
        return put((byte) value);
    }

    /**
     * 写入一个byte值
     *
     * @param value byte值
     *
     * @return ByteArray
     */
    public ByteArray put(byte value) {
        if (count >= content.length - 1) {
            byte[] ns = new byte[content.length + 8];
            System.arraycopy(content, 0, ns, 0, count);
            this.content = ns;
        }
        content[count++] = value;
        return this;
    }

    /**
     * 写入一个byte值， content.length 必须不能小于offset+1
     *
     * @param offset 偏移量
     * @param value  byte值
     *
     * @return ByteArray
     */
    public ByteArray putByte(int offset, byte value) {
        content[offset] = value;
        return this;
    }

    /**
     * 写入一个byte值， content.length 必须不能小于offset+1
     *
     * @param offset 偏移量
     * @param value  byte值
     *
     * @return ByteArray
     */
    public ByteArray putByte(int offset, int value) {
        content[offset] = (byte) value;
        return this;
    }

    /**
     * 写入一组byte值
     *
     * @param values 一组byte值
     *
     * @return ByteArray
     */
    public ByteArray put(byte... values) {
        if (values.length < 1) {
            return this;
        }
        if (count >= content.length - values.length) {
            byte[] ns = new byte[content.length + values.length];
            System.arraycopy(content, 0, ns, 0, count);
            this.content = ns;
        }
        System.arraycopy(values, 0, content, count, values.length);
        count += values.length;
        return this;
    }

    /**
     * 指定位置写入一组byte值， content.length 必须不能小于offset+values.length
     *
     * @param offset 偏移量
     * @param values 一组byte值
     *
     * @return ByteArray
     */
    public ByteArray put(int offset, byte... values) {
        if (values.length < 1) {
            throw new IllegalArgumentException();
        }
        System.arraycopy(values, 0, content, offset, values.length);
        return this;
    }

    /**
     * 写入一组byte值
     *
     * @param values 一组byte值
     * @param offset 偏移量
     * @param length 长度
     *
     * @return ByteArray
     */
    public ByteArray put(byte[] values, int offset, int length) {
        if (count >= content.length - length) {
            byte[] ns = new byte[content.length + Math.max(16, length)];
            System.arraycopy(content, 0, ns, 0, count);
            this.content = ns;
        }
        System.arraycopy(values, offset, content, count, length);
        count += length;
        return this;
    }

    /**
     * 写入一组byte值， content.length 必须不能小于poffset+length
     *
     * @param poffset 偏移量
     * @param values  一组byte值
     * @param offset  偏移量
     * @param length  长度
     *
     * @return ByteArray
     */
    public ByteArray put(int poffset, byte[] values, int offset, int length) {
        System.arraycopy(values, offset, content, poffset, length);
        return this;
    }

    /**
     * 写入ByteArray中的一部分
     *
     * @param array  ByteArray
     * @param offset 偏移量
     * @param length 长度
     *
     * @return ByteArray
     */
    public ByteArray put(ByteArray array, int offset, int length) {
        if (count >= content.length - length) {
            byte[] ns = new byte[content.length + Math.max(16, length)];
            System.arraycopy(content, 0, ns, 0, count);
            this.content = ns;
        }
        System.arraycopy(array.content, offset, content, count, length);
        count += length;
        return this;
    }

    /**
     * 写入ByteBuffer指定长度的数据
     *
     * @param buffer 数据
     * @param len    指定长度
     *
     * @return ByteArray
     */
    public ByteArray put(ByteBuffer buffer, int len) {
        if (len < 1) {
            return this;
        }
        if (count >= content.length - len) {
            byte[] ns = new byte[content.length + len];
            System.arraycopy(content, 0, ns, 0, count);
            this.content = ns;
        }
        buffer.get(content, count, len);
        count += len;
        return this;
    }

    @Override
    public String toString() {
        return new String(content, 0, count);
    }

    /**
     * 按指定字符集转成字符串
     *
     * @param charset 字符集
     *
     * @return 字符串
     */
    public String toString(final Charset charset) {
        return toString(0, count, charset);
    }

    /**
     * 将指定的起始位置和长度按指定字符集转成字符串
     *
     * @param offset  起始位置
     * @param len     长度
     * @param charset 字符集
     *
     * @return 字符串
     */
    public String toString(final int offset, int len, final Charset charset) {
        if (charset == null) {
            return new String(content, offset, len, StandardCharsets.UTF_8);
        }
        return new String(content, offset, len, charset);
    }

    /**
     * 将指定的起始位置按指定字符集转成字符串
     *
     * @param offset  起始位置
     * @param charset 字符集
     *
     * @return 字符串
     */
    public String toString(final int offset, final Charset charset) {
        if (charset == null) {
            return new String(content, offset, count - offset, StandardCharsets.UTF_8);
        }
        return new String(content, offset, count - offset, charset);
    }

    /**
     * 将指定的起始位置和长度按指定字符集转成字符串,并trim
     *
     * @param offset  起始位置
     * @param len     长度
     * @param charset 字符集
     *
     * @return 字符串
     */
//    public String toTrimString(int offset, int len, final Charset charset) {
//        if (len == 0) return "";
//        int st = 0;
//        while (st < len && (content[offset] & 0xff) <= ' ') {
//            offset++;
//            st++;
//        }
//        while (len > 0 && (content[len - 1] & 0xff) <= ' ') len--;
//        if (len == 0) return "";
//        if (charset == null) return new String(content, offset, len - st, StandardCharsets.UTF_8);
//        return new String(content, offset, len - st, charset);
//    }
    /**
     * 将指定的起始位置和长度按指定字符集并转义后转成字符串
     *
     * @param charset 字符集
     *
     * @return 字符串
     */
//    public String toDecodeString(final Charset charset) {
//        return toDecodeString(0, count, charset);
//    }
    /**
     * 将指定的起始位置和长度按指定字符集并转义后转成字符串
     *
     * @param offset  起始位置
     * @param len     长度
     * @param charset 字符集
     *
     * @return 字符串
     */
//    public String toDecodeString(final int offset, int len, final Charset charset) {
//        int start = offset;
//        final int end = offset + len;
//        boolean flag = false; //是否需要转义
//        byte[] bs = content;
//        for (int i = offset; i < end; i++) {
//            if (content[i] == '+' || content[i] == '%') {
//                flag = true;
//                break;
//            }
//        }
//        if (flag) {
//            int index = 0;
//            bs = new byte[len];
//            for (int i = offset; i < end; i++) {
//                switch (content[i]) {
//                    case '+':
//                        bs[index] = ' ';
//                        break;
//                    case '%':
//                        bs[index] = (byte) ((hexBit(content[++i]) * 16 + hexBit(content[++i])));
//                        break;
//                    default:
//                        bs[index] = content[i];
//                        break;
//                }
//                index++;
//            }
//            start = 0;
//            len = index;
//        }
//        if (charset == null) return new String(bs, start, len, StandardCharsets.UTF_8);
//        return new String(bs, start, len, charset);
//    }
//
//    private static int hexBit(byte b) {
//        if ('0' <= b && '9' >= b) return b - '0';
//        if ('a' <= b && 'z' >= b) return b - 'a' + 10;
//        if ('A' <= b && 'Z' >= b) return b - 'A' + 10;
//        return b;
//    }
}
