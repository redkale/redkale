/*
 *
 */
package org.redkale.locked.spi;

import org.redkale.annotation.AutoLoad;
import org.redkale.annotation.Component;
import org.redkale.annotation.Nullable;
import org.redkale.annotation.Resource;
import org.redkale.annotation.ResourceType;
import org.redkale.boot.Application;
import org.redkale.service.Local;
import org.redkale.service.Service;
import org.redkale.source.CacheSource;
import org.redkale.util.AnyValue;
import org.redkale.locked.LockedManager;

/** @author zhangjx */
@Local
@Component
@AutoLoad(false)
@ResourceType(LockedManager.class)
public class LockedManagerService implements LockedManager, Service {

    // 是否开启锁
    protected boolean enabled = true;

    // 配置
    protected AnyValue config;

    @Resource(required = false)
    protected Application application;

    // 远程缓存Source
    protected CacheSource remoteSource;

    protected LockedManagerService(@Nullable CacheSource remoteSource) {
        this.remoteSource = remoteSource;
    }

    // 一般用于独立组件
    public static LockedManagerService create(@Nullable CacheSource remoteSource) {
        return new LockedManagerService(remoteSource);
    }

    public boolean enabled() {
        return this.enabled;
    }

    public LockedManagerService enabled(boolean val) {
        this.enabled = val;
        return this;
    }
}
