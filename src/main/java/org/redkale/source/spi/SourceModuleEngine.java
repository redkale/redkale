/*
 *
 */
package org.redkale.source.spi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import org.redkale.boot.Application;
import org.redkale.boot.ModuleEngine;
import org.redkale.inject.Resourcable;
import org.redkale.inject.ResourceEvent;
import org.redkale.net.sncp.Sncp;
import org.redkale.service.Service;
import org.redkale.source.AbstractCacheSource;
import org.redkale.source.AbstractDataSource;
import org.redkale.source.CacheMemorySource;
import org.redkale.source.CacheSource;
import org.redkale.source.DataJdbcSource;
import org.redkale.source.DataMemorySource;
import org.redkale.source.DataNativeSqlParser;
import org.redkale.source.DataSource;
import org.redkale.source.DataSources;
import org.redkale.source.DataSqlSource;
import org.redkale.source.SearchSource;
import org.redkale.source.SourceManager;
import org.redkale.source.SourceType;
import org.redkale.util.AnyValue;
import org.redkale.util.AnyValueWriter;
import org.redkale.util.InstanceProvider;
import org.redkale.util.RedkaleClassLoader;
import org.redkale.util.Utility;

/** @author zhangjx */
public class SourceModuleEngine extends ModuleEngine implements SourceManager {

    // Source 原始的配置资源, 只会存在redkale.datasource(.|[) redkale.cachesource(.|[)开头的配置项
    private final Properties sourceProperties = new Properties();

    // CacheSource 资源
    private final List<CacheSource> cacheSources = new CopyOnWriteArrayList<>();

    private final ReentrantLock cacheSourceLock = new ReentrantLock();

    // DataSource 资源
    private final List<DataSource> dataSources = new CopyOnWriteArrayList<>();

    private final ReentrantLock dataSourceLock = new ReentrantLock();

    // 原生sql解析器
    DataNativeSqlParser nativeSqlParser;

    public SourceModuleEngine(Application application) {
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
        if ("cachesource".equals(path)) {
            return AnyValue.MergeEnum.REPLACE;
        }
        if ("datasource".equals(path)) {
            return AnyValue.MergeEnum.REPLACE;
        }
        return null;
    }

    /** 配置项加载后被调用 */
    @Override
    public void onEnvironmentLoaded(Properties allProps) {
        allProps.forEach((key, val) -> {
            if (key.toString().startsWith("redkale.datasource.")
                    || key.toString().startsWith("redkale.datasource[")
                    || key.toString().startsWith("redkale.cachesource.")
                    || key.toString().startsWith("redkale.cachesource[")) {
                if (key.toString().endsWith(".name")) {
                    logger.log(
                            Level.WARNING,
                            "skip illegal key " + key + " in source config, key cannot endsWith '.name'");
                } else {
                    this.sourceProperties.put(key, val);
                }
            }
        });
    }

    /** 结束Application.init方法前被调用 */
    @Override
    public void onAppPostInit() {
        // 加载原生sql解析器
        Iterator<DataNativeSqlParserProvider> it = ServiceLoader.load(
                        DataNativeSqlParserProvider.class, application.getClassLoader())
                .iterator();
        RedkaleClassLoader.putServiceLoader(DataNativeSqlParserProvider.class);
        List<DataNativeSqlParserProvider> providers = new ArrayList<>();
        while (it.hasNext()) {
            DataNativeSqlParserProvider provider = it.next();
            if (provider != null && provider.acceptsConf(null)) {
                RedkaleClassLoader.putReflectionPublicConstructors(
                        provider.getClass(), provider.getClass().getName());
                providers.add(provider);
            }
        }
        for (DataNativeSqlParserProvider provider : InstanceProvider.sort(providers)) {
            this.nativeSqlParser = provider.createInstance();
            this.resourceFactory.register(DataNativeSqlParser.class, this.nativeSqlParser);
            break; // only first provider
        }
        resourceFactory.register(SourceManager.class, this);
        // --------------------------------- 注册 DataSource、CacheSource ---------------------------------
        resourceFactory.register(new DataSourceLoader(this));
        resourceFactory.register(new CacheSourceLoader(this));
        resourceFactory.register(new DataSqlMapperLoader(this));
    }

