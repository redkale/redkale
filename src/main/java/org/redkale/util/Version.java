/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.*;

/**
 * 版本, 可用于标记Service的接口版本变化
 *
 * <p>详情见: https://redkale.org
 *
 * @since 2.1.0
 * @author zhangjx
 * @deprecated 暂不实现
 */
@Deprecated(since = "2.8.0")
@Inherited
@Documented
@Target({TYPE, METHOD})
@Retention(RUNTIME)
public @interface Version {

	int value();
}
