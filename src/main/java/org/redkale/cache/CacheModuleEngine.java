/*
 *
 */
package org.redkale.cache;

import org.redkale.boot.Application;
import org.redkale.boot.ModuleEngine;
import org.redkale.cache.CacheManager;
import org.redkale.util.AnyValue;

/**
 *
 * @author zhangjx
 */
public class CacheModuleEngine extends ModuleEngine {

    //全局缓存管理器
    private DefaultCacheManager cacheManager;

    public CacheModuleEngine(Application application) {
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
        if ("".equals(path) && "cache".equals(key)) {
            return AnyValue.MergeEnum.REPLACE;
        }
        return null;
    }

    /**
     * 结束Application.init方法前被调用
     */
    public void onAppPostInit() {
        //设置缓存管理器
        this.cacheManager = DefaultCacheManager.create(null).enabled(false);
        final AnyValue cacheConf = application.getAppConfig().getAnyValue("cache");
        if (cacheConf != null && !application.isCompileMode()) {
            this.resourceFactory.inject(this.cacheManager);
            this.cacheManager.init(cacheConf);
        }
        this.resourceFactory.register("", CacheManager.class, this.cacheManager);
    }

    /**
     * 进入Application.shutdown方法被调用
     */
    public void onAppPreShutdown() {
        if (!application.isCompileMode()) {
            this.cacheManager.destroy(this.cacheManager.getConfig());
        }
    }
}
