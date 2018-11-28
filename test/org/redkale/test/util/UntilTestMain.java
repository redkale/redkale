/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.util;

import org.redkale.util.Reproduce;
import org.redkale.util.Attribute;

/**
 *
 * @author zhangjx
 */
public class UntilTestMain {

    public static void main(String[] args) throws Throwable {
        reproduce(args);
        attribute(args);
    }

    public static void reproduce(String[] args) throws Throwable {
        final TestBean bean = new TestBean();
        bean.setId(123456);
        bean.setName("zhangjx");
        bean.time = 2000;
        final TestXBean beanx = new TestXBean();
        Reproduce<TestXBean, TestBean> action1 = Reproduce.create(TestXBean.class, TestBean.class);
        Reproduce<TestXBean, TestBean> action2 = new Reproduce<TestXBean, TestBean>() {

            @Override
            public TestXBean apply(TestXBean dest, TestBean src) {
                dest.time = src.time;
                dest.setId(src.getId());
                dest.setName(src.getName());
                dest.setMap(src.getMap());
                return dest;
            }
        };
        final int count = 1_000_000;
        long s = System.nanoTime();
        for (int i = 0; i < count; i++) {
            action2.apply(beanx, bean);
        }
        long e = System.nanoTime() - s;
        System.out.println("静态Reproduce耗时: " + e);
        s = System.nanoTime();
        for (int i = 0; i < count; i++) {
            action1.apply(beanx, bean);
        }
        e = System.nanoTime() - s;
        System.out.println("动态Reproduce耗时: " + e);
        System.out.println();
    }

    public static void attribute(String[] args) throws Throwable {
        final TestBean bean = new TestBean();
        bean.setId(123456);
        bean.setName("zhangjx");
        Attribute<TestBean, String> action1 = Attribute.create(TestBean.class.getDeclaredField("name"));
        Attribute<TestBean, String> action2 = new Attribute<TestBean, String>() {

            @Override
            public String field() {
                return "name";
            }

            @Override
            public String get(TestBean obj) {
                return obj.getName();
            }

            @Override
            public void set(TestBean obj, String value) {
                obj.setName(value);
            }

            @Override
            public Class type() {
                return String.class;
            }

            @Override
            public Class declaringClass() {
                return TestBean.class;
            }
        };
        final int count = 1_000_000;
        long s = System.nanoTime();
        for (int i = 0; i < count; i++) {
            action2.set(bean, "zhangjx2");
        }
        long e = System.nanoTime() - s;
        System.out.println("静态Attribute耗时: " + e);
        s = System.nanoTime();
        for (int i = 0; i < count; i++) {
            action1.set(bean, "zhangjx2");
        }
        e = System.nanoTime() - s;
        System.out.println("动态Attribute耗时: " + e);
        System.out.println();
        System.out.println("TestBean.map: " + Attribute.create(TestBean.class.getDeclaredField("map")).genericType());
    }
}
