/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@Target({FIELD})
@Retention(RUNTIME)
public @interface DistributeGenerator {

    long initialValue() default 1;

    /**
     * 如果allocationSize的值小于或等于1,则主键不会加上nodeid
     *
     * @return allocationSize
     */
    int allocationSize() default 1000;
}
