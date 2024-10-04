/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.convert.pb;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Stream;
import org.redkale.convert.SimpledCoder;
import org.redkale.convert.ext.AtomicBooleanSimpledCoder;
import org.redkale.convert.ext.AtomicIntegerSimpledCoder;
import org.redkale.convert.ext.AtomicLongSimpledCoder;
import org.redkale.convert.ext.BigDecimalSimpledCoder;
import org.redkale.convert.ext.BigIntegerSimpledCoder;
import org.redkale.convert.ext.BoolSimpledCoder;
import org.redkale.convert.ext.ByteSimpledCoder;
import org.redkale.convert.ext.CharSequenceSimpledCoder;
import org.redkale.convert.ext.CharSequenceSimpledCoder.StringBuilderSimpledCoder;
import org.redkale.convert.ext.CharSimpledCoder;
import org.redkale.convert.ext.DateSimpledCoder;
import org.redkale.convert.ext.DoubleSimpledCoder;
import org.redkale.convert.ext.DurationSimpledCoder;
import org.redkale.convert.ext.FloatSimpledCoder;
import org.redkale.convert.ext.InetAddressSimpledCoder;
import org.redkale.convert.ext.InetAddressSimpledCoder.InetSocketAddressSimpledCoder;
import org.redkale.convert.ext.InstantSimpledCoder;
import org.redkale.convert.ext.IntSimpledCoder;
import org.redkale.convert.ext.LocalDateSimpledCoder;
import org.redkale.convert.ext.LocalDateTimeSimpledCoder;
import org.redkale.convert.ext.LocalTimeSimpledCoder;
import org.redkale.convert.ext.LongAdderSimpledCoder;
import org.redkale.convert.ext.LongSimpledCoder;
import org.redkale.convert.ext.NumberSimpledCoder;
import org.redkale.convert.ext.ShortSimpledCoder;
import org.redkale.convert.ext.StringSimpledCoder;
import org.redkale.convert.ext.StringWrapperSimpledCoder;
import org.redkale.convert.ext.Uint128SimpledCoder;
import org.redkale.util.*;

/**
 * SimpledCoder子类convertTo方法中都不会执行writeField/writeTag
 *
 * @author zhangjx
 */
public abstract class ProtobufCoders {

    static final Creator<List> LIST_CREATOR = Creator.load(List.class);

    private ProtobufCoders() {
        // do nothing
    }

    // ------------------------------------- boolean -------------------------------------
    public static class ProtobufBoolSimpledCoder extends BoolSimpledCoder<ProtobufReader, ProtobufWriter>
            implements ProtobufPrimitivable<Boolean>, ProtobufEncodeable<ProtobufWriter, Boolean> {

        public static final ProtobufBoolSimpledCoder instance = new ProtobufBoolSimpledCoder();

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, Boolean value) {
            return computeSize(value);
        }

        @Override
        public int computeSize(Boolean value) {
            return value == null ? 0 : 1;
        }

        @Override
        public Type getType() {
            return Boolean.class;
        }

