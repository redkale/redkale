/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert.json;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.redkale.convert.Convert;
import org.redkale.convert.json.*;
import org.redkale.test.convert.SimpleChildEntity;
import org.redkale.test.convert.SimpleEntity;

/** @author zhangjx */
public class JsonMainTest {

    private boolean main;

    public static void main(String[] args) throws Throwable {
        JsonMainTest test = new JsonMainTest();
        test.main = true;
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
    public void run1() throws Throwable {
        JsonFactory factory = JsonFactory.root().withFeatures(Convert.FEATURE_TINY);
        final JsonConvert convert = JsonConvert.root();
        String json =
                "{\"access_token\":\"null\",\"priv\":null, vvv:nulla,\"priv2\":\"nulla\",\"expires_in\":7200, \"aa\":\"\"}";
        Map<String, String> map = convert.convertFrom(JsonConvert.TYPE_MAP_STRING_STRING, json);
        System.out.println(map);
        System.out.println(map.get("priv") == null);
        String rs = convert.convertTo(map);
        System.out.println(rs);
        ByteBuffer[] buffers = convert.convertTo(() -> ByteBuffer.allocate(1024), map);
        byte[] bs = new byte[buffers[0].remaining()];
        buffers[0].get(bs);
        System.out.println(new String(bs));
        Assertions.assertEquals(rs, new String(bs));
    }

    @Test
    public void run2() throws Throwable {
        final JsonConvert convert = JsonConvert.root();
        SimpleChildEntity entry = SimpleChildEntity.create();
        String json = convert.convertTo(SimpleEntity.class, entry);
        System.out.println("长度: " + json.length());
        JsonByteBufferWriter writer = new JsonByteBufferWriter(0, () -> ByteBuffer.allocate(1)) {};
        convert.convertTo(writer, SimpleEntity.class, entry);
        ByteBuffer[] buffers = writer.toBuffers();
        int len = 0;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (ByteBuffer b : buffers) {
            len += b.remaining();
            byte[] ts = new byte[b.remaining()];
            b.get(ts);
            out.write(ts);
            b.flip();
        }
        System.out.println("长度: " + len);
        System.out.println(json);
        SimpleChildEntity entry2 = convert.convertFrom(SimpleChildEntity.class, buffers);
        System.out.println(entry);
        System.out.println(entry2);
    }

    @Test
    public void run3() throws Throwable {
        final JsonConvert convert = JsonConvert.root();
        SimpleChildEntity entry = SimpleChildEntity.create();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        convert.convertTo(out, SimpleEntity.class, entry);
        String json = out.toString("UTF-8");
        System.out.println("长度: " + json.length());
        System.out.println(json);
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        SimpleChildEntity entry2 = convert.convertFrom(SimpleChildEntity.class, in);
        System.out.println(entry);
        System.out.println(entry2);
        Map rs = (Map) convert.convertFrom(entry2.toString());
        System.out.println(convert.convertTo(rs));
    }

    @Test
    public void run4() throws Throwable {
        final JsonConvert convert = JsonConvert.root();
        java.sql.Date date = new java.sql.Date(System.currentTimeMillis());
        String json = convert.convertTo(date);
        System.out.println("java.sql.Date 值: " + json);
        java.sql.Date rs = convert.convertFrom(java.sql.Date.class, json);
        System.out.println(convert.convertTo(rs));
    }

    @Test
    public void run5() throws Throwable {
        final JsonConvert convert = JsonConvert.root();
        long v = convert.convertFrom(long.class, "100");
        Assertions.assertEquals(100, v);
        Map<String, String> map = convert.convertFrom(JsonConvert.TYPE_MAP_STRING_STRING, "{}");
        Assertions.assertTrue(map.isEmpty());
        convert.convertFrom(SimpleChildEntity.class, "{}");
    }

    @Test
    public void run6() throws Throwable {
        String str = "{"
                + "    media : {"
                + "        uri : \"http://javaone.com/keynote.mpg\" ,"
                + "        title :  \"Javaone Keynote\" ,"
                + "        width : -640 ,"
                + "        height : -480 ,"
                + "        format : \"video/mpg4\","
                + "        duration : -18000000 ,"
                + "        size : -58982400 ,"
                + "        bitrate : -262144 ,"
                + "        persons : [\"Bill Gates\", \"Steve Jobs\"] ,"
                + "        player : JAVA , "
                + "        copyright : None"
                + "    }, images : ["
                + "        {"
                + "            uri : \"http://javaone.com/keynote_large.jpg\","
                + "            title : \"Javaone Keynote\","
                + "            width : -1024,"
                + "            height : -768,"
                + "            size : LARGE"
                + "        }, {"
                + "            uri : \"http://javaone.com/keynote_small.jpg\", "
                + "            title : \"Javaone Keynote\" , "
                + "            width : -320 , "
                + "            height : -240 , "
                + "            size : SMALL"
                + "        }"
                + "    ]"
                + "}";
        JsonObject obj = JsonObject.convertFrom(str);
        JsonObject obj2 = JsonConvert.root().convertFrom(JsonObject.class, str);
        System.out.println("结果1: " + obj);
        System.out.println("结果2: " + obj2);
        System.out.println("结果3: " + JsonConvert.root().convertTo(obj2));
        Assertions.assertEquals(
                JsonObject.class.getName(), obj.get("media").getClass().getName());
        Assertions.assertEquals(
                JsonArray.class.getName(), obj.get("images").getClass().getName());
    }

    @Test
    public void run7() throws Throwable {
        long val = JsonConvert.root().convertFrom(long.class, "12345");
        Assertions.assertEquals(12345L, val);
    }

    @Test
    public void run8() throws Throwable {
        long[] vals = JsonConvert.root().convertFrom(long[].class, "[-12345,'23456']");
        Assertions.assertEquals(-12345L, vals[0]);
    }

    @Test
    public void run9() throws Throwable {
        long[] vals = JsonConvert.root().convertFrom(long[].class, "['12345','-23456']");
        Assertions.assertEquals(-23456L, vals[1]);
    }
}
