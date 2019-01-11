/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.*;

/**
 * 加密字段标记 <br>
 * 注意: 加密字段不能用于 LIKE 等过滤查询 <br>
 * 如果有对加密字段进行过滤查询的需求，就要保证加密算法也能兼容LIKE，如："abc"的加密字符串也是"abcde"的加密字符串的一部分
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.0.0
 */
@Inherited
@Documented
@Target({FIELD})
@Retention(RUNTIME)
public @interface CryptColumn {

    Class<? extends CryptHandler> handler();
}
