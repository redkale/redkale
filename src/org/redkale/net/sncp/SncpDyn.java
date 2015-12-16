/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.sncp;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 修饰由SNCP协议动态生成的class、和method
 * 本地模式：动态生成的_DynLocalXXXService类、其带有@MultiRun方法均会打上@SncpDyn 的注解
 * 远程模式：动态生成的_DynRemoteXXXService类会打上@SncpDyn 的注解
 *
 * @see http://www.redkale.org
 * @author zhangjx
 */
@Inherited
@Documented
@Target({METHOD, TYPE})
@Retention(RUNTIME)
public @interface SncpDyn {

    int index() default 0;  //排列顺序， 一般用于Method
}
