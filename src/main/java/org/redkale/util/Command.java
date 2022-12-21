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
 * 接收命令的标记， 只能标记在本地模式下Service里参数为(String)或(String, String[])的public方法上
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.1.0
 */
@Deprecated
@Inherited
@Documented
@Target({METHOD})
@Retention(RUNTIME)
public @interface Command {

    /**
     * 命令号，没有指定值则接收所有的命令
     *
     * @return String
     */
    String value() default "";

    /**
     * 参数帮助说明，在value不为空命令redkale --help时显示
     *
     * @return String
     *
     * @since 2.7.0
     */
    String description() default "";

    /**
     * 描述
     *
     * @return String
     */
    String comment() default "";
}
