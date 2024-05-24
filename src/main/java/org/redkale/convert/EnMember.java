/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import java.lang.reflect.*;
import org.redkale.annotation.Comment;
import org.redkale.persistence.Column;
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

    final String comment;

    final boolean string;

    //final boolean isnumber;
    final boolean bool;

    final char[] jsonFieldNameChars;

    final byte[] jsonFieldNameBytes;

    final Field field; //对应类成员的Field也可能为null

    final Method method; //对应类成员的Method也可能为null

    int index;

    int position; //从1开始

    int tag; //主要给protobuf使用

    public EnMember(Attribute<T, F> attribute, Encodeable<W, F> encoder, Field field, Method method) {
        this.attribute = attribute;
        this.encoder = encoder;
        this.field = field;
        this.method = method;
        Class t = attribute.type();
        this.string = CharSequence.class.isAssignableFrom(t);
        this.bool = t == Boolean.class || t == boolean.class;
        this.jsonFieldNameChars = ('"' + attribute.field() + "\":").toCharArray();
        this.jsonFieldNameBytes = ('"' + attribute.field() + "\":").getBytes();
        if (field != null) {
            Comment ct = field.getAnnotation(Comment.class);
            if (ct == null) {
                Column col = field.getAnnotation(Column.class);
                this.comment = col == null ? "":col.comment();
            } else {
                this.comment = ct.value();
            }
        } else if (method != null) {
            Comment ct = method.getAnnotation(Comment.class);
            if (ct == null) {
                Column col = method.getAnnotation(Column.class);
                this.comment = col == null ? "":col.comment();
            } else {
                this.comment = ct.value();
            }
        } else {
            this.comment = "";
        }
        //this.isnumber = Number.class.isAssignableFrom(t) || (!this.isbool && t.isPrimitive());
    }

    public static <W extends Writer, T, F> EnMember<W, T, F> create(final ConvertFactory factory, final Class<T> clazz, final String fieldname) {
        try {
            Field field = clazz.getDeclaredField(fieldname);
            return new EnMember<>(Attribute.create(field), factory.loadEncoder(field.getGenericType()), field, null);
        } catch (Exception e) {
            throw new ConvertException(e);
        }
    }

    public static <W extends Writer, T, F> EnMember<W, T, F> create(final ConvertFactory factory, final Class<T> clazz, final String fieldname, final Class<F> fieldtype) {
        try {
            Field field = clazz.getDeclaredField(fieldname);
            return new EnMember<>(Attribute.create(clazz, fieldname, fieldtype), factory.loadEncoder(fieldtype), field, null);
        } catch (Exception e) {
            throw new ConvertException(e);
        }
    }

    public static <W extends Writer, T, F> EnMember<W, T, F> create(final Attribute<T, F> attribute, final ConvertFactory factory, final Class<F> fieldtype) {
        return new EnMember<>(attribute, factory.loadEncoder(fieldtype), null, null);
    }

    public final boolean accepts(String name) {
        return attribute.field().equals(name);
    }

    public Attribute<T, F> getAttribute() {
        return attribute;
    }

    public String getComment() {
        return comment;
    }

    public char[] getJsonFieldNameChars() {
        return jsonFieldNameChars;
    }

    public byte[] getJsonFieldNameBytes() {
        return jsonFieldNameBytes;
    }

    public Encodeable<W, F> getEncoder() {
        return encoder;
    }

    public boolean isStringType() {
        return string;
    }

    public Field getField() {
        return field;
    }

    public Method getMethod() {
        return method;
    }

    public boolean isBoolType() {
        return bool;
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

    public int compareTo(boolean fieldSort, EnMember<W, T, F> o) {
        if (o == null) {
            return -1;
        }
        if (this.position != o.position) {
            return (this.position == 0 ? Integer.MAX_VALUE : this.position) - (o.position == 0 ? Integer.MAX_VALUE : o.position);
        }
        if (this.index != o.index) {
            return (this.index == 0 ? Integer.MAX_VALUE : this.index) - (o.index == 0 ? Integer.MAX_VALUE : o.index);
        }
        if (this.index != 0) {
            throw new ConvertException("fields (" + attribute.field() + ", " + o.attribute.field() + ") have same ConvertColumn.index(" + this.index + ") in " + attribute.declaringClass());
        }
        return fieldSort ? this.attribute.field().compareTo(o.attribute.field()) : 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof EnMember)) {
            return false;
        }
        EnMember other = (EnMember) obj;
        return compareTo(true, other) == 0;
    }

    @Override
    public int hashCode() {
        return this.attribute.field().hashCode();
    }

    @Override
    public String toString() {
        return "EnMember{" + "attribute=" + attribute.field() + ", position=" + position + ", encoder=" + (encoder == null ? null : encoder.getClass().getName()) + '}';
    }
}
