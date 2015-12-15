/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.util.concurrent.*;

/**
 *
 * @see http://www.redkale.org
 * @author zhangjx
 */
public class WorkThread extends Thread {

    private final ExecutorService executor;

    public WorkThread(ExecutorService executor, Runnable runner) {
        super(runner);
        this.executor = executor;
        this.setDaemon(true);
    }

    public void submit(Runnable runner) {
        executor.submit(runner);
    }

    public ExecutorService getExecutor() {
        return executor;
    }
}
