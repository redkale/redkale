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
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.redkale.annotation.ClassDepends;
import org.redkale.convert.*;
import org.redkale.util.*;

/** @author zhangjx */
public class ProtobufWriter extends Writer implements ByteTuple {

    private static final int DEFAULT_SIZE = Integer.getInteger(
            "redkale.convert.protobuf.writer.buffer.defsize",
            Integer.getInteger("redkale.convert.writer.buffer.defsize", 1024));

    private static final int CHILD_SIZE = 8;

    private static final int TENTHOUSAND_MAX = 10001;

    private static final byte[][] TENTHOUSAND_UINT_BYTES = new byte[TENTHOUSAND_MAX][];
    private static final byte[][] TENTHOUSAND_UINT_BYTES2 = new byte[TENTHOUSAND_MAX][];

    private static final byte[][] TENTHOUSAND_SINT32_BYTES = new byte[TENTHOUSAND_MAX][];
    private static final byte[][] TENTHOUSAND_SINT32_BYTES2 = new byte[TENTHOUSAND_MAX][];

    private static final byte[][] TENTHOUSAND_SINT64_BYTES = new byte[TENTHOUSAND_MAX][];
    private static final byte[][] TENTHOUSAND_SINT64_BYTES2 = new byte[TENTHOUSAND_MAX][];

    private static final byte[][] TENTHOUSAND_FIXED32_BYTES = new byte[TENTHOUSAND_MAX][];
    private static final byte[][] TENTHOUSAND_FIXED32_BYTES2 = new byte[TENTHOUSAND_MAX][];

    private static final byte[][] TENTHOUSAND_FIXED64_BYTES = new byte[TENTHOUSAND_MAX][];
    private static final byte[][] TENTHOUSAND_FIXED64_BYTES2 = new byte[TENTHOUSAND_MAX][];

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

    private byte[] content;

    protected int count;

    protected int initOffset;

    protected boolean enumtostring;

    protected ProtobufWriter parent;

    private ArrayDeque<ProtobufWriter> childWriters;

