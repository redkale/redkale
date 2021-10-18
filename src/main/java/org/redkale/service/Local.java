/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.service;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 本地模式注解。<br>
 * 声明为Local的Service只能以本地模式存在， 即使配置文件中配置成远程模式也将被忽略。 <br>
 * Service里被标记为Local的public方法不会被重载。
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@Inherited
@Documented
@Target({TYPE, METHOD, PARAMETER})
@Retention(RUNTIME)
public @interface Local {

    /**
     * 标记全局唯一性
     * <p>
     * 有些Service可能只能启动一个实例， 比如凌晨定时清除一些数据的Service, 在整个系统部署中应该只被部署一次
     *
     * @since 2.1.0
     * @return boolean
     */
    //boolean unique() default false;

    String comment() default ""; //备注描述
}
