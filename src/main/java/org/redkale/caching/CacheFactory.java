/*
 *
 */
package org.redkale.caching;

import java.lang.reflect.Type;
import org.redkale.source.CacheMemorySource;
import org.redkale.source.CacheSource;

/**
 * //TODO 待实现
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

    protected long hdelLocal(String map, String key) {
        return localSource.hdel(map, key);
    }

    protected <T> void hsetLocal(final String map, final String key, final Type type, final T value) {
        localSource.hset(map, key, type, value);
    }

    protected <T> T hgetLocal(final String map, final String key, final Type type) {
        return localSource.hget(map, key, type);
    }
}
