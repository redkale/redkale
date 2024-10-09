/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;
import org.redkale.util.ColumnHandler;

/**
 * 字段值转换器，常见于脱敏操作
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 *
 */
@Documented
@Target({METHOD, FIELD})
@Retention(RUNTIME)
@Repeatable(ConvertColumnHandler.ConvertColumnHandlers.class)
public @interface ConvertColumnHandler {

    /**
     * 字段值转换器
     *
     * @return ColumnHandler实现类
     *
     * @since 2.8.0
     */
    Class<? extends ColumnHandler> value() default ColumnHandler.class;

    /**
     * 解析/序列化定制化的TYPE
     *
     * @return JSON or PROTOBUF or ALL
     */
    ConvertType type() default ConvertType.ALL;

    /**
     * ConvertColumnHandler 的多用类
     *
     * <p>详情见: https://redkale.org
     *
     * @author zhangjx
     * @since 2.8.0
     */
    @Documented
    @Target({METHOD, FIELD})
    @Retention(RUNTIME)
    public static @interface ConvertColumnHandlers {

        ConvertColumnHandler[] value();
    }
}
