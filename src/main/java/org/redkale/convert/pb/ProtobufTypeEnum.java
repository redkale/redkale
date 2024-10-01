/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.convert.pb;

import java.lang.reflect.Type;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * @author zhangjx
 */
public enum ProtobufTypeEnum {
    // boolean/byte/char/short/int/long
    INT(0),
    // double
    DOUBLE(1),
    // float
    FLOAT(5),
    // byte[]
    BYTES(2);

    private int value;

    private ProtobufTypeEnum(int v) {
        this.value = v;
    }

    public int getValue() {
        return value;
    }

    public static ProtobufTypeEnum valueOf(Type type, boolean enumtostring) {
        if (type == double.class || type == Double.class) {
            return DOUBLE;
        } else if (type == float.class || type == Float.class) {
            return FLOAT;
        } else if ((type == boolean.class || type == Boolean.class)
                || (type == byte.class || type == Byte.class)
                || (type == char.class || type == Character.class)
                || (type == short.class || type == Short.class)
                || (type == int.class || type == Integer.class)
                || (type == long.class || type == Long.class)
                || (type == AtomicBoolean.class || type == AtomicInteger.class)
                || type == AtomicLong.class) {
            return INT;
        } else if (!enumtostring && (type instanceof Class) && ((Class) type).isEnum()) {
            return INT;
        } else { // byte[]
            return BYTES;
        }
    }
}
