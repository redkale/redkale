/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.bson;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Supplier;
import org.redkale.util.ByteArray;
import org.redkale.util.Utility;

/**
 * 以ByteBuffer为数据载体的BsonWriter
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class BsonByteBufferWriter extends BsonWriter {

    private final Supplier<ByteBuffer> supplier;

    private ByteBuffer[] buffers;

    private int index;

    public BsonByteBufferWriter(Supplier<ByteBuffer> supplier) {
        this(0, supplier);
    }

    protected BsonByteBufferWriter(int features, Supplier<ByteBuffer> supplier) {
        super((byte[]) null);
        this.features = features;
        this.supplier = supplier;
    }

    @Override
    public ByteBuffer[] toBuffers() {
        if (buffers == null) {
            return new ByteBuffer[0];
        }
        for (int i = index; i < this.buffers.length; i++) {
            ByteBuffer buf = this.buffers[i];
            if (buf.position() != 0) {
                buf.flip();
            }
        }
        return this.buffers;
    }

    @Override
    public ByteArray toByteArray() {
        ByteArray array = new ByteArray();
        if (buffers != null) {
            for (ByteBuffer buf : toBuffers()) {
                array.put(buf);
                buf.flip();
            }
        }
        return array;
    }

    @Override
    public String toString() {
        return Objects.toString(this);
    }

    @Override
    protected int expand(final int byteLength) {
        if (this.buffers == null) {
            this.index = 0;
            this.buffers = new ByteBuffer[] {supplier.get()};
        }
        ByteBuffer buffer = this.buffers[index];
        if (!buffer.hasRemaining()) {
            buffer.flip();
            buffer = supplier.get();
            this.buffers = Utility.append(this.buffers, buffer);
            this.index++;
        }
        int len = buffer.remaining();
        int size = 0;
        while (len < byteLength) {
            buffer = supplier.get();
            this.buffers = Utility.append(this.buffers, buffer);
            len += buffer.remaining();
            size++;
        }
        return size;
    }

    @Override
    public void writeTo(final byte[] chs, final int start, final int len) {
        if (expand(len) == 0) {
            this.buffers[index].put(chs, start, len);
        } else {
            ByteBuffer buffer = this.buffers[index];
            final int end = start + len;
            int remain = len; // 还剩多少没有写
            while (remain > 0) {
                final int br = buffer.remaining();
                if (remain > br) { // 一个buffer写不完
                    buffer.put(chs, end - remain, br);
                    buffer = nextByteBuffer();
                    remain -= br;
                } else {
                    buffer.put(chs, end - remain, remain);
                    remain = 0;
                }
            }
        }
        this.count += len;
    }

    private ByteBuffer nextByteBuffer() {
        this.buffers[this.index].flip();
        return this.buffers[++this.index];
    }

    @Override
    public void writeTo(final byte ch) {
        expand(1);
        this.buffers[index].put(ch);
        count++;
    }

    @Override
    protected boolean recycle() {
        super.recycle();
        this.index = 0;
        this.specificObjectType = null;
        this.buffers = null;
        return false;
    }

    @Override
    public final byte[] toArray() {
        throw new UnsupportedOperationException("Not supported yet."); // 无需实现
    }

    @Override
    public final byte[] content() {
        throw new UnsupportedOperationException("Not supported yet."); // 无需实现
    }

    @Override
    public final int offset() {
        throw new UnsupportedOperationException("Not supported yet."); // 无需实现
    }

    @Override
    public final int length() {
        throw new UnsupportedOperationException("Not supported yet."); // 无需实现
    }
}
