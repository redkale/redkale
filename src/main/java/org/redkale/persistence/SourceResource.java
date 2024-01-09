/*
 *
 */
package org.redkale.persistence;

import java.lang.annotation.Documented;
import static java.lang.annotation.ElementType.TYPE;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;

/**
 * 指定Source的资源名
 *
 * <p>
 * 详情见: https://redkale.org
 *
 *
 * @see org.redkale.source.DataSqlMapper
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
@Inherited
@Documented
@Target({TYPE})
@Retention(RUNTIME)
public @interface SourceResource {

    String value();
}
