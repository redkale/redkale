/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 *
 * <p>
 * 详情见: http://redkale.org
 *
 * @author zhangjx
 */
@Target({TYPE})
@Retention(RUNTIME)
public @interface DistributeTable {

    Class<? extends DistributeTableStrategy> strategy();
}
