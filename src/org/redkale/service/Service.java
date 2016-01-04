/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.service;

import org.redkale.util.*;

/**
 * 所有Service的实现类不得声明为final， 允许远程模式的public方法和public String name()方法都不能声明为final。
 * 注意: "$"是一个很特殊的Service.name值 。 被标记为@Resource(name = "$") 的Service的资源名与所属父Service的资源名一致。
 *
 * <p>
 * @Resource(name = ".*")
 * private HashMap<String, XXXService> nodemap;
 * 被注入的多个XXXService实例 但不会包含自身的XXXService。
 *
 * <p> 详情见: http://www.redkale.org
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

    /**
     * Service的name， 一个Service在同一进程内可以包含多个实例， 使用name区分
     * <p>
     * @return
     */
    default String name() {
        return "";
    }
}
