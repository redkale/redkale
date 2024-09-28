/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.test.convert;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.redkale.convert.ConvertColumn;
import org.redkale.convert.bson.BsonConvert;
import org.redkale.convert.json.JsonConvert;
import org.redkale.convert.pb.ProtobufConvert;
import org.redkale.util.TypeToken;
import org.redkale.util.Utility;

/**
 *
 * @author zhangjx
 */
public class GenericEntityTest {
    private static final Type ENTITY_TYPE = new TypeToken<GenericEntity<Long, String, SimpleEntity>>() {}.getType();
    private static final String JSON =
            "{\"entry\":{\"key\":\"aaaa\",\"value\":{\"addr\":\"127.0.0.1:6666\",\"addrs\":[22222,33333,44444,55555,66666,77777,88888,99999],\"desc\":\"\",\"id\":1000000001,\"lists\":[\"aaaa\",\"bbbb\",\"cccc\"],\"map\":{\"AAA\":111,\"CCC\":333,\"BBB\":222},\"name\":\"this is name\\n \\\"test\",\"strings\":[\"zzz\",\"yyy\",\"xxx\"]}},\"list\":[1234567890],\"name\":\"你好\"}";

    public static void main(String[] args) throws Throwable {
        GenericEntityTest test = new GenericEntityTest();
        test.runJson1();
        test.runJson2();
        test.runJson3();
        test.runPb1();
        test.runPb2();
        test.runPb3();
        test.runBson1();
        test.runBson2();
        test.runBson3();
    }

    @Test
    public void runJson1() throws Exception {
        JsonConvert convert = JsonConvert.root();
        GenericEntity<Long, String, SimpleEntity> bean = createBean();
        String json = convert.convertTo(bean);
        System.out.println(json);
        System.out.println(convert.convertFrom(ENTITY_TYPE, json).toString());
        Assertions.assertEquals(JSON, json);
    }

    @Test
    public void runJson2() throws Exception {
        JsonConvert convert = JsonConvert.root();
        InputStream in = ConvertHelper.createInputStream(createBytes());
        GenericEntity<Long, String, SimpleEntity> bean = convert.convertFrom(ENTITY_TYPE, in);
        Assertions.assertEquals(JSON, bean.toString());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        convert.convertTo(out, ENTITY_TYPE, bean);
        Assertions.assertArrayEquals(createBytes(), out.toByteArray());
    }

    @Test
    public void runJson3() throws Exception {
        JsonConvert convert = JsonConvert.root();
        ByteBuffer in = ConvertHelper.createByteBuffer(createBytes());
        GenericEntity<Long, String, SimpleEntity> bean = convert.convertFrom(ENTITY_TYPE, in);
        Assertions.assertEquals(JSON, bean.toString());
        Supplier<ByteBuffer> out = ConvertHelper.createSupplier();
        ByteBuffer[] buffers = convert.convertTo(out, ENTITY_TYPE, bean);
        Assertions.assertArrayEquals(createBytes(), ConvertHelper.toBytes(buffers));
    }

    @Test
    public void runPb1() throws Exception {
        ProtobufConvert convert = ProtobufConvert.root();
        GenericEntity<Long, String, SimpleEntity> bean = createBean();
        byte[] bs = convert.convertTo(bean);
        Utility.println("proto", bs);
        String rs = convert.convertFrom(ENTITY_TYPE, bs).toString();
        System.out.println();
        Assertions.assertEquals(JSON, rs);
    }

    @Test
    public void runPb2() throws Exception {
        ProtobufConvert convert = ProtobufConvert.root();
        GenericEntity<Long, String, SimpleEntity> bean = createBean();
        byte[] bs = convert.convertTo(bean);
        //        InputStream in = ConvertHelper.createInputStream(bs);
        //        GenericEntity<Long, String, SimpleEntity> rs = convert.convertFrom(ENTITY_TYPE, in);
        //        Assertions.assertEquals(JSON, rs.toString());
        //        ByteArrayOutputStream out = new ByteArrayOutputStream();
        //        convert.convertTo(out, ENTITY_TYPE, rs);
        //        Assertions.assertArrayEquals(bs, out.toByteArray());
    }

