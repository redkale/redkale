/*
 *
 */
package org.redkale.net.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import org.redkale.net.*;
import org.redkale.util.ObjectPool;

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
class WebSocketAsyncGroup extends AsyncIOGroup {

    public WebSocketAsyncGroup(String threadNameFormat, ExecutorService workExecutor, int bufferCapacity, ObjectPool<ByteBuffer> safeBufferPool) {
        super(false, threadNameFormat, workExecutor, bufferCapacity, safeBufferPool);
    }

    @Override
    protected AsyncIOThread createAsyncIOThread(ThreadGroup g, String name, int index, int threads, ExecutorService workExecutor, ObjectPool<ByteBuffer> safeBufferPool) throws IOException {
        return new WebSocketWriteIOThread(this.timeoutExecutor, g, name, index, threads, workExecutor, safeBufferPool);
    }

}
