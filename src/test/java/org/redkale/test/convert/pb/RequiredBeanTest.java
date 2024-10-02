/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.test.convert.pb;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redkale.convert.ConvertColumn;
import org.redkale.convert.json.JsonConvert;
import org.redkale.convert.pb.ProtobufConvert;
import org.redkale.util.Utility;

/**
 *
 * @author zhangjx
 */
public class RequiredBeanTest {

    public static void main(String[] args) throws Throwable {
        RequiredBeanTest test = new RequiredBeanTest();
        test.run();
    }

    @Test
    public void run() throws Exception {
        RequiredBean bean = createRequiredBean();
        ProtobufConvert convert = ProtobufConvert.root();
        byte[] bytes = convert.convertTo(bean);
        System.out.println("序列化0: 26.[0x08,0x0c,0x12,0x03,0x61,0x61,0x61,0x12,0x03,0x62,0x62,0x62,"
                + "0x1a,0x03,0x63,0x63,0x63,0x1a,0x03,0x64,0x64,0x64,0x22,0x02,0x02,0x04]");
        Utility.println("序列化0: ", bytes);
        RequiredArray array = new RequiredArray();
        array.id = 32;
        array.beans = new RequiredBean[] {bean, null, bean};
        byte[] bytes2 = convert.convertTo(array);
        System.out.println("序列化s: 60.[0x0a,0x1a,0x08,0x0c,0x12,0x03,0x61,0x61,0x61,0x12,0x03,0x62,0x62,0x62,"
                + "0x1a,0x03,0x63,0x63,0x63,0x1a,0x03,0x64,0x64,0x64,0x22,0x02,0x02,0x04,"
                + "0x0a,0x00,"
                + "0x0a,0x1a,0x08,0x0c,0x12,0x03,0x61,0x61,0x61,0x12,0x03,0x62,0x62,0x62,"
                + "0x1a,0x03,0x63,0x63,0x63,0x1a,0x03,0x64,0x64,0x64,0x22,0x02,0x02,0x04,"
                + "0x10,0x40]");
        Utility.println("序列化s: ", bytes2);
        System.out.println("-----------------------------------------------");
        String jsons1 = JsonConvert.root().convertTo(array);
        RequiredArray array2 = convert.convertFrom(RequiredArray.class, bytes2);
        String jsons2 = JsonConvert.root().convertTo(array2);
        System.out.println(jsons1);
        System.out.println(jsons2);
        Assertions.assertEquals(jsons1, jsons2);
    }

    public static RequiredBean createRequiredBean() {
        RequiredBean bean = new RequiredBean();
        bean.id = 6;
        bean.strs1 = new String[] {"aaa", "bbb"};
        bean.strs2 = List.of("ccc", "ddd");
        bean.zbig1 = List.of(new AtomicInteger(1), new AtomicInteger(2));
        return bean;
    }

    public static class RequiredArray {

        @ConvertColumn(index = 1)
        public RequiredBean[] beans;

        @ConvertColumn(index = 2)
        public int id;
    }

    public static class RequiredBean {

        @ConvertColumn(index = 1)
        public int id;

        @ConvertColumn(index = 2)
        public String[] strs1;

        @ConvertColumn(index = 3)
        public List<String> strs2;

        @ConvertColumn(index = 4)
        public List<AtomicInteger> zbig1;
    }
}
