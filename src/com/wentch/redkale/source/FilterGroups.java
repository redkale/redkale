/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.source;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.*;

/**
 *
 * @author zhangjx
 */
@Inherited
@Documented
@Target({FIELD})
@Retention(RUNTIME)
public @interface FilterGroups {

    FilterGroup[] value();
}
