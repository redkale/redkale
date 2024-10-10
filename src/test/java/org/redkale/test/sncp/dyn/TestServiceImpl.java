/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.test.sncp.dyn;

import java.nio.channels.CompletionHandler;
import java.util.concurrent.CompletableFuture;
import org.redkale.annotation.ResourceType;
import org.redkale.test.util.TestBean;

/**
 *
 * @author zhangjx
 */
@ResourceType(TestService.class)
public class TestServiceImpl implements TestService {

    @Override
    public boolean change(TestBean bean, String name, int id) {
        return false;
    }

    @Override
    public void insert(BooleanHandler handler, TestBean bean, String name, int id) {}

    @Override
    public void update(
            long show, short v2, CompletionHandler<Boolean, TestBean> handler, TestBean bean, String name, int id) {}

    @Override
    public CompletableFuture<String> changeName(TestBean bean, String name, int id) {
        return null;
    }

    @Override
    public void hello(TestBean bean) {
        System.out.println("hello: " + bean);
    }
}
