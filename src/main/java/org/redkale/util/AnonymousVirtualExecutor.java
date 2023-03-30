///*
// *
// */
//package org.redkale.util;
//
//import java.util.concurrent.*;
//import java.util.concurrent.atomic.AtomicInteger;
//import java.util.function.Function;
//
///**
// *
// * @author zhangjx
// */
//public class AnonymousVirtualExecutor implements Function<String, ExecutorService> {
//
//    @Override
//    public ExecutorService apply(String threadNameFormat) {
//        final ThreadFactory factory = Thread.ofVirtual().factory();
//        final AtomicInteger counter = new AtomicInteger();
//        return Executors.newThreadPerTaskExecutor(r -> {
//            Thread t = factory.newThread(r);
//            int c = counter.incrementAndGet();
//            t.setName(String.format(threadNameFormat, "Virtual-" + (c < 10 ? ("00" + c) : (c < 100 ? ("0" + c) : c))));
//            return t;
//        });
//    }
//}
