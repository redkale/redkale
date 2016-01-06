/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import org.redkale.util.Attribute;

/**
 *
 * <p>
 * 详情见: http://www.redkale.org
 *
 * @author zhangjx
 */
public interface Writer {

    /**
     * 当tiny=true时， 字符串为空、boolean为false的字段值都会被跳过， 不会输出。
     *
     * @return 是否简化
     */
    public boolean tiny();

    /**
     * 输出null值
     */
    public void writeNull();

    /**
     * 写入类名
     *
     * @param clazz 类名
     */
    public void wirteClassName(String clazz);

    /**
     * 输出一个对象前的操作
     *
     * @param fieldCount 字段个数
     *
     * @param obj        写入的对象
     */
    public void writeObjectB(int fieldCount, Object obj);

    /**
     * 输出一个对象后的操作
     *
     * @param obj 写入的对象
     */
    public void writeObjectE(Object obj);

    /**
     * 输出一个数组前的操作
     *
     * @param size 数组长度
     */
    public void writeArrayB(int size);

    /**
     * 输出数组元素间的间隔符
     *
     */
    public void writeArrayMark();

    /**
     * 输出一个数组后的操作
     *
     */
    public void writeArrayE();

    /**
     * 输出一个Map前的操作
     *
     * @param size map大小
     */
    public void writeMapB(int size);

    /**
     * 输出一个Map中key与value间的间隔符
     *
     */
    public void writeMapMark();

    /**
     * 输出一个Map后的操作
     *
     */
    public void writeMapE();

    /**
     * 输出一个字段
     *
     * @param comma     是否非第一个字段
     * @param attribute 字段的Attribute对象
     */
    public void writeField(boolean comma, Attribute attribute);

    /**
     * 写入一个boolean值
     *
     * @param value boolean值
     */
    public void writeBoolean(boolean value);

    /**
     * 写入一个byte值
     *
     * @param value byte值
     */
    public void writeByte(byte value);

    /**
     * 写入一个char值
     *
     * @param value char值
     */
    public void writeChar(char value);

    /**
     * 写入一个short值
     *
     * @param value short值
     */
    public void writeShort(short value);

    /**
     * 写入一个int值
     *
     * @param value int值
     */
    public void writeInt(int value);

    /**
     * 写入一个long值
     *
     * @param value long值
     */
    public void writeLong(long value);

    /**
     * 写入一个float值
     *
     * @param value float值
     */
    public void writeFloat(float value);

    /**
     * 写入一个double值
     *
     * @param value double值
     */
    public void writeDouble(double value);

    /**
     * 写入无转义字符长度不超过255的字符串， 例如枚举值、字段名、类名字符串等 *
     *
     * @param value 非空且不含需要转义的字符的String值
     */
    public void writeSmallString(String value);

    /**
     * 写入一个String值
     *
     * @param value String值
     */
    public void writeString(String value);
}
