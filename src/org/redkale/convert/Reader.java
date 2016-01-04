/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * <p>
 * 详情见: http://www.redkale.org
 *
 * @author zhangjx
 */
public interface Reader {

    public static final short SIGN_NULL = -1;

    public static final short SIGN_NOLENGTH = -2;

    /**
     * 是否还存在下个元素或字段
     *
     * @return 是否还存在下个元素或字段
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
     * @return 返回字段数
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
     * @return 返回数组的长度
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
     * @return 返回map的size
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
     * @param index   当前members的游标位置
     * @param members DeMember的全量集合
     * @return 匹配的DeMember
     */
    public DeMember readField(final AtomicInteger index, final DeMember[] members);

    /**
     * 读取一个boolean值
     *
     * @return boolean值
     */
    public boolean readBoolean();

    /**
     * 读取一个byte值
     *
     * @return byte值
     */
    public byte readByte();

    /**
     * 读取一个char值
     *
     * @return char值
     */
    public char readChar();

    /**
     * 读取一个short值
     *
     * @return short值
     */
    public short readShort();

    /**
     * 读取一个int值
     *
     * @return int值
     */
    public int readInt();

    /**
     * 读取一个long值
     *
     * @return long值
     */
    public long readLong();

    /**
     * 读取一个float值
     *
     * @return float值
     */
    public float readFloat();

    /**
     * 读取一个double值
     *
     * @return double值
     */
    public double readDouble();

    /**
     * 读取无转义字符长度不超过255的字符串， 例如枚举值、字段名、类名字符串等
     *
     * @return String值
     */
    public String readSmallString();

    /**
     * 读取反解析对象的类名
     *
     * @return 类名
     */
    public String readClassName();

    /**
     * 读取一个String值
     *
     * @return String值
     */
    public String readString();

}
