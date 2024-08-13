/*
 *
 */
package org.redkale.locked.spi;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import org.redkale.annotation.Component;
import org.redkale.asm.AsmMethodBoost;
import org.redkale.boot.Application;
import org.redkale.boot.ModuleEngine;
import org.redkale.boot.NodeServer;
import org.redkale.locked.Locked;
import org.redkale.locked.LockedManager;
import org.redkale.net.sncp.Sncp;
import org.redkale.service.Service;
import org.redkale.util.AnyValue;
import org.redkale.util.InstanceProvider;
import org.redkale.util.RedkaleClassLoader;
import org.redkale.util.RedkaleException;

/** @author zhangjx */
public class LockedModuleEngine extends ModuleEngine {

    protected static final String CONFIG_NAME = "locked";

    // 全局锁管理器
    private LockedManager lockManager;

    private AnyValue config;

    public LockedModuleEngine(Application application) {
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
    public AsmMethodBoost createAsmMethodBoost(boolean remote, Class serviceClass) {
        return new LockedAsmMethodBoost(remote, serviceClass);
    }

    /** 结束Application.init方法前被调用 */
    @Override
    public void onAppPostInit() {
        // 设置锁管理器
        this.config = application.getAppConfig().getAnyValue(CONFIG_NAME);
        this.lockManager = createManager(this.config);
        if (!application.isCompileMode()) {
            this.resourceFactory.inject(this.lockManager);
            if (this.lockManager instanceof Service) {
                ((Service) this.lockManager).init(this.config);
            }
        }
        this.resourceFactory.register("", LockedManager.class, this.lockManager);
    }

    /**
     * 执行Service.init方法后被调用
     *
     * @param server NodeServer
     * @param service Service
     */
    @Override
    public void onServicePostInit(NodeServer server, Service service) {
        if (Sncp.isSncpDyn(service)) {
            return; // 跳过动态生成的Service
        }
        for (Method method : service.getClass().getDeclaredMethods()) {
            if (method.getAnnotation(Locked.class) != null) {
                throw new RedkaleException("@" + Locked.class.getSimpleName() + " cannot on final or @"
                        + Component.class.getSimpleName() + " class, but on " + method);
            }
        }
    }

    /** 进入Application.shutdown方法被调用 */
    @Override
    public void onAppPreShutdown() {
        if (!application.isCompileMode() && this.lockManager instanceof Service) {
            ((Service) this.lockManager).destroy(this.config);
        }
    }

    private LockedManager createManager(AnyValue conf) {
        Iterator<LockedManagerProvider> it = ServiceLoader.load(
                        LockedManagerProvider.class, application.getClassLoader())
                .iterator();
        RedkaleClassLoader.putServiceLoader(LockedManagerProvider.class);
        List<LockedManagerProvider> providers = new ArrayList<>();
        while (it.hasNext()) {
            LockedManagerProvider provider = it.next();
            if (provider != null && provider.acceptsConf(conf)) {
                RedkaleClassLoader.putReflectionPublicConstructors(
                        provider.getClass(), provider.getClass().getName());
                providers.add(provider);
            }
        }
        for (LockedManagerProvider provider : InstanceProvider.sort(providers)) {
            return provider.createInstance();
        }
        return LockedManagerService.create(null).enabled(false);
    }
}
