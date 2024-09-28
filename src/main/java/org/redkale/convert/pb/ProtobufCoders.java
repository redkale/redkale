/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.convert.pb;

import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Stream;
import org.redkale.convert.SimpledCoder;
import org.redkale.util.*;

/**
 *
 * @author zhangjx
 */
public abstract class ProtobufCoders {

    static final Creator<List> LIST_CREATOR = Creator.load(List.class);

    private ProtobufCoders() {
        // do nothing
    }

    public static class ProtobufBoolArraySimpledCoder extends SimpledCoder<ProtobufReader, ProtobufWriter, boolean[]>
            implements ProtobufPrimitivable {

        public static final ProtobufBoolArraySimpledCoder instance = new ProtobufBoolArraySimpledCoder();

        @Override
        public void convertTo(ProtobufWriter out, boolean[] values) {
            out.writeBools(values);
        }

        @Override
        public boolean[] convertFrom(ProtobufReader in) {
            return in.readBools();
        }
    }

    public static class ProtobufByteArraySimpledCoder extends SimpledCoder<ProtobufReader, ProtobufWriter, byte[]>
            implements ProtobufPrimitivable {

        public static final ProtobufByteArraySimpledCoder instance = new ProtobufByteArraySimpledCoder();

        @Override
        public void convertTo(ProtobufWriter out, byte[] values) {
            out.writeBytes(values);
        }

        @Override
        public byte[] convertFrom(ProtobufReader in) {
            return in.readBytes();
        }
    }

    public static class ProtobufCharArraySimpledCoder extends SimpledCoder<ProtobufReader, ProtobufWriter, char[]>
            implements ProtobufPrimitivable {

        public static final ProtobufCharArraySimpledCoder instance = new ProtobufCharArraySimpledCoder();

        @Override
        public void convertTo(ProtobufWriter out, char[] values) {
            out.writeChars(values);
        }

        @Override
        public char[] convertFrom(ProtobufReader in) {
            return in.readChars();
        }
    }

    public static class ProtobufShortArraySimpledCoder extends SimpledCoder<ProtobufReader, ProtobufWriter, short[]>
            implements ProtobufPrimitivable {

        public static final ProtobufShortArraySimpledCoder instance = new ProtobufShortArraySimpledCoder();

        @Override
        public void convertTo(ProtobufWriter out, short[] values) {
            out.writeShorts(values);
        }

        @Override
        public short[] convertFrom(ProtobufReader in) {
            return in.readShorts();
        }
    }

    public static class ProtobufIntArraySimpledCoder extends SimpledCoder<ProtobufReader, ProtobufWriter, int[]>
            implements ProtobufPrimitivable {

        public static final ProtobufIntArraySimpledCoder instance = new ProtobufIntArraySimpledCoder();

        @Override
        public void convertTo(ProtobufWriter out, int[] values) {
            out.writeInts(values);
        }

        @Override
        public int[] convertFrom(ProtobufReader in) {
            return in.readInts();
        }
    }

    public static class ProtobufFloatArraySimpledCoder extends SimpledCoder<ProtobufReader, ProtobufWriter, float[]>
            implements ProtobufPrimitivable {

        public static final ProtobufFloatArraySimpledCoder instance = new ProtobufFloatArraySimpledCoder();

        @Override
        public void convertTo(ProtobufWriter out, float[] values) {
            out.writeFloats(values);
        }

        @Override
        public float[] convertFrom(ProtobufReader in) {
            return in.readFloats();
        }
    }

    public static class ProtobufLongArraySimpledCoder extends SimpledCoder<ProtobufReader, ProtobufWriter, long[]>
            implements ProtobufPrimitivable {

        public static final ProtobufLongArraySimpledCoder instance = new ProtobufLongArraySimpledCoder();

        @Override
        public void convertTo(ProtobufWriter out, long[] values) {
            out.writeLongs(values);
        }

        @Override
        public long[] convertFrom(ProtobufReader in) {
            return in.readLongs();
        }
    }

    public static class ProtobufDoubleArraySimpledCoder extends SimpledCoder<ProtobufReader, ProtobufWriter, double[]>
            implements ProtobufPrimitivable {

        public static final ProtobufDoubleArraySimpledCoder instance = new ProtobufDoubleArraySimpledCoder();

        @Override
        public void convertTo(ProtobufWriter out, double[] values) {
            out.writeDoubles(values);
        }

        @Override
        public double[] convertFrom(ProtobufReader in) {
            return in.readDoubles();
        }
    }

