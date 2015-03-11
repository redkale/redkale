/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.util;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

/**
 *
 * @author zhangjx
 * @param <T>
 */
public final class ObjectPool<T extends ObjectPool.Poolable> {

    public static interface Poolable {

        public void prepare();

        public void release();
    }

    private final Queue<T> queue;

    private final Creator<T> creator;

    public ObjectPool(Class<T> clazz) {
        this(2, clazz);
    }

    public ObjectPool(int max, Class<T> clazz) {
        this(max, Creator.create(clazz));
    }

    public ObjectPool(Creator<T> creator) {
        this(2, creator);
    }

    public ObjectPool(int max, Creator<T> creator) {
        this.creator = creator;
        this.queue = new ArrayBlockingQueue<>(Math.max(Runtime.getRuntime().availableProcessors() * 2, max));
    }

    public T poll() {
        T result = queue.poll();
        if (result == null) {
            result = this.creator.create();
        } else {
            result.prepare();
        }
        return result;
    }

    public void offer(final T e) {
        if (e != null) {
            e.release();
            queue.offer(e);
        }
    }
}
