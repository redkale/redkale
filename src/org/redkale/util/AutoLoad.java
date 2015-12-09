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
 * 自动加载。 使用场景有两种：
 * 一、 扫描时自动加载。 系统在启动时会扫描classpath下Service和Servlet的实现类，默认情况下会加载所有的实现类， 只有当标记为@AutoLoad(false)时才会被忽略。 没标记的与标记为@AutoLoad(true)是等价的。
 * 二、 Entity类数据加载。 DataSource初始化某个标记为@javax.persistence.Cacheable的Entity类时，如果该类存在@AutoLoad时，则需要将Entity类对应的表数据全量加载进缓存中。
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
