/*

*/

package org.redkale.source;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;

/**
 * 设置 {@link org.redkale.source.FilterGroup}的<b>OR</b>关系
 *
 * 详情见: https://redkale.org
 *
 * @see org.redkale.source.FilterBean
 * @see org.redkale.source.FilterNode
 * @see org.redkale.source.FilterGroup
 * @author zhangjx
 * @since 2.8.0
 */
@Documented
@Target({ElementType.TYPE})
@Retention(RUNTIME)
public @interface FilterOrs {
    /**
     * OR 关系的组名
     *
     * @return 组名集合
     */
    String[] value();
}
