/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.*;

/**
 * VirtualEntity表示虚拟的数据实体类， 通常Entity都会映射到数据库中的某个表，而标记为VirtualEntity的Entity类只存在DataCache中
 *
 * <p>
 * 详情见: http://www.redkale.org
 *
 * @author zhangjx
 */
@Documented
@Target(TYPE)
@Retention(RUNTIME)
public @interface VirtualEntity {

}
