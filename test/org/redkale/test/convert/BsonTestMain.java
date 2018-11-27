/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert;

import java.io.*;
import org.redkale.convert.bson.BsonByteBufferWriter;
import org.redkale.convert.bson.BsonFactory;
import org.redkale.util.Utility;
import org.redkale.convert.bson.BsonConvert;
import java.nio.*;
import java.util.*;
import org.redkale.convert.json.*;
import org.redkale.util.*;

/**
 *
 * @author zhangjx
 */
public class BsonTestMain {

    public static void main(String[] args) throws Throwable {
        Serializable[] sers = new Serializable[]{"aaa", 4};
        final BsonConvert convert = BsonFactory.root().getConvert();
        byte[] bytes = convert.convertTo(sers);
        Utility.println("---", bytes);
        Serializable[] a = convert.convertFrom(Serializable[].class, bytes);
        System.out.println(Arrays.toString(a));
        Two.main(args); 
        main2(args);
        main3(args);
        main4(args);
        main5(args);
        main6(args);
    }

    public static void main2(String[] args) throws Exception {
        final BsonConvert convert = BsonFactory.root().getConvert();
        SimpleChildEntity entry = SimpleChildEntity.create();
        byte[] bytes = convert.convertTo(SimpleEntity.class, entry);
        System.out.println("长度: " + bytes.length);
        BsonByteBufferWriter writer = convert.pollBsonWriter(() -> ByteBuffer.allocate(1));
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
        SimpleChildEntity entry2 = convert.convertFrom(SimpleChildEntity.class, buffers);
        System.out.println(entry);
        System.out.println(entry2);
    }

    public static void main3(String[] args) throws Exception {
        final BsonConvert convert = BsonFactory.root().getConvert();
        SimpleChildEntity entry = SimpleChildEntity.create();
        byte[] bytes = convert.convertTo(SimpleEntity.class, entry);
        Utility.println(null, bytes);
        System.out.println(JsonConvert.root().convertTo(entry));
        SimpleEntity rs = convert.convertFrom(SimpleEntity.class, bytes);
        System.out.println(rs.toString());
        System.out.println(JsonConvert.root().convertTo(rs));

        ComplextEntity bean = new ComplextEntity();
        byte[] bytes2 = convert.convertTo(Object.class, bean);
        final int len = bytes2.length;
        BsonByteBufferWriter writer = convert.pollBsonWriter(() -> ByteBuffer.allocate(len / 2));
        convert.convertTo(writer, bean);
        bytes2 = writer.toArray();
        System.out.println(convert.convertFrom(ComplextEntity.class, bytes2).toString());
    }

    public static void main4(String[] args) throws Exception {
        final BsonConvert convert = BsonFactory.root().getConvert();
        SimpleChildEntity entry = SimpleChildEntity.create();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        convert.convertTo(out, SimpleEntity.class, entry);
        byte[] bytes = out.toByteArray();
        Utility.println(null, bytes);
        System.out.println(JsonConvert.root().convertTo(entry));
        SimpleEntity rs = convert.convertFrom(SimpleEntity.class, new ByteArrayInputStream(bytes));
        System.out.println(rs.toString());

    }

    public static void main5(String[] args) throws Exception {
        final BsonConvert convert = BsonFactory.root().getConvert();

        LinkedHashMap map = new LinkedHashMap();
        map.put("1", 1);
        map.put("2", "a2");
        byte[] bs = convert.convertTo(Object.class, map);
        Object mapobj = convert.convertFrom(Object.class, bs);
        System.out.println(mapobj);
    }

    public static void main6(String[] args) throws Exception {
        final BsonConvert convert = BsonFactory.root().getConvert();

        Optional<String> val = Optional.ofNullable("haha");
        byte[] bs = convert.convertTo(val);
        Object obj = convert.convertFrom(Optional.class, bs);
        System.out.println(obj);
        bs = convert.convertTo(Object.class, val);
        obj = convert.convertFrom(Object.class, bs);
        System.out.println(obj);
        bs = convert.convertTo(new TypeToken<Optional<String>>(){}.getType(), val);
        obj = convert.convertFrom(new TypeToken<Optional<String>>(){}.getType(), bs);
        System.out.println(obj);
        System.out.println(JsonConvert.root().convertTo(val)); 
    }
}
