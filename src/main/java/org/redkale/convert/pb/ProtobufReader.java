/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.pb;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.redkale.convert.*;

/** @author zhangjx */
public class ProtobufReader extends Reader {

    protected int position = -1;

    protected int initoffset;

    private byte[] content;

    protected int cachetag = Integer.MIN_VALUE;

    protected boolean enumtostring;

    public ProtobufReader() {
        // do nothing
    }

    public ProtobufReader(byte[] bytes) {
        setBytes(bytes, 0, bytes.length);
    }

    public ProtobufReader(byte[] bytes, int start, int len) {
        setBytes(bytes, start, len);
    }

    public ProtobufReader enumtostring(boolean enumtostring) {
        this.enumtostring = enumtostring;
        return this;
    }

    @Override
    public void prepare(byte[] bytes) {
        setBytes(bytes);
    }

    public final void setBytes(byte[] bytes) {
        if (bytes == null) {
            this.position = 0;
            this.initoffset = 0;
        } else {
            setBytes(bytes, 0, bytes.length);
        }
    }

    public final void setBytes(byte[] bytes, int start, int len) {
        if (bytes == null) {
            this.position = 0;
            this.initoffset = 0;
        } else {
            this.content = bytes;
            this.position = start - 1;
            this.initoffset = this.position;
        }
    }

    protected boolean recycle() {
        this.position = -1;
        this.initoffset = -1;
        this.content = null;
        return true;
    }

    public ProtobufReader clear() {
        this.recycle();
        return this;
    }

    public byte[] remainBytes() {
        if (this.position >= this.content.length) {
            return new byte[0];
        }
        return Arrays.copyOfRange(this.content, this.position + 1, this.content.length);
    }

    /** 跳过属性的值 */
    @Override
    @SuppressWarnings("unchecked")
    public final void skipValue() {
        int tag = readTag();
        if (tag == 0) {
            return;
        }
        switch (tag & 0x7) {
            case 0:
                readRawVarint32();
                break;
            case 1:
                readRawLittleEndian64();
                break;
            case 2:
                readByteArray();
                break;
            case 5:
                readRawLittleEndian32();
                break;
        }
    }

    @Override
    public final String readObjectB(final Class clazz) {
        return (this.position + 1) < this.content.length ? "" : null;
    }

    @Override
    public final void readObjectE(final Class clazz) {
        // do nothing
    }

    @Override
    public final int readMapB(DeMember member, byte[] typevals, Decodeable keyDecoder, Decodeable valueDecoder) {
        return Reader.SIGN_NOLENGTH;
    }

    @Override
    public final void readMapE() {
        // do nothing
    }

    /**
     * 判断下一个非空白字符是否为[
     *
     * @param member DeMember
     * @param typevals byte[]
     * @param componentDecoder Decodeable
     * @return SIGN_NOLENGTH 或 SIGN_NULL
     */
    @Override
    public final int readArrayB(DeMember member, byte[] typevals, Decodeable componentDecoder) {
        if (member == null || componentDecoder == null) {
            return Reader.SIGN_NOLENBUTBYTES;
        }
        Type type = componentDecoder.getType();
        if (!(type instanceof Class)) {
            return Reader.SIGN_NOLENBUTBYTES;
        }
        Class clazz = (Class) type;
        if (clazz.isPrimitive()
                || clazz == Boolean.class
                || clazz == Byte.class
                || clazz == Short.class
                || clazz == Character.class
                || clazz == Integer.class
                || clazz == Float.class
                || clazz == Long.class
                || clazz == Double.class
                || clazz == AtomicInteger.class
                || clazz == AtomicLong.class) {
            return Reader.SIGN_NOLENBUTBYTES;
        }
        return Reader.SIGN_NOLENGTH;
    }

    @Override
    public final void readArrayE() {
        // do nothing
    }

    /** 判断下一个非空白字节是否: */
    @Override
    public final void readBlank() {
        // do nothing
    }

    @Override
    public final int position() {
        return this.position;
    }

    @Override
    public final int readMemberContentLength(DeMember member, Decodeable decoder) {
        if (member == null && decoder == null) {
            return -1; // 为byte[]
        }
        if (member != null) {
            if (member.getDecoder() instanceof ProtobufArrayDecoder) {
                ProtobufArrayDecoder pdecoder = (ProtobufArrayDecoder) member.getDecoder();
                if (pdecoder.simple) {
                    return readRawVarint32();
                }
            } else if (member.getDecoder() instanceof ProtobufCollectionDecoder) {
                ProtobufCollectionDecoder pdecoder = (ProtobufCollectionDecoder) member.getDecoder();
                if (pdecoder.simple) {
                    return readRawVarint32();
                }
            } else if (member.getDecoder() instanceof ProtobufStreamDecoder) {
                ProtobufStreamDecoder pdecoder = (ProtobufStreamDecoder) member.getDecoder();
                if (pdecoder.simple) {
                    return readRawVarint32();
                }
            }
            return -1;
        }
        return readRawVarint32(); // readUInt32
    }

    @Override
    public final DeMember readFieldName(final DeMemberInfo memberInfo) {
        int tag = readTag();
        DeMember member = memberInfo.getMemberByTag(tag);
        if (member != null) {
            return member;
        }
        backTag(tag);
        return null;
    }

    // ------------------------------------------------------------
    @Override
    public final boolean readBoolean() {
        return readRawVarint64() != 0;
    }

    @Override
    public final byte readByte() {
        return (byte) readInt();
    }

    @Override
    public final char readChar() {
        return (char) readInt();
    }

    @Override
    public final short readShort() {
        return (short) readInt();
    }

    @Override
    public final int readInt() { // readSInt32
        int n = readRawVarint32();
        return (n >>> 1) ^ -(n & 1);
    }

