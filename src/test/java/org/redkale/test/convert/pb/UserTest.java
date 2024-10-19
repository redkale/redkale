/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.test.convert.pb;

import java.io.Serializable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redkale.convert.ConvertColumn;
import org.redkale.convert.json.JsonConvert;
import org.redkale.convert.pb.ProtobufConvert;
import org.redkale.convert.pb.ProtobufFactory;
import org.redkale.test.convert.User;
import org.redkale.util.AnyValue;
import org.redkale.util.AnyValueWriter;
import org.redkale.util.Utility;

/**
 *
 * @author zhangjx
 */
public class UserTest {

    public static void main(String[] args) throws Throwable {
        UserTest test = new UserTest();
        test.run1();
        test.run2();
        test.run3();
    }

    @Test
    public void run1() throws Exception {
        User user = User.create();
        ProtobufConvert convert = ProtobufConvert.root();
        byte[] bytes = convert.convertTo(user);
        User user2 = convert.convertFrom(User.class, bytes);
        System.out.println(user);
        System.out.println(user2);
        Assertions.assertEquals(user.toString(), user2.toString());
    }

    @Test
    public void run2() throws Exception {
        InnerBean bean = new InnerBean();
        bean.id = 20;
        bean.time = 1122334455;
        bean.names = new Serializable[] {"aaa", "bbb"};
        byte[] bs = ProtobufConvert.root().convertTo(bean);
        InnerBean bean2 = ProtobufConvert.root().convertFrom(InnerBean.class, bs);
        System.out.println(bean2);
        Assertions.assertEquals(bean.toString(), bean2.toString());
    }

    @Test
    public void run3() throws Exception {
        ProtobufFactory factory = ProtobufFactory.root();
        AnyValueWriter writer = AnyValueWriter.create();
        writer.addValue("name", "aaa");
        writer.addValue("name", "bbb");
        writer.addValue("node", AnyValueWriter.create("id", "111"));
        writer.addValue("node", AnyValueWriter.create("id", "222"));
        writer.addValue("node", AnyValueWriter.create("id", "333"));
        String excepted =
                "proto-buf 89.[0x12,0x0b,0x0a,0x04,0x6e,0x61,0x6d,0x65,0x12,0x03,0x61,0x61,0x61,0x12,0x0b,0x0a,0x04,0x6e,0x61,0x6d,0x65,0x12,0x03,0x62,0x62,0x62,0x1a,0x13,0x0a,0x04,0x6e,0x6f,0x64,0x65,0x12,0x0b,0x12,0x09,0x0a,0x02,0x69,0x64,0x12,0x03,0x31,0x31,0x31,0x1a,0x13,0x0a,0x04,0x6e,0x6f,0x64,0x65,0x12,0x0b,0x12,0x09,0x0a,0x02,0x69,0x64,0x12,0x03,0x32,0x32,0x32,0x1a,0x13,0x0a,0x04,0x6e,0x6f,0x64,0x65,0x12,0x0b,0x12,0x09,0x0a,0x02,0x69,0x64,0x12,0x03,0x33,0x33,0x33]";
        byte[] bs = factory.getConvert().convertTo(AnyValue.class, writer);
        System.out.println(excepted);
        String rs = Utility.println("pbconvert ", bs);
        Assertions.assertEquals(excepted.substring(excepted.indexOf('[')), rs.substring(rs.indexOf('[')));

        AnyValue other = factory.getConvert().convertFrom(AnyValue.class, bs);
        System.out.println(other);
        Assertions.assertEquals(writer.toString(), other.toString());
    }

    public static class InnerBean {

        @ConvertColumn(index = 1)
        public Serializable id;

        @ConvertColumn(index = 2)
        public Serializable[] names;

        @ConvertColumn(index = 3)
        public Serializable time;

        @Override
        public String toString() {
            return JsonConvert.root().convertTo(this);
        }
    }
}
