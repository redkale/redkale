/*
 *
 */
package org.redkale.schedule.spi;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import org.redkale.boot.Application;
import org.redkale.boot.ModuleEngine;
import org.redkale.schedule.ScheduleManager;
import org.redkale.service.Service;
import org.redkale.util.AnyValue;
import org.redkale.util.InstanceProvider;
import org.redkale.util.RedkaleClassLoader;

/**
 *
 * @author zhangjx
 */
public class ScheduleModuleEngine extends ModuleEngine {

    //全局定时任务管理器
    private ScheduleManager scheduleManager;

    //配置
    protected AnyValue config;

    public ScheduleModuleEngine(Application application) {
        super(application);
    }

    /**
     * 判断模块的配置项合并策略， 返回null表示模块不识别此配置项
     *
     * @param path 配置项路径
     * @param key  配置项名称
     * @param val1 配置项原值
     * @param val2 配置项新值
     *
     * @return MergeEnum
     */
    @Override
    public AnyValue.MergeEnum mergeAppConfigStrategy(String path, String key, AnyValue val1, AnyValue val2) {
        if ("".equals(path) && "schedule".equals(key)) {
            return AnyValue.MergeEnum.REPLACE;
        }
        return null;
    }

    /**
     * 结束Application.init方法前被调用
     */
    @Override
    public void onAppPostInit() {
        //设置定时管理器
        this.config = application.getAppConfig().getAnyValue("schedule");
        this.scheduleManager = createManager(this.config);
        if (!application.isCompileMode()) {
            this.resourceFactory.inject(this.scheduleManager);
            if (this.scheduleManager instanceof Service) {
                ((Service) this.scheduleManager).init(this.config);
            }
        }
        this.resourceFactory.register("", ScheduleManager.class, this.scheduleManager);
    }

    /**
     * 执行Service.init方法后被调用
     *
     * @param service Service
     */
    @Override
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
     * 服务全部启动前被调用
     */
    @Override
    public void onServersPreStart() {
        if (this.scheduleManager instanceof ScheduleManagerService) {
            ((ScheduleManagerService) this.scheduleManager).onServersPreStart();
        }
    }

    /**
     * 服务全部启动后被调用
     */
    @Override
    public void onServersPostStart() {
        if (this.scheduleManager instanceof ScheduleManagerService) {
            ((ScheduleManagerService) this.scheduleManager).onServersPostStart();
        }
    }

    /**
     * 进入Application.shutdown方法被调用
     */
    @Override
    public void onAppPreShutdown() {
        if (!application.isCompileMode() && this.scheduleManager instanceof Service) {
            ((Service) this.scheduleManager).destroy(this.config);
        }
    }

    private ScheduleManager createManager(AnyValue conf) {
        Iterator<ScheduleManagerProvider> it = ServiceLoader.load(ScheduleManagerProvider.class, application.getClassLoader()).iterator();
        RedkaleClassLoader.putServiceLoader(ScheduleManagerProvider.class);
        List<ScheduleManagerProvider> providers = new ArrayList<>();
        while (it.hasNext()) {
            ScheduleManagerProvider provider = it.next();
            if (provider != null && provider.acceptsConf(conf)) {
                RedkaleClassLoader.putReflectionPublicConstructors(provider.getClass(), provider.getClass().getName());
                providers.add(provider);
            }
        }
        for (ScheduleManagerProvider provider : InstanceProvider.sort(providers)) {
            return provider.createInstance();
        }
        return ScheduleManagerService.create(null).enabled(false);
    }
}
