/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 用于定制Source操作JSON字段的转换策略。 <br>
 * 只能依附在Entity类的静态无参数方法上, 且返回值必须是JsonConvert。  <br>
 * 注意: 如果一个类有两个静态方法标记为&#64;SourceConvert， 框架只会识别第一个。
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
public @interface SourceConvert {

}
