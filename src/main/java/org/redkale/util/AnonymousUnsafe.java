//package org.redkale.util;
//
//import java.lang.reflect.Field;
//import java.util.Objects;
//
//public class AnonymousUnsafe implements Unsafe {
//
//    private final sun.misc.Unsafe unsafe;
//
//    public AnonymousUnsafe() {
//        sun.misc.Unsafe unsafe0 = null;
//        try {
//            Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
//            field.setAccessible(true);
//            unsafe0 = (sun.misc.Unsafe) field.get(null);
//        } catch (Throwable e) {
//            throw new RedkaleException("init unsafe error", e);
//        }
//        this.unsafe = Objects.requireNonNull(unsafe0);
//    }
//
//    @Override
//    public boolean getBoolean(Object o, long offset) {
//        return unsafe.getBoolean(o, offset);
//    }
//
//    @Override
//    public byte getByte(Object o, long offset) {
//        return unsafe.getByte(o, offset);
//    }
//
//    @Override
//    public short getShort(Object o, long offset) {
//        return unsafe.getShort(o, offset);
//    }
//
//    @Override
//    public char getChar(Object o, long offset) {
//        return unsafe.getChar(o, offset);
//    }
//
//    @Override
//    public int getInt(Object o, long offset) {
//        return unsafe.getInt(o, offset);
//    }
//
//    @Override
//    public long getLong(Object o, long offset) {
//        return unsafe.getLong(o, offset);
//    }
//
//    @Override
//    public float getFloat(Object o, long offset) {
//        return unsafe.getFloat(o, offset);
//    }
//
//    @Override
//    public double getDouble(Object o, long offset) {
//        return unsafe.getDouble(o, offset);
//    }
//
//    @Override
//    public Object getObject(Object o, long offset) {
//        return unsafe.getObject(o, offset);
//    }
//
//    @Override
//    public long objectFieldOffset(Field f) {
//        return unsafe.objectFieldOffset(f);
//    }
//
//    @Override
//    public long staticFieldOffset(Field f) {
//        return unsafe.staticFieldOffset(f);
//    }
//}
