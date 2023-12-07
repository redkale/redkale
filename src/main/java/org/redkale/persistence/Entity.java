/** *****************************************************************************
 * Copyright (c) 2008 - 2013 Oracle Corporation. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0
 * which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 *     Linda DeMichiel - Java Persistence 2.1
 *     Linda DeMichiel - Java Persistence 2.0
 *
 ***************************************************************************** */
package org.redkale.persistence;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Specifies that the class is an entity. This annotation is applied to the
 * entity class.
 *
 * @since Java Persistence 1.0
 */
@Inherited
@Documented
@Target(TYPE)
@Retention(RUNTIME)
public @interface Entity {

    /**
     * (Optional) The entity name. Defaults to the unqualified
     * name of the entity class. This name is used to refer to the
     * entity in queries. The name must not be a reserved literal
     * in the Java Persistence query language.
     *
     * @return String
     */
    String name() default "";

    /**
     * (Optional) The comment of the entity.
     *
     * @return String
     */
    String comment() default "";

    /**
     * (Optional) 是否缓存实体对象
     *
     * @return boolean
     */
    boolean cacheable() default false;

    /**
     * (Optional) 定时自动更新缓存的周期秒数，为0表示不做定时更新， 大于0表示每经过interval秒后会自动从数据库中拉取数据更新Cache
     *
     * @return int
     */
    int cacheInterval() default 0;

    /**
     * (Optional) DataSource是否直接返回对象的真实引用， 而不是copy一份
     *
     * @return boolean
     */
    boolean cacheDirect() default false;
}
