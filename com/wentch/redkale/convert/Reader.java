/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.convert;

import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author zhangjx
 */
public interface Reader {

    public static final short SIGN_NULL = -1;

    public static final short SIGN_NOLENGTH = -2;

    /**
     * 是否还存在下个元素或字段
     *
     * @return
     */
    public boolean hasNext();

    /**
     * 跳过值(不包含值前面的字段)
     */
    public void skipValue();

    /**
     * /跳过字段与值之间的多余内容， json就是跳过:符, map跳过:
     */
    public void skipBlank();

    /**
     * 读取对象的开头 返回字段数
     *
     * @return
     */
    public int readObjectB();

    /**
     * 读取对象的尾端
     *
     */
    public void readObjectE();

    /**
     * 读取数组的开头并返回数组的长度
     *
     * @return
     */
    public int readArrayB();

    /**
     * 读取数组的尾端
     *
     */
    public void readArrayE();

    /**
     * 读取map的开头并返回map的size
     *
     * @return
     */
    public int readMapB();

    /**
     * 读取数组的尾端
     *
     */
    public void readMapE();

    /**
     * 根据字段读取字段对应的DeMember
     *
     * @param index
     * @param members
     * @return
     */
    public DeMember readField(final AtomicInteger index, final DeMember[] members);

    public boolean readBoolean();

    public byte readByte();

    public char readChar();

    public short readShort();

    public int readInt();

    public long readLong();

    public float readFloat();

    public double readDouble();

    /**
     * 读取无转义字符长度不超过255的字符串， 例如枚举值、字段名、类名字符串等
     *
     * @return
     */
    public String readSmallString();

    public String readClassName();

    public String readString();

}
