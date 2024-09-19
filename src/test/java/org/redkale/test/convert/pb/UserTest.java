/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.test.convert.pb;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redkale.convert.pb.ProtobufConvert;

/**
 *
 * @author zhangjx
 */
public class UserTest {

    public static void main(String[] args) throws Throwable {
        UserTest test = new UserTest();
        test.run();
    }

    @Test
    public void run() throws Exception {
        User user = User.create();
        ProtobufConvert convert = ProtobufConvert.root();
        byte[] bytes = convert.convertTo(user);
        User user2 = convert.convertFrom(User.class, bytes);
        System.out.println(user);
        System.out.println(user2);
        Assertions.assertEquals(user.toString(), user2.toString());
    }
}
