/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert.bson;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import org.junit.jupiter.api.*;
import org.redkale.annotation.ConstructorParameters;
import org.redkale.convert.bson.*;
import org.redkale.convert.json.JsonConvert;
import org.redkale.persistence.*;
import org.redkale.test.convert.SimpleChildEntity;
import org.redkale.test.convert.SimpleEntity;
import org.redkale.util.*;

/** @author zhangjx */
public class BsonMainTest {

    public static void main(String[] args) throws Throwable {
        BsonMainTest test = new BsonMainTest();
        test.run1();
        test.run2();
        test.run3();
        test.run4();
        test.run5();
        test.run6();
        test.run7();
        test.run8();
    }

    @Test
    public void run1() throws Throwable {
        Serializable[] sers = new Serializable[] {"aaa", 4};
        final BsonConvert convert = BsonFactory.root().getConvert();
        byte[] bytes = convert.convertTo(sers);
        Utility.println("---", bytes);
        byte[] checks = new byte[] {
            0x00, 0x00, 0x00, 0x02, 0x7f, 0x01, 0x41, 0x00, 0x00, 0x00, 0x03, 0x61, 0x61, 0x61, 0x01, 0x69, 0x00, 0x00,
            0x00, 0x04
        };
        Assertions.assertArrayEquals(checks, bytes);
        Serializable[] a = convert.convertFrom(Serializable[].class, bytes);
        Assertions.assertEquals("[aaa, 4]", Arrays.toString(a));
    }

    @Test
    public void run2() throws Exception {
        final BsonConvert convert = BsonFactory.root().getConvert();
        SimpleChildEntity entry = SimpleChildEntity.create();
        byte[] bytes = convert.convertTo(SimpleEntity.class, entry);
        System.out.println("长度: " + bytes.length);
        Assertions.assertEquals(260, bytes.length);
        BsonByteBufferWriter writer = convert.pollWriter(() -> ByteBuffer.allocate(1));
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
        Assertions.assertEquals(260, len);
        SimpleChildEntity entry2 = convert.convertFrom(SimpleChildEntity.class, buffers);
        System.out.println(entry);
        Assertions.assertEquals(entry.toString(), entry2.toString());
    }

    @Test
    public void run3() throws Exception {
        final BsonConvert convert = BsonFactory.root().getConvert();
        SimpleChildEntity entry = SimpleChildEntity.create();
        byte[] bytes = convert.convertTo(SimpleEntity.class, entry);
        Utility.println(null, bytes);
        System.out.println(JsonConvert.root().convertTo(entry));
        SimpleEntity rs = convert.convertFrom(SimpleEntity.class, bytes);
        Assertions.assertEquals(JsonConvert.root().convertTo(entry), rs.toString());

        ComplextEntity bean = new ComplextEntity();
        byte[] bytes2 = convert.convertTo(Object.class, bean);
        final int len = bytes2.length;
        BsonByteBufferWriter writer = convert.pollWriter(() -> ByteBuffer.allocate(len / 2));
        convert.convertTo(writer, bean);
        bytes2 = writer.toByteArray().getBytes();
        System.out.println(convert.convertFrom(ComplextEntity.class, bytes2).toString());
        Assertions.assertEquals(
                "{\"chname\":\"\",\"flag\":true,\"userid\":0}",
                convert.convertFrom(ComplextEntity.class, bytes2).toString());
    }

    @Test
    public void run4() throws Exception {
        final BsonConvert convert = BsonFactory.root().getConvert();
        SimpleChildEntity entry = SimpleChildEntity.create();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        convert.convertTo(out, SimpleEntity.class, entry);
        byte[] bytes = out.toByteArray();
        Utility.println(null, bytes);
        SimpleEntity rs = convert.convertFrom(SimpleEntity.class, new ByteArrayInputStream(bytes));
        System.out.println(rs.toString());
        Assertions.assertEquals(JsonConvert.root().convertTo(entry), rs.toString());
    }

    @Test
    public void run5() throws Exception {
        final BsonConvert convert = BsonFactory.root().getConvert();

        LinkedHashMap map = new LinkedHashMap();
        map.put("1", 1);
        map.put("2", "a2");
        byte[] bs = convert.convertTo(Object.class, map);
        Object mapobj = convert.convertFrom(Object.class, bs);
        System.out.println(mapobj);
        Assertions.assertEquals("{1=1, 2=a2}", mapobj.toString());
    }

