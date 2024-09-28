/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.pb;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.redkale.convert.ConvertException;
import org.redkale.util.ByteArray;

/** @author zhangjx */
public class ProtobufByteBufferReader extends ProtobufReader {

    private ByteBuffer[] buffers;

    private ByteBuffer currentBuffer;

    private int currentIndex = 0;

    protected ProtobufByteBufferReader(ByteBuffer... buffers) {
        this.buffers = buffers;
        this.currentBuffer = buffers[currentIndex];
    }

    @Override
    protected boolean recycle() {
        super.recycle();
        this.currentIndex = 0;
        this.currentBuffer = null;
        this.buffers = null;
        return false;
    }

    protected int remaining() {
        int count = 0;
        for (int i = currentIndex; i < buffers.length; i++) {
            count += buffers[i].remaining();
        }
        return count;
    }

    protected byte nextByte() {
        if (this.currentBuffer.hasRemaining()) {
            this.position++;
            return this.currentBuffer.get();
        }
        for (; ; ) {
            this.currentBuffer = this.buffers[++this.currentIndex];
            if (this.currentBuffer.hasRemaining()) {
                this.position++;
                return this.currentBuffer.get();
            }
        }
    }

    protected byte[] nextBytes(int size) {
        byte[] bs = new byte[size];
        if (this.currentBuffer.remaining() >= size) {
            this.position += size;
            this.currentBuffer.get(bs);
        } else {
            for (int i = 0; i < bs.length; i++) {
                bs[i] = nextByte();
            }
        }
        return bs;
    }

    @Override
    public byte[] remainBytes() {
        ByteArray array = new ByteArray();
        if (currentBuffer.hasRemaining()) {
            array.put(currentBuffer);
        }
        int end = buffers.length - 1;
        while (this.currentIndex < end) {
            this.currentBuffer = this.buffers[++this.currentIndex];
            array.put(currentBuffer);
        }
        return array.getBytes();
    }

    @Override
    public boolean hasNext() {
        if (currentBuffer.hasRemaining()) {
            return true;
        }
        int end = buffers.length - 1;
        while (this.currentIndex < end) {
            this.currentBuffer = this.buffers[++this.currentIndex];
            if (this.currentBuffer.hasRemaining()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public final boolean readBoolean() {
        return nextByte() != 0;
    }

    @Override
    public final String readString() {
        final int size = readRawVarint32();
        return new String(nextBytes(size), StandardCharsets.UTF_8);
    }

    @Override
    public final byte[] readByteArray() {
        final int size = readRawVarint32();
        return nextBytes(size);
    }

    protected final int readRawVarint32() { // readUInt32
        fastpath:
        {
            if (!hasNext()) {
                break fastpath;
            }
            int x;
            if ((x = nextByte()) >= 0) {
                return x;
            } else if (remaining() < 9) {
                break fastpath;
            } else if ((x ^= (nextByte() << 7)) < 0) {
                x ^= (~0 << 7);
            } else if ((x ^= (nextByte() << 14)) >= 0) {
                x ^= (~0 << 7) ^ (~0 << 14);
            } else if ((x ^= (nextByte() << 21)) < 0) {
                x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21);
            } else {
                int y = nextByte();
                x ^= y << 28;
                x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21) ^ (~0 << 28);
                if (y < 0 && nextByte() < 0 && nextByte() < 0 && nextByte() < 0 && nextByte() < 0 && nextByte() < 0) {
                    break fastpath; // Will throw malformedVarint()
                }
            }
            return x;
        }
        return (int) readRawVarint64SlowPath();
    }

    protected final long readRawVarint64() {
        fastpath:
        {
            if (!hasNext()) {
                break fastpath;
            }
            long x;
            int y;
            if ((y = nextByte()) >= 0) {
                return y;
            } else if (remaining() < 9) {
                break fastpath;
            } else if ((y ^= (nextByte() << 7)) < 0) {
                x = y ^ (~0 << 7);
            } else if ((y ^= (nextByte() << 14)) >= 0) {
                x = y ^ ((~0 << 7) ^ (~0 << 14));
            } else if ((y ^= (nextByte() << 21)) < 0) {
                x = y ^ ((~0 << 7) ^ (~0 << 14) ^ (~0 << 21));
            } else if ((x = y ^ ((long) nextByte() << 28)) >= 0L) {
                x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28);
            } else if ((x ^= ((long) nextByte() << 35)) < 0L) {
                x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35);
            } else if ((x ^= ((long) nextByte() << 42)) >= 0L) {
                x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42);
            } else if ((x ^= ((long) nextByte() << 49)) < 0L) {
                x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42) ^ (~0L << 49);
            } else {
                x ^= ((long) nextByte() << 56);
                x ^= (~0L << 7)
                        ^ (~0L << 14)
                        ^ (~0L << 21)
                        ^ (~0L << 28)
                        ^ (~0L << 35)
                        ^ (~0L << 42)
                        ^ (~0L << 49)
                        ^ (~0L << 56);
                if (x < 0L) {
                    if (nextByte() < 0L) {
                        break fastpath; // Will throw malformedVarint()
                    }
                }
            }
            return x;
        }
        return readRawVarint64SlowPath();
    }

    protected final long readRawVarint64SlowPath() {
        long result = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            final byte b = nextByte();
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
        }
        throw new ConvertException("readRawVarint64SlowPath error");
    }

    @Override
    protected final int readRawLittleEndian32() {
        return ((nextByte() & 0xff)
                | ((nextByte() & 0xff) << 8)
                | ((nextByte() & 0xff) << 16)
                | ((nextByte() & 0xff) << 24));
    }

    @Override
    protected final long readRawLittleEndian64() {
        return ((nextByte() & 0xffL)
                | ((nextByte() & 0xffL) << 8)
                | ((nextByte() & 0xffL) << 16)
                | ((nextByte() & 0xffL) << 24)
                | ((nextByte() & 0xffL) << 32)
                | ((nextByte() & 0xffL) << 40)
                | ((nextByte() & 0xffL) << 48)
                | ((nextByte() & 0xffL) << 56));
    }
}
