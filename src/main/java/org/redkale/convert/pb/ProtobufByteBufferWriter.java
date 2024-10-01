/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.pb;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Supplier;
import org.redkale.util.ByteArray;
import org.redkale.util.Utility;

/** @author zhangjx */
public class ProtobufByteBufferWriter extends ProtobufWriter {

    private final Supplier<ByteBuffer> supplier;

    private ByteBuffer[] buffers;

    private int currBufIndex;

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
        this.currBufIndex = 0;
        return false;
    }

    @Override
    public final ProtobufWriter pollChild() {
        ProtobufWriter rs = super.pollChild();
        this.delegate = null;
        this.child = null;
        rs.parent = null;
        return rs;
    }

    @Override
    public final void offerChild(ProtobufWriter child) {
        int total = child.length();
        ProtobufWriter next = child;
        while ((next = next.child) != null) {
            total += next.length();
        }
        writeLength(total);
        writeTo(child.content(), 0, child.length());
        next = child;
        while ((next = next.child) != null) {
            writeTo(next.content(), 0, next.length());
        }
        offerPool(child);
    }

    @Override
    public ByteBuffer[] toBuffers() {
        if (buffers == null) {
            return new ByteBuffer[0];
        }
        for (int i = currBufIndex; i < this.buffers.length; i++) {
            ByteBuffer buf = this.buffers[i];
            if (buf.position() != 0) {
                buf.flip();
            }
        }
        return this.buffers;
    }

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
    protected int expand(final int byteLength) {
        if (this.buffers == null) {
            this.currBufIndex = 0;
            this.buffers = new ByteBuffer[] {supplier.get()};
        }
        ByteBuffer buffer = this.buffers[currBufIndex];
        if (!buffer.hasRemaining()) {
            buffer.flip();
            buffer = supplier.get();
            this.buffers = Utility.append(this.buffers, buffer);
            this.currBufIndex++;
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
    public void writeTo(final byte ch) {
        expand(1);
        this.buffers[currBufIndex].put(ch);
        count++;
    }

    @Override
    public void writeTo(final byte[] chs, final int start, final int len) {
        if (expand(len) == 0) {
            this.buffers[currBufIndex].put(chs, start, len);
        } else {
            ByteBuffer buffer = this.buffers[currBufIndex];
            final int end = start + len;
            int remain = len; // 还剩多少没有写
            while (remain > 0) {
                final int br = buffer.remaining();
                if (remain > br) { // 一个buffer写不完
                    buffer.put(chs, end - remain, br).flip();
                    buffer = this.buffers[++this.currBufIndex];
                    remain -= br;
                } else {
                    buffer.put(chs, end - remain, remain);
                    remain = 0;
                }
            }
        }
        this.count += len;
    }

    @Override
    protected final void writeUInt32(int value) {
        if (value >= 0 && value < TENTHOUSAND_MAX) {
            writeTo(TENTHOUSAND_UINT_BYTES[value]);
            return;
        } else if (value < 0 && value > TENTHOUSAND_MAX) {
            writeTo(TENTHOUSAND_UINT_BYTES2[-value]);
            return;
        }
        while (true) {
            if ((value & ~0x7F) == 0) {
                writeTo((byte) value);
                return;
            } else {
                writeTo((byte) ((value & 0x7F) | 0x80));
                value >>>= 7;
            }
        }
    }

    @Override
    protected final void writeUInt64(long value) {
        if (value >= 0 && value < TENTHOUSAND_MAX) {
            writeTo(TENTHOUSAND_UINT_BYTES[(int) value]);
            return;
        } else if (value < 0 && value > TENTHOUSAND_MAX) {
            writeTo(TENTHOUSAND_UINT_BYTES2[(int) -value]);
            return;
        }
        while (true) {
            if ((value & ~0x7FL) == 0) {
                writeTo((byte) value);
                return;
            } else {
                writeTo((byte) ((value & 0x7F) | 0x80));
                value >>>= 7;
            }
        }
    }

    @Override
    public String toString() {
        return Objects.toString(this);
    }

    @Override
    public final ProtobufWriter clear() {
        throw new UnsupportedOperationException("Not supported yet."); // 无需实现
    }

    @Override
    public final byte[] toArray() {
        return toByteArray().getBytes();
    }

    @Override
    public final byte[] content() {
        throw new UnsupportedOperationException("Not supported yet."); // 无需实现
    }
}
