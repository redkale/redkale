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
public class CacheFactory {

    protected CacheSource localSource = new CacheMemorySource("caching");

    protected CacheSource remoteSource;

    protected CacheFactory(CacheSource remoteSource) {
        this.remoteSource = remoteSource;
    }

    public static CacheFactory create(CacheSource remoteSource) {
        return new CacheFactory(remoteSource);
    }
}
