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
 * 默认情况下FilterBean下的过滤字段之间是AND关系。 <br>
 * 当需要使用OR或AND OR组合过滤查询时需要使用 FilterOrs、FilterGroup。 <br>
 * 示例一:
 *
 * <blockquote>
 *
 * <pre>
 * &#64;FilterOrs({"g1"})
 * public class TestFilterBean implements FilterBean {
 *
 *      private int id;
 *
 *      &#64;FilterGroup("g1")
 *      private String desc;
 *
 *      &#64;FilterGroup("g1")
 *      private String name;
 * }
 * </pre>
 *
 * </blockquote>
 *
 * 转换的SQL语句为: WHERE id = ? AND (desc = ? OR name = ?)
 *
 * <p>示例二:
 *
 * <blockquote>
 *
 * <pre>
 * &#64;FilterOrs({"g1","subg2"})
 * public class TestFilterBean implements FilterBean {
 *
 *      private int id;
 *
 *      &#64;FilterGroup("g1.subg1")
 *      &#64;FilterColumn(express = LIKE)
 *      private String desc;
 *
 *      &#64;FilterGroup("g1.subg1")
 *      &#64;FilterColumn(express = LIKE)
 *      private String name;
 *
 *      &#64;FilterGroup("g1.subg2")
 *      private int age;
 *
 *      &#64;FilterGroup("g1.subg2")
 *      private int birthday;
 * }
 * </pre>
 *
 * </blockquote>
 *
 * 转换的SQL语句为: WHERE id = ? AND ((desc LIKE ? AND name LIKE ?) OR (age = ? OR birthday = ?)) <br>
 */
/**
 * 详情见: https://redkale.org
 *
 * @see org.redkale.source.FilterBean
 * @see org.redkale.source.FilterNode
 * @see org.redkale.source.FilterOrs
 * @author zhangjx
 */
@Documented
@Target({FIELD})
@Retention(RUNTIME)
public @interface FilterGroup {

    String value() default "";
}
