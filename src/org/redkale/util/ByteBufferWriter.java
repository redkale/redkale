/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.nio.*;
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

    private int position;

    private boolean bigEndian = true;

    protected ByteBufferWriter(Supplier<ByteBuffer> supplier) {
        this.supplier = supplier;
    }

    public static ByteBufferWriter create(Supplier<ByteBuffer> supplier) {
        return new ByteBufferWriter(supplier);
    }

    public static ByteBuffer[] toBuffers(Supplier<ByteBuffer> supplier, byte[] content) {
        return new ByteBufferWriter(supplier).put(content, 0, content.length).toBuffers();
    }

    public static ByteBuffer[] toBuffers(Supplier<ByteBuffer> supplier, byte[] content, int offset, int length) {
        return new ByteBufferWriter(supplier).put(content, offset, length).toBuffers();
    }

    private ByteBuffer getLastBuffer(int size) {
        if (this.buffers == null) {
            ByteBuffer buf = supplier.get();
            this.bigEndian = buf.order() == ByteOrder.BIG_ENDIAN;
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
            buf.flip();
        }
        return this.buffers;
    }

    public int position() {
        return position;
    }

    public ByteBufferWriter put(byte b) {
        getLastBuffer(1).put(b);
        position++;
        return this;
    }

    public ByteBufferWriter putShort(short value) {
        getLastBuffer(2).putShort(value);
        position += 2;
        return this;
    }

    public ByteBufferWriter putInt(int value) {
        getLastBuffer(4).putInt(value);
        position += 4;
        return this;
    }

    //重新设置指定位置的值
    public ByteBufferWriter putInt(final int index, int value) {
        int start = 0;
        ByteBuffer[] buffs = this.buffers;
        for (int i = 0; i < buffs.length; i++) {
            int pos = buffs[i].position();
            if (pos + start > index) {
                int r = pos + start - index;
                if (r >= 4) {
                    buffs[i].putInt(index - start, value);
                    return this;
                } else {
                    byte b1 = bigEndian ? (byte) ((value >> 24) & 0xFF) : (byte) (value & 0xFF);
                    byte b2 = bigEndian ? (byte) ((value >> 16) & 0xFF) : (byte) ((value >> 8) & 0xFF);
                    byte b3 = bigEndian ? (byte) ((value >> 8) & 0xFF) : (byte) ((value >> 16) & 0xFF);
                    byte b4 = bigEndian ? (byte) (value & 0xFF) : (byte) ((value >> 24) & 0xFF);
                    if (r == 3) {
                        buffs[i].put(index - start, b1);
                        buffs[i].put(index - start + 1, b2);
                        buffs[i].put(index - start + 2, b3);
                        buffs[i + 1].put(0, b4);
                    } else if (r == 2) {
                        buffs[i].put(index - start, b1);
                        buffs[i].put(index - start + 1, b2);
                        buffs[i + 1].put(0, b3);
                        buffs[i + 1].put(1, b4);
                    } else if (r == 1) {
                        buffs[i].put(index - start, b1);
                        buffs[i + 1].put(0, b2);
                        buffs[i + 1].put(1, b3);
                        buffs[i + 1].put(2, b4);
                    }
                    return this;
                }
            } else {
                start += pos;
            }
        }
        throw new ArrayIndexOutOfBoundsException(index);
    }

//    public static void main(String[] args) throws Throwable {
//        ObjectPool<ByteBuffer> pool = new ObjectPool<>(20, (p) -> ByteBuffer.allocate(10), (ByteBuffer t) -> t.clear(), (ByteBuffer t) -> false);
//        ByteBufferWriter writer = ByteBufferWriter.create(pool);
//        for (int i = 1; i <= 18; i++) {
//            writer.put((byte) i);
//        }
//        System.out.println(Arrays.toString(toBytes(writer.toBuffers())));
//
//        writer = ByteBufferWriter.create(pool);
//        for (int i = 1; i <= 18; i++) {
//            writer.put((byte) i);
//        }
//        int value = 0x223344;
//        byte[] b4 = new byte[]{(byte) ((value >> 24) & 0xFF), (byte) ((value >> 16) & 0xFF), (byte) ((value >> 8) & 0xFF), (byte) (value & 0xFF)};
//        writer.putInt(9, value);
//        System.out.println(Arrays.toString(b4));
//        System.out.println(Arrays.toString(toBytes(writer.toBuffers())));
//    }
    public ByteBufferWriter putFloat(float value) {
        getLastBuffer(4).putFloat(value);
        position += 4;
        return this;
    }

    public ByteBufferWriter putLong(long value) {
        getLastBuffer(8).putLong(value);
        position += 8;
        return this;
    }

    public ByteBufferWriter putDouble(double value) {
        getLastBuffer(8).putDouble(value);
        position += 8;
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
            position += length;
        } else {
            buf.put(src, offset, remain);
            position += remain;
            put(src, offset + remain, length - remain);
        }
        return this;
    }

}
