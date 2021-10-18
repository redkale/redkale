/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.client;

import java.util.function.*;
import org.redkale.net.WorkThread;
import org.redkale.util.ByteArray;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.3.0
 */
public abstract class ClientRequest implements BiConsumer<ClientConnection, ByteArray> {

    protected long createTime = System.currentTimeMillis();

    WorkThread workThread;

    public long getCreateTime() {
        return createTime;
    }

    public <T extends ClientRequest> T currThread(WorkThread thread) {
        this.workThread = thread;
        return (T) this;
    }

    protected void prepare() {
        this.createTime = System.currentTimeMillis();
    }

    protected boolean recycle() {
        this.createTime = 0;
        return true;
    }
}
