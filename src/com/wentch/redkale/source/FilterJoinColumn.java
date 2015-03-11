/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.source;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 *
 * @author zhangjx
 */
@Inherited
@Documented
@Target({FIELD})
@Retention(RUNTIME)
public @interface FilterJoinColumn {

    public enum JoinType { //不能支持RIGHT， 因为right获取的主对象都是null

        LEFT, INNER;
    }

    /**
     * 关联表
     *
     * @return
     */
    Class table();

    /**
     * 默认使用主键
     * <p>
     * @return
     */
    String column() default "";

    JoinType type() default JoinType.INNER;
}
