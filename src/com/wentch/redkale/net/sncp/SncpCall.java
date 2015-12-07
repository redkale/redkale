/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.sncp;

import com.wentch.redkale.util.*;
import java.lang.annotation.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 参数回写
 *
 * @author zhangjx
 */
@Inherited
@Documented
@Target({ElementType.PARAMETER})
@Retention(RUNTIME)
public @interface SncpCall {

    Class<? extends Attribute> value();
}