    /**
     * 配置项变更时被调用
     *
     * @param namespace 命名空间
     * @param events 变更项
     */
    @Override
    public void onEnvironmentChanged(String namespace, List<ResourceEvent> events) {
        Set<String> sourceRemovedKeys = new HashSet<>();
        Properties sourceChangedProps = new Properties();

        for (ResourceEvent<String> event : events) {
            if (event.name().startsWith("redkale.datasource.")
                    || event.name().startsWith("redkale.datasource[")
                    || event.name().startsWith("redkale.cachesource.")
                    || event.name().startsWith("redkale.cachesource[")) {
                if (event.name().endsWith(".name")) {
                    logger.log(
                            Level.WARNING,
                            "skip illegal key " + event.name() + " in source config "
                                    + (namespace == null ? "" : namespace) + ", key cannot endsWith '.name'");
                } else {
                    if (!Objects.equals(event.newValue(), this.sourceProperties.getProperty(event.name()))) {
                        if (event.newValue() == null) {
                            if (this.sourceProperties.containsKey(event.name())) {
                                sourceRemovedKeys.add(event.name());
                            }
                        } else {
                            sourceChangedProps.put(event.name(), event.newValue());
                        }
                    }
                }
            }
        }
        // 数据源配置项的变更
        if (!sourceChangedProps.isEmpty() || !sourceRemovedKeys.isEmpty()) {
            Set<String> cacheSourceNames = new LinkedHashSet<>();
            Set<String> dataSourceNames = new LinkedHashSet<>();
            List<String> keys = new ArrayList<>();
            keys.addAll(sourceRemovedKeys);
            keys.addAll((Set) sourceChangedProps.keySet());
            for (final String key : keys) {
                if (key.startsWith("redkale.cachesource[")) {
                    cacheSourceNames.add(key.substring("redkale.cachesource[".length(), key.indexOf(']')));
                } else if (key.startsWith("redkale.cachesource.")) {
                    String subkey = key.substring("redkale.cachesource.".length());
                    int pos = subkey.indexOf('.');
                    if (pos < 1) {
                        cacheSourceNames.add("");
                    } else {
                        cacheSourceNames.add(subkey.substring(0, pos));
                    }
                } else if (key.startsWith("redkale.datasource[")) {
                    dataSourceNames.add(key.substring("redkale.datasource[".length(), key.indexOf(']')));
                } else if (key.startsWith("redkale.datasource.")) {
                    String subkey = key.substring("redkale.datasource.".length());
                    int pos = subkey.indexOf('.');
                    if (pos < 1) {
                        dataSourceNames.add("");
                    } else {
                        dataSourceNames.add(subkey.substring(0, pos));
                    }
                }
            }
            // 更新缓存
            onSourceChanged("cachesource", cacheSourceNames, cacheSources, sourceRemovedKeys, sourceChangedProps);
            // 更新数据库
            onSourceChanged("datasource", dataSourceNames, dataSources, sourceRemovedKeys, sourceChangedProps);
            // 更新到内存配置
            sourceRemovedKeys.forEach(this.sourceProperties::remove);
            this.sourceProperties.putAll(sourceChangedProps);
        }
    }

