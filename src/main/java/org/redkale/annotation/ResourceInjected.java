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
 * &#64;Resource资源被依赖注入时的监听事件。<br>
 * 本注解只能标记在空参数或者(String、Object、java.lang.reflect.Field)三个参数类型的任意组合方法上 <br>
 * 方法在资源被依赖注入后调用。
 *
 * <blockquote>
 *
 * <pre>
 * public class ResourceService implements Service {
 *
 *    &#64;Resource(name = "res.id")
 *    private int id;
 *
 *    &#64;Resource(name = "res.name")
 *    private String name;
 *
 *    &#64;ResourceInjected
 *    private void onInjected(Object src, String fieldName) {
 *       System.out .println("资源被注入到对象(" + src + ")的字段(" + fieldName + ")上");
 *  }
 * }
 *
 * public class RecordService implements Service {
 *
 *    &#64;Resource
 *    private ResourceService resService;
 *
 *    public void test() {
 *  }
 *
 *  public static void main(String[] args) throws Exception {
 *      ResourceFactory factory = ResourceFactory.create();
 *      factory.register("res.id", "2345");
 *      factory.register("res.name", "my old name");
 *      ResourceService res = new ResourceService();
 *      factory.inject(res);
 *      factory.register("", res);
 *      RecordService serice = new RecordService();
 *      factory.inject(record);
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
public @interface ResourceInjected {}
