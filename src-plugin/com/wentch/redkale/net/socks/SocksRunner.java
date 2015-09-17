/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.socks;

import com.wentch.redkale.net.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.logging.*;

/**
 *
 * @author zhangjx
 */
public class SocksRunner implements Runnable {

    private final AsyncConnection channel;

    private final Logger logger;

    private final boolean finest;

    private final Context context;

    private final byte[] bindAddressBytes;

    private ByteBuffer buffer;

    protected boolean closed = false;

    private InetSocketAddress remoteAddress;

    private AsyncConnection remoteChannel;

    public SocksRunner(Context context, AsyncConnection channel, final byte[] bindAddressBytes) {
        this.context = context;
        this.logger = context.getLogger();
        this.finest = this.context.getLogger().isLoggable(Level.FINEST);
        this.channel = channel;
        this.buffer = context.pollBuffer();
        this.bindAddressBytes = bindAddressBytes;
    }

    @Override
    public void run() {
        try {
            ask();
        } catch (Exception e) {
            closeRunner(e);
        }
    }

    private void ask() {
        buffer.putChar((char) 0x0500);
        buffer.flip();
        this.channel.write(buffer, null, new CompletionHandler<Integer, Void>() {

            @Override
            public void completed(Integer result, Void attachment) {
                if (buffer.hasRemaining()) {
                    channel.write(buffer, null, this);
                    return;
                }
                connect();
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                closeRunner(exc);
            }
        });
    }

    private void connect() {
        buffer.clear();
        this.channel.read(buffer, null, new CompletionHandler<Integer, Void>() {

            @Override
            public void completed(Integer result, Void attachment) {
                buffer.flip();
                if (buffer.getChar() != 0x0501) {
                    if (finest) logger.finest("connect header not 0x0501");
                    closeRunner(null);
                    return;
                }
                char addrtype = buffer.getChar(); //0x0001 - 4  ; 0x0003 - x ; 0x0004 - 16
                try {
                    byte[] bytes = new byte[(addrtype == 0x0003) ? (buffer.get() & 0xff) : (addrtype * 4)];
                    buffer.get(bytes);
                    remoteAddress = new InetSocketAddress((addrtype == 0x0003) ? InetAddress.getByName(new String(bytes)) : InetAddress.getByAddress(bytes), buffer.getChar());
                } catch (UnknownHostException e) {
                    throw new RuntimeException(e);
                }
                try {
                    remoteChannel = AsyncConnection.create("TCP", remoteAddress, 6, 6);
                    buffer.clear();
                    buffer.putChar((char) 0x0500);
                    buffer.put((byte) 0x00);  //rsv
                    buffer.put(bindAddressBytes);
                    buffer.flip();
                    channel.write(buffer, null, new CompletionHandler<Integer, Void>() {

                        @Override
                        public void completed(Integer result, Void attachment) {
                            if (buffer.hasRemaining()) {
                                channel.write(buffer, null, this);
                                return;
                            }
                            stream();
                        }

                        @Override
                        public void failed(Throwable exc, Void attachment) {
                            closeRunner(exc);
                        }
                    });
                } catch (IOException e) {
                    buffer.clear();
                    buffer.putChar((char) 0x0504);
                    if (finest) logger.finest(remoteAddress + " remote connect error");
                    channel.write(buffer, null, new CompletionHandler<Integer, Void>() {

                        @Override
                        public void completed(Integer result, Void attachment) {
                            if (buffer.hasRemaining()) {
                                channel.write(buffer, null, this);
                                return;
                            }
                            closeRunner(null);
                        }

                        @Override
                        public void failed(Throwable exc, Void attachment) {
                            closeRunner(exc);
                        }
                    });
                }
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                closeRunner(exc);
            }
        });
    }

    private void stream() {
        new StreamCompletionHandler(channel, remoteChannel).completed(0, null);
        new StreamCompletionHandler(remoteChannel, channel).completed(0, null);
    }

    public synchronized void closeRunner(final Throwable e) {
        if (closed) return;
        closed = true;
        try {
            channel.close();
        } catch (Throwable t) {
        }
        context.offerBuffer(buffer);
        buffer = null;
        if (e != null && finest) {
            logger.log(Level.FINEST, "close socks channel by error", e);
        }
    }

    private class StreamCompletionHandler implements CompletionHandler<Integer, Void> {

        private final AsyncConnection conn1;

        private final AsyncConnection conn2;

        private final ByteBuffer rbuffer;

        public StreamCompletionHandler(AsyncConnection conn1, AsyncConnection conn2) {
            this.conn1 = conn1;
            this.conn2 = conn2;
            this.rbuffer = context.pollBuffer();
            this.rbuffer.flip();
        }

        @Override
        public void completed(Integer result0, Void v0) {
            final CompletionHandler self = this;
            if (rbuffer.hasRemaining()) {
                conn2.write(rbuffer, null, self);
                return;
            }
            rbuffer.clear();
            conn1.read(rbuffer, null, new CompletionHandler<Integer, Void>() {

                @Override
                public void completed(Integer result, Void attachment) {
                    rbuffer.flip();
                    conn2.write(rbuffer, attachment, self);
                }

                @Override
                public void failed(Throwable exc, Void attachment) {
                    self.failed(exc, attachment);
                }
            });
        }

        @Override
        public void failed(Throwable exc, Void v) {
            context.offerBuffer(rbuffer);
            conn1.dispose();
            conn2.dispose();
            if (finest) logger.log(Level.FINEST, "StreamCompletionHandler closed", exc);
        }
    }
}
