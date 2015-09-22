/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.convert;

import java.lang.reflect.Type;

/**
 *
 * @author zhangjx
 * @param <R>
 * @param <T>
 */
public interface Decodeable<R extends Reader, T> {

    public T convertFrom(final R in);

    /**
     * 泛型映射接口
     *
     * @return
     */
    public Type getType();

}
