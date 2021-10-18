/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 接收命令的标记， 只能标记在本地模式下Service里参数为String且返回类型为void的public方法上
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.1.0
 */
@Inherited
@Documented
@Target({METHOD})
@Retention(RUNTIME)
public @interface Command {

}
