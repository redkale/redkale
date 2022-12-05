/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.*;
import java.net.*;
import java.util.*;
import org.redkale.util.AnyValue;
import org.redkale.util.RedkaleClassLoader;

/**
 * 常量放入 AbstractDataSource 类中，方法都已作废，persistence.xml 采用 source.properties 代替
 *
 * @author zhangjx
 */
@Deprecated //@deprecated @since 2.7.0
public final class DataSources {

    @Deprecated //@deprecated @since 2.7.0
    private static final String DATASOURCE_CONFPATH = "DATASOURCE_CONFPATH";

    @Deprecated //@deprecated @since 2.7.0
    private static final String JDBC_DATASOURCE_CLASS = "javax.persistence.datasource";

    @Deprecated //@deprecated @since 2.7.0
    private static final String JDBC_TABLE_AUTODDL = "javax.persistence.table.autoddl";

    @Deprecated //@deprecated @since 2.7.0
    private static final String JDBC_CACHE_MODE = "javax.persistence.cachemode";

    @Deprecated //@deprecated @since 2.7.0
    private static final String JDBC_CONNECTIONS_LIMIT = "javax.persistence.connections.limit";

    @Deprecated //@deprecated @since 2.7.0
    private static final String JDBC_CONNECTIONSCAPACITY = "javax.persistence.connections.bufcapacity";

    @Deprecated //@deprecated @since 2.7.0
    private static final String JDBC_CONTAIN_SQLTEMPLATE = "javax.persistence.contain.sqltemplate";

    @Deprecated //@deprecated @since 2.7.0
    private static final String JDBC_NOTCONTAIN_SQLTEMPLATE = "javax.persistence.notcontain.sqltemplate";

    @Deprecated //@deprecated @since 2.7.0
    private static final String JDBC_TABLENOTEXIST_SQLSTATES = "javax.persistence.tablenotexist.sqlstates";

    @Deprecated //@deprecated @since 2.7.0
    private static final String JDBC_TABLECOPY_SQLTEMPLATE = "javax.persistence.tablecopy.sqltemplate";

    @Deprecated //@deprecated @since 2.7.0
    private static final String JDBC_CONNECTTIMEOUT_SECONDS = "javax.persistence.connecttimeout";

    @Deprecated //@deprecated @since 2.7.0
    private static final String JDBC_READTIMEOUT_SECONDS = "javax.persistence.readtimeout";

    @Deprecated //@deprecated @since 2.7.0
    private static final String JDBC_WRITETIMEOUT_SECONDS = "javax.persistence.writetimeout";

    @Deprecated //@deprecated @since 2.7.0
    private static final String JDBC_URL = "javax.persistence.jdbc.url";

    @Deprecated //@deprecated @since 2.7.0
    private static final String JDBC_USER = "javax.persistence.jdbc.user";

    @Deprecated //@deprecated @since 2.7.0
    private static final String JDBC_PWD = "javax.persistence.jdbc.password";

    @Deprecated //@deprecated @since 2.7.0
    private static final String JDBC_ENCODING = "javax.persistence.jdbc.encoding";

    @Deprecated //@deprecated @since 2.5.0
    private static final String JDBC_DRIVER = "javax.persistence.jdbc.driver";

    @Deprecated //@deprecated @since 2.5.0
    private static final String JDBC_SOURCE = "javax.persistence.jdbc.source";

    //@since 2.4.0 for SearchSource  default value: true
    private static final String JDBC_AUTO_MAPPING = "javax.persistence.jdbc.auto-mapping";

    private DataSources() {
    }

    @Deprecated //@deprecated @since 2.7.0
    public static DataSource createDataSource(final String unitName, final AnyValue conf) throws IOException {
        Properties prop = new Properties();
        AnyValue[] confs = conf.getAnyValues("property");
        if (confs != null) {
            for (AnyValue itemConf : confs) {
                String name = itemConf.getValue("name");
                String value = itemConf.getValue("value");
                if (name != null && value != null) prop.put(name, value);
            }
        }
        return createDataSource(unitName, prop, prop);
    }

    @Deprecated //@deprecated @since 2.7.0
    public static DataSource createDataSource(final String unitName, Properties prop) throws IOException {
        return createDataSource(unitName, prop, prop);
    }

