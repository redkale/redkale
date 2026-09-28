/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import static org.redkale.util.Utility.isEmpty;

import java.io.*;
import java.net.*;
import java.util.*;
import org.redkale.inject.ResourceFactory;
import org.redkale.service.Service;
import org.redkale.source.spi.DataSourceProvider;
import org.redkale.util.*;

/**
 * 构建DataSource对象的工具类
 *
 * @author zhangjx
 */
public final class DataSources {

    // @since 2.8.0  复用另一source资源
    public static final String DATA_SOURCE_RESOURCE = "resource";

    // @since 2.8.0 格式: x.x.x.x:yyyy
    public static final String DATA_SOURCE_PROXY_ADDRESS = "proxy-address";

    // @since 2.8.0 值: SOCKS/DIRECT/HTTP，默认值: http
    public static final String DATA_SOURCE_PROXY_TYPE = "proxy-type";

    // @since 2.8.0
    public static final String DATA_SOURCE_PROXY_USER = "proxy-user";

    // @since 2.8.0
    public static final String DATA_SOURCE_PROXY_PASSWORD = "proxy-password";

    // @since 2.8.0 值: true/false，默认值: true
    public static final String DATA_SOURCE_PROXY_ENABLE = "proxy-enable";

    // @since 2.8.0
    public static final String DATA_SOURCE_URL = "url";

    // @since 2.8.0
    public static final String DATA_SOURCE_USER = "user";

    // @since 2.8.0
    public static final String DATA_SOURCE_PASSWORD = "password";

    // @since 2.8.0
    public static final String DATA_SOURCE_ENCODING = "encoding";

    // @since 2.8.0
    public static final String DATA_SOURCE_MAXCONNS = "maxconns";

    // @since 2.8.0
    public static final String DATA_SOURCE_PIPELINES = "pipelines";

    // @since 2.8.0 //超过多少毫秒视为较慢, 会打印警告级别的日志, 默认值: 2000
    public static final String DATA_SOURCE_SLOWMS_WARN = "warn-slowms";

    // @since 2.8.0 //超过多少毫秒视为较慢, 会打印警告级别的日志, 默认值: 3000
    public static final String DATA_SOURCE_SLOWMS_ERROR = "error-slowms";

    // @since 2.8.0 //是否非阻塞式
    public static final String DATA_SOURCE_NON_BLOCKING = "non-blocking";

    // @since 2.8.0 //sourceExecutor线程数, 默认值: 内核数
    public static final String DATA_SOURCE_THREADS = "threads";

    // @since 2.8.0
    public static final String DATA_SOURCE_AUTOMAPPING = "auto-mapping";

    // @since 2.8.0
    public static final String DATA_SOURCE_TABLE_AUTODDL = "table-autoddl";

    // @since 2.8.0
    public static final String DATA_SOURCE_CONNECT_TIMEOUT_SECONDS = "connect-timeout";

    // @since 2.8.0 //NONE ALL 设置@Cacheable是否生效
    public static final String DATA_SOURCE_CACHEMODE = "cachemode";

    // @since 2.8.0
    public static final String DATA_SOURCE_CONNECTIONS_CAPACITY = "connections-bufcapacity";

    // @since 2.8.0
    public static final String DATA_SOURCE_CONTAIN_SQLTEMPLATE = "contain-sqltemplate";

    // @since 2.8.0
    public static final String DATA_SOURCE_NOTCONTAIN_SQLTEMPLATE = "notcontain-sqltemplate";

    // @since 2.8.0
    public static final String DATA_SOURCE_TABLENOTEXIST_SQLSTATES = "tablenotexist-sqlstates";

    // @since 2.8.0
    public static final String DATA_SOURCE_TABLECOPY_SQLTEMPLATE = "tablecopy-sqltemplate";

    private DataSources() {
        // do nothing
    }

