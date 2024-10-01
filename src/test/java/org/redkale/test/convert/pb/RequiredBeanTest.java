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
        RequiredBean bean = RequiredBean.create();
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

    public static class RequiredArray {
        @ConvertColumn(index = 1)
        private RequiredBean[] beans;

        @ConvertColumn(index = 2)
        private int id;

        public RequiredBean[] getBeans() {
            return beans;
        }

        public void setBeans(RequiredBean[] beans) {
            this.beans = beans;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }
    }

    public static class RequiredBean {

        public static RequiredBean create() {
            RequiredBean bean = new RequiredBean();
            bean.id = 6;
            bean.strs1 = new String[] {"aaa", "bbb"};
            bean.strs2 = List.of("ccc", "ddd");
            bean.zbig1 = List.of(new AtomicInteger(1), new AtomicInteger(2));
            return bean;
        }

        @ConvertColumn(index = 1)
        private int id;

        @ConvertColumn(index = 2)
        private String[] strs1;

        @ConvertColumn(index = 3)
        private List<String> strs2;

        @ConvertColumn(index = 4)
        private List<AtomicInteger> zbig1;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String[] getStrs1() {
            return strs1;
        }

        public void setStrs1(String[] strs1) {
            this.strs1 = strs1;
        }

        public List<String> getStrs2() {
            return strs2;
        }

        public void setStrs2(List<String> strs2) {
            this.strs2 = strs2;
        }

        public List<AtomicInteger> getZbig1() {
            return zbig1;
        }

        public void setZbig1(List<AtomicInteger> zbig1) {
            this.zbig1 = zbig1;
        }
    }
}
