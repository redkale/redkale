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

    @Override
    protected final int readRawVarint32() { // readUInt32
        byte b = nextByte();
        if (b >= 0) {
            return b;
        }
        int result = b & 0x7f;
        if ((b = nextByte()) >= 0) {
            result |= b << 7;
        } else {
            result |= (b & 0x7f) << 7;
            if ((b = nextByte()) >= 0) {
                result |= b << 14;
            } else {
                result |= (b & 0x7f) << 14;
                if ((b = nextByte()) >= 0) {
                    result |= b << 21;
                } else {
                    result |= (b & 0x7f) << 21;
                    result |= (b = nextByte()) << 28;
                    if (b < 0) {
                        // Discard upper 32 bits.
                        for (int i = 0; i < 5; i++) {
                            if (nextByte() >= 0) {
                                return result;
                            }
                        }
                        throw new ConvertException("readRawVarint32 error");
                    }
                }
            }
        }
        return result;
    }

    @Override
    protected final long readRawVarint64() {
        int shift = 0;
        long result = 0;
        while (shift < 64) {
            final byte b = nextByte();
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
            shift += 7;
        }
        throw new ConvertException("readRawVarint64 error");
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
