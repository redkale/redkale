/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

/**
 * 以ByteBuffer为数据载体的Writer
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class ByteBufferWriter {

    private final Supplier<ByteBuffer> supplier;

    private ByteBuffer[] buffers;

    protected ByteBufferWriter(Supplier<ByteBuffer> supplier) {
        this.supplier = supplier;
    }

    public static ByteBufferWriter create(Supplier<ByteBuffer> supplier) {
        return new ByteBufferWriter(supplier);
    }

    private ByteBuffer getLastBuffer(int size) {
        if (this.buffers == null) {
            ByteBuffer buf = supplier.get();
            this.buffers = Utility.append(this.buffers, buf);
            return buf;
        } else if (this.buffers[this.buffers.length - 1].remaining() < size) {
            ByteBuffer buf = supplier.get();
            this.buffers = Utility.append(this.buffers, buf);
            return buf;
        }
        return this.buffers[this.buffers.length - 1];
    }

    public ByteBuffer[] toBuffers() {
        if (buffers == null) return new ByteBuffer[0];
        for (ByteBuffer buf : this.buffers) {
            if (buf.position() != 0) buf.flip();
        }
        return this.buffers;
    }

    public ByteBufferWriter put(byte b) {
        getLastBuffer(1).put(b);
        return this;
    }

    public ByteBufferWriter put(short value) {
        getLastBuffer(2).putShort(value);
        return this;
    }

    public ByteBufferWriter putInt(int value) {
        getLastBuffer(4).putInt(value);
        return this;
    }

    public ByteBufferWriter putFloat(float value) {
        getLastBuffer(4).putFloat(value);
        return this;
    }

    public ByteBufferWriter putLong(long value) {
        getLastBuffer(8).putLong(value);
        return this;
    }

    public ByteBufferWriter putDouble(double value) {
        getLastBuffer(8).putDouble(value);
        return this;
    }

    public ByteBufferWriter put(byte[] src) {
        return put(src, 0, src.length);
    }

    public ByteBufferWriter put(byte[] src, int offset, int length) {
        ByteBuffer buf = getLastBuffer(1);
        int remain = buf.remaining();
        if (remain >= length) {
            buf.put(src, offset, length);
        } else {
            buf.put(src, offset, remain);
            put(src, offset + remain, length - remain);
        }
        return this;
    }

}
