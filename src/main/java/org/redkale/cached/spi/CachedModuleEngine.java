/*
 *
 */
package org.redkale.cached.spi;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import org.redkale.annotation.Component;
import org.redkale.asm.AsmMethodBoost;
import org.redkale.boot.Application;
import org.redkale.boot.ModuleEngine;
import org.redkale.boot.NodeServer;
import org.redkale.cached.Cached;
import org.redkale.cached.CachedManager;
import org.redkale.net.sncp.Sncp;
import org.redkale.service.Service;
import org.redkale.util.AnyValue;
import org.redkale.util.InstanceProvider;
import org.redkale.util.RedkaleClassLoader;
import org.redkale.util.RedkaleException;

/**
 * 缓存管理器
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public class CachedModuleEngine extends ModuleEngine {

    protected static final String CONFIG_NAME = "cached";

    // 全局缓存管理器
    protected ConcurrentHashMap<String, ManagerEntity> cacheManagerMap = new ConcurrentHashMap<>();

    public CachedModuleEngine(Application application) {
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

    /**
     * 动态扩展类的方法
     *
     * @param remote 是否远程模式
     * @param serviceClass 类
     * @return 方法动态扩展器
     */
    @Override
    public AsmMethodBoost createAsmMethodBoost(boolean remote, Class serviceClass) {
        return new CachedAsmMethodBoost(remote, serviceClass);
    }

    /** 结束Application.init方法前被调用 */
    @Override
    public void onAppPostInit() {
        // 设置缓存管理器
        AnyValue[] configs = application.getAppConfig().getAnyValues(CONFIG_NAME);
        if (configs == null || configs.length == 0) {
            configs = new AnyValue[] {AnyValue.create()};
        }
        Map<String, AnyValue> configMap = new HashMap<>();
        for (AnyValue config : configs) {
            String name = config.getOrDefault("name", "");
            if (configMap.containsKey(name)) {
                throw new RedkaleException(
                        CachedManager.class.getSimpleName() + " config(name='" + name + "') is repeat");
            }
            configMap.put(name, config);
        }
        this.resourceFactory.register(new CachedManagerLoader(this, configMap));
        this.resourceFactory.register(new CachedKeyGeneratorLoader(this));
    }

    /**
     * 执行Service.init方法后被调用
     *
     * @param service Service
     */
    @Override
    public void onServicePostInit(NodeServer server, Service service) {
        if (Sncp.isSncpDyn(service)) {
            return; // 跳过动态生成的Service
        }
        for (Method method : service.getClass().getDeclaredMethods()) {
            if (method.getAnnotation(Cached.class) != null) {
                throw new RedkaleException("@" + Cached.class.getSimpleName() + " cannot on final or @"
                        + Component.class.getSimpleName() + " class, but on " + method);
            }
        }
    }

    /**
     * 进入Application.shutdown方法被调用
     */
    @Override
    public void onAppPreShutdown() {
        if (!application.isCompileMode()) {
            cacheManagerMap.forEach((k, v) -> {
                CachedManager manager = v.manager;
                if (manager instanceof Service) {
                    ((Service) manager).destroy(v.config);
                }
            });
            cacheManagerMap.clear();
        }
    }

    protected CachedManager createManager(AnyValue conf) {
        Iterator<CachedManagerProvider> it = ServiceLoader.load(
                        CachedManagerProvider.class, application.getClassLoader())
                .iterator();
        RedkaleClassLoader.putServiceLoader(CachedManagerProvider.class);
        List<CachedManagerProvider> providers = new ArrayList<>();
        while (it.hasNext()) {
            CachedManagerProvider provider = it.next();
            if (provider != null && provider.acceptsConf(conf)) {
                RedkaleClassLoader.putReflectionPublicConstructors(
                        provider.getClass(), provider.getClass().getName());
                providers.add(provider);
            }
        }
        for (CachedManagerProvider provider : InstanceProvider.sort(providers)) {
            return provider.createInstance();
        }
        return CachedManagerService.create(null).enabled(false);
    }

    protected static class ManagerEntity {

        public CachedManager manager;

        public AnyValue config;

        public ManagerEntity(CachedManager manager, AnyValue config) {
            this.manager = manager;
            this.config = config;
        }
    }
}
