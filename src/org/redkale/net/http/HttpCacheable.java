/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.lang.annotation.*;

/**
 * 配合 BasedHttpServlet 使用， 当标记为 @HttpCacheable 的方法使用response.finish的参数将被缓存一定时间(默认值timeout=15秒)。
 * 通常情况下 @HttpCacheable 需要与 @AuthIgnore 一起使用，因为没有标记@AuthIgnore的方法一般输出的结果与当前用户信息有关。
 *
 * @author zhangjx
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HttpCacheable {

    /**
     * 超时的秒数
     *
     * @return
     */
    int timeout() default 15;
}
