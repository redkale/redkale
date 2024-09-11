/*
 *
 */
package org.redkale.cached.spi;

import java.util.concurrent.TimeUnit;
import org.redkale.cached.Cached;
import org.redkale.convert.json.JsonConvert;

/**
 * 缓存信息的基本对象
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 *
 */
public class CachedEntry {

    private String manager;

    private String name;

    private String key;

    private String localLimit;

    private String localExpire;

    private String remoteExpire;

    private TimeUnit timeUnit;

    private boolean nullable;

    public CachedEntry() {}

    public CachedEntry(DynForCached cached) {
        this.manager = cached.manager();
        this.name = cached.name();
        this.key = cached.key();
        this.localLimit = cached.localLimit();
        this.localExpire = cached.localExpire();
        this.remoteExpire = cached.remoteExpire();
        this.timeUnit = cached.timeUnit();
        this.nullable = cached.nullable();
    }

    public CachedEntry(Cached cached) {
        this.manager = cached.manager();
        this.name = cached.name();
        this.key = cached.key();
        this.localLimit = cached.localLimit();
        this.localExpire = cached.localExpire();
        this.remoteExpire = cached.remoteExpire();
        this.timeUnit = cached.timeUnit();
        this.nullable = cached.nullable();
    }

    public String getManager() {
        return manager;
    }

    public String getName() {
        return name;
    }

    public String getKey() {
        return key;
    }

    public String getLocalLimit() {
        return localLimit;
    }

    public String getLocalExpire() {
        return localExpire;
    }

    public String getRemoteExpire() {
        return remoteExpire;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public boolean isNullable() {
        return nullable;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }
}
