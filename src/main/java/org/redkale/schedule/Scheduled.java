/*
 *
 */
package org.redkale.schedule;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 定时任务标记，只能作用于Service的无参数或者单一ScheduleEvent参数的方法上, 功能类似Spring里的Scheduled注解
 *
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Scheduled {

    /**
     * 名称, 也可以用于第三方实现的定时任务组件的key
     *
     * @return 名称
     */
    String name() default "";

    /**
     * cron表达式, 特殊值: <br>
     * yearly、annually、monthly、weekly、daily、midnight、hourly、minutely
     * 1m、2m、3m、5m、10m、15m、30m、1h、2h、3h、6h
     *
     * @return cron表达式
     */
    String cron() default "";

    /**
     * 时区
     *
     * @return 时区
     */
    String zone() default "";

    /**
     * 延迟时间，支持参数配置、乘法表达式和对象字段值 <br>
     * 参数值支持方式:<br>
     * 100: 设置数值
     * 5*60: 乘法表达式，值为30
     * ${env.scheduling.fixedDelay}: 读取系统配置项
     * #delays: 读取宿主对象的delays字段值作为值，字段类型必须是int、long数值类型
     *
     * @return 延迟时间
     */
    String fixedDelay() default "-1";

    /**
     * 周期时间，支持参数配置、乘法表达式和对象字段值 <br>
     * 参数值支持方式:<br>
     * 100: 设置数值
     * 5*60: 乘法表达式，值为30
     * ${env.scheduling.fixedRate}: 读取系统配置项
     * #intervals: 读取宿主对象的intervals字段值作为值，字段类型必须是int、long数值类型
     *
     * @return 周期时间
     */
    String fixedRate() default "-1";

    /**
     * 起始延迟时间，支持参数配置、乘法表达式和对象字段值 <br>
     * 参数值支持方式:<br>
     * 100: 设置数值
     * 5*60: 乘法表达式，值为30
     * ${env.scheduling.initialDelay}: 读取系统配置项
     * #inits: 读取宿主对象的inits字段值作为值，字段类型必须是int、long数值类型
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

}
