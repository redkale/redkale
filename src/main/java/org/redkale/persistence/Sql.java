/*
 *
 */
package org.redkale.persistence;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 原始sql语句
 *
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @see org.redkale.source.DataSqlMapper
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
@Inherited
@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Sql {

    String value();
}
