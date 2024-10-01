/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.pb;

import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.*;
import java.util.stream.Stream;
import org.redkale.annotation.ClassDepends;
import org.redkale.annotation.Nullable;
import org.redkale.convert.*;
import org.redkale.util.*;

/** @author zhangjx */
public class ProtobufWriter extends Writer implements ByteTuple {

    private static final int DEFAULT_SIZE = Integer.getInteger(
            "redkale.convert.protobuf.writer.buffer.defsize",
            Integer.getInteger("redkale.convert.writer.buffer.defsize", 1024));

    private static final int CHILD_SIZE = 32;
    protected static final byte[] EMPTY_BYTES = new byte[0];

    protected static final int TENTHOUSAND_MAX = 10001;

    protected static final byte[][] TENTHOUSAND_UINT_BYTES = new byte[TENTHOUSAND_MAX][];
    protected static final byte[][] TENTHOUSAND_UINT_BYTES2 = new byte[TENTHOUSAND_MAX][];

    protected static final byte[][] TENTHOUSAND_SINT32_BYTES = new byte[TENTHOUSAND_MAX][];
    protected static final byte[][] TENTHOUSAND_SINT32_BYTES2 = new byte[TENTHOUSAND_MAX][];

    protected static final byte[][] TENTHOUSAND_SINT64_BYTES = new byte[TENTHOUSAND_MAX][];
    protected static final byte[][] TENTHOUSAND_SINT64_BYTES2 = new byte[TENTHOUSAND_MAX][];

    protected static final byte[][] TENTHOUSAND_FIXED32_BYTES = new byte[TENTHOUSAND_MAX][];
    protected static final byte[][] TENTHOUSAND_FIXED32_BYTES2 = new byte[TENTHOUSAND_MAX][];

    protected static final byte[][] TENTHOUSAND_FIXED64_BYTES = new byte[TENTHOUSAND_MAX][];
    protected static final byte[][] TENTHOUSAND_FIXED64_BYTES2 = new byte[TENTHOUSAND_MAX][];

    static {
        for (int i = 0; i < TENTHOUSAND_UINT_BYTES.length; i++) {
            TENTHOUSAND_UINT_BYTES[i] = uint32(i);
            TENTHOUSAND_UINT_BYTES2[i] = uint32(-i);

            TENTHOUSAND_SINT32_BYTES[i] = sint32(i);
            TENTHOUSAND_SINT32_BYTES2[i] = sint32(-i);

            TENTHOUSAND_SINT64_BYTES[i] = sint64(i);
            TENTHOUSAND_SINT64_BYTES2[i] = sint64(-i);

            TENTHOUSAND_FIXED32_BYTES[i] = fixed32(i);
            TENTHOUSAND_FIXED32_BYTES2[i] = fixed32(-i);

            TENTHOUSAND_FIXED64_BYTES[i] = fixed64(i);
            TENTHOUSAND_FIXED64_BYTES2[i] = fixed64(-i);
        }
    }

    protected int count;

    protected boolean enumtostring;

    protected ProtobufWriter parent;

    protected ProtobufWriter child;

    // 链表结构
    protected ProtobufWriter delegate;

    private byte[] content;

    private ArrayDeque<ProtobufWriter> pool;

    protected ProtobufWriter(byte[] bs) {
        this.content = bs;
    }

    private ProtobufWriter(byte[] bs, int count) {
        this.content = bs;
        this.count = count;
    }

    public ProtobufWriter() {
        this(DEFAULT_SIZE);
    }

    public ProtobufWriter(int size) {
        this.content = new byte[Math.max(size, DEFAULT_SIZE)];
    }

    public ProtobufWriter(ByteTuple tuple) {
        this.content = tuple.content();
        this.count = tuple.length();
    }

    @Override
    public final ProtobufWriter withFeatures(int features) {
        super.withFeatures(features);
        return this;
    }

    protected final ProtobufWriter configWrite() {
        return this;
    }

    protected final ProtobufWriter configFieldFunc(ProtobufWriter out) {
        this.mapFieldFunc = out.mapFieldFunc;
        this.objFieldFunc = out.objFieldFunc;
        this.objExtFunc = out.objExtFunc;
        this.features = out.features;
        this.enumtostring = out.enumtostring;
        return this;
    }

