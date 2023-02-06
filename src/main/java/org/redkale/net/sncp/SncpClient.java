/*
 *
 */
package org.redkale.net.sncp;

import java.net.InetSocketAddress;
import org.redkale.annotation.Resource;
import org.redkale.convert.bson.BsonConvert;
import org.redkale.net.*;
import org.redkale.net.client.*;

/**
 * SNCP版Client
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
public class SncpClient extends Client<SncpClientConnection, SncpClientRequest, SncpClientResult> {

    private final InetSocketAddress clientSncpAddress;

    @Resource
    protected BsonConvert bsonConvert;

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

    protected SncpClientConnection connect(SncpServiceInfo info) {
        return null;
    }

    //只给远程模式调用的
    public <T> T remote(final SncpServiceInfo info, final int index, final Object... params) {
        return null;
    }
}
