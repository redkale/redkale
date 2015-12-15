/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 *
 * @see http://www.redkale.org
 * @author zhangjx
 */
@Documented
@Target(TYPE)
@Retention(RUNTIME)
public @interface VirtualEntity {

}
