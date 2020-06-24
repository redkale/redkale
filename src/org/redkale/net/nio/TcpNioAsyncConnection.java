/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.nio;

import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Set;
import java.util.function.*;
import javax.net.ssl.SSLContext;
import org.redkale.net.AsyncConnection;
import org.redkale.util.ObjectPool;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * 
 * @since 2.1.0
 */
class TcpNioAsyncConnection extends AsyncConnection {

    public TcpNioAsyncConnection(ObjectPool<ByteBuffer> bufferPool, SSLContext sslContext) {
        super(bufferPool, sslContext);
    }

    public TcpNioAsyncConnection(Supplier<ByteBuffer> bufferSupplier, Consumer<ByteBuffer> bufferConsumer, SSLContext sslContext) {
        super(bufferSupplier, bufferConsumer, sslContext);
    }

    @Override
    public boolean isOpen() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean isTCP() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean shutdownInput() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean shutdownOutput() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public <T> boolean setOption(SocketOption<T> name, T value) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Set<SocketOption<?>> supportedOptions() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public SocketAddress getRemoteAddress() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public SocketAddress getLocalAddress() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public int getReadTimeoutSeconds() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public int getWriteTimeoutSeconds() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setReadTimeoutSeconds(int readTimeoutSeconds) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setWriteTimeoutSeconds(int writeTimeoutSeconds) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public ReadableByteChannel readableByteChannel() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void read(CompletionHandler<Integer, ByteBuffer> handler) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public WritableByteChannel rritableByteChannel() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public <A> void write(ByteBuffer src, A attachment, CompletionHandler<Integer, ? super A> handler) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public <A> void write(ByteBuffer[] srcs, int offset, int length, A attachment, CompletionHandler<Integer, ? super A> handler) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
