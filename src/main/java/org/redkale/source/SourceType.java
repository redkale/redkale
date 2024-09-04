/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.source;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 显式的指明Source多个资源类型。
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
@Documented
@Target({TYPE})
@Retention(RUNTIME)
@Repeatable(SourceType.SourceTypes.class)
public @interface SourceType {

    Class value();

    @Documented
    @Target({TYPE})
    @Retention(RUNTIME)
    @interface SourceTypes {

        SourceType[] value();
    }
}
