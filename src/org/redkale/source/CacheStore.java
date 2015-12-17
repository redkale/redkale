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
 * 用于标记CacheSource 是否需要持久化到文件中
 * 注意： 标记为@CacheStore 的 CacheSource对的name()不能包含特殊字符， 否则无法创建存储文件。
 *
 * @see http://www.redkale.org
 * @author zhangjx
 */
@Target({FIELD})
@Retention(RUNTIME)
public @interface CacheStore {

    Class keyType(); //key对应的class

    Class valueType();  //value 对应的class
}
