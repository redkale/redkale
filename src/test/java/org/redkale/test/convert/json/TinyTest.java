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

    private boolean main;

    public static void main(String[] args) throws Throwable {
        TinyTest test = new TinyTest();
        test.main = true;
        test.run();
    }

    @Test
    public void run() throws Exception {
        TinyRecord record = new TinyRecord();
        record.id = 5;
        {
            JsonFactory factory = JsonFactory.create().withFeatures(Convert.FEATURE_TINY);
            JsonConvert convert = factory.getConvert();
            String json = "{\"id\":5}";
            if (!main) {
                Assertions.assertEquals(json, convert.convertTo(record));
            }
            System.out.println(convert.convertTo(record));
        }
        {
            JsonFactory factory = JsonFactory.create().withFeatures(0);
            JsonConvert convert = factory.getConvert();
            String json = "{\"id\":5,\"name\":\"\"}";
            if (!main) {
                Assertions.assertEquals(json, convert.convertTo(record));
            }
            System.out.println(convert.convertTo(record));
        }
    }

    public static class TinyRecord {

        public String name = "";

        public int id;
    }
}
