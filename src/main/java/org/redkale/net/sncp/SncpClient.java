/*
 *
 */
package org.redkale.net.sncp;

import java.net.InetSocketAddress;
import org.redkale.net.*;
import org.redkale.net.client.*;

/**
 *
 * @author zhangjx
 */
public class SncpClient extends Client<SncpClientConnection, SncpClientRequest, SncpClientResult> {

    private InetSocketAddress sncpAddress;

    @SuppressWarnings("OverridableMethodCallInConstructor")
    public SncpClient(String name, AsyncGroup group, InetSocketAddress sncpAddress, ClientAddress address, int maxConns, int maxPipelines) {
        super(name, group, true, address, maxConns, maxPipelines, null, null, null); //maxConns
        this.sncpAddress = sncpAddress;
    }

    @Override
    public SncpClientConnection createClientConnection(int index, AsyncConnection channel) {
        return new SncpClientConnection(this, index, channel);
    }

    public InetSocketAddress getSncpAddress() {
        return sncpAddress;
    }

}
