/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.test.convert.pb;

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
        convert.convertTo(user);
    }
}
