/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.ext;

import java.time.LocalDate;
import org.redkale.convert.*;
import org.redkale.convert.json.*;

/**
 * java.time.LocalDate 的SimpledCoder实现
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> Reader输入的子类型
 * @param <W> Writer输出的子类型
 */
public final class LocalDateSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, LocalDate> {

    public static final LocalDateSimpledCoder instance = new LocalDateSimpledCoder();

    @Override
    public void convertTo(W out, LocalDate value) {
        out.writeInt(
                value == null ? -1 : value.getYear() * 100_00 + value.getMonthValue() * 100 + value.getDayOfMonth());
    }

    @Override
    public LocalDate convertFrom(R in) {
        int t = in.readInt();
        return t == -1 ? null : LocalDate.of(t / 100_00, t % 100_00 / 100, t % 100);
    }

    //    public static void main(String[] args) throws Throwable {
    //        LocalDate now = LocalDate.now();
    //        System.out.println(now);
    //        BsonWriter writer = new BsonWriter();
    //        LocalDateSimpledCoder.instance.convertTo(writer, now);
    //        System.out.println(new ByteArray(writer).getInt(0));
    //        BsonReader reader = new BsonReader(writer.toArray());
    //        System.out.println(LocalDateSimpledCoder.instance.convertFrom(reader));
    //    }
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
                out.writeSmallString(value.toString());
            }
        }

        @Override
        public LocalDate convertFrom(R in) {
            final String str = in.readSmallString();
            if (str == null) {
                return null;
            }
            return LocalDate.parse(str);
        }
    }
}
