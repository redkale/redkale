/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import java.lang.reflect.*;
import java.util.function.*;
import org.redkale.annotation.ClassDepends;
import org.redkale.util.*;

/**
 * 序列化的数据输出流
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
public abstract class Writer {

    // 当前对象输出字段名之前是否需要分隔符， JSON字段间的分隔符为,逗号
    protected boolean comma;

    // convertTo时是否以指定Type的ObjectEncoder进行处理
    protected Type specificObjectType;

    // 对某个key值进行动态处理，仅供MapEncoder使用
    protected BiFunction<Object, Object, Object> mapFieldFunc;

    // 对某个字段值进行动态处理
    protected BiFunction<Attribute, Object, Object> objFieldFunc;

    // 对某个对象进行动态扩展字段值处理
    protected Function<Object, ConvertField[]> objExtFunc;

    // 配置项
    protected int features;

    /**
     * 设置specificObjectType
     *
     * @param value Type
     */
    public void specificObjectType(Type value) {
        if (value instanceof GenericArrayType) {
            this.specificObjectType = ((GenericArrayType) value).getGenericComponentType();
        } else if (value instanceof Class && ((Class) value).isArray()) {
            this.specificObjectType = ((Class) value).getComponentType();
        } else {
            this.specificObjectType = value;
        }
    }

    protected boolean recycle() {
        this.comma = false;
        this.specificObjectType = null;
        this.mapFieldFunc = null;
        this.objFieldFunc = null;
        this.objExtFunc = null;
        return true;
    }

    /**
     * 返回specificObjectType
     *
     * @return int
     */
    public Type specificObjectType() {
        return this.specificObjectType;
    }

    /**
     * 获取配置属性
     *
     * @return int
     */
    public final int getFeatures() {
        return features;
    }

    public Writer withFeatures(int features) {
        this.features = features;
        return this;
    }

    protected final boolean tiny() {
        return ConvertFactory.checkTinyFeature(features);
    }

    protected final boolean nullable() {
        return ConvertFactory.checkNullableFeature(features);
    }

    /** 输出null值 */
    public abstract void writeNull();

    /**
     * 是否需要写入类名, BSON需要， JSON不需要
     *
     * @return boolean
     */
    public abstract boolean needWriteClassName();

    /**
     * 写入类名
     *
     * @param clazz 类名
     */
    public abstract void writeClassName(String clazz);

    /**
     * 输出一个对象前的操作 注： 覆盖此方法必须要先调用父方法 super.writeObjectB(obj);
     *
     * @param obj 写入的对象
     * @return 返回-1表示还没有写入对象内容，大于-1表示已写入对象内容，返回对象内容大小
     */
    public int writeObjectB(Object obj) {
        this.comma = false;
        return -1;
    }

    /**
     * 输出一个为null的对象
     *
     * @param clazz 对象的类名
     */
    @ClassDepends
    public final void writeObjectNull(final Class clazz) {
        writeClassName(null);
        writeNull();
    }

    /**
     * 输出一个对象的某个字段
     *
     * @param member 字段
     * @param obj 写入的对象
     */
    @SuppressWarnings("unchecked")
    public void writeObjectField(final EnMember member, Object obj) {
        Object value;
        if (objFieldFunc == null) {
            value = member.getFieldValue(obj);
        } else {
            value = objFieldFunc.apply(member.attribute, obj);
        }
        if (value == null) {
            if (nullable()) {
                Attribute attr = member.getAttribute();
                this.writeFieldName(member, attr.field(), attr.genericType(), member.getPosition());
                writeNull();
                this.comma = true;
            }
            return;
        }
        if (tiny()) {
            if (member.string) {
                if (((CharSequence) value).length() == 0) {
                    return;
                }
            } else if (member.bool) {
                if (!((Boolean) value)) {
                    return;
                }
            }
        }
        Attribute attr = member.getAttribute();
        this.writeFieldName(member, attr.field(), attr.genericType(), member.getPosition());
        member.encoder.convertTo(this, value);
        this.comma = true;
    }

    /**
     * 输出一个对象的某个扩展字段
     *
     * @param fieldName 字段名称
     * @param fieldType 字段类型
     * @param fieldPos 字段顺序
     * @param anyEncoder Encoder
     * @param value 写入的字段对象
     */
    @SuppressWarnings("unchecked")
    public void writeObjectField(
            final String fieldName, Type fieldType, int fieldPos, Encodeable anyEncoder, Object value) {
        if (value == null) {
            if (nullable()) {
                this.writeFieldName(null, fieldName, fieldType, fieldPos);
                writeNull();
                this.comma = true;
            }
            return;
        }
        if (fieldType == null) {
            fieldType = value.getClass();
        }
        if (tiny() && fieldType instanceof Class) {
            Class clazz = (Class) fieldType;
            if (CharSequence.class.isAssignableFrom(clazz)) {
                if (((CharSequence) value).length() == 0) {
                    return;
                }
            } else if (clazz == boolean.class || clazz == Boolean.class) {
                if (!((Boolean) value)) {
                    return;
                }
            }
        }
        this.writeFieldName(null, fieldName, fieldType, fieldPos);
        anyEncoder.convertTo(this, value);
        this.comma = true;
    }

    /**
     * 输出一个字段名
     *
     * @param member 字段
     */
    public final void writeFieldName(final EnMember member) {
        Attribute attr = member.getAttribute();
        this.writeFieldName(member, attr.field(), attr.genericType(), member.getPosition());
    }

    /**
     * 输出一个对象后的操作
     *
     * @param obj 写入的对象
     */
    public abstract void writeObjectE(Object obj);

    /**
     * 输出一个数组前的操作
     *
     * @param size 数组长度
     * @param arrayEncoder Encodeable 可能是ArrayEncoder、CollectionEncoder或StreamEncoder
     * @param componentEncoder Encodeable
     * @param obj 对象, 不一定是数组、Collection对象，也可能是伪Collection对象
     * @return 返回-1表示还没有写入对象内容，大于-1表示已写入对象内容，返回对象内容大小
     */
    public abstract int writeArrayB(int size, Encodeable arrayEncoder, Encodeable componentEncoder, Object obj);

    /** 输出数组元素间的间隔符 */
    public abstract void writeArrayMark();

    /** 输出一个数组后的操作 */
    public abstract void writeArrayE();

    /**
     * 输出一个Map前的操作
     *
     * @param size map大小
     * @param keyEncoder Encodeable
     * @param valueEncoder Encodeable
     * @param obj 对象, 不一定是Map对象，也可能是伪Map对象
     * @return 返回-1表示还没有写入对象内容，大于-1表示已写入对象内容，返回对象内容大小
     */
    public abstract int writeMapB(int size, Encodeable keyEncoder, Encodeable valueEncoder, Object obj);

    /** 输出一个Map中key与value间的间隔符 */
    public abstract void writeMapMark();

    /** 输出一个Map后的操作 */
    public abstract void writeMapE();

    /**
     * 输出一个字段名
     *
     * @param member EnMember
     * @param fieldName 字段名称
     * @param fieldType 字段类型
     * @param fieldPos 字段顺序
     */
    public abstract void writeFieldName(EnMember member, String fieldName, Type fieldType, int fieldPos);

    /**
     * 写入一个boolean值
     *
     * @param value boolean值
     */
    public abstract void writeBoolean(boolean value);

    /**
     * 写入一个byte值
     *
     * @param value byte值
     */
    public abstract void writeByte(byte value);

    /**
     * 写入byte[]
     *
     * @param values byte[]
     */
    public abstract void writeByteArray(byte[] values);

    /**
     * 写入一个char值
     *
     * @param value char值
     */
    public abstract void writeChar(char value);

    /**
     * 写入一个short值
     *
     * @param value short值
     */
    public abstract void writeShort(short value);

    /**
     * 写入一个int值
     *
     * @param value int值
     */
    public abstract void writeInt(int value);

    /**
     * 写入一个long值
     *
     * @param value long值
     */
    public abstract void writeLong(long value);

    /**
     * 写入一个float值
     *
     * @param value float值
     */
    public abstract void writeFloat(float value);

    /**
     * 写入一个double值
     *
     * @param value double值
     */
    public abstract void writeDouble(double value);

    /**
     * 写入无转义字符长度不超过255的字符串， 例如枚举值、字段名、类名字符串等 *
     *
     * @param value 非空且不含需要转义的字符的String值
     */
    public abstract void writeSmallString(String value);

    /**
     * 写入一个String值
     *
     * @param value String值
     */
    public abstract void writeString(String value);

    /**
     * 写入一个StringConvertWrapper值
     *
     * @param value StringConvertWrapper值
     */
    public abstract void writeWrapper(StringWrapper value);
}
