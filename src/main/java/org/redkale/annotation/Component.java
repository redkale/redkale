package org.redkale.annotation;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.*;

/**
 * 标记Component的Service类特点: <br>
 * 1、直接构造, 不使用Sncp动态构建对象 <br>
 * 2、不会生成对应协议的Servlet <br>
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
@Inherited
@Documented
@Target({TYPE})
@Retention(RUNTIME)
public @interface Component {}
