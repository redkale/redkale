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
package javax.persistence;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.*;

/**
 * Specifies that the class is an entity. This annotation is applied to the entity class.
 *
 * @since Java Persistence 1.0
 * @deprecated replace by {@link org.redkale.persistence.Entity}
 * @see org.redkale.persistence.Entity
 */
@Deprecated(since = "2.8.0")
@Inherited
@Documented
@Target(TYPE)
@Retention(RUNTIME)
public @interface Entity {

	/**
	 * (Optional) The entity name. Defaults to the unqualified name of the entity class. This name is used to refer to
	 * the entity in queries. The name must not be a reserved literal in the Java Persistence query language.
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
}
