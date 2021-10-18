/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 等于level日志级别且包含keys字符串的日志才会被排除 <br>
 *
 * <blockquote><pre>
 * &#64;LogExcludeLevel(levels = {"FINEST"}, keys = {"SET username ="})
 * public class UserRecord {
 *      public int userid;
 *      public String username = "";
 * }
 *
 * 这样当调用DataSource对UserRecord对象进行操作时，拼接的SQL语句含"SET username ="字样的都会在FINEST日志级别过滤掉
 * </pre></blockquote>
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@Documented
@Target({TYPE})
@Retention(RUNTIME)
@Repeatable(LogExcludeLevel.LogExcludeLevels.class)
public @interface LogExcludeLevel {

    String[] levels();

    String[] keys();

    @Documented
    @Target({TYPE})
    @Retention(RUNTIME)
    @interface LogExcludeLevels {

        LogExcludeLevel[] value();
    }
}
