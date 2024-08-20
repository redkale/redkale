/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.util;

import java.util.Properties;
import java.util.TreeMap;
import org.junit.jupiter.api.*;
import org.redkale.util.AnyValue;
import org.redkale.util.AnyValueWriter;

/** @author zhangjx */
public class AnyValuePropertiesTest {

    public static void main(String[] args) throws Throwable {
        AnyValuePropertiesTest test = new AnyValuePropertiesTest();
        test.run1();
        test.run2();
        test.run3();
        test.run4();
        test.run5();
    }

    @Test
    public void run1() {
        Properties properties = new Properties();
        properties.put("redkale.aaa.ooo", "value o");
        properties.put("redkale.aaa.ppp", "value p");
        properties.put("redkale.bbb.qqq.rrr", "value r");
        properties.put("redkale.bbb.sss", "value s");
        properties.put("redkale.source[my].sss", "my s");
        properties.put("redkale.source[my].ttt", "my t");
        properties.put("redkale.source[you].sss", "you s");
        properties.put("redkale.source[you].ttt", "you t");
        properties.put("redkale.ddd[2].ww", "ww 2");
        properties.put("redkale.ddd[2].nn", "nn 2");
        properties.put("redkale.ddd[0].ww", "ww 0");
        properties.put("redkale.ddd[0].nn", "nn 0");
        properties.put("redkale.ddd[10].ww", "ww 10");
        properties.put("redkale.ddd[10].nn", "nn 10");
        properties.put("redkale.mmm.node[5]", "n5");
        properties.put("redkale.mmm.node[0]", "n0");
        properties.put("redkale.mmm.node[20]", "n20");

        String result = "{\r\n"
                + "    'redkale': {\r\n"
                + "        'source': {\r\n"
                + "            'my': {\r\n"
                + "                'sss': 'my s',\r\n"
                + "                'ttt': 'my t'\r\n"
                + "            },\r\n"
                + "            'you': {\r\n"
                + "                'ttt': 'you t',\r\n"
                + "                'sss': 'you s'\r\n"
                + "            }\r\n"
                + "        },\r\n"
                + "        'ddd': {\r\n"
                + "            '$index': 0,\r\n"
                + "            'ww': 'ww 0',\r\n"
                + "            'nn': 'nn 0'\r\n"
                + "        },\r\n"
                + "        'ddd': {\r\n"
                + "            '$index': 2,\r\n"
                + "            'ww': 'ww 2',\r\n"
                + "            'nn': 'nn 2'\r\n"
                + "        },\r\n"
                + "        'ddd': {\r\n"
                + "            '$index': 10,\r\n"
                + "            'ww': 'ww 10',\r\n"
                + "            'nn': 'nn 10'\r\n"
                + "        },\r\n"
                + "        'mmm': {\r\n"
                + "            'node': 'n0',\r\n"
                + "            'node': 'n5',\r\n"
                + "            'node': 'n20'\r\n"
                + "        },\r\n"
                + "        'bbb': {\r\n"
                + "            'sss': 'value s',\r\n"
                + "            'qqq': {\r\n"
                + "                'rrr': 'value r'\r\n"
                + "            }\r\n"
                + "        },\r\n"
                + "        'aaa': {\r\n"
                + "            'ppp': 'value p',\r\n"
                + "            'ooo': 'value o'\r\n"
                + "        }\r\n"
                + "    }\r\n"
                + "}";
        Assertions.assertEquals(result, AnyValue.loadFromProperties(properties).toString());
        System.out.println("------------------------ 01 ------------------------");
    }

    @Test
    public void run2() {
        Properties prop = new Properties();
        prop.put("redkale.name", "myname");
        prop.put("redkale.node[3].id", "333");
        prop.put("redkale.node[3].desc", "haha3");
        prop.put("redkale.node[1].id", "111");
        prop.put("redkale.node[1].desc", "haha1");
        prop.put("redkale.node[2].id", "222");
        prop.put("redkale.node[2].desc", "haha2");

        AnyValue conf = AnyValue.loadFromProperties(prop);
        // System.out.println(conf);

        Properties prop2 = new Properties();
        prop2.put("redkale.name", "myname too");
        prop2.put("redkale.node[3].id", "999");
        prop2.put("redkale.node[3].desc", "haha9");
        prop2.put("redkale.node[4].id", "444");
        prop2.put("redkale.node[4].desc", "haha4");
        AnyValue conf2 = AnyValue.loadFromProperties(prop2);
        // System.out.println(conf2);

        // System.out.println(conf.copy().merge(conf2));
        // System.out.println(conf);
        System.out.println("------------------------ 02 ------------------------");
    }

    @Test
    public void run3() {
        AnyValueWriter conf = AnyValue.create();
        conf.addValue("name", "haha");
        conf.addValue(
                "value",
                AnyValue.create()
                        .addValue("id", 1234)
                        .addValue("key", (String) null)
                        .addValue("desc", "nothing !!!"));
        String json = "{\"name\":\"haha\",\"value\":{\"id\":\"1234\",\"key\":null,\"desc\":\"nothing !!!\"}}";
        Assertions.assertEquals(json, conf.toJsonString());
        System.out.println("------------------------ 03 ------------------------");
    }

    @Test
    public void run4() {
        Properties prop = new Properties();
        prop.put("redkale.datasource.url", "jdbc:mysql://127.0.0.1");
        prop.put("redkale.datasource.user", "user1");
        prop.put("redkale.datasource.password", "123");
        prop.put("redkale.datasource.platf.url", "jdbc:mysql://127.0.0.12");
        prop.put("redkale.datasource.platf.user", "user2");
        prop.put("redkale.datasource.platf.password", "345");

        AnyValue conf = AnyValue.loadFromProperties(prop);
        System.out.println(conf);
        System.out.println("------------------------ 04 ------------------------");
    }

    @Test
    public void run5() {
        AnyValueWriter conf = AnyValue.create();
        conf.addValue("ns", "haha");
        conf.addValue("ns", "hehe");
        conf.addValue("status", 45);
        conf.addValue("name", AnyValueWriter.create("id", 123).addValue("desc", "test"));
        conf.addValue("nodes", AnyValueWriter.create("time", 123).addValue("time", 456));
        conf.addValue("area", AnyValueWriter.create("areaid", 123).addValue("name", "aaa"));
        conf.addValue("area", AnyValueWriter.create("areaid", 456).addValue("name", "bbb"));

        Properties props1 = conf.toProperties();
        System.out.println(conf);
        System.out.println(conf.toJsonObject());
        System.out.println("----------------------------------------------------");
        new TreeMap(props1).forEach((k, v) -> System.out.println(k + " = " + v));
        Properties props2 = new Properties();
        props2.put("ns[0]", "haha");
        props2.put("ns[1]", "hehe");
        props2.put("status", "45");
        props2.put("name.desc", "test");
        props2.put("name.id", "123");
        props2.put("nodes.time[0]", "123");
        props2.put("nodes.time[1]", "456");
        props2.put("area[0].areaid", "123");
        props2.put("area[0].name", "aaa");
        props2.put("area[1].areaid", "456");
        props2.put("area[1].name", "bbb");

        Assertions.assertEquals(props1.toString(), props2.toString());
        System.out.println("------------------------ 05 ------------------------");
    }
}
