/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.Serializable;

/**
 *
 * @see http://www.redkale.org
 * @author zhangjx
 */
public interface DataCacheListener {

    public <T> void insertCache(Class<T> clazz, T... entitys);

    public <T> void updateCache(Class<T> clazz, T... entitys);

    public <T> void deleteCache(Class<T> clazz, Serializable... ids);
}
