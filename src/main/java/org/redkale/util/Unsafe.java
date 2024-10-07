/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.lang.reflect.Field;

/**
 * sun.misc.Unsafe 封装类
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.5.0
 */
public interface Unsafe {

    public boolean getBoolean(Object o, long offset);

    public byte getByte(Object o, long offset);

    public short getShort(Object o, long offset);

    public char getChar(Object o, long offset);

    public int getInt(Object o, long offset);

    public long getLong(Object o, long offset);

    public float getFloat(Object o, long offset);

    public double getDouble(Object o, long offset);

    public Object getObject(Object o, long offset);

    public long objectFieldOffset(Field f);

    public long staticFieldOffset(Field f);
}
