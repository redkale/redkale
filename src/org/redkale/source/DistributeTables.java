/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 当使用DistributeGenerator控制主键值时， 如果表A与表AHistory使用同一主键时， 就需要将表A的class标记：
 * <blockquote><pre>
 *  &#64;DistributeTables({AHistory.class})
 *  public class A {
 *  }
 * </pre></blockquote>
 * 这样DistributeGenerator将从A、B表中取最大值来初始化主键值。 常见场景就是表B是数据表A对应的历史表
 *
 * <p>
 * 详情见: http://redkale.org
 *
 * @author zhangjx
 */
@Target({TYPE})
@Retention(RUNTIME)
public @interface DistributeTables {

    Class[] value();
}
