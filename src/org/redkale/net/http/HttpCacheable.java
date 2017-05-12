/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 配合 HttpServlet 使用。 <br>
 * 当标记为 &#64;HttpCacheable 的方法使用response.finish的参数将被缓存一段时间(默认值 seconds=15秒)。 <br>
 * 通常情况下 &#64;HttpCacheable 需要与 &#64;AuthIgnore 一起使用，没有标记&#64;AuthIgnore的方法一般输出的结果与当前用户信息有关。 <br>
 * <p>
 * 注意： 不能标记在HttpServlet类中已有的方法(如: execute/preExecute/authenticate)
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@Documented
@Target({METHOD})
@Retention(RUNTIME)
public @interface HttpCacheable {

    /**
     * 超时的秒数
     *
     * @return 超时秒数
     */
    int seconds() default 15;
}
