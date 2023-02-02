/*
 *
 */
package org.redkale.net.sncp;

import org.redkale.net.*;
import org.redkale.net.client.*;

/**
 *
 * @author zhangjx
 */
public class SncpClient extends Client<SncpClientConnection, SncpClientRequest, SncpClientResult> {

    @SuppressWarnings("OverridableMethodCallInConstructor")
    public SncpClient(String name, AsyncGroup group, String key, ClientAddress address, int maxConns, int maxPipelines) {
        super(name, group, true, address, maxConns, maxPipelines, null, null, null); //maxConns
    }

    @Override
    protected SncpClientConnection createClientConnection(int index, AsyncConnection channel) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
