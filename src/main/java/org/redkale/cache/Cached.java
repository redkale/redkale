/*
 *
 */
package org.redkale.cache;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;
import org.redkale.service.LoadMode;

/**
 * 标记在Service的缓存接口, 方法有以下限制: <br>
 * 1、方法返回类型不能是void/CompletableFuture&#60;Void&#62; 2、方法返回类型必须可json序列化 3、方法必须是protected/public 4、方法不能是final/static
 *
 * @since 2.8.0
 */
@Documented
@Target(METHOD)
@Retention(RUNTIME)
public @interface Cached {

    /**
     * 缓存的key，支持参数动态组合，比如"key_#{id}"
     *
     * @return 键
     */
    String key();

    /**
     * 缓存的hash, 不能含有':'、'#'、'@'字符
     *
     * @return hash
     */
    String hash() default CacheManager.DEFAULT_HASH;

    /**
     * 本地缓存过期时长， 0表示永不过期， -1表示不作本地缓存。<br>
     * 参数值支持方式:<br>
     * 100: 设置数值 ${env.cache.expires}: 读取系统配置项
     *
     * @return 过期时长
     */
    String localExpire() default "-1";

    /**
     * 远程缓存过期时长， 0表示永不过期， -1表示不作远程缓存。<br>
     * 参数值支持方式:<br>
     * 100: 设置数值 ${env.cache.expires}: 读取系统配置项
     *
     * @return 过期时长
     */
    String remoteExpire() default "-1";

    /**
     * 是否可以缓存null值
     *
     * @return 是否可以缓存null
     */
    boolean nullable() default false;

    /**
     * 过期时长的时间单位
     *
     * @return 时间单位
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * 备注
     *
     * @return 备注
     */
    String comment() default "";

    /**
     * Service加载模式
     *
     * @return 模式
     */
    LoadMode mode() default LoadMode.ANY;
}
