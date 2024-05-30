/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.proto;

import java.nio.ByteBuffer;
import java.util.function.Supplier;
import org.redkale.util.Utility;

/** @author zhangjx */
public class ProtobufByteBufferWriter extends ProtobufWriter {

    private final Supplier<ByteBuffer> supplier;

    private ByteBuffer[] buffers;

    private int index;

    public ProtobufByteBufferWriter(int features, boolean enumtostring, Supplier<ByteBuffer> supplier) {
        super((byte[]) null);
        this.features = features;
        this.enumtostring = enumtostring;
        this.supplier = supplier;
    }

    @Override
    protected boolean recycle() {
        super.recycle();
        this.buffers = null;
        this.index = 0;
        return false;
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
    public byte[] toArray() {
        if (buffers == null) {
            return new byte[0];
        }
        int pos = 0;
        byte[] bytes = new byte[this.count];
        for (ByteBuffer buf : toBuffers()) {
            int r = buf.remaining();
            buf.get(bytes, pos, r);
            buf.flip();
            pos += r;
        }
        return bytes;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "[count=" + this.count + "]";
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
    public byte[] content() {
        throw new UnsupportedOperationException("Not supported yet."); // 无需实现
    }

    @Override
    public int offset() {
        throw new UnsupportedOperationException("Not supported yet."); // 无需实现
    }

    @Override
    public int length() {
        throw new UnsupportedOperationException("Not supported yet."); // 无需实现
    }
}
