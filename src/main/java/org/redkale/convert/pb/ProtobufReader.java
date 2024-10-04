/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.pb;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.redkale.convert.*;
import org.redkale.util.Creator;

/** @author zhangjx */
public class ProtobufReader extends Reader {

    protected static final Creator LIS_CREATOR = Creator.create(List.class);

    protected int position = -1;

    protected int limit = -1;

    protected Integer cacheTag;

    private byte[] content;

    public ProtobufReader() {
        // do nothing
    }

    public ProtobufReader(byte[] bytes) {
        setBytes(bytes, 0, bytes.length);
    }

    public ProtobufReader(byte[] bytes, int start, int len) {
        setBytes(bytes, start, len);
    }

    @Override
    public void prepare(byte[] bytes) {
        setBytes(bytes);
    }

    public final void setBytes(byte[] bytes) {
        if (bytes == null) {
            this.position = 0;
            this.limit = 0;
        } else {
            setBytes(bytes, 0, bytes.length);
        }
    }

    public final void setBytes(byte[] bytes, int start, int len) {
        if (bytes == null) {
            this.position = 0;
            this.limit = 0;
        } else {
            this.content = bytes;
            this.position = start - 1;
            this.limit = start + len;
        }
    }

    public void limit(int limit) {
        this.limit = limit;
    }

    public int limit() {
        return this.limit;
    }

    protected boolean recycle() {
        this.position = -1;
        this.limit = -1;
        this.content = null;
        this.cacheTag = null;
        return true;
    }

