///*
// *
// */
//package org.redkale.util;
//
//import java.util.concurrent.*;
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
//        final String threadName = String.format(threadNameFormat, "Virtual");
//        return Executors.newThreadPerTaskExecutor(r -> {
//            Thread t = factory.newThread(r);
//            t.setName(threadName);
//            return t;
//        });
//    }
//}
