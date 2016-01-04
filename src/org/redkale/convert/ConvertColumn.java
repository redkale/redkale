/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

/**
 * 依附在setter、getter方法、字段进行简单的配置
 *
 * <p> 详情见: http://www.redkale.org
 * @author zhangjx
 */
@Inherited
@Documented
@Target({METHOD, FIELD})
@Retention(RUNTIME)
@Repeatable(ConvertColumns.class)
public @interface ConvertColumn {

    /**
     * 给字段取个别名， 只对JSON有效
     *
     * @return
     */
    String name() default "";

    /**
     * 解析/序列化时是否屏蔽该字段
     *
     * @return
     */
    boolean ignore() default false;

    /**
     * 解析/序列化定制化的TYPE
     *
     * @return
     */
    ConvertType type() default ConvertType.ALL;
}
