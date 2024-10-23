/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert.json;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.redkale.convert.Convert;
import org.redkale.convert.ConvertStandardString;
import org.redkale.convert.Decodeable;
import org.redkale.convert.json.*;
import org.redkale.util.TypeToken;

/** @author zhangjx */
public class Json5Test {

    public static void main(String[] args) throws Throwable {
        Json5Test test = new Json5Test();
        test.run1();
        test.run2();
        test.run3();
        test.run4();
        test.run5();
    }

    @Test
    public void run1() throws Exception {
        JsonConvert convert = JsonConvert.root();
        Json5Bean bean = new Json5Bean();
        bean.id = 500;
        bean.idx = 600;
        bean.decmails = 3.2f;
        bean.value = 44444;
        bean.name = "ha\t\"ha";
        bean.desc = "normal";
        String json =
                "{/*多行\r\n注释**/\"decmails\":3.2,//单行注释\r\n\"id\":0x1F4,\"idx\":600,name:\"ha\\t\\\"ha\",\"desc\":\"normal\",\"value\":44444,}";
        Json5Bean bean2 = convert.convertFrom(Json5Bean.class, json);
        System.out.println(bean2.name);
        Assertions.assertTrue(bean.equals(bean2));
        System.out.println(convert.convertTo(bean2));
        bean2 = convert.convertFrom(Json5Bean.class, new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        System.out.println(bean2.name);
        Assertions.assertTrue(bean.equals(bean2));
        System.out.println(convert.convertTo(bean2));

        String arrayJson = "[" + json + "," + json + "," + "]";
        Json5Bean[] beans = convert.convertFrom(Json5Bean[].class, arrayJson);
        System.out.println(convert.convertTo(beans));
        beans = convert.convertFrom(
                Json5Bean[].class, new ByteArrayInputStream(arrayJson.getBytes(StandardCharsets.UTF_8)));
        System.out.println(convert.convertTo(beans));

        String intjson = "[1,2,3,4,]";
        int[] ints1 = convert.convertFrom(int[].class, intjson);
        System.out.println(Arrays.toString(ints1));
    }

    @Test
    public void run2() throws Exception {
        JsonFactory factory = JsonFactory.root().withFeatures(Convert.FEATURE_TINY | Convert.FEATURE_NULLABLE);
        final JsonConvert convert = factory.getConvert();
        Json5Bean bean = new Json5Bean();
        bean.id = 60;
        System.out.println(convert.convertTo(bean));
    }

    @Test
    public void run3() throws Exception {
        Decodeable<JsonReader, String> decoder = JsonFactory.root().loadDecoder(String.class);
        String val = decoder.convertFrom(new JsonReader("null"));
        Assertions.assertTrue(val == null);
        val = decoder.convertFrom(new JsonReader("nullable"));
        Assertions.assertEquals("nullable", val);

        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("name1", null);
        map.put("name2", "nullable");
        map.put("site", "002");
        String json = JsonConvert.root().convertTo(map);
        System.out.println(json);
        Map<String, String> rs = JsonConvert.root().convertFrom(MAP_TYPE, json.replace("\"nullable\"", "nullable"));
        String json2 = JsonConvert.root().convertTo(rs);
        System.out.println(json2);
        Assertions.assertEquals(json, json2);
    }

    @Test
    public void run4() throws Exception {
        int val = JsonConvert.root().convertFrom(int.class, "NaN");
        Assertions.assertEquals(0, val);
    }

    @Test
    public void run5() throws Exception {
        long val = JsonConvert.root().convertFrom(long.class, "Infinity");
        Assertions.assertEquals(Long.MAX_VALUE, val);
        val = JsonConvert.root().convertFrom(long.class, "-Infinity");
        Assertions.assertEquals(Long.MIN_VALUE, val);
    }

    private static Type MAP_TYPE = new TypeToken<LinkedHashMap<String, String>>() {}.getType();

    public static class Json5Bean {

        public int id;

        public long idx;

        public float decmails;

        public long value;

        public String name;

        @ConvertStandardString
        public String desc;

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 47 * hash + this.id;
            hash = 47 * hash + (int) this.idx;
            hash = 47 * hash + Float.floatToIntBits(this.decmails);
            hash = 47 * hash + (int) (this.value ^ (this.value >>> 32));
            hash = 47 * hash + Objects.hashCode(this.name);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Json5Bean other = (Json5Bean) obj;
            if (this.id != other.id) {
                return false;
            }
            if (this.idx != other.idx) {
                return false;
            }
            if (Float.floatToIntBits(this.decmails) != Float.floatToIntBits(other.decmails)) {
                return false;
            }
            if (this.value != other.value) {
                return false;
            }
            if (!Objects.equals(this.name, other.name)) {
                return false;
            }
            return true;
        }
    }
}
