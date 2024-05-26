/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.*;

/**
 * 用于序列化时接口或抽象类的默认实现类, 被标记的类必须是接口或抽象类 <br>
 * 使用场景: <br>
 *
 * <blockquote>
 *
 * <pre>
 * &#64;ConvertImpl(OneImpl.class)
 * public interface OneEntity {
 *     public String getName();
 * }
 *
 *
 * public class OneImpl implements OneEntity {
 *     private String name;
 *     public String getName(){return name;}
 *     public void setName(String name){this.name=name;}
 * }
 *
 *
 * String json = "{'name':'hello'}";
 * OneEntity one = JsonConvert.root().convertFrom(OneEntity.class, json);
 * //one instanceof OneImpl
 *
 * </pre>
 *
 * </blockquote>
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.5.0
 */
// 一定不能标记Inherited
@Documented
@Target({TYPE})
@Retention(RUNTIME)
public @interface ConvertImpl {

    /**
     * 默认的实现类
     *
     * @return String
     */
    Class value() default Object.class;

    /**
     * 实现类的集合
     *
     * @return Class[]
     */
    Class[] types() default {};
}
