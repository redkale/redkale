/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.*;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.*;
import javax.xml.stream.*;
import org.redkale.util.AnyValue;

/**
 *
 * @author zhangjx
 */
public final class DataSources {

    public static final String DATASOURCE_CONFPATH = "DATASOURCE_CONFPATH";

    public static final String JDBC_DATASOURCE_CLASS = "javax.persistence.datasource";

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

    public static final String JDBC_DRIVER = "javax.persistence.jdbc.driver";

    public static final String JDBC_SOURCE = "javax.persistence.jdbc.source";

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
        if (DataJdbcSource.class.getName().equals(impl)) {
            try {
                return new DataJdbcSource(unitName, persistxml, readprop, writeprop);
            } catch (RuntimeException re) {
                if (!(re.getCause() instanceof ClassNotFoundException)) throw re;
                String dbtype = null;
                {
                    /* jdbc:mysql:// jdbc:microsoft:sqlserver:// 取://之前的到最后一个:之间的字符串 */
                    String url = readprop.getProperty(JDBC_URL);
                    int pos = url.indexOf("://");
                    if (pos > 0) {
                        String url0 = url.substring(0, pos);
                        pos = url0.lastIndexOf(':');
                        if (pos > 0) dbtype = url0.substring(pos + 1);
                    } else { //jdbc:oracle:thin:@localhost:1521
                        String url0 = url.substring(url.indexOf(":") + 1);
                        pos = url0.indexOf(':');
                        if (pos > 0) dbtype = url0.substring(0, pos);
                    }
                }
                if (dbtype == null) throw re;
                Iterator<SourceLoader> it = ServiceLoader.load(SourceLoader.class).iterator();
                Class dsClass = null;
                while (it.hasNext()) {
                    SourceLoader loader = it.next();
                    if (dbtype.equalsIgnoreCase(loader.dbtype())) {
                        dsClass = loader.dataSourceClass();
                        if (dsClass != null) break;
                    }
                }
                if (dsClass == null) throw re;
                impl = dsClass.getName();
            }
        }
        try {
            Class ds = Thread.currentThread().getContextClassLoader().loadClass(impl);
            for (Constructor d : ds.getConstructors()) {
                Class<?>[] paramtypes = d.getParameterTypes();
                if (paramtypes.length == 1 && paramtypes[0] == Properties.class) {
                    return (DataSource) d.newInstance(readprop);
                } else if (paramtypes.length == 2 && paramtypes[0] == String.class && paramtypes[1] == Properties.class) {
                    return (DataSource) d.newInstance(unitName, readprop);
                } else if (paramtypes.length == 3 && paramtypes[0] == String.class && paramtypes[1] == Properties.class && paramtypes[2] == Properties.class) {
                    return (DataSource) d.newInstance(unitName, readprop, writeprop);
                } else if (paramtypes.length == 4 && paramtypes[0] == String.class && paramtypes[1] == URL.class && paramtypes[2] == Properties.class && paramtypes[3] == Properties.class) {
                    return (DataSource) d.newInstance(unitName, persistxml, readprop, writeprop);
                }
            }
            throw new IOException("DataSource impl class (" + impl + ") have no Constructor by (Properties prop) or (String name, Properties prop) or (String name, Properties readprop, Propertieswriteprop) or (String name, URL persistxml, Properties readprop, Propertieswriteprop)");
        } catch (IOException ex) {
            throw ex;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public static DataSource createDataSource(final String unitName) throws IOException {
        return createDataSource(unitName, System.getProperty(DATASOURCE_CONFPATH) == null
            ? DataJdbcSource.class.getResource("/META-INF/persistence.xml")
            : new File(System.getProperty(DATASOURCE_CONFPATH)).toURI().toURL());
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
        Properties result = new Properties();
        boolean flag = false;
        try (final InputStream in = in0) {
            XMLStreamReader reader = XMLInputFactory.newFactory().createXMLStreamReader(in);
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    if ("persistence-unit".equalsIgnoreCase(reader.getLocalName())) {
                        if (!result.isEmpty()) result = new Properties();
                        map.put(reader.getAttributeValue(null, "name"), result);
                        flag = true;
                    } else if (flag && "property".equalsIgnoreCase(reader.getLocalName())) {
                        String name = reader.getAttributeValue(null, "name");
                        String value = reader.getAttributeValue(null, "value");
                        if (name == null) continue;
                        result.put(name, value);
                    } else if (flag && "shared-cache-mode".equalsIgnoreCase(reader.getLocalName())) { //兼容shared-cache-mode属性
                        result.put(JDBC_CACHE_MODE, reader.getElementText());
                    }
                }
            }
            in.close();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return map;
    }
}
