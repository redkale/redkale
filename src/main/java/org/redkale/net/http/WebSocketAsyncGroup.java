/*
 *
 */
package org.redkale.net.http;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import org.redkale.net.*;
import org.redkale.util.ByteBufferPool;

/**
 * WebSocket只写版的AsyncIOGroup <br>
 * 只会用到ioWriteThread
 *
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
@Deprecated(since = "2.8.0")
class WebSocketAsyncGroup extends AsyncIOGroup {

    public WebSocketAsyncGroup(String threadNameFormat, ExecutorService workExecutor, ByteBufferPool safeBufferPool) {
        super(threadNameFormat, workExecutor, safeBufferPool);
    }

    @Override
    protected AsyncIOThread createAsyncIOThread(ThreadGroup g, String name, int index, int threads, ExecutorService workExecutor, ByteBufferPool safeBufferPool) throws IOException {
        return new WebSocketWriteIOThread(this.timeoutExecutor, g, name, index, threads, workExecutor, safeBufferPool);
    }

}
