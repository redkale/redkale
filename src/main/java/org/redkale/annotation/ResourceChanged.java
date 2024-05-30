/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.annotation;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.*;

/**
 * &#64;Resource资源被更新时的监听事件, 本注解只能标记在方法参数为ResourceEvent[]上 <br>
 * 注意: 一个类只能存在一个&#64;ResourceChanged的方法， 多余的会被忽略 <br>
 * 方法在资源被更新以后调用。
 *
 * <blockquote>
 *
 * <pre>
 * public class RecordService implements Service {
 *
 *    &#64;Resource(name = "record.id")
 *    private int id;
 *
 *    &#64;Resource(name = "record.name")
 *    private String name;
 *
 *    &#64;ResourceChanged
 *    private void changeResource(ResourceEvent[] events) {
 *      for(ResourceEvent event : events) {
 *          System.out .println("@Resource = " + event.name() + " 资源变更:  newVal = " + event.newValue() + ", oldVal = " + event.oldValue());
 *      }
 *  }
 *
 *  public static void main(String[] args) throws Exception {
 *      ResourceFactory factory = ResourceFactory.create();
 *      factory.register("record.id", "2345");
 *      factory.register("record.name", "my old name");
 *      Record record = new Record();
 *      factory.inject(record);
 *      factory.register("record.name", "my new name");
 *  }
 * }
 * </pre>
 *
 * </blockquote>
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
@Documented
@Target({METHOD})
@Retention(RUNTIME)
public @interface ResourceChanged {

    /**
     * 新旧值是否不同时才回调方法 <br>
     * true: 新值与旧值不同时才回调ResourceChanged方法 false: 只要执行了ResourceFactory.register 就回调ResourceChanged方法
     *
     * @since 2.7.0
     * @return boolean
     */
    boolean different() default true;
}
