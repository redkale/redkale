/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert.json;

import java.util.*;
import org.junit.jupiter.api.*;
import org.redkale.convert.json.*;
import org.redkale.util.AnyValue;
import org.redkale.util.AnyValueWriter;

/** @author zhangjx */
public class DyncJsonTest {

    public static void main(String[] args) throws Throwable {
        DyncJsonTest test = new DyncJsonTest();
        test.run1();
        test.run2();
    }

    @Test
    public void run1() throws Exception {
        SimpleDyncBean bean = new SimpleDyncBean();
        bean.name = "haha";
        System.out.println(JsonConvert.root().convertTo(bean));
        Assertions.assertEquals("{\"name\":\"haha\"}", JsonConvert.root().convertTo(bean));

        SimpleDyncBean2 bean2 = new SimpleDyncBean2();
        bean2.name = "haha";

        System.out.println(JsonConvert.root().convertTo(bean2));
        Assertions.assertEquals("{\"name\":\"haha\"}", JsonConvert.root().convertTo(bean2));
        SimpleDyncBean3 bean3 = new SimpleDyncBean3();
        bean3.name = "haha";
        System.out.println(JsonConvert.root().convertTo(bean3));
        Assertions.assertEquals("{\"name\":\"haha\"}", JsonConvert.root().convertTo(bean3));
    }

    @Test
    public void run2() throws Exception {
        AnyValueWriter writer = AnyValueWriter.create();
        writer.addValue("name", "aaa");
        writer.addValue("name", "bbb");
        writer.addValue("node", AnyValueWriter.create("id", "123"));
        writer.addValue("node", AnyValueWriter.create("id", "456"));
        System.out.println(writer);
        String bs = JsonConvert.root().convertTo(AnyValue.class, writer);
        AnyValue other = JsonConvert.root().convertFrom(AnyValue.class, bs);
        System.out.println(other);
        Assertions.assertEquals(writer.toString(), other.toString());
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
