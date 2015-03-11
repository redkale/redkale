/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.sncp;

import com.wentch.redkale.convert.bson.BsonConvert;
import com.wentch.redkale.convert.bson.BsonFactory;
import com.wentch.redkale.net.ResponsePool;
import com.wentch.redkale.net.BufferPool;
import com.wentch.redkale.net.PrepareServlet;
import com.wentch.redkale.net.Context;
import com.wentch.redkale.watch.WatchFactory;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;

/**
 *
 * @author zhangjx
 */
public final class SncpContext extends Context {

    protected final BsonFactory bsonFactory;

    public SncpContext(long serverStartTime, Logger logger, ExecutorService executor, BufferPool bufferPool,
            ResponsePool responsePool, int maxbody, Charset charset, InetSocketAddress address,
            PrepareServlet prepare, WatchFactory watch, int readTimeoutSecond, int writeTimeoutSecond) {
        super(serverStartTime, logger, executor, bufferPool, responsePool, maxbody, charset,
                address, prepare, watch, readTimeoutSecond, writeTimeoutSecond);
        this.bsonFactory = BsonFactory.root();
    }

    protected WatchFactory getWatchFactory() {
        return watch;
    }

    protected ExecutorService getExecutor() {
        return executor;
    }

    protected ResponsePool getResponsePool() {
        return responsePool;
    }

    public BsonConvert getBsonConvert() {
        return bsonFactory.getConvert();
    }
}
