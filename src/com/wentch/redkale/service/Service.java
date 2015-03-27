/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.service;

import com.wentch.redkale.util.*;

/**
 * 所有Service的实现类不得声明为final， 允许远程模式的public方法不能声明为final。
 * 
 * @Resource(name = ".*")
 * private HashMap<String, XXXService> nodemap;
 * 被注入的多个XXXService实例 但不会包含自身的XXXService。
 *
 * @author zhangjx
 */
public interface Service {

    /**
     * 该方法必须是可以重复调用， 当reload时需要重复调用init方法
     *
     * @param config
     */
    default void init(AnyValue config) {

    }

    default void destroy(AnyValue config) {

    }
}
