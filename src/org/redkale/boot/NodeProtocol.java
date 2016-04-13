/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.boot;

import java.lang.annotation.*;

/**
 * 根据application.xml中的server节点中的protocol值来适配Server的加载逻辑
 *
 * <p>
 * 详情见: http://redkale.org
 *
 * @author zhangjx
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NodeProtocol {

    String[] value();
}
