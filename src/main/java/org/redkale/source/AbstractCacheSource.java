/*
 */
package org.redkale.source;

import java.util.*;
import org.redkale.annotation.AutoLoad;
import org.redkale.annotation.ResourceChanged;
import org.redkale.annotation.ResourceType;
import org.redkale.inject.Resourcable;
import org.redkale.inject.ResourceEvent;
import org.redkale.inject.ResourceFactory;
import org.redkale.service.*;
import org.redkale.source.spi.CacheSourceProvider;
import org.redkale.util.*;

/**
 * CacheSource的S抽象实现类 <br>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.7.0
 */
@Local
@AutoLoad(false)
@SuppressWarnings("unchecked")
@ResourceType(CacheSource.class)
public abstract class AbstractCacheSource extends AbstractService implements CacheSource, AutoCloseable, Resourcable {

    // @since 2.8.0  复用另一source资源
    public static final String CACHE_SOURCE_RESOURCE = "resource";

    // @since 2.7.0
    public static final String CACHE_SOURCE_DB = "db";

    // @since 2.7.0
    public static final String CACHE_SOURCE_USER = "user";

    // @since 2.7.0
    public static final String CACHE_SOURCE_PASSWORD = "password";

    // @since 2.7.0
    public static final String CACHE_SOURCE_ENCODING = "encoding";

    // @since 2.8.0
    public static final String CACHE_SOURCE_NODES = "nodes";

    // @since 2.8.0
    public static final String CACHE_SOURCE_TYPE = "type";

    // @since 2.7.0
    public static final String CACHE_SOURCE_MAXCONNS = "maxconns";

    // @since 2.7.0
    public static final String CACHE_SOURCE_PIPELINES = "pipelines";

    // @since 2.8.0 //是否非阻塞式
    public static final String CACHE_SOURCE_NON_BLOCKING = "non-blocking";

    @ResourceChanged
    public abstract void onResourceChange(ResourceEvent[] events);

    // 从Properties配置中创建DataSource
    public static CacheSource createCacheSource(Properties sourceProperties, String sourceName) throws Exception {
        AnyValue redConf = AnyValue.loadFromProperties(sourceProperties);
        AnyValue sourceConf = redConf.getAnyValue("cachesource").getAnyValue(sourceName);
        return createCacheSource(null, null, sourceConf, sourceName, false);
    }

    // 根据配置中创建DataSource
    public static CacheSource createCacheSource(
            ClassLoader serverClassLoader,
            ResourceFactory resourceFactory,
            AnyValue sourceConf,
            String sourceName,
            boolean compileMode)
            throws Exception {
        CacheSource source = null;
        if (serverClassLoader == null) {
            serverClassLoader = Thread.currentThread().getContextClassLoader();
        }
        String classVal = sourceConf.getValue("type");
        if (classVal == null || classVal.isEmpty()) {
            RedkaleClassLoader.putServiceLoader(CacheSourceProvider.class);
            List<CacheSourceProvider> providers = new ArrayList<>();
            Iterator<CacheSourceProvider> it = ServiceLoader.load(CacheSourceProvider.class, serverClassLoader)
                    .iterator();
            while (it.hasNext()) {
                CacheSourceProvider provider = it.next();
                if (provider != null) {
                    RedkaleClassLoader.putReflectionPublicConstructors(
                            provider.getClass(), provider.getClass().getName());
                }
                if (provider != null && provider.acceptsConf(sourceConf)) {
                    providers.add(provider);
                }
            }
            for (CacheSourceProvider provider : InstanceProvider.sort(providers)) {
                source = provider.createInstance();
                if (source != null) {
                    break;
                }
            }
            if (source == null) {
                if (CacheMemorySource.acceptsConf(sourceConf)) {
                    source = new CacheMemorySource(sourceName);
                }
            }
        } else {
            Class sourceType = serverClassLoader.loadClass(classVal);
            RedkaleClassLoader.putReflectionPublicConstructors(sourceType, sourceType.getName());
            source = (CacheSource) sourceType.getConstructor().newInstance();
        }
        if (source == null) {
            throw new SourceException("Not found CacheSourceProvider for config=" + sourceConf);
        }
        if (!compileMode && resourceFactory != null) {
            resourceFactory.inject(sourceName, source);
        }
        if (!compileMode && source instanceof Service) {
            ((Service) source).init(sourceConf);
        }
        return source;
    }
}