    @Deprecated //@deprecated @since 2.7.0
    public static DataSource createDataSource(final String unitName, Properties readprop, Properties writeprop) throws IOException {
        return createDataSource(unitName, null, readprop, writeprop);
    }

    @Deprecated //@deprecated @since 2.7.0
    public static DataSource createDataSource(final String unitName, URL persistxml, Properties readprop, Properties writeprop) throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
//        String impl = readprop.getProperty(JDBC_DATASOURCE_CLASS, DataJdbcSource.class.getName());
//        String dbtype = null;
//        if (DataJdbcSource.class.getName().equals(impl)) {
//            if (DataJdbcSource.acceptsConf(readprop)) {
//                return new DataJdbcSource(unitName, persistxml, AbstractDataSource.parseDbtype(readprop.getProperty(JDBC_URL)), readprop, writeprop);
//            }
//            String url = readprop.getProperty(JDBC_URL);
//            dbtype = AbstractDataSource.parseDbtype(url);
//            if (dbtype == null) throw new RuntimeException("not found datasource implements class, url=" + url);
//
//            RedkaleClassLoader.putServiceLoader(DataSourceProvider.class);
//            Class dsClass = null;
//            final AnyValue.DefaultAnyValue lc = new AnyValue.DefaultAnyValue();
//            readprop.forEach((k, v) -> lc.addValue(k.toString(), v.toString()));
//            lc.setValue("dbtype", dbtype);
//            List<DataSourceProvider> providers = new ArrayList<>();
//            Iterator<DataSourceProvider> it = ServiceLoader.load(DataSourceProvider.class).iterator();
//            while (it.hasNext()) {
//                DataSourceProvider provider = it.next();
//                if (provider != null) {
//                    RedkaleClassLoader.putReflectionPublicConstructors(provider.getClass(), provider.getClass().getName());
//                }
//                if (provider != null && provider.acceptsConf(lc)) {
//                    providers.add(provider);
//                }
//            }
//            Collections.sort(providers, (a, b) -> {
//                Priority p1 = a == null ? null : a.getClass().getAnnotation(Priority.class);
//                Priority p2 = b == null ? null : b.getClass().getAnnotation(Priority.class);
//                return (p2 == null ? 0 : p2.value()) - (p1 == null ? 0 : p1.value());
//            });
//            for (DataSourceProvider provider : providers) {
//                dsClass = provider.sourceClass();
//                if (dsClass != null) break;
//            }
//            if (dsClass == null) throw new RuntimeException("not found datasource implements ServiceLoader, url=" + url);
//            impl = dsClass.getName();
//        }
//        try {
//            Class ds = Thread.currentThread().getContextClassLoader().loadClass(impl);
//            RedkaleClassLoader.putReflectionPublicConstructors(ds, impl);
//            for (Constructor d : ds.getConstructors()) {
//                Class<?>[] paramtypes = d.getParameterTypes();
//                if (paramtypes.length == 1 && paramtypes[0] == Properties.class) {
//                    RedkaleClassLoader.putReflectionDeclaredConstructors(ds, impl, Properties.class);
//                    return (DataSource) d.newInstance(readprop);
//                } else if (paramtypes.length == 2 && paramtypes[0] == String.class && paramtypes[1] == Properties.class) {
//                    RedkaleClassLoader.putReflectionDeclaredConstructors(ds, impl, String.class, Properties.class);
//                    return (DataSource) d.newInstance(unitName, readprop);
//                } else if (paramtypes.length == 3 && paramtypes[0] == String.class && paramtypes[1] == Properties.class && paramtypes[2] == Properties.class) {
//                    RedkaleClassLoader.putReflectionDeclaredConstructors(ds, impl, String.class, Properties.class, Properties.class);
//                    return (DataSource) d.newInstance(unitName, readprop, writeprop);
//                } else if (paramtypes.length == 4 && paramtypes[0] == String.class && paramtypes[1] == String.class && paramtypes[2] == Properties.class && paramtypes[3] == Properties.class) {
//                    RedkaleClassLoader.putReflectionDeclaredConstructors(ds, impl, String.class, String.class, Properties.class, Properties.class);
//                    return (DataSource) d.newInstance(unitName, dbtype, readprop, writeprop);
//                } else if (paramtypes.length == 4 && paramtypes[0] == String.class && paramtypes[1] == URL.class && paramtypes[2] == Properties.class && paramtypes[3] == Properties.class) {
//                    RedkaleClassLoader.putReflectionDeclaredConstructors(ds, impl, String.class, URL.class, Properties.class, Properties.class);
//                    return (DataSource) d.newInstance(unitName, persistxml, readprop, writeprop);
//                } else if (paramtypes.length == 5 && paramtypes[0] == String.class && paramtypes[1] == URL.class && paramtypes[2] == String.class && paramtypes[3] == Properties.class && paramtypes[4] == Properties.class) {
//                    RedkaleClassLoader.putReflectionDeclaredConstructors(ds, impl, String.class, URL.class, Properties.class, Properties.class);
//                    if (dbtype == null) dbtype = AbstractDataSource.parseDbtype(readprop.getProperty(JDBC_URL));
//                    return (DataSource) d.newInstance(unitName, persistxml, dbtype, readprop, writeprop);
//                }
//            }
//            throw new IOException("DataSource impl class (" + impl + ") have no Constructor by (Properties prop) or (String name, Properties prop) or (String name, Properties readprop, Propertieswriteprop) or (String name, URL persistxml, Properties readprop, Propertieswriteprop)");
//        } catch (IOException ex) {
//            throw ex;
//        } catch (Exception e) {
//            throw new IOException(e);
//        }
    }

    @Deprecated //@deprecated @since 2.7.0
    public static DataSource createDataSource(final String confURI, final String unitName) throws IOException {
        return createDataSource(unitName, System.getProperty(DATASOURCE_CONFPATH, "").isEmpty()
            ? RedkaleClassLoader.getConfResourceAsURI(confURI, "persistence.xml").toURL()
            : (System.getProperty(DATASOURCE_CONFPATH, "").contains("://") ? URI.create(System.getProperty(DATASOURCE_CONFPATH)).toURL() : new File(System.getProperty(DATASOURCE_CONFPATH)).toURI().toURL()));
    }

    @Deprecated //@deprecated @since 2.7.0
    public static DataSource createDataSource(final String unitName, URL persistxml) throws IOException {
        if (persistxml == null) persistxml = DataSources.class.getResource("/persistence.xml");
        InputStream in = persistxml == null ? null : persistxml.openStream();
        if (in == null) return null;
        Map<String, Properties> map = loadPersistenceXml(in);
        Properties readprop = null;
        Properties writeprop = null;
        if (unitName != null) {
            readprop = map.get(unitName);
            writeprop = readprop;
            if (readprop == null) {
                readprop = map.get(unitName + ".read");
                writeprop = map.get(unitName + ".write");
            }
        }
        if (unitName == null || unitName.isEmpty()) {
            String key = null;
            for (Map.Entry<String, Properties> en : map.entrySet()) {
                key = en.getKey();
                readprop = en.getValue();
                writeprop = readprop;
                break;
            }
            if (key != null && (key.endsWith(".read") || key.endsWith(".write"))) {
                if (key.endsWith(".read")) {
                    writeprop = map.get(key.substring(0, key.lastIndexOf('.')) + ".write");
                } else {
                    readprop = map.get(key.substring(0, key.lastIndexOf('.')) + ".read");
                }
            }
        }
        if (readprop == null) throw new IOException("Cannot find (resource.name = '" + unitName + "') DataSource");
        if (writeprop == null) writeprop = readprop;
        if (readprop.getProperty(JDBC_URL, "").startsWith("memory:source")) return new DataMemorySource(unitName);

        return createDataSource(unitName, readprop, writeprop);
    }

    //@since 2.8.0 临时给Application使用，直到DataSources整个类移除
    public static Properties loadSourceProperties(final InputStream in) {
        try {
            Map<String, Properties> map = loadPersistenceXml(in);
            final Properties sourceProperties = new Properties();
            map.forEach((unitName, prop) -> {
                if (unitName.endsWith(".write")) return;
                if (unitName.endsWith(".read")) {
                    String name = unitName.replace(".read", "");
                    prop.forEach((k, v) -> {
                        sourceProperties.put("redkale.datasource[" + name + "].read." + transferKeyName(k.toString()), v);
                    });
                    map.get(name + ".write").forEach((k, v) -> {
                        sourceProperties.put("redkale.datasource[" + name + "].write." + transferKeyName(k.toString()), v);
                    });
                } else {
                    prop.forEach((k, v) -> {
                        sourceProperties.put("redkale.datasource[" + unitName + "]." + transferKeyName(k.toString()), v);
                    });
                }
            });
            return sourceProperties;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static String transferKeyName(String key) {
        if (JDBC_TABLE_AUTODDL.equalsIgnoreCase(key)) {
            return AbstractDataSource.DATA_SOURCE_TABLE_AUTODDL;
        } else if (JDBC_CACHE_MODE.equalsIgnoreCase(key)) {
            return AbstractDataSource.DATA_SOURCE_CACHEMODE;
        } else if (JDBC_CONNECTIONS_LIMIT.equalsIgnoreCase(key)) {
            return AbstractDataSource.DATA_SOURCE_MAXCONNS;
        } else if (JDBC_CONNECTIONSCAPACITY.equalsIgnoreCase(key)) {
            return AbstractDataSource.DATA_SOURCE_CONNECTIONS_CAPACITY;
        } else if (JDBC_CONTAIN_SQLTEMPLATE.equalsIgnoreCase(key)) {
            return AbstractDataSource.DATA_SOURCE_CONTAIN_SQLTEMPLATE;
        } else if (JDBC_NOTCONTAIN_SQLTEMPLATE.equalsIgnoreCase(key)) {
            return AbstractDataSource.DATA_SOURCE_NOTCONTAIN_SQLTEMPLATE;
        } else if (JDBC_TABLENOTEXIST_SQLSTATES.equalsIgnoreCase(key)) {
            return AbstractDataSource.DATA_SOURCE_TABLENOTEXIST_SQLSTATES;
        } else if (JDBC_TABLECOPY_SQLTEMPLATE.equalsIgnoreCase(key)) {
            return AbstractDataSource.DATA_SOURCE_TABLECOPY_SQLTEMPLATE;
        } else if (JDBC_CONNECTTIMEOUT_SECONDS.equalsIgnoreCase(key)) {
            return AbstractDataSource.DATA_SOURCE_CONNECTTIMEOUT_SECONDS;
        } else if (JDBC_URL.equalsIgnoreCase(key)) {
            return AbstractDataSource.DATA_SOURCE_URL;
        } else if (JDBC_USER.equalsIgnoreCase(key)) {
            return AbstractDataSource.DATA_SOURCE_USER;
        } else if (JDBC_PWD.equalsIgnoreCase(key)) {
            return AbstractDataSource.DATA_SOURCE_PASSWORD;
        } else if (JDBC_AUTO_MAPPING.equalsIgnoreCase(key)) {
            return AbstractDataSource.DATA_SOURCE_AUTOMAPPING;
        } else if (JDBC_ENCODING.equalsIgnoreCase(key)) {
            return AbstractDataSource.DATA_SOURCE_ENCODING;
        }
        return key;
    }

    @Deprecated //@deprecated @since 2.7.0
    public static Map<String, Properties> loadPersistenceXml(final InputStream in0) {
        final Map<String, Properties> map = new TreeMap<>();
        try (final InputStream in = in0) {
            AnyValue config = AnyValue.loadFromXml(in).getAnyValue("persistence");
            for (AnyValue conf : config.getAnyValues("persistence-unit")) {
                Properties result = new Properties();
                conf.forEach(null, (n, c) -> {
                    if ("properties".equals(n)) {
                        for (AnyValue sub : c.getAnyValues("property")) {
                            result.put(sub.getValue("name"), sub.getValue("value"));
                        }
                    } else {
                        String v = c.getValue(AnyValue.XML_TEXT_NODE_NAME);
                        if (v != null) {
                            if ("shared-cache-mode".equalsIgnoreCase(n)) {
                                result.put(JDBC_CACHE_MODE, v);
                            } else {
                                result.put(n, v);
                            }
                        }
                    }
                });
                map.put(conf.getValue("name"), result);
            }
            in.close();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return map;
    }

}
