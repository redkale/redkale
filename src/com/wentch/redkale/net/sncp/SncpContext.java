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
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

/**
 *
 * @author zhangjx
 */
public final class SncpContext extends Context {

    protected static class RequestEntry {

        protected final long seqid;

        protected final byte[] body;

        protected final long time = System.currentTimeMillis();

        private int received;

        public RequestEntry(long seqid, byte[] body) {
            this.seqid = seqid;
            this.body = body;
        }

        public void add(ByteBuffer buffer, int pos) {
            this.received += buffer.remaining();
            buffer.get(body, pos, buffer.remaining());
        }

        public boolean isCompleted() {
            return this.body.length <= this.received;
        }

    }

    private final ConcurrentHashMap<Long, RequestEntry> requests = new ConcurrentHashMap<>();

    protected final BsonFactory bsonFactory;

    public SncpContext(long serverStartTime, Logger logger, ExecutorService executor, ObjectPool<ByteBuffer> bufferPool,
            ObjectPool<Response> responsePool, int maxbody, Charset charset, InetSocketAddress address,
            PrepareServlet prepare, WatchFactory watch, int readTimeoutSecond, int writeTimeoutSecond) {
        super(serverStartTime, logger, executor, bufferPool, responsePool, maxbody, charset,
                address, prepare, watch, readTimeoutSecond, writeTimeoutSecond);
        this.bsonFactory = BsonFactory.root();
    }

    protected RequestEntry addRequestEntity(long seqid, byte[] bodys) {
        RequestEntry entry = new RequestEntry(seqid, bodys);
        requests.put(seqid, entry);
        return entry;
    }

    protected void expireRequestEntry(long milliSecond) {
        if (requests.size() < 32) return;
        List<Long> seqids = new ArrayList<>();
        long t = System.currentTimeMillis() - milliSecond;
        requests.forEach((x, y) -> {
            if (y.time < t) seqids.add(x);
        });
        for (long seqid : seqids) {
            requests.remove(seqid);
        }
    }

    protected RequestEntry getRequestEntity(long seqid) {
        return requests.get(seqid);
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
