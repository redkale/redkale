/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert;

import java.io.*;
import org.redkale.convert.json.JsonConvert;
import org.redkale.convert.json.JsonFactory;
import java.nio.*;
import java.util.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redkale.convert.json.*;

/**
 *
 * @author zhangjx
 */
public class JsonTestMain {

    @Test
    public void run1() throws Throwable {
        JsonFactory factory = JsonFactory.root().tiny(true);
        final JsonConvert convert = JsonConvert.root();
        String json = "{\"access_token\":\"null\",\"priv\":null, vvv:nulla,\"priv2\":\"nulla\",\"expires_in\":7200, \"aa\":\"\"}";
        Map<String, String> map = convert.convertFrom(JsonConvert.TYPE_MAP_STRING_STRING, json);
        System.out.println(map);
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
        JsonByteBufferWriter writer = new JsonByteBufferWriter(false, () -> ByteBuffer.allocate(1)) {
        };
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
}
