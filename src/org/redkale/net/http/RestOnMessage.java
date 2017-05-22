/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 标记在RestWebSocket的接收消息方法上
 *
 * <br><p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@Inherited
@Documented
@Target({METHOD})
@Retention(RUNTIME)
public @interface RestOnMessage {

    /**
     * 请求的方法名, 不能含特殊字符
     *
     * @return String
     */
    String name() default "";

    /**
     * 备注描述
     *
     * @return String
     */
    String comment() default "";
}
