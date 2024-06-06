/*
 *
 */
package org.redkale.cache.spi;

import java.util.concurrent.TimeUnit;
import org.redkale.cache.Cached;
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
public class CacheEntry {

    private String key;

    private String hash;

    private String localExpire;

    private String remoteExpire;

    private TimeUnit timeUnit;

    private boolean nullable;

    public CacheEntry() {}

    public CacheEntry(DynForCache cached) {
        this.key = cached.key();
        this.hash = cached.hash();
        this.localExpire = cached.localExpire();
        this.remoteExpire = cached.remoteExpire();
        this.timeUnit = cached.timeUnit();
        this.nullable = cached.nullable();
    }

    public CacheEntry(Cached cached) {
        this.key = cached.key();
        this.hash = cached.hash();
        this.localExpire = cached.localExpire();
        this.remoteExpire = cached.remoteExpire();
        this.timeUnit = cached.timeUnit();
        this.nullable = cached.nullable();
    }

    public String getKey() {
        return key;
    }

    public String getHash() {
        return hash;
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
