/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 用于忽略字段、方法或类。使用场景:
 * 1、convert功能中被标记为@Ignore的字段或方法会被忽略
 * 2、FilterBean中的被标记为@Ignore的字段会被忽略
 * 3、被标记为@Ignore的Service类不会被自动加载
 * 4、被标记为@Ignore的Servlet类不会被自动加载
 *
 * @see http://www.redkale.org
 * @author zhangjx
 */
@Inherited
@Documented
@Target({TYPE, FIELD, METHOD})
@Retention(RUNTIME)
public @interface Ignore {

}
