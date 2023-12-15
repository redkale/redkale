/*
 *
 */
package org.redkale.source;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import org.redkale.annotation.Resource;
import org.redkale.boot.Application;
import org.redkale.boot.ModuleEngine;
import org.redkale.net.Servlet;
import org.redkale.net.sncp.Sncp;
import org.redkale.service.Service;
import org.redkale.util.AnyValue;
import org.redkale.util.AnyValueWriter;
import org.redkale.util.InstanceProvider;
import org.redkale.util.RedkaleClassLoader;
import org.redkale.util.RedkaleException;
import org.redkale.util.ResourceEvent;
import org.redkale.util.ResourceFactory;
import org.redkale.util.ResourceTypeLoader;
import org.redkale.util.Utility;

/**
 *
 * @author zhangjx
 */
public class SourceModuleEngine extends ModuleEngine {

    //Source 原始的配置资源, 只会存在redkale.datasource(.|[) redkale.cachesource(.|[)开头的配置项
    private final Properties sourceProperties = new Properties();

    //CacheSource 资源
    private final List<CacheSource> cacheSources = new CopyOnWriteArrayList<>();

    private final ReentrantLock cacheSourceLock = new ReentrantLock();

    //DataSource 资源
    private final List<DataSource> dataSources = new CopyOnWriteArrayList<>();

    private final ReentrantLock dataSourceLock = new ReentrantLock();

    //原生sql解析器
    DataNativeSqlParser nativeSqlParser;

    public SourceModuleEngine(Application application) {
        super(application);
    }

    /**
     * 配置项加载后被调用
     */
    @Override
    public void onEnvironmentLoaded(Properties props) {
        props.forEach((key, val) -> {
            if (key.toString().startsWith("redkale.datasource.") || key.toString().startsWith("redkale.datasource[")
                || key.toString().startsWith("redkale.cachesource.") || key.toString().startsWith("redkale.cachesource[")) {
                if (key.toString().endsWith(".name")) {
                    logger.log(Level.WARNING, "skip illegal key " + key + " in source config, key cannot endsWith '.name'");
                } else {
                    this.sourceProperties.put(key, val);
                }
            }
        });
    }

    /**
     * 结束Application.init方法前被调用
     */
    @Override
    public void onAppPostInit() {
        //加载原生sql解析器
        Iterator<DataNativeSqlParserProvider> it = ServiceLoader.load(DataNativeSqlParserProvider.class, application.getClassLoader()).iterator();
        RedkaleClassLoader.putServiceLoader(DataNativeSqlParserProvider.class);
        List<DataNativeSqlParserProvider> providers = new ArrayList<>();
        while (it.hasNext()) {
            DataNativeSqlParserProvider provider = it.next();
            if (provider != null && provider.acceptsConf(null)) {
                RedkaleClassLoader.putReflectionPublicConstructors(provider.getClass(), provider.getClass().getName());
                providers.add(provider);
            }
        }
        for (DataNativeSqlParserProvider provider : InstanceProvider.sort(providers)) {
            this.nativeSqlParser = provider.createInstance();
            this.resourceFactory.register(DataNativeSqlParser.class, this.nativeSqlParser);
            break;  //only first provider
        }

        //------------------------------------- 注册 DataSource --------------------------------------------------------        
        resourceFactory.register((ResourceFactory rf, String srcResourceName, final Object srcObj, String resourceName, Field field, final Object attachment) -> {
            try {
                if (field.getAnnotation(Resource.class) == null && field.getAnnotation(javax.annotation.Resource.class) == null) {
                    return null;
                }
                if ((srcObj instanceof Service) && Sncp.isRemote((Service) srcObj)) {
                    return null; //远程模式不得注入 DataSource
                }
                DataSource source = loadDataSource(resourceName, false);
                field.set(srcObj, source);
                return source;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "DataSource inject to " + srcObj + " error", e);
                return null;
            }
        }, DataSource.class);

