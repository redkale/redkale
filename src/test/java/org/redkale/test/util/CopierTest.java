/*
 *
 */
package org.redkale.test.util;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.*;
import org.redkale.convert.json.JsonConvert;
import org.redkale.util.*;

/**
 *
 * @author zhangjx
 */
public class CopierTest {

    public static void main(String[] args) throws Throwable {
        CopierTest test = new CopierTest();
        test.run1();
        test.run2();
        test.run3();
        test.run4();
        test.run5();
        test.run6();
        test.run7();
        test.run8();
        test.run9();
        test.run10();
        test.run11();
        test.run12();
    }

    @Test
    public void run1() throws Exception {
        TestBean bean = new TestBean();
        bean.setId(222);
        bean.time = 55555L;
        bean.setName("haha");
        bean.setMap(Utility.ofMap("aa", "bbb"));
        Map map = new TreeMap(Copier.copy(bean, Map.class));
        System.out.println(JsonConvert.root().convertTo(map));
        TreeMap rs = Copier.copy(bean, TreeMap.class);
        rs.remove("remark");
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
        Copier.load(TestInterface.class, Map.class).apply(bean, rs);
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
        TestInterface ti = Copier.load(Map.class, TestInterface.class).apply(map, new TestBean());;
        Assertions.assertEquals("{\"id\":222,\"map\":{\"aa\":\"bbb\"},\"time\":0}", JsonConvert.root().convertTo(ti));
    }

    @Test
    public void run4() throws Exception {
        TestBean bean = new TestBean();
        Map map = new TreeMap();
        map.put("name", "haha");
        map.put("time", "55555");
        map.put("id", "222");
        map.put("map", Utility.ofMap("aa", "bbb"));
        Copier.load(Map.class, TestBean.class).apply(map, bean);
        System.out.println(JsonConvert.root().convertTo(bean));
        map.put("time", 55555L);
        map.put("id", 222);
        Assertions.assertEquals(bean.toString(), JsonConvert.root().convertTo(map));
    }

    @Test
    public void run5() throws Exception {
        Map map = new TreeMap();
        map.put("name", "haha");
        map.put("time", "55555");
        map.put("id", "222");
        map.put("map", Utility.ofMap("aa", "bbb"));
        Map rs = new TreeMap();
        Copier.load(Map.class, Map.class).apply(map, rs);
        System.out.println("Map: " + JsonConvert.root().convertTo(rs));
        Assertions.assertEquals(JsonConvert.root().convertTo(map), JsonConvert.root().convertTo(rs));
    }

    @Test
    public void run6() throws Exception {
        TestBean bean = new TestBean();
        bean.setId(222);
        bean.time = 55555L;
        bean.setName(null);
        bean.setMap(Utility.ofMap("aa", "bbb"));
        ConcurrentHashMap rs = Copier.copy(bean, ConcurrentHashMap.class);
        System.out.println(JsonConvert.root().convertTo(rs));
        System.out.println("------------------------------------------");
    }

    @Test
    public void run7() throws Exception {
        TestBean bean = new TestBean();
        Map map = new TreeMap();
        map.put("name", "haha");
        map.put("time", "55555");
        map.put("id", null);
        map.put("map", Utility.ofMap("aa", "bbb"));
        Copier.load(Map.class, TestBean.class).apply(map, bean);
        System.out.println(JsonConvert.root().convertTo(bean));
        System.out.println("------------------------------------------");
    }

    @Test
    public void run8() throws Exception {
        TestBean bean = new TestBean();
        Map map = new TreeMap();
        map.put("name", "");
        map.put("time", "55555");
        map.put("id", null);
        map.put("map", Utility.ofMap("aa", "bbb"));
        Copier.load(Map.class, TestBean.class, Copier.OPTION_SKIP_RMPTY_STRING).apply(map, bean);
        System.out.println(JsonConvert.root().convertTo(bean));
        Assertions.assertTrue(bean.getName() == null);
    }

    @Test
    public void run9() throws Exception {
        TestBean bean = new TestBean();
        bean.remark = "hehehoho";
        Map map = new TreeMap();
        map.put("name", "");
        map.put("time", "55555");
        map.put("id", null);
        map.put("remark", null);
        map.put("map", Utility.ofMap("aa", "bbb"));
        Copier.load(Map.class, TestBean.class).apply(map, bean);
        System.out.println(JsonConvert.root().convertTo(bean));
        Assertions.assertTrue(bean.remark == null);

        bean.remark = "hehehoho";
        Copier.load(Map.class, TestBean.class, Copier.OPTION_SKIP_NULL_VALUE).apply(map, bean);
        System.out.println(JsonConvert.root().convertTo(bean));
        Assertions.assertTrue(bean.remark != null);
    }

    @Test
    public void run10() throws Exception {
        TestBean bean = new TestBean();
        bean.remark = "hehehoho";
        TestXBean srcBean = new TestXBean();
        srcBean.setName("");
        srcBean.time = 55555;
        srcBean.remark = null;
        srcBean.setMap(Utility.ofMap("aa", "bbb"));
        Copier.load(TestXBean.class, TestBean.class).apply(srcBean, bean);
        System.out.println(JsonConvert.root().convertTo(bean));
        Assertions.assertTrue(bean.remark == null);

        bean.remark = "hehehoho";
        Copier.load(TestXBean.class, TestBean.class, Copier.OPTION_SKIP_NULL_VALUE).apply(srcBean, bean);
        System.out.println(JsonConvert.root().convertTo(bean));
        Assertions.assertTrue(bean.remark != null);
    }

    @Test
    public void run11() throws Exception {
        TestBean bean = new TestBean();
        TestXBean srcBean = new TestXBean();
        srcBean.setName("");
        srcBean.time = 55555;
        srcBean.remark = null;
        srcBean.setMap(Utility.ofMap("aa", "bbb"));
        Copier.load(TestXBean.class, TestBean.class, Copier.OPTION_SKIP_RMPTY_STRING).apply(srcBean, bean);
        System.out.println(JsonConvert.root().convertTo(bean));
        Assertions.assertTrue(bean.getName() == null);
    }

    @Test
    public void run12() throws Exception {
        TestBean bean = new TestBean();
        bean.remark = "hehehoho";
        TestXBean srcBean = new TestXBean();
        srcBean.setName("");
        srcBean.time = 55555;
        srcBean.remark = null;
        srcBean.setMap(Utility.ofMap("aa", "bbb"));
        Copier.load(TestXBean.class, TestBean.class).apply(srcBean, bean);
        System.out.println(JsonConvert.root().convertTo(bean));
        Assertions.assertTrue(bean.remark == null);

        bean.setName(null);
        bean.remark = "hehehoho";
        Copier.load(TestXBean.class, TestBean.class, Copier.OPTION_SKIP_NULL_VALUE | Copier.OPTION_SKIP_RMPTY_STRING).apply(srcBean, bean);
        System.out.println(JsonConvert.root().convertTo(bean));
        Assertions.assertTrue(bean.getName() == null);
    }
}
