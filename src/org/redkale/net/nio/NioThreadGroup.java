/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.nio;

import java.nio.channels.SelectionKey;
import java.util.concurrent.*;

/**
 * 协议处理的IO线程组
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.1.0
 */
class NioThreadGroup {

    private NioThread[] ioThreads;

    private ScheduledThreadPoolExecutor timeoutExecutor;

    public ScheduledFuture scheduleTimeout(Runnable callable, long delay, TimeUnit unit) {
        return timeoutExecutor.schedule(callable, delay, unit);
    }

    public void interestOps(NioThread ioThread, SelectionKey key, int opt) {
        if ((key.interestOps() & opt) != 0) return;
        key.interestOps(key.interestOps() | opt);
        if (ioThread.inSameThread()) return;
        //非IO线程中
        key.selector().wakeup();
    }
}