    @Override
    public final long readLong() { // readSInt64
        long n = readRawVarint64();
        return (n >>> 1) ^ -(n & 1);
    }

    @Override
    public final float readFloat() {
        return Float.intBitsToFloat(readRawLittleEndian32());
    }

    @Override
    public final double readDouble() {
        return Double.longBitsToDouble(readRawLittleEndian64());
    }

    @Override
    public final String readClassName() {
        return "";
    }

    @Override
    public final String readSmallString() {
        return readString();
    }

    @Override
    public final String readString() {
        return new String(readByteArray(), StandardCharsets.UTF_8);
    }

    protected final int readTag() {
        if (cachetag != Integer.MIN_VALUE) {
            int tag = cachetag;
            cachetag = Integer.MIN_VALUE;
            return tag;
        }
        return readRawVarint32();
    }

    protected final void backTag(int tag) {
        this.cachetag = tag;
    }

    protected byte currentByte() {
        return this.content[this.position];
    }

    /**
     * 判断对象是否存在下一个属性或者数组是否存在下一个元素
     *
     * @param startPosition 起始位置
     * @param contentLength 内容大小， 不确定的传-1
     * @return 是否存在
     */
    @Override
    public boolean hasNext(int startPosition, int contentLength) {
        // ("-------------: " + startPosition + ", " + contentLength + ", " + this.position);
        if (startPosition >= 0 && contentLength >= 0) {
            return (this.position) < (startPosition + contentLength);
        }
        return (this.position + 1) < this.content.length;
    }

    @Override
    public byte[] readByteArray() {
        final int size = readRawVarint32();
        byte[] bs = new byte[size];
        System.arraycopy(content, position + 1, bs, 0, size);
        position += size;
        return bs;
    }

    protected int readRawVarint32() { // readUInt32
        fastpath:
        {
            int tempPos = this.position;
            if ((tempPos + 1) == content.length) {
                break fastpath;
            }

            int x;
            if ((x = content[++tempPos]) >= 0) {
                this.position = tempPos;
                return x;
            } else if (content.length - (tempPos + 1) < 9) {
                break fastpath;
            } else if ((x ^= (content[++tempPos] << 7)) < 0) {
                x ^= (~0 << 7);
            } else if ((x ^= (content[++tempPos] << 14)) >= 0) {
                x ^= (~0 << 7) ^ (~0 << 14);
            } else if ((x ^= (content[++tempPos] << 21)) < 0) {
                x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21);
            } else {
                int y = content[++tempPos];
                x ^= y << 28;
                x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21) ^ (~0 << 28);
                if (y < 0
                        && content[++tempPos] < 0
                        && content[++tempPos] < 0
                        && content[++tempPos] < 0
                        && content[++tempPos] < 0
                        && content[++tempPos] < 0) {
                    break fastpath; // Will throw malformedVarint()
                }
            }
            this.position = tempPos;
            return x;
        }
        return (int) readRawVarint64SlowPath();
    }

    protected long readRawVarint64() {
        fastpath:
        {
            int tempPos = this.position;
            if ((tempPos + 1) == content.length) {
                break fastpath;
            }

            long x;
            int y;
            if ((y = content[++tempPos]) >= 0) {
                this.position = tempPos;
                return y;
            } else if (content.length - (tempPos + 1) < 9) {
                break fastpath;
            } else if ((y ^= (content[++tempPos] << 7)) < 0) {
                x = y ^ (~0 << 7);
            } else if ((y ^= (content[++tempPos] << 14)) >= 0) {
                x = y ^ ((~0 << 7) ^ (~0 << 14));
            } else if ((y ^= (content[++tempPos] << 21)) < 0) {
                x = y ^ ((~0 << 7) ^ (~0 << 14) ^ (~0 << 21));
            } else if ((x = y ^ ((long) content[++tempPos] << 28)) >= 0L) {
                x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28);
            } else if ((x ^= ((long) content[++tempPos] << 35)) < 0L) {
                x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35);
            } else if ((x ^= ((long) content[++tempPos] << 42)) >= 0L) {
                x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42);
            } else if ((x ^= ((long) content[++tempPos] << 49)) < 0L) {
                x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42) ^ (~0L << 49);
            } else {
                x ^= ((long) content[++tempPos] << 56);
                x ^= (~0L << 7)
                        ^ (~0L << 14)
                        ^ (~0L << 21)
                        ^ (~0L << 28)
                        ^ (~0L << 35)
                        ^ (~0L << 42)
                        ^ (~0L << 49)
                        ^ (~0L << 56);
                if (x < 0L) {
                    if (content[++tempPos] < 0L) {
                        break fastpath; // Will throw malformedVarint()
                    }
                }
            }
            this.position = tempPos;
            return x;
        }
        return readRawVarint64SlowPath();
    }

    protected long readRawVarint64SlowPath() {
        long result = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            final byte b = content[++this.position];
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
        }
        throw new ConvertException("readRawVarint64SlowPath error");
    }

    protected int readRawLittleEndian32() {
        return ((content[++this.position] & 0xff)
                | ((content[++this.position] & 0xff) << 8)
                | ((content[++this.position] & 0xff) << 16)
                | ((content[++this.position] & 0xff) << 24));
    }

    protected long readRawLittleEndian64() {
        return ((content[++this.position] & 0xffL)
                | ((content[++this.position] & 0xffL) << 8)
                | ((content[++this.position] & 0xffL) << 16)
                | ((content[++this.position] & 0xffL) << 24)
                | ((content[++this.position] & 0xffL) << 32)
                | ((content[++this.position] & 0xffL) << 40)
                | ((content[++this.position] & 0xffL) << 48)
                | ((content[++this.position] & 0xffL) << 56));
    }

    @Override
    public ValueType readType() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
