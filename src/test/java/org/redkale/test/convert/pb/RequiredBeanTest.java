/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.test.convert.pb;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redkale.convert.ConvertColumn;
import org.redkale.convert.json.JsonConvert;
import org.redkale.convert.pb.ProtobufConvert;
import org.redkale.test.convert.ConvertHelper;
import org.redkale.util.Utility;

/**
 *
 * @author zhangjx
 */
public class RequiredBeanTest {

    public static void main(String[] args) throws Throwable {
        RequiredBeanTest test = new RequiredBeanTest();
        test.run1();
        test.run2();
        test.run3();
    }

    @Test
    public void run1() throws Exception {
        System.out.println("-------------------- run1 ---------------------------------");
        RequiredBean bean = createRequiredBean();
        ProtobufConvert convert = ProtobufConvert.root();
        byte[] bytes = convert.convertTo(bean);
        final String excepted = "序列化0: 30.[0x08,0x0c,0x12,0x03,0x61,0x61,0x61,0x12,0x03,0x62,0x62,"
                + "0x62,0x1a,0x03,0x63,0x63,0x63,0x1a,0x03,0x64,0x64,0x64,0x22,0x02,0x2c,0x42,0x2a,"
                + "0x02,0x02,0x04]";
        System.out.println(excepted);
        String rs = Utility.println("序列化0: ", bytes);
        Assertions.assertEquals(excepted, rs);

        RequiredArray array = RequiredArray.create(bean);
        System.out.println("-----------------------------------------------");
        byte[] bytes2 = convert.convertTo(array);
        final String excepted2 = "序列化s: 100.[0x0a,0x1e,0x08,0x0c,0x12,0x03,0x61,0x61,0x61,0x12,0x03,"
                + "0x62,0x62,0x62,0x1a,0x03,0x63,0x63,0x63,0x1a,0x03,0x64,0x64,0x64,0x22,0x02,0x2c,"
                + "0x42,0x2a,0x02,0x02,0x04,0x0a,0x00,0x0a,0x1e,0x08,0x0c,0x12,0x03,0x61,0x61,0x61,"
                + "0x12,0x03,0x62,0x62,0x62,0x1a,0x03,0x63,0x63,0x63,0x1a,0x03,0x64,0x64,0x64,0x22,"
                + "0x02,0x2c,0x42,0x2a,0x02,0x02,0x04,0x10,0x40,0x1a,0x1e,0x08,0x0c,0x12,0x03,0x61,"
                + "0x61,0x61,0x12,0x03,0x62,0x62,0x62,0x1a,0x03,0x63,0x63,0x63,0x1a,0x03,0x64,0x64,"
                + "0x64,0x22,0x02,0x2c,0x42,0x2a,0x02,0x02,0x04]";
        System.out.println(excepted2);
        String rs2 = Utility.println("序列化s: ", bytes2);
        Assertions.assertEquals(excepted2, rs2);
        System.out.println("-----------------------------------------------");
        String jsons1 = JsonConvert.root().convertTo(array);
        RequiredArray array2 = convert.convertFrom(RequiredArray.class, bytes2);
        String jsons2 = JsonConvert.root().convertTo(array2);
        System.out.println(jsons1);
        System.out.println(jsons2);
        Assertions.assertEquals(jsons1, jsons2);
    }

    @Test
    public void run2() throws Exception {
        System.out.println("-------------------- run3 ---------------------------------");
        ProtobufConvert convert = ProtobufConvert.root();
        RequiredBean bean = createRequiredBean();
        RequiredArray array = RequiredArray.create(bean);
        byte[] bs = convert.convertTo(RequiredArray.class, array);
        ByteBuffer in = ConvertHelper.createByteBuffer(bs);
        RequiredArray rs = convert.convertFrom(RequiredArray.class, in);
        Supplier<ByteBuffer> out = ConvertHelper.createSupplier();
        ByteBuffer[] buffers = convert.convertTo(out, RequiredArray.class, rs);
        byte[] bs2 = ConvertHelper.toBytes(buffers);
        Utility.println("proto1 ", bs);
        Utility.println("proto2 ", bs2);
        Assertions.assertArrayEquals(bs, bs2);
    }

    @Test
    public void run3() throws Exception {
        System.out.println("-------------------- run2 ---------------------------------");
        ProtobufConvert convert = ProtobufConvert.root();
        RequiredBean bean = createRequiredBean();
        byte[] bs = convert.convertTo(RequiredBean.class, bean);
        ByteBuffer in = ConvertHelper.createByteBuffer(bs);
        RequiredBean rs = convert.convertFrom(RequiredBean.class, in);
        Supplier<ByteBuffer> out = ConvertHelper.createSupplier();
        ByteBuffer[] buffers = convert.convertTo(out, RequiredBean.class, rs);
        byte[] bs2 = ConvertHelper.toBytes(buffers);
        Utility.println("proto1 ", bs);
        Utility.println("proto2 ", bs2);
        Assertions.assertArrayEquals(bs, bs2);
    }

    public static RequiredBean createRequiredBean() {
        RequiredBean bean = new RequiredBean();
        bean.id1 = 6;
        bean.strs2 = new String[] {"aaa", "bbb"};
        bean.strs3 = List.of("ccc", "ddd");
        bean.time4 = List.of(22L, 33L);
        bean.zbig5 = List.of(new AtomicInteger(1), new AtomicInteger(2));
        return bean;
    }

    public static class RequiredArray {

        @ConvertColumn(index = 1)
        public RequiredBean[] beans;

        @ConvertColumn(index = 2)
        public int id;

        @ConvertColumn(index = 3)
        public RequiredBean one;

        public static RequiredArray create(RequiredBean bean) {
            RequiredArray array = new RequiredArray();
            array.id = 32;
            array.beans = new RequiredBean[] {bean, null, bean};
            array.one = bean;
            return array;
        }
    }

    public static class RequiredBean {

        @ConvertColumn(index = 1)
        public int id1;

        @ConvertColumn(index = 2)
        public String[] strs2;

        @ConvertColumn(index = 3)
        public List<String> strs3;

        @ConvertColumn(index = 4)
        public List<Long> time4;

        @ConvertColumn(index = 5)
        public List<AtomicInteger> zbig5;
    }
}