    public static class ProtobufBoolArraySimpledCoder2 extends SimpledCoder<ProtobufReader, ProtobufWriter, Boolean[]>
            implements ProtobufPrimitivable {

        public static final ProtobufBoolArraySimpledCoder2 instance = new ProtobufBoolArraySimpledCoder2();

        @Override
        public void convertTo(ProtobufWriter out, Boolean[] values) {
            out.writeBools(values);
        }

        @Override
        public Boolean[] convertFrom(ProtobufReader in) {
            return Utility.box(in.readBools());
        }
    }

    public static class ProtobufByteArraySimpledCoder2 extends SimpledCoder<ProtobufReader, ProtobufWriter, Byte[]>
            implements ProtobufPrimitivable {

        public static final ProtobufByteArraySimpledCoder2 instance = new ProtobufByteArraySimpledCoder2();

        @Override
        public void convertTo(ProtobufWriter out, Byte[] values) {
            out.writeBytes(values);
        }

        @Override
        public Byte[] convertFrom(ProtobufReader in) {
            return Utility.box(in.readBytes());
        }
    }

    public static class ProtobufCharArraySimpledCoder2 extends SimpledCoder<ProtobufReader, ProtobufWriter, Character[]>
            implements ProtobufPrimitivable {

        public static final ProtobufCharArraySimpledCoder2 instance = new ProtobufCharArraySimpledCoder2();

        @Override
        public void convertTo(ProtobufWriter out, Character[] values) {
            out.writeChars(values);
        }

        @Override
        public Character[] convertFrom(ProtobufReader in) {
            return Utility.box(in.readChars());
        }
    }

    public static class ProtobufShortArraySimpledCoder2 extends SimpledCoder<ProtobufReader, ProtobufWriter, Short[]>
            implements ProtobufPrimitivable {

        public static final ProtobufShortArraySimpledCoder2 instance = new ProtobufShortArraySimpledCoder2();

        @Override
        public void convertTo(ProtobufWriter out, Short[] values) {
            out.writeShorts(values);
        }

        @Override
        public Short[] convertFrom(ProtobufReader in) {
            return Utility.box(in.readShorts());
        }
    }

    public static class ProtobufIntArraySimpledCoder2 extends SimpledCoder<ProtobufReader, ProtobufWriter, Integer[]>
            implements ProtobufPrimitivable {

        public static final ProtobufIntArraySimpledCoder2 instance = new ProtobufIntArraySimpledCoder2();

        @Override
        public void convertTo(ProtobufWriter out, Integer[] values) {
            out.writeInts(values);
        }

        @Override
        public Integer[] convertFrom(ProtobufReader in) {
            return Utility.box(in.readInts());
        }
    }

    public static class ProtobufFloatArraySimpledCoder2 extends SimpledCoder<ProtobufReader, ProtobufWriter, Float[]>
            implements ProtobufPrimitivable {

        public static final ProtobufFloatArraySimpledCoder2 instance = new ProtobufFloatArraySimpledCoder2();

        @Override
        public void convertTo(ProtobufWriter out, Float[] values) {
            out.writeFloats(values);
        }

        @Override
        public Float[] convertFrom(ProtobufReader in) {
            return Utility.box(in.readFloats());
        }
    }

    public static class ProtobufLongArraySimpledCoder2 extends SimpledCoder<ProtobufReader, ProtobufWriter, Long[]>
            implements ProtobufPrimitivable {

        public static final ProtobufLongArraySimpledCoder2 instance = new ProtobufLongArraySimpledCoder2();

        @Override
        public void convertTo(ProtobufWriter out, Long[] values) {
            out.writeLongs(values);
        }

        @Override
        public Long[] convertFrom(ProtobufReader in) {
            return Utility.box(in.readLongs());
        }
    }

    public static class ProtobufDoubleArraySimpledCoder2 extends SimpledCoder<ProtobufReader, ProtobufWriter, Double[]>
            implements ProtobufPrimitivable {

        public static final ProtobufDoubleArraySimpledCoder2 instance = new ProtobufDoubleArraySimpledCoder2();

        @Override
        public void convertTo(ProtobufWriter out, Double[] values) {
            out.writeDoubles(values);
        }

        @Override
        public Double[] convertFrom(ProtobufReader in) {
            return Utility.box(in.readDoubles());
        }
    }

