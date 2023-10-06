/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.service;

import java.util.concurrent.*;
import org.redkale.annotation.Resource;
import org.redkale.boot.Application;
import org.redkale.net.WorkThread;
import org.redkale.net.sncp.Sncp;

/**
 *
 * @author zhangjx
 */
public abstract class AbstractService implements Service {

    //配置<executor threads="0"> APP_EXECUTOR资源为null
    @Resource(name = Application.RESNAME_APP_EXECUTOR, required = false)
    private ExecutorService workExecutor;

    @Resource(name = Resource.SELF_NAME, required = false)
    private String serviceName;

    @Resource(name = Resource.SELF_TYPE, required = false)
    private Class serviceType;

    protected String serviceName() {
        return serviceName;
    }

    protected Class serviceType() {
        return serviceType == null ? Sncp.getServiceType(getClass()) : serviceType;
    }

    /**
     * 异步执行任务
     *
     *
     * @param command 任务
     */
    protected void runAsync(Runnable command) {
        ExecutorService executor = this.workExecutor;
        if (executor != null) {
            executor.execute(command);
        } else {
            Thread thread = Thread.currentThread();
            if (thread instanceof WorkThread) {
                ((WorkThread) thread).runWork(command);
            } else {
                ForkJoinPool.commonPool().execute(command);
            }
        }
    }

    /**
     * 获取线程池
     *
     * @return ExecutorService
     */
    protected ExecutorService getExecutor() {
        ExecutorService executor = this.workExecutor;
        if (executor != null) {
            return executor;
        }
        Thread thread = Thread.currentThread();
        if (thread instanceof WorkThread) {
            ExecutorService e = ((WorkThread) thread).getWorkExecutor();
            if (e != null) {
                return e;
            }
        }
        return ForkJoinPool.commonPool();
    }
}
