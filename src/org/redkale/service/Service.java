/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.service;

import org.redkale.util.*;

/**
 * 所有Service的实现类不得声明为final， 允许远程模式的public方法都不能声明为final。
 * 注意: "$"是一个很特殊的Service.name值 。 被标记为@Resource(name = "$") 的Service的资源名与所属父Service的资源名一致。
 *
 * <blockquote><pre>
 * Service的资源类型
 * 业务逻辑的Service通常有两种编写方式：
 *    1、只写一个Service实现类。
 *    2、先定义业务的Service接口或抽象类，再编写具体实现类。
 * 第二种方式需要在具体实现类上使用&#64;ResourceType指明资源注入的类型。
 * </pre></blockquote>
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public interface Service {

    /**
     * 该方法必须是可以重复调用， 当reload时需要重复调用init方法
     *
     * @param config 配置参数
     */
    default void init(AnyValue config) {

    }

    /**
     * 进程退出时，调用Service销毁
     *
     * @param config 配置参数
     */
    default void destroy(AnyValue config) {

    }

    /**
     * Service的接口版本号
     * <b>注: public方法的参数或返回类型或参数类型内部变更后改值必须进行改变</b>
     *
     * @return 接口版本号
     */
    default int version() {
        return 0;
    }

}
