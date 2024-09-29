/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 序列化时标记String字段的值是否为无转义字符且长度不超过255的字符串，通常用于类名、字段名、枚举值字符串等
 * replace by {@link org.redkale.convert.ConvertStandardString}
 *
 * <p>详情见: https://redkale.org
 *
 * @see org.redkale.convert.ConvertStandardString
 * @deprecated 2.8.0
 * @author zhangjx
 * @since 2.3.0
 */
@Deprecated(since = "2.8.0")
@Target({METHOD, FIELD})
@Retention(RUNTIME)
public @interface ConvertSmallString {}
