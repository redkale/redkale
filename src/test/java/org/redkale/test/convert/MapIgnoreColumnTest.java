/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert;

import java.util.*;
import org.junit.jupiter.api.*;
import org.redkale.convert.json.*;

/** @author zhangjx */
public class MapIgnoreColumnTest {

    private boolean main;

    public static void main(String[] args) throws Throwable {
        MapIgnoreColumnTest test = new MapIgnoreColumnTest();
        test.main = true;
        test.run();
    }

    @Test
    public void run() throws Exception {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("aaa", "123");
        map.put("bbb", List.of(1, 2));
        System.out.println(JsonConvert.root().convertTo(map));
        JsonFactory factory = JsonFactory.create();
        factory.register(Map.class, true, "aaa");
        JsonConvert convert = factory.getConvert();
        String rs = "{\"bbb\":[1,2]}";
        if (!main) Assertions.assertEquals(rs, convert.convertTo(map));
        System.out.println(convert.convertTo(map));
        JsonConvert convert2 = JsonConvert.root()
                .newConvert(
                        null,
                        (k, v) -> {
                            if ("bbb".equals(k)) return null;
                            return v;
                        },
                        null);
        if (!main) Assertions.assertEquals("{\"aaa\":\"123\",\"bbb\":null}", convert2.convertTo(map));
        System.out.println(convert2.convertTo(map));
    }
}
