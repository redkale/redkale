/*
 */
package org.redkale.net.client;

import java.net.SocketAddress;
import java.util.concurrent.CompletableFuture;
import org.redkale.convert.json.JsonConvert;
import org.redkale.net.*;

/**
 * Client连接地址
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.7.0
 */
public class ClientAddress implements java.io.Serializable {

    protected SocketAddress address;

    public ClientAddress() {
    }


    public ClientAddress(SocketAddress address) {
        this.address = address;
    }

    public CompletableFuture<AsyncConnection> createClient(final boolean tcp, final AsyncGroup group, int readTimeoutSeconds, int writeTimeoutSeconds) {
        return group.createClient(tcp, address, readTimeoutSeconds, writeTimeoutSeconds);
    }
    
    public SocketAddress getAddress() {
        return address;
    }

    public void setAddress(SocketAddress address) {
        this.address = address;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }

}
