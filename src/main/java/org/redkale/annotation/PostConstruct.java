/*
 *
 */
package org.redkale.annotation;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * @since Common Annotations 1.0
 * @since 2.8.0
 */
@Documented
@Retention(RUNTIME)
@Target(METHOD)
public @interface PostConstruct {}
