/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.annotation;

import java.lang.annotation.*;

/**
 * @since Common Annotations 1.0
 *
 * @since 2.8.0
 */
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Resource {
  
    /**
     * 是否必须存在
     *
     * @return boolean
     *
     * @since 2.8.0
     */
    public boolean required() default true;

    /**
     * 资源名称
     *
     * @return String
     */
    public String name() default "";

    /**
     * 依赖注入的类型
     *
     * @return Class
     */
    public Class<?> type() default Object.class;

}
