/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.pb;

import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.redkale.convert.*;
import org.redkale.util.*;

/** @author zhangjx */
public class ProtobufWriter extends Writer implements ByteTuple {

    private static final int DEFAULT_SIZE = Integer.getInteger(
            "redkale.convert.protobuf.writer.buffer.defsize",
            Integer.getInteger("redkale.convert.writer.buffer.defsize", 1024));

    private byte[] content;

    protected int count;

    protected int initOffset;

    protected boolean enumtostring;

    protected ProtobufWriter parent;

    public static ObjectPool<ProtobufWriter> createPool(int max) {
        return ObjectPool.createSafePool(max, (Object... params) -> new ProtobufWriter(), null, t -> t.recycle());
    }

    protected ProtobufWriter(ProtobufWriter parent, int features) {
        this();
        this.parent = parent;
        this.features = features;
        if (parent != null) {
            this.enumtostring = parent.enumtostring;
        }
    }

    protected ProtobufWriter(byte[] bs) {
        this.content = bs;
    }

    @Override
    public ProtobufWriter withFeatures(int features) {
        super.withFeatures(features);
        return this;
    }

    protected ProtobufWriter configFieldFunc(Writer writer) {
        if (writer == null) {
            return this;
        }
        ProtobufWriter out = (ProtobufWriter) writer;
        this.mapFieldFunc = out.mapFieldFunc;
        this.objFieldFunc = out.objFieldFunc;
        this.objExtFunc = out.objExtFunc;
        this.features = out.features;
        this.enumtostring = out.enumtostring;
        return this;
    }

    public ProtobufWriter() {
        this(DEFAULT_SIZE);
    }

    public ProtobufWriter(int size) {
        this.content = new byte[Math.max(size, DEFAULT_SIZE)];
    }

    public ProtobufWriter(ByteArray array) {
        this.content = array.content();
        this.count = array.length();
    }

    @Override
    protected boolean recycle() {
        super.recycle();
        this.count = 0;
        this.initOffset = 0;
        if (this.content.length > DEFAULT_SIZE) {
            this.content = new byte[DEFAULT_SIZE];
        }
        return true;
    }

    @Override
    public byte[] content() {
        return content;
    }

    @Override
    public int offset() {
        return initOffset;
    }

    @Override
    public int length() {
        return count;
    }

    /**
     * 将本对象的内容引用复制给array
     *
     * @param array ByteArray
     */
    public void directTo(ByteArray array) {
        array.directFrom(content, count);
    }

    public ByteBuffer[] toBuffers() {
        return new ByteBuffer[] {ByteBuffer.wrap(content, 0, count)};
    }

    /**
     * 直接获取全部数据, 实际数据需要根据count长度来截取
     *
     * @return byte[]
     */
    public byte[] directBytes() {
        return content;
    }

    public void completed(ConvertBytesHandler handler, Consumer<ProtobufWriter> callback) {
        handler.completed(content, 0, count, callback, this);
    }

    @Override
    public byte[] toArray() {
        if (count == content.length) {
            return content;
        }
        byte[] newdata = new byte[count];
        System.arraycopy(content, 0, newdata, 0, count);
        return newdata;
    }

    public ProtobufWriter enumtostring(boolean enumtostring) {
        this.enumtostring = enumtostring;
        return this;
    }

