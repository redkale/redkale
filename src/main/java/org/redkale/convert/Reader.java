/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import org.redkale.annotation.Nullable;

/**
 * 反序列化的数据读取流
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
public abstract class Reader {

    public enum ValueType {
        STRING,
        ARRAY,
        MAP;
    }

    /**
     * 集合对象为null
     *
     * @see #readArrayB(org.redkale.convert.Decodeable)
     */
    public static final short SIGN_NULL = -1;

    /**
     * 不确定的长度， 比如解析json数组
     *
     * @see #readArrayB(org.redkale.convert.Decodeable)
     */
    public static final short SIGN_VARIABLE = -2;

    /**
     * 设置Reader的内容，通常结合对象池使用
     *
     * @param content 内容
     */
    public abstract void prepare(byte[] content);

    /**
     * 是否还存在下个元素或字段
     *
     * @return 是否还存在下个元素或字段
     */
    public abstract boolean hasNext();

    /**
     * 获取当前位置
     *
     * @return 当前位置
     */
    public abstract int position();

    /** 跳过值(不包含值前面的字段) */
    public abstract void skipValue();

    /** /跳过字段与值之间的多余内容， json就是跳过:符, map跳过: */
    public abstract void readBlank();

    /**
     * 读取下个值的类型
     *
     * @return ValueType
     */
    public abstract ValueType readType();

    /**
     * 读取对象的类名， 返回 null 表示对象为null， 返回空字符串表示当前class与返回的class一致，返回非空字符串表示class是当前class的子类。
     *
     * @param clazz 类名
     * @return 返回字段数
     */
    public String readObjectB(final Class clazz) {
        return null;
    }

    /**
     * 读取对象的尾端
     *
     * @param clazz 类名
     */
    public abstract void readObjectE(final Class clazz);

    /**
     * 读取数组的开头并返回数组的长度
     *
     * @see #SIGN_NULL
     * @see #SIGN_VARIABLE
     * @param componentDecoder Decodeable
     * @return 返回数组的长度
     */
    public abstract int readArrayB(@Nullable Decodeable componentDecoder);

    /** 读取数组的尾端 */
    public abstract void readArrayE();

    /**
     * 读取map的开头并返回map的size
     *
     * @param keyDecoder Decodeable
     * @param valueDecoder Decodeable
     * @return 返回map的size
     */
    public abstract int readMapB(Decodeable keyDecoder, Decodeable valueDecoder);

    /** 读取数组的尾端 */
    public abstract void readMapE();

    /**
     * 根据字段读取字段对应的DeMember
     *
     * @param memberInfo DeMember信息
     *
     * @return 匹配的DeMember
     */
    public abstract DeMember readFieldName(final DeMemberInfo memberInfo);

    /**
     * 读取一个boolean值
     *
     * @return boolean值
     */
    public abstract boolean readBoolean();

    /**
     * 读取一个byte值
     *
     * @return byte值
     */
    public abstract byte readByte();

    /**
     * 读取byte[]
     *
     * @return byte[]
     */
    public abstract byte[] readByteArray();

    /**
     * 读取一个char值
     *
     * @return char值
     */
    public abstract char readChar();

    /**
     * 读取一个short值
     *
     * @return short值
     */
    public abstract short readShort();

    /**
     * 读取一个int值
     *
     * @return int值
     */
    public abstract int readInt();

    /**
     * 读取一个long值
     *
     * @return long值
     */
    public abstract long readLong();

    /**
     * 读取一个float值
     *
     * @return float值
     */
    public abstract float readFloat();

    /**
     * 读取一个double值
     *
     * @return double值
     */
    public abstract double readDouble();

    /**
     * 读取无转义字符长度不超过255的字符串， 例如枚举值、字段名、类名字符串等
     *
     * @return String值
     */
    public abstract String readSmallString();

    /**
     * 读取反解析对象的类名
     *
     * @return 类名
     */
    public abstract String readClassName();

    /**
     * 读取一个String值
     *
     * @return String值
     */
    public abstract String readString();
}
