/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import java.lang.reflect.Type;
import java.util.List;

/**
 * 文本序列化/反序列化操作类
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> Reader输入的子类
 * @param <W> Writer输出的子类
 */
public abstract class TextConvert<R extends Reader, W extends Writer> extends Convert<R, W> {

    protected TextConvert(ConvertFactory<R, W> factory, int features) {
        super(factory, features);
    }

    @Override
    public final boolean isBinary() {
        return false;
    }

    public abstract <T> T convertFrom(final Type type, final String text);

    public <T> List<T> convertListFrom(final Type componentType, final String text) {
        return (List) convertFrom(factory.createGenericListType(componentType), text);
    }

    /**
     * 序列化
     *
     * @see org.redkale.convert.json.JsonConvert#convertTo(java.lang.reflect.Type, java.lang.Object)
     * @param value Object
     * @return String
     */
    public final String convertTo(final Object value) {
        return convertTo((Type) null, value);
    }

    /**
     * 序列化
     *
     * @see org.redkale.convert.json.JsonConvert#convertTo(java.lang.reflect.Type, java.lang.Object)
     * @param type Type
     * @param value Object
     * @return String
     */
    public abstract String convertTo(final Type type, final Object value);
}
