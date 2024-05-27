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
 * 标记注释，备注
 *
 * <p>详情见: https://redkale.org
 *
 * @see org.redkale.annotation.Comment
 * @author zhangjx
 */
@Deprecated(since = "2.8.0")
@Inherited
@Documented
@Target({TYPE, METHOD, FIELD, PARAMETER, CONSTRUCTOR, LOCAL_VARIABLE, ANNOTATION_TYPE, TYPE_PARAMETER})
@Retention(RUNTIME)
public @interface Comment {

	String name() default "";

	String value();
}
