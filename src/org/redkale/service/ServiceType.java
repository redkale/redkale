/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.service;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <blockquote><pre>
 * Service的资源类型
 * 业务逻辑的Service通常有两种编写方式：
 *    1、只写一个Service实现类。
 *    2、先定义业务的Service接口或抽象类，再编写具体实现类。
 * &#64;ServiceType用于第二种方式， 在具体实现类上需要使用&#64;ServiceType指明资源注入的类型。
 * </pre></blockquote>
 * <p>
 * 详情见: http://www.redkale.org
 *
 * @author zhangjx
 */
@Inherited
@Documented
@Target({TYPE})
@Retention(RUNTIME)
public @interface ServiceType {

    Class<? extends Service> value();

}
