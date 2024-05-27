/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.util;

import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redkale.util.MultiHashKey;
import org.redkale.util.Utility;

/** @author zhangjx */
public class MultiHashKeyTest {

    public static void main(String[] args) throws Throwable {
        MultiHashKeyTest test = new MultiHashKeyTest();
        test.run1();
        test.run2();
        test.run3();
        test.run4();
        test.run5();
        test.run6();
        test.run7();
        test.run8();
        test.run9();
    }

    @Test
    public void run1() throws Exception {
        String[] paramNames = {"name", "id"};
        String key = "#{name}";
        MultiHashKey rs = MultiHashKey.create(paramNames, key);
        System.out.println(rs);
        Assertions.assertEquals("ParamKey{field: name, index: 0}", rs.toString());
        Assertions.assertEquals("haha", rs.keyFor("haha", 123));
    }

    @Test
    public void run2() throws Exception {
        String[] paramNames = {"name", "id"};
        String key = "#{id}";
        MultiHashKey rs = MultiHashKey.create(paramNames, key);
        System.out.println(rs);
        Assertions.assertEquals("ParamKey{field: id, index: 1}", rs.toString());
        Assertions.assertEquals("123", rs.keyFor("haha", 123));
    }

    @Test
    public void run3() throws Exception {
        String[] paramNames = {"name", "id"};
        String key = "name";
        MultiHashKey rs = MultiHashKey.create(paramNames, key);
        System.out.println(rs);
        Assertions.assertEquals("StringKey{key: name}", rs.toString());
        Assertions.assertEquals("name", rs.keyFor("haha", 123));
    }

    @Test
    public void run4() throws Exception {
        String[] paramNames = {"name", "id"};
        String key = "key_#{name}_#{id}_#{name.index}";
        MultiHashKey rs = MultiHashKey.create(paramNames, key);
        System.out.println(rs);
        Assertions.assertEquals(
                "ArrayKey[StringKey{key: key_}, ParamKey{field: name, index: 0}, StringKey{key: _}, "
                        + "ParamKey{field: id, index: 1}, StringKey{key: _}, "
                        + "ParamsKey{field: name.index, index: 0}]",
                rs.toString());
        Assertions.assertEquals("key_n124_123_124", rs.keyFor(new Name(124), 123));
    }

    @Test
    public void run5() throws Exception {
        String[] paramNames = {"map", "id"};
        String key = "key_#{map.name}_#{id}_#{map.index}";
        MultiHashKey rs = MultiHashKey.create(paramNames, key);
        System.out.println(rs);
        Assertions.assertEquals(
                "ArrayKey[StringKey{key: key_}, "
                        + "ParamsKey{field: map.name, index: 0}, StringKey{key: _}, "
                        + "ParamKey{field: id, index: 1}, StringKey{key: _}, "
                        + "ParamsKey{field: map.index, index: 0}]",
                rs.toString());
        Map<String, Object> map = Utility.ofMap("name", "me", "index", 123);
        Assertions.assertEquals("key_me_123_123", rs.keyFor(map, 123));
    }

    @Test
    public void run6() throws Exception {
        String[] paramNames = {"map", "id"};
        String key = "{key_#{map.name}_#{id}_#{map.index}}";
        MultiHashKey rs = MultiHashKey.create(paramNames, key);
        Map<String, Object> map = Utility.ofMap("name", "me", "index", 123);
        Assertions.assertEquals("{key_me_123_123}", rs.keyFor(map, 123));
    }

    @Test
    public void run7() throws Exception {}

    @Test
    public void run8() throws Exception {}

    @Test
    public void run9() throws Exception {}

    public static class Name {

        private int index;

        public Name(int index) {
            this.index = index;
        }

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public String toString() {
            return "n" + index;
        }
    }
}