    private void onSourceChanged(
            String sourceType,
            Set<String> sourceNames,
            List<? extends Resourcable> sources,
            Set<String> sourceRemovedKeys,
            Properties sourceChangedProps) {
        for (String sourceName : sourceNames) {
            Object source = Utility.find(sources, s -> Objects.equals(s.resourceName(), sourceName));
            if (source == null) {
                continue; // 多余的数据源
            }
            AnyValueWriter old = (AnyValueWriter) findSourceConfig(sourceName, sourceType);
            Properties newProps = new Properties();
            this.sourceProperties.forEach((k, v) -> {
                final String key = k.toString();
                String prefix = "redkale." + sourceType + "[" + sourceName + "].";
                int pos = key.indexOf(prefix);
                if (pos < 0) {
                    prefix = "redkale." + sourceType + "." + sourceName + ".";
                    pos = key.indexOf(prefix);
                }
                if (pos < 0 && sourceName.isEmpty() && key.startsWith("redkale." + sourceType + ".")) {
                    String subKey = key.substring(("redkale." + sourceType + ".").length());
                    if (subKey.indexOf('.') < 0) {
                        pos = 0;
                    }
                }
                if (pos < 0) {
                    return; // 不是同一name数据源配置项
                }
                newProps.put(k, v);
            });
            List<ResourceEvent> changeEvents = new ArrayList<>();
            sourceChangedProps.forEach((k, v) -> {
                final String key = k.toString();
                String prefix = "redkale." + sourceType + "[" + sourceName + "].";
                int pos = key.indexOf(prefix);
                if (pos < 0) {
                    prefix = "redkale." + sourceType + "." + sourceName + ".";
                    pos = key.indexOf(prefix);
                }
                if (pos < 0 && sourceName.isEmpty() && key.startsWith("redkale." + sourceType + ".")) {
                    String subKey = key.substring(("redkale." + sourceType + ".").length());
                    if (subKey.indexOf('.') < 0) {
                        pos = 0;
                    }
                }
                if (pos < 0) {
                    return; // 不是同一name数据源配置项
                }
                newProps.put(k, v);
                changeEvents.add(ResourceEvent.create(
                        key.substring(prefix.length()), v, this.sourceProperties.getProperty(key)));
            });
            sourceRemovedKeys.forEach(k -> {
                final String key = k;
                String prefix = "redkale." + sourceType + "[" + sourceName + "].";
                int pos = key.indexOf(prefix);
                if (pos < 0) {
                    prefix = "redkale." + sourceType + "." + sourceName + ".";
                    pos = key.indexOf(prefix);
                }
                if (pos < 0 && sourceName.isEmpty() && key.startsWith("redkale." + sourceType + ".")) {
                    String subKey = key.substring(("redkale." + sourceType + ".").length());
                    if (subKey.indexOf('.') < 0) {
                        pos = 0;
                    }
                }
                if (pos < 0) {
                    return;
                }
                newProps.remove(k); // 不是同一name数据源配置项
                changeEvents.add(ResourceEvent.create(
                        key.substring(prefix.length()), null, this.sourceProperties.getProperty(key)));
            });
            if (!changeEvents.isEmpty()) {
                AnyValueWriter back = old == null ? null : old.copy();
                try {
                    if (old != null) {
                        AnyValue parent = AnyValue.loadFromProperties(newProps)
                                .getAnyValue("redkale")
                                .getAnyValue(sourceType);
                        AnyValue sub = parent.getAnyValue(sourceName);
                        if (sub == null && sourceName.isEmpty()) {
                            ((AnyValueWriter) parent).clearAnyEntrys();
                            sub = parent;
                        }
                        old.replace(sub);
                    }
                    if (source instanceof AbstractDataSource) {
                        ((AbstractDataSource) source)
                                .onResourceChange(changeEvents.toArray(new ResourceEvent[changeEvents.size()]));
                    } else if (source instanceof AbstractCacheSource) {
                        ((AbstractCacheSource) source)
                                .onResourceChange(changeEvents.toArray(new ResourceEvent[changeEvents.size()]));
                    }
                } catch (RuntimeException e) {
                    if (old != null) {
                        old.replace(back); // 还原配置
                    }
                    throw e;
                }
            }
        }
    }

