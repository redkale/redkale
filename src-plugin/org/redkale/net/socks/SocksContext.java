/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.socks;

import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.concurrent.*;
import java.util.logging.*;
import org.redkale.net.*;
import org.redkale.net.http.*;
import org.redkale.util.*;
import org.redkale.watch.*;

/**
 *
 * @author zhangjx
 */
public class SocksContext extends HttpContext {

    protected final AsynchronousChannelGroup group;

    public SocksContext(long serverStartTime, Logger logger, ExecutorService executor, int bufferCapacity, ObjectPool<ByteBuffer> bufferPool,
            ObjectPool<Response> responsePool, int maxbody, Charset charset, InetSocketAddress address, PrepareServlet prepare,
            WatchFactory watch, int readTimeoutSecond, int writeTimeoutSecond, String contextPath) {
        super(serverStartTime, logger, executor, bufferCapacity, bufferPool, responsePool, maxbody, charset,
                address, prepare, watch, readTimeoutSecond, writeTimeoutSecond, contextPath);
        AsynchronousChannelGroup g = null;
        try {
            g = AsynchronousChannelGroup.withThreadPool(executor);
        } catch (Exception e) {
            e.printStackTrace();
        }
        this.group = g;
    }

    protected AsynchronousChannelGroup getAsynchronousChannelGroup() {
        return group;
    }

}
