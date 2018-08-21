/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.lang.reflect.Method;
import java.net.URL;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import javax.sql.*;
import static org.redkale.source.DataSources.*;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class PoolJdbcSource extends PoolSource<Connection> {

    protected final ConnectionPoolDataSource source;

    protected final ArrayBlockingQueue<PooledConnection> queue;

    protected final ConnectionEventListener listener;

    protected final String unitName;

    protected final URL persistxml;

    public PoolJdbcSource(String unitName, URL persistxml, String rwtype, ArrayBlockingQueue aqueue, Semaphore semaphore, Properties prop, Logger logger) {
        super(rwtype, semaphore, prop, logger);
        this.unitName = unitName;
        this.persistxml = persistxml;
        this.source = createDataSource(prop);
        this.queue = aqueue == null ? new ArrayBlockingQueue<>(this.maxconns) : aqueue;
        this.listener = new ConnectionEventListener() {

            @Override
            public void connectionClosed(ConnectionEvent event) {
                PooledConnection pc = (PooledConnection) event.getSource();
                if (queue.offer(pc)) {
                    saveCounter.incrementAndGet();
                } else {
                    try {
                        pc.close();
                    } catch (Exception e) {
                        logger.log(Level.INFO, DataSource.class.getSimpleName() + " " + pc + " close error", e);
                    }
                }
            }

            @Override
            public void connectionErrorOccurred(ConnectionEvent event) {
                usingCounter.decrementAndGet();
                if ("08S01".equals(event.getSQLException().getSQLState())) return; //MySQL特性， 长时间连接没使用会抛出com.mysql.jdbc.exceptions.jdbc4.CommunicationsException
                logger.log(Level.WARNING, "connectionErronOccurred  [" + event.getSQLException().getSQLState() + "]", event.getSQLException());
            }
        };
    }

    private static ConnectionPoolDataSource createDataSource(Properties property) {
        try {
            return createDataSource(property.getProperty(JDBC_SOURCE, property.getProperty(JDBC_DRIVER)),
                property.getProperty(JDBC_URL), property.getProperty(JDBC_USER), property.getProperty(JDBC_PWD));
        } catch (Exception ex) {
            throw new RuntimeException("(" + property + ") have no jdbc parameters", ex);
        }
    }

    private static ConnectionPoolDataSource createDataSource(String source0, String url, String user, String password) throws Exception {
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
            } else if (url.startsWith("jdbc:h2")) {
                source0 = "org.h2.Driver";
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
                        Thread.currentThread().getContextClassLoader().loadClass("com.mysql.cj.jdbc.MysqlConnectionPoolDataSource");
                        source = "com.mysql.cj.jdbc.MysqlConnectionPoolDataSource";
                    } catch (Throwable e) {
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
                case "org.h2.Driver":
                    source = "org.h2.jdbcx.JdbcDataSource";
                    break;
            }
        }
        final Class clazz = Thread.currentThread().getContextClassLoader().loadClass(source);
        Object pdsource = clazz.getDeclaredConstructor().newInstance();
        if (source.contains(".postgresql.")) {
            Class driver = Thread.currentThread().getContextClassLoader().loadClass("org.postgresql.Driver");
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

    @Override
    protected int getDefaultPort() {
        return 0;
    }

    @Override
    public void change(Properties property) {
        Method seturlm;
        Class clazz = source.getClass();
        String newurl = property.getProperty(JDBC_URL, this.url);
        String newuser = property.getProperty(JDBC_USER, this.username);
        String newpassword = property.getProperty(JDBC_PWD, this.password);
        if (Objects.equals(this.url, newurl) && Objects.equals(this.username, newuser) && Objects.equals(this.password, newpassword)) return;
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
            this.username = newuser;
            this.password = newpassword;
            logger.log(Level.INFO, DataSource.class.getSimpleName() + "(" + unitName + "." + rwtype + ") change  (" + property + ")");
        } catch (Exception e) {
            logger.log(Level.SEVERE, DataSource.class.getSimpleName() + " dynamic change JDBC (url userName password) error", e);
        }
    }

    @Override
    public void offerConnection(final Connection conn) {
        if (conn == null) return;
        try {
            conn.close();
        } catch (Exception e) {
            logger.log(Level.WARNING, "closeSQLConnection abort", e);
        }
    }

    @Override
    public Connection poll() {
        return poll(0, null);
    }

    @Override
    public CompletableFuture<Connection> pollAsync() {
        return CompletableFuture.completedFuture(poll());
    }

    private Connection poll(final int count, SQLException e) {
        if (count >= 3) {
            logger.log(Level.WARNING, "create pooled connection error", e);
            throw new RuntimeException(e);
        }
        PooledConnection result = queue.poll();
        if (result == null) {
            if (usingCounter.get() >= maxconns) {
                try {
                    result = queue.poll(6, TimeUnit.SECONDS);
                } catch (Exception t) {
                    logger.log(Level.WARNING, "take pooled connection error", t);
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
                logger.info("sql connection is not vaild");
                usingCounter.decrementAndGet();
                return poll(0, null);
            }
        } catch (SQLException ex) {
            if (!"08S01".equals(ex.getSQLState())) {//MySQL特性， 长时间连接没使用会抛出com.mysql.jdbc.exceptions.jdbc4.CommunicationsException
                logger.log(Level.FINER, "result.getConnection from pooled connection abort [" + ex.getSQLState() + "]", ex);
            }
            return poll(0, null);
        }
        return conn;
    }

    @Override
    public void close() {
        queue.stream().forEach(x -> {
            try {
                x.close();
            } catch (Exception e) {
            }
        });
    }

}