    public ProtobufReader clear() {
        this.recycle();
        return this;
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
            case 0: // boolean/byte/char/short/int/long
                readRawVarint32();
                break;
            case 1: // double
                readRawLittleEndian64();
                break;
            case 2: // byte[]
                readByteArray();
                break;
            case 5: // float
                readRawLittleEndian32();
                break;
            default:
                break;
        }
    }

    @Override
    public final String readObjectB(final Class clazz) {
        return hasNext() ? "" : null;
    }

    @Override
    public final void readObjectE(final Class clazz) {
        // do nothing
    }

    @Override
    public final int readMapB(Decodeable keyDecoder, Decodeable valueDecoder) {
        return Reader.SIGN_VARIABLE;
    }

    @Override
    public final void readMapE() {
        // do nothing
    }

    /**
     * 判断下一个非空白字符是否为[
     *
     * @param componentDecoder Decodeable
     * @return SIGN_VARIABLE 或 SIGN_NULL
     */
    @Override
    public final int readArrayB(Decodeable componentDecoder) {
        return Reader.SIGN_VARIABLE;
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
    public final DeMember readField(final DeMemberInfo memberInfo) {
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
    public boolean readBoolean() {
        return content[++this.position] == 1;
    }

    public final boolean[] readBools() {
        int size = readRawVarint32();
        boolean[] data = new boolean[size];
        for (int i = 0; i < size; i++) {
            data[i] = readBoolean();
        }
        return data;
    }

    public final Collection<Boolean> readBools(Creator<? extends Collection> creator) {
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

    public final byte[] readBytes() {
        return readByteArray();
    }

    public final Collection<Byte> readBytes(Creator<? extends Collection> creator) {
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

    public final char[] readChars() {
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

    public final Collection<Character> readChars(Creator<? extends Collection> creator) {
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

    public final short[] readShorts() {
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

    public final Collection<Short> readShorts(Creator<? extends Collection> creator) {
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

    public final int[] readInts() {
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

    public final Collection<Integer> readInts(Creator<? extends Collection> creator) {
        Collection<Integer> data = creator.create();
        int len = readRawVarint32();
        while (len > 0) {
            int val = readInt();
            data.add(val);
            len -= ProtobufFactory.computeSInt32SizeNoTag(val);
        }
        return data;
    }

    @Override
    public final float readFloat() {
        return Float.intBitsToFloat(readRawLittleEndian32());
    }

    public final float[] readFloats() {
        int len = readRawVarint32();
        float[] rs = new float[len / 4];
        for (int i = 0; i < rs.length; i++) {
            rs[i] = readFloat();
        }
        return rs;
    }

    public final Collection<Float> readFloats(Creator<? extends Collection> creator) {
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

    public final long[] readLongs() {
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

    public final Collection<Long> readLongs(Creator<? extends Collection> creator) {
        Collection<Long> data = creator.create();
        int len = readRawVarint32();
        while (len > 0) {
            long val = readLong();
            data.add(val);
            len -= ProtobufFactory.computeSInt64SizeNoTag(val);
        }
        return data;
    }

    public final String[] readStrings(int tag) {
        Collection<String> data = readStrings(tag, LIS_CREATOR);
        return data.toArray(new String[data.size()]);
    }

    public final Collection<String> readStrings(int tag, Creator<? extends Collection> creator) {
        Collection<String> data = creator.create();
        while (true) {
            data.add(readString());
            if (!readNextTag(tag)) {
                break;
            }
        }
        return data;
    }

    @Override
    public final double readDouble() {
        return Double.longBitsToDouble(readRawLittleEndian64());
    }

    public final double[] readDoubles() {
        int size = readRawVarint32();
        double[] rs = new double[size / 8];
        for (int i = 0; i < rs.length; i++) {
            rs[i] = readDouble();
        }
        return rs;
    }

    public final Collection<Double> readDoubles(Creator<? extends Collection> creator) {
        Collection<Double> data = creator.create();
        int size = readRawVarint32() / 8;
        for (int i = 0; i < size; i++) {
            data.add(readDouble());
        }
        return data;
    }

    @Override
    public final String readClassName() {
        return "";
    }

    @Override
    public final String readStandardString() {
        return readString();
    }

    @Override
    public String readString() {
        final int size = readRawVarint32();
        String val = new String(content, position + 1, size, StandardCharsets.UTF_8);
        position += size;
        return val;
    }

    public final boolean readNextTag(DeMember member) {
        return readNextTag(member.getTag());
    }

    public final boolean readNextTag(int memberTag) {
        if (!hasNext()) {
            return false;
        }
        int tag = readTag();
        if (tag != memberTag) { // 元素结束
            backTag(tag);
            return false;
        }
        return true;
    }

    protected final int readTag() {
        if (cacheTag != null) {
            int tag = cacheTag;
            cacheTag = null;
            return tag;
        }
        return readRawVarint32();
    }

    protected final void backTag(int tag) {
        this.cacheTag = tag;
    }

    @Override
    public final ValueType readType() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean hasNext() {
        return (this.position + 1) < this.limit;
    }

    @Override
    public byte[] readByteArray() {
        final int size = readRawVarint32();
        byte[] bs = new byte[size];
        System.arraycopy(content, position + 1, bs, 0, size);
        position += size;
        return bs;
    }

    // 通常用于尾部解析
    public byte[] remainBytes() {
        if (this.position >= this.limit) {
            return new byte[0];
        }
        return Arrays.copyOfRange(this.content, this.position + 1, this.limit);
    }

    protected int readRawVarint32() { // readUInt32
        byte[] data = content;
        int curr = this.position;
        byte b = data[++curr];
        if (b >= 0) {
            this.position = curr;
            return b;
        }
        int result = b & 0x7f;
        if ((b = data[++curr]) >= 0) {
            result |= b << 7;
        } else {
            result |= (b & 0x7f) << 7;
            if ((b = data[++curr]) >= 0) {
                result |= b << 14;
            } else {
                result |= (b & 0x7f) << 14;
                if ((b = data[++curr]) >= 0) {
                    result |= b << 21;
                } else {
                    result |= (b & 0x7f) << 21;
                    result |= (b = data[++curr]) << 28;
                    if (b < 0) {
                        // Discard upper 32 bits.
                        for (int i = 0; i < 5; i++) {
                            if (data[++curr] >= 0) {
                                this.position = curr;
                                return result;
                            }
                        }
                        throw new ConvertException("readRawVarint32 error");
                    }
                }
            }
        }
        this.position = curr;
        return result;
    }

    protected long readRawVarint64() {
        byte[] data = content;
        int curr = this.position;
        int shift = 0;
        long result = 0;
        while (shift < 64) {
            final byte b = data[++curr];
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                this.position = curr;
                return result;
            }
            shift += 7;
        }
        throw new ConvertException("readRawVarint64 error");
    }

    protected long readRawVarint64SlowPath() {
        long result = 0;
        byte[] data = content;
        int curr = this.position;
        for (int shift = 0; shift < 64; shift += 7) {
            final byte b = data[++curr];
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                this.position = curr;
                return result;
            }
        }
        throw new ConvertException("readRawVarint64SlowPath error");
    }

    protected int readRawLittleEndian32() { // float
        return ((content[++this.position] & 0xff)
                | ((content[++this.position] & 0xff) << 8)
                | ((content[++this.position] & 0xff) << 16)
                | ((content[++this.position] & 0xff) << 24));
    }

    protected long readRawLittleEndian64() { // double
        return ((content[++this.position] & 0xffL)
                | ((content[++this.position] & 0xffL) << 8)
                | ((content[++this.position] & 0xffL) << 16)
                | ((content[++this.position] & 0xffL) << 24)
                | ((content[++this.position] & 0xffL) << 32)
                | ((content[++this.position] & 0xffL) << 40)
                | ((content[++this.position] & 0xffL) << 48)
                | ((content[++this.position] & 0xffL) << 56));
    }
}
