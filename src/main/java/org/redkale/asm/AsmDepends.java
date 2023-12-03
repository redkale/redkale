/*
 *
 */
package org.redkale.asm;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

/**
 * 被标记的元素表示会被asm动态字节码调用到
 *
 * <p>
 * 详情见: https://redkale.org
 *
 *
 * @author zhangjx
 * @since 2.8.0
 */
@Documented
@Target({TYPE, METHOD, FIELD})
@Retention(SOURCE)
public @interface AsmDepends {

    Class[] value() default {};
}
