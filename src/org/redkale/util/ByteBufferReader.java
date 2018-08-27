/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.nio.*;
import java.util.*;

/**
 * 以ByteBuffer为数据载体的Reader <br>
 * 注意：最小可读空间至少是8
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class ByteBufferReader {

    private ByteBuffer[] buffers;

    private int currIndex;

    private ByteBuffer currBuffer;

    private final boolean bigEndian;

    public ByteBufferReader(Collection<ByteBuffer> buffers) {
        Objects.requireNonNull(buffers);
        this.buffers = buffers.toArray(new ByteBuffer[buffers.size()]);
        this.currBuffer = this.buffers[0];
        this.currIndex = 0;
        this.bigEndian = this.currBuffer.order() == ByteOrder.BIG_ENDIAN;
    }

    public ByteBufferReader(ByteBuffer[] buffers) {
        Objects.requireNonNull(buffers);
        this.buffers = buffers;
        this.currBuffer = this.buffers[0];
        this.currIndex = 0;
        this.bigEndian = this.currBuffer.order() == ByteOrder.BIG_ENDIAN;
    }

    public ByteBufferReader(ByteBuffer buffer) {
        Objects.requireNonNull(buffer);
        this.buffers = new ByteBuffer[]{buffer};
        this.currBuffer = this.buffers[0];
        this.currIndex = 0;
        this.bigEndian = this.currBuffer.order() == ByteOrder.BIG_ENDIAN;
    }

    public static ByteBufferReader create(ByteBuffer buffer) {
        return new ByteBufferReader(buffer);
    }

    public static ByteBufferReader create(Collection<ByteBuffer> buffers) {
        return new ByteBufferReader(buffers);
    }

    public static ByteBufferReader create(ByteBuffer[] buffers) {
        return new ByteBufferReader(buffers);
    }

    public static byte[] toBytes(ByteBuffer[] buffers) {
        if (buffers == null) return null;
        int size = 0;
        for (ByteBuffer buffer : buffers) {
            size += buffer.remaining();
        }
        byte[] bs = new byte[size];
        int index = 0;
        for (ByteBuffer buffer : buffers) {
            int remain = buffer.remaining();
            buffer.get(bs, index, remain);
            index += remain;
        }
        return bs;
    }

    public boolean hasRemaining() {
        return this.currBuffer.hasRemaining();
    }

    public byte get() {
        ByteBuffer buf = this.currBuffer;
        if (!buf.hasRemaining()) {
            buf = this.buffers[++this.currIndex];
            this.currBuffer = buf;
        }
        return this.currBuffer.get();
    }

    public short getShort() {
        ByteBuffer buf = this.currBuffer;
        int remain = buf.remaining();
        if (remain >= 2) return buf.getShort();
        if (remain == 0) {
            buf = this.buffers[++this.currIndex];
            this.currBuffer = buf;
            return buf.getShort();
        }
        if (bigEndian) return (short) ((buf.get() << 8) | (get() & 0xff));
        return (short) ((buf.get() & 0xff) | (get() << 8));
    }

    public int getInt() {
        ByteBuffer buf = this.currBuffer;
        int remain = buf.remaining();
        if (remain >= 4) return buf.getInt();
        if (remain == 0) {
            buf = this.buffers[++this.currIndex];
            this.currBuffer = buf;
            return buf.getInt();
        }
        if (bigEndian) {
            if (remain == 1) {
                return ((buf.get() << 24)
                    | ((get() & 0xff) << 16)
                    | ((get() & 0xff) << 8)
                    | ((get() & 0xff)));
            }
            if (remain == 2) {
                return ((buf.get() << 24)
                    | ((buf.get() & 0xff) << 16)
                    | ((get() & 0xff) << 8)
                    | ((get() & 0xff)));
            }
            //remain == 3
            return ((buf.get() << 24)
                | ((buf.get() & 0xff) << 16)
                | ((buf.get() & 0xff) << 8)
                | ((get() & 0xff)));
        }
        if (remain == 1) {
            return ((buf.get() & 0xff)
                | ((get() & 0xff) << 8)
                | ((get() & 0xff) << 16)
                | ((get() << 24)));
        }
        if (remain == 2) {
            return ((buf.get() & 0xff)
                | ((buf.get() & 0xff) << 8)
                | ((get() & 0xff) << 16)
                | ((get() << 24)));
        }
        //remain == 3
        return ((buf.get()) & 0xff)
            | ((buf.get() & 0xff) << 8)
            | ((buf.get() & 0xff) << 16)
            | ((get() << 24));
    }

    public float getFloat() {
        return Float.intBitsToFloat(getInt());
    }

    public long getLong() {
        ByteBuffer buf = this.currBuffer;
        int remain = buf.remaining();
        if (remain >= 8) return buf.getLong();
        if (remain == 0) {
            buf = this.buffers[++this.currIndex];
            this.currBuffer = buf;
            return buf.getLong();
        }
        if (bigEndian) {
            if (remain == 1) {
                return ((((long) buf.get()) << 56)
                    | (((long) get() & 0xff) << 48)
                    | (((long) get() & 0xff) << 40)
                    | (((long) get() & 0xff) << 32)
                    | (((long) get() & 0xff) << 24)
                    | (((long) get() & 0xff) << 16)
                    | (((long) get() & 0xff) << 8)
                    | (((long) get() & 0xff)));
            }
            if (remain == 2) {
                return ((((long) buf.get()) << 56)
                    | (((long) buf.get() & 0xff) << 48)
                    | (((long) get() & 0xff) << 40)
                    | (((long) get() & 0xff) << 32)
                    | (((long) get() & 0xff) << 24)
                    | (((long) get() & 0xff) << 16)
                    | (((long) get() & 0xff) << 8)
                    | (((long) get() & 0xff)));
            }
            if (remain == 3) {
                return ((((long) buf.get()) << 56)
                    | (((long) buf.get() & 0xff) << 48)
                    | (((long) buf.get() & 0xff) << 40)
                    | (((long) get() & 0xff) << 32)
                    | (((long) get() & 0xff) << 24)
                    | (((long) get() & 0xff) << 16)
                    | (((long) get() & 0xff) << 8)
                    | (((long) get() & 0xff)));
            }
            if (remain == 4) {
                return ((((long) buf.get()) << 56)
                    | (((long) buf.get() & 0xff) << 48)
                    | (((long) buf.get() & 0xff) << 40)
                    | (((long) buf.get() & 0xff) << 32)
                    | (((long) get() & 0xff) << 24)
                    | (((long) get() & 0xff) << 16)
                    | (((long) get() & 0xff) << 8)
                    | (((long) get() & 0xff)));
            }
            if (remain == 5) {
                return ((((long) buf.get()) << 56)
                    | (((long) buf.get() & 0xff) << 48)
                    | (((long) buf.get() & 0xff) << 40)
                    | (((long) buf.get() & 0xff) << 32)
                    | (((long) buf.get() & 0xff) << 24)
                    | (((long) get() & 0xff) << 16)
                    | (((long) get() & 0xff) << 8)
                    | (((long) get() & 0xff)));
            }
            if (remain == 6) {
                return ((((long) buf.get()) << 56)
                    | (((long) buf.get() & 0xff) << 48)
                    | (((long) buf.get() & 0xff) << 40)
                    | (((long) buf.get() & 0xff) << 32)
                    | (((long) buf.get() & 0xff) << 24)
                    | (((long) buf.get() & 0xff) << 16)
                    | (((long) get() & 0xff) << 8)
                    | (((long) get() & 0xff)));
            }
            //remain == 7
            return ((((long) buf.get()) << 56)
                | (((long) buf.get() & 0xff) << 48)
                | (((long) buf.get() & 0xff) << 40)
                | (((long) buf.get() & 0xff) << 32)
                | (((long) buf.get() & 0xff) << 24)
                | (((long) buf.get() & 0xff) << 16)
                | (((long) buf.get() & 0xff) << 8)
                | (((long) get() & 0xff)));
        }
        if (remain == 1) {
            return ((((long) buf.get() & 0xff))
                | (((long) get() & 0xff) << 8)
                | (((long) get() & 0xff) << 16)
                | (((long) get() & 0xff) << 24)
                | (((long) get() & 0xff) << 32)
                | (((long) get() & 0xff) << 40)
                | (((long) get() & 0xff) << 48)
                | (((long) get()) << 56));
        }
        if (remain == 2) {
            return ((((long) buf.get() & 0xff))
                | (((long) buf.get() & 0xff) << 8)
                | (((long) get() & 0xff) << 16)
                | (((long) get() & 0xff) << 24)
                | (((long) get() & 0xff) << 32)
                | (((long) get() & 0xff) << 40)
                | (((long) get() & 0xff) << 48)
                | (((long) get()) << 56));
        }
        if (remain == 3) {
            return ((((long) buf.get() & 0xff))
                | (((long) buf.get() & 0xff) << 8)
                | (((long) buf.get() & 0xff) << 16)
                | (((long) get() & 0xff) << 24)
                | (((long) get() & 0xff) << 32)
                | (((long) get() & 0xff) << 40)
                | (((long) get() & 0xff) << 48)
                | (((long) get()) << 56));
        }
        if (remain == 4) {
            return ((((long) buf.get() & 0xff))
                | (((long) buf.get() & 0xff) << 8)
                | (((long) buf.get() & 0xff) << 16)
                | (((long) buf.get() & 0xff) << 24)
                | (((long) get() & 0xff) << 32)
                | (((long) get() & 0xff) << 40)
                | (((long) get() & 0xff) << 48)
                | (((long) get()) << 56));
        }
        if (remain == 5) {
            return ((((long) buf.get() & 0xff))
                | (((long) buf.get() & 0xff) << 8)
                | (((long) buf.get() & 0xff) << 16)
                | (((long) buf.get() & 0xff) << 24)
                | (((long) buf.get() & 0xff) << 32)
                | (((long) get() & 0xff) << 40)
                | (((long) get() & 0xff) << 48)
                | (((long) get()) << 56));
        }
        if (remain == 6) {
            return ((((long) buf.get() & 0xff))
                | (((long) buf.get() & 0xff) << 8)
                | (((long) buf.get() & 0xff) << 16)
                | (((long) buf.get() & 0xff) << 24)
                | (((long) buf.get() & 0xff) << 32)
                | (((long) buf.get() & 0xff) << 40)
                | (((long) get() & 0xff) << 48)
                | (((long) get()) << 56));
        }
        //remain == 7
        return ((((long) buf.get() & 0xff))
            | (((long) buf.get() & 0xff) << 8)
            | (((long) buf.get() & 0xff) << 16)
            | (((long) buf.get() & 0xff) << 24)
            | (((long) buf.get() & 0xff) << 32)
            | (((long) buf.get() & 0xff) << 40)
            | (((long) buf.get() & 0xff) << 48)
            | (((long) get()) << 56));
    }

    public double getDouble() {
        return Double.longBitsToDouble(getLong());
    }

    public ByteBufferReader get(byte[] dst) {
        return get(dst, 0, dst.length);
    }

    public ByteBufferReader get(byte[] dst, int offset, int length) {
        ByteBuffer buf = this.currBuffer;
        int remain = buf.remaining();
        if (remain >= length) {
            buf.get(dst, offset, length);
            return this;
        }
        buf.get(dst, offset, remain);
        this.currBuffer = this.buffers[++this.currIndex];
        return get(dst, offset + remain, length - remain);
    }

    public ByteBufferReader skip(int size) {
        ByteBuffer buf = this.currBuffer;
        int remain = buf.remaining();
        if (remain >= size) {
            buf.position(buf.position() + size);
            return this;
        }
        buf.position(buf.position() + remain);
        this.currBuffer = this.buffers[++this.currIndex];
        return skip(size - remain);
    }
}
