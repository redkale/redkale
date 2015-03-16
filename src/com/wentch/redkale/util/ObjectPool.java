/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.util;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

/**
 *
 * @author zhangjx
 * @param <T>
 */
public final class ObjectPool<T> {

    private final Queue<T> queue;

    private Creator<T> creator;

    private final Predicate<T> recycler;

    private final AtomicLong creatCounter;

    private final AtomicLong cycleCounter;

    public ObjectPool(Class<T> clazz, Predicate<T> recycler) {
        this(2, clazz, recycler);
    }

    public ObjectPool(int max, Class<T> clazz, Predicate<T> recycler) {
        this(max, Creator.create(clazz), recycler);
    }

    public ObjectPool(Creator<T> creator, Predicate<T> recycler) {
        this(2, creator, recycler);
    }

    public ObjectPool(int max, Creator<T> creator, Predicate<T> recycler) {
        this(null, null, max, creator, recycler);
    }

    public ObjectPool(AtomicLong creatCounter, AtomicLong cycleCounter, int max, Creator<T> creator, Predicate<T> recycler) {
        this.creatCounter = creatCounter;
        this.cycleCounter = cycleCounter;
        this.creator = creator;
        this.recycler = recycler;
        this.queue = new ArrayBlockingQueue<>(Math.max(Runtime.getRuntime().availableProcessors() * 2, max));
    }

    public void setCreator(Creator<T> creator) {
        this.creator = creator;
    }

    public T poll() {
        T result = queue.poll();
        if (result == null) {
            if (creatCounter != null) creatCounter.incrementAndGet();
            result = this.creator.create();
        }
        return result;
    }

    public void offer(final T e) {
        if (e != null && recycler.test(e)) {
            if (cycleCounter != null) cycleCounter.incrementAndGet();
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
