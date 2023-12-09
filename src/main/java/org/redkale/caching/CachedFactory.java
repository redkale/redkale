/*
 *
 */
package org.redkale.caching;

import org.redkale.source.CacheMemorySource;
import org.redkale.source.CacheSource;

/**
 * @TODO 待实现
 *
 * @author zhangjx
 */
public class CachedFactory {

    protected CacheSource localSource = new CacheMemorySource("cacheing");

    protected CacheSource remoteSource;

    protected CachedFactory(CacheSource remoteSource) {
        this.remoteSource = remoteSource;
    }

    public static CachedFactory create(CacheSource remoteSource) {
        return new CachedFactory(remoteSource);
    }
}
