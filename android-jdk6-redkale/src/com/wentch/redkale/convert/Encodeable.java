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
 * @param <W>
 * @param <T>
 */
public interface Encodeable<W extends Writer, T> {

    public void convertTo(final W out, T value);

    /**
     * 泛型映射接口
     *
     * @return
     */
    public Type getType();

}
