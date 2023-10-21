///*
// *
// */
//package org.redkale.util;
//
//import java.util.concurrent.Executor;
//
///**
// * 虚拟线程运行
// *
// * @author zhangjx
// * @since 2.8.0
// */
//public class AnonymousVirtualExecutor implements Executor {
//
//    @Override
//    public void execute(Runnable t) {
//        Thread.ofVirtual().name("Redkale-VirtualThread").start(t);
//    }
//
//}
