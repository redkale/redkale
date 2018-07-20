/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import java.lang.reflect.*;
import org.redkale.util.Attribute;

/**
 * 字段的序列化操作类
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <W> Writer输出的子类
 * @param <T> 字段依附的类
 * @param <F> 字段的数据类型
 */
@SuppressWarnings("unchecked")
public final class EnMember<W extends Writer, T, F> {

    final Attribute<T, F> attribute;

    final Encodeable<W, F> encoder;

    final boolean istring;

    //final boolean isnumber;
    final boolean isbool;

    protected int index;

    protected int position; //从1开始

    public EnMember(Attribute<T, F> attribute, Encodeable<W, F> encoder) {
        this.attribute = attribute;
        this.encoder = encoder;
        Class t = attribute.type();
        this.istring = CharSequence.class.isAssignableFrom(t);
        this.isbool = t == Boolean.class || t == boolean.class;
        //this.isnumber = Number.class.isAssignableFrom(t) || (!this.isbool && t.isPrimitive());
    }

    public static <W extends Writer, T, F> EnMember<W, T, F> create(final ConvertFactory factory, final Class<T> clazz, final String fieldname) {
        try {
            Field field = clazz.getDeclaredField(fieldname);
            return new EnMember<>(Attribute.create(field), factory.loadEncoder(field.getGenericType()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <W extends Writer, T, F> EnMember<W, T, F> create(final ConvertFactory factory, final Class<T> clazz, final String fieldname, final Class<F> fieldtype) {
        return new EnMember<>(Attribute.create(clazz, fieldname, fieldtype), factory.loadEncoder(fieldtype));
    }

    public static <W extends Writer, T, F> EnMember<W, T, F> create(final Attribute<T, F> attribute, final ConvertFactory factory, final Class<F> fieldtype) {
        return new EnMember<>(attribute, factory.loadEncoder(fieldtype));
    }

    public final boolean match(String name) {
        return attribute.field().equals(name);
    }

    public Attribute<T, F> getAttribute() {
        return attribute;
    }

    public Encodeable<W, F> getEncoder() {
        return encoder;
    }

    public boolean isStringType() {
        return istring;
    }

    public boolean isBoolType() {
        return isbool;
    }

    public int getIndex() {
        return this.index;
    }

    public int getPosition() {
        return this.position;
    }

    public int compareTo(boolean fieldSort, EnMember<W, T, F> o) {
        if (o == null) return -1;
        if (this.index != o.index) return (this.index == 0 ? Integer.MAX_VALUE : this.index) - (o.index == 0 ? Integer.MAX_VALUE : o.index);
        if (this.index != 0) throw new RuntimeException("fields (" + attribute.field() + ", " + o.attribute.field() + ") have same ConvertColumn.index(" + this.index + ") in " + attribute.declaringClass());
        return fieldSort ? this.attribute.field().compareTo(o.attribute.field()) : 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof EnMember)) return false;
        EnMember other = (EnMember) obj;
        return compareTo(true, other) == 0;
    }

    @Override
    public int hashCode() {
        return this.attribute.field().hashCode();
    }

    @Override
    public String toString() {
        return "EnMember{" + "attribute=" + attribute.field() + ", encoder=" + (encoder == null ? null : encoder.getClass().getName()) + '}';
    }
}