    @Test
    public void run6() throws Exception {
        final BsonConvert convert = BsonFactory.root().getConvert();

        Optional<String> val = Optional.ofNullable("haha");
        byte[] bs = convert.convertTo(val);
        Object obj = convert.convertFrom(Optional.class, bs);
        System.out.println(obj);
        Assertions.assertEquals("Optional[haha]", obj.toString());
        bs = convert.convertTo(Object.class, val);
        obj = convert.convertFrom(Object.class, bs);
        Assertions.assertEquals("Optional[haha]", obj.toString());
        bs = convert.convertTo(new TypeToken<Optional<String>>() {}.getType(), val);
        obj = convert.convertFrom(new TypeToken<Optional<String>>() {}.getType(), bs);
        Assertions.assertEquals("Optional[haha]", obj.toString());
        System.out.println(JsonConvert.root().convertTo(val));
        Assertions.assertEquals("\"haha\"", JsonConvert.root().convertTo(val));
    }

    @Test
    public void run7() throws Throwable {
        Two two = new Two();
        two.setKey("key111");
        two.setCode(12345);
        List<String> list = new ArrayList<>();
        list.add("haha");
        two.setList(list);
        Map<String, String> map = new HashMap<>();
        map.put("222", "333");
        two.setStringMap(map);

        List<ConvertRecord> records = new ArrayList<>();
        records.add(ConvertRecord.createDefault());
        two.setRecords(records);

        Map<String, ConvertRecord> rmap = new HashMap<>();
        rmap.put("222", ConvertRecord.createDefault());
        two.setRecordMap(rmap);

        byte[] bs = BsonFactory.root().getConvert().convertTo(two);

        One one = BsonFactory.root().getConvert().convertFrom(One.class, bs);
        System.out.println(one);
        Assertions.assertEquals(
                "{\"bytes\":[3,4,5],\"code\":12345,\"ints\":[3000,4000,5000],\"key\":\"key111\"}", one.toString());
    }

    @Test
    public void run8() throws Exception {
        final JsonConvert jsonConvert = JsonConvert.root();
        final BsonConvert bsonConvert = BsonFactory.root().getConvert();
        ConstructorArgsEntity bean = new ConstructorArgsEntity(12345678, "哈哈");
        bean.setCreatetime(12345678901L);
        String json = jsonConvert.convertTo(bean);
        System.out.println(json);
        Assertions.assertEquals("{\"createtime\":12345678901,\"name\":\"哈哈\",\"userid\":12345678}", json);
        Assertions.assertEquals(
                jsonConvert.convertFrom(ConstructorArgsEntity.class, json).toString(), json);
        byte[] bytes = bsonConvert.convertTo(bean);
        Assertions.assertEquals(
                bsonConvert.convertFrom(ConstructorArgsEntity.class, bytes).toString(), json);
    }

    public static class ComplextEntity {

        @Id
        private int userid;

        private String chname = "";

        @Transient
        private boolean flag = true;

        @Transient
        private List<SimpleChildEntity> children;

        @Transient
        private SimpleEntity user;

        public int getUserid() {
            return userid;
        }

        public void setUserid(int userid) {
            this.userid = userid;
        }

        public String getChname() {
            return chname;
        }

        public void setChname(String chname) {
            this.chname = chname;
        }

        public boolean isFlag() {
            return flag;
        }

        public void setFlag(boolean flag) {
            this.flag = flag;
        }

        public List<SimpleChildEntity> getChildren() {
            return children;
        }

        public void setChildren(List<SimpleChildEntity> children) {
            this.children = children;
        }

        public SimpleEntity getUser() {
            return user;
        }

        public void setUser(SimpleEntity user) {
            this.user = user;
        }

        @Override
        public String toString() {
            return JsonConvert.root().convertTo(this);
        }
    }

    public static class ConstructorArgsEntity {

        private final int userid;

        private String name;

        private long createtime;

        @ConstructorParameters({"userid", "name"})
        public ConstructorArgsEntity(int userid, String name) {
            this.userid = userid;
            this.name = name;
        }

        public int getUserid() {
            return userid;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public long getCreatetime() {
            return createtime;
        }

        public void setCreatetime(long createtime) {
            this.createtime = createtime;
        }

        @Override
        public String toString() {
            return JsonConvert.root().convertTo(this);
        }
    }
}
