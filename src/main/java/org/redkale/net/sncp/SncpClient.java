/*
 *
 */
package org.redkale.net.sncp;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import org.redkale.net.*;
import org.redkale.net.client.*;

/**
 * SNCP版Client, 一个SncpServer只能对应一个SncpClient
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public class SncpClient extends Client<SncpClientConnection, SncpClientRequest, SncpClientResult> {

    private final AtomicLong seqno = new AtomicLong();

    final String nodeid;

    final InetSocketAddress clientSncpAddress;

    public SncpClient(
            String name,
            AsyncGroup group,
            String nodeid,
            InetSocketAddress clientSncpAddress,
            ClientAddress address,
            String netprotocol,
            int maxConns,
            int maxPipelines) {
        super(
                name,
                group,
                "TCP".equalsIgnoreCase(netprotocol),
                address,
                maxConns,
                maxPipelines,
                null,
                null,
                null); // maxConns
        this.clientSncpAddress = clientSncpAddress;
        this.nodeid = nodeid;
        this.connectTimeoutSeconds = 10;
        this.readTimeoutSeconds = 10;
        this.writeTimeoutSeconds = 10;
    }

    @Override
    public SncpClientConnection createClientConnection(AsyncConnection channel) {
        return new SncpClientConnection(this, channel);
    }

    public InetSocketAddress getClientSncpAddress() {
        return clientSncpAddress;
    }

    public String getNodeid() {
        return nodeid;
    }

    protected long nextSeqno() {
        // System.nanoTime()值并发下会出现重复，windows11 jdk17出现过
        return seqno.incrementAndGet();
    }

    @Override
    protected CompletableFuture<SncpClientResult> writeChannel(ClientConnection conn, SncpClientRequest request) {
        return super.writeChannel(conn, request);
    }
}
