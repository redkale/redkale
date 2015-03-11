/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net;

import java.nio.*;

/**
 *
 * @author zhangjx
 */
public final class ChunkBuffer {

    final ByteBuffer buffer;

    private final BufferPool pool;

    ChunkBuffer(BufferPool pool, ByteBuffer buffer) {
        this.pool = pool;
        this.buffer = buffer;
    }

    public void release() {
        //pool.offer(this);
    }

    public int limit() {
        return buffer.limit();
    }

    public void limit(int limit) {
        buffer.limit(limit);
    }

    public int position() {
        return buffer.position();
    }

    public void position(int position) {
        buffer.position(position);
    }

    public void clear() {
        buffer.clear();
    }

    public ChunkBuffer flip() {
        buffer.flip();
        return this;
    }

    public int remaining() {
        return buffer.remaining();
    }

    public boolean hasRemaining() {
        return buffer.hasRemaining();
    }

    public boolean isReadOnly() {
        return buffer.isReadOnly();
    }

    public boolean hasArray() {
        return buffer.hasArray();
    }

    public byte[] array() {
        return buffer.array();
    }

    public int arrayOffset() {
        return buffer.arrayOffset();
    }

    public boolean isDirect() {
        return buffer.isDirect();
    }

    public ChunkBuffer slice() {
        buffer.slice();
        return this;
    }

    public ChunkBuffer duplicate() {
        buffer.duplicate();
        return this;
    }

    public ChunkBuffer asReadOnlyBuffer() {
        buffer.asReadOnlyBuffer();
        return this;
    }

    public byte get() {
        return buffer.get();
    }

    public ChunkBuffer put(byte b) {
        buffer.put(b);
        return this;
    }

    public byte get(int index) {
        return buffer.get(index);
    }

    public ChunkBuffer put(int index, byte b) {
        buffer.put(index, b);
        return this;
    }

    public ChunkBuffer compact() {
        buffer.compact();
        return this;
    }

    public char getChar() {
        return buffer.getChar();
    }

    public ChunkBuffer putChar(char value) {
        buffer.putChar(value);
        return this;
    }

    public char getChar(int index) {
        return buffer.getChar(index);
    }

    public ChunkBuffer putChar(int index, char value) {
        buffer.putChar(index, value);
        return this;
    }

    public short getShort() {
        return buffer.getShort();
    }

    public ChunkBuffer putShort(short value) {
        buffer.putShort(value);
        return this;
    }

    public short getShort(int index) {
        return buffer.getShort(index);
    }

    public ChunkBuffer putShort(int index, short value) {
        buffer.putShort(index, value);
        return this;
    }

    public int getInt() {
        return buffer.getInt();
    }

    public ChunkBuffer putInt(int value) {
        buffer.putInt(value);
        return this;
    }

    public int getInt(int index) {
        return buffer.getInt(index);
    }

    public ChunkBuffer putInt(int index, int value) {
        buffer.putInt(index, value);
        return this;
    }

    public long getLong() {
        return buffer.getLong();
    }

    public ChunkBuffer putLong(long value) {
        buffer.putLong(value);
        return this;
    }

    public long getLong(int index) {
        return buffer.getLong(index);
    }

    public ChunkBuffer putLong(int index, long value) {
        buffer.putLong(index, value);
        return this;
    }

    public float getFloat() {
        return buffer.getFloat();
    }

    public ChunkBuffer putFloat(float value) {
        buffer.putFloat(value);
        return this;
    }

    public float getFloat(int index) {
        return buffer.getFloat(index);
    }

    public ChunkBuffer putFloat(int index, float value) {
        buffer.putFloat(index, value);
        return this;
    }

    public double getDouble() {
        return buffer.getDouble();
    }

    public ChunkBuffer putDouble(double value) {
        buffer.putDouble(value);
        return this;
    }

    public double getDouble(int index) {
        return buffer.getDouble(index);
    }

    public ChunkBuffer putDouble(int index, double value) {
        buffer.putDouble(index, value);
        return this;
    }

}
