/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.*;

/**
 * 对象池
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <T> 对象池元素的数据类型
 */
public class ThreadLocalObjectPool<T> extends ObjectPool<T> {

    public ThreadLocalObjectPool(Class<T> clazz, Consumer<T> prepare, Predicate<T> recycler) {
        this(2, clazz, prepare, recycler);
    }

    public ThreadLocalObjectPool(int max, Class<T> clazz, Consumer<T> prepare, Predicate<T> recycler) {
        this(max, Creator.create(clazz), prepare, recycler);
    }

    public ThreadLocalObjectPool(Creator<T> creator, Consumer<T> prepare, Predicate<T> recycler) {
        this(2, creator, prepare, recycler);
    }

    public ThreadLocalObjectPool(int max, Creator<T> creator, Consumer<T> prepare, Predicate<T> recycler) {
        this(null, null, max, creator, prepare, recycler);
    }

    public ThreadLocalObjectPool(int max, Supplier<T> creator, Consumer<T> prepare, Predicate<T> recycler) {
        this(null, null, max, creator, prepare, recycler);
    }

    public ThreadLocalObjectPool(AtomicLong creatCounter, AtomicLong cycleCounter, int max, Supplier<T> creator, Consumer<T> prepare, Predicate<T> recycler) {
        this(creatCounter, cycleCounter, max, c -> creator.get(), prepare, recycler);
    }

    public ThreadLocalObjectPool(AtomicLong creatCounter, AtomicLong cycleCounter, int max, Creator<T> creator, Consumer<T> prepare, Predicate<T> recycler) {
        super(creatCounter, cycleCounter, max, creator, prepare, recycler, new LinkedList<>());
    }

    @Override
    public T get() {
        T result = queue.poll();
        if (result == null) {
            if (creatCounter != null) creatCounter.incrementAndGet();
            result = this.creator.create();
        }
        if (prepare != null) prepare.accept(result);
        return result;
    }

    @Override
    public void accept(final T e) {
        if (e != null && recycler.test(e) && this.queue.size() < this.max) {
            if (cycleCounter != null) cycleCounter.incrementAndGet();
            queue.offer(e);
        }
    }

}
