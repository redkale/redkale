/*
 *
 */
package org.redkale.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 定时任务标记，只能作用于Service的protected且无参数方法上, 功能类似Spring里的Scheduled注解
 *
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Scheduled {

    /**
     * 名称
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
     *
     * @return deplay时间
     */
    String fixedDelay() default "-1";

    /**
     *
     * @return 周期时间
     */
    String fixedRate() default "-1";

    /**
     *
     * @return 起始deplay时间
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
