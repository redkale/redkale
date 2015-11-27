/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.http;

import java.lang.annotation.*;

/**
 *
 * @author zhangjx
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HttpCacheable {

    /**
     * 超时的秒数
     *
     * @return
     */
    int timeout() default 15;
}
