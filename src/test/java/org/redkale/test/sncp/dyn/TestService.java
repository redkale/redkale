/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.test.sncp.dyn;

import java.nio.channels.CompletionHandler;
import java.util.concurrent.CompletableFuture;
import org.redkale.service.Service;
import org.redkale.test.util.TestBean;

/**
 *
 * @author zhangjx
 */
public interface TestService extends Service {

    public boolean change(TestBean bean, String name, int id);

    public void insert(BooleanHandler handler, TestBean bean, String name, int id);

    public void update(
            long show, short v2, CompletionHandler<Boolean, TestBean> handler, TestBean bean, String name, int id);

    public CompletableFuture<String> changeName(TestBean bean, String name, int id);

    public void hello(TestBean bean);
}
