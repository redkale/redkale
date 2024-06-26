/**
 * ***************************************************************************** Copyright (c) 2008 - 2013 Oracle
 * Corporation. All rights reserved.
 *
 * <p>This program and the accompanying materials are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution. The Eclipse Public License is available
 * at http://www.eclipse.org/legal/epl-v10.html and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * <p>Contributors: Linda DeMichiel - Java Persistence 2.1 Linda DeMichiel - Java Persistence 2.0
 *
 * <p>****************************************************************************
 */
package org.redkale.persistence;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.*;

/**
 * Provides for the specification of generation strategies for the values of primary keys.
 *
 * <p>The <code>GeneratedValue</code> annotation may be applied to a primary key property or field of an entity or
 * mapped superclass in conjunction with the {@link Id} annotation. The use of the <code>GeneratedValue</code>
 * annotation is only required to be supported for simple primary keys. Use of the <code>GeneratedValue</code>
 * annotation is not supported for derived primary keys.
 *
 * <pre>
 *
 *     Example 1:
 *
 *     &#064;Id
 *     &#064;GeneratedValue(strategy=SEQUENCE, generator="CUST_SEQ")
 *     &#064;Column(name="CUST_ID")
 *     public Long getId() { return id; }
 *
 *     Example 2:
 *
 *     &#064;Id
 *     &#064;GeneratedValue(strategy=TABLE, generator="CUST_GEN")
 *     &#064;Column(name="CUST_ID")
 *     Long id;
 * </pre>
 *
 * @see Id
 * @since Java Persistence 1.0
 */
@Target({METHOD, FIELD})
@Retention(RUNTIME)
public @interface GeneratedValue {}
