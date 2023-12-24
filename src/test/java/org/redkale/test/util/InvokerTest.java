/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.util;

import java.io.IOException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redkale.util.Invoker;

/**
 *
 * @author zhangjx
 */
public class InvokerTest {

    public static void main(String[] args) throws Throwable {
        InvokerTest test = new InvokerTest();
        test.run1();
        test.run2();
        test.run3();
        test.run4();
        test.run5();
        test.run6();
        test.run7();
    }

    @Test
    public void run1() {
        Invoker<String, String> invoker = Invoker.create(String.class, "toLowerCase");
        Assertions.assertEquals("aaa", invoker.invoke("AAA"));
    }

    @Test
    public void run2() {
        Invoker<String, Boolean> invoker = Invoker.create(String.class, "equals", Object.class);
        Assertions.assertTrue(invoker.invoke("AAA", "AAA"));
    }

    @Test
    public void run3() {
        Invoker<java.sql.Date, java.sql.Date> invoker = Invoker.create(java.sql.Date.class, "valueOf", String.class);
        String str = new java.sql.Date(System.currentTimeMillis()).toString();
        System.out.println(str);
        Assertions.assertEquals(str, invoker.invoke(null, str).toString());
    }

    @Test
    public void run4() {
        Invoker<Action, Void> invoker = Invoker.create(Action.class, "test1");
        Action action = new Action();
        invoker.invoke(action);
    }

    @Test
    public void run5() {
        Invoker<Action, Void> invoker = Invoker.create(Action.class, "test2", String.class);
        Action action = new Action();
        invoker.invoke(action, "name");
    }

    @Test
    public void run6() {
        Invoker<Action, Integer> invoker = Invoker.create(Action.class, "test3", String.class, int.class);
        Action action = new Action();
        int rs = invoker.invoke(action, "name", 1);
        System.out.println(rs);
        Assertions.assertEquals(3, rs);
    }

    @Test
    public void run7() {
        Invoker<Action, Integer> invoker = Invoker.create(Action.class, "test4", String.class, int.class);
        Action action = new Action();
        int rs = invoker.invoke(action, "name", 1);
        System.out.println(rs);
        Assertions.assertEquals(4, rs);
    }

    public static class Action {

        public void test1() {
        }

        public void test2(String name) throws IOException {
        }

        public int test3(String name, int id) {
            return 3;
        }

        public int test4(String name, int id) throws IOException {
            return 4;
        }
    }
}
