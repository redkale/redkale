/*
 *
 */
package org.redkale.cache.spi;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.redkale.asm.AsmMethodBoost;
import org.redkale.boot.Application;
import org.redkale.boot.ModuleEngine;
import org.redkale.cache.CacheManager;
import org.redkale.inject.ResourceFactory;
import org.redkale.inject.ResourceTypeLoader;
import org.redkale.service.Service;
import org.redkale.util.AnyValue;
import org.redkale.util.InstanceProvider;
import org.redkale.util.RedkaleClassLoader;
import org.redkale.util.RedkaleException;

/** @author zhangjx */
public class CacheModuleEngine extends ModuleEngine {

    // 全局缓存管理器
    private CacheManager cacheManager;

    private AnyValue config;

    public CacheModuleEngine(Application application) {
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
        if ("".equals(path) && "cache".equals(key)) {
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
        return new CacheAsmMethodBoost(remote, serviceClass);
    }

    /** 结束Application.init方法前被调用 */
    @Override
    public void onAppPostInit() {
        // 设置缓存管理器
        this.config = application.getAppConfig().getAnyValue("cache");
        this.cacheManager = createManager(this.config);
        if (!application.isCompileMode()) {
            this.resourceFactory.inject(this.cacheManager);
            if (this.cacheManager instanceof Service) {
                ((Service) this.cacheManager).init(this.config);
            }
        }
        this.resourceFactory.register("", CacheManager.class, this.cacheManager);
        ConcurrentHashMap<String, CacheKeyGenerator> generatorMap = new ConcurrentHashMap<>();
        this.resourceFactory.register(new ResourceTypeLoader() {

            @Override
            public Object load(
                    ResourceFactory rf,
                    String srcResourceName,
                    Object srcObj,
                    String resourceName,
                    Field field,
                    Object attachment) {
                try {
                    CacheKeyGenerator generator = rf.find(resourceName, CacheKeyGenerator.class);
                    if (generator == null) {
                        return generator;
                    }
                    generator = generatorMap.computeIfAbsent(resourceName, n -> {
                        for (CacheKeyGenerator instance :
                                ServiceLoader.load(CacheKeyGenerator.class, application.getClassLoader())) {
                            if (Objects.equals(n, instance.name())) {
                                rf.inject(instance);
                                if (instance instanceof Service) {
                                    ((Service) instance).init(null);
                                }
                                return instance;
                            }
                        }
                        return null;
                    });
                    if (generator != null) {
                        rf.register(resourceName, CacheKeyGenerator.class, generator);
                    }
                    return generator;
                } catch (Exception e) {
                    logger.log(Level.SEVERE, CacheKeyGenerator.class.getSimpleName() + " inject error", e);
                    throw e instanceof RuntimeException ? (RuntimeException) e : new RedkaleException(e);
                }
            }

            @Override
            public Type resourceType() {
                return CacheKeyGenerator.class;
            }
        });
    }

    /** 进入Application.shutdown方法被调用 */
    @Override
    public void onAppPreShutdown() {
        if (!application.isCompileMode() && this.cacheManager instanceof Service) {
            ((Service) this.cacheManager).destroy(this.config);
        }
    }

    private CacheManager createManager(AnyValue conf) {
        Iterator<CacheManagerProvider> it = ServiceLoader.load(CacheManagerProvider.class, application.getClassLoader())
                .iterator();
        RedkaleClassLoader.putServiceLoader(CacheManagerProvider.class);
        List<CacheManagerProvider> providers = new ArrayList<>();
        while (it.hasNext()) {
            CacheManagerProvider provider = it.next();
            if (provider != null && provider.acceptsConf(conf)) {
                RedkaleClassLoader.putReflectionPublicConstructors(
                        provider.getClass(), provider.getClass().getName());
                providers.add(provider);
            }
        }
        for (CacheManagerProvider provider : InstanceProvider.sort(providers)) {
            return provider.createInstance();
        }
        return CacheManagerService.create(null).enabled(false);
    }
}
