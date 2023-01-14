/*
 *
 */
package org.redkale.net.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import org.redkale.net.AsyncIOThread;
import org.redkale.util.ObjectPool;

/**
 * 客户端IO读线程
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
public class ClientReadIOThread extends AsyncIOThread {

    public ClientReadIOThread(ThreadGroup g, String name, int index, int threads,
        ExecutorService workExecutor, ObjectPool<ByteBuffer> safeBufferPool) throws IOException {
        super(g, name, index, threads, workExecutor, safeBufferPool);
    }

}
