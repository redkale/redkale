/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 用于类名的别名, 该值必须是全局唯一 <br>
 * 使用场景: 当自定义序列化为了不指定class可以使用@ConvertEntity来取个别名。 <br>
 * 关联方法: {@link org.redkale.convert.Reader#readClassName()} 和 {@link org.redkale.convert.Writer#writeClassName(java.lang.String) } 。
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
@Inherited
@Documented
@Target({TYPE})
@Retention(RUNTIME)
public @interface ConvertEntity {

    /**
     * 别名值
     *
     * @return String
     */
    String value();
}
