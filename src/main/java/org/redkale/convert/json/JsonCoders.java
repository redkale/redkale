/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.convert.json;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.redkale.convert.SimpledCoder;
import org.redkale.convert.ext.StringSimpledCoder;
import org.redkale.util.StringWrapper;
import org.redkale.util.Uint128;
import org.redkale.util.Utility;

/**
 *
 * @author zhangjx
 */
public abstract class JsonCoders {

    private JsonCoders() {
        // do nothing
    }

    /**
     * InetAddress 的JsonSimpledCoder实现
     *
     * @param <R> Reader输入的子类型
     * @param <W> Writer输出的子类型
     */
    public static final class InetAddressJsonSimpledCoder<R extends JsonReader, W extends JsonWriter>
            extends SimpledCoder<R, W, InetAddress> {

        public static final InetAddressJsonSimpledCoder instance = new InetAddressJsonSimpledCoder();

        @Override
        public void convertTo(W out, InetAddress value) {
            if (value == null) {
                out.writeNull();
                return;
            }
            out.writeWrapper(new StringWrapper(value.getHostAddress()));
        }

        @Override
        public InetAddress convertFrom(R in) {
            String str = in.readString();
            if (str == null) {
                return null;
            }
            try {
                return InetAddress.getByName(str);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    /**
     * InetSocketAddress 的JsonSimpledCoder实现
     *
     * @param <R> Reader输入的子类型
     * @param <W> Writer输出的子类型
     */
    public static final class InetSocketAddressJsonSimpledCoder<R extends JsonReader, W extends JsonWriter>
            extends SimpledCoder<R, W, InetSocketAddress> {

        public static final InetSocketAddressJsonSimpledCoder instance = new InetSocketAddressJsonSimpledCoder();

        @Override
        public void convertTo(W out, InetSocketAddress value) {
            if (value == null) {
                out.writeNull();
                return;
            }
            StringSimpledCoder.instance.convertTo(out, value.getHostString() + ":" + value.getPort());
        }

        @Override
        public InetSocketAddress convertFrom(R in) {
            String str = in.readStringValue();
            if (str == null) {
                return null;
            }
            int pos = str.indexOf(':');
            return new InetSocketAddress(str.substring(0, pos), Integer.parseInt(str.substring(pos + 1)));
        }
    }
    /**
     * Uint128 的JsonSimpledCoder实现
     *
     * @param <R> Reader输入的子类型
     * @param <W> Writer输出的子类型
     */
    public static class Uint128JsonSimpledCoder<R extends JsonReader, W extends JsonWriter>
            extends SimpledCoder<R, W, Uint128> {

        public static final Uint128JsonSimpledCoder instance = new Uint128JsonSimpledCoder();

        @Override
        public void convertTo(final W out, final Uint128 value) {
            if (value == null) {
                out.writeNull();
            } else {
                out.writeStandardString(value.toString());
            }
        }

        @Override
        public Uint128 convertFrom(R in) {
            final String str = in.readStandardString();
            if (str == null) {
                return null;
            }
            return Uint128.create(Utility.hexToBin(str));
        }
    }

    /**
     * BigInteger 的JsonSimpledCoder实现
     *
     * @param <R> Reader输入的子类型
     * @param <W> Writer输出的子类型
     */
    public static class BigIntegerJsonSimpledCoder<R extends JsonReader, W extends JsonWriter>
            extends SimpledCoder<R, W, BigInteger> {

        public static final BigIntegerJsonSimpledCoder instance = new BigIntegerJsonSimpledCoder();

        @Override
        public void convertTo(final W out, final BigInteger value) {
            if (value == null) {
                out.writeNull();
            } else {
                out.writeStandardString(value.toString());
            }
        }

        @Override
        public BigInteger convertFrom(R in) {
            final String str = in.readString();
            if (str == null) {
                return null;
            }
            return new BigInteger(str);
        }
    }

    /**
     * BigInteger 的十六进制JsonSimpledCoder实现
     *
     * @param <R> Reader输入的子类型
     * @param <W> Writer输出的子类型
     */
    public static class BigIntegerHexJsonSimpledCoder<R extends JsonReader, W extends JsonWriter>
            extends SimpledCoder<R, W, BigInteger> {

        public static final BigIntegerHexJsonSimpledCoder instance = new BigIntegerHexJsonSimpledCoder();

        @Override
        public void convertTo(final W out, final BigInteger value) {
            if (value == null) {
                out.writeNull();
            } else {
                String s = value.toString(16);
                out.writeStandardString(s.charAt(0) == '-' ? ("-0x" + s.substring(1)) : ("0x" + s));
            }
        }

        @Override
        public BigInteger convertFrom(R in) {
            final String str = in.readString();
            if (str == null) {
                return null;
            }
            if (str.length() > 2) {
                if (str.charAt(0) == '0' && (str.charAt(1) == 'x' || str.charAt(1) == 'X')) {
                    return new BigInteger(str.substring(2), 16);
                } else if (str.charAt(0) == '-'
                        && str.length() > 3
                        && str.charAt(1) == '0'
                        && (str.charAt(2) == 'x' || str.charAt(2) == 'X')) {
                    return new BigInteger("-" + str.substring(3), 16);
                }
            }
            return new BigInteger(str);
        }
    }

    /**
     * BigDecimal 的JsonSimpledCoder实现
     *
     * @param <R> Reader输入的子类型
     * @param <W> Writer输出的子类型
     */
    public static class BigDecimalJsonSimpledCoder<R extends JsonReader, W extends JsonWriter>
            extends SimpledCoder<R, W, BigDecimal> {

        public static final BigDecimalJsonSimpledCoder instance = new BigDecimalJsonSimpledCoder();

        @Override
        public void convertTo(final W out, final BigDecimal value) {
            if (value == null) {
                out.writeNull();
            } else {
                out.writeStandardString(value.toString());
            }
        }

        @Override
        public BigDecimal convertFrom(R in) {
            final String str = in.readString();
            if (str == null) {
                return null;
            }
            return new BigDecimal(str);
        }
    }

    public static final class InstantJsonSimpledCoder<R extends JsonReader, W extends JsonWriter>
            extends SimpledCoder<R, W, Instant> {

        public static final InstantJsonSimpledCoder instance = new InstantJsonSimpledCoder();

        @Override
        public void convertTo(final W out, final Instant value) {
            if (value == null) {
                out.writeNull();
            } else {
                out.writeStandardString(value.toString());
            }
        }

        @Override
        public Instant convertFrom(R in) {
            final String str = in.readStandardString();
            if (str == null) {
                return null;
            }
            return Instant.parse(str);
        }
    }

    /**
     * java.time.LocalDate 的JsonSimpledCoder实现
     *
     * @param <R> Reader输入的子类型
     * @param <W> Writer输出的子类型
     */
    public static final class LocalDateJsonSimpledCoder<R extends JsonReader, W extends JsonWriter>
            extends SimpledCoder<R, W, LocalDate> {

        public static final LocalDateJsonSimpledCoder instance = new LocalDateJsonSimpledCoder();

        @Override
        public void convertTo(final W out, final LocalDate value) {
            if (value == null) {
                out.writeNull();
            } else {
                out.writeStandardString(value.toString());
            }
        }

        @Override
        public LocalDate convertFrom(R in) {
            final String str = in.readStandardString();
            if (str == null) {
                return null;
            }
            return LocalDate.parse(str);
        }
    }

    /**
     * java.time.LocalTime 的JsonSimpledCoder实现
     *
     * @param <R> Reader输入的子类型
     * @param <W> Writer输出的子类型
     */
    public static final class LocalTimeJsonSimpledCoder<R extends JsonReader, W extends JsonWriter>
            extends SimpledCoder<R, W, LocalTime> {

        public static final LocalTimeJsonSimpledCoder instance = new LocalTimeJsonSimpledCoder();

        @Override
        public void convertTo(final W out, final LocalTime value) {
            if (value == null) {
                out.writeNull();
            } else {
                out.writeStandardString(value.toString());
            }
        }

        @Override
        public LocalTime convertFrom(R in) {
            final String str = in.readStandardString();
            if (str == null) {
                return null;
            }
            return LocalTime.parse(str);
        }
    }

    /**
     * java.time.LocalDateTime 的JsonSimpledCoder实现
     *
     * @param <R> Reader输入的子类型
     * @param <W> Writer输出的子类型
     */
    public static final class LocalDateTimeJsonSimpledCoder<R extends JsonReader, W extends JsonWriter>
            extends SimpledCoder<R, W, LocalDateTime> {

        public static final LocalDateTimeJsonSimpledCoder instance = new LocalDateTimeJsonSimpledCoder();

        @Override
        public void convertTo(final W out, final LocalDateTime value) {
            if (value == null) {
                out.writeNull();
            } else {
                out.writeStandardString(value.toString());
            }
        }

        @Override
        public LocalDateTime convertFrom(R in) {
            final String str = in.readStandardString();
            if (str == null) {
                return null;
            }
            return LocalDateTime.parse(str);
        }
    }
}
