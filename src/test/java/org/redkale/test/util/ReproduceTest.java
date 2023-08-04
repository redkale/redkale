/*
 *
 */
package org.redkale.test.util;

import java.util.*;
import org.junit.jupiter.api.*;
import org.redkale.convert.json.JsonConvert;
import org.redkale.util.*;

/**
 *
 * @author zhangjx
 */
public class ReproduceTest {

    public static void main(String[] args) throws Throwable {
        ReproduceTest test = new ReproduceTest();
        test.run1();
        test.run2();
        test.run3();
        test.run4();
    }

    @Test
    public void run1() throws Exception {
        TestBean bean = new TestBean();
        bean.setId(222);
        bean.time = 55555L;
        bean.setName("haha");
        bean.setMap(Utility.ofMap("aa", "bbb"));
        Map map = new TreeMap(Reproduce.copy(Map.class, bean));
        System.out.println(JsonConvert.root().convertTo(map));
        TreeMap rs = Reproduce.copy(TreeMap.class, bean);
        Assertions.assertEquals(bean.toString(), JsonConvert.root().convertTo(rs));
    }

    @Test
    public void run2() throws Exception {
        TestBean bean = new TestBean();
        bean.setId(222);
        bean.time = 55555L;
        bean.setName("haha");
        bean.setMap(Utility.ofMap("aa", "bbb"));
        TreeMap rs = new TreeMap();
        Reproduce.load(Map.class, TestInterface.class).apply(rs, bean);
        System.out.println(JsonConvert.root().convertTo(rs));
    }

    @Test
    public void run3() throws Exception {
        Map map = new LinkedHashMap();
        map.put("name", "haha");
        map.put("time", "55555");
        map.put("id", "222");
        map.put("map", Utility.ofMap("aa", "bbb"));
        TestBean bean = new TestBean();
        Reproduce.load(TestInterface.class, Map.class).apply(bean, map);
        Assertions.assertEquals("{\"id\":222,\"map\":{\"aa\":\"bbb\"},\"time\":0}", JsonConvert.root().convertTo(bean));
    }

    @Test
    public void run4() throws Exception {
        TestBean bean = new TestBean();
        Map map = new TreeMap();
        map.put("name", "haha");
        map.put("time", "55555");
        map.put("id", "222");
        map.put("map", Utility.ofMap("aa", "bbb"));
        Reproduce.load(TestBean.class, Map.class).apply(bean, map);
        System.out.println(JsonConvert.root().convertTo(bean));
        map.put("time", 55555L);
        map.put("id", 222);
        Assertions.assertEquals(bean.toString(), JsonConvert.root().convertTo(map));
    }
}
