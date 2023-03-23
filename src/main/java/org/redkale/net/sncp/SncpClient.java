/*
 *
 */
package org.redkale.net.sncp;

import java.net.*;
import java.util.concurrent.CompletableFuture;
import org.redkale.net.*;
import org.redkale.net.client.*;

/**
 * SNCP版Client, 一个SncpServer只能对应一个SncpClient
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
public class SncpClient extends Client<SncpClientConnection, SncpClientRequest, SncpClientResult> {

    final InetSocketAddress clientSncpAddress;

    public SncpClient(String name, AsyncGroup group, InetSocketAddress clientSncpAddress, ClientAddress address, String netprotocol, int maxConns, int maxPipelines) {
        super(name, group, "TCP".equalsIgnoreCase(netprotocol), address, maxConns, maxPipelines, null, null, null); //maxConns
        this.clientSncpAddress = clientSncpAddress;
    }

    @Override
    public SncpClientConnection createClientConnection(int index, AsyncConnection channel) {
        return new SncpClientConnection(this, index, channel);
    }

    public InetSocketAddress getClientSncpAddress() {
        return clientSncpAddress;
    }

    @Override
    protected CompletableFuture<SncpClientConnection> connect(SocketAddress addr) {
        return super.connect(addr);
    }

    @Override
    protected CompletableFuture<SncpClientResult> writeChannel(ClientConnection conn, SncpClientRequest request) {
        return super.writeChannel(conn, request);
    }

}
