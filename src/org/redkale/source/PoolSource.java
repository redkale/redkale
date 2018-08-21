/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import static org.redkale.source.DataSources.*;

/**
 * 连接池类
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <DBChannel> 连接泛型
 */
public abstract class PoolSource<DBChannel> {

    protected final AtomicLong closeCounter = new AtomicLong();

    protected final AtomicLong usingCounter = new AtomicLong();

    protected final AtomicLong creatCounter = new AtomicLong();

    protected final AtomicLong cycleCounter = new AtomicLong();

    protected final AtomicLong saveCounter = new AtomicLong();

    protected final Semaphore semaphore;

    protected final Logger logger;

    protected final String rwtype; // "" 或 "read"  或 "write"

    protected final int maxconns;

    protected final String dbtype;

    protected int connectTimeoutSeconds;

    protected int readTimeoutSeconds;

    protected int writeTimeoutSeconds;

    protected String url;

    protected InetSocketAddress servaddr;

    protected String username;

    protected String password;

    protected String database;

    protected String encoding;

    protected Properties props;

    protected Properties attributes = new Properties();

    @SuppressWarnings("OverridableMethodCallInConstructor")
    public PoolSource(String rwtype, Semaphore semaphore, Properties prop, Logger logger) {
        this.logger = logger;
        this.rwtype = rwtype;
        this.props = prop;
        this.url = prop.getProperty(JDBC_URL);
        this.username = prop.getProperty(JDBC_USER, "");
        this.password = prop.getProperty(JDBC_PWD, "");
        this.encoding = prop.getProperty(JDBC_ENCODING, "");
        this.connectTimeoutSeconds = Integer.decode(prop.getProperty(JDBC_CONNECTTIMEOUT_SECONDS, "3"));
        this.readTimeoutSeconds = Integer.decode(prop.getProperty(JDBC_READTIMEOUT_SECONDS, "3"));
        this.writeTimeoutSeconds = Integer.decode(prop.getProperty(JDBC_WRITETIMEOUT_SECONDS, "3"));
        this.maxconns = Math.max(8, Integer.decode(prop.getProperty(JDBC_CONNECTIONS_LIMIT, "" + Runtime.getRuntime().availableProcessors() * 100)));
        this.semaphore = semaphore == null ? new Semaphore(this.maxconns) : semaphore;
        String dbtype0 = "";
        { //jdbc:mysql:// jdbc:microsoft:sqlserver:// 取://之前的到最后一个:之间的字符串
            int pos = this.url.indexOf("://");
            if (pos > 0) {
                String url0 = this.url.substring(0, pos);
                pos = url0.lastIndexOf(':');
                if (pos > 0) dbtype0 = url0.substring(pos + 1);
            } else { //jdbc:oracle:thin:@localhost:1521
                String url0 = url.substring(url.indexOf(":") + 1);
                pos = url0.indexOf(':');
                if (pos > 0) dbtype0 = url0.substring(0, pos);
            }
            if ("mysqlx".equalsIgnoreCase(dbtype0)) dbtype0 = "mysql"; //MySQL X DevAPI 
        }
        this.dbtype = dbtype0.toLowerCase();
        parseAddressAndDbnameAndAttrs();

        if ("oracle".equals(this.dbtype)) {
            this.props.setProperty(JDBC_CONTAIN_SQLTEMPLATE, "INSTR(${keystr}, ${column}) > 0");
            this.props.setProperty(JDBC_NOTCONTAIN_SQLTEMPLATE, "INSTR(${keystr}, ${column}) = 0");
            if (!this.props.containsKey(JDBC_TABLENOTEXIST_SQLSTATES)) {
                this.props.setProperty(JDBC_TABLENOTEXIST_SQLSTATES, "42000;42S02");
            }
            if (!this.props.containsKey(JDBC_TABLECOPY_SQLTEMPLATE)) {
                //注意：此语句复制表结构会导致默认值和主键信息的丢失
                this.props.setProperty(JDBC_TABLECOPY_SQLTEMPLATE, "CREATE TABLE ${newtable} AS SELECT * FROM ${oldtable} WHERE 1=2");
            }
        } else if ("sqlserver".equals(this.dbtype)) {
            this.props.setProperty(JDBC_CONTAIN_SQLTEMPLATE, "CHARINDEX(${column}, ${keystr}) > 0");
            this.props.setProperty(JDBC_NOTCONTAIN_SQLTEMPLATE, "CHARINDEX(${column}, ${keystr}) = 0");
        } else if ("postgresql".equals(this.dbtype)) {
            if (!this.props.containsKey(JDBC_TABLECOPY_SQLTEMPLATE)) { //注意：此语句复制表结构会导致默认值和主键信息的丢失
                //注意：postgresql不支持跨库复制表结构
                this.props.setProperty(JDBC_TABLECOPY_SQLTEMPLATE, "CREATE TABLE ${newtable} AS (SELECT * FROM ${oldtable} LIMIT 0)");
            }
            if (!this.props.containsKey(JDBC_TABLENOTEXIST_SQLSTATES)) {
                this.props.setProperty(JDBC_TABLENOTEXIST_SQLSTATES, "42P01;3F000");
            }
        } else if ("mysql".equals(this.dbtype)) {
            if (this.encoding.isEmpty()) this.encoding = attributes.getProperty("characterEncoding", "");
        }
    }

    protected void parseAddressAndDbnameAndAttrs() {
        if (this.url.startsWith("jdbc:h2:")) return;
        String url0 = this.url.substring(this.url.indexOf("://") + 3);
        int pos = url0.indexOf('?'); //127.0.0.1:5432/db?charset=utr8&xxx=yy
        if (pos > 0) {
            String params = url0.substring(pos + 1).replace("&amp;", "&");
            for (String param : params.split("&")) {
                int p = param.indexOf('=');
                if (p < 1) continue;
                this.attributes.put(param.substring(0, p), param.substring(p + 1));
            }
            url0 = url0.substring(0, pos);
        }
        pos = url0.indexOf('/'); //127.0.0.1:5432/db
        if (pos > 0) {
            this.database = url0.substring(pos + 1);
            url0 = url0.substring(0, pos);
        }
        pos = url0.indexOf(':');
        if (pos > 0) {
            this.servaddr = new InetSocketAddress(url0.substring(0, pos), Integer.parseInt(url0.substring(pos + 1)));
        } else {
            this.servaddr = new InetSocketAddress(url0, getDefaultPort());
        }
    }

    protected abstract int getDefaultPort();

    public abstract void change(Properties property);

    public abstract DBChannel poll();

    public abstract CompletableFuture<DBChannel> pollAsync();

    public abstract void offerConnection(final DBChannel conn);

    public abstract void close();

    public final String getDbtype() {
        return dbtype;
    }

    public final long getCloseCount() {
        return closeCounter.longValue();
    }

    public final long getUsingCount() {
        return usingCounter.longValue();
    }

    public final long getCreatCount() {
        return creatCounter.longValue();
    }

    public final long getCycleCount() {
        return cycleCounter.longValue();
    }

    public final long getSaveCount() {
        return saveCounter.longValue();
    }

    public final int getMaxconns() {
        return maxconns;
    }

    public final int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public final int getReadTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    public final int getWriteTimeoutSeconds() {
        return writeTimeoutSeconds;
    }

    public final String getUrl() {
        return url;
    }

    public final InetSocketAddress getServaddr() {
        return servaddr;
    }

    public final String getUsername() {
        return username;
    }

    public final String getPassword() {
        return password;
    }

    public final String getDatabase() {
        return database;
    }

}
