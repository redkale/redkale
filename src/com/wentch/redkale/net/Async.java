/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 当Service是Remote模式时， 用该注解标注在方法上可使数据变成异步传输, 该注解只能标注在返回类型为void的public方法上
 *
 * @author zhangjx
 */
@Inherited
@Documented
@Target({TYPE, METHOD})
@Retention(RUNTIME)
public @interface Async {

}
