/*
 *
 */
package org.redkale.annotation;

import java.lang.annotation.Documented;
import static java.lang.annotation.ElementType.METHOD;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * //TODO 待实现
 *
 * 标记在Service的缓存接口, 方法有以下限制: <br>
 * 1、方法返回类型不能是void/CompletableFuture&#60;Void&#62;
 * 2、方法必须是protected/public
 * 3、方法不能是final
 *
 * @since 2.8.0
 */
@Inherited
@Documented
@Target(METHOD)
@Retention(RUNTIME)
public @interface Caching {

    /**
     * 缓存的key，支持参数动态组合，比如"key_#{id}"
     *
     * @return 键
     */
    String key();

    /**
     * 缓存的hash, 默认值用当前Service类的SimpleName
     *
     * @return hash
     */
    String map() default "";

    /**
     * 本地缓存过期时长， 0表示永不过期， -1表示不作本地缓存。<br>
     * 参数值支持方式:<br>
     * 100: 设置数值
     * 5*60: 乘法表达式，值为30
     * ${env.cache.expires}: 读取系统配置项
     * #delays: 读取宿主对象的delays字段值作为值，字段类型必须是int、long数值类型
     *
     * @return 过期时长
     */
    String localExpire() default "-1";

    /**
     * 远程缓存过期时长， 0表示永不过期， -1表示不作远程缓存。<br>
     * 参数值支持方式:<br>
     * 100: 设置数值
     * 5*60: 乘法表达式，值为30
     * ${env.cache.expires}: 读取系统配置项
     * #delays: 读取宿主对象的delays字段值作为值，字段类型必须是int、long数值类型
     *
     * @return 过期时长
     */
    String remoteExpire() default "-1";

    /**
     * 过期时长的时间单位
     *
     * @return 时间单位
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * 是否可以缓存null值
     *
     * @return 是否可以缓存null
     */
    boolean nullable() default false;

}
