/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 *
 * @author zhangjx
 */
@Inherited
@Documented
@Target({METHOD, TYPE})
@Retention(RUNTIME)
public @interface AuthIgnore {

}
