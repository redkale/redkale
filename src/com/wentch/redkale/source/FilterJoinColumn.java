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

        INNER;
    }

    /**
     * 关联表 通常join表默认别名为b/c/d/...自增， 被join表默认别名为a
     *
     * @return
     */
    Class table();

    /**
     * 默认使用join表(b)的主键, join表与被join表(a)的字段必须一样
     * <p>
     * @return
     */
    String column() default "";

    JoinType type() default JoinType.INNER;
}
