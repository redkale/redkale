/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.util;

import org.redkale.util.Invoker;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 *
 * @author zhangjx
 */
public class InvokerTest {

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
}
