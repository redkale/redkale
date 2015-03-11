/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.source;

import java.io.Serializable;

/**
 *
 * @author zhangjx
 */
public interface DataCacheListener {

    public <T> void insert(String sourceName, Class<T> clazz, T... entitys);

    public <T> void update(String sourceName, Class<T> clazz, T... entitys);

    public <T> void delete(String sourceName, Class<T> clazz, Serializable... ids);
}
