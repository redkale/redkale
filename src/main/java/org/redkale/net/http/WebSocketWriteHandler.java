/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.util.*;
import java.util.logging.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.channels.CompletionHandler;
import org.redkale.util.ByteArray;
import static org.redkale.net.http.WebSocket.*;

/**
 *
 * @author zhangjx
 */
public class WebSocketWriteHandler implements CompletionHandler<Integer, Void> {

    protected final HttpContext context;

    protected final WebSocket webSocket;

    protected final AtomicBoolean writePending = new AtomicBoolean();

    protected final ByteArray writeArray = new ByteArray();

    protected final List<WebSocketFuture<Integer>> respList = new ArrayList();

    protected final ConcurrentLinkedDeque<WebSocketFuture<Integer>> requestQueue = new ConcurrentLinkedDeque();

    public WebSocketWriteHandler(HttpContext context, WebSocket webSocket) {
        this.context = context;
        this.webSocket = webSocket;
    }

    public CompletableFuture<Integer> send(WebSocketPacket... packets) {
        WebSocketFuture<Integer> future = new WebSocketFuture<>(packets);
        if (writePending.compareAndSet(false, true)) {
            respList.clear();
            respList.add(future);
            writeArray.clear();
            for (WebSocketPacket p : packets) {
                writeEncode(p);
            }
            webSocket._channel.write(writeArray, this);
        } else {
            requestQueue.offer(future);
        }
        return future;
    }

    @Override
    public void completed(Integer result, Void attachment) {
        webSocket.lastSendTime = System.currentTimeMillis();
        for (WebSocketFuture<Integer> future : respList) {
            future.complete(0);
        }
        respList.clear();
        writeArray.clear();
        WebSocketFuture req;
        while ((req = requestQueue.poll()) != null) {
            respList.add(req);
            for (WebSocketPacket p : req.packets) {
                writeEncode(p);
            }
        }
        if (writeArray.isEmpty()) {
            if (!writePending.compareAndSet(true, false)) {
                completed(0, attachment);
            }
        } else {
            webSocket._channel.write(writeArray, this);
        }
    }

    @Override
    public void failed(Throwable exc, Void attachment) {
        WebSocketFuture req;
        try {
            while ((req = requestQueue.poll()) != null) {
                req.completeExceptionally(exc);
            }
            for (WebSocketFuture<Integer> future : respList) {
                future.completeExceptionally(exc);
            }
            respList.clear();
        } catch (Exception e) {
        }
        webSocket.kill(RETCODE_SENDEXCEPTION, "websocket send message failed on CompletionHandler");
        if (exc != null && context.getLogger().isLoggable(Level.FINER)) {
            context.getLogger().log(Level.FINER, "WebSocket sendMessage on CompletionHandler failed, force to close channel, live " + (System.currentTimeMillis() - webSocket.getCreateTime()) / 1000 + " seconds", exc);
        }
    }

    //消息编码
    protected void writeEncode(final WebSocketPacket packet) {
        final ByteArray array = writeArray;
        final byte opcode = (byte) (packet.type.getValue() | 0x80);
        final byte[] content = packet.getPayload();
        final int len = content.length;
        if (len <= 0x7D) { //125
            array.put(opcode);
            array.put((byte) len);
        } else if (len <= 0xFFFF) { // 65535
            array.put(opcode);
            array.put((byte) 0x7E); //126
            array.putChar((char) len);
        } else {
            array.put(opcode);
            array.put((byte) 0x7F); //127
            array.putLong(len);
        }
        array.put(content);
    }

    protected static class WebSocketFuture<T> extends CompletableFuture<T> {

        protected WebSocketPacket[] packets;

        public WebSocketFuture() {
            super();
        }

        public WebSocketFuture(WebSocketPacket... packets) {
            super();
            this.packets = packets;
        }
    }
}
