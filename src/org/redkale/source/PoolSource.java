/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.util.Properties;
import java.util.concurrent.CompletableFuture;

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
}
