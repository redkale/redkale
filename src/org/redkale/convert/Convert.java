/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

/**
 * 序列化操作类
 *
 * <p>
 * 详情见: http://www.redkale.org
 *
 * @author zhangjx
 * @param <R> Reader输入的子类
 * @param <W> Writer输出的子类
 */
public abstract class Convert<R extends Reader, W extends Writer> {

    protected final ConvertFactory<R, W> factory;

    protected Convert(ConvertFactory<R, W> factory) {
        this.factory = factory;
    }

    public ConvertFactory<R, W> getFactory() {
        return this.factory;
    }
}
