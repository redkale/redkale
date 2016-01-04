/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import java.lang.reflect.Type;

/**
 *
 * <p> 详情见: http://www.redkale.org
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
