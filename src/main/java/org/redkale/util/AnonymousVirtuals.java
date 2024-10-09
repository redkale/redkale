/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.util;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 *
 * @author zhangjx
 */
class AnonymousVirtuals {

    static {
        // 加载时进行可用性判断
        Thread.currentThread().isVirtual();
    }

    private AnonymousVirtuals() {
        // do nothing
    }

    public static Executor createExecutor() {
        return new VirtualExecutor();
    }

    public static Function<String, ExecutorService> createPoolFunction() {
        return new VirtualPoolFunction();
    }

    public static Function<Supplier, ThreadLocal> createThreadLocalFunction() {
        return new VirtualThreadLocal(() -> null);
    }

    public static Function<String, ThreadFactory> createThreadFactoryFunction() {
        return new VirtualThreadFactory("");
    }

    static class VirtualExecutor implements Executor {

        @Override
        public void execute(Runnable t) {
            Thread.ofVirtual().name("Redkale-VirtualThread").start(t);
        }
    }

    static class VirtualPoolFunction implements Function<String, ExecutorService> {

        @Override
        public ExecutorService apply(String threadNameFormat) {
            final ThreadFactory factory = Thread.ofVirtual().factory();
            final String threadName = String.format(threadNameFormat, "Virtual");
            return Executors.newThreadPerTaskExecutor(r -> {
                Thread t = factory.newThread(r);
                t.setName(threadName);
                return t;
            });
        }
    }

    static class VirtualThreadLocal<T> extends ThreadLocal<T> implements Function<Supplier<T>, ThreadLocal<T>> {

        private final Supplier<T> supplier;

        public VirtualThreadLocal(Supplier<T> supplier) {
            this.supplier = supplier;
        }

        @Override
        public ThreadLocal<T> apply(Supplier<T> supplier) {
            return new VirtualThreadLocal<>(supplier);
        }

        @Override
        protected T initialValue() {
            return supplier.get();
        }

        @Override
        public void set(T value) {
            Thread t = Thread.currentThread();
            if (!t.isVirtual()) {
                super.set(value);
            }
        }

        @Override
        public T get() {
            Thread t = Thread.currentThread();
            return t.isVirtual() ? initialValue() : super.get();
        }
    }

    static class VirtualThreadFactory implements ThreadFactory, Function<String, ThreadFactory> {

        private final ThreadFactory factory = Thread.ofVirtual().factory();

        private final String name;

        public VirtualThreadFactory(String name) {
            this.name = name;
        }

        @Override
        public ThreadFactory apply(String name) {
            return new VirtualThreadFactory(name);
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = factory.newThread(r);
            if (name != null) {
                t.setName(name);
            }
            return t;
        }
    }
}
