/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.sncp;

import com.wentch.redkale.convert.bson.*;
import com.wentch.redkale.net.*;
import com.wentch.redkale.util.*;
import com.wentch.redkale.watch.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.concurrent.*;
import java.util.logging.*;

/**
 *
 * @author zhangjx
 */
public final class SncpContext extends Context {

    protected final BsonFactory bsonFactory;

    public SncpContext(long serverStartTime, Logger logger, ExecutorService executor, int bufferCapacity, ObjectPool<ByteBuffer> bufferPool,
            ObjectPool<Response> responsePool, int maxbody, Charset charset, InetSocketAddress address, PrepareServlet prepare,
            WatchFactory watch, int readTimeoutSecond, int writeTimeoutSecond) {
        super(serverStartTime, logger, executor, bufferCapacity, bufferPool, responsePool, maxbody, charset,
                address, prepare, watch, readTimeoutSecond, writeTimeoutSecond);
        this.bsonFactory = BsonFactory.root();
    }

    protected WatchFactory getWatchFactory() {
        return watch;
    }

    protected ExecutorService getExecutor() {
        return executor;
    }

    protected ObjectPool<Response> getResponsePool() {
        return responsePool;
    }

    public BsonConvert getBsonConvert() {
        return bsonFactory.getConvert();
    }
}
