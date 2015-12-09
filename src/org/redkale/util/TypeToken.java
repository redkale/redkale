/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * 
 * 获取泛型的Type类
 *
 * @author zhangjx
 * @param <T>
 */
public abstract class TypeToken<T> {

    private final Type type;

    public TypeToken() {
        type = ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
    }

    public final Type getType() {
        return type;
    }
}
