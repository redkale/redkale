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
import org.redkale.convert.pb.ProtobufArrayEncoder;
import org.redkale.convert.pb.ProtobufConvert;
import org.redkale.convert.pb.ProtobufFactory;
import org.redkale.convert.pb.ProtobufObjectEncoder;
import org.redkale.test.convert.User;
import org.redkale.util.AnyValue;
import org.redkale.util.AnyValueWriter;

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

    // @Test
    public void run3() throws Exception {
        ProtobufFactory factory = ProtobufFactory.root();
        AnyValueWriter writer = AnyValueWriter.create();
        writer.addValue("name", "aaa");
        writer.addValue("name", "bbb");
        writer.addValue("node", AnyValueWriter.create("id", "123"));
        writer.addValue("node", AnyValueWriter.create("id", "456"));
        System.out.println(writer);
        ProtobufObjectEncoder encoder = (ProtobufObjectEncoder) factory.loadEncoder(AnyValueWriter.class);
        System.out.println(encoder);
        ProtobufArrayEncoder stringEntrys = (ProtobufArrayEncoder) encoder.getMembers()[2].getEncoder();
        System.out.println(stringEntrys);
        byte[] bs = factory.getConvert().convertTo(AnyValue.class, writer);
        System.out.println("长度: " + bs.length);
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
