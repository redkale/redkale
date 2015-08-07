/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.service;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 *
 * @author zhangjx
 */
@Inherited
@Documented
@Target({METHOD})
@Retention(RUNTIME)
public @interface MultiRun {

    boolean samerun() default true;  //是否同组节点也运营指定操作

    boolean diffrun() default true; //是否不同组节点也运营指定操作

    boolean async() default true; //分布式运行是否采用异步模式
}
