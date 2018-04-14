/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

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

    protected final String stype; // "" 或 "read"  或 "write"

    protected final int maxconns;

    protected String url;

    protected String user;

    protected String password;

    protected String defdb;

    protected Properties props;

    public PoolSource(String stype, Properties prop, Logger logger) {
        this.logger = logger;
        this.stype = stype;
        this.props = prop;
        this.url = prop.getProperty(JDBC_URL);
        this.user = prop.getProperty(JDBC_USER);
        this.password = prop.getProperty(JDBC_PWD);
        this.maxconns = Integer.decode(prop.getProperty(JDBC_CONNECTIONSMAX, "" + Runtime.getRuntime().availableProcessors() * 16));
    }

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
