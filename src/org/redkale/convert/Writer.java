/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import org.redkale.util.Attribute;

/**
 *
 * @author zhangjx
 */
public interface Writer {

    /**
     * 当tiny=true时， 字符串为空、boolean为false的字段值都会被跳过， 不会输出。
     * <p>
     * @return
     */
    public boolean isTiny();

    /**
     * 输出null值
     */
    public void writeNull();

    /**
     *
     * @param clazz
     */
    public void wirteClassName(String clazz);

    /**
     * 输出一个对象前的操作
     *
     * @param fieldCount 字段个数
     *
     * @param obj
     */
    public void writeObjectB(int fieldCount, Object obj);

    /**
     * 输出一个对象后的操作
     *
     * @param obj
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
     * @param attribute
     */
    public void writeField(boolean comma, Attribute attribute);

    public void writeBoolean(boolean value);

    public void writeByte(byte value);

    public void writeChar(char value);

    public void writeShort(short value);

    public void writeInt(int value);

    public void writeLong(long value);

    public void writeFloat(float value);

    public void writeDouble(double value);

    /**
     * 写入无转义字符长度不超过255的字符串， 例如枚举值、字段名、类名字符串等 *
     *
     * @param value
     */
    public void writeSmallString(String value);

    public void writeString(String value);
}
