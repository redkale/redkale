/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net;

import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.*;

/**
 *
 * @author zhangjx
 */
public final class BufferPool {

    private final int capacity;

    private final ArrayBlockingQueue<ByteBuffer> queue;

    private final AtomicLong creatCounter;

    private final AtomicLong cycleCounter;

    public BufferPool(AtomicLong creatCounter, AtomicLong cycleCounter, int capacity) {
        this(creatCounter, cycleCounter, capacity, 0);
    }

    public BufferPool(AtomicLong creatCounter, AtomicLong cycleCounter, int capacity, int max) {
        this.capacity = capacity;
        this.queue = new ArrayBlockingQueue<>(Math.max(32, max));
        this.creatCounter = creatCounter;
        this.cycleCounter = cycleCounter;
    }

    public ByteBuffer poll() {
        ByteBuffer result = queue.poll();
        if (result == null) {
            creatCounter.incrementAndGet();
            result = ByteBuffer.allocateDirect(capacity);
        }
        return result;
    }

    public void offer(final ByteBuffer e) {
        if (e != null && !e.isReadOnly() && e.capacity() == this.capacity) {
            cycleCounter.incrementAndGet();
            e.clear();
            queue.offer(e);
        }
    }

    public long getCreatCount() {
        return creatCounter.longValue();
    }

    public long getCycleCount() {
        return cycleCounter.longValue();
    }
}
