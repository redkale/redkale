/*
 *
 */
package org.redkale.net.sncp;

import org.redkale.net.AsyncConnection;
import org.redkale.net.client.*;

/**
 *
 * @author zhangjx
 */
public class SncpClientConnection extends ClientConnection<SncpClientRequest, SncpClientResult> {

    public SncpClientConnection(SncpClient client, int index, AsyncConnection channel) {
        super(client, index, channel);
    }

    @Override
    protected ClientCodec createCodec() {
        return new SncpClientCodec(this);
    }

}
