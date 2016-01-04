/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 *
 * <p> 详情见: http://www.redkale.org
 * @author zhangjx
 */
@Inherited
@Documented
@Target({FIELD})
@Retention(RUNTIME)
public @interface FilterJoinColumn {

    /**
     * 关联表 通常join表默认别名为b/c/d/...自增， 被join表默认别名为a
     *
     * @return
     */
    Class table();

    /**
     *
     * 多个关联字段, 默认使用join表(b)的主键, join表与被join表(a)的字段必须一样
     * 例如: SELECT a.* FROM user a INNER JOIN record b ON a.userid = b.userid AND a.usertype = b.usertype
     * 那么注解为: @FilterJoinColumn(table = Record.class, columns = {"userid", "usertype"})
     * <p>
     * @return
     */
    String[] columns();
}
