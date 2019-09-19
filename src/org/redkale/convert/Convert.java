/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.function.*;
import org.redkale.util.Attribute;

/**
 * 序列化/反序列化操作类
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> Reader输入的子类
 * @param <W> Writer输出的子类
 */
public abstract class Convert<R extends Reader, W extends Writer> {

    protected final ConvertFactory<R, W> factory;

    protected Convert(ConvertFactory<R, W> factory) {
        this.factory = factory;
    }

    public ConvertFactory<R, W> getFactory() {
        return this.factory;
    }

    protected <S extends W> S funcWrite(S writer, BiFunction<Attribute, Object, Object> fieldFunc) {
        writer.fieldFunc = fieldFunc;
        return writer;
    }

    public abstract boolean isBinary();

    public abstract <T> T convertFrom(final Type type, final byte[] bytes);

    public abstract <T> T convertFrom(final Type type, final ByteBuffer... buffers);

    public abstract <T> T convertFrom(final Type type, final ConvertMask mask, final ByteBuffer... buffers);

    public abstract ByteBuffer[] convertTo(final Supplier<ByteBuffer> supplier, final Object value);

    public abstract ByteBuffer[] convertTo(final Supplier<ByteBuffer> supplier, final Type type, final Object value);

    public abstract ByteBuffer[] convertMapTo(final Supplier<ByteBuffer> supplier, final Object... values);

    public abstract ByteBuffer[] convertTo(final Supplier<ByteBuffer> supplier, BiFunction<Attribute, Object, Object> fieldFunc, final Object value);

    public abstract ByteBuffer[] convertTo(final Supplier<ByteBuffer> supplier, final Type type, BiFunction<Attribute, Object, Object> fieldFunc, final Object value);

    public abstract ByteBuffer[] convertMapTo(final Supplier<ByteBuffer> supplier, BiFunction<Attribute, Object, Object> fieldFunc, final Object... values);
}