    public static class ProtobufAtomicIntegerArraySimpledCoder
            extends SimpledCoder<ProtobufReader, ProtobufWriter, AtomicInteger[]> implements ProtobufPrimitivable {

        public static final ProtobufAtomicIntegerArraySimpledCoder instance =
                new ProtobufAtomicIntegerArraySimpledCoder();

        @Override
        public void convertTo(ProtobufWriter out, AtomicInteger[] values) {
            out.writeAtomicIntegers(values);
        }

        @Override
        public AtomicInteger[] convertFrom(ProtobufReader in) {
            return in.readAtomicIntegers();
        }
    }

    public static class ProtobufAtomicLongArraySimpledCoder
            extends SimpledCoder<ProtobufReader, ProtobufWriter, AtomicLong[]> implements ProtobufPrimitivable {

        public static final ProtobufAtomicLongArraySimpledCoder instance = new ProtobufAtomicLongArraySimpledCoder();

        @Override
        public void convertTo(ProtobufWriter out, AtomicLong[] values) {
            out.writeAtomicLongs(values);
        }

        @Override
        public AtomicLong[] convertFrom(ProtobufReader in) {
            return in.readAtomicLongs();
        }
    }

    public static class ProtobufBoolCollectionSimpledCoder
            extends SimpledCoder<ProtobufReader, ProtobufWriter, Collection<Boolean>> implements ProtobufPrimitivable {

        private final Creator<? extends Collection> creator;

        public ProtobufBoolCollectionSimpledCoder(Creator<? extends Collection> creator) {
            this.creator = creator;
        }

        @Override
        public void convertTo(ProtobufWriter out, Collection<Boolean> values) {
            out.writeBools(values);
        }

        @Override
        public Collection<Boolean> convertFrom(ProtobufReader in) {
            return in.readBools(creator);
        }
    }

    public static class ProtobufByteCollectionSimpledCoder
            extends SimpledCoder<ProtobufReader, ProtobufWriter, Collection<Byte>> implements ProtobufPrimitivable {

        private final Creator<? extends Collection> creator;

        public ProtobufByteCollectionSimpledCoder(Creator<? extends Collection> creator) {
            this.creator = creator;
        }

        @Override
        public void convertTo(ProtobufWriter out, Collection<Byte> values) {
            out.writeBytes(values);
        }

        @Override
        public Collection<Byte> convertFrom(ProtobufReader in) {
            return in.readBytes(creator);
        }
    }

    public static class ProtobufCharCollectionSimpledCoder
            extends SimpledCoder<ProtobufReader, ProtobufWriter, Collection<Character>>
            implements ProtobufPrimitivable {

        private final Creator<? extends Collection> creator;

        public ProtobufCharCollectionSimpledCoder(Creator<? extends Collection> creator) {
            this.creator = creator;
        }

        @Override
        public void convertTo(ProtobufWriter out, Collection<Character> values) {
            out.writeChars(values);
        }

        @Override
        public Collection<Character> convertFrom(ProtobufReader in) {
            return in.readChars(creator);
        }
    }

    public static class ProtobufShortCollectionSimpledCoder
            extends SimpledCoder<ProtobufReader, ProtobufWriter, Collection<Short>> implements ProtobufPrimitivable {

        private final Creator<? extends Collection> creator;

        public ProtobufShortCollectionSimpledCoder(Creator<? extends Collection> creator) {
            this.creator = creator;
        }

        @Override
        public void convertTo(ProtobufWriter out, Collection<Short> values) {
            out.writeShorts(values);
        }

        @Override
        public Collection<Short> convertFrom(ProtobufReader in) {
            return in.readShorts(creator);
        }
    }

    public static class ProtobufIntCollectionSimpledCoder
            extends SimpledCoder<ProtobufReader, ProtobufWriter, Collection<Integer>> implements ProtobufPrimitivable {

        private final Creator<? extends Collection> creator;

        public ProtobufIntCollectionSimpledCoder(Creator<? extends Collection> creator) {
            this.creator = creator;
        }

        @Override
        public void convertTo(ProtobufWriter out, Collection<Integer> values) {
            out.writeInts(values);
        }

        @Override
        public Collection<Integer> convertFrom(ProtobufReader in) {
            return in.readInts(creator);
        }
    }

