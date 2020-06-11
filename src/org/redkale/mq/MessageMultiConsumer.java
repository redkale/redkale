/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 多消费组，需要同 @RestService 一起使用
 * <p>
 * 标记 @MessageMultiConsumer 的Service的@RestMapping方法都只能是void返回类型
 *
 * <p>
 * 详情见: https://redkale.org
 *
 *
 * @author zhangjx
 *
 * @since 2.1.0
 */
@Inherited
@Documented
@Target({TYPE})
@Retention(RUNTIME)
public @interface MessageMultiConsumer {

    String module();
}
