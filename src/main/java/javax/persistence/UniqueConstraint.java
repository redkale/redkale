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

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.*;

/**
 * Specifies that a unique constraint is to be included in the generated DDL for a primary or secondary table.
 *
 * <pre>
 *    Example:
 *    &#064;Entity
 *    &#064;Table(
 *        name="EMPLOYEE",
 *        uniqueConstraints=
 *            &#064;UniqueConstraint(columnNames={"EMP_ID", "EMP_NAME"})
 *    )
 *    public class Employee { ... }
 * </pre>
 *
 * @since Java Persistence 1.0
 * @deprecated replace by {@link org.redkale.persistence.UniqueConstraint}
 * @see org.redkale.persistence.UniqueConstraint
 */
@Deprecated(since = "2.8.0")
@Target({})
@Retention(RUNTIME)
public @interface UniqueConstraint {

	/**
	 * (Optional) Constraint name. A provider-chosen name will be chosen if a name is not specified.
	 *
	 * @return String
	 * @since Java Persistence 2.0
	 */
	String name() default "";

	/**
	 * (Required) An array of the column names that make up the constraint.
	 *
	 * @return String[]
	 */
	String[] columnNames();
}
