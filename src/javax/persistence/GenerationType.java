/*******************************************************************************
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
 ******************************************************************************/
package javax.persistence;

/** 
 * Defines the types of primary key generation strategies. 
 *
 * @see GeneratedValue
 *
 * @since Java Persistence 1.0
 */
public enum GenerationType { 

    /**
     * Indicates that the persistence provider must assign 
     * primary keys for the entity using an underlying 
     * database table to ensure uniqueness.
     */
    TABLE, 

    /**
     * Indicates that the persistence provider must assign 
     * primary keys for the entity using a database sequence.
     */
    SEQUENCE, 

    /**
     * Indicates that the persistence provider must assign 
     * primary keys for the entity using a database identity column.
     */
    IDENTITY, 

    /**
     * Indicates that the persistence provider should pick an 
     * appropriate strategy for the particular database. The 
     * <code>AUTO</code> generation strategy may expect a database 
     * resource to exist, or it may attempt to create one. A vendor 
     * may provide documentation on how to create such resources 
     * in the event that it does not support schema generation 
     * or cannot create the schema resource at runtime.
     */
    AUTO
}
