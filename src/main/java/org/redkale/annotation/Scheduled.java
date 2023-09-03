/*
 *
 */
package org.redkale.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 定时任务标记，只能作用于Service的方法上, 功能类似Spring里的Scheduled注解
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

    String cron() default "";

    String zone() default "";

    long fixedDelay() default -1;

    String fixedDelayString() default "";

    long fixedRate() default -1;

    String fixedRateString() default "";

    long initialDelay() default -1;

    String initialDelayString() default "";

    TimeUnit timeUnit() default TimeUnit.MILLISECONDS;
}