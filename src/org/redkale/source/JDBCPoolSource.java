/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.nio.file.*;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import javax.sql.*;
import static org.redkale.source.Sources.*;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class JDBCPoolSource {

    private static final Map<String, AbstractMap.SimpleEntry<WatchService, List<WeakReference<JDBCPoolSource>>>> maps = new HashMap<>();

    private final AtomicLong usingCounter = new AtomicLong();

    private final AtomicLong creatCounter = new AtomicLong();

    private final AtomicLong cycleCounter = new AtomicLong();

    private final AtomicLong saveCounter = new AtomicLong();

    private final ConnectionPoolDataSource source;

    private final ArrayBlockingQueue<PooledConnection> queue;

    private final ConnectionEventListener listener;

    private final DataDefaultSource dataSource;

    private final String stype; // "" 或 "read"  或 "write"

    private final int max;

    private String url;

    private String user;

    private String password;

    final Properties props;

    public JDBCPoolSource(DataDefaultSource source, String stype, Properties prop) {
        this.dataSource = source;
        this.stype = stype;
        this.props = prop;
        this.source = createDataSource(prop);
        this.url = prop.getProperty(JDBC_URL);
        this.user = prop.getProperty(JDBC_USER);
        this.password = prop.getProperty(JDBC_PWD);
        this.max = Integer.decode(prop.getProperty(JDBC_CONNECTIONSMAX, "" + Runtime.getRuntime().availableProcessors() * 16));
        this.queue = new ArrayBlockingQueue<>(this.max);
        this.listener = new ConnectionEventListener() {

            @Override
            public void connectionClosed(ConnectionEvent event) {
                PooledConnection pc = (PooledConnection) event.getSource();
                if (queue.offer(pc)) saveCounter.incrementAndGet();
            }

            @Override
            public void connectionErrorOccurred(ConnectionEvent event) {
                usingCounter.decrementAndGet();
                if ("08S01".equals(event.getSQLException().getSQLState())) return; //MySQL特性， 长时间连接没使用会抛出com.mysql.jdbc.exceptions.jdbc4.CommunicationsException
                dataSource.logger.log(Level.WARNING, "connectionErronOccurred  [" + event.getSQLException().getSQLState() + "]", event.getSQLException());
            }
        };
        if (this.isOracle()) {
            this.props.setProperty(JDBC_CONTAIN_SQLTEMPLATE, "INSTR(${keystr}, ${column}) > 0");
            this.props.setProperty(JDBC_NOTCONTAIN_SQLTEMPLATE, "INSTR(${keystr}, ${column}) = 0");
        } else if (this.isSqlserver()) {
            this.props.setProperty(JDBC_CONTAIN_SQLTEMPLATE, "CHARINDEX(${column}, ${keystr}) > 0");
            this.props.setProperty(JDBC_NOTCONTAIN_SQLTEMPLATE, "CHARINDEX(${column}, ${keystr}) = 0");
        }

        try {
            this.watch();
        } catch (Exception e) {
            dataSource.logger.log(Level.WARNING, DataSource.class.getSimpleName() + " watch " + dataSource.conf + " error", e);
        }
    }

    static ConnectionPoolDataSource createDataSource(Properties property) {
        try {
            return createDataSource(property.getProperty(JDBC_SOURCE, property.getProperty(JDBC_DRIVER)),
                property.getProperty(JDBC_URL), property.getProperty(JDBC_USER), property.getProperty(JDBC_PWD));
        } catch (Exception ex) {
            throw new RuntimeException("(" + property + ") have no jdbc parameters", ex);
        }
    }

    static ConnectionPoolDataSource createDataSource(String source0, String url, String user, String password) throws Exception {
        String source = source0;
        if (source0 == null || source0.isEmpty()) {
            if (url.startsWith("jdbc:mysql:")) {
                source0 = "com.mysql.jdbc.Driver";
            } else if (url.startsWith("jdbc:mariadb:")) {
                source0 = "org.mariadb.jdbc.Driver";
            } else if (url.startsWith("jdbc:oracle:")) {
                source0 = "oracle.jdbc.driver.OracleDriver";
            } else if (url.startsWith("jdbc:postgresql:")) {
                source0 = "org.postgresql.Driver";
            } else if (url.startsWith("jdbc:microsoft:sqlserver:")) {
                source0 = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            }
        }
        if (source0 != null && source0.contains("Driver")) {  //为了兼容JPA的配置文件
            switch (source0) {
                case "org.mariadb.jdbc.Driver":
                    source = "org.mariadb.jdbc.MySQLDataSource";
                    break;
                case "com.mysql.cj.jdbc.Driver":
                case "com.mysql.jdbc.Driver":
                    try {
                        Class.forName("com.mysql.cj.jdbc.MysqlConnectionPoolDataSource");
                        source = "com.mysql.cj.jdbc.MysqlConnectionPoolDataSource";
                    } catch (Exception e) {
                        source = "com.mysql.jdbc.jdbc2.optional.MysqlConnectionPoolDataSource";
                    }
                    break;
                case "oracle.jdbc.driver.OracleDriver":
                    source = "oracle.jdbc.pool.OracleConnectionPoolDataSource";
                    break;
                case "org.postgresql.Driver":
                    source = "org.postgresql.ds.PGConnectionPoolDataSource";
                    break;
                case "com.microsoft.sqlserver.jdbc.SQLServerDriver":
                    source = "com.microsoft.sqlserver.jdbc.SQLServerConnectionPoolDataSource";
                    break;
            }
        }
        final Class clazz = Class.forName(source);
        Object pdsource = clazz.newInstance();
        if (source.contains(".postgresql.")) {
            Class driver = Class.forName("org.postgresql.Driver");
            Properties properties = (Properties) driver.getMethod("parseURL", String.class, Properties.class).invoke(null, url, new Properties());
            clazz.getMethod("setServerName", String.class).invoke(pdsource, properties.getProperty("PGHOST"));
            clazz.getMethod("setDatabaseName", String.class).invoke(pdsource, properties.getProperty("PGDBNAME"));
            clazz.getMethod("setPortNumber", int.class).invoke(pdsource, Integer.parseInt(properties.getProperty("PGPORT", "5432")));
        } else {
            Method seturlm;
            try {
                seturlm = clazz.getMethod("setUrl", String.class);
            } catch (Exception e) {
                seturlm = clazz.getMethod("setURL", String.class);
            }
            seturlm.invoke(pdsource, url);
        }
        clazz.getMethod("setUser", String.class).invoke(pdsource, user);
        clazz.getMethod("setPassword", String.class).invoke(pdsource, password);
        return (ConnectionPoolDataSource) pdsource;
    }

    final boolean isMysql() {
        return source != null && source.getClass().getName().contains(".mysql.");
    }

    final boolean isOracle() {
        return source != null && source.getClass().getName().contains("oracle.");
    }

    final boolean isSqlserver() {
        return source != null && source.getClass().getName().contains(".sqlserver.");
    }

    final boolean isPostgresql() {
        return source != null && source.getClass().getName().contains(".postgresql.");
    }

    private void watch() throws IOException {
        if (dataSource.conf == null || dataSource.name == null) return;
        final String file = dataSource.conf.getFile();
        final File f = new File(file);
        if (!f.isFile() || !f.canRead()) return;
        synchronized (maps) {
            AbstractMap.SimpleEntry<WatchService, List<WeakReference<JDBCPoolSource>>> entry = maps.get(file);
            if (entry != null) {
                entry.getValue().add(new WeakReference<>(this));
                return;
            }
            final WatchService watcher = f.toPath().getFileSystem().newWatchService();
            final List<WeakReference<JDBCPoolSource>> list = new CopyOnWriteArrayList<>();
            Thread watchThread = new Thread() {

                @Override
                public void run() {
                    try {
                        while (!this.isInterrupted()) {
                            final WatchKey key = watcher.take();
                            long d;   //防止文件正在更新过程中去读取
                            for (;;) {
                                d = f.lastModified();
                                Thread.sleep(2000L);
                                if (d == f.lastModified()) break;
                            }
                            final Map<String, Properties> m = loadPersistenceXml(new FileInputStream(file));
                            key.pollEvents().stream().forEach((event) -> {
                                if (event.kind() != ENTRY_MODIFY) return;
                                if (!((Path) event.context()).toFile().getName().equals(f.getName())) return;
                                for (WeakReference<JDBCPoolSource> ref : list) {
                                    JDBCPoolSource pool = ref.get();
                                    if (pool == null) continue;
                                    try {
                                        Properties property = m.get(pool.dataSource.name);
                                        if (property == null) property = m.get(pool.dataSource.name + "." + pool.stype);
                                        if (property != null) pool.change(property);
                                    } catch (Exception ex) {
                                        dataSource.logger.log(Level.INFO, event.context() + " occur error", ex);
                                    }
                                }
                            });
                            key.reset();
                        }
                    } catch (Exception e) {
                        dataSource.logger.log(Level.WARNING, "DataSource watch " + file + " occur error", e);
                    }
                }
            };
            f.getParentFile().toPath().register(watcher, ENTRY_MODIFY);
            watchThread.setName("DataSource-Watch-" + maps.size() + "-Thread");
            watchThread.setDaemon(true);
            watchThread.start();
            dataSource.logger.log(Level.INFO, watchThread.getName() + " start watching " + file);
            //-----------------------------------------------------------            
            list.add(new WeakReference<>(this));
            maps.put(file, new AbstractMap.SimpleEntry<>(watcher, list));
        }
    }

    public void change(Properties property) {
        Method seturlm;
        Class clazz = source.getClass();
        String newurl = property.getProperty(JDBC_URL);
        String newuser = property.getProperty(JDBC_USER);
        String newpassword = property.getProperty(JDBC_PWD);
        if (this.url.equals(newurl) && this.user.equals(newuser) && this.password.equals(newpassword)) return;
        try {
            try {
                seturlm = clazz.getMethod("setUrl", String.class);
            } catch (Exception e) {
                seturlm = clazz.getMethod("setURL", String.class);
            }
            seturlm.invoke(source, newurl);
            clazz.getMethod("setUser", String.class).invoke(source, newuser);
            clazz.getMethod("setPassword", String.class).invoke(source, newpassword);
            this.url = newurl;
            this.user = newuser;
            this.password = newpassword;
            dataSource.logger.log(Level.INFO, DataSource.class.getSimpleName() + "(" + dataSource.name + "." + stype + ") change  (" + property + ")");
        } catch (Exception e) {
            dataSource.logger.log(Level.SEVERE, DataSource.class.getSimpleName() + " dynamic change JDBC (url userName password) error", e);
        }
    }

    public Connection poll() {
        return poll(0, null);
    }

    private Connection poll(final int count, SQLException e) {
        if (count >= 3) {
            dataSource.logger.log(Level.WARNING, "create pooled connection error", e);
            throw new RuntimeException(e);
        }
        PooledConnection result = queue.poll();
        if (result == null) {
            if (usingCounter.get() >= max) {
                try {
                    result = queue.poll(6, TimeUnit.SECONDS);
                } catch (Exception t) {
                    dataSource.logger.log(Level.WARNING, "take pooled connection error", t);
                }
            }
            if (result == null) {
                try {
                    result = source.getPooledConnection();
                    result.addConnectionEventListener(listener);
                    usingCounter.incrementAndGet();
                } catch (SQLException ex) {
                    return poll(count + 1, ex);
                }
                creatCounter.incrementAndGet();
            }
        } else {
            cycleCounter.incrementAndGet();
        }
        Connection conn;
        try {
            conn = result.getConnection();
            if (!conn.isValid(1)) {
                dataSource.logger.info("sql connection is not vaild");
                usingCounter.decrementAndGet();
                return poll(0, null);
            }
        } catch (SQLException ex) {
            if (!"08S01".equals(ex.getSQLState())) {//MySQL特性， 长时间连接没使用会抛出com.mysql.jdbc.exceptions.jdbc4.CommunicationsException
                dataSource.logger.log(Level.FINER, "result.getConnection from pooled connection abort [" + ex.getSQLState() + "]", ex);
            }
            return poll(0, null);
        }
        return conn;
    }

    public long getCreatCount() {
        return creatCounter.longValue();
    }

    public long getCycleCount() {
        return cycleCounter.longValue();
    }

    public long getSaveCount() {
        return saveCounter.longValue();
    }

    public void close() {
        queue.stream().forEach(x -> {
            try {
                x.close();
            } catch (Exception e) {
            }
        });
    }
}