    @Test
    public void runPb3() throws Exception {
        ProtobufConvert convert = ProtobufConvert.root();
        GenericEntity<Long, String, SimpleEntity> bean = createBean();
        byte[] bs = convert.convertTo(bean);
        //        ByteBuffer in = ConvertHelper.createByteBuffer(bs);
        //        GenericEntity<Long, String, SimpleEntity> rs = convert.convertFrom(ENTITY_TYPE, in);
        //        Assertions.assertEquals(JSON, rs.toString());
        //        Supplier<ByteBuffer> out = ConvertHelper.createSupplier();
        //        ByteBuffer[] buffers = convert.convertTo(out, ENTITY_TYPE, rs);
        //        Assertions.assertArrayEquals(bs, ConvertHelper.toBytes(buffers));
    }

    @Test
    public void runBson1() throws Exception {
        BsonConvert convert = BsonConvert.root();
        GenericEntity<Long, String, SimpleEntity> bean = createBean();
        byte[] bs = convert.convertTo(bean);
        //        Utility.println("bson", bs);
        //        String rs = convert.convertFrom(ENTITY_TYPE, bs).toString();
        //        System.out.println();
        //        Assertions.assertEquals(JSON, rs);
    }

    @Test
    public void runBson2() throws Exception {
        BsonConvert convert = BsonConvert.root();
        GenericEntity<Long, String, SimpleEntity> bean = createBean();
        byte[] bs = convert.convertTo(bean);
        //        InputStream in = ConvertHelper.createInputStream(bs);
        //        GenericEntity<Long, String, SimpleEntity> rs = convert.convertFrom(ENTITY_TYPE, in);
        //        Assertions.assertEquals(JSON, rs.toString());
        //        ByteArrayOutputStream out = new ByteArrayOutputStream();
        //        convert.convertTo(out, ENTITY_TYPE, rs);
        //        Assertions.assertArrayEquals(bs, out.toByteArray());
    }

    @Test
    public void runBson3() throws Exception {
        BsonConvert convert = BsonConvert.root();
        GenericEntity<Long, String, SimpleEntity> bean = createBean();
        byte[] bs = convert.convertTo(bean);
        //        ByteBuffer in = ConvertHelper.createByteBuffer(bs);
        //        GenericEntity<Long, String, SimpleEntity> rs = convert.convertFrom(ENTITY_TYPE, in);
        //        Assertions.assertEquals(JSON, rs.toString());
        //        Supplier<ByteBuffer> out = ConvertHelper.createSupplier();
        //        ByteBuffer[] buffers = convert.convertTo(out, ENTITY_TYPE, rs);
        //        Assertions.assertArrayEquals(bs, ConvertHelper.toBytes(buffers));
    }

    private byte[] createBytes() {
        return JSON.getBytes(StandardCharsets.UTF_8);
    }

    private GenericEntity<Long, String, SimpleEntity> createBean() {
        GenericEntity<Long, String, SimpleEntity> bean = new GenericEntity<>();
        bean.setName("你好");
        List<Long> list = new ArrayList<>();
        list.add(1234567890L);
        bean.setList(list);
        bean.setEntry(new GenericEntity.Entry<>("aaaa", SimpleEntity.create()));
        return bean;
    }

    public static class GenericEntity<T, K, V> {

        @ConvertColumn(index = 3)
        private K name;

        @ConvertColumn(index = 2)
        private List<? extends T> list;

        @ConvertColumn(index = 1)
        private Entry<K, V> entry;

        @Override
        public String toString() {
            return JsonConvert.root().convertTo(this);
        }

        public K getName() {
            return name;
        }

        public void setName(K name) {
            this.name = name;
        }

        public List<? extends T> getList() {
            return list;
        }

        public void setList(List<? extends T> list) {
            this.list = list;
        }

        public Entry<K, V> getEntry() {
            return entry;
        }

        public void setEntry(Entry<K, V> entry) {
            this.entry = entry;
        }

        public static class Entry<K, V> {

            @ConvertColumn(index = 1)
            private K key;

            @ConvertColumn(index = 2)
            private V value;

            public Entry() {}

            public Entry(K key, V value) {
                this.key = key;
                this.value = value;
            }

            @Override
            public String toString() {
                return JsonConvert.root().convertTo(this);
            }

            public K getKey() {
                return key;
            }

            public void setKey(K key) {
                this.key = key;
            }

            public V getValue() {
                return value;
            }

            public void setValue(V value) {
                this.value = value;
            }
        }
    }
}
