/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 自动加载。 使用场景：
 * 1、被标记为@AutoLoad(false)的Service类不会被自动加载
 * 2、被标记为@AutoLoad(false)的Servlet类不会被自动加载
 * 3、被标记为@AutoLoad且同时被标记为@javax.persistence.Cacheable的Entity类在被DataSource初始化时需要将Entity类对应的表数据全量加载进缓存中。
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
