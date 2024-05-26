/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.*;

/**
 * 只能注解于Service类的方法的参数或参数内的Serializable字段
 *
 * <p>用于获取HTTP请求端的用户ID HttpRequest.currentUserid
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.1.0
 */
@Documented
@Target({PARAMETER, FIELD})
@Retention(RUNTIME)
public @interface RestUserid {}
