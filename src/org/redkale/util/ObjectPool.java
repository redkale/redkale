/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.logging.*;

/**
 * 对象池
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <T> 对象池元素的数据类型
 */
public class ObjectPool<T> implements Supplier<T>, Consumer<T> {

    protected static final Logger logger = Logger.getLogger(ObjectPool.class.getSimpleName());

    protected final boolean debug;

    protected Creator<T> creator;

    protected int max;

    protected final Consumer<T> prepare;

    protected final Predicate<T> recycler;

    protected final AtomicLong creatCounter;

    protected final AtomicLong cycleCounter;

    protected final Queue<T> queue;

    protected ObjectPool(AtomicLong creatCounter, AtomicLong cycleCounter, int max, Creator<T> creator, Consumer<T> prepare, Predicate<T> recycler, Queue<T> queue) {
        this.creatCounter = creatCounter;
        this.cycleCounter = cycleCounter;
        this.creator = creator;
        this.prepare = prepare;
        this.recycler = recycler;
        this.max = max;
        this.debug = logger.isLoggable(Level.FINEST);
        this.queue = queue;
    }

    //非线程安全版
    public static <T> ObjectPool<T> createUnsafePool(Class<T> clazz, Consumer<T> prepare, Predicate<T> recycler) {
        return createUnsafePool(2, clazz, prepare, recycler);
    }

    //非线程安全版
    public static <T> ObjectPool<T> createUnsafePool(int max, Class<T> clazz, Consumer<T> prepare, Predicate<T> recycler) {
        return createUnsafePool(max, Creator.create(clazz), prepare, recycler);
    }

    //非线程安全版
    public static <T> ObjectPool<T> createUnsafePool(Creator<T> creator, Consumer<T> prepare, Predicate<T> recycler) {
        return createUnsafePool(2, creator, prepare, recycler);
    }

    //非线程安全版
    public static <T> ObjectPool<T> createUnsafePool(int max, Creator<T> creator, Consumer<T> prepare, Predicate<T> recycler) {
        return createUnsafePool(null, null, max, creator, prepare, recycler);
    }

    //非线程安全版
    public static <T> ObjectPool<T> createUnsafePool(int max, Supplier<T> creator, Consumer<T> prepare, Predicate<T> recycler) {
        return createUnsafePool(null, null, max, creator, prepare, recycler);
    }

    //非线程安全版
    public static <T> ObjectPool<T> createUnsafePool(AtomicLong creatCounter, AtomicLong cycleCounter, int max, Supplier<T> creator, Consumer<T> prepare, Predicate<T> recycler) {
        return createUnsafePool(creatCounter, cycleCounter, max, c -> creator.get(), prepare, recycler);
    }

    //非线程安全版
    public static <T> ObjectPool<T> createUnsafePool(AtomicLong creatCounter, AtomicLong cycleCounter, int max, Creator<T> creator, Consumer<T> prepare, Predicate<T> recycler) {
        return new ObjectPool(creatCounter, cycleCounter, Math.max(Runtime.getRuntime().availableProcessors(), max),
            creator, prepare, recycler, new ArrayDeque<>(Math.max(Runtime.getRuntime().availableProcessors(), max)));
    }

    //线程安全版
    public static <T> ObjectPool<T> createSafePool(Class<T> clazz, Consumer<T> prepare, Predicate<T> recycler) {
        return createSafePool(2, clazz, prepare, recycler);
    }

    //线程安全版
    public static <T> ObjectPool<T> createSafePool(int max, Class<T> clazz, Consumer<T> prepare, Predicate<T> recycler) {
        return createSafePool(max, Creator.create(clazz), prepare, recycler);
    }

    //线程安全版
    public static <T> ObjectPool<T> createSafePool(Creator<T> creator, Consumer<T> prepare, Predicate<T> recycler) {
        return createSafePool(2, creator, prepare, recycler);
    }

    //线程安全版
    public static <T> ObjectPool<T> createSafePool(int max, Creator<T> creator, Consumer<T> prepare, Predicate<T> recycler) {
        return createSafePool(null, null, max, creator, prepare, recycler);
    }

    //线程安全版
    public static <T> ObjectPool<T> createSafePool(int max, Supplier<T> creator, Consumer<T> prepare, Predicate<T> recycler) {
        return createSafePool(null, null, max, creator, prepare, recycler);
    }

    //线程安全版
    public static <T> ObjectPool<T> createSafePool(AtomicLong creatCounter, AtomicLong cycleCounter, int max, Supplier<T> creator, Consumer<T> prepare, Predicate<T> recycler) {
        return createSafePool(creatCounter, cycleCounter, max, c -> creator.get(), prepare, recycler);
    }

    //线程安全版
    public static <T> ObjectPool<T> createSafePool(AtomicLong creatCounter, AtomicLong cycleCounter, int max, Creator<T> creator, Consumer<T> prepare, Predicate<T> recycler) {
        return new ObjectPool(creatCounter, cycleCounter, Math.max(Runtime.getRuntime().availableProcessors(), max),
            creator, prepare, recycler, new LinkedBlockingQueue<>(Math.max(Runtime.getRuntime().availableProcessors(), max)));
    }

    public void setCreator(Creator<T> creator) {
        this.creator = creator;
    }

    public Creator<T> getCreator() {
        return this.creator;
    }

    public int getMax() {
        return max;
    }

    public Consumer<T> getPrepare() {
        return prepare;
    }

    public Predicate<T> getRecycler() {
        return recycler;
    }

    public AtomicLong getCreatCounter() {
        return creatCounter;
    }

    public AtomicLong getCycleCounter() {
        return cycleCounter;
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
        if (e != null && recycler.test(e)) {
            if (cycleCounter != null) cycleCounter.incrementAndGet();
//            if (debug) {
//                for (T t : queue) {
//                    if (t == e) {
//                        logger.log(Level.WARNING, "[" + Thread.currentThread().getName() + "] repeat offer the same object(" + e + ")", new Exception());
//                        return;
//                    }
//                }
//            }
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
