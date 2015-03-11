/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.source;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.lang.annotation.ElementType.FIELD;
import java.lang.annotation.*;

/*
 * 默认情况下FilterBean下的过滤字段之间是AND关系。
 * 当需要使用OR或AND OR组合过滤查询时需要使用 FilterGroup。
 * FilterGroup 的value 必须是[OR]或者[AND]开头， 多级需要用点.分隔。 (注: 暂时不支持多级)
 * 示例一:
 * public class TestFilterBean implements FilterBean {
 *
 *      private int id;
 *
 *      @FilterGroup("[OR]g1")
 *      private String desc;
 *
 *      @FilterGroup("[OR]g1")
 *      private String name;
 * }
 * 转换的SQL语句为:  WHERE id = ? AND (desc = ? OR name = ?)
 *
 * 示例二:
 * public class TestFilterBean implements FilterBean {
 *
 *      private int id;
 *
 *      @FilterGroup("[OR]g1.[AND]subg1")
 *      @FilterColumn(express = LIKE)
 *      private String desc;
 *
 *      @FilterGroup("[OR]g1.[AND]subg1")
 *      @FilterColumn(express = LIKE)
 *      private String name;
 *
 *      @FilterGroup("[OR]g1.[OR]subg2")
 *      private int age;
 *
 *      @FilterGroup("[OR]g1.[OR]subg2")
 *      private int birthday;
 * }
 * 转换的SQL语句为:  WHERE id = ? AND ((desc LIKE ? AND name LIKE ?) OR (age = ? OR birthday = ?))
 * 因为默认是AND关系， @FilterGroup("") 等价于  @FilterGroup("[AND]")
 * 所以示例二的@FilterGroup("[OR]g1.[AND]subg1") 可以简化为 @FilterGroup("[OR]g1")
 */
/**
 * @author zhangjx
 */
@Inherited
@Documented
@Target({FIELD})
@Retention(RUNTIME)
public @interface FilterGroup {

    String value() default "[AND]";
}
