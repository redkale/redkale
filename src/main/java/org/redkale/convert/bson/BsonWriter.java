/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.bson;

import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.function.Consumer;
import org.redkale.convert.*;
import org.redkale.convert.ext.ByteSimpledCoder;
import org.redkale.util.*;

/**
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class BsonWriter extends Writer implements ByteTuple {

    private static final int DEFAULT_SIZE = Integer.getInteger(
            "redkale.convert.bson.writer.buffer.defsize",
            Integer.getInteger("redkale.convert.writer.buffer.defsize", 1024));

    private byte[] content;

    protected int count;

    @Override
    public byte[] content() {
        return content;
    }

    @Override
    public int offset() {
        return 0;
    }

    @Override
    public int length() {
        return count;
    }

    /**
     * 直接获取全部数据, 实际数据需要根据count长度来截取
     *
     * @return byte[]
     */
    public byte[] directBytes() {
        return content;
    }

    /**
     * 将本对象的内容引用复制给array
     *
     * @param array ByteArray
     */
    public void directTo(ByteArray array) {
        array.directFrom(content, count);
    }

    public void completed(ConvertBytesHandler handler, Consumer<BsonWriter> callback) {
        handler.completed(content, 0, count, callback, this);
    }

    public ByteArray toByteArray() {
        return new ByteArray(this);
    }

    public ByteBuffer[] toBuffers() {
        return new ByteBuffer[] {ByteBuffer.wrap(content, 0, count)};
    }

    protected BsonWriter(byte[] bs) {
        this.content = bs == null ? new byte[0] : bs;
    }

    public BsonWriter() {
        this(DEFAULT_SIZE);
        this.features = BsonFactory.root().getFeatures();
    }

    public BsonWriter(int size) {
        this.content = new byte[size > 128 ? size : 128];
    }

    public BsonWriter(ByteArray array) {
        this.content = array.content();
        this.count = array.length();
    }

    @Override
    public final BsonWriter withFeatures(int features) {
        super.withFeatures(features);
        return this;
    }

    // -----------------------------------------------------------------------
    // -----------------------------------------------------------------------
    /**
     * 扩充指定长度的缓冲区
     *
     * @param len 扩容长度
     * @return 固定0
     */
    protected int expand(int len) {
        int newcount = count + len;
        if (newcount > content.length) {
            byte[] newdata = new byte[Math.max(content.length * 3 / 2, newcount)];
            System.arraycopy(content, 0, newdata, 0, count);
            this.content = newdata;
        }
        return 0;
    }

    public void writeTo(final byte ch) {
        expand(1);
        content[count++] = ch;
    }

    // 类似writeTo(new byte[length])
    public void writePlaceholderTo(final int length) {
        expand(length);
        count += length;
    }

    public final void writeTo(final byte... chs) {
        writeTo(chs, 0, chs.length);
    }

    public void writeTo(final byte[] chs, final int start, final int len) {
        expand(len);
        System.arraycopy(chs, start, content, count, len);
        count += len;
    }

    @Override
    protected boolean recycle() {
        super.recycle();
        this.count = 0;
        this.specificObjectType = null;
        if (this.content != null && this.content.length > DEFAULT_SIZE) {
            this.content = new byte[DEFAULT_SIZE];
        }
        return true;
    }

    public BsonWriter clear() {
        recycle();
        return this;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "[count=" + this.count + "]";
    }

    // ------------------------------------------------------------------------
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
            if (flag) {
                writeArrayMark();
            }
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
        writeTo(
                (byte) (value >> 56),
                (byte) (value >> 48),
                (byte) (value >> 40),
                (byte) (value >> 32),
                (byte) (value >> 24),
                (byte) (value >> 16),
                (byte) (value >> 8),
                (byte) value);
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
        writeStandardString(clazz == null ? "" : clazz);
    }

    @Override
    public final void writeObjectB(Object obj) {
        super.writeObjectB(obj);
        writeStandardString("");
        writeShort(BsonReader.SIGN_OBJECTB);
    }

    @Override
    public final void writeObjectE(Object obj) {
        writeByte(BsonReader.SIGN_NONEXT);
        writeShort(BsonReader.SIGN_OBJECTE);
    }

    @Override
    public final void writeFieldName(EnMember member, String fieldName, Type fieldType, int fieldPos) {
        writeByte(BsonReader.SIGN_HASNEXT);
        writeStandardString(fieldName);
        writeByte(BsonFactory.typeEnum(fieldType));
    }

    /**
     * 对于类的字段名、枚举值这些长度一般不超过255且不会出现双字节字符的字符串采用writeSmallString处理, readSmallString用于读取
     *
     * @param value String值
     */
    @Override
    public final void writeStandardString(String value) {
        if (value.isEmpty()) {
            writeTo((byte) 0);
            return;
        }
        char[] chars = Utility.charArray(value);
        if (chars.length > 255) {
            throw new ConvertException("'" + value + "' have  very long length");
        }
        byte[] bytes = new byte[chars.length + 1];
        bytes[0] = (byte) chars.length;
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] > Byte.MAX_VALUE) {
                throw new ConvertException("'" + value + "'  have double-word");
            }
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
    public final void writeWrapper(StringWrapper value) {
        this.writeString(value == null ? null : value.getValue());
    }

    @Override
    public final void writeNull() {
        writeShort(Reader.SIGN_NULL);
    }

    @Override
    public final void writeArrayB(int size, Encodeable componentEncoder, Object obj) {
        writeInt(size);
        if (componentEncoder != null && componentEncoder != ByteSimpledCoder.instance) {
            writeByte(BsonFactory.typeEnum(componentEncoder.getType()));
        }
    }

    @Override
    public final void writeArrayMark() {
        // do nothing
    }

    @Override
    public final void writeArrayE() {
        // do nothing
    }

    @Override
    public void writeMapB(int size, Encodeable keyEncoder, Encodeable valueEncoder, Object obj) {
        writeInt(size);
        writeByte(BsonFactory.typeEnum(keyEncoder.getType()));
        writeByte(BsonFactory.typeEnum(valueEncoder.getType()));
    }

    @Override
    public final void writeMapMark() {
        // do nothing
    }

    @Override
    public final void writeMapE() {
        // do nothing
    }
}
