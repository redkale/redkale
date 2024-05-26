/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.*;

/**
 * 序列化时标记String字段的值是否为无转义字符且长度不超过255的字符串，通常用于类名、字段名、枚举值字符串等
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.3.0
 */
@Target({METHOD, FIELD})
@Retention(RUNTIME)
public @interface ConvertSmallString {}
