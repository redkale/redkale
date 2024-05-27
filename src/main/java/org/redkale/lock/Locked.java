/*
 *
 */
package org.redkale.lock;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.redkale.service.LoadMode;

/**
 * //TODO 待实现
 *
 * <p>标记在Service的锁接口, 方法有以下限制: <br>
 * 2、方法必须是protected/public 3、方法不能是final/static
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
@Documented
@Target(METHOD)
@Retention(RUNTIME)
public @interface Locked {

    /**
     * Service加载模式
     *
     * @return 模式
     */
    LoadMode mode() default LoadMode.ANY;
}
