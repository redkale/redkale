/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.annotation;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.*;

/**
 * 标记参数bean
 *
 * @since 2.5.0
 */
@Inherited
@Documented
@Target(TYPE)
@Retention(RUNTIME)
public @interface Bean {}
