/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.bson;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import static org.redkale.convert.Reader.SIGN_NULL;

/**
 * 以ByteBuffer为数据载体的BsonReader
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class BsonByteBufferReader extends BsonReader {

    private ByteBuffer[] buffers;

    private int currentIndex = 0;

    private ByteBuffer currentBuffer;

    protected BsonByteBufferReader(ByteBuffer... buffers) {
        this.buffers = buffers;
        if (buffers != null && buffers.length > 0) {
            this.currentBuffer = buffers[currentIndex];
        }
    }

    @Override
    protected boolean recycle() {
        super.recycle(); // this.position 初始化值为-1
        this.currentIndex = 0;
        this.currentBuffer = null;
        this.buffers = null;
        return false;
    }

    @Override
    protected byte currentByte() {
        return currentBuffer.get(currentBuffer.position());
    }

    // ------------------------------------------------------------

    @Override
    public final boolean readBoolean() {
        return readByte() == 1;
    }

    @Override
    public byte readByte() {
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

    @Override
    public final char readChar() {
        if (this.currentBuffer != null) {
            int remain = this.currentBuffer.remaining();
            if (remain >= 2) {
                this.position += 2;
                return this.currentBuffer.getChar();
            }
        }
        return (char) ((0xff00 & (readByte() << 8)) | (0xff & readByte()));
    }

    @Override
    public final short readShort() {
        if (this.currentBuffer != null) {
            int remain = this.currentBuffer.remaining();
            if (remain >= 2) {
                this.position += 2;
                return this.currentBuffer.getShort();
            }
        }
        return (short) ((0xff00 & (readByte() << 8)) | (0xff & readByte()));
    }

    @Override
    public final int readInt() {
        if (this.currentBuffer != null) {
            int remain = this.currentBuffer.remaining();
            if (remain >= 4) {
                this.position += 4;
                return this.currentBuffer.getInt();
            }
        }
        return ((readByte() & 0xff) << 24)
                | ((readByte() & 0xff) << 16)
                | ((readByte() & 0xff) << 8)
                | (readByte() & 0xff);
    }

    @Override
    public final long readLong() {
        if (this.currentBuffer != null) {
            int remain = this.currentBuffer.remaining();
            if (remain >= 8) {
                this.position += 8;
                return this.currentBuffer.getLong();
            }
        }
        return ((((long) readByte() & 0xff) << 56)
                | (((long) readByte() & 0xff) << 48)
                | (((long) readByte() & 0xff) << 40)
                | (((long) readByte() & 0xff) << 32)
                | (((long) readByte() & 0xff) << 24)
                | (((long) readByte() & 0xff) << 16)
                | (((long) readByte() & 0xff) << 8)
                | (((long) readByte() & 0xff)));
    }

    protected byte[] read(final int len) {
        byte[] bs = new byte[len];
        read(bs, 0);
        return bs;
    }

    private void read(final byte[] bs, final int pos) {
        int remain = this.currentBuffer.remaining();
        if (remain < 1) {
            this.currentBuffer = this.buffers[++this.currentIndex];
            read(bs, pos);
            return;
        }
        int len = bs.length - pos;
        if (remain >= len) {
            this.position += len;
            this.currentBuffer.get(bs, pos, len);
            return;
        }
        this.currentBuffer.get(bs, pos, remain);
        this.position += remain;
        this.currentBuffer = this.buffers[++this.currentIndex];
        read(bs, pos + remain);
    }

    @Override
    public final String readSmallString() {
        int len = 0xff & readByte();
        if (len == 0) {
            return "";
        }
        return new String(read(len));
    }

    @Override
    public final String readString() {
        int len = readInt();
        if (len == SIGN_NULL) {
            return null;
        }
        if (len == 0) {
            return "";
        }
        return new String(read(len), StandardCharsets.UTF_8);
    }
}
