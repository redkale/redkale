/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.*;
import java.lang.reflect.Constructor;
import java.net.*;
import java.util.*;
import javax.annotation.Priority;
import org.redkale.convert.json.JsonConvert;
import org.redkale.util.AnyValue;
import org.redkale.util.RedkaleClassLoader;

/**
 *
 * @author zhangjx
 */
public final class DataSources {

    public static final String DATASOURCE_CONFPATH = "DATASOURCE_CONFPATH";

    public static final String JDBC_DATASOURCE_CLASS = "javax.persistence.datasource";

    public static final String JDBC_TABLE_AUTODDL = "javax.persistence.table.autoddl";

    public static final String JDBC_CACHE_MODE = "javax.persistence.cachemode";

    public static final String JDBC_CONNECTIONS_LIMIT = "javax.persistence.connections.limit";

    public static final String JDBC_CONNECTIONSCAPACITY = "javax.persistence.connections.bufcapacity";

    public static final String JDBC_CONTAIN_SQLTEMPLATE = "javax.persistence.contain.sqltemplate";

    public static final String JDBC_NOTCONTAIN_SQLTEMPLATE = "javax.persistence.notcontain.sqltemplate";

    public static final String JDBC_TABLENOTEXIST_SQLSTATES = "javax.persistence.tablenotexist.sqlstates";

    public static final String JDBC_TABLECOPY_SQLTEMPLATE = "javax.persistence.tablecopy.sqltemplate";

    public static final String JDBC_CONNECTTIMEOUT_SECONDS = "javax.persistence.connecttimeout";

    public static final String JDBC_READTIMEOUT_SECONDS = "javax.persistence.readtimeout";

    public static final String JDBC_WRITETIMEOUT_SECONDS = "javax.persistence.writetimeout";

    public static final String JDBC_URL = "javax.persistence.jdbc.url";

    public static final String JDBC_USER = "javax.persistence.jdbc.user";

    public static final String JDBC_PWD = "javax.persistence.jdbc.password";

    public static final String JDBC_ENCODING = "javax.persistence.jdbc.encoding";

    @Deprecated //@deprecated @since 2.5.0
    public static final String JDBC_DRIVER = "javax.persistence.jdbc.driver";

    @Deprecated //@deprecated @since 2.5.0
    public static final String JDBC_SOURCE = "javax.persistence.jdbc.source";

    //@since 2.4.0 for SearchSource  default value: true
    public static final String JDBC_AUTO_MAPPING = "javax.persistence.jdbc.auto-mapping";

    private DataSources() {
    }

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

    public static DataSource createDataSource(final String unitName, Properties prop) throws IOException {
        return createDataSource(unitName, prop, prop);
    }

    public static DataSource createDataSource(final String unitName, Properties readprop, Properties writeprop) throws IOException {
        return createDataSource(unitName, null, readprop, writeprop);
    }

