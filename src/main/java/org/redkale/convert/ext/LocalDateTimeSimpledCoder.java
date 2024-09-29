/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.ext;

import java.time.*;
import org.redkale.convert.*;
import org.redkale.convert.json.*;

/**
 * java.time.LocalDateTime 的SimpledCoder实现
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> Reader输入的子类型
 * @param <W> Writer输出的子类型
 */
public final class LocalDateTimeSimpledCoder<R extends Reader, W extends Writer>
        extends SimpledCoder<R, W, LocalDateTime> {

    private static final ByteArraySimpledCoder bsSimpledCoder = ByteArraySimpledCoder.instance;

    public static final LocalDateTimeSimpledCoder instance = new LocalDateTimeSimpledCoder();

    @Override
    public void convertTo(W out, LocalDateTime value) {
        if (value == null) {
            bsSimpledCoder.convertTo(out, null);
        } else {
            long v1 = value.toEpochSecond(ZoneOffset.UTC);
            int v2 = value.getNano();
            byte[] bs = new byte[] {
                (byte) (v1 >> 56),
                (byte) (v1 >> 48),
                (byte) (v1 >> 40),
                (byte) (v1 >> 32),
                (byte) (v1 >> 24),
                (byte) (v1 >> 16),
                (byte) (v1 >> 8),
                (byte) v1,
                (byte) (v2 >> 24),
                (byte) (v2 >> 16),
                (byte) (v2 >> 8),
                (byte) v2
            };
            bsSimpledCoder.convertTo(out, bs);
        }
    }

    @Override
    public LocalDateTime convertFrom(R in) {
        byte[] bs = bsSimpledCoder.convertFrom(in);
        if (bs == null) {
            return null;
        }
        long v1 = (((long) bs[0] & 0xff) << 56)
                | (((long) bs[1] & 0xff) << 48)
                | (((long) bs[2] & 0xff) << 40)
                | (((long) bs[3] & 0xff) << 32)
                | (((long) bs[4] & 0xff) << 24)
                | (((long) bs[5] & 0xff) << 16)
                | (((long) bs[6] & 0xff) << 8)
                | ((long) bs[7] & 0xff);
        int v2 = ((bs[8] & 0xff) << 24) | ((bs[9] & 0xff) << 16) | ((bs[10] & 0xff) << 8) | (bs[11] & 0xff);
        return LocalDateTime.ofEpochSecond(v1, v2, ZoneOffset.UTC);
    }

    //    public static void main(String[] args) throws Throwable {
    //        LocalDateTime now = LocalDateTime.now();
    //        System.out.println(now);
    //        BsonWriter writer = new BsonWriter();
    //        LocalDateTimeSimpledCoder.instance.convertTo(writer, now);
    //        BsonReader reader = new BsonReader(writer.toArray());
    //        System.out.println(LocalDateTimeSimpledCoder.instance.convertFrom(reader));
    //    }
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
