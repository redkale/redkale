/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.service;

import java.io.*;
import javax.annotation.*;
import org.redkale.source.*;
import org.redkale.util.*;

/**
 *
 * @author zhangjx
 */
@AutoLoad(false)
public class DataCacheListenerService implements DataCacheListener, Service {

    @Resource(name = "$")
    private DataSource source;

    @Override
    @MultiRun(selfrun = false, async = true)
    public <T> void insertCache(Class<T> clazz, T... entitys) {
        ((DataDefaultSource) source).insertCache(clazz, entitys);
    }

    @Override
    @MultiRun(selfrun = false, async = true)
    public <T> void updateCache(Class<T> clazz, T... entitys) {
        ((DataDefaultSource) source).updateCache(clazz, entitys);
    }

    @Override
    @MultiRun(selfrun = false, async = true)
    public <T> void deleteCache(Class<T> clazz, Serializable... ids) {
        ((DataDefaultSource) source).deleteCache(clazz, ids);
    }

}