    /** 服务全部停掉后被调用 */
    @Override
    public void onServersPostStop() {
        for (DataSource source : dataSources) {
            if (source == null) {
                continue;
            }
            try {
                if (source instanceof Service) {
                    long s = System.currentTimeMillis();
                    ((Service) source)
                            .destroy(Sncp.isSncpDyn((Service) source) ? Sncp.getResourceConf((Service) source) : null);
                    logger.info(source + " destroy in " + (System.currentTimeMillis() - s) + " ms");
                }
            } catch (Exception e) {
                logger.log(Level.FINER, source.getClass() + " close DataSource erroneous", e);
            }
        }
        for (CacheSource source : cacheSources) {
            if (source == null) {
                continue;
            }
            try {
                if (source instanceof Service) {
                    long s = System.currentTimeMillis();
                    ((Service) source)
                            .destroy(Sncp.isSncpDyn((Service) source) ? Sncp.getResourceConf((Service) source) : null);
                    logger.info(source + " destroy in " + (System.currentTimeMillis() - s) + " ms");
                }
            } catch (Exception e) {
                logger.log(Level.FINER, source.getClass() + " close CacheSource erroneous", e);
            }
        }
    }

    /**
     * 获取所有CacheSource, 不同资源名可能指向同一个CacheSource
     *
     * @return CacheSource集合
     */
    @Override
    public Map<String, CacheSource> getCacheSources() {
        Map<String, CacheSource> sources = new HashMap<>();
        cacheSources.forEach(v -> sources.put(v.resourceName(), v));
        return sources;
    }

