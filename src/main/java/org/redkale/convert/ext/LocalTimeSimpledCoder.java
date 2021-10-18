/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.ext;

import java.time.*;
import org.redkale.convert.*;

/**
 * java.time.LocalTime 的SimpledCoder实现
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> Reader输入的子类型
 * @param <W> Writer输出的子类型
 */
public final class LocalTimeSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, LocalTime> {

    public static final LocalTimeSimpledCoder instance = new LocalTimeSimpledCoder();

    @Override
    public void convertTo(W out, LocalTime value) {
        out.writeLong(value == null ? -1L : value.toNanoOfDay());
    }

    @Override
    public LocalTime convertFrom(R in) {
        long t = in.readLong();
        return t == -1 ? null : LocalTime.ofNanoOfDay(t);
    }

//    public static void main(String[] args) throws Throwable {
//        LocalTime now = LocalTime.now();
//        System.out.println(now);
//        BsonWriter writer = new BsonWriter();
//        LocalTimeSimpledCoder.instance.convertTo(writer, now);
//        BsonReader reader = new BsonReader(writer.toArray()); 
//        System.out.println(LocalTimeSimpledCoder.instance.convertFrom(reader));
//    }
    /**
     * java.time.LocalTime 的JsonSimpledCoder实现
     *
     * @param <R> Reader输入的子类型
     * @param <W> Writer输出的子类型
     */
    public final static class LocalTimeJsonSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, LocalTime> {

        public static final LocalTimeJsonSimpledCoder instance = new LocalTimeJsonSimpledCoder();

        @Override
        public void convertTo(final Writer out, final LocalTime value) {
            if (value == null) {
                out.writeNull();
            } else {
                out.writeSmallString(value.toString());
            }
        }

        @Override
        public LocalTime convertFrom(Reader in) {
            final String str = in.readSmallString();
            if (str == null) return null;
            return LocalTime.parse(str);
        }
    }
}
