/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 标记参数， replace by {@link org.redkale.annotation.Serial}
 *
 * @see org.redkale.annotation.Serial
 * @since 2.5.0
 */
@Deprecated(since = "2.8.0")
@Inherited
@Documented
@Target(TYPE)
@Retention(RUNTIME)
public @interface Bean {}
