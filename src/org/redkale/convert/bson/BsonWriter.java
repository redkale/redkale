/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.bson;

import java.nio.ByteBuffer;
import org.redkale.convert.*;
import org.redkale.convert.ext.ByteSimpledCoder;
import org.redkale.util.*;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class BsonWriter extends Writer {

    private static final int defaultSize = Integer.getInteger("convert.bson.writer.buffer.defsize", 1024);

    private byte[] content;

    protected int count;

    protected boolean tiny;

    public static ObjectPool<BsonWriter> createPool(int max) {
        return new ObjectPool<>(max, (Object... params) -> new BsonWriter(), null, (t) -> t.recycle());
    }

    public byte[] toArray() {
        if (count == content.length) return content;
        byte[] newdata = new byte[count];
        System.arraycopy(content, 0, newdata, 0, count);
        return newdata;
    }

    public ByteBuffer[] toBuffers() {
        return new ByteBuffer[]{ByteBuffer.wrap(content, 0, count)};
    }

    protected BsonWriter(byte[] bs) {
        this.content = bs;
    }

    public BsonWriter() {
        this(defaultSize);
    }

    public BsonWriter(int size) {
        this.content = new byte[size > 128 ? size : 128];
    }

    @Override
    public final boolean tiny() {
        return tiny;
    }

    public BsonWriter tiny(boolean tiny) {
        this.tiny = tiny;
        return this;
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    /**
     * 扩充指定长度的缓冲区
     *
     * @param len 扩容长度
     *
     * @return 固定0
     */
    protected int expand(int len) {
        int newcount = count + len;
        if (newcount <= content.length) return 0;
        byte[] newdata = new byte[Math.max(content.length * 3 / 2, newcount)];
        System.arraycopy(content, 0, newdata, 0, count);
        this.content = newdata;
        return 0;
    }

    public void writeTo(final byte ch) {
        expand(1);
        content[count++] = ch;
    }

    public final void writeTo(final byte... chs) {
        writeTo(chs, 0, chs.length);
    }

    public void writeTo(final byte[] chs, final int start, final int len) {
        expand(len);
        System.arraycopy(chs, start, content, count, len);
        count += len;
    }

    protected boolean recycle() {
        this.count = 0;
        this.specify = null;
        if (this.content.length > defaultSize) {
            this.content = new byte[defaultSize];
        }
        return true;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "[count=" + this.count + "]";
    }

    //------------------------------------------------------------------------
    public final int count() {
        return this.count;
    }

    @Override
    public final void writeBoolean(boolean value) {
        writeTo(value ? (byte) 1 : (byte) 0);
    }

    @Override
    public final void writeByte(byte value) {
        writeTo(value);
    }

    @Override
    public final void writeByteArray(byte[] values) {
        if (values == null) {
            writeNull();
            return;
        }
        writeArrayB(values.length, null, values);
        boolean flag = false;
        for (byte v : values) {
            if (flag) writeArrayMark();
            writeByte(v);
            flag = true;
        }
        writeArrayE();
    }

    @Override
    public final void writeChar(final char value) {
        writeTo((byte) ((value & 0xFF00) >> 8), (byte) (value & 0xFF));
    }

    @Override
    public final void writeShort(short value) {
        writeTo((byte) (value >> 8), (byte) value);
    }

    @Override
    public final void writeInt(int value) {
        writeTo((byte) (value >> 24), (byte) (value >> 16), (byte) (value >> 8), (byte) value);
    }

    @Override
    public final void writeLong(long value) {
        writeTo((byte) (value >> 56), (byte) (value >> 48), (byte) (value >> 40), (byte) (value >> 32),
            (byte) (value >> 24), (byte) (value >> 16), (byte) (value >> 8), (byte) value);
    }

    @Override
    public final void writeFloat(float value) {
        writeInt(Float.floatToIntBits(value));
    }

    @Override
    public final void writeDouble(double value) {
        writeLong(Double.doubleToLongBits(value));
    }

    @Override
    public final boolean needWriteClassName() {
        return true;
    }

    @Override
    public final void writeClassName(String clazz) {
        writeSmallString(clazz == null ? "" : clazz);
    }

    @Override
    public final int writeObjectB(Object obj) {
        super.writeObjectB(obj);
        writeSmallString("");
        writeShort(BsonReader.SIGN_OBJECTB);
        return -1;
    }

    @Override
    public final void writeObjectE(Object obj) {
        writeByte(BsonReader.SIGN_NONEXT);
        writeShort(BsonReader.SIGN_OBJECTE);
    }

    @Override
    public final void writeFieldName(EnMember member) {
        Attribute attribute = member.getAttribute();
        writeByte(BsonReader.SIGN_HASNEXT);
        writeSmallString(attribute.field());
        writeByte(BsonFactory.typeEnum(attribute.type()));
    }

    /**
     * 对于类的字段名、枚举值这些长度一般不超过255且不会出现双字节字符的字符串采用writeSmallString处理, readSmallString用于读取
     *
     * @param value String值
     */
    @Override
    public final void writeSmallString(String value) {
        if (value.isEmpty()) {
            writeTo((byte) 0);
            return;
        }
        char[] chars = Utility.charArray(value);
        if (chars.length > 255) throw new ConvertException("'" + value + "' have  very long length");
        byte[] bytes = new byte[chars.length + 1];
        bytes[0] = (byte) chars.length;
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] > Byte.MAX_VALUE) throw new ConvertException("'" + value + "'  have double-word");
            bytes[i + 1] = (byte) chars[i];
        }
        writeTo(bytes);
    }

    @Override
    public final void writeString(String value) {
        if (value == null) {
            writeInt(Reader.SIGN_NULL);
            return;
        } else if (value.isEmpty()) {
            writeInt(0);
            return;
        }
        byte[] bytes = Utility.encodeUTF8(value);
        writeInt(bytes.length);
        writeTo(bytes);
    }

    @Override
    public final void writeWrapper(StringConvertWrapper value) {
        this.writeString(value == null ? null : value.getValue());
    }

    @Override
    public final void writeNull() {
        writeShort(Reader.SIGN_NULL);
    }

    @Override
    public final int writeArrayB(int size, Encodeable<Writer, Object> componentEncoder, Object obj) {
        writeInt(size);
        if (componentEncoder != null && componentEncoder != ByteSimpledCoder.instance) {
            writeByte(BsonFactory.typeEnum(componentEncoder.getType()));
        }
        return -1;
    }

    @Override
    public final void writeArrayMark() {
    }

    @Override
    public final void writeArrayE() {
    }

    @Override
    public int writeMapB(int size, Encodeable<Writer, Object> keyEncoder, Encodeable<Writer, Object> valueEncoder, Object obj) {
        writeInt(size);
        writeByte(BsonFactory.typeEnum(keyEncoder.getType()));
        writeByte(BsonFactory.typeEnum(valueEncoder.getType()));
        return -1;
    }

    @Override
    public final void writeMapMark() {
    }

    @Override
    public final void writeMapE() {
    }

}
