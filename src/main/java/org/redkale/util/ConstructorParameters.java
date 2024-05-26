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
 * 类似java.beans.ConstructorProperties, 必须配合Creator使用
 *
 * <p>详情见: https://redkale.org
 *
 * @see org.redkale.annotation.ConstructorParameters
 * @author zhangjx
 * @deprecated replaced by org.redkale.annotation.ConstructorParameters
 * @see org.redkale.annotation.ConstructorParameters
 */
@Deprecated(since = "2.8.0")
@Documented
@Target({METHOD, CONSTRUCTOR})
@Retention(RUNTIME)
public @interface ConstructorParameters {

    String[] value();
}
