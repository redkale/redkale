/// *
// *
// */
// package org.redkale.util;
//
// import java.util.concurrent.*;
// import java.util.function.Function;
//
/// **
// * 虚拟线程池
// *
// * @author zhangjx
// * @since 2.8.0
// */
// public class AnonymousVirtualPoolFunction implements Function<String, ExecutorService> {
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
// }