    // 从Properties配置中创建DataSource
    public static DataSource createDataSource(Properties sourceProperties, String sourceName) {
        AnyValue redConf = AnyValue.loadFromProperties(sourceProperties);
        AnyValue sourceConf = redConf.getAnyValue("datasource").getAnyValue(sourceName);
        return createDataSource(sourceConf, sourceName);
    }

    // 从AnyValue配置中创建DataSource
    public static DataSource createDataSource(AnyValue sourceConf, String sourceName) {
        return createDataSource(null, null, sourceConf, sourceName, false);
    }

    // 根据配置中创建DataSource
    public static DataSource createDataSource(
            ClassLoader serverClassLoader,
            ResourceFactory resourceFactory,
            AnyValue sourceConf,
            String sourceName,
            boolean compileMode) {
        DataSource source = null;
        if (serverClassLoader == null) {
            serverClassLoader = Thread.currentThread().getContextClassLoader();
        }
        String classVal = sourceConf.getValue("type");
        if (isEmpty(classVal)) {
            if (DataJdbcSource.acceptsConf(sourceConf)) {
                source = new DataJdbcSource();
            } else {
                RedkaleClassLoader.putServiceLoader(DataSourceProvider.class);
                List<DataSourceProvider> providers = new ArrayList<>();
                Iterator<DataSourceProvider> it = ServiceLoader.load(DataSourceProvider.class, serverClassLoader)
                        .iterator();
                while (it.hasNext()) {
                    DataSourceProvider provider = it.next();
                    if (provider != null) {
                        RedkaleClassLoader.putReflectionPublicConstructors(
                                provider.getClass(), provider.getClass().getName());
                    }
                    if (provider != null && provider.acceptsConf(sourceConf)) {
                        providers.add(provider);
                    }
                }
                for (DataSourceProvider provider : InstanceProvider.sort(providers)) {
                    source = provider.createInstance();
                    if (source != null) {
                        break;
                    }
                }
                if (source == null) {
                    if (DataMemorySource.acceptsConf(sourceConf)) {
                        source = new DataMemorySource(sourceName);
                    }
                }
            }
        } else {
            try {
                Class sourceType = serverClassLoader.loadClass(classVal);
                RedkaleClassLoader.putReflectionPublicConstructors(sourceType, sourceType.getName());
                source = (DataSource) sourceType.getConstructor().newInstance();
            } catch (Exception e) {
                throw new SourceException(e);
            }
        }
        if (source == null) {
            throw new SourceException("Not found DataSourceProvider for config=" + sourceConf);
        }
        if (!compileMode && resourceFactory != null) {
            resourceFactory.inject(sourceName, source);
        }
        if (!compileMode && source instanceof Service) {
            ((Service) source).init(sourceConf);
        }
        return source;
    }

    public static String parseDbtype(String url) {
        String dbtype = null;
        if (url == null) {
            return dbtype;
        }
        if (url.startsWith("http://")
                || url.startsWith("https://")
                || url.startsWith("search://")
                || url.startsWith("searchs://")) { // elasticsearch or opensearch
            dbtype = "search";
        } else {
            /* jdbc:mysql:// jdbc:microsoft:sqlserver:// 取://之前的到最后一个:之间的字符串 */
            int pos = url.indexOf("://");
            if (pos > 0) {
                String url0 = url.substring(0, pos);
                pos = url0.lastIndexOf(':');
                if (pos > 0) {
                    dbtype = url0.substring(pos + 1);
                } else { // mongodb://127.0.01:27017
                    dbtype = url0;
                }
            } else { // jdbc:oracle:thin:@localhost:1521
                String url0 = url.substring(url.indexOf(":") + 1);
                pos = url0.indexOf(':');
                if (pos > 0) {
                    dbtype = url0.substring(0, pos);
                }
            }
        }
        if ("mariadb".equals(dbtype)) {
            return "mysql";
        }
        return dbtype;
    }
}
