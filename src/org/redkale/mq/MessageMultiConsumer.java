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
 * 多消费组，需要同 &#64;RestService 一起使用
 * <p>
 * 通常一个topic只会被一个RestService消费， 当一个topic需要被其他RestService消费时，就需要使用&#64;MessageMultiConsumer
 *
 * <blockquote><pre>
 * &#64;RestService(name = "user", comment = "用户服务")
 * public class UserService implements Service{
 *
 *      &#64;RestMapping(comment = "用户登录")
 *      public RetResult login(LoginBean bean){
 *          //do something
 *      }
 * }
 * </pre></blockquote>
 *
 * 需求：统计用户登录次数， 可以创建一个MessageMultiConsumer 的 RestService：
 * <blockquote><pre>
 * <b>&#64;MessageMultiConsumer(module = "user") </b>
 * &#64;RestService(name = "loginstat", comment = "用户统计服务")
 * public class LoginStatService implements Service{
 *
 *      private AtomicLong counter = new AtomicLong();
 *
 *      &#64;RestMapping(name = "login", comment = "用户登录统计")
 *      public void stat(LoginBean bean){     //参数必须和UserService.login方法一致
 *          counter.incrementAndGet();
 *      }
 * }
 * </pre></blockquote>
 *
 * <p>
 * 注： 标记 &#64;MessageMultiConsumer 的Service的&#64;RestMapping方法都只能是void返回类型
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
