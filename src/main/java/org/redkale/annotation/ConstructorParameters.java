/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.annotation;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.*;

/**
 * 类似java.beans.ConstructorProperties, 必须配合Creator使用
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
@Documented
@Target({METHOD, CONSTRUCTOR})
@Retention(RUNTIME)
@ClassDepends
public @interface ConstructorParameters {

    String[] value();
}
