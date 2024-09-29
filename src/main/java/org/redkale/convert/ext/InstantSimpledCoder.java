/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.ext;

import java.time.Instant;
import org.redkale.convert.*;
import org.redkale.convert.json.*;

/**
 * java.time.Instant 的SimpledCoder实现
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> Reader输入的子类型
 * @param <W> Writer输出的子类型
 */
public class InstantSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, Instant> {

    public static final InstantSimpledCoder instance = new InstantSimpledCoder();

    @Override
    public void convertTo(W out, Instant value) {
        out.writeLong(value == null ? -1L : value.toEpochMilli());
    }

    @Override
    public Instant convertFrom(R in) {
        long t = in.readLong();
        return t == -1 ? null : Instant.ofEpochMilli(t);
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
}