        //------------------------------------- 注册 CacheSource --------------------------------------------------------
        resourceFactory.register(new ResourceTypeLoader() {
            @Override
            public Object load(ResourceFactory rf, String srcResourceName, final Object srcObj, final String resourceName, Field field, final Object attachment) {
                try {
                    if (field.getAnnotation(Resource.class) == null && field.getAnnotation(javax.annotation.Resource.class) == null) {
                        return null;
                    }
                    if ((srcObj instanceof Service) && Sncp.isRemote((Service) srcObj)) {
                        return null; //远程模式不需要注入 CacheSource 
                    }
                    if (srcObj instanceof Servlet) {
                        throw new RedkaleException("CacheSource cannot inject in Servlet " + srcObj);
                    }
                    final boolean ws = (srcObj instanceof org.redkale.net.http.WebSocketNodeService);
                    CacheSource source = loadCacheSource(resourceName, ws);
                    field.set(srcObj, source);
                    Resource res = field.getAnnotation(Resource.class);
                    if (res != null && res.required() && source == null) {
                        throw new RedkaleException("CacheSource (resourceName = '" + resourceName + "') not found");
                    } else {
                        logger.info("Load CacheSource (type = " + (source == null ? null : source.getClass().getSimpleName()) + ", resourceName = '" + resourceName + "')");
                    }
                    return source;
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "DataSource inject error", e);
                    return null;
                }
            }

            @Override
            public boolean autoNone() {
                return false;
            }
        }, CacheSource.class);

    }

    /**
     * 配置项变更时被调用
     *
     * @param namespace 命名空间
     * @param events    变更项
     */
    public void onEnvironmentChanged(String namespace, List<ResourceEvent> events) {
        Set<String> sourceRemovedKeys = new HashSet<>();
        Properties sourceChangedProps = new Properties();

        for (ResourceEvent<String> event : events) {
            if (event.name().startsWith("redkale.datasource.") || event.name().startsWith("redkale.datasource[")
                || event.name().startsWith("redkale.cachesource.") || event.name().startsWith("redkale.cachesource[")) {
                if (event.name().endsWith(".name")) {
                    logger.log(Level.WARNING, "skip illegal key " + event.name() + " in source config " + (namespace == null ? "" : namespace) + ", key cannot endsWith '.name'");
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
        //数据源配置项的变更
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
                    cacheSourceNames.add(key.substring("redkale.cachesource.".length(), key.indexOf('.', "redkale.cachesource.".length())));
                } else if (key.startsWith("redkale.datasource[")) {
                    dataSourceNames.add(key.substring("redkale.datasource[".length(), key.indexOf(']')));
                } else if (key.startsWith("redkale.datasource.")) {
                    dataSourceNames.add(key.substring("redkale.datasource.".length(), key.indexOf('.', "redkale.datasource.".length())));
                }
            }
            //更新缓存
            for (String sourceName : cacheSourceNames) {
                CacheSource source = Utility.find(cacheSources, s -> Objects.equals(s.resourceName(), sourceName));
                if (source == null) {
                    continue;  //多余的数据源
                }
                final AnyValueWriter old = (AnyValueWriter) findSourceConfig(sourceName, "cachesource");
                Properties newProps = new Properties();
                this.sourceProperties.forEach((k, v) -> {
                    final String key = k.toString();
                    String prefix = "redkale.cachesource[" + sourceName + "].";
                    int pos = key.indexOf(prefix);
                    if (pos < 0) {
                        prefix = "redkale.cachesource." + sourceName + ".";
                        pos = key.indexOf(prefix);
                    }
                    if (pos < 0) {
                        return; //不是同一name数据源配置项
                    }
                    newProps.put(k, v);
                });
                List<ResourceEvent> changeEvents = new ArrayList<>();
                sourceChangedProps.forEach((k, v) -> {
                    final String key = k.toString();
                    String prefix = "redkale.cachesource[" + sourceName + "].";
                    int pos = key.indexOf(prefix);
                    if (pos < 0) {
                        prefix = "redkale.cachesource." + sourceName + ".";
                        pos = key.indexOf(prefix);
                    }
                    if (pos < 0) {
                        return; //不是同一name数据源配置项
                    }
                    newProps.put(k, v);
                    changeEvents.add(ResourceEvent.create(key.substring(prefix.length()), v, this.sourceProperties.getProperty(key)));
                });
                sourceRemovedKeys.forEach(k -> {
                    final String key = k;
                    String prefix = "redkale.cachesource[" + sourceName + "].";
                    int pos = key.indexOf(prefix);
                    if (pos < 0) {
                        prefix = "redkale.cachesource." + sourceName + ".";
                        pos = key.indexOf(prefix);
                    }
                    if (pos < 0) {
                        return;
                    }
                    newProps.remove(k); //不是同一name数据源配置项
                    changeEvents.add(ResourceEvent.create(key.substring(prefix.length()), null, this.sourceProperties.getProperty(key)));
                });
                if (!changeEvents.isEmpty()) {
                    AnyValueWriter back = old.copy();
                    try {
                        old.replace(AnyValue.loadFromProperties(newProps).getAnyValue("redkale").getAnyValue("cachesource").getAnyValue(sourceName));
                        ((AbstractCacheSource) source).onResourceChange(changeEvents.toArray(new ResourceEvent[changeEvents.size()]));
                    } catch (RuntimeException e) {
                        old.replace(back);  //还原配置
                        throw e;
                    }
                }
            }
            //更新数据库                
            for (String sourceName : dataSourceNames) {
                DataSource source = Utility.find(dataSources, s -> Objects.equals(s.resourceName(), sourceName));
                if (source == null) {
                    continue;  //多余的数据源
                }
                AnyValueWriter old = (AnyValueWriter) findSourceConfig(sourceName, "datasource");
                Properties newProps = new Properties();
                this.sourceProperties.forEach((k, v) -> {
                    final String key = k.toString();
                    String prefix = "redkale.datasource[" + sourceName + "].";
                    int pos = key.indexOf(prefix);
                    if (pos < 0) {
                        prefix = "redkale.datasource." + sourceName + ".";
                        pos = key.indexOf(prefix);
                    }
                    if (pos < 0) {
                        return; //不是同一name数据源配置项
                    }
                    newProps.put(k, v);
                });
                List<ResourceEvent> changeEvents = new ArrayList<>();
                sourceChangedProps.forEach((k, v) -> {
                    final String key = k.toString();
                    String prefix = "redkale.datasource[" + sourceName + "].";
                    int pos = key.indexOf(prefix);
                    if (pos < 0) {
                        prefix = "redkale.datasource." + sourceName + ".";
                        pos = key.indexOf(prefix);
                    }
                    if (pos < 0) {
                        return; //不是同一name数据源配置项
                    }
                    newProps.put(k, v);
                    changeEvents.add(ResourceEvent.create(key.substring(prefix.length()), v, this.sourceProperties.getProperty(key)));
                });
                sourceRemovedKeys.forEach(k -> {
                    final String key = k;
                    String prefix = "redkale.datasource[" + sourceName + "].";
                    int pos = key.indexOf(prefix);
                    if (pos < 0) {
                        prefix = "redkale.datasource." + sourceName + ".";
                        pos = key.indexOf(prefix);
                    }
                    if (pos < 0) {
                        return;
                    }
                    newProps.remove(k); //不是同一name数据源配置项
                    changeEvents.add(ResourceEvent.create(key.substring(prefix.length()), null, this.sourceProperties.getProperty(key)));
                });
                if (!changeEvents.isEmpty()) {
                    AnyValueWriter back = old.copy();
                    try {
                        old.replace(AnyValue.loadFromProperties(newProps).getAnyValue("redkale").getAnyValue("datasource").getAnyValue(sourceName));
                        ((AbstractDataSource) source).onResourceChange(changeEvents.toArray(new ResourceEvent[changeEvents.size()]));
                    } catch (RuntimeException e) {
                        old.replace(back);  //还原配置
                        throw e;
                    }
                }
            }
            sourceRemovedKeys.forEach(this.sourceProperties::remove);
            this.sourceProperties.putAll(sourceChangedProps);
        }
    }

    /**
     * 服务全部停掉后被调用
     */
    public void onServersPostStop() {
        for (DataSource source : dataSources) {
            if (source == null) {
                continue;
            }
            try {
                if (source instanceof Service) {
                    long s = System.currentTimeMillis();
                    ((Service) source).destroy(Sncp.isSncpDyn((Service) source) ? Sncp.getResourceConf((Service) source) : null);
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
                    ((Service) source).destroy(Sncp.isSncpDyn((Service) source) ? Sncp.getResourceConf((Service) source) : null);
                    logger.info(source + " destroy in " + (System.currentTimeMillis() - s) + " ms");
                }
            } catch (Exception e) {
                logger.log(Level.FINER, source.getClass() + " close CacheSource erroneous", e);
            }
        }
    }

    private CacheSource loadCacheSource(final String sourceName, boolean autoMemory) {
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
                logger.info("Load CacheSource resourceName = '" + sourceName + "', source = " + source + " in " + (System.currentTimeMillis() - st) + " ms");
                return source;
            }
            if (!sourceConf.getValue(AbstractCacheSource.CACHE_SOURCE_RESOURCE, "").isEmpty()) {
                CacheSource source = loadCacheSource(sourceConf.getValue(AbstractCacheSource.CACHE_SOURCE_RESOURCE), autoMemory);
                if (source != null) {
                    resourceFactory.register(sourceName, CacheSource.class, source);
                }
                return source;
            }
            try {
                CacheSource source = AbstractCacheSource.createCacheSource(application.getServerClassLoader(),
                    resourceFactory, sourceConf, sourceName, application.isCompileMode());

                cacheSources.add(source);
                resourceFactory.register(sourceName, CacheSource.class, source);
                logger.info("Load CacheSource resourceName = '" + sourceName + "', source = " + source + " in " + (System.currentTimeMillis() - st) + " ms");
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

    private DataSource loadDataSource(final String sourceName, boolean autoMemory) {
        dataSourceLock.lock();
        try {
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
                logger.info("Load DataSource resourceName = '" + sourceName + "', source = " + source);
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
                        if (source instanceof DataJdbcSource) {
                            resourceFactory.register(sourceName, DataJdbcSource.class, source);
                        }
                    }
                }
                return source;
            }
            try {
                DataSource source = DataSources.createDataSource(application.getServerClassLoader(),
                    resourceFactory, sourceConf, sourceName, application.isCompileMode());

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
                logger.info("Load DataSource resourceName = '" + sourceName + "', source = " + source);
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
            return null;
        }
        AnyValue conf = AnyValueWriter.loadFromProperties(props);
        ((AnyValueWriter) conf).setValue("name", sourceName);
        return conf;
    }

}
