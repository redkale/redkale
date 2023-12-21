/*
 *
 */
package org.redkale.cache.spi;

import java.util.concurrent.TimeUnit;
import org.redkale.cache.Cached;
import org.redkale.convert.json.JsonConvert;

/**
 *
 * @author zhangjx
 */
public class CacheEntry {

    private String key;

    private String hash;

    private String localExpire;

    private String remoteExpire;

    private TimeUnit timeUnit;

    private boolean nullable;

    public CacheEntry() {
    }

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

    public void setKey(String key) {
        this.key = key;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getLocalExpire() {
        return localExpire;
    }

    public void setLocalExpire(String localExpire) {
        this.localExpire = localExpire;
    }

    public String getRemoteExpire() {
        return remoteExpire;
    }

    public void setRemoteExpire(String remoteExpire) {
        this.remoteExpire = remoteExpire;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public void setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    public boolean isNullable() {
        return nullable;
    }

    public void setNullable(boolean nullable) {
        this.nullable = nullable;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }
}