    protected ProtobufWriter(ProtobufWriter parent) {
        this();
        this.parent = parent;
        if (parent != null) {
            this.features = parent.features;
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

    protected ProtobufWriter configParentFunc(ProtobufWriter parent) {
        this.parent = parent;
        if (parent != null) {
            this.features = parent.features;
            this.enumtostring = parent.enumtostring;
        }
        return this;
    }

    protected ProtobufWriter configFieldFunc(ProtobufWriter out) {
        if (out == null) {
            return this;
        }
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
        this.parent = null;
        this.mapFieldFunc = null;
        this.objFieldFunc = null;
        this.objExtFunc = null;
        this.features = 0;
        this.enumtostring = false;
        this.count = 0;
        this.initOffset = 0;
        if (this.content.length > DEFAULT_SIZE) {
            this.content = new byte[DEFAULT_SIZE];
        }
        return true;
    }

    public final ProtobufWriter pollChild() {
        Queue<ProtobufWriter> queue = this.childWriters;
        if (queue == null) {
            this.childWriters = new ArrayDeque<>(CHILD_SIZE);
            queue = this.childWriters;
        }
        ProtobufWriter result = queue.poll();
        if (result == null) {
            result = new ProtobufWriter();
        }
        return result.configFieldFunc(this);
    }

    public final void offerChild(final ProtobufWriter child) {
        Queue<ProtobufWriter> queue = this.childWriters;
        if (child != null && queue != null && queue.size() < CHILD_SIZE) {
            child.recycle();
            queue.offer(child);
        }
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
        byte[] copy = new byte[count];
        System.arraycopy(content, 0, copy, 0, count);
        return copy;
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
            parent.writeLength(count());
            parent.writeTo(toArray());
        }
    }

    @Override
    public int writeArrayB(int size, Encodeable encoder, Encodeable componentEncoder, Object obj) {
        return -1;
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
    public int writeMapB(int size, Encodeable keyEncoder, Encodeable valueEncoder, Object obj) {
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
    public void writeByte(byte value) {
        writeInt(value);
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
    public void writeLong(long value) { // writeSInt64
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
        byte[] bs = Utility.isLatin1(value) ? Utility.latin1ByteArray(value) : value.getBytes(StandardCharsets.UTF_8);
        writeLength(bs.length);
        writeTo(bs);
    }

    public void writeStrings(int tag, String[] value) {
        String[] array = value;
        if (array != null && array.length > 0) {
            for (String item : array) {
                writeTag(tag);
                byte[] bs = item == null ? new byte[0] : item.getBytes(StandardCharsets.UTF_8);
                writeBytes(bs);
            }
        }
    }

    public void writeStrings(int tag, Collection<String> value) {
        Collection<String> array = value;
        if (array != null && !array.isEmpty()) {
            for (String item : array) {
                writeTag(tag);
                byte[] bs = item == null ? new byte[0] : item.getBytes(StandardCharsets.UTF_8);
                writeBytes(bs);
            }
        }
    }

    public void writeBools(boolean[] value) {
        boolean[] array = value;
        if (array != null && array.length > 0) {
            writeLength(array.length);
            for (boolean item : array) {
                writeTo(item ? (byte) 1 : (byte) 0);
            }
        }
    }

    public void writeBools(Boolean[] value) {
        Boolean[] array = value;
        if (array != null && array.length > 0) {
            writeLength(array.length);
            for (Boolean item : array) {
                writeTo(item != null && item ? (byte) 1 : (byte) 0);
            }
        }
    }

    public void writeBools(Collection<Boolean> value) {
        Collection<Boolean> array = value;
        if (array != null && !array.isEmpty()) {
            writeLength(array.size());
            for (Boolean item : array) {
                writeTo(item != null && item ? (byte) 1 : (byte) 0);
            }
        }
    }

    public void writeBools(Stream<Boolean> value) {
        if (value != null) {
            writeBools(value.toArray(s -> new Boolean[s]));
        }
    }

    public void writeBytes(byte[] value) {
        byte[] array = value;
        if (array != null && array.length > 0) {
            writeLength(array.length);
            writeTo(array);
        }
    }

    public void writeBytes(Byte[] value) {
        Byte[] array = value;
        if (array != null && array.length > 0) {
            writeLength(array.length);
            for (Byte item : array) {
                writeTo(item == null ? (byte) 0 : item);
            }
        }
    }

    public void writeBytes(Collection<Byte> value) {
        Collection<Byte> array = value;
        if (array != null && !array.isEmpty()) {
            writeLength(array.size());
            for (Byte item : array) {
                writeTo(item == null ? (byte) 0 : item);
            }
        }
    }

    public void writeBytes(Stream<Byte> value) {
        if (value != null) {
            writeBytes(value.toArray(s -> new Byte[s]));
        }
    }

    public void writeChars(char[] value) {
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

    public void writeChars(Character[] value) {
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

    public void writeChars(Collection<Character> value) {
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

    public void writeChars(Stream<Character> value) {
        if (value != null) {
            writeChars(value.toArray(s -> new Character[s]));
        }
    }

    public void writeShorts(short[] value) {
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

    public void writeShorts(Short[] value) {
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

    public void writeShorts(Collection<Short> value) {
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

    public void writeShorts(Stream<Short> value) {
        if (value != null) {
            writeShorts(value.toArray(s -> new Short[s]));
        }
    }

    public void writeInts(int[] value) {
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

    public void writeInts(Integer[] value) {
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

    public void writeInts(Collection<Integer> value) {
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

    public void writeInts(Stream<Integer> value) {
        if (value != null) {
            writeInts(value.toArray(s -> new Integer[s]));
        }
    }

    public void writeFloats(float[] value) {
        float[] array = value;
        if (array != null && array.length > 0) {
            int len = array.length * 4;
            writeLength(len);
            for (float item : array) {
                writeFloat(item);
            }
        }
    }

    public void writeFloats(Float[] value) {
        Float[] array = value;
        if (array != null && array.length > 0) {
            int len = array.length * 4;
            writeLength(len);
            for (Float item : array) {
                writeFloat(item == null ? 0 : item);
            }
        }
    }

    public void writeFloats(Collection<Float> value) {
        Collection<Float> array = value;
        if (array != null && !array.isEmpty()) {
            int len = array.size() * 4;
            writeLength(len);
            for (Float item : array) {
                writeFloat(item == null ? 0 : item);
            }
        }
    }

    public void writeFloats(Stream<Float> value) {
        if (value != null) {
            writeFloats(value.toArray(s -> new Float[s]));
        }
    }

    public void writeLongs(long[] value) {
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

    public void writeLongs(Long[] value) {
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

    public void writeLongs(Collection<Long> value) {
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

    public void writeLongs(Stream<Long> value) {
        if (value != null) {
            writeLongs(value.toArray(s -> new Long[s]));
        }
    }

    public void writeDoubles(double[] value) {
        double[] array = value;
        if (array != null && array.length > 0) {
            int len = array.length * 8;
            writeLength(len);
            for (double item : array) {
                writeDouble(item);
            }
        }
    }

    public void writeDoubles(Double[] value) {
        Double[] array = value;
        if (array != null && array.length > 0) {
            int len = array.length * 8;
            writeLength(len);
            for (Double item : array) {
                writeDouble(item == null ? 0 : item);
            }
        }
    }

    public void writeDoubles(Collection<Double> value) {
        Collection<Double> array = value;
        if (array != null && !array.isEmpty()) {
            int len = array.size() * 8;
            writeLength(len);
            for (Double item : array) {
                writeDouble(item == null ? 0 : item);
            }
        }
    }

    public void writeDoubles(Stream<Double> value) {
        if (value != null) {
            writeDoubles(value.toArray(s -> new Double[s]));
        }
    }

    public void writeAtomicIntegers(AtomicInteger[] value) {
        AtomicInteger[] array = value;
        if (array != null && array.length > 0) {
            int len = 0;
            for (AtomicInteger item : array) {
                len += ProtobufFactory.computeSInt32SizeNoTag(item == null ? 0 : item.get());
            }
            writeLength(len);
            for (AtomicInteger item : array) {
                writeInt(item == null ? 0 : item.get());
            }
        }
    }

    public void writeAtomicIntegers(Collection<AtomicInteger> value) {
        Collection<AtomicInteger> array = value;
        if (array != null && !array.isEmpty()) {
            int len = 0;
            for (AtomicInteger item : array) {
                len += ProtobufFactory.computeSInt32SizeNoTag(item == null ? 0 : item.get());
            }
            writeLength(len);
            for (AtomicInteger item : array) {
                writeInt(item == null ? 0 : item.get());
            }
        }
    }

    public void writeAtomicIntegers(Stream<AtomicInteger> value) {
        if (value != null) {
            writeAtomicIntegers(value.toArray(s -> new AtomicInteger[s]));
        }
    }

    public void writeAtomicLongs(AtomicLong[] value) {
        AtomicLong[] array = value;
        if (array != null && array.length > 0) {
            int len = 0;
            for (AtomicLong item : array) {
                len += ProtobufFactory.computeSInt64SizeNoTag(item == null ? 0 : item.get());
            }
            writeLength(len);
            for (AtomicLong item : array) {
                writeLong(item == null ? 0 : item.get());
            }
        }
    }

    public void writeAtomicLongs(Collection<AtomicLong> value) {
        Collection<AtomicLong> array = value;
        if (array != null && !array.isEmpty()) {
            int len = 0;
            for (AtomicLong item : array) {
                len += ProtobufFactory.computeSInt64SizeNoTag(item == null ? 0 : item.get());
            }
            writeLength(len);
            for (AtomicLong item : array) {
                writeLong(item == null ? 0 : item.get());
            }
        }
    }

    public void writeAtomicLongs(Stream<AtomicLong> value) {
        if (value != null) {
            writeAtomicLongs(value.toArray(s -> new AtomicLong[s]));
        }
    }

    @Override
    public void writeWrapper(StringWrapper value) {
        if (value != null) {
            writeString(value.getValue());
        }
    }

    @ClassDepends
    public void writeFieldValue(int tag, boolean value) {
        if (value) {
            writeTag(tag);
            writeBoolean(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldValue(int tag, byte value) {
        if (value != 0) {
            writeTag(tag);
            writeByte(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldValue(int tag, char value) {
        if (value != 0) {
            writeTag(tag);
            writeChar(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldValue(int tag, short value) {
        if (value != 0) {
            writeTag(tag);
            writeShort(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldValue(int tag, int value) {
        if (value != 0) {
            writeTag(tag);
            writeInt(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldValue(int tag, float value) {
        if (value != 0) {
            writeTag(tag);
            writeFloat(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldValue(int tag, long value) {
        if (value != 0) {
            writeTag(tag);
            writeLong(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldValue(int tag, double value) {
        if (value != 0) {
            writeTag(tag);
            writeDouble(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldValue(int tag, Boolean value) {
        if (value != null && !value) {
            writeTag(tag);
            writeBoolean(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldValue(int tag, Byte value) {
        if (value != null && value != 0) {
            writeTag(tag);
            writeByte(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldValue(int tag, Character value) {
        if (value != null && value != 0) {
            writeTag(tag);
            writeChar(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldValue(int tag, Short value) {
        if (value != null && value != 0) {
            writeTag(tag);
            writeShort(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldValue(int tag, Integer value) {
        if (value != null && value != 0) {
            writeTag(tag);
            writeInt(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldValue(int tag, Float value) {
        if (value != null && value != 0) {
            writeTag(tag);
            writeFloat(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldValue(int tag, Long value) {
        if (value != null && value != 0) {
            writeTag(tag);
            writeLong(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldValue(int tag, Double value) {
        if (value != null && value != 0) {
            writeTag(tag);
            writeDouble(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldValue(int tag, AtomicInteger value) {
        if (value != null && value.get() != 0) {
            writeTag(tag);
            writeInt(value.get());
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldValue(int tag, AtomicLong value) {
        if (value != null && value.get() != 0) {
            writeTag(tag);
            writeLong(value.get());
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldValue(int tag, boolean[] value) {
        if (value != null && value.length > 0) {
            writeTag(tag);
            writeBools(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldValue(int tag, byte[] value) {
        if (value != null && value.length > 0) {
            writeTag(tag);
            writeBytes(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldValue(int tag, char[] value) {
        if (value != null && value.length > 0) {
            writeTag(tag);
            writeChars(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldValue(int tag, short[] value) {
        if (value != null && value.length > 0) {
            writeTag(tag);
            writeShorts(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldValue(int tag, int[] value) {
        if (value != null && value.length > 0) {
            writeTag(tag);
            writeInts(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldValue(int tag, float[] value) {
        if (value != null && value.length > 0) {
            writeTag(tag);
            writeFloats(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldValue(int tag, long[] value) {
        if (value != null && value.length > 0) {
            writeTag(tag);
            writeLongs(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldValue(int tag, double[] value) {
        if (value != null && value.length > 0) {
            writeTag(tag);
            writeDoubles(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldValue(int tag, Boolean[] value) {
        if (value != null && value.length > 0) {
            writeTag(tag);
            writeBools(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldValue(int tag, Byte[] value) {
        if (value != null && value.length > 0) {
            writeTag(tag);
            writeBytes(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldValue(int tag, Character[] value) {
        if (value != null && value.length > 0) {
            writeTag(tag);
            writeChars(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldValue(int tag, Short[] value) {
        if (value != null && value.length > 0) {
            writeTag(tag);
            writeShorts(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldValue(int tag, Integer[] value) {
        if (value != null && value.length > 0) {
            writeTag(tag);
            writeInts(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldValue(int tag, Float[] value) {
        if (value != null && value.length > 0) {
            writeTag(tag);
            writeFloats(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldValue(int tag, Long[] value) {
        if (value != null && value.length > 0) {
            writeTag(tag);
            writeLongs(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldValue(int tag, Double[] value) {
        if (value != null && value.length > 0) {
            writeTag(tag);
            writeDoubles(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldValue(int tag, AtomicInteger[] value) {
        if (value != null && value.length > 0) {
            writeTag(tag);
            writeAtomicIntegers(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldValue(int tag, AtomicLong[] value) {
        if (value != null && value.length > 0) {
            writeTag(tag);
            writeAtomicLongs(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldValue(int tag, String[] value) {
        if (value != null && value.length > 0) {
            writeStrings(tag, value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldBoolsValue(int tag, Collection<Boolean> value) {
        if (value != null && !value.isEmpty()) {
            writeTag(tag);
            writeBools(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldBytesValue(int tag, Collection<Byte> value) {
        if (value != null && !value.isEmpty()) {
            writeTag(tag);
            writeBytes(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldCharsValue(int tag, Collection<Character> value) {
        if (value != null && !value.isEmpty()) {
            writeTag(tag);
            writeChars(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldShortsValue(int tag, Collection<Short> value) {
        if (value != null && !value.isEmpty()) {
            writeTag(tag);
            writeShorts(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldIntsValue(int tag, Collection<Integer> value) {
        if (value != null && !value.isEmpty()) {
            writeTag(tag);
            writeInts(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldFloatsValue(int tag, Collection<Float> value) {
        if (value != null && !value.isEmpty()) {
            writeTag(tag);
            writeFloats(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldLongsValue(int tag, Collection<Long> value) {
        if (value != null && !value.isEmpty()) {
            writeTag(tag);
            writeLongs(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldDoublesValue(int tag, Collection<Double> value) {
        if (value != null && !value.isEmpty()) {
            writeTag(tag);
            writeDoubles(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldAtomicIntegersValue(int tag, Collection<AtomicInteger> value) {
        if (value != null && !value.isEmpty()) {
            writeTag(tag);
            writeAtomicIntegers(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldAtomicLongsValue(int tag, Collection<AtomicLong> value) {
        if (value != null && !value.isEmpty()) {
            writeTag(tag);
            writeAtomicLongs(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldStringsValue(int tag, Collection<String> value) {
        if (value != null && !value.isEmpty()) {
            writeStrings(tag, value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldValue(int tag, String value) {
        if (value != null && (!value.isEmpty() || !tiny())) {
            writeTag(tag);
            writeString(value);
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldValue(int tag, Enum value) {
        if (value != null) {
            writeTag(tag);
            if (enumtostring) {
                writeString(value.name());
            } else {
                writeUInt32(value.ordinal());
            }
            this.comma = true;
        }
    }

    @ClassDepends
    public void writeFieldValue(int tag, SimpledCoder encoder, Object value) {
        if (value != null) {
            writeTag(tag);
            encoder.convertTo(this, value);
            this.comma = true;
        }
    }

    @Override
    @ClassDepends
    public void writeObjectField(final EnMember member, Object obj) {
        Object value;
        if (objFieldFunc == null) {
            value = member.getFieldValue(obj);
        } else {
            value = objFieldFunc.apply(member.getAttribute(), obj);
        }
        if (value == null) {
            return;
        }
        Encodeable encoder = member.getEncoder();
        if (encoder instanceof MapEncoder) {
            if (!((Map) value).isEmpty()) {
                ((MapEncoder) encoder).convertTo(this, member, (Map) value);
            }
        } else if (encoder instanceof ProtobufArrayEncoder) {
            ProtobufArrayEncoder arrayEncoder = (ProtobufArrayEncoder) encoder;
            arrayEncoder.convertTo(this, member, (Object[]) value);
        } else if (encoder instanceof ProtobufCollectionEncoder) {
            ProtobufCollectionEncoder collectionEncoder = (ProtobufCollectionEncoder) encoder;
            collectionEncoder.convertTo(this, member, (Collection) value);
        } else if (encoder instanceof ProtobufStreamEncoder) {
            ProtobufStreamEncoder streamEncoder = (ProtobufStreamEncoder) encoder;
            streamEncoder.convertTo(this, member, (Stream) value);
        } else {
            this.writeFieldName(member);
            encoder.convertTo(this, value);
        }
        this.comma = true;
    }

    @ClassDepends
    public void writeTag(int tag) {
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
        if (value >= 0 && value < TENTHOUSAND_MAX) {
            writeTo(TENTHOUSAND_UINT_BYTES[value]);
            return;
        } else if (value < 0 && value > TENTHOUSAND_MAX) {
            writeTo(TENTHOUSAND_UINT_BYTES2[-value]);
            return;
        }
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
        if (value >= 0 && value < TENTHOUSAND_MAX) {
            writeTo(TENTHOUSAND_UINT_BYTES[(int) value]);
            return;
        } else if (value < 0 && value > TENTHOUSAND_MAX) {
            writeTo(TENTHOUSAND_UINT_BYTES2[(int) -value]);
            return;
        }
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
        if (value >= 0 && value < TENTHOUSAND_MAX) {
            writeTo(TENTHOUSAND_FIXED32_BYTES[value]);
            return;
        } else if (value < 0 && value > TENTHOUSAND_MAX) {
            writeTo(TENTHOUSAND_FIXED32_BYTES2[-value]);
            return;
        }
        writeTo((byte) (value & 0xFF), (byte) ((value >> 8) & 0xFF), (byte) ((value >> 16) & 0xFF), (byte)
                ((value >> 24) & 0xFF));
    }

    protected void writeFixed64(long value) {
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
