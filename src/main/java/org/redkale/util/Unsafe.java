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

    public int getInt(Object o, long offset);

    public void putInt(Object o, long offset, int x);

    public Object getObject(Object o, long offset);

    public void putObject(Object o, long offset, Object x);

    public boolean getBoolean(Object o, long offset);

    public void putBoolean(Object o, long offset, boolean x);

    public byte getByte(Object o, long offset);

    public void putByte(Object o, long offset, byte x);

    public short getShort(Object o, long offset);

    public void putShort(Object o, long offset, short x);

    public char getChar(Object o, long offset);

    public void putChar(Object o, long offset, char x);

    public long getLong(Object o, long offset);

    public void putLong(Object o, long offset, long x);

    public float getFloat(Object o, long offset);

    public void putFloat(Object o, long offset, float x);

    public double getDouble(Object o, long offset);

    public void putDouble(Object o, long offset, double x);

    public byte getByte(long address);

    public void putByte(long address, byte x);

    public short getShort(long address);

    public void putShort(long address, short x);

    public char getChar(long address);

    public void putChar(long address, char x);

    public int getInt(long address);

    public void putInt(long address, int x);

    public long getLong(long address);

    public void putLong(long address, long x);

    public float getFloat(long address);

    public void putFloat(long address, float x);

    public double getDouble(long address);

    public void putDouble(long address, double x);

    public long getAddress(long address);

    public void putAddress(long address, long x);

    public long allocateMemory(long bytes);

    public long reallocateMemory(long address, long bytes);

    public void setMemory(Object o, long offset, long bytes, byte value);

    public void setMemory(long address, long bytes, byte value);

    public void copyMemory(Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes);

    public void copyMemory(long srcAddress, long destAddress, long bytes);

    public void freeMemory(long address);

    public long objectFieldOffset(Field f);

    public long staticFieldOffset(Field f);

    public Object staticFieldBase(Field f);

    public int arrayBaseOffset(Class<?> arrayClass);

    public int arrayIndexScale(Class<?> arrayClass);

    public int addressSize();

    public int pageSize();

    public Object allocateInstance(Class<?> cls) throws InstantiationException;

    public void throwException(Throwable ee);

    public boolean compareAndSwapObject(Object o, long offset, Object expected, Object x);

    public boolean compareAndSwapInt(Object o, long offset, int expected, int x);

    public boolean compareAndSwapLong(Object o, long offset, long expected, long x);

    public Object getObjectVolatile(Object o, long offset);

    public void putObjectVolatile(Object o, long offset, Object x);

    public int getIntVolatile(Object o, long offset);

    public void putIntVolatile(Object o, long offset, int x);

    public boolean getBooleanVolatile(Object o, long offset);

    public void putBooleanVolatile(Object o, long offset, boolean x);

    public byte getByteVolatile(Object o, long offset);

    public void putByteVolatile(Object o, long offset, byte x);

    public short getShortVolatile(Object o, long offset);

    public void putShortVolatile(Object o, long offset, short x);

    public char getCharVolatile(Object o, long offset);

    public void putCharVolatile(Object o, long offset, char x);

    public long getLongVolatile(Object o, long offset);

    public void putLongVolatile(Object o, long offset, long x);

    public float getFloatVolatile(Object o, long offset);

    public void putFloatVolatile(Object o, long offset, float x);

    public double getDoubleVolatile(Object o, long offset);

    public void putDoubleVolatile(Object o, long offset, double x);

    public void putOrderedObject(Object o, long offset, Object x);

    public void putOrderedInt(Object o, long offset, int x);

    public void putOrderedLong(Object o, long offset, long x);

    public void unpark(Object thread);

    public void park(boolean isAbsolute, long time);

    public int getLoadAverage(double[] loadavg, int nelems);

    public int getAndAddInt(Object o, long offset, int delta);

    public long getAndAddLong(Object o, long offset, long delta);

    public int getAndSetInt(Object o, long offset, int newValue);

    public long getAndSetLong(Object o, long offset, long newValue);

    public Object getAndSetObject(Object o, long offset, Object newValue);

    public void loadFence();

    public void storeFence();

    public void fullFence();

    public void invokeCleaner(java.nio.ByteBuffer directBuffer);
}
