/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.socks;

import org.redkale.net.AsyncConnection;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.logging.*;

/**
 *
 * @see http://www.redkale.org
 * @author zhangjx
 */
public class SocksRunner implements Runnable {

    private final AsyncConnection channel;

    private final Logger logger;

    private final boolean finest;

    private final SocksContext context;

    private final byte[] bindAddressBytes;

    private ByteBuffer buffer;

    protected boolean closed = false;

    private InetSocketAddress remoteAddress;

    private AsyncConnection remoteChannel;

    public SocksRunner(SocksContext context, AsyncConnection channel, final byte[] bindAddressBytes) {
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
                try {
                    connect();
                } catch (Exception e) {
                    closeRunner(e);
                }
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
                    failed(e, attachment);
                    return;
                }
                try {
                    remoteChannel = AsyncConnection.create("TCP", context.getAsynchronousChannelGroup(), remoteAddress, 6, 6);
                    buffer.clear();
                    buffer.putChar((char) 0x0500);
                    buffer.put((byte) 0x00);  //rsv
                    buffer.put(bindAddressBytes);
                    buffer.flip();
                    final ByteBuffer rbuffer = context.pollBuffer();
                    final ByteBuffer wbuffer = context.pollBuffer();
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
                            context.offerBuffer(rbuffer);
                            context.offerBuffer(wbuffer);
                            closeRunner(exc);
                        }
                    });
                } catch (Exception e) {
                    buffer.clear();
                    buffer.putChar((char) 0x0504);
                    if (finest) logger.log(Level.FINEST, remoteAddress + " remote connect error", e);
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
        new StreamCompletionHandler(channel, remoteChannel).completed(1, null);
        new StreamCompletionHandler(remoteChannel, channel).completed(1, null);
    }

    public void closeRunner(final Throwable e) {
        if (closed) return;
        synchronized (this) {
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
    }

    private class StreamCompletionHandler implements CompletionHandler<Integer, Void> {

        private final AsyncConnection readconn;

        private final AsyncConnection writeconn;

        private final ByteBuffer rbuffer;

        public StreamCompletionHandler(AsyncConnection conn1, AsyncConnection conn2) {
            this.readconn = conn1;
            this.writeconn = conn2;
            this.rbuffer = context.pollBuffer();
            this.rbuffer.flip();
        }

        @Override
        public void completed(Integer result0, Void v0) {
            final CompletionHandler self = this;
            if (rbuffer.hasRemaining()) {
                writeconn.write(rbuffer, null, self);
                return;
            }
            if (result0 < 1) {
                self.failed(null, v0);
                return;
            }
            rbuffer.clear();
            readconn.read(rbuffer, null, new CompletionHandler<Integer, Void>() {

                @Override
                public void completed(Integer result, Void attachment) {
                    if (result < 1) {
                        self.failed(null, attachment);
                        return;
                    }
                    rbuffer.flip();
                    writeconn.write(rbuffer, attachment, self);
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
            readconn.dispose();
            writeconn.dispose();
            if (finest) logger.log(Level.FINEST, "StreamCompletionHandler closed", exc);
        }
    }
}