    protected final BiFunction mapFieldFunc() {
        return mapFieldFunc;
    }

    protected Function<Object, ConvertField[]> objExtFunc() {
        return objExtFunc;
    }

    @Override
    protected boolean recycle() {
        super.recycle();
        if (this.delegate != null && this.pool != null) {
            List<ProtobufWriter> list = new ArrayList<>();
            ProtobufWriter next = this;
            while ((next = next.child) != null) {
                list.add(next);
            }
            for (ProtobufWriter item : list) {
                offerPool(item);
            }
        }
        this.parent = null;
        this.child = null;
        this.delegate = null;
        this.mapFieldFunc = null;
        this.objFieldFunc = null;
        this.objExtFunc = null;
        this.features = 0;
        this.enumtostring = false;
        this.count = 0;
        if (this.content.length > DEFAULT_SIZE) {
            this.content = new byte[DEFAULT_SIZE];
        }
        return true;
    }

    protected final void offerPool(ProtobufWriter item) {
        if (this.pool != null && this.pool.size() < CHILD_SIZE) {
            item.recycle();
            this.pool.offer(item);
        }
    }

    public ProtobufWriter pollChild() {
        Queue<ProtobufWriter> queue = this.pool;
        if (queue == null) {
            this.pool = new ArrayDeque<>(CHILD_SIZE);
            queue = this.pool;
        }
        ProtobufWriter result = queue.poll();
        if (result == null) {
            result = new ProtobufWriter();
        }
        if (delegate == null) {
            result.parent = this;
            this.child = result;
            delegate = result;
        } else {
            result.parent = delegate;
            delegate.child = result;
            delegate = result;
        }
        result.configFieldFunc(result.parent);
        return result;
    }

    public void offerChild(ProtobufWriter child) {
        if (child != null) {
            int len = child.length();
            ProtobufWriter next = child;
            while ((next = next.child) != null) {
                len += next.length();
            }
            child.parent.writeSelfLength(len);
        }
    }

    @Override
    public final int length() {
        return count;
    }

    @Override
    public byte[] content() {
        return content;
    }

