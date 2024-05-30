/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.service;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.*;

/**
 * SNCP协议中标记为目标topic参数, 该注解只能标记在类型为String的参数上。
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.1.0
 */
@Documented
@Target({PARAMETER})
@Retention(RUNTIME)
public @interface RpcTargetTopic {}
