/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.*;

/**
 *
 * @author zhangjx
 * @param <T>
 */
public final class ResponsePool<T extends Response> {

    public static interface ResponseFactory<T> {

        T createResponse();
    }

    private final AtomicLong creatCounter;

    private final AtomicLong cycleCounter;

    private ResponseFactory<T> factory;

    private final ArrayBlockingQueue<T> queue;

    public ResponsePool(AtomicLong creatCounter, AtomicLong cycleCounter) {
        this(creatCounter, cycleCounter, 0);
    }

    public ResponsePool(AtomicLong creatCounter, AtomicLong cycleCounter, int max) {
        this.queue = new ArrayBlockingQueue<>(Math.max(32, max));
        this.creatCounter = creatCounter;
        this.cycleCounter = cycleCounter;
    }

    public void setResponseFactory(ResponseFactory<T> factory) {
        this.factory = factory;
    }

    public T poll() {
        T result = queue.poll();
        if (result == null) {
            creatCounter.incrementAndGet();
            result = factory.createResponse();
        }
        return result;
    }

    public void offer(final T e) {
        if (e != null) {
            cycleCounter.incrementAndGet();
            e.recycle();
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
