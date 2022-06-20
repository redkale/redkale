/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.function.*;
import org.redkale.util.*;

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

    protected <S extends W> S configWrite(S writer) {
        return writer;
    }

    protected <S extends W> S fieldFunc(S writer, BiFunction<Attribute, Object, Object> objFieldFunc, Function<Object, ConvertField[]> objExtFunc) {
        writer.objFieldFunc = objFieldFunc;
        writer.objExtFunc = objExtFunc;
        return writer;
    }

    protected <S extends W> S fieldFunc(S writer, BiFunction<Object, Object, Object> mapFieldFunc, BiFunction<Attribute, Object, Object> objFieldFunc, Function<Object, ConvertField[]> objExtFunc) {
        writer.mapFieldFunc = mapFieldFunc;
        writer.objFieldFunc = objFieldFunc;
        writer.objExtFunc = objExtFunc;
        return writer;
    }

    public abstract Convert<R, W> newConvert(final BiFunction<Attribute, Object, Object> objFieldFunc);

    public abstract Convert<R, W> newConvert(final BiFunction<Attribute, Object, Object> objFieldFunc, Function<Object, ConvertField[]> objExtFunc);

    public abstract boolean isBinary();

    public abstract <T> T convertFrom(final Type type, final byte[] bytes);

    //@since 2.2.0
    public abstract <T> T convertFrom(final Type type, final byte[] bytes, final int offset, final int length);

    public abstract <T> T convertFrom(final Type type, final ByteBuffer... buffers);

    public abstract <T> T convertFrom(final Type type, final ConvertMask mask, final ByteBuffer... buffers);

    public abstract void convertTo(final W writer, final Object value);

    public abstract void convertTo(final W writer, final Type type, final Object value);

    public abstract byte[] convertToBytes(final Object value);

    public abstract byte[] convertToBytes(final Type type, final Object value);

    public abstract void convertToBytes(final Object value, final ConvertBytesHandler handler);

    public abstract void convertToBytes(final Type type, final Object value, final ConvertBytesHandler handler);

    public abstract void convertToBytes(final ByteArray array, final Object value);

    public abstract void convertToBytes(final ByteArray array, final Type type, final Object value);

    public abstract ByteBuffer[] convertTo(final Supplier<ByteBuffer> supplier, final Object value);

    public abstract ByteBuffer[] convertTo(final Supplier<ByteBuffer> supplier, final Type type, final Object value);

}
