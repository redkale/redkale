/*
 *
 */
package org.redkale.test.util;

import java.util.*;
import org.junit.jupiter.api.Test;
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
    }

    @Test
    public void run1() throws Exception {
        TestBean bean = new TestBean();
        bean.setId(222);
        bean.time = 55555L;
        bean.setName("haha");
        bean.setMap(Utility.ofMap("aa", "bbb"));
        System.out.println(Reproduce.copy(Map.class, bean));
    }

    @Test
    public void run2() throws Exception {
        TestBean bean = new TestBean();
        bean.setId(222);
        bean.time = 55555L;
        bean.setName("haha");
        bean.setMap(Utility.ofMap("aa", "bbb"));
        System.out.println(Reproduce.load(Map.class, TestInterface.class).apply(new HashMap(), bean));
    }
}
