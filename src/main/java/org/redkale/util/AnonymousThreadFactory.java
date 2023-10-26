///*
// *
// */
//package org.redkale.util;
//
//import java.util.concurrent.ThreadFactory;
//import java.util.function.Function;
//
///**
// * 虚拟线程工厂
// *
// * @author zhangjx
// * @since 2.8.0
// */
//public class AnonymousThreadFactory implements ThreadFactory, Function<String, ThreadFactory> {
//
//    private final ThreadFactory factory = Thread.ofVirtual().factory();
//
//    private final String name;
//
//    public AnonymousThreadFactory(String name) {
//        this.name = name;
//    }
//
//    @Override
//    public ThreadFactory apply(String name) {
//        return new AnonymousThreadFactory(name);
//    }
//
//    @Override
//    public Thread newThread(Runnable r) {
//        Thread t = factory.newThread(r);
//        if (name != null) {
//            t.setName(name);
//        }
//        return t;
//    }
//
//}
