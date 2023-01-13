/*
 *
 */
package org.redkale.net.client;

import java.nio.ByteBuffer;
import java.nio.channels.Selector;
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

    public ClientReadIOThread(String name, int index, int threads, ExecutorService workExecutor, Selector selector,
        ObjectPool<ByteBuffer> unsafeBufferPool, ObjectPool<ByteBuffer> safeBufferPool) {
        super(name, index, threads, workExecutor, selector, unsafeBufferPool, safeBufferPool);
    }

}
