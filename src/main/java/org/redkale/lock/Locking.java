/*
 *
 */
package org.redkale.lock;

import java.lang.annotation.Documented;
import static java.lang.annotation.ElementType.METHOD;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;

/**
 * //TODO 待实现
 *
 * 标记在Service的锁接口, 方法有以下限制: <br>
 * 1、方法返回类型不能是void/CompletableFuture&#60;Void&#62;
 * 2、方法必须是protected/public
 * 3、方法不能是final
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
@Inherited
@Documented
@Target(METHOD)
@Retention(RUNTIME)
public @interface Locking {

}
