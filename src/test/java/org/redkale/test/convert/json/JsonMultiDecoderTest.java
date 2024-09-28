/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert.json;

import java.lang.reflect.Type;
import org.junit.jupiter.api.*;
import org.redkale.convert.json.JsonConvert;

/** @author zhangjx */
public class JsonMultiDecoderTest {

    private boolean main;

    public static void main(String[] args) throws Throwable {
        JsonMultiDecoderTest test = new JsonMultiDecoderTest();
        test.main = true;
        test.run();
    }

    @Test
    public void run() throws Exception {
        JsonConvert convert = JsonConvert.root();
        String json = "[\"aaaa\",{\"name\":\"haha\"}]";
        Type[] types = new Type[] {String.class, JsonConvert.TYPE_MAP_STRING_STRING};
        Object[] objs = convert.convertFrom(types, json);
        System.out.println(convert.convertTo(objs));
        if (!main) Assertions.assertEquals(convert.convertTo(objs), json);
    }
}