    @Override
    public CacheSource loadCacheSource(final String sourceName, boolean autoMemory) {
        cacheSourceLock.lock();
        try {
            long st = System.currentTimeMillis();
            CacheSource old = resourceFactory.find(sourceName, CacheSource.class);
            if (old != null) {
                return old;
            }
            final AnyValue sourceConf = findSourceConfig(sourceName, "cachesource");
            if (sourceConf == null) {
                if (!autoMemory) {
                    return null;
                }
                CacheSource source = new CacheMemorySource(sourceName);
                cacheSources.add(source);
                resourceFactory.register(sourceName, CacheSource.class, source);
                if (!application.isCompileMode() && source instanceof Service) {
                    ((Service) source).init(sourceConf);
                }
                logger.info("Load CacheSource resourceName='" + sourceName + "', source=" + source + " in "
                        + (System.currentTimeMillis() - st) + " ms");
                return source;
            }
            if (!sourceConf
                    .getValue(AbstractCacheSource.CACHE_SOURCE_RESOURCE, "")
                    .isEmpty()) {
                CacheSource source =
                        loadCacheSource(sourceConf.getValue(AbstractCacheSource.CACHE_SOURCE_RESOURCE), autoMemory);
                if (source != null) {
                    resourceFactory.register(sourceName, CacheSource.class, source);
                    for (SourceType t : source.getClass().getAnnotationsByType(SourceType.class)) {
                        if (t.value() != CacheSource.class) {
                            resourceFactory.register(sourceName, t.value(), source);
                        }
                    }
                }
                return source;
            }
            try {
                CacheSource source = AbstractCacheSource.createCacheSource(
                        application.getServerClassLoader(),
                        resourceFactory,
                        sourceConf,
                        sourceName,
                        application.isCompileMode());

                cacheSources.add(source);
                resourceFactory.register(sourceName, CacheSource.class, source);
                for (SourceType t : source.getClass().getAnnotationsByType(SourceType.class)) {
                    if (t.value() != CacheSource.class) {
                        resourceFactory.register(sourceName, t.value(), source);
                    }
                }
                logger.info("Load CacheSource resourceName='" + sourceName + "', source=" + source + " in "
                        + (System.currentTimeMillis() - st) + " ms");
                return source;
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "load application CaheSource error: " + sourceConf, e);
            }
            return null;
        } finally {
            cacheSourceLock.unlock();
        }
    }

    /**
     * 获取所有DataSource, 不同资源名可能指向同一个DataSource
     *
     * @return DataSource集合
     */
    @Override
    public Map<String, DataSource> getDataSources() {
        Map<String, DataSource> sources = new HashMap<>();
        dataSources.forEach(v -> sources.put(v.resourceName(), v));
        return sources;
    }

    @Override
    public DataSource loadDataSource(final String sourceName, boolean autoMemory) {
        dataSourceLock.lock();
        try {
            long st = System.currentTimeMillis();
            DataSource old = resourceFactory.find(sourceName, DataSource.class);
            if (old != null) {
                return old;
            }
            final AnyValue sourceConf = findSourceConfig(sourceName, "datasource");
            if (sourceConf == null) {
                if (!autoMemory) {
                    return null;
                }
                DataSource source = new DataMemorySource(sourceName);
                if (!application.isCompileMode() && source instanceof Service) {
                    resourceFactory.inject(sourceName, source);
                    ((Service) source).init(sourceConf);
                }
                dataSources.add(source);
                resourceFactory.register(sourceName, DataSource.class, source);
                logger.info("Load DataSource resourceName='" + sourceName + "', source=" + source + " in "
                        + (System.currentTimeMillis() - st) + " ms");
                return source;
            }
            if (!sourceConf.getValue(DataSources.DATA_SOURCE_RESOURCE, "").isEmpty()) {
                DataSource source = loadDataSource(sourceConf.getValue(DataSources.DATA_SOURCE_RESOURCE), autoMemory);
                if (source != null) {
                    if (source instanceof DataMemorySource && source instanceof SearchSource) {
                        resourceFactory.register(sourceName, SearchSource.class, source);
                    } else {
                        resourceFactory.register(sourceName, DataSource.class, source);
                        if (source instanceof DataSqlSource) {
                            resourceFactory.register(sourceName, DataSqlSource.class, source);
                        }
                        for (SourceType t : source.getClass().getAnnotationsByType(SourceType.class)) {
                            if (t.value() != DataSqlSource.class) {
                                resourceFactory.register(sourceName, t.value(), source);
                            }
                        }
                    }
                }
                return source;
            }
            try {
                DataSource source = DataSources.createDataSource(
                        application.getServerClassLoader(),
                        resourceFactory,
                        sourceConf,
                        sourceName,
                        application.isCompileMode());

                if (!application.isCompileMode() && source instanceof Service) {
                    resourceFactory.inject(sourceName, source);
                    ((Service) source).init(sourceConf);
                }
                dataSources.add(source);
                if (source instanceof DataMemorySource && source instanceof SearchSource) {
                    resourceFactory.register(sourceName, SearchSource.class, source);
                } else {
                    resourceFactory.register(sourceName, DataSource.class, source);
                    if (source instanceof DataSqlSource) {
                        resourceFactory.register(sourceName, DataSqlSource.class, source);
                    }
                    if (source instanceof DataJdbcSource) {
                        resourceFactory.register(sourceName, DataJdbcSource.class, source);
                    }
                }
                logger.info("Load DataSource resourceName='" + sourceName + "', source=" + source + " in "
                        + (System.currentTimeMillis() - st) + " ms");
                return source;
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "load application DataSource error: " + sourceConf, e);
            }
            return null;
        } finally {
            dataSourceLock.unlock();
        }
    }

    private AnyValue findSourceConfig(String sourceName, String sourceType) {
        Properties props = new Properties();
        String bprefix = "redkale." + sourceType;
        String prefix1 = bprefix + "." + sourceName + ".";
        String prefix2 = bprefix + "[" + sourceName + "].";
        this.sourceProperties.forEach((k, v) -> {
            String key = k.toString();
            if (key.startsWith(prefix1)) {
                props.put(key.substring(prefix1.length()), v);
            } else if (key.startsWith(prefix2)) {
                props.put(key.substring(prefix2.length()), v);
            }
        });
        if (props.isEmpty()) {
            if (sourceName.isEmpty()) {
                AnyValueWriter allConf = (AnyValueWriter) AnyValueWriter.loadFromProperties(props);
                if (allConf.getStringEntrys() != null && allConf.getStringEntrys().length > 0) {
                    allConf.clearAnyEntrys();
                    return allConf;
                }
            }
            return null;
        }
        AnyValue conf = AnyValueWriter.loadFromProperties(props);
        ((AnyValueWriter) conf).setValue("name", sourceName);
        return conf;
    }
}
