/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.protobuf;

import java.nio.ByteBuffer;

/**
 *
 * @author zhangjx
 */
public class ProtobufByteBufferReader extends ProtobufReader {

    private ByteBuffer[] buffers;

    private int currentIndex = 0;

    private ByteBuffer currentBuffer;

    protected ProtobufByteBufferReader(ByteBuffer... buffers) {
        this.buffers = buffers;
        if (buffers != null && buffers.length > 0) this.currentBuffer = buffers[currentIndex];
    }

    @Override
    protected boolean recycle() {
        super.recycle();   // this.position 初始化值为-1
        this.currentIndex = 0;
        this.currentBuffer = null;
        this.buffers = null;
        return false;
    }

    @Override
    protected byte currentByte() {
        return currentBuffer.get(currentBuffer.position());
    }

    protected byte nextByte() {
        if (this.currentBuffer.hasRemaining()) {
            this.position++;
            return this.currentBuffer.get();
        }
        for (;;) {
            this.currentBuffer = this.buffers[++this.currentIndex];
            if (this.currentBuffer.hasRemaining()) {
                this.position++;
                return this.currentBuffer.get();
            }
        }
    }
//
//    //------------------------------------------------------------
//    /**
//     * 判断对象是否存在下一个属性或者数组是否存在下一个元素
//     *
//     * @param startPosition 起始位置
//     * @param contentLength 内容大小， 不确定的传-1
//     *
//     * @return 是否存在
//     */
//    @Override
//    public boolean hasNext(int startPosition, int contentLength) {
//        //("-------------: " + startPosition + ", " + contentLength + ", " + this.position);
//        if (startPosition >= 0 && contentLength >= 0) {
//            return (this.position) < (startPosition + contentLength);
//        }
//        return (this.position + 1) < this.content.length;
//    }
//
//    @Override
//    public byte[] readByteArray() {
//        final int size = readRawVarint32();
//        byte[] bs = new byte[size];
//        System.arraycopy(content, position + 1, bs, 0, size);
//        position += size;
//        return bs;
//    }
//
//    protected int readRawVarint32() {  //readUInt32
//        fastpath:
//        {
//            int tempPos = this.position;
//            if ((tempPos + 1) == content.length) break fastpath;
//
//            int x;
//            if ((x = content[++tempPos]) >= 0) {
//                this.position = tempPos;
//                return x;
//            } else if (content.length - (tempPos + 1) < 9) {
//                break fastpath;
//            } else if ((x ^= (content[++tempPos] << 7)) < 0) {
//                x ^= (~0 << 7);
//            } else if ((x ^= (content[++tempPos] << 14)) >= 0) {
//                x ^= (~0 << 7) ^ (~0 << 14);
//            } else if ((x ^= (content[++tempPos] << 21)) < 0) {
//                x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21);
//            } else {
//                int y = content[++tempPos];
//                x ^= y << 28;
//                x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21) ^ (~0 << 28);
//                if (y < 0
//                    && content[++tempPos] < 0
//                    && content[++tempPos] < 0
//                    && content[++tempPos] < 0
//                    && content[++tempPos] < 0
//                    && content[++tempPos] < 0) {
//                    break fastpath; // Will throw malformedVarint()
//                }
//            }
//            this.position = tempPos;
//            return x;
//        }
//        return (int) readRawVarint64SlowPath();
//    }
//
//    protected long readRawVarint64() {
//        fastpath:
//        {
//            int tempPos = this.position;
//            if ((tempPos + 1) == content.length) break fastpath;
//
//            long x;
//            int y;
//            if ((y = content[++tempPos]) >= 0) {
//                this.position = tempPos;
//                return y;
//            } else if (content.length - (tempPos + 1) < 9) {
//                break fastpath;
//            } else if ((y ^= (content[++tempPos] << 7)) < 0) {
//                x = y ^ (~0 << 7);
//            } else if ((y ^= (content[++tempPos] << 14)) >= 0) {
//                x = y ^ ((~0 << 7) ^ (~0 << 14));
//            } else if ((y ^= (content[++tempPos] << 21)) < 0) {
//                x = y ^ ((~0 << 7) ^ (~0 << 14) ^ (~0 << 21));
//            } else if ((x = y ^ ((long) content[++tempPos] << 28)) >= 0L) {
//                x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28);
//            } else if ((x ^= ((long) content[++tempPos] << 35)) < 0L) {
//                x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35);
//            } else if ((x ^= ((long) content[++tempPos] << 42)) >= 0L) {
//                x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42);
//            } else if ((x ^= ((long) content[++tempPos] << 49)) < 0L) {
//                x ^= (~0L << 7)
//                    ^ (~0L << 14)
//                    ^ (~0L << 21)
//                    ^ (~0L << 28)
//                    ^ (~0L << 35)
//                    ^ (~0L << 42)
//                    ^ (~0L << 49);
//            } else {
//                x ^= ((long) content[++tempPos] << 56);
//                x ^= (~0L << 7)
//                    ^ (~0L << 14)
//                    ^ (~0L << 21)
//                    ^ (~0L << 28)
//                    ^ (~0L << 35)
//                    ^ (~0L << 42)
//                    ^ (~0L << 49)
//                    ^ (~0L << 56);
//                if (x < 0L) {
//                    if (content[++tempPos] < 0L) {
//                        break fastpath; // Will throw malformedVarint()
//                    }
//                }
//            }
//            this.position = tempPos;
//            return x;
//        }
//        return readRawVarint64SlowPath();
//    }
//
//    protected long readRawVarint64SlowPath() {
//        long result = 0;
//        for (int shift = 0; shift < 64; shift += 7) {
//            final byte b = content[++this.position];
//            result |= (long) (b & 0x7F) << shift;
//            if ((b & 0x80) == 0) return result;
//        }
//        throw new ConvertException("readRawVarint64SlowPath error");
//    }
//
//    protected int readRawLittleEndian32() {
//        return ((content[++this.position] & 0xff)
//            | ((content[++this.position] & 0xff) << 8)
//            | ((content[++this.position] & 0xff) << 16)
//            | ((content[++this.position] & 0xff) << 24));
//    }
//
//    protected long readRawLittleEndian64() {
//        return ((content[++this.position] & 0xffL)
//            | ((content[++this.position] & 0xffL) << 8)
//            | ((content[++this.position] & 0xffL) << 16)
//            | ((content[++this.position] & 0xffL) << 24)
//            | ((content[++this.position] & 0xffL) << 32)
//            | ((content[++this.position] & 0xffL) << 40)
//            | ((content[++this.position] & 0xffL) << 48)
//            | ((content[++this.position] & 0xffL) << 56));
//    }
}
