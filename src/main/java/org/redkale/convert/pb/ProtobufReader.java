/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.pb;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.redkale.convert.*;
import org.redkale.util.Creator;

/** @author zhangjx */
public class ProtobufReader extends Reader {

    protected int position = -1;

    protected int initoffset;

    private byte[] content;

    protected int cacheTag = Integer.MIN_VALUE;

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
            default:
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
        if (ProtobufFactory.isNoLenBytesType(type)) {
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
        return content[++this.position] != 0;
    }

    public boolean[] readBools() {
        int size = readRawVarint32();
        boolean[] data = new boolean[size];
        for (int i = 0; i < size; i++) {
            data[i] = readBoolean();
        }
        return data;
    }

    public Collection<Boolean> readBools(Creator<? extends Collection> creator) {
        int size = readRawVarint32();
        Collection<Boolean> data = creator.create();
        for (int i = 0; i < size; i++) {
            data.add(readBoolean());
        }
        return data;
    }

    @Override
    public final byte readByte() {
        return (byte) readInt();
    }

    public byte[] readBytes() {
        return readByteArray();
    }

    public Collection<Byte> readBytes(Creator<? extends Collection> creator) {
        Collection<Byte> data = creator.create();
        for (byte b : readByteArray()) {
            data.add(b);
        }
        return data;
    }

    @Override
    public final char readChar() {
        return (char) readInt();
    }

    public char[] readChars() {
        int len = readRawVarint32();
        List<Integer> list = new ArrayList<>(len);
        while (len > 0) {
            int val = readChar();
            list.add(val);
            len -= ProtobufFactory.computeSInt32SizeNoTag(val);
        }
        char[] rs = new char[list.size()];
        for (int i = 0; i < rs.length; i++) {
            rs[i] = (char) list.get(i).intValue();
        }
        return rs;
    }

    public Collection<Character> readChars(Creator<? extends Collection> creator) {
        Collection<Character> data = creator.create();
        int len = readRawVarint32();
        while (len > 0) {
            char val = readChar();
            data.add(val);
            len -= ProtobufFactory.computeSInt32SizeNoTag(val);
        }
        return data;
    }

    @Override
    public final short readShort() {
        return (short) readInt();
    }

    public short[] readShorts() {
        int len = readRawVarint32();
        List<Short> list = new ArrayList<>(len);
        while (len > 0) {
            short val = readShort();
            list.add(val);
            len -= ProtobufFactory.computeSInt32SizeNoTag(val);
        }
        short[] rs = new short[list.size()];
        for (int i = 0; i < rs.length; i++) {
            rs[i] = list.get(i);
        }
        return rs;
    }

    public Collection<Short> readShorts(Creator<? extends Collection> creator) {
        Collection<Short> data = creator.create();
        int len = readRawVarint32();
        while (len > 0) {
            short val = readShort();
            data.add(val);
            len -= ProtobufFactory.computeSInt32SizeNoTag(val);
        }
        return data;
    }

    @Override
    public final int readInt() { // readSInt32
        int n = readRawVarint32();
        return (n >>> 1) ^ -(n & 1);
    }

    public int[] readInts() {
        int len = readRawVarint32();
        List<Integer> list = new ArrayList<>(len);
        while (len > 0) {
            int val = readInt();
            list.add(val);
            len -= ProtobufFactory.computeSInt32SizeNoTag(val);
        }
        int[] rs = new int[list.size()];
        for (int i = 0; i < rs.length; i++) {
            rs[i] = list.get(i);
        }
        return rs;
    }

    public Collection<Integer> readInts(Creator<? extends Collection> creator) {
        Collection<Integer> data = creator.create();
        int len = readRawVarint32();
        while (len > 0) {
            int val = readInt();
            data.add(val);
            len -= ProtobufFactory.computeSInt32SizeNoTag(val);
        }
        return data;
    }

    public AtomicInteger[] readAtomicIntegers() {
        int len = readRawVarint32();
        List<AtomicInteger> list = new ArrayList<>(len);
        while (len > 0) {
            int val = readInt();
            list.add(new AtomicInteger(val));
            len -= ProtobufFactory.computeSInt32SizeNoTag(val);
        }
        return list.toArray(new AtomicInteger[list.size()]);
    }

    public Collection<AtomicInteger> readAtomicIntegers(Creator<? extends Collection> creator) {
        Collection<AtomicInteger> data = creator.create();
        int len = readRawVarint32();
        while (len > 0) {
            int val = readInt();
            data.add(new AtomicInteger(val));
            len -= ProtobufFactory.computeSInt32SizeNoTag(val);
        }
        return data;
    }

    @Override
    public final float readFloat() {
        return Float.intBitsToFloat(readRawLittleEndian32());
    }

    public float[] readFloats() {
        int len = readRawVarint32();
        float[] rs = new float[len / 4];
        for (int i = 0; i < rs.length; i++) {
            rs[i] = readFloat();
        }
        return rs;
    }

    public Collection<Float> readFloats(Creator<? extends Collection> creator) {
        Collection<Float> data = creator.create();
        int len = readRawVarint32() / 4;
        for (int i = 0; i < len; i++) {
            data.add(readFloat());
        }
        return data;
    }

    @Override
    public final long readLong() { // readSInt64
        long n = readRawVarint64();
        return (n >>> 1) ^ -(n & 1);
    }

    public long[] readLongs() {
        int len = readRawVarint32();
        List<Long> list = new ArrayList<>(len);
        while (len > 0) {
            long val = readLong();
            list.add(val);
            len -= ProtobufFactory.computeSInt64SizeNoTag(val);
        }
        long[] rs = new long[list.size()];
        for (int i = 0; i < rs.length; i++) {
            rs[i] = list.get(i);
        }
        return rs;
    }

    public Collection<Long> readLongs(Creator<? extends Collection> creator) {
        Collection<Long> data = creator.create();
        int len = readRawVarint32();
        while (len > 0) {
            long val = readLong();
            data.add(val);
            len -= ProtobufFactory.computeSInt64SizeNoTag(val);
        }
        return data;
    }

    public AtomicLong[] readAtomicLongs() {
        int len = readRawVarint32();
        List<AtomicLong> list = new ArrayList<>(len);
        while (len > 0) {
            long val = readInt();
            list.add(new AtomicLong(val));
            len -= ProtobufFactory.computeSInt64SizeNoTag(val);
        }
        return list.toArray(new AtomicLong[list.size()]);
    }

    public Collection<AtomicLong> readAtomicLongs(Creator<? extends Collection> creator) {
        Collection<AtomicLong> data = creator.create();
        int len = readRawVarint32();
        while (len > 0) {
            long val = readLong();
            data.add(new AtomicLong(val));
            len -= ProtobufFactory.computeSInt64SizeNoTag(val);
        }
        return data;
    }

    @Override
    public final double readDouble() {
        return Double.longBitsToDouble(readRawLittleEndian64());
    }

    public double[] readDoubles() {
        int len = readRawVarint32();
        double[] rs = new double[len / 8];
        for (int i = 0; i < rs.length; i++) {
            rs[i] = readDouble();
        }
        return rs;
    }

    public Collection<Double> readDoubles(Creator<? extends Collection> creator) {
        Collection<Double> data = creator.create();
        int len = readRawVarint32() / 8;
        for (int i = 0; i < len; i++) {
            data.add(readDouble());
        }
        return data;
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
        if (cacheTag != Integer.MIN_VALUE) {
            int tag = cacheTag;
            cacheTag = Integer.MIN_VALUE;
            return tag;
        }
        return readRawVarint32();
    }

    protected final void backTag(int tag) {
        this.cacheTag = tag;
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
