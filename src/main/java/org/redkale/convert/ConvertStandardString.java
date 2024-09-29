/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.convert;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;

/**
 * 序列化时标记String字段的值是否为无转义字符且长度不超过255的字符串，通常用于类名、字段名、枚举值字符串等
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
@Target({METHOD, FIELD})
@Retention(RUNTIME)
public @interface ConvertStandardString {}
