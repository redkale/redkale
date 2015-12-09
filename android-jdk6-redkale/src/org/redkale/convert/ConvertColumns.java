/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

/**
 * ConvertColumn 的多用类
 *
 * @author zhangjx
 */
@Inherited
@Documented
@Target({METHOD, FIELD})
@Retention(RUNTIME)
public @interface ConvertColumns {

    ConvertColumn[] value();
}
