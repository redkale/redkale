//package org.redkale.util;
//
//import java.util.function.*;
//
///**
// *
// * @author zhangjx
// * @since 2.7.0
// */
//public class SignalShutDown implements Consumer<Consumer<String>>, sun.misc.SignalHandler {
//
//    private Consumer<String> shutdownConsumer;
//
//    @Override
//    public void accept(Consumer<String> consumer) {
//        this.shutdownConsumer = consumer;
//        //Linux: 
//        //HUP     1    终端断线
//        //INT     2    中断（同 Ctrl + C）
//        //QUIT    3    退出（同 Ctrl + \）
//        //TERM    15   终止
//        //KILL    9    强制终止
//        //CONT    18   继续（与STOP相反， fg/bg命令）
//        //Windows: 
//        //SIGINT（INT）     Ctrl+C中断
//        //SIGILL （ILL）      非法指令
//        //SIGFPE（FPE）      浮点异常
//        //SIGSEGV（SEGV）   无效的内存引用
//        //SIGTERM（TERM）   kill发出的软件终止
//        //SIGBREAK（BREAK） Ctrl+Break中断
//        //SIGABRT（ABRT）   调用abort导致
//        //http://www.comptechdoc.org/os/linux/programming/linux_pgsignals.html
//        try {
//            String[] sigs = new String[]{"HUP", "TERM", "INT", "QUIT", "KILL", "TSTP", "USR1", "USR2", "STOP"};
//            for (String sig : sigs) {
//                try {
//                    sun.misc.Signal.handle(new sun.misc.Signal(sig), this);
//                } catch (Exception e) {
//                }
//            }
//        } catch (Throwable t) {
//        }
//    }
//
//    @Override
//    public synchronized void handle(sun.misc.Signal sig) {
//        String sigstr = sig + "," + sig.getName() + "," + sig.getNumber();
//        shutdownConsumer.accept(sigstr);
//    }
//}