    public static class ProtobufFloatCollectionSimpledCoder
            extends SimpledCoder<ProtobufReader, ProtobufWriter, Collection<Float>> implements ProtobufPrimitivable {

        private final Creator<? extends Collection> creator;

        public ProtobufFloatCollectionSimpledCoder(Creator<? extends Collection> creator) {
            this.creator = creator;
        }

        @Override
        public void convertTo(ProtobufWriter out, Collection<Float> values) {
            out.writeFloats(values);
        }

        @Override
        public Collection<Float> convertFrom(ProtobufReader in) {
            return in.readFloats(creator);
        }
    }

    public static class ProtobufLongCollectionSimpledCoder
            extends SimpledCoder<ProtobufReader, ProtobufWriter, Collection<Long>> implements ProtobufPrimitivable {

        private final Creator<? extends Collection> creator;

        public ProtobufLongCollectionSimpledCoder(Creator<? extends Collection> creator) {
            this.creator = creator;
        }

        @Override
        public void convertTo(ProtobufWriter out, Collection<Long> values) {
            out.writeLongs(values);
        }

        @Override
        public Collection<Long> convertFrom(ProtobufReader in) {
            return in.readLongs(creator);
        }
    }

    public static class ProtobufDoubleCollectionSimpledCoder
            extends SimpledCoder<ProtobufReader, ProtobufWriter, Collection<Double>> implements ProtobufPrimitivable {

        private final Creator<? extends Collection> creator;

        public ProtobufDoubleCollectionSimpledCoder(Creator<? extends Collection> creator) {
            this.creator = creator;
        }

        @Override
        public void convertTo(ProtobufWriter out, Collection<Double> values) {
            out.writeDoubles(values);
        }

        @Override
        public Collection<Double> convertFrom(ProtobufReader in) {
            return in.readDoubles(creator);
        }
    }

    public static class ProtobufAtomicIntegerCollectionSimpledCoder
            extends SimpledCoder<ProtobufReader, ProtobufWriter, Collection<AtomicInteger>>
            implements ProtobufPrimitivable {

        private final Creator<? extends Collection> creator;

        public ProtobufAtomicIntegerCollectionSimpledCoder(Creator<? extends Collection> creator) {
            this.creator = creator;
        }

        @Override
        public void convertTo(ProtobufWriter out, Collection<AtomicInteger> values) {
            out.writeAtomicIntegers(values);
        }

        @Override
        public Collection<AtomicInteger> convertFrom(ProtobufReader in) {
            return in.readAtomicIntegers(creator);
        }
    }

    public static class ProtobufAtomicLongCollectionSimpledCoder
            extends SimpledCoder<ProtobufReader, ProtobufWriter, Collection<AtomicLong>>
            implements ProtobufPrimitivable {

        private final Creator<? extends Collection> creator;

        public ProtobufAtomicLongCollectionSimpledCoder(Creator<? extends Collection> creator) {
            this.creator = creator;
        }

        @Override
        public void convertTo(ProtobufWriter out, Collection<AtomicLong> values) {
            out.writeAtomicLongs(values);
        }

        @Override
        public Collection<AtomicLong> convertFrom(ProtobufReader in) {
            return in.readAtomicLongs(creator);
        }
    }

    public static class ProtobufBoolStreamSimpledCoder
            extends SimpledCoder<ProtobufReader, ProtobufWriter, Stream<Boolean>> implements ProtobufPrimitivable {

        public static final ProtobufBoolStreamSimpledCoder instance = new ProtobufBoolStreamSimpledCoder();

        @Override
        public void convertTo(ProtobufWriter out, Stream<Boolean> values) {
            out.writeBools(values);
        }

        @Override
        public Stream<Boolean> convertFrom(ProtobufReader in) {
            return in.readBools(LIST_CREATOR).stream();
        }
    }

    public static class ProtobufByteStreamSimpledCoder
            extends SimpledCoder<ProtobufReader, ProtobufWriter, Stream<Byte>> implements ProtobufPrimitivable {

        public static final ProtobufByteStreamSimpledCoder instance = new ProtobufByteStreamSimpledCoder();

        @Override
        public void convertTo(ProtobufWriter out, Stream<Byte> values) {
            out.writeBytes(values);
        }

        @Override
        public Stream<Byte> convertFrom(ProtobufReader in) {
            return in.readBytes(LIST_CREATOR).stream();
        }
    }

    public static class ProtobufCharStreamSimpledCoder
            extends SimpledCoder<ProtobufReader, ProtobufWriter, Stream<Character>> implements ProtobufPrimitivable {

        public static final ProtobufCharStreamSimpledCoder instance = new ProtobufCharStreamSimpledCoder();

        @Override
        public void convertTo(ProtobufWriter out, Stream<Character> values) {
            out.writeChars(values);
        }

        @Override
        public Stream<Character> convertFrom(ProtobufReader in) {
            return in.readChars(LIST_CREATOR).stream();
        }
    }

