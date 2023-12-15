/*
 *
 */
package org.redkale.schedule.support;

import org.redkale.boot.Application;
import org.redkale.boot.ModuleEngine;
import org.redkale.service.Service;
import org.redkale.util.AnyValue;

/**
 *
 * @author zhangjx
 */
public class ScheduleModuleEngine extends ModuleEngine {

    //全局定时任务管理器
    private ScheduleManagerService scheduleManager;

    public ScheduleModuleEngine(Application application) {
        super(application);
    }

    /**
     * 结束Application.init方法前被调用
     */
    public void onAppPostInit() {
        //设置定时管理器
        this.scheduleManager = ScheduleManagerService.create(null).enabled(false);
        final AnyValue scheduleConf = environment.getAnyValue("redkale.schedule", true);
        this.resourceFactory.inject(this.scheduleManager);
        if (!application.isCompileMode()) {
            this.scheduleManager.init(scheduleConf);
        }
        this.resourceFactory.register("", this.scheduleManager);
    }

    /**
     * 执行Service.init方法后被调用
     *
     * @param service Service
     */
    public void onServicePostInit(Service service) {
        this.scheduleManager.schedule(service);
    }

    /**
     * 执行Service.destroy方法后被调用
     *
     * @param service Service
     */
    @Override
    public void onServicePreDestroy(Service service) {
        this.scheduleManager.unschedule(service);
    }

    /**
     * 进入Application.shutdown方法被调用
     */
    public void onAppPreShutdown() {
        if (!application.isCompileMode()) {
            this.scheduleManager.destroy(this.scheduleManager.getConfig());
        }
    }
}
