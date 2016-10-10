/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.*;

/**
 * 用于类名的别名， 类似javax.persistence.Table
 * 该值必须是全局唯一
 * 使用场景: 当BSON序列化为了不指定class可以使用@ConvertEntity来取个别名。关联方法:  Reader.readClassName() 和  Writer.writeClassName(String value) 。
 *
 * <p> 详情见: https://redkale.org
 * @author zhangjx
 */
@Inherited
@Documented
@Target({TYPE})
@Retention(RUNTIME)
public @interface ConvertEntity {

    String value();
}
