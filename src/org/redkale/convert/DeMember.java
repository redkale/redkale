/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import java.lang.reflect.*;
import org.redkale.util.Attribute;

/**
 * 字段的反序列化操作类
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> Reader输入的子类
 * @param <T> 字段依附的类
 * @param <F> 字段的数据类型
 */
@SuppressWarnings("unchecked")
public final class DeMember<R extends Reader, T, F> {

    protected int index;

    protected int position; //从1开始

    protected final Attribute<T, F> attribute;

    protected Decodeable<R, F> decoder;

    public DeMember(final Attribute<T, F> attribute) {
        this.attribute = attribute;
    }

    public DeMember(Attribute<T, F> attribute, Decodeable<R, F> decoder) {
        this(attribute);
        this.decoder = decoder;
    }

    public static <R extends Reader, T, F> DeMember<R, T, F> create(final ConvertFactory factory, final Class<T> clazz, final String fieldname) {
        try {
            Field field = clazz.getDeclaredField(fieldname);
            return new DeMember<>(Attribute.create(field), factory.loadDecoder(field.getGenericType()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <R extends Reader, T, F> DeMember<R, T, F> create(final ConvertFactory factory, final Class<T> clazz, final String fieldname, final Class<F> fieldtype) {
        return new DeMember<>(Attribute.create(clazz, fieldname, fieldtype), factory.loadDecoder(fieldtype));
    }

    public static <R extends Reader, T, F> DeMember<R, T, F> create(final Attribute<T, F> attribute, final ConvertFactory factory, final Class<F> fieldtype) {
        return new DeMember<>(attribute, factory.loadDecoder(fieldtype));
    }

    public final boolean match(String name) {
        return attribute.field().equals(name);
    }

    public final void read(R in, T obj) {
        this.attribute.set(obj, decoder.convertFrom(in));
    }

    public final F read(R in) {
        return decoder.convertFrom(in);
    }

    public Attribute<T, F> getAttribute() {
        return this.attribute;
    }

    public Decodeable<R, F> getDecoder() {
        return decoder;
    }

    public int getIndex() {
        return this.index;
    }

    public int getPosition() {
        return this.position;
    }

    public int compareTo(boolean fieldSort, DeMember<R, T, F> o) {
        if (o == null) return -1;
        if (this.index != o.index) return (this.index == 0 ? Integer.MAX_VALUE : this.index) - (o.index == 0 ? Integer.MAX_VALUE : o.index);
        if (this.index != 0) throw new RuntimeException("fields (" + attribute.field() + ", " + o.attribute.field() + ") have same ConvertColumn.index(" + this.index + ") in " + attribute.declaringClass());
        return fieldSort ? this.attribute.field().compareTo(o.attribute.field()) : 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof DeMember)) return false;
        DeMember other = (DeMember) obj;
        return compareTo(true, other) == 0;
    }

    @Override
    public int hashCode() {
        return this.attribute.field().hashCode();
    }

    @Override
    public String toString() {
        return "DeMember{" + "attribute=" + attribute.field() + ", decoder=" + (decoder == null ? null : decoder.getClass().getName()) + '}';
    }
}
