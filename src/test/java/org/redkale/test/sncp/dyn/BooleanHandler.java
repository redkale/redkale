/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.test.sncp.dyn;

import java.nio.channels.CompletionHandler;
import org.redkale.test.util.TestBean;

/**
 *
 * @author zhangjx
 */
public class BooleanHandler implements CompletionHandler<Boolean, TestBean> {

    @Override
    public void completed(Boolean result, TestBean attachment) {}

    @Override
    public void failed(Throwable exc, TestBean attachment) {}
}
