/*
 *
 */
package org.redkale.scheduled.spi;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import org.redkale.boot.Application;
import org.redkale.boot.ModuleEngine;
import org.redkale.scheduled.ScheduledManager;
import org.redkale.service.Service;
import org.redkale.util.AnyValue;
import org.redkale.util.InstanceProvider;
import org.redkale.util.RedkaleClassLoader;

/** @author zhangjx */
public class ScheduledModuleEngine extends ModuleEngine {

    protected static final String CONFIG_NAME = "scheduled";

    // 全局定时任务管理器
    private ScheduledManager scheduledManager;

    // 配置
    protected AnyValue config;

    public ScheduledModuleEngine(Application application) {
        super(application);
    }

    /**
     * 判断模块的配置项合并策略， 返回null表示模块不识别此配置项
     *
     * @param path 配置项路径
     * @param key 配置项名称
     * @param val1 配置项原值
     * @param val2 配置项新值
     * @return MergeEnum
     */
    @Override
    public AnyValue.MergeEnum mergeAppConfigStrategy(String path, String key, AnyValue val1, AnyValue val2) {
        if ("".equals(path) && CONFIG_NAME.equals(key)) {
            return AnyValue.MergeEnum.REPLACE;
        }
        return null;
    }

    /** 结束Application.init方法前被调用 */
    @Override
    public void onAppPostInit() {
        // 设置定时管理器
        this.config = application.getAppConfig().getAnyValue(CONFIG_NAME);
        this.scheduledManager = createManager(this.config);
        if (!application.isCompileMode()) {
            this.resourceFactory.inject(this.scheduledManager);
            if (this.scheduledManager instanceof Service) {
                ((Service) this.scheduledManager).init(this.config);
            }
        }
        this.resourceFactory.register("", ScheduledManager.class, this.scheduledManager);
    }

    /**
     * 执行Service.init方法后被调用
     *
     * @param service Service
     */
    @Override
    public void onServicePostInit(Service service) {
        this.scheduledManager.schedule(service);
    }

    /**
     * 执行Service.destroy方法后被调用
     *
     * @param service Service
     */
    @Override
    public void onServicePreDestroy(Service service) {
        this.scheduledManager.unschedule(service);
    }

    /** 服务全部启动前被调用 */
    @Override
    public void onServersPreStart() {
        if (this.scheduledManager instanceof ScheduleManagerService) {
            ((ScheduleManagerService) this.scheduledManager).onServersPreStart();
        }
    }

    /** 服务全部启动后被调用 */
    @Override
    public void onServersPostStart() {
        if (this.scheduledManager instanceof ScheduleManagerService) {
            ((ScheduleManagerService) this.scheduledManager).onServersPostStart();
        }
    }

    /** 进入Application.shutdown方法被调用 */
    @Override
    public void onAppPreShutdown() {
        if (!application.isCompileMode() && this.scheduledManager instanceof Service) {
            ((Service) this.scheduledManager).destroy(this.config);
        }
    }

    private ScheduledManager createManager(AnyValue conf) {
        Iterator<ScheduledManagerProvider> it = ServiceLoader.load(
                        ScheduledManagerProvider.class, application.getClassLoader())
                .iterator();
        RedkaleClassLoader.putServiceLoader(ScheduledManagerProvider.class);
        List<ScheduledManagerProvider> providers = new ArrayList<>();
        while (it.hasNext()) {
            ScheduledManagerProvider provider = it.next();
            if (provider != null && provider.acceptsConf(conf)) {
                RedkaleClassLoader.putReflectionPublicConstructors(
                        provider.getClass(), provider.getClass().getName());
                providers.add(provider);
            }
        }
        for (ScheduledManagerProvider provider : InstanceProvider.sort(providers)) {
            return provider.createInstance();
        }
        return ScheduleManagerService.create(null).enabled(false);
    }
}
