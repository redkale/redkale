/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.test.convert;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

/**
 *
 * @author zhangjx
 */
public abstract class ConvertHelper {
    private ConvertHelper() {
        //
    }

    public static byte[] toBytes(ByteBuffer[] buffers) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (ByteBuffer buffer : buffers) {
            byte[] bs = new byte[buffer.remaining()];
            buffer.get(bs);
            out.write(bs, 0, bs.length);
        }
        return out.toByteArray();
    }

    public static Supplier<ByteBuffer> createSupplier() {
        return () -> ByteBuffer.allocate(1024);
    }

    public static ByteBuffer createByteBuffer(byte[] bs) {
        return ByteBuffer.wrap(bs);
    }

    public static InputStream createInputStream(byte[] bs) {
        return new ByteArrayInputStream(bs);
    }
}