        @Override
        public final Class primitiveType() {
            return boolean.class;
        }
    }

    public static class ProtobufByteSimpledCoder extends ByteSimpledCoder<ProtobufReader, ProtobufWriter>
            implements ProtobufPrimitivable<Byte>, ProtobufEncodeable<ProtobufWriter, Byte> {

        public static final ProtobufByteSimpledCoder instance = new ProtobufByteSimpledCoder();

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, Byte value) {
            return computeSize(value);
        }

        @Override
        public int computeSize(Byte value) {
            return value == null ? 0 : 1;
        }

        @Override
        public Type getType() {
            return Byte.class;
        }

        @Override
        public final Class primitiveType() {
            return byte.class;
        }
    }

    public static class ProtobufCharSimpledCoder extends CharSimpledCoder<ProtobufReader, ProtobufWriter>
            implements ProtobufPrimitivable<Character>, ProtobufEncodeable<ProtobufWriter, Character> {

        public static final ProtobufCharSimpledCoder instance = new ProtobufCharSimpledCoder();

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, Character value) {
            return computeSize(value);
        }

        @Override
        public int computeSize(Character value) {
            return value == null ? 0 : ProtobufFactory.computeSInt32SizeNoTag(value);
        }

        @Override
        public Type getType() {
            return Character.class;
        }

        @Override
        public final Class primitiveType() {
            return char.class;
        }
    }

    public static class ProtobufShortSimpledCoder extends ShortSimpledCoder<ProtobufReader, ProtobufWriter>
            implements ProtobufPrimitivable<Short>, ProtobufEncodeable<ProtobufWriter, Short> {

        public static final ProtobufShortSimpledCoder instance = new ProtobufShortSimpledCoder();

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, Short value) {
            return computeSize(value);
        }

        @Override
        public int computeSize(Short value) {
            return value == null ? 0 : ProtobufFactory.computeSInt32SizeNoTag(value);
        }

        @Override
        public Type getType() {
            return Short.class;
        }

        @Override
        public final Class primitiveType() {
            return short.class;
        }
    }

    public static class ProtobufIntSimpledCoder extends IntSimpledCoder<ProtobufReader, ProtobufWriter>
            implements ProtobufPrimitivable<Integer>, ProtobufEncodeable<ProtobufWriter, Integer> {

        public static final ProtobufIntSimpledCoder instance = new ProtobufIntSimpledCoder();

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, Integer value) {
            return computeSize(value);
        }

        @Override
        public int computeSize(Integer value) {
            return value == null ? 0 : ProtobufFactory.computeSInt32SizeNoTag(value);
        }

        @Override
        public Type getType() {
            return Integer.class;
        }

        @Override
        public final Class primitiveType() {
            return int.class;
        }
    }

    public static class ProtobufFloatSimpledCoder extends FloatSimpledCoder<ProtobufReader, ProtobufWriter>
            implements ProtobufPrimitivable<Float>, ProtobufEncodeable<ProtobufWriter, Float> {

        public static final ProtobufFloatSimpledCoder instance = new ProtobufFloatSimpledCoder();

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, Float value) {
            return computeSize(value);
        }

        @Override
        public int computeSize(Float value) {
            return value == null ? 0 : 4;
        }

        @Override
        public Type getType() {
            return Float.class;
        }

        @Override
        public final Class primitiveType() {
            return float.class;
        }
    }

    public static class ProtobufLongSimpledCoder extends LongSimpledCoder<ProtobufReader, ProtobufWriter>
            implements ProtobufPrimitivable<Long>, ProtobufEncodeable<ProtobufWriter, Long> {

        public static final ProtobufLongSimpledCoder instance = new ProtobufLongSimpledCoder();

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, Long value) {
            return computeSize(value);
        }

        @Override
        public int computeSize(Long value) {
            return value == null ? 0 : ProtobufFactory.computeSInt64SizeNoTag(value);
        }

        @Override
        public Type getType() {
            return Long.class;
        }

        @Override
        public final Class primitiveType() {
            return long.class;
        }
    }

    public static class ProtobufDoubleSimpledCoder extends DoubleSimpledCoder<ProtobufReader, ProtobufWriter>
            implements ProtobufPrimitivable<Double>, ProtobufEncodeable<ProtobufWriter, Double> {

        public static final ProtobufDoubleSimpledCoder instance = new ProtobufDoubleSimpledCoder();

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, Double value) {
            return computeSize(value);
        }

        @Override
        public int computeSize(Double value) {
            return value == null ? 0 : 8;
        }

        @Override
        public Type getType() {
            return Double.class;
        }

        @Override
        public final Class primitiveType() {
            return double.class;
        }
    }

    public static class ProtobufStringSimpledCoder extends StringSimpledCoder<ProtobufReader, ProtobufWriter>
            implements ProtobufEncodeable<ProtobufWriter, String> {

        public static final ProtobufStringSimpledCoder instance = new ProtobufStringSimpledCoder();

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, String value) {
            if (value == null || value.isEmpty()) {
                return 0;
            }
            int len = Utility.encodeUTF8Length(value);
            return ProtobufFactory.computeSInt32SizeNoTag(len) + len;
        }

        @Override
        public Type getType() {
            return String.class;
        }
    }

    // ------------------------------------- simple object -------------------------------------
    public static class ProtobufNumberSimpledCoder extends NumberSimpledCoder<ProtobufReader, ProtobufWriter>
            implements ProtobufPrimitivable<Number>, ProtobufEncodeable<ProtobufWriter, Number> {

        public static final ProtobufNumberSimpledCoder instance = new ProtobufNumberSimpledCoder();

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, Number value) {
            return computeSize(value);
        }

        @Override
        public int computeSize(Number value) {
            return ProtobufLongSimpledCoder.instance.computeSize(value.longValue());
        }

        @Override
        public Type getType() {
            return Number.class;
        }

        @Override
        public final Class primitiveType() {
            return long.class;
        }
    }

    public static class ProtobufStringWrapperSimpledCoder
            extends StringWrapperSimpledCoder<ProtobufReader, ProtobufWriter>
            implements ProtobufEncodeable<ProtobufWriter, StringWrapper> {

        public static final ProtobufStringWrapperSimpledCoder instance = new ProtobufStringWrapperSimpledCoder();

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, StringWrapper value) {
            return ProtobufStringSimpledCoder.instance.computeSize(
                    out, tagSize, value == null ? null : value.getValue());
        }

        @Override
        public Type getType() {
            return StringWrapper.class;
        }
    }

    public static class ProtobufCharSequenceSimpledCoder
            extends CharSequenceSimpledCoder<ProtobufReader, ProtobufWriter>
            implements ProtobufEncodeable<ProtobufWriter, CharSequence> {

        public static final ProtobufCharSequenceSimpledCoder instance = new ProtobufCharSequenceSimpledCoder();

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, CharSequence value) {
            return ProtobufStringSimpledCoder.instance.computeSize(
                    out, tagSize, value == null ? null : value.toString());
        }

        @Override
        public Type getType() {
            return CharSequence.class;
        }
    }

    public static class ProtobufStringBuilderSimpledCoder
            extends StringBuilderSimpledCoder<ProtobufReader, ProtobufWriter>
            implements ProtobufEncodeable<ProtobufWriter, StringBuilder> {

        public static final ProtobufStringBuilderSimpledCoder instance = new ProtobufStringBuilderSimpledCoder();

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, StringBuilder value) {
            return ProtobufStringSimpledCoder.instance.computeSize(
                    out, tagSize, value == null ? null : value.toString());
        }

        @Override
        public Type getType() {
            return StringBuilder.class;
        }
    }

    public static class ProtobufDateSimpledCoder extends DateSimpledCoder<ProtobufReader, ProtobufWriter>
            implements ProtobufPrimitivable<java.util.Date>, ProtobufEncodeable<ProtobufWriter, java.util.Date> {

        public static final ProtobufDateSimpledCoder instance = new ProtobufDateSimpledCoder();

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, java.util.Date value) {
            return computeSize(value);
        }

        @Override
        public int computeSize(java.util.Date value) {
            return ProtobufLongSimpledCoder.instance.computeSize(value.getTime());
        }

        @Override
        public Type getType() {
            return java.util.Date.class;
        }

        @Override
        public final Class primitiveType() {
            return long.class;
        }
    }

    public static class ProtobufInstantSimpledCoder extends InstantSimpledCoder<ProtobufReader, ProtobufWriter>
            implements ProtobufPrimitivable<java.time.Instant>, ProtobufEncodeable<ProtobufWriter, java.time.Instant> {

        public static final ProtobufInstantSimpledCoder instance = new ProtobufInstantSimpledCoder();

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, java.time.Instant value) {
            return computeSize(value);
        }

        @Override
        public int computeSize(java.time.Instant value) {
            return ProtobufLongSimpledCoder.instance.computeSize(value == null ? null : value.toEpochMilli());
        }

        @Override
        public Type getType() {
            return java.time.Instant.class;
        }

        @Override
        public final Class primitiveType() {
            return long.class;
        }
    }

    public static class ProtobufLocalDateSimpledCoder extends LocalDateSimpledCoder<ProtobufReader, ProtobufWriter>
            implements ProtobufPrimitivable<java.time.LocalDate>,
                    ProtobufEncodeable<ProtobufWriter, java.time.LocalDate> {

        public static final ProtobufLocalDateSimpledCoder instance = new ProtobufLocalDateSimpledCoder();

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, java.time.LocalDate value) {
            return computeSize(value);
        }

        @Override
        public int computeSize(java.time.LocalDate value) {
            return ProtobufIntSimpledCoder.instance.computeSize(
                    value == null
                            ? null
                            : (value.getYear() * 100_00 + value.getMonthValue() * 100 + value.getDayOfMonth()));
        }

        @Override
        public Type getType() {
            return java.time.LocalDate.class;
        }

        @Override
        public final Class primitiveType() {
            return int.class;
        }
    }

    public static class ProtobufLocalTimeSimpledCoder extends LocalTimeSimpledCoder<ProtobufReader, ProtobufWriter>
            implements ProtobufPrimitivable<java.time.LocalTime>,
                    ProtobufEncodeable<ProtobufWriter, java.time.LocalTime> {

        public static final ProtobufLocalTimeSimpledCoder instance = new ProtobufLocalTimeSimpledCoder();

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, java.time.LocalTime value) {
            return computeSize(value);
        }

        @Override
        public int computeSize(java.time.LocalTime value) {
            return ProtobufLongSimpledCoder.instance.computeSize(value == null ? null : value.toNanoOfDay());
        }

        @Override
        public Type getType() {
            return java.time.LocalTime.class;
        }

        @Override
        public final Class primitiveType() {
            return long.class;
        }
    }

    public static class ProtobufLocalDateTimeSimpledCoder
            extends LocalDateTimeSimpledCoder<ProtobufReader, ProtobufWriter>
            implements ProtobufEncodeable<ProtobufWriter, java.time.LocalDateTime> {

        public static final ProtobufLocalDateTimeSimpledCoder instance = new ProtobufLocalDateTimeSimpledCoder();

        public ProtobufLocalDateTimeSimpledCoder() {
            super(ProtobufByteArraySimpledCoder.instance);
        }

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, java.time.LocalDateTime value) {
            return value == null ? 0 : (ProtobufFactory.computeSInt64SizeNoTag(12) + 12);
        }

        @Override
        public Type getType() {
            return java.time.LocalDateTime.class;
        }
    }

    public static class ProtobufDurationSimpledCoder extends DurationSimpledCoder<ProtobufReader, ProtobufWriter>
            implements ProtobufPrimitivable<java.time.Duration>,
                    ProtobufEncodeable<ProtobufWriter, java.time.Duration> {

        public static final ProtobufDurationSimpledCoder instance = new ProtobufDurationSimpledCoder();

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, java.time.Duration value) {
            return computeSize(value);
        }

        @Override
        public int computeSize(java.time.Duration value) {
            return ProtobufLongSimpledCoder.instance.computeSize(value == null ? null : value.toNanos());
        }

        @Override
        public Type getType() {
            return java.time.Duration.class;
        }

        @Override
        public final Class primitiveType() {
            return long.class;
        }
    }

    public static class ProtobufAtomicBooleanSimpledCoder
            extends AtomicBooleanSimpledCoder<ProtobufReader, ProtobufWriter>
            implements ProtobufPrimitivable<AtomicBoolean>, ProtobufEncodeable<ProtobufWriter, AtomicBoolean> {

        public static final ProtobufAtomicBooleanSimpledCoder instance = new ProtobufAtomicBooleanSimpledCoder();

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, AtomicBoolean value) {
            return computeSize(value);
        }

        @Override
        public int computeSize(AtomicBoolean value) {
            return value == null ? 0 : 1;
        }

        @Override
        public Type getType() {
            return AtomicBoolean.class;
        }

        @Override
        public final Class primitiveType() {
            return boolean.class;
        }
    }

    public static class ProtobufAtomicIntegerSimpledCoder
            extends AtomicIntegerSimpledCoder<ProtobufReader, ProtobufWriter>
            implements ProtobufPrimitivable<AtomicInteger>, ProtobufEncodeable<ProtobufWriter, AtomicInteger> {

        public static final ProtobufAtomicIntegerSimpledCoder instance = new ProtobufAtomicIntegerSimpledCoder();

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, AtomicInteger value) {
            return computeSize(value);
        }

        @Override
        public int computeSize(AtomicInteger value) {
            return ProtobufIntSimpledCoder.instance.computeSize(value == null ? null : value.get());
        }

        @Override
        public Type getType() {
            return AtomicInteger.class;
        }

        @Override
        public final Class primitiveType() {
            return int.class;
        }
    }

    public static class ProtobufAtomicLongSimpledCoder extends AtomicLongSimpledCoder<ProtobufReader, ProtobufWriter>
            implements ProtobufPrimitivable<AtomicLong>, ProtobufEncodeable<ProtobufWriter, AtomicLong> {

        public static final ProtobufAtomicLongSimpledCoder instance = new ProtobufAtomicLongSimpledCoder();

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, AtomicLong value) {
            return computeSize(value);
        }

        @Override
        public int computeSize(AtomicLong value) {
            return ProtobufLongSimpledCoder.instance.computeSize(value == null ? null : value.get());
        }

        @Override
        public Type getType() {
            return AtomicLong.class;
        }

        @Override
        public final Class primitiveType() {
            return long.class;
        }
    }

    public static class ProtobufBigIntegerSimpledCoder extends BigIntegerSimpledCoder<ProtobufReader, ProtobufWriter>
            implements ProtobufEncodeable<ProtobufWriter, BigInteger> {

        public static final ProtobufBigIntegerSimpledCoder instance = new ProtobufBigIntegerSimpledCoder();

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, BigInteger value) {
            if (value == null) {
                return 0;
            }
            byte[] bs = value.toByteArray();
            return ProtobufByteArraySimpledCoder.instance.computeSize(out, tagSize, bs);
        }

        @Override
        public Type getType() {
            return BigInteger.class;
        }
    }

    public static class ProtobufBigDecimalSimpledCoder extends BigDecimalSimpledCoder<ProtobufReader, ProtobufWriter>
            implements ProtobufEncodeable<ProtobufWriter, BigDecimal> {

        public static final ProtobufBigDecimalSimpledCoder instance = new ProtobufBigDecimalSimpledCoder();

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, BigDecimal value) {
            if (value == null) {
                return 0;
            }
            return ProtobufStringSimpledCoder.instance.computeSize(out, tagSize, value.toString());
        }

        @Override
        public Type getType() {
            return BigDecimal.class;
        }
    }

    public static class ProtobufInetAddressSimpledCoder extends InetAddressSimpledCoder<ProtobufReader, ProtobufWriter>
            implements ProtobufEncodeable<ProtobufWriter, InetAddress> {

        public static final ProtobufInetAddressSimpledCoder instance = new ProtobufInetAddressSimpledCoder();

        public ProtobufInetAddressSimpledCoder() {
            super(ProtobufByteArraySimpledCoder.instance);
        }

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, InetAddress value) {
            if (value == null) {
                return 0;
            }
            byte[] bs = value.getAddress();
            return ProtobufByteArraySimpledCoder.instance.computeSize(out, tagSize, bs);
        }

        @Override
        public Type getType() {
            return InetAddress.class;
        }
    }

    public static class ProtobufInetSocketAddressSimpledCoder
            extends InetSocketAddressSimpledCoder<ProtobufReader, ProtobufWriter>
            implements ProtobufEncodeable<ProtobufWriter, InetSocketAddress> {

        public static final ProtobufInetSocketAddressSimpledCoder instance =
                new ProtobufInetSocketAddressSimpledCoder();

        public ProtobufInetSocketAddressSimpledCoder() {
            super(ProtobufByteArraySimpledCoder.instance);
        }

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, InetSocketAddress value) {
            if (value == null) {
                return 0;
            }
            byte[] bs = value.getAddress().getAddress();
            return bs.length + 2; // port固定2字节
        }

        @Override
        public Type getType() {
            return InetSocketAddress.class;
        }
    }

    public static class ProtobufLongAdderSimpledCoder extends LongAdderSimpledCoder<ProtobufReader, ProtobufWriter>
            implements ProtobufPrimitivable<LongAdder>, ProtobufEncodeable<ProtobufWriter, LongAdder> {

        public static final ProtobufLongAdderSimpledCoder instance = new ProtobufLongAdderSimpledCoder();

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, LongAdder value) {
            return computeSize(value);
        }

        @Override
        public int computeSize(LongAdder value) {
            return ProtobufLongSimpledCoder.instance.computeSize(value == null ? null : value.longValue());
        }

        @Override
        public Type getType() {
            return LongAdder.class;
        }

        @Override
        public final Class primitiveType() {
            return long.class;
        }
    }

    public static class ProtobufUint128SimpledCoder extends Uint128SimpledCoder<ProtobufReader, ProtobufWriter>
            implements ProtobufEncodeable<ProtobufWriter, Uint128> {

        public static final ProtobufUint128SimpledCoder instance = new ProtobufUint128SimpledCoder();

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, Uint128 value) {
            if (value == null) {
                return 0;
            }
            byte[] bs = value.getBytes();
            return ProtobufByteArraySimpledCoder.instance.computeSize(out, tagSize, bs);
        }

        @Override
        public Type getType() {
            return Uint128.class;
        }
    }

    // ------------------------------------- boolean[] -------------------------------------
    public static class ProtobufBoolArraySimpledCoder extends SimpledCoder<ProtobufReader, ProtobufWriter, boolean[]>
            implements ProtobufEncodeable<ProtobufWriter, boolean[]> {

        public static final ProtobufBoolArraySimpledCoder instance = new ProtobufBoolArraySimpledCoder();

        @Override
        public void convertTo(ProtobufWriter out, boolean[] values) {
            out.writeBools(values);
        }

        @Override
        public boolean[] convertFrom(ProtobufReader in) {
            return in.readBools();
        }

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, boolean[] value) {
            return value == null ? 0 : value.length;
        }
    }

    public static class ProtobufByteArraySimpledCoder extends SimpledCoder<ProtobufReader, ProtobufWriter, byte[]>
            implements ProtobufEncodeable<ProtobufWriter, byte[]> {

        public static final ProtobufByteArraySimpledCoder instance = new ProtobufByteArraySimpledCoder();

        @Override
        public void convertTo(ProtobufWriter out, byte[] values) {
            out.writeBytes(values);
        }

        @Override
        public byte[] convertFrom(ProtobufReader in) {
            return in.readBytes();
        }

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, byte[] value) {
            return value == null ? 0 : value.length;
        }
    }

    public static class ProtobufCharArraySimpledCoder extends SimpledCoder<ProtobufReader, ProtobufWriter, char[]>
            implements ProtobufEncodeable<ProtobufWriter, char[]> {

        public static final ProtobufCharArraySimpledCoder instance = new ProtobufCharArraySimpledCoder();

        @Override
        public void convertTo(ProtobufWriter out, char[] values) {
            out.writeChars(values);
        }

        @Override
        public char[] convertFrom(ProtobufReader in) {
            return in.readChars();
        }

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, char[] value) {
            if (value == null || value.length == 0) {
                return 0;
            }
            int len = 0;
            for (char item : value) {
                len += ProtobufFactory.computeSInt32SizeNoTag(item);
            }
            return len;
        }
    }

    public static class ProtobufShortArraySimpledCoder extends SimpledCoder<ProtobufReader, ProtobufWriter, short[]>
            implements ProtobufEncodeable<ProtobufWriter, short[]> {

        public static final ProtobufShortArraySimpledCoder instance = new ProtobufShortArraySimpledCoder();

        @Override
        public void convertTo(ProtobufWriter out, short[] values) {
            out.writeShorts(values);
        }

        @Override
        public short[] convertFrom(ProtobufReader in) {
            return in.readShorts();
        }

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, short[] value) {
            if (value == null || value.length == 0) {
                return 0;
            }
            int len = 0;
            for (short item : value) {
                len += ProtobufFactory.computeSInt32SizeNoTag(item);
            }
            return len;
        }
    }

    public static class ProtobufIntArraySimpledCoder extends SimpledCoder<ProtobufReader, ProtobufWriter, int[]>
            implements ProtobufEncodeable<ProtobufWriter, int[]> {

        public static final ProtobufIntArraySimpledCoder instance = new ProtobufIntArraySimpledCoder();

        @Override
        public void convertTo(ProtobufWriter out, int[] values) {
            out.writeInts(values);
        }

        @Override
        public int[] convertFrom(ProtobufReader in) {
            return in.readInts();
        }

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, int[] value) {
            if (value == null || value.length == 0) {
                return 0;
            }
            int len = 0;
            for (int item : value) {
                len += ProtobufFactory.computeSInt32SizeNoTag(item);
            }
            return len;
        }
    }

    public static class ProtobufFloatArraySimpledCoder extends SimpledCoder<ProtobufReader, ProtobufWriter, float[]>
            implements ProtobufEncodeable<ProtobufWriter, float[]> {

        public static final ProtobufFloatArraySimpledCoder instance = new ProtobufFloatArraySimpledCoder();

        @Override
        public void convertTo(ProtobufWriter out, float[] values) {
            out.writeFloats(values);
        }

        @Override
        public float[] convertFrom(ProtobufReader in) {
            return in.readFloats();
        }

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, float[] value) {
            return value == null ? 0 : value.length * 4;
        }
    }

    public static class ProtobufLongArraySimpledCoder extends SimpledCoder<ProtobufReader, ProtobufWriter, long[]>
            implements ProtobufEncodeable<ProtobufWriter, long[]> {

        public static final ProtobufLongArraySimpledCoder instance = new ProtobufLongArraySimpledCoder();

        @Override
        public void convertTo(ProtobufWriter out, long[] values) {
            out.writeLongs(values);
        }

        @Override
        public long[] convertFrom(ProtobufReader in) {
            return in.readLongs();
        }

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, long[] value) {
            if (value == null || value.length == 0) {
                return 0;
            }
            int len = 0;
            for (long item : value) {
                len += ProtobufFactory.computeSInt64SizeNoTag(item);
            }
            return len;
        }
    }

    public static class ProtobufDoubleArraySimpledCoder extends SimpledCoder<ProtobufReader, ProtobufWriter, double[]>
            implements ProtobufEncodeable<ProtobufWriter, double[]> {

        public static final ProtobufDoubleArraySimpledCoder instance = new ProtobufDoubleArraySimpledCoder();

        @Override
        public void convertTo(ProtobufWriter out, double[] values) {
            out.writeDoubles(values);
        }

        @Override
        public double[] convertFrom(ProtobufReader in) {
            return in.readDoubles();
        }

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, double[] value) {
            return value == null ? 0 : value.length * 8;
        }
    }

    // ------------------------------------- Boolean[] -------------------------------------
    public static class ProtobufBoolArraySimpledCoder2 extends SimpledCoder<ProtobufReader, ProtobufWriter, Boolean[]>
            implements ProtobufEncodeable<ProtobufWriter, Boolean[]> {

        public static final ProtobufBoolArraySimpledCoder2 instance = new ProtobufBoolArraySimpledCoder2();

        @Override
        public void convertTo(ProtobufWriter out, Boolean[] values) {
            out.writeBools(values);
        }

        @Override
        public Boolean[] convertFrom(ProtobufReader in) {
            return Utility.box(in.readBools());
        }

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, Boolean[] value) {
            return value == null ? 0 : value.length;
        }
    }

    public static class ProtobufByteArraySimpledCoder2 extends SimpledCoder<ProtobufReader, ProtobufWriter, Byte[]>
            implements ProtobufEncodeable<ProtobufWriter, Byte[]> {

        public static final ProtobufByteArraySimpledCoder2 instance = new ProtobufByteArraySimpledCoder2();

        @Override
        public void convertTo(ProtobufWriter out, Byte[] values) {
            out.writeBytes(values);
        }

        @Override
        public Byte[] convertFrom(ProtobufReader in) {
            return Utility.box(in.readBytes());
        }

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, Byte[] value) {
            return value == null ? 0 : value.length;
        }
    }

    public static class ProtobufCharArraySimpledCoder2 extends SimpledCoder<ProtobufReader, ProtobufWriter, Character[]>
            implements ProtobufEncodeable<ProtobufWriter, Character[]> {

        public static final ProtobufCharArraySimpledCoder2 instance = new ProtobufCharArraySimpledCoder2();

        @Override
        public void convertTo(ProtobufWriter out, Character[] values) {
            out.writeChars(values);
        }

        @Override
        public Character[] convertFrom(ProtobufReader in) {
            return Utility.box(in.readChars());
        }

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, Character[] value) {
            if (value == null || value.length == 0) {
                return 0;
            }
            int len = 0;
            for (Character item : value) {
                len += ProtobufFactory.computeSInt32SizeNoTag(item == null ? 0 : item);
            }
            return len;
        }
    }

    public static class ProtobufShortArraySimpledCoder2 extends SimpledCoder<ProtobufReader, ProtobufWriter, Short[]>
            implements ProtobufEncodeable<ProtobufWriter, Short[]> {

        public static final ProtobufShortArraySimpledCoder2 instance = new ProtobufShortArraySimpledCoder2();

        @Override
        public void convertTo(ProtobufWriter out, Short[] values) {
            out.writeShorts(values);
        }

        @Override
        public Short[] convertFrom(ProtobufReader in) {
            return Utility.box(in.readShorts());
        }

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, Short[] value) {
            if (value == null || value.length == 0) {
                return 0;
            }
            int len = 0;
            for (Short item : value) {
                len += ProtobufFactory.computeSInt32SizeNoTag(item == null ? 0 : item);
            }
            return len;
        }
    }

    public static class ProtobufIntArraySimpledCoder2 extends SimpledCoder<ProtobufReader, ProtobufWriter, Integer[]>
            implements ProtobufEncodeable<ProtobufWriter, Integer[]> {

        public static final ProtobufIntArraySimpledCoder2 instance = new ProtobufIntArraySimpledCoder2();

        @Override
        public void convertTo(ProtobufWriter out, Integer[] values) {
            out.writeInts(values);
        }

        @Override
        public Integer[] convertFrom(ProtobufReader in) {
            return Utility.box(in.readInts());
        }

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, Integer[] value) {
            if (value == null || value.length == 0) {
                return 0;
            }
            int len = 0;
            for (Integer item : value) {
                len += ProtobufFactory.computeSInt32SizeNoTag(item == null ? 0 : item);
            }
            return len;
        }
    }

    public static class ProtobufFloatArraySimpledCoder2 extends SimpledCoder<ProtobufReader, ProtobufWriter, Float[]>
            implements ProtobufEncodeable<ProtobufWriter, Float[]> {

        public static final ProtobufFloatArraySimpledCoder2 instance = new ProtobufFloatArraySimpledCoder2();

        @Override
        public void convertTo(ProtobufWriter out, Float[] values) {
            out.writeFloats(values);
        }

        @Override
        public Float[] convertFrom(ProtobufReader in) {
            return Utility.box(in.readFloats());
        }

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, Float[] value) {
            return value == null ? 0 : value.length * 4;
        }
    }

    public static class ProtobufLongArraySimpledCoder2 extends SimpledCoder<ProtobufReader, ProtobufWriter, Long[]>
            implements ProtobufEncodeable<ProtobufWriter, Long[]> {

        public static final ProtobufLongArraySimpledCoder2 instance = new ProtobufLongArraySimpledCoder2();

        @Override
        public void convertTo(ProtobufWriter out, Long[] values) {
            out.writeLongs(values);
        }

        @Override
        public Long[] convertFrom(ProtobufReader in) {
            return Utility.box(in.readLongs());
        }

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, Long[] value) {
            if (value == null || value.length == 0) {
                return 0;
            }
            int len = 0;
            for (Long item : value) {
                len += ProtobufFactory.computeSInt64SizeNoTag(item == null ? 0 : item);
            }
            return len;
        }
    }

    public static class ProtobufDoubleArraySimpledCoder2 extends SimpledCoder<ProtobufReader, ProtobufWriter, Double[]>
            implements ProtobufEncodeable<ProtobufWriter, Double[]> {

        public static final ProtobufDoubleArraySimpledCoder2 instance = new ProtobufDoubleArraySimpledCoder2();

        @Override
        public void convertTo(ProtobufWriter out, Double[] values) {
            out.writeDoubles(values);
        }

        @Override
        public Double[] convertFrom(ProtobufReader in) {
            return Utility.box(in.readDoubles());
        }

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, Double[] value) {
            return value == null ? 0 : value.length * 8;
        }
    }

    // ------------------------------------- Collection<Boolean> -------------------------------------
    public static class ProtobufBoolCollectionSimpledCoder
            extends SimpledCoder<ProtobufReader, ProtobufWriter, Collection<Boolean>>
            implements ProtobufEncodeable<ProtobufWriter, Collection<Boolean>> {

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

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, Collection<Boolean> value) {
            return value == null ? 0 : value.size();
        }
    }

    public static class ProtobufByteCollectionSimpledCoder
            extends SimpledCoder<ProtobufReader, ProtobufWriter, Collection<Byte>>
            implements ProtobufEncodeable<ProtobufWriter, Collection<Byte>> {

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

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, Collection<Byte> value) {
            return value == null ? 0 : value.size();
        }
    }

    public static class ProtobufCharCollectionSimpledCoder
            extends SimpledCoder<ProtobufReader, ProtobufWriter, Collection<Character>>
            implements ProtobufEncodeable<ProtobufWriter, Collection<Character>> {

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

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, Collection<Character> value) {
            if (value == null || value.isEmpty()) {
                return 0;
            }
            int len = 0;
            for (Character item : value) {
                len += ProtobufFactory.computeSInt32SizeNoTag(item == null ? 0 : item);
            }
            return len;
        }
    }

    public static class ProtobufShortCollectionSimpledCoder
            extends SimpledCoder<ProtobufReader, ProtobufWriter, Collection<Short>>
            implements ProtobufEncodeable<ProtobufWriter, Collection<Short>> {

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

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, Collection<Short> value) {
            if (value == null || value.isEmpty()) {
                return 0;
            }
            int len = 0;
            for (Short item : value) {
                len += ProtobufFactory.computeSInt32SizeNoTag(item == null ? 0 : item);
            }
            return len;
        }
    }

    public static class ProtobufIntCollectionSimpledCoder
            extends SimpledCoder<ProtobufReader, ProtobufWriter, Collection<Integer>>
            implements ProtobufEncodeable<ProtobufWriter, Collection<Integer>> {

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

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, Collection<Integer> value) {
            if (value == null || value.isEmpty()) {
                return 0;
            }
            int len = 0;
            for (Integer item : value) {
                len += ProtobufFactory.computeSInt32SizeNoTag(item == null ? 0 : item);
            }
            return len;
        }
    }

    public static class ProtobufFloatCollectionSimpledCoder
            extends SimpledCoder<ProtobufReader, ProtobufWriter, Collection<Float>>
            implements ProtobufEncodeable<ProtobufWriter, Collection<Float>> {

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

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, Collection<Float> value) {
            return value == null ? 0 : value.size() * 4;
        }
    }

    public static class ProtobufLongCollectionSimpledCoder
            extends SimpledCoder<ProtobufReader, ProtobufWriter, Collection<Long>>
            implements ProtobufEncodeable<ProtobufWriter, Collection<Long>> {

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

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, Collection<Long> value) {
            if (value == null || value.isEmpty()) {
                return 0;
            }
            int len = 0;
            for (Long item : value) {
                len += ProtobufFactory.computeSInt64SizeNoTag(item == null ? 0 : item);
            }
            return len;
        }
    }

    public static class ProtobufDoubleCollectionSimpledCoder
            extends SimpledCoder<ProtobufReader, ProtobufWriter, Collection<Double>>
            implements ProtobufEncodeable<ProtobufWriter, Collection<Double>> {

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

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, Collection<Double> value) {
            return value == null ? 0 : value.size() * 8;
        }
    }

    // ------------------------------------- Stream<Boolean> -------------------------------------
    public static class ProtobufBoolStreamSimpledCoder
            extends SimpledCoder<ProtobufReader, ProtobufWriter, Stream<Boolean>>
            implements ProtobufEncodeable<ProtobufWriter, Stream<Boolean>> {

        public static final ProtobufBoolStreamSimpledCoder instance = new ProtobufBoolStreamSimpledCoder();

        @Override
        public void convertTo(ProtobufWriter out, Stream<Boolean> values) {
            out.writeBools(values);
        }

        @Override
        public Stream<Boolean> convertFrom(ProtobufReader in) {
            return in.readBools(LIST_CREATOR).stream();
        }

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, Stream<Boolean> value) {
            return value == null ? 0 : (int) value.count();
        }
    }

    public static class ProtobufByteStreamSimpledCoder
            extends SimpledCoder<ProtobufReader, ProtobufWriter, Stream<Byte>>
            implements ProtobufEncodeable<ProtobufWriter, Stream<Byte>> {

        public static final ProtobufByteStreamSimpledCoder instance = new ProtobufByteStreamSimpledCoder();

        @Override
        public void convertTo(ProtobufWriter out, Stream<Byte> values) {
            out.writeBytes(values);
        }

        @Override
        public Stream<Byte> convertFrom(ProtobufReader in) {
            return in.readBytes(LIST_CREATOR).stream();
        }

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, Stream<Byte> value) {
            return value == null ? 0 : (int) value.count();
        }
    }

    public static class ProtobufCharStreamSimpledCoder
            extends SimpledCoder<ProtobufReader, ProtobufWriter, Stream<Character>>
            implements ProtobufEncodeable<ProtobufWriter, Stream<Character>> {

        public static final ProtobufCharStreamSimpledCoder instance = new ProtobufCharStreamSimpledCoder();

        @Override
        public void convertTo(ProtobufWriter out, Stream<Character> values) {
            out.writeChars(values);
        }

        @Override
        public Stream<Character> convertFrom(ProtobufReader in) {
            return in.readChars(LIST_CREATOR).stream();
        }

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, Stream<Character> value) {
            if (value == null) {
                return 0;
            }
            int len = 0;
            for (Object item : value.toArray()) {
                len += ProtobufFactory.computeSInt32SizeNoTag(item == null ? 0 : (Character) item);
            }
            return len;
        }
    }

    public static class ProtobufShortStreamSimpledCoder
            extends SimpledCoder<ProtobufReader, ProtobufWriter, Stream<Short>>
            implements ProtobufEncodeable<ProtobufWriter, Stream<Short>> {

        public static final ProtobufShortStreamSimpledCoder instance = new ProtobufShortStreamSimpledCoder();

        @Override
        public void convertTo(ProtobufWriter out, Stream<Short> values) {
            out.writeShorts(values);
        }

        @Override
        public Stream<Short> convertFrom(ProtobufReader in) {
            return in.readShorts(LIST_CREATOR).stream();
        }

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, Stream<Short> value) {
            if (value == null) {
                return 0;
            }
            int len = 0;
            for (Object item : value.toArray()) {
                len += ProtobufFactory.computeSInt32SizeNoTag(item == null ? 0 : (Short) item);
            }
            return len;
        }
    }

    public static class ProtobufIntStreamSimpledCoder
            extends SimpledCoder<ProtobufReader, ProtobufWriter, Stream<Integer>>
            implements ProtobufEncodeable<ProtobufWriter, Stream<Integer>> {

        public static final ProtobufIntStreamSimpledCoder instance = new ProtobufIntStreamSimpledCoder();

        @Override
        public void convertTo(ProtobufWriter out, Stream<Integer> values) {
            out.writeInts(values);
        }

        @Override
        public Stream<Integer> convertFrom(ProtobufReader in) {
            return in.readInts(LIST_CREATOR).stream();
        }

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, Stream<Integer> value) {
            if (value == null) {
                return 0;
            }
            int len = 0;
            for (Object item : value.toArray()) {
                len += ProtobufFactory.computeSInt32SizeNoTag(item == null ? 0 : (Integer) item);
            }
            return len;
        }
    }

    public static class ProtobufFloatStreamSimpledCoder
            extends SimpledCoder<ProtobufReader, ProtobufWriter, Stream<Float>>
            implements ProtobufEncodeable<ProtobufWriter, Stream<Float>> {

        public static final ProtobufFloatStreamSimpledCoder instance = new ProtobufFloatStreamSimpledCoder();

        @Override
        public void convertTo(ProtobufWriter out, Stream<Float> values) {
            out.writeFloats(values);
        }

        @Override
        public Stream<Float> convertFrom(ProtobufReader in) {
            return in.readFloats(LIST_CREATOR).stream();
        }

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, Stream<Float> value) {
            return value == null ? 0 : (int) value.count() * 4;
        }
    }

    public static class ProtobufLongStreamSimpledCoder
            extends SimpledCoder<ProtobufReader, ProtobufWriter, Stream<Long>>
            implements ProtobufEncodeable<ProtobufWriter, Stream<Long>> {

        public static final ProtobufLongStreamSimpledCoder instance = new ProtobufLongStreamSimpledCoder();

        @Override
        public void convertTo(ProtobufWriter out, Stream<Long> values) {
            out.writeLongs(values);
        }

        @Override
        public Stream<Long> convertFrom(ProtobufReader in) {
            return in.readLongs(LIST_CREATOR).stream();
        }

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, Stream<Long> value) {
            if (value == null) {
                return 0;
            }
            int len = 0;
            for (Object item : value.toArray()) {
                len += ProtobufFactory.computeSInt64SizeNoTag(item == null ? 0 : (Long) item);
            }
            return len;
        }
    }

    public static class ProtobufDoubleStreamSimpledCoder
            extends SimpledCoder<ProtobufReader, ProtobufWriter, Stream<Double>>
            implements ProtobufEncodeable<ProtobufWriter, Stream<Double>> {

        public static final ProtobufDoubleStreamSimpledCoder instance = new ProtobufDoubleStreamSimpledCoder();

        @Override
        public void convertTo(ProtobufWriter out, Stream<Double> values) {
            out.writeDoubles(values);
        }

        @Override
        public Stream<Double> convertFrom(ProtobufReader in) {
            return in.readDoubles(LIST_CREATOR).stream();
        }

        @Override
        public int computeSize(ProtobufWriter out, int tagSize, Stream<Double> value) {
            return value == null ? 0 : (int) value.count() * 8;
        }
    }
}
