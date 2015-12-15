/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

/**
 * 序列化操作类
 *
 * @see http://www.redkale.org
 * @author zhangjx
 * @param <R>
 * @param <W>
 */
public abstract class Convert<R extends Reader, W extends Writer> {

    protected final Factory<R, W> factory;

    protected Convert(Factory<R, W> factory) {
        this.factory = factory;
    }

    public Factory<R, W> getFactory() {
        return this.factory;
    }
}
