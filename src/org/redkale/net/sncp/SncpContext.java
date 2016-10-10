/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.sncp;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;
import org.redkale.net.*;
import org.redkale.util.ObjectPool;
import org.redkale.watch.WatchFactory;

/**
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class SncpContext extends Context {

    public SncpContext(long serverStartTime, Logger logger, ExecutorService executor, int bufferCapacity, ObjectPool<ByteBuffer> bufferPool,
                       ObjectPool<Response> responsePool, int maxbody, Charset charset, InetSocketAddress address, PrepareServlet prepare,
                       WatchFactory watch, int readTimeoutSecond, int writeTimeoutSecond) {
        super(serverStartTime, logger, executor, bufferCapacity, bufferPool, responsePool, maxbody, charset,
            address, prepare, watch, readTimeoutSecond, writeTimeoutSecond);
    }
}