    @Override
    public final int offset() {
        return 0;
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

    public void completed(ConvertBytesHandler handler, Consumer<ProtobufWriter> callback) {
        handler.completed(content, 0, count, callback, this);
    }

    @Override
    public byte[] toArray() {
        if (delegate == null) {
            byte[] copy = new byte[count];
            System.arraycopy(content, 0, copy, 0, count);
            return copy;
        } else {
            int total = count;
            ProtobufWriter next = this;
            while ((next = next.child) != null) {
                total += next.length();
            }
            byte[] data = new byte[total];
            System.arraycopy(content, 0, data, 0, count);
            next = this;
            int pos = count;
            while ((next = next.child) != null) {
                System.arraycopy(next.content(), 0, data, pos, next.length());
                pos += next.length();
            }
            return data;
        }
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
        if (delegate == null) {
            expand(1);
            content[count++] = ch;
        } else {
            delegate.writeTo(ch);
        }
    }

    public final void writeTo(final byte... chs) {
        writeTo(chs, 0, chs.length);
    }

    public void writeTo(final byte[] chs, final int start, final int len) {
        if (delegate == null) {
            expand(len);
            System.arraycopy(chs, start, content, count, len);
            count += len;
        } else {
            delegate.writeTo(chs, start, len);
        }
    }

    public ProtobufWriter clear() {
        this.count = 0;
        this.delegate = null;
        return this;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "@" + Objects.hashCode(this) + "[count=" + this.count + "]";
    }

    // ------------------------------------------------------------------------
    @Override
    public final void writeBoolean(boolean value) {
        writeTo(value ? (byte) 1 : (byte) 0);
    }

    @Override
    public final void writeNull() {
        // do nothing
    }

    @Override
    public final boolean needWriteClassName() {
        return false;
    }

    @Override
    public final void writeClassName(String clazz) {
        // do nothing
    }

    @Override
    @ClassDepends
    public final void writeObjectB(Object obj) {
        // do nothing
    }

    @Override
    public final void writeObjectE(Object obj) {
        // do nothing
    }

    @Override
    public final void writeArrayB(int size, Encodeable componentEncoder, Object obj) {
        // do nothing
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
    public final void writeMapB(int size, Encodeable keyEncoder, Encodeable valueEncoder, Object obj) {
        // do nothing
    }

    @Override
    public final void writeMapMark() {
        // do nothing
    }

    @Override
    public final void writeMapE() {
        // do nothing
    }

    /**
     * 输出一个字段名
     *
     * @param member 字段
     */
    public final void writeField(final EnMember member) {
        writeTag(member.getTag());
    }

    // 被ObjectEncoder调用
    @Override
    public final void writeField(EnMember member, String fieldName, Type fieldType, int fieldPos) {
        writeTag(member != null ? member.getTag() : fieldPos);
    }

    @Override
    public final void writeByteArray(byte[] values) {
        writeBytes(values);
    }

    @Override
    public final void writeByte(byte value) {
        writeInt(value);
    }

    @Override
    public final void writeChar(char value) {
        writeInt(value);
    }

    @Override
    public final void writeShort(short value) {
        writeInt(value);
    }

    @Override
    public final void writeInt(int value) { // writeSInt32
        if (value >= 0 && value < TENTHOUSAND_MAX) {
            writeTo(TENTHOUSAND_SINT32_BYTES[value]);
            return;
        } else if (value < 0 && value > TENTHOUSAND_MAX) {
            writeTo(TENTHOUSAND_SINT32_BYTES2[-value]);
            return;
        }
        writeUInt32((value << 1) ^ (value >> 31));
    }

    @Override
    public final void writeLong(long value) { // writeSInt64
        if (value >= 0 && value < TENTHOUSAND_MAX) {
            writeTo(TENTHOUSAND_SINT64_BYTES[(int) value]);
            return;
        } else if (value < 0 && value > TENTHOUSAND_MAX) {
            writeTo(TENTHOUSAND_SINT64_BYTES2[(int) -value]);
            return;
        }
        writeUInt64((value << 1) ^ (value >> 63));
    }

    @Override
    public final void writeFloat(float value) {
        writeFixed32(Float.floatToRawIntBits(value));
    }

    @Override
    public final void writeDouble(double value) {
        writeFixed64(Double.doubleToRawLongBits(value));
    }

    @Override
    public final void writeStandardString(String value) {
        writeString(value);
    }

    @Override
    public final void writeString(String value) {
        byte[] bs = Utility.isLatin1(value) ? Utility.latin1ByteArray(value) : value.getBytes(StandardCharsets.UTF_8);
        writeLength(bs.length);
        writeTo(bs);
    }

    public final void writeStrings(int tag, String[] value) {
        String[] array = value;
        if (array != null && array.length > 0) {
            for (String item : array) {
                writeTag(tag);
                byte[] bs = item == null ? EMPTY_BYTES : item.getBytes(StandardCharsets.UTF_8);
                writeBytes(bs);
            }
        }
    }

    public final void writeStrings(int tag, Collection<String> value) {
        Collection<String> array = value;
        if (array != null && !array.isEmpty()) {
            for (String item : array) {
                writeTag(tag);
                byte[] bs = item == null ? EMPTY_BYTES : item.getBytes(StandardCharsets.UTF_8);
                writeBytes(bs);
            }
        }
    }

    public final void writeBools(boolean[] value) {
        boolean[] array = value;
        if (array != null && array.length > 0) {
            writeLength(array.length);
            for (boolean item : array) {
                writeTo(item ? (byte) 1 : (byte) 0);
            }
        }
    }

    public final void writeBools(Boolean[] value) {
        Boolean[] array = value;
        if (array != null && array.length > 0) {
            writeLength(array.length);
            for (Boolean item : array) {
                writeTo(item != null && item ? (byte) 1 : (byte) 0);
            }
        }
    }

    public final void writeBools(Collection<Boolean> value) {
        Collection<Boolean> array = value;
        if (array != null && !array.isEmpty()) {
            writeLength(array.size());
            for (Boolean item : array) {
                writeTo(item != null && item ? (byte) 1 : (byte) 0);
            }
        }
    }

    public final void writeBools(Stream<Boolean> value) {
        if (value != null) {
            writeBools(value.toArray(s -> new Boolean[s]));
        }
    }

    public final void writeBytes(byte[] value) {
        byte[] array = value;
        if (array != null && array.length > 0) {
            writeLength(array.length);
            writeTo(array);
        }
    }

    public final void writeBytes(Byte[] value) {
        Byte[] array = value;
        if (array != null && array.length > 0) {
            writeLength(array.length);
            for (Byte item : array) {
                writeTo(item == null ? (byte) 0 : item);
            }
        }
    }

    public final void writeBytes(Collection<Byte> value) {
        Collection<Byte> array = value;
        if (array != null && !array.isEmpty()) {
            writeLength(array.size());
            for (Byte item : array) {
                writeTo(item == null ? (byte) 0 : item);
            }
        }
    }

    public final void writeBytes(Stream<Byte> value) {
        if (value != null) {
            writeBytes(value.toArray(s -> new Byte[s]));
        }
    }

    public final void writeChars(char[] value) {
        char[] array = value;
        if (array != null && array.length > 0) {
            int len = 0;
            for (char item : array) {
                len += ProtobufFactory.computeSInt32SizeNoTag(item);
            }
            writeLength(len);
            for (char item : array) {
                writeChar(item);
            }
        }
    }

    public final void writeChars(Character[] value) {
        Character[] array = value;
        if (array != null && array.length > 0) {
            int len = 0;
            for (Character item : array) {
                len += ProtobufFactory.computeSInt32SizeNoTag(item);
            }
            writeLength(len);
            for (Character item : array) {
                writeChar(item == null ? 0 : item);
            }
        }
    }

    public final void writeChars(Collection<Character> value) {
        Collection<Character> array = value;
        if (array != null && !array.isEmpty()) {
            int len = 0;
            for (Character item : array) {
                len += ProtobufFactory.computeSInt32SizeNoTag(item);
            }
            writeLength(len);
            for (Character item : array) {
                writeChar(item == null ? 0 : item);
            }
        }
    }

    public final void writeChars(Stream<Character> value) {
        if (value != null) {
            writeChars(value.toArray(s -> new Character[s]));
        }
    }

    public final void writeShorts(short[] value) {
        short[] array = value;
        if (array != null && array.length > 0) {
            int len = 0;
            for (short item : array) {
                len += ProtobufFactory.computeSInt32SizeNoTag(item);
            }
            writeLength(len);
            for (short item : array) {
                writeShort(item);
            }
        }
    }

    public final void writeShorts(Short[] value) {
        Short[] array = value;
        if (array != null && array.length > 0) {
            int len = 0;
            for (Short item : array) {
                len += ProtobufFactory.computeSInt32SizeNoTag(item == null ? 0 : item);
            }
            writeLength(len);
            for (Short item : array) {
                writeShort(item == null ? 0 : item);
            }
        }
    }

    public final void writeShorts(Collection<Short> value) {
        Collection<Short> array = value;
        if (array != null && !array.isEmpty()) {
            int len = 0;
            for (Short item : array) {
                len += ProtobufFactory.computeSInt32SizeNoTag(item == null ? 0 : item);
            }
            writeLength(len);
            for (Short item : array) {
                writeShort(item == null ? 0 : item);
            }
        }
    }

    public final void writeShorts(Stream<Short> value) {
        if (value != null) {
            writeShorts(value.toArray(s -> new Short[s]));
        }
    }

    public final void writeInts(int[] value) {
        int[] array = value;
        if (array != null && array.length > 0) {
            int len = 0;
            for (int item : array) {
                len += ProtobufFactory.computeSInt32SizeNoTag(item);
            }
            writeLength(len);
            for (int item : array) {
                writeInt(item);
            }
        }
    }

    public final void writeInts(Integer[] value) {
        Integer[] array = value;
        if (array != null && array.length > 0) {
            int len = 0;
            for (Integer item : array) {
                len += ProtobufFactory.computeSInt32SizeNoTag(item == null ? 0 : item);
            }
            writeLength(len);
            for (Integer item : array) {
                writeInt(item == null ? 0 : item);
            }
        }
    }

    public final void writeInts(Collection<Integer> value) {
        Collection<Integer> array = value;
        if (array != null && !array.isEmpty()) {
            int len = 0;
            for (Integer item : array) {
                len += ProtobufFactory.computeSInt32SizeNoTag(item == null ? 0 : item);
            }
            writeLength(len);
            for (Integer item : array) {
                writeInt(item == null ? 0 : item);
            }
        }
    }

    public final void writeInts(Stream<Integer> value) {
        if (value != null) {
            writeInts(value.toArray(s -> new Integer[s]));
        }
    }

    public final void writeFloats(float[] value) {
        float[] array = value;
        if (array != null && array.length > 0) {
            int len = array.length * 4;
            writeLength(len);
            for (float item : array) {
                writeFloat(item);
            }
        }
    }

    public final void writeFloats(Float[] value) {
        Float[] array = value;
        if (array != null && array.length > 0) {
            int len = array.length * 4;
            writeLength(len);
            for (Float item : array) {
                writeFloat(item == null ? 0 : item);
            }
        }
    }

    public final void writeFloats(Collection<Float> value) {
        Collection<Float> array = value;
        if (array != null && !array.isEmpty()) {
            int len = array.size() * 4;
            writeLength(len);
            for (Float item : array) {
                writeFloat(item == null ? 0 : item);
            }
        }
    }

    public final void writeFloats(Stream<Float> value) {
        if (value != null) {
            writeFloats(value.toArray(s -> new Float[s]));
        }
    }

    public final void writeLongs(long[] value) {
        long[] array = value;
        if (array != null && array.length > 0) {
            int len = 0;
            for (long item : array) {
                len += ProtobufFactory.computeSInt64SizeNoTag(item);
            }
            writeLength(len);
            for (long item : array) {
                writeLong(item);
            }
        }
    }

    public final void writeLongs(Long[] value) {
        Long[] array = value;
        if (array != null && array.length > 0) {
            int len = 0;
            for (Long item : array) {
                len += ProtobufFactory.computeSInt64SizeNoTag(item == null ? 0 : item);
            }
            writeLength(len);
            for (Long item : array) {
                writeLong(item == null ? 0 : item);
            }
        }
    }

    public final void writeLongs(Collection<Long> value) {
        Collection<Long> array = value;
        if (array != null && !array.isEmpty()) {
            int len = 0;
            for (Long item : array) {
                len += ProtobufFactory.computeSInt64SizeNoTag(item == null ? 0 : item);
            }
            writeLength(len);
            for (Long item : array) {
                writeLong(item == null ? 0 : item);
            }
        }
    }

    public final void writeLongs(Stream<Long> value) {
        if (value != null) {
            writeLongs(value.toArray(s -> new Long[s]));
        }
    }

    public final void writeDoubles(double[] value) {
        double[] array = value;
        if (array != null && array.length > 0) {
            int len = array.length * 8;
            writeLength(len);
            for (double item : array) {
                writeDouble(item);
            }
        }
    }

    public final void writeDoubles(Double[] value) {
        Double[] array = value;
        if (array != null && array.length > 0) {
            int len = array.length * 8;
            writeLength(len);
            for (Double item : array) {
                writeDouble(item == null ? 0 : item);
            }
        }
    }

    public final void writeDoubles(Collection<Double> value) {
        Collection<Double> array = value;
        if (array != null && !array.isEmpty()) {
            int len = array.size() * 8;
            writeLength(len);
            for (Double item : array) {
                writeDouble(item == null ? 0 : item);
            }
        }
    }

    public final void writeDoubles(Stream<Double> value) {
        if (value != null) {
            writeDoubles(value.toArray(s -> new Double[s]));
        }
    }

    @Override
    public final void writeWrapper(StringWrapper value) {
        if (value != null) {
            writeString(value.getValue());
        }
    }

    @ClassDepends
    public final void writeFieldValue(int tag, boolean value) {
        if (value) {
            writeTag(tag);
            writeBoolean(value);
        }
    }

    @ClassDepends
    public final void writeFieldValue(int tag, byte value) {
        if (value != 0) {
            writeTag(tag);
            writeByte(value);
        }
    }

    @ClassDepends
    public final void writeFieldValue(int tag, char value) {
        if (value != 0) {
            writeTag(tag);
            writeChar(value);
        }
    }

    @ClassDepends
    public final void writeFieldValue(int tag, short value) {
        if (value != 0) {
            writeTag(tag);
            writeShort(value);
        }
    }

    @ClassDepends
    public final void writeFieldValue(int tag, int value) {
        if (value != 0) {
            writeTag(tag);
            writeInt(value);
        }
    }

    @ClassDepends
    public final void writeFieldValue(int tag, float value) {
        if (value != 0) {
            writeTag(tag);
            writeFloat(value);
        }
    }

    @ClassDepends
    public final void writeFieldValue(int tag, long value) {
        if (value != 0) {
            writeTag(tag);
            writeLong(value);
        }
    }

    @ClassDepends
    public final void writeFieldValue(int tag, double value) {
        if (value != 0) {
            writeTag(tag);
            writeDouble(value);
        }
    }

    @ClassDepends
    public final void writeFieldValue(int tag, Boolean value) {
        if (value != null && !value) {
            writeTag(tag);
            writeBoolean(value);
        }
    }

    @ClassDepends
    public final void writeFieldValue(int tag, Byte value) {
        if (value != null && value != 0) {
            writeTag(tag);
            writeByte(value);
        }
    }

    @ClassDepends
    public final void writeFieldValue(int tag, Character value) {
        if (value != null && value != 0) {
            writeTag(tag);
            writeChar(value);
        }
    }

    @ClassDepends
    public final void writeFieldValue(int tag, Short value) {
        if (value != null && value != 0) {
            writeTag(tag);
            writeShort(value);
        }
    }

    @ClassDepends
    public final void writeFieldValue(int tag, Integer value) {
        if (value != null && value != 0) {
            writeTag(tag);
            writeInt(value);
        }
    }

    @ClassDepends
    public final void writeFieldValue(int tag, Float value) {
        if (value != null && value != 0) {
            writeTag(tag);
            writeFloat(value);
        }
    }

    @ClassDepends
    public final void writeFieldValue(int tag, Long value) {
        if (value != null && value != 0) {
            writeTag(tag);
            writeLong(value);
        }
    }

    @ClassDepends
    public final void writeFieldValue(int tag, Double value) {
        if (value != null && value != 0) {
            writeTag(tag);
            writeDouble(value);
        }
    }

    @ClassDepends
    public final void writeFieldValue(int tag, boolean[] value) {
        if (value != null && value.length > 0) {
            writeTag(tag);
            writeBools(value);
        }
    }

    @ClassDepends
    public final void writeFieldValue(int tag, byte[] value) {
        if (value != null && value.length > 0) {
            writeTag(tag);
            writeBytes(value);
        }
    }

    @ClassDepends
    public final void writeFieldValue(int tag, char[] value) {
        if (value != null && value.length > 0) {
            writeTag(tag);
            writeChars(value);
        }
    }

    @ClassDepends
    public final void writeFieldValue(int tag, short[] value) {
        if (value != null && value.length > 0) {
            writeTag(tag);
            writeShorts(value);
        }
    }

    @ClassDepends
    public final void writeFieldValue(int tag, int[] value) {
        if (value != null && value.length > 0) {
            writeTag(tag);
            writeInts(value);
        }
    }

    @ClassDepends
    public final void writeFieldValue(int tag, float[] value) {
        if (value != null && value.length > 0) {
            writeTag(tag);
            writeFloats(value);
        }
    }

    @ClassDepends
    public final void writeFieldValue(int tag, long[] value) {
        if (value != null && value.length > 0) {
            writeTag(tag);
            writeLongs(value);
        }
    }

    @ClassDepends
    public final void writeFieldValue(int tag, double[] value) {
        if (value != null && value.length > 0) {
            writeTag(tag);
            writeDoubles(value);
        }
    }

    @ClassDepends
    public final void writeFieldValue(int tag, Boolean[] value) {
        if (value != null && value.length > 0) {
            writeTag(tag);
            writeBools(value);
        }
    }

    @ClassDepends
    public final void writeFieldValue(int tag, Byte[] value) {
        if (value != null && value.length > 0) {
            writeTag(tag);
            writeBytes(value);
        }
    }

    @ClassDepends
    public final void writeFieldValue(int tag, Character[] value) {
        if (value != null && value.length > 0) {
            writeTag(tag);
            writeChars(value);
        }
    }

    @ClassDepends
    public final void writeFieldValue(int tag, Short[] value) {
        if (value != null && value.length > 0) {
            writeTag(tag);
            writeShorts(value);
        }
    }

    @ClassDepends
    public final void writeFieldValue(int tag, Integer[] value) {
        if (value != null && value.length > 0) {
            writeTag(tag);
            writeInts(value);
        }
    }

    @ClassDepends
    public final void writeFieldValue(int tag, Float[] value) {
        if (value != null && value.length > 0) {
            writeTag(tag);
            writeFloats(value);
        }
    }

    @ClassDepends
    public final void writeFieldValue(int tag, Long[] value) {
        if (value != null && value.length > 0) {
            writeTag(tag);
            writeLongs(value);
        }
    }

    @ClassDepends
    public final void writeFieldValue(int tag, Double[] value) {
        if (value != null && value.length > 0) {
            writeTag(tag);
            writeDoubles(value);
        }
    }

    @ClassDepends
    public final void writeFieldValue(int tag, String[] value) {
        if (value != null && value.length > 0) {
            writeStrings(tag, value);
        }
    }

    @ClassDepends
    public final void writeFieldBoolsValue(int tag, Collection<Boolean> value) {
        if (value != null && !value.isEmpty()) {
            writeTag(tag);
            writeBools(value);
        }
    }

    @ClassDepends
    public final void writeFieldBytesValue(int tag, Collection<Byte> value) {
        if (value != null && !value.isEmpty()) {
            writeTag(tag);
            writeBytes(value);
        }
    }

    @ClassDepends
    public final void writeFieldCharsValue(int tag, Collection<Character> value) {
        if (value != null && !value.isEmpty()) {
            writeTag(tag);
            writeChars(value);
        }
    }

    @ClassDepends
    public final void writeFieldShortsValue(int tag, Collection<Short> value) {
        if (value != null && !value.isEmpty()) {
            writeTag(tag);
            writeShorts(value);
        }
    }

    @ClassDepends
    public final void writeFieldIntsValue(int tag, Collection<Integer> value) {
        if (value != null && !value.isEmpty()) {
            writeTag(tag);
            writeInts(value);
        }
    }

    @ClassDepends
    public final void writeFieldFloatsValue(int tag, Collection<Float> value) {
        if (value != null && !value.isEmpty()) {
            writeTag(tag);
            writeFloats(value);
        }
    }

    @ClassDepends
    public final void writeFieldLongsValue(int tag, Collection<Long> value) {
        if (value != null && !value.isEmpty()) {
            writeTag(tag);
            writeLongs(value);
        }
    }

    @ClassDepends
    public final void writeFieldDoublesValue(int tag, Collection<Double> value) {
        if (value != null && !value.isEmpty()) {
            writeTag(tag);
            writeDoubles(value);
        }
    }

    @ClassDepends
    public final void writeFieldStringsValue(int tag, Collection<String> value) {
        if (value != null && !value.isEmpty()) {
            writeStrings(tag, value);
        }
    }

    @ClassDepends
    public final void writeFieldValue(int tag, String value) {
        if (value != null && (!value.isEmpty() || !tiny())) {
            writeTag(tag);
            writeString(value);
        }
    }

    @ClassDepends
    public final void writeFieldValue(int tag, Enum value) {
        if (value != null) {
            writeTag(tag);
            if (enumtostring) {
                writeString(value.name());
            } else {
                writeUInt32(value.ordinal());
            }
        }
    }

    @ClassDepends
    public final void writeFieldValue(int tag, SimpledCoder encoder, Object value) {
        if (value != null) {
            writeTag(tag);
            encoder.convertTo(this, value);
        }
    }

    @Override
    @ClassDepends // objExtFunc扩展字段时member=null
    public final void writeObjectField(@Nullable EnMember member, Object obj) {
        Object value;
        if (objFieldFunc == null) {
            value = member.getFieldValue(obj);
        } else {
            value = objFieldFunc.apply(member.getAttribute(), obj);
        }
        if (value == null) {
            return;
        }
        ProtobufEncodeable encoder = (ProtobufEncodeable) member.getEncoder();
        if (encoder instanceof SimpledCoder) {
            this.writeField(member);
            encoder.convertTo(this, value);
        } else {
            encoder.convertTo(this, member, value);
        }
    }

    @ClassDepends
    public final void writeTag(int tag) {
        if (tag < 128) {
            writeTo((byte) tag);
        } else {
            writeUInt32(tag);
        }
    }

    protected final void writeLength(int value) {
        if (value < 128) {
            writeTo((byte) value);
        } else {
            writeUInt32(value);
        }
    }

    protected final void writeSelfLength(int value) {
        ProtobufWriter old = this.delegate;
        this.delegate = null;
        if (value < 128) {
            writeTo((byte) value);
        } else {
            writeUInt32(value);
        }
        this.delegate = old;
    }

    protected void writeUInt32(int value) {
        if (value >= 0 && value < TENTHOUSAND_MAX) {
            writeTo(TENTHOUSAND_UINT_BYTES[value]);
            return;
        } else if (value < 0 && value > TENTHOUSAND_MAX) {
            writeTo(TENTHOUSAND_UINT_BYTES2[-value]);
            return;
        }
        if (delegate == null) {
            expand(5);
            int curr = this.count;
            byte[] data = this.content;
            while (true) {
                if ((value & ~0x7F) == 0) {
                    data[curr++] = (byte) value;
                    this.count = curr;
                    return;
                } else {
                    data[curr++] = (byte) ((value & 0x7F) | 0x80);
                    value >>>= 7;
                }
            }
        } else {
            delegate.writeUInt32(value);
        }
    }

    protected void writeUInt64(long value) {
        if (value >= 0 && value < TENTHOUSAND_MAX) {
            writeTo(TENTHOUSAND_UINT_BYTES[(int) value]);
            return;
        } else if (value < 0 && value > TENTHOUSAND_MAX) {
            writeTo(TENTHOUSAND_UINT_BYTES2[(int) -value]);
            return;
        }
        if (delegate == null) {
            expand(10);
            int curr = this.count;
            byte[] data = this.content;
            while (true) {
                if ((value & ~0x7FL) == 0) {
                    data[curr++] = (byte) value;
                    this.count = curr;
                    return;
                } else {
                    data[curr++] = (byte) (((int) value & 0x7F) | 0x80);
                    value >>>= 7;
                }
            }
        } else {
            delegate.writeUInt64(value);
        }
    }

    protected final void writeFixed32(int value) {
        if (value >= 0 && value < TENTHOUSAND_MAX) {
            writeTo(TENTHOUSAND_FIXED32_BYTES[value]);
            return;
        } else if (value < 0 && value > TENTHOUSAND_MAX) {
            writeTo(TENTHOUSAND_FIXED32_BYTES2[-value]);
            return;
        }
        writeTo(
                (byte) (value & 0xFF), // 0
                (byte) ((value >> 8) & 0xFF), // 1
                (byte) ((value >> 16) & 0xFF), // 2
                (byte) ((value >> 24) & 0xFF)); // 3
    }

    protected final void writeFixed64(long value) {
        if (value >= 0 && value < TENTHOUSAND_MAX) {
            writeTo(TENTHOUSAND_FIXED64_BYTES[(int) value]);
            return;
        } else if (value < 0 && value > TENTHOUSAND_MAX) {
            writeTo(TENTHOUSAND_FIXED64_BYTES2[(int) -value]);
            return;
        }
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

    public static byte[] uint64(long value) {
        byte[] bs = new byte[16];
        int pos = 0;
        while (true) {
            if ((value & ~0x7FL) == 0) {
                bs[pos++] = ((byte) value);
                return pos == bs.length ? bs : Arrays.copyOf(bs, pos);
            } else {
                bs[pos++] = (byte) (((int) value & 0x7F) | 0x80);
                value >>>= 7;
            }
        }
    }

    public static byte[] sint32(int value) {
        return uint32((value << 1) ^ (value >> 31));
    }

    public static byte[] sint64(long value) {
        return uint64((value << 1) ^ (value >> 63));
    }

    public static byte[] fixed32(int value) {
        return new byte[] {
            (byte) (value & 0xFF),
            (byte) ((value >> 8) & 0xFF),
            (byte) ((value >> 16) & 0xFF),
            (byte) ((value >> 24) & 0xFF)
        };
    }

    public static byte[] fixed64(long value) {
        return new byte[] {
            (byte) ((int) (value) & 0xFF),
            (byte) ((int) (value >> 8) & 0xFF),
            (byte) ((int) (value >> 16) & 0xFF),
            (byte) ((int) (value >> 24) & 0xFF),
            (byte) ((int) (value >> 32) & 0xFF),
            (byte) ((int) (value >> 40) & 0xFF),
            (byte) ((int) (value >> 48) & 0xFF),
            (byte) ((int) (value >> 56) & 0xFF)
        };
    }
}