    public static DataSource createDataSource(final String unitName, URL persistxml, Properties readprop, Properties writeprop) throws IOException {
        String impl = readprop.getProperty(JDBC_DATASOURCE_CLASS, DataJdbcSource.class.getName());
        String dbtype = null;
        if (DataJdbcSource.class.getName().equals(impl)) {
            if (DataJdbcSource.acceptsConf(readprop)) {
                return new DataJdbcSource(unitName, persistxml, parseDbtype(readprop.getProperty(JDBC_URL)), readprop, writeprop);
            }
            String url = readprop.getProperty(JDBC_URL);
            dbtype = parseDbtype(url);
            if (dbtype == null) throw new RuntimeException("not found datasource implements class, url=" + url);

            RedkaleClassLoader.putServiceLoader(DataSourceProvider.class);
            Class dsClass = null;
            final AnyValue.DefaultAnyValue lc = new AnyValue.DefaultAnyValue();
            readprop.forEach((k, v) -> lc.addValue(k.toString(), v.toString()));
            lc.setValue("dbtype", dbtype);
            List<DataSourceProvider> providers = new ArrayList<>();
            Iterator<DataSourceProvider> it = ServiceLoader.load(DataSourceProvider.class).iterator();
            while (it.hasNext()) {
                DataSourceProvider provider = it.next();
                if (provider != null) {
                    RedkaleClassLoader.putReflectionPublicConstructors(provider.getClass(), provider.getClass().getName());
                }
                if (provider != null && provider.acceptsConf(lc)) {
                    providers.add(provider);
                }
            }
            Collections.sort(providers, (a, b) -> {
                Priority p1 = a == null ? null : a.getClass().getAnnotation(Priority.class);
                Priority p2 = b == null ? null : b.getClass().getAnnotation(Priority.class);
                return (p2 == null ? 0 : p2.value()) - (p1 == null ? 0 : p1.value());
            });
            for (DataSourceProvider provider : providers) {
                dsClass = provider.sourceClass();
                if (dsClass != null) break;
            }
            if (dsClass == null) throw new RuntimeException("not found datasource implements ServiceLoader, url=" + url);
            impl = dsClass.getName();
        }
        try {
            Class ds = Thread.currentThread().getContextClassLoader().loadClass(impl);
            RedkaleClassLoader.putReflectionPublicConstructors(ds, impl);
            for (Constructor d : ds.getConstructors()) {
                Class<?>[] paramtypes = d.getParameterTypes();
                if (paramtypes.length == 1 && paramtypes[0] == Properties.class) {
                    RedkaleClassLoader.putReflectionDeclaredConstructors(ds, impl, Properties.class);
                    return (DataSource) d.newInstance(readprop);
                } else if (paramtypes.length == 2 && paramtypes[0] == String.class && paramtypes[1] == Properties.class) {
                    RedkaleClassLoader.putReflectionDeclaredConstructors(ds, impl, String.class, Properties.class);
                    return (DataSource) d.newInstance(unitName, readprop);
                } else if (paramtypes.length == 3 && paramtypes[0] == String.class && paramtypes[1] == Properties.class && paramtypes[2] == Properties.class) {
                    RedkaleClassLoader.putReflectionDeclaredConstructors(ds, impl, String.class, Properties.class, Properties.class);
                    return (DataSource) d.newInstance(unitName, readprop, writeprop);
                } else if (paramtypes.length == 4 && paramtypes[0] == String.class && paramtypes[1] == String.class && paramtypes[2] == Properties.class && paramtypes[3] == Properties.class) {
                    RedkaleClassLoader.putReflectionDeclaredConstructors(ds, impl, String.class, String.class, Properties.class, Properties.class);
                    return (DataSource) d.newInstance(unitName, dbtype, readprop, writeprop);
                } else if (paramtypes.length == 4 && paramtypes[0] == String.class && paramtypes[1] == URL.class && paramtypes[2] == Properties.class && paramtypes[3] == Properties.class) {
                    RedkaleClassLoader.putReflectionDeclaredConstructors(ds, impl, String.class, URL.class, Properties.class, Properties.class);
                    return (DataSource) d.newInstance(unitName, persistxml, readprop, writeprop);
                } else if (paramtypes.length == 5 && paramtypes[0] == String.class && paramtypes[1] == URL.class && paramtypes[2] == String.class && paramtypes[3] == Properties.class && paramtypes[4] == Properties.class) {
                    RedkaleClassLoader.putReflectionDeclaredConstructors(ds, impl, String.class, URL.class, Properties.class, Properties.class);
                    if (dbtype == null) dbtype = parseDbtype(readprop.getProperty(JDBC_URL));
                    return (DataSource) d.newInstance(unitName, persistxml, dbtype, readprop, writeprop);
                }
            }
            throw new IOException("DataSource impl class (" + impl + ") have no Constructor by (Properties prop) or (String name, Properties prop) or (String name, Properties readprop, Propertieswriteprop) or (String name, URL persistxml, Properties readprop, Propertieswriteprop)");
        } catch (IOException ex) {
            throw ex;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public static String parseDbtype(String url) {
        String dbtype = null;
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("search://") || url.startsWith("searchs://")) { //elasticsearch or opensearch
            dbtype = "search";
        } else {
            /* jdbc:mysql:// jdbc:microsoft:sqlserver:// 取://之前的到最后一个:之间的字符串 */
            int pos = url.indexOf("://");
            if (pos > 0) {
                String url0 = url.substring(0, pos);
                pos = url0.lastIndexOf(':');
                if (pos > 0) {
                    dbtype = url0.substring(pos + 1);
                } else { //mongodb://127.0.01:27017
                    dbtype = url0;
                }
            } else { //jdbc:oracle:thin:@localhost:1521
                String url0 = url.substring(url.indexOf(":") + 1);
                pos = url0.indexOf(':');
                if (pos > 0) dbtype = url0.substring(0, pos);
            }
        }
        if ("mariadb".equals(dbtype)) return "mysql";
        return dbtype;
    }

    public static DataSource createDataSource(final String confURI, final String unitName) throws IOException {
        return createDataSource(unitName, System.getProperty(DATASOURCE_CONFPATH, "").isEmpty()
            ? RedkaleClassLoader.getConfResourceAsURI(confURI, "persistence.xml").toURL()
            : (System.getProperty(DATASOURCE_CONFPATH, "").contains("://") ? URI.create(System.getProperty(DATASOURCE_CONFPATH)).toURL() : new File(System.getProperty(DATASOURCE_CONFPATH)).toURI().toURL()));
    }

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
        if (readprop.getProperty(JDBC_URL, "").startsWith("memory:source")) return new DataMemorySource(unitName, persistxml, readprop, writeprop);

        return createDataSource(unitName, readprop, writeprop);
    }

    public static Map<String, Properties> loadPersistenceXml(final InputStream in0) {
        final Map<String, Properties> map = new LinkedHashMap();

        boolean flag = false;
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

    public static UrlInfo parseUrl(final String url) {
        final UrlInfo info = new UrlInfo();
        info.url = url;
        if (url.startsWith("jdbc:h2:")) return info;
        String url0 = url.substring(url.indexOf("://") + 3);
        int pos = url0.indexOf('?'); //127.0.0.1:5432/db?charset=utr8&xxx=yy
        if (pos > 0) {
            String params = url0.substring(pos + 1).replace("&amp;", "&");
            for (String param : params.split("&")) {
                int p = param.indexOf('=');
                if (p < 1) continue;
                info.attributes.put(param.substring(0, p), param.substring(p + 1));
            }
            url0 = url0.substring(0, pos);
        }
        pos = url0.indexOf('/'); //127.0.0.1:5432/db
        if (pos > 0) {
            info.database = url0.substring(pos + 1);
            url0 = url0.substring(0, pos);
        }
        pos = url0.indexOf(':');
        if (pos > 0) {
            info.servaddr = new InetSocketAddress(url0.substring(0, pos), Integer.parseInt(url0.substring(pos + 1)));
        } else if (url.startsWith("http://")) {
            info.servaddr = new InetSocketAddress(url0, 80);
        } else if (url.startsWith("https://")) {
            info.servaddr = new InetSocketAddress(url0, 443);
        } else {
            throw new RuntimeException(url + " parse port error");
        }
        return info;
    }

    public static class UrlInfo {

        public Properties attributes = new Properties();

        public String url;

        public String database;

        public InetSocketAddress servaddr;

        @Override
        public String toString() {
            return JsonConvert.root().convertTo(this);
        }
    }
}
