/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.service;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * SNCP协议中用于CompletionHandler回调函数中的attach字段。
 *
 *
 * <p> 详情见: http://www.redkale.org
 * @author zhangjx
 */
@Inherited
@Documented
@Target({PARAMETER})
@Retention(RUNTIME)
public @interface DynAttachment {

}
