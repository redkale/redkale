/*
 *
 */
package org.redkale.source;

import java.util.Map;

/**
 * source组件的基本管理器
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public interface SourceManager {

    /**
     * @param sourceName 资源名
     * @return CacheSource
     */
    default CacheSource loadCacheSource(final String sourceName) {
        return loadCacheSource(sourceName, false);
    }

    /**
     * @param sourceName 资源名
     * @param autoMemory 不存在是否自动创建内存版CacheSource
     * @return CacheSource
     */
    public CacheSource loadCacheSource(final String sourceName, boolean autoMemory);

    /**
     * 获取所有CacheSource, 不同资源名可能指向同一个CacheSource
     *
     * @return CacheSource集合
     */
    public Map<String, CacheSource> getCacheSources();

    /**
     * @param sourceName 资源名
     * @return DataSource
     */
    default DataSource loadDataSource(final String sourceName) {
        return loadDataSource(sourceName, false);
    }

    /**
     * 加载DataSource
     *
     * @param sourceName 资源名
     * @param autoMemory 不存在是否自动创建内存版DataSource
     * @return DataSource
     */
    public DataSource loadDataSource(final String sourceName, boolean autoMemory);

    /**
     * 获取所有DataSource, 不同资源名可能指向同一个DataSource
     *
     * @return DataSource集合
     */
    public Map<String, DataSource> getDataSources();
}
