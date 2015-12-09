/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 *
 * @author zhangjx
 */
@Documented @Target(CONSTRUCTOR) @Retention(RUNTIME)
public @interface ConstructorProperties {
    /**
       <p>The getter names.</p>
       @return the getter names corresponding to the parameters in the
       annotated constructor.
    */
    String[] value();
}