    protected int expand(int len) {
        int newcount = count + len;
        if (newcount > content.length) {
            byte[] newdata = new byte[Math.max(content.length * 2, newcount)];
            System.arraycopy(content, 0, newdata, 0, count);
            this.content = newdata;
        }
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

    public ProtobufWriter clear() {
        this.count = 0;
        this.initOffset = 0;
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
    public void writeNull() {
        // do nothing
    }

    @Override
    public boolean needWriteClassName() {
        return false;
    }

    @Override
    public void writeClassName(String clazz) {
        // do nothing
    }

    @Override
    public int writeObjectB(Object obj) {
        super.writeObjectB(obj);
        return -1;
    }

    @Override
    public void writeObjectE(Object obj) {
        if (parent != null) {
            parent.writeUInt32(count());
            parent.writeTo(toArray());
        }
    }

    @Override
    public int writeArrayB(int size, Encodeable encoder, Encodeable<Writer, Object> componentEncoder, Object obj) {
        if (obj == null) {
            writeNull();
            return 0;
        } else if (size < 1) {
            // writeUInt32(0);
            return 0;
        } else if (obj instanceof byte[]) {
            int length = ((byte[]) obj).length;
            writeUInt32(length);
            writeTo((byte[]) obj);
            return length;
        } else {
            final Class type = obj.getClass();
            ProtobufWriter tmp = new ProtobufWriter().configFieldFunc(this);
            if (type == boolean[].class) {
                for (boolean item : (boolean[]) obj) {
                    tmp.writeBoolean(item);
                }
            } else if (type == Boolean[].class) {
                for (Boolean item : (Boolean[]) obj) {
                    tmp.writeBoolean(item != null && item);
                }
            } else if (type == short[].class) {
                for (short item : (short[]) obj) {
                    tmp.writeShort(item);
                }
            } else if (type == Short[].class) {
                for (Short item : (Short[]) obj) {
                    tmp.writeShort(item == null ? 0 : item);
                }
            } else if (type == char[].class) {
                for (char item : (char[]) obj) {
                    tmp.writeChar(item);
                }
            } else if (type == Character[].class) {
                for (Character item : (Character[]) obj) {
                    tmp.writeChar(item == null ? 0 : item);
                }
            } else if (type == int[].class) {
                for (int item : (int[]) obj) {
                    tmp.writeInt(item);
                }
            } else if (type == Integer[].class) {
                for (Integer item : (Integer[]) obj) {
                    tmp.writeInt(item == null ? 0 : item);
                }
            } else if (type == float[].class) {
                for (float item : (float[]) obj) {
                    tmp.writeFloat(item);
                }
            } else if (type == Float[].class) {
                for (Float item : (Float[]) obj) {
                    tmp.writeFloat(item == null ? 0F : item);
                }
            } else if (type == long[].class) {
                for (long item : (long[]) obj) {
                    tmp.writeLong(item);
                }
            } else if (type == Long[].class) {
                for (Long item : (Long[]) obj) {
                    tmp.writeLong(item == null ? 0L : item);
                }
            } else if (type == double[].class) {
                for (double item : (double[]) obj) {
                    tmp.writeDouble(item);
                }
            } else if (type == Double[].class) {
                for (Double item : (Double[]) obj) {
                    tmp.writeDouble(item == null ? 0D : item);
                }
            } else if (type == AtomicInteger[].class) {
                for (AtomicInteger item : (AtomicInteger[]) obj) {
                    tmp.writeInt(item == null ? 0 : item.get());
                }
            } else if (type == AtomicLong[].class) {
                for (AtomicLong item : (AtomicLong[]) obj) {
                    tmp.writeLong(item == null ? 0L : item.get());
                }
            } else if (encoder instanceof ProtobufCollectionDecoder) {
                ProtobufCollectionDecoder listEncoder = (ProtobufCollectionDecoder) encoder;
                Type componentType = listEncoder.getComponentType();
                if (listEncoder.simple) {
                    if (componentType == Boolean.class) {
                        for (Boolean item : (Collection<Boolean>) obj) {
                            tmp.writeBoolean(item);
                        }
                    } else if (componentType == Short.class) {
                        for (Short item : (Collection<Short>) obj) {
                            tmp.writeShort(item);
                        }
                    } else if (componentType == Integer.class) {
                        for (Integer item : (Collection<Integer>) obj) {
                            tmp.writeInt(item);
                        }
                    } else if (componentType == Float.class) {
                        for (Float item : (Collection<Float>) obj) {
                            tmp.writeFloat(item);
                        }
                    } else if (componentType == Long.class) {
                        for (Long item : (Collection<Long>) obj) {
                            tmp.writeLong(item);
                        }
                    } else if (componentType == Double.class) {
                        for (Double item : (Collection<Double>) obj) {
                            tmp.writeDouble(item);
                        }
                    } else if (componentType == AtomicInteger.class) {
                        for (AtomicInteger item : (Collection<AtomicInteger>) obj) {
                            tmp.writeInt(item == null ? 0 : item.get());
                        }
                    } else if (componentType == AtomicLong.class) {
                        for (AtomicLong item : (Collection<AtomicLong>) obj) {
                            tmp.writeLong(item == null ? 0L : item.get());
                        }
                    }
                } else {
                    return -1;
                }
            } else if (encoder instanceof ProtobufStreamDecoder) {
                ProtobufStreamDecoder streamEncoder = (ProtobufStreamDecoder) encoder;
                Type componentType = streamEncoder.getComponentType();
                if (streamEncoder.simple) {
                    if (componentType == Boolean.class) {
                        ((Stream<Boolean>) obj).forEach(tmp::writeBoolean);
                    } else if (componentType == Short.class) {
                        ((Stream<Short>) obj).forEach(tmp::writeShort);
                    } else if (componentType == Integer.class) {
                        ((Stream<Integer>) obj).forEach(tmp::writeInt);
                    } else if (componentType == Float.class) {
                        ((Stream<Float>) obj).forEach(tmp::writeFloat);
                    } else if (componentType == Long.class) {
                        ((Stream<Long>) obj).forEach(tmp::writeLong);
                    } else if (componentType == Double.class) {
                        ((Stream<Double>) obj).forEach(tmp::writeDouble);
                    } else if (componentType == AtomicInteger.class) {
                        ((Stream<AtomicInteger>) obj).forEach(item -> tmp.writeInt(item == null ? 0 : item.get()));
                    } else if (componentType == AtomicLong.class) {
                        ((Stream<AtomicLong>) obj).forEach(item -> tmp.writeLong(item == null ? 0L : item.get()));
                    }
                } else {
                    return -1;
                }
            } else {
                return -1;
            }
            int length = tmp.count();
            writeLength(length);
            writeTo(tmp.toArray());
            return length;
        }
    }

    @Override
    public void writeArrayMark() {
        // do nothing
    }

    @Override
    public void writeArrayE() {
        // do nothing
    }

    @Override
    public int writeMapB(
            int size, Encodeable<Writer, Object> keyEncoder, Encodeable<Writer, Object> valueEncoder, Object obj) {
        return -1;
    }

    @Override
    public void writeMapMark() {
        // do nothing
    }

    @Override
    public void writeMapE() {
        // do nothing
    }

    @Override
    public void writeFieldName(EnMember member, String fieldName, Type fieldType, int fieldPos) {
        writeTag(member.getTag());
    }

    @Override
    public void writeObjectField(final EnMember member, Object obj) {
        Object value;
        if (objFieldFunc == null) {
            value = member.getFieldValue(obj);
        } else {
            value = objFieldFunc.apply(member.getAttribute(), obj);
        }
        if (value == null) {
            this.writeFieldName(member);
            writeNull();
            return;
        }
        if (tiny()) {
            if (member.isStringType()) {
                if (((CharSequence) value).length() == 0) {
                    return;
                }
            } else if (member.isBoolType()) {
                if (!((Boolean) value)) {
                    return;
                }
            }
        }
        Type mtype = member.getAttribute().type();
        if (mtype == boolean[].class && ((boolean[]) value).length < 1) {
            return;
        }
        if (mtype == byte[].class && ((byte[]) value).length < 1) {
            return;
        }
        if (mtype == short[].class && ((short[]) value).length < 1) {
            return;
        }
        if (mtype == char[].class && ((char[]) value).length < 1) {
            return;
        }
        if (mtype == int[].class && ((int[]) value).length < 1) {
            return;
        }
        if (mtype == float[].class && ((float[]) value).length < 1) {
            return;
        }
        if (mtype == long[].class && ((long[]) value).length < 1) {
            return;
        }
        if (mtype == double[].class && ((double[]) value).length < 1) {
            return;
        }

        Encodeable encoder = member.getEncoder();
        if (encoder == null) {
            return;
        }
        if (encoder instanceof MapEncoder) {
            if (!((Map) value).isEmpty()) {
                ((MapEncoder) encoder).convertTo(this, member, (Map) value);
            }
        } else if (encoder instanceof ProtobufArrayEncoder) {
            ProtobufArrayEncoder arrayEncoder = (ProtobufArrayEncoder) encoder;
            if (arrayEncoder.simple) {
                if (((Object[]) value).length < 1) {
                    this.writeFieldName(member);
                    ProtobufWriter tmp = new ProtobufWriter().configFieldFunc(this);
                    arrayEncoder.convertTo(tmp, member, (Object[]) value);
                    // int length = tmp.count();
                    // this.writeUInt32(length);
                    this.writeTo(tmp.toArray());
                }
            } else {
                arrayEncoder.convertTo(this, member, (Object[]) value);
            }
        } else if (encoder instanceof ProtobufCollectionEncoder) {
            ProtobufCollectionEncoder collectionEncoder = (ProtobufCollectionEncoder) encoder;
            if (collectionEncoder.simple) {
                if (!((Collection) value).isEmpty()) {
                    this.writeFieldName(member);
                    ProtobufWriter tmp = new ProtobufWriter().configFieldFunc(this);
                    collectionEncoder.convertTo(tmp, member, (Collection) value);
                    int length = tmp.count();
                    this.writeUInt32(length);
                    this.writeTo(tmp.toArray());
                }
            } else {
                collectionEncoder.convertTo(this, member, (Collection) value);
            }
        } else if (encoder instanceof ProtobufStreamEncoder) {
            ProtobufStreamEncoder streamEncoder = (ProtobufStreamEncoder) encoder;
            if (streamEncoder.simple) {
                this.writeFieldName(member);
                ProtobufWriter tmp = new ProtobufWriter().configFieldFunc(this);
                streamEncoder.convertTo(tmp, member, (Stream) value);
                int length = tmp.count();
                this.writeUInt32(length);
                this.writeTo(tmp.toArray());
            } else {
                streamEncoder.convertTo(this, member, (Stream) value);
            }
        } else {
            this.writeFieldName(member);
            encoder.convertTo(this, value);
        }
        this.comma = true;
    }

    @Override
    public void writeByte(byte value) {
        writeInt(value);
    }

    @Override
    public final void writeByteArray(byte[] values) {
        if (values == null) {
            writeNull();
            return;
        }
        if (writeArrayB(values.length, null, null, values) < 0) {
            boolean flag = false;
            for (byte v : values) {
                if (flag) {
                    writeArrayMark();
                }
                writeByte(v);
                flag = true;
            }
        }
        writeArrayE();
    }

    @Override
    public void writeChar(char value) {
        writeInt(value);
    }

    @Override
    public void writeShort(short value) {
        writeInt(value);
    }

    @Override
    public void writeInt(int value) { // writeSInt32
        writeUInt32((value << 1) ^ (value >> 31));
    }

    @Override
    public void writeLong(long value) { // writeSInt64
        writeUInt64((value << 1) ^ (value >> 63));
    }

    @Override
    public void writeFloat(float value) {
        writeFixed32(Float.floatToRawIntBits(value));
    }

    @Override
    public void writeDouble(double value) {
        writeFixed64(Double.doubleToRawLongBits(value));
    }

    @Override
    public void writeSmallString(String value) {
        writeString(value);
    }

    @Override
    public void writeString(String value) {
        byte[] bs = Utility.isLatin1(value) ? Utility.latin1ByteArray(value) : Utility.encodeUTF8(value);
        writeLength(bs.length);
        writeTo(bs);
    }

    @Override
    public void writeWrapper(StringWrapper value) {
        if (value != null) {
            writeString(value.getValue());
        }
    }

    public static byte[] uint32(int value) {
        byte[] bs = new byte[8];
        int pos = 0;
        while (true) {
            if ((value & ~0x7F) == 0) {
                bs[pos++] = ((byte) value);
                return pos == bs.length ? bs : Arrays.copyOf(bs, pos);
            } else {
                bs[pos++] = ((byte) ((value & 0x7F) | 0x80));
                value >>>= 7;
            }
        }
    }

    protected void writeTag(int tag) {
        if (tag < 128) {
            writeTo((byte) tag);
        } else {
            writeUInt32(tag);
        }
    }

    protected void writeLength(int value) {
        if (value < 128) {
            writeTo((byte) value);
        } else {
            writeUInt32(value);
        }
    }

    protected void writeUInt32(int value) {
        while (true) {
            if ((value & ~0x7F) == 0) {
                writeTo((byte) value);
                return;
            } else {
                writeTo((byte) ((value & 0x7F) | 0x80));
                value >>>= 7;
            }
        }
    }

    protected void writeUInt64(long value) {
        while (true) {
            if ((value & ~0x7FL) == 0) {
                writeTo((byte) value);
                return;
            } else {
                writeTo((byte) (((int) value & 0x7F) | 0x80));
                value >>>= 7;
            }
        }
    }

    protected void writeFixed32(int value) {
        writeTo((byte) (value & 0xFF), (byte) ((value >> 8) & 0xFF), (byte) ((value >> 16) & 0xFF), (byte)
                ((value >> 24) & 0xFF));
    }

    protected void writeFixed64(long value) {
        writeTo(
                (byte) ((int) (value) & 0xFF),
                (byte) ((int) (value >> 8) & 0xFF),
                (byte) ((int) (value >> 16) & 0xFF),
                (byte) ((int) (value >> 24) & 0xFF),
                (byte) ((int) (value >> 32) & 0xFF),
                (byte) ((int) (value >> 40) & 0xFF),
                (byte) ((int) (value >> 48) & 0xFF),
                (byte) ((int) (value >> 56) & 0xFF));
    }
}
