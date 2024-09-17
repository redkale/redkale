/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import java.lang.reflect.*;
import org.redkale.annotation.Comment;
import org.redkale.util.Attribute;

/**
 * 字段的反序列化操作类
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> Reader输入的子类
 * @param <T> 字段依附的类
 * @param <F> 字段的数据类型
 */
@SuppressWarnings("unchecked")
public final class DeMember<R extends Reader, T, F> {

    // 对应类成员的Field， 也可能为null
    final Field field;

    // 对应类成员的Method也可能为null
    final Method method;

    final String comment;

    // 从1开始
    protected int index;

    // 从1开始
    protected int position;

    // 主要给protobuf使用  从1开始
    protected int tag;

    protected final Attribute<T, F> attribute;

    protected Decodeable<R, F> decoder;

    public DeMember(final Attribute<T, F> attribute, int tag, Decodeable<R, F> decoder) {
        this.attribute = attribute;
        this.tag = tag;
        this.index = tag;
        this.decoder = decoder;
        this.comment = "";
        this.field = null;
        this.method = null;
    }

    public DeMember(final Attribute<T, F> attribute, Field field, Method method) {
        this.attribute = attribute;
        this.field = field;
        this.method = method;
        if (field != null) {
            Comment ct = field.getAnnotation(Comment.class);
            this.comment = ct == null ? "" : ct.value();
        } else if (method != null) {
            Comment ct = method.getAnnotation(Comment.class);
            this.comment = ct == null ? "" : ct.value();
        } else {
            this.comment = "";
        }
    }

    public DeMember(Attribute<T, F> attribute, Decodeable<R, F> decoder, Field field, Method method) {
        this(attribute, field, method);
        this.decoder = decoder;
    }

    public static <R extends Reader, T, F> DeMember<R, T, F> create(
            ConvertFactory factory, Class<T> clazz, String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            return new DeMember<>(Attribute.create(field), factory.loadDecoder(field.getGenericType()), field, null);
        } catch (Exception e) {
            throw new ConvertException(e);
        }
    }

    public static <R extends Reader, T, F> DeMember<R, T, F> create(
            ConvertFactory factory, Class<T> clazz, String fieldName, Class<F> fieldType) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            return new DeMember<>(
                    Attribute.create(clazz, fieldName, fieldType), factory.loadDecoder(fieldType), field, null);
        } catch (Exception e) {
            throw new ConvertException(e);
        }
    }

    public static <R extends Reader, T, F> DeMember<R, T, F> create(
            Attribute<T, F> attribute, ConvertFactory factory, Class<F> fieldType) {
        return new DeMember<>(attribute, factory.loadDecoder(fieldType), null, null);
    }

    public final boolean accepts(String name) {
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

    public String getComment() {
        return comment;
    }

    public Decodeable<R, F> getDecoder() {
        return decoder;
    }

    public Field getField() {
        return field;
    }

    public Method getMethod() {
        return method;
    }

    public int getIndex() {
        return this.index;
    }

    public int getPosition() {
        return this.position;
    }

    public int getTag() {
        return this.tag;
    }

    public int compareTo(boolean fieldSort, DeMember<R, T, F> o) {
        if (o == null) {
            return -1;
        }
        if (this.position != o.position) {
            return (this.position == 0 ? Integer.MAX_VALUE : this.position)
                    - (o.position == 0 ? Integer.MAX_VALUE : o.position);
        }
        if (this.index != o.index) {
            return (this.index == 0 ? Integer.MAX_VALUE : this.index) - (o.index == 0 ? Integer.MAX_VALUE : o.index);
        }
        if (this.index != 0) {
            throw new ConvertException("fields (" + attribute.field() + ", " + o.attribute.field()
                    + ") have same ConvertColumn.index(" + this.index + ") in " + attribute.declaringClass());
        }
        return fieldSort ? this.attribute.field().compareTo(o.attribute.field()) : 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DeMember)) {
            return false;
        }
        DeMember other = (DeMember) obj;
        return compareTo(true, other) == 0;
    }

    @Override
    public int hashCode() {
        return this.attribute.field().hashCode();
    }

    @Override
    public String toString() {
        return "DeMember{" + "attribute=" + attribute.field() + ", position=" + position + ", tag=" + tag + ", decoder="
                + (decoder == null ? null : decoder.getClass().getName()) + '}';
    }

}
