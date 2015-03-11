/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.util;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 自动全量缓存
 *
 * @author zhangjx
 */
@Inherited
@Documented
@Target({TYPE})
@Retention(RUNTIME)
public @interface AutoLoad {

    boolean value() default true;
}
