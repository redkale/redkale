/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert.json;

import org.junit.jupiter.api.*;
import org.redkale.convert.Convert;
import org.redkale.convert.json.*;

/** @author zhangjx */
public class TinyTest {

    public static void main(String[] args) throws Throwable {
        TinyTest test = new TinyTest();
        test.run1();
        test.run2();
        test.run3();
    }

    @Test
    public void run1() throws Exception {
        TinyRecord record = new TinyRecord();
        record.id = 5;
        JsonFactory factory = JsonFactory.create().withFeatures(Convert.FEATURE_TINY);
        JsonConvert convert = factory.getConvert();
        String json = "{\"id\":5}";
        Assertions.assertEquals(json, convert.convertTo(record));
        System.out.println(convert.convertTo(record));
    }

    @Test
    public void run2() throws Exception {
        TinyRecord record = new TinyRecord();
        record.id = 5;
        JsonFactory factory = JsonFactory.create().withFeatures(0);
        JsonConvert convert = factory.getConvert();
        String json = "{\"id\":5,\"name\":\"\"}";
        Assertions.assertEquals(json, convert.convertTo(record));
        System.out.println(convert.convertTo(record));
    }

    @Test
    public void run3() throws Exception {
        String json = "{\"id\":5,\"name\":\"\", \"status\":2}";
        JsonConvert.root().convertFrom(TinyRecord.class, json);
    }

    public static class TinyRecord {

        public String name = "";

        public int id;
    }


}