    public static class ProtobufShortStreamSimpledCoder
            extends SimpledCoder<ProtobufReader, ProtobufWriter, Stream<Short>> implements ProtobufPrimitivable {

        public static final ProtobufShortStreamSimpledCoder instance = new ProtobufShortStreamSimpledCoder();

        @Override
        public void convertTo(ProtobufWriter out, Stream<Short> values) {
            out.writeShorts(values);
        }

        @Override
        public Stream<Short> convertFrom(ProtobufReader in) {
            return in.readShorts(LIST_CREATOR).stream();
        }
    }

    public static class ProtobufIntStreamSimpledCoder
            extends SimpledCoder<ProtobufReader, ProtobufWriter, Stream<Integer>> implements ProtobufPrimitivable {

        public static final ProtobufIntStreamSimpledCoder instance = new ProtobufIntStreamSimpledCoder();

        @Override
        public void convertTo(ProtobufWriter out, Stream<Integer> values) {
            out.writeInts(values);
        }

        @Override
        public Stream<Integer> convertFrom(ProtobufReader in) {
            return in.readInts(LIST_CREATOR).stream();
        }
    }

    public static class ProtobufFloatStreamSimpledCoder
            extends SimpledCoder<ProtobufReader, ProtobufWriter, Stream<Float>> implements ProtobufPrimitivable {

        public static final ProtobufFloatStreamSimpledCoder instance = new ProtobufFloatStreamSimpledCoder();

        @Override
        public void convertTo(ProtobufWriter out, Stream<Float> values) {
            out.writeFloats(values);
        }

        @Override
        public Stream<Float> convertFrom(ProtobufReader in) {
            return in.readFloats(LIST_CREATOR).stream();
        }
    }

    public static class ProtobufLongStreamSimpledCoder
            extends SimpledCoder<ProtobufReader, ProtobufWriter, Stream<Long>> implements ProtobufPrimitivable {

        public static final ProtobufLongStreamSimpledCoder instance = new ProtobufLongStreamSimpledCoder();

        @Override
        public void convertTo(ProtobufWriter out, Stream<Long> values) {
            out.writeLongs(values);
        }

        @Override
        public Stream<Long> convertFrom(ProtobufReader in) {
            return in.readLongs(LIST_CREATOR).stream();
        }
    }

    public static class ProtobufDoubleStreamSimpledCoder
            extends SimpledCoder<ProtobufReader, ProtobufWriter, Stream<Double>> implements ProtobufPrimitivable {

        public static final ProtobufDoubleStreamSimpledCoder instance = new ProtobufDoubleStreamSimpledCoder();

        @Override
        public void convertTo(ProtobufWriter out, Stream<Double> values) {
            out.writeDoubles(values);
        }

        @Override
        public Stream<Double> convertFrom(ProtobufReader in) {
            return in.readDoubles(LIST_CREATOR).stream();
        }
    }

    public static class ProtobufAtomicIntegerStreamSimpledCoder
            extends SimpledCoder<ProtobufReader, ProtobufWriter, Stream<AtomicInteger>>
            implements ProtobufPrimitivable {

        public static final ProtobufAtomicIntegerStreamSimpledCoder instance =
                new ProtobufAtomicIntegerStreamSimpledCoder();

        @Override
        public void convertTo(ProtobufWriter out, Stream<AtomicInteger> values) {
            out.writeAtomicIntegers(values);
        }

        @Override
        public Stream<AtomicInteger> convertFrom(ProtobufReader in) {
            return in.readAtomicIntegers(LIST_CREATOR).stream();
        }
    }

    public static class ProtobufAtomicLongStreamSimpledCoder
            extends SimpledCoder<ProtobufReader, ProtobufWriter, Stream<AtomicLong>> implements ProtobufPrimitivable {

        public static final ProtobufAtomicLongStreamSimpledCoder instance = new ProtobufAtomicLongStreamSimpledCoder();

        @Override
        public void convertTo(ProtobufWriter out, Stream<AtomicLong> values) {
            out.writeAtomicLongs(values);
        }

        @Override
        public Stream<AtomicLong> convertFrom(ProtobufReader in) {
            return in.readAtomicLongs(LIST_CREATOR).stream();
        }
    }
}
