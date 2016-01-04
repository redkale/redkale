/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.watch;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 该注解只能放在field类型为Collection, Map, 或者java.util.concurrent.atomic.AtomicXXX的Number类);
 *
 * <p>
 * 详情见: http://www.redkale.org
 *
 * @author zhangjx
 */
@Inherited
@Documented
@Target({FIELD})
@Retention(RUNTIME)
public @interface Watchable {

    String name();

    String description() default "";

    /**
     * 该值指明是不是只收集阶段数据， 而且被注解的字段只能被赋予java.util.concurrent.atomic.AtomicXXX的Number类型字段。
     * 例如收集每分钟的注册用户数， 就需要将interval设置true。
     *
     * @return 是否收集
     */
    boolean interval() default false;
}
