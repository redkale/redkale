/*
 *
 */
package org.redkale.annotation;

import java.lang.annotation.*;

/**
 * 非阻塞模式标记， 标记在Service类和方法、Filter类、HttpServlet类上  <br>
 * 一般情况下，没有显注此注解的方法视为阻塞时， 以下两种情况除外:  <br>
 * 1、返回类型是CompletionStage <br>
 * 2、返回类型是void且参数存在CompletionHandler类型 <br>
 * 阻塞模式的方法会在work线程池中运行， 非阻塞在IO线程中运行。
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface NonBlocking {  //不可使用@Inherited，防止被继承, 见HttpServlet.preExecute/authenticate/execute

    boolean value() default true;
}
