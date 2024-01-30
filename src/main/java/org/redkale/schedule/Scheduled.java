/*
 *
 */
package org.redkale.schedule;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;
import org.redkale.service.LoadMode;

/**
 * 定时任务标记，只能作用于Service的无参数或者单一ScheduleEvent参数的菲static方法上, 功能类似Spring里的Scheduled注解
 *
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Scheduled {

    /**
     * 名称, 可用于第三方实现的定时任务组件的key
     *
     * @return 名称
     */
    String name() default "";

    /**
     * cron表达式, 特殊值: <br>
     * &#64;yearly、&#64;annually、&#64;monthly、&#64;weekly、&#64;daily、&#64;midnight、&#64;hourly、&#64;minutely
     * &#64;1m、&#64;2m、&#64;3m、&#64;5m、&#64;10m、&#64;15m、&#64;30m、
     * &#64;1h、&#64;2h、&#64;3h、&#64;6h
     * ${env.scheduling.cron}: 读取系统配置项
     *
     * @return cron表达式
     */
    String cron() default "";

    /**
     * 时区, 例如: Asia/Shanghai
     *
     * @return 时区
     */
    String zone() default "";

    /**
     * 延迟时间，支持参数配置、乘法表达式和对象字段值 <br>
     * 参数值支持方式:<br>
     * 100: 设置数值
     * ${env.scheduling.fixedDelay}: 读取系统配置项
     *
     * 值大于0且fixedRate小于0则使用 ScheduledThreadPoolExecutor.scheduleWithFixedDelay
     *
     * @return 延迟时间
     */
    String fixedDelay() default "-1";

    /**
     * 周期时间，支持参数配置、乘法表达式和对象字段值 <br>
     * 参数值支持方式:<br>
     * 100: 设置数值
     * ${env.scheduling.fixedRate}: 读取系统配置项
     *
     * 值大于0则使用 ScheduledThreadPoolExecutor.scheduleAtFixedRate
     *
     * @return 周期时间
     */
    String fixedRate() default "-1";

    /**
     * 起始延迟时间，支持参数配置、乘法表达式和对象字段值 <br>
     * 参数值支持方式:<br>
     * 100: 设置数值
     * ${env.scheduling.initialDelay}: 读取系统配置项
     *
     * 值大于0且fixedRate和fixedDelay小于0则使用 ScheduledThreadPoolExecutor.schedule
     *
     * @return 起始延迟时间
     */
    String initialDelay() default "-1";

    /**
     * 时间单元
     *
     * @return 时间单元
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
    LoadMode mode() default LoadMode.LOCAL;
}
