/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.source;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 *
 * @author zhangjx
 */
@Target({FIELD})
@Retention(RUNTIME)
public @interface DistributeGenerator {

    /**
     * 当使用DistributeGenerator控制主键值时， 如果表A删除的数据迁移到表B时， 就需要将表A的class标记：
     * DistributeTables({B.class})
     * public class A {
     * }
     * 这样DistributeGenerator将从A、B表中取最大值来初始化主键值。
     *
     * @author zhangjx
     */
    @Target({TYPE})
    @Retention(RUNTIME)
    public @interface DistributeTables {

        Class[] value();
    }

    int initialValue() default 1;

    /**
     * 如果allocationSize的值小于或等于1,则主键不会加上nodeid
     * <p>
     * @return
     */
    int allocationSize() default 1000;
}
