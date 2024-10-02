/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.test.convert.pb;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redkale.convert.Encodeable;
import org.redkale.convert.pb.ProtobufDynEncoder;
import org.redkale.convert.pb.ProtobufFactory;

/**
 *
 * @author zhangjx
 */
public class UserDynTest {

    public static void main(String[] args) throws Throwable {
        UserDynTest test = new UserDynTest();
        test.run1();
        test.run2();
    }

    @Test
    public void run1() throws Exception {
        ProtobufFactory factory = ProtobufFactory.root();
        Method method = ProtobufFactory.class.getDeclaredMethod("createObjectEncoder", Type.class);
        method.setAccessible(true);
        Encodeable encoder = (Encodeable) method.invoke(factory, UserBean.class);
        Assertions.assertTrue(!ProtobufDynEncoder.class.isAssignableFrom(encoder.getClass()));
    }

    @Test
    public void run2() throws Exception {
        ProtobufFactory factory = ProtobufFactory.root();
        Encodeable encoder = factory.loadEncoder(UserBean.class);
        Assertions.assertTrue(ProtobufDynEncoder.class.isAssignableFrom(encoder.getClass()));
    }
}
