/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import org.redkale.convert.*;

/**
 * 指定class某个字段的自定义序列化和反序列化策略。  <br>
 * 只能依附在Service实现类的public方法上, 当方法的返回值以JSON输出时对指定类型的转换设定。  <br>
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@Inherited
@Documented
@Target({METHOD})
@Retention(RUNTIME)
@Repeatable(RestConvertCoder.RestConvertCoders.class)
public @interface RestConvertCoder {

    Class type();

    String field();

    Class<? extends SimpledCoder> coder();

    @Inherited
    @Documented
    @Target({METHOD})
    @Retention(RUNTIME)
    @interface RestConvertCoders {

        RestConvertCoder[] value();
    }
}
