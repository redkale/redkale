/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert.json;

import java.util.*;
import org.junit.jupiter.api.*;
import org.redkale.convert.json.*;

/** @author zhangjx */
public class DyncJsonTest {

    private boolean main;

    public static void main(String[] args) throws Throwable {
        DyncJsonTest test = new DyncJsonTest();
        test.main = true;
        test.run();
    }

    @Test
    public void run() throws Exception {
        SimpleDyncBean bean = new SimpleDyncBean();
        bean.name = "haha";
        System.out.println(JsonConvert.root().convertTo(bean));
        if (!main)
            Assertions.assertEquals("{\"name\":\"haha\"}", JsonConvert.root().convertTo(bean));

        SimpleDyncBean2 bean2 = new SimpleDyncBean2();
        bean2.name = "haha";

        System.out.println(JsonConvert.root().convertTo(bean2));
        if (!main)
            Assertions.assertEquals("{\"name\":\"haha\"}", JsonConvert.root().convertTo(bean2));
        SimpleDyncBean3 bean3 = new SimpleDyncBean3();
        bean3.name = "haha";
        System.out.println(JsonConvert.root().convertTo(bean3));
        if (!main)
            Assertions.assertEquals("{\"name\":\"haha\"}", JsonConvert.root().convertTo(bean3));
    }

    public static class SimpleDyncBean {

        public String name;

        public List<SimpleDyncBean> beans;
    }

    public static class SimpleDyncBean2 {

        public String name;

        public SimpleDyncBean2 bean2;
    }

    public static class SimpleDyncBean3 {

        public String name;

        public Map<String, SimpleDyncBean3> beanmap;
    }
}
