/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.net.InetSocketAddress;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
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
 * @param <T> 连接泛型
 */
public abstract class PoolSource<T> {

    protected final AtomicLong usingCounter = new AtomicLong();

    protected final AtomicLong creatCounter = new AtomicLong();

    protected final AtomicLong cycleCounter = new AtomicLong();

    protected final AtomicLong saveCounter = new AtomicLong();

    protected final Logger logger;

    protected final String rwtype; // "" 或 "read"  或 "write"

    protected final int maxconns;

    protected final String dbtype;

    protected int connectTimeoutSeconds;

    protected int readTimeoutSeconds;

    protected int writeTimeoutSeconds;

    protected String url;

    protected InetSocketAddress addr;

    protected String user;

    protected String password;

    protected String defdb;

    protected Properties props;

    @SuppressWarnings("OverridableMethodCallInConstructor")
    public PoolSource(String stype, Properties prop, Logger logger) {
        this.logger = logger;
        this.rwtype = stype;
        this.props = prop;
        this.url = prop.getProperty(JDBC_URL);
        this.user = prop.getProperty(JDBC_USER);
        this.password = prop.getProperty(JDBC_PWD);
        this.connectTimeoutSeconds = Integer.decode(prop.getProperty(JDBC_CONNECTTIMEOUT_SECONDS, "6"));
        this.readTimeoutSeconds = Integer.decode(prop.getProperty(JDBC_READTIMEOUT_SECONDS, "6"));
        this.writeTimeoutSeconds = Integer.decode(prop.getProperty(JDBC_WRITETIMEOUT_SECONDS, "6"));
        this.maxconns = Integer.decode(prop.getProperty(JDBC_CONNECTIONSMAX, "" + Runtime.getRuntime().availableProcessors() * 16));
        String dbtype0 = "";
        { //jdbc:mysql:// jdbc:microsoft:sqlserver:// 取://之前的到最后一个:之间的字符串
            int pos = this.url.indexOf("://");
            if (pos > 0) {
                String url0 = this.url.substring(0, pos);
                pos = url0.lastIndexOf(':');
                if (pos > 0) dbtype0 = url0.substring(pos + 1);
            }
        }
        this.dbtype = dbtype0.toLowerCase();
        parseAddressAndDbname();

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
        }
    }

    protected void parseAddressAndDbname() {
        String url0 = this.url.substring(this.url.indexOf("://") + 3);
        int pos = url0.indexOf('?'); //127.0.0.1:5432/db?charset=utr8&xxx=yy
        if (pos > 0) url0 = url0.substring(0, pos);
        pos = url0.indexOf('/'); //127.0.0.1:5432/db
        if (pos > 0) {
            this.defdb = url0.substring(pos + 1);
            url0 = url0.substring(0, pos);
        }
        pos = url0.indexOf(':');
        if (pos > 0) {
            this.addr = new InetSocketAddress(url0.substring(0, pos), Integer.parseInt(url0.substring(pos + 1)));
        } else {
            this.addr = new InetSocketAddress(url0, getDefaultPort());
        }
    }

    protected abstract int getDefaultPort();

    /**
     * 是否异步， 为true则只能调用pollAsync方法，为false则只能调用poll方法
     *
     * @return 是否异步
     */
    public abstract boolean isAysnc();

    public abstract void change(Properties property);

    public abstract T poll();

    public abstract CompletableFuture<T> pollAsync();

    public abstract void close();

    public final String getDbtype() {
        return dbtype;
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
}
