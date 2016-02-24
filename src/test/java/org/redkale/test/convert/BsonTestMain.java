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
import java.util.Arrays;
import org.redkale.convert.json.*;

/**
 *
 * @author zhangjx
 */
public class BsonTestMain {

    public static void main(String[] args) throws Exception {
        Serializable[] sers = new Serializable[]{"aaa", 4};
        final BsonConvert convert = BsonFactory.root().getConvert();
        byte[] bytes = convert.convertTo(sers);
        Utility.println("---", bytes); 
        Serializable[] a = convert.convertFrom(Serializable[].class, bytes);
        System.out.println(Arrays.toString(a));
        main2(args);
        main3(args);
        main4(args);
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
}
