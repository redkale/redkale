/*
 *
 */
package org.redkale.net.http;

import org.redkale.net.AsyncConnection;
import org.redkale.net.client.ClientCodec;
import org.redkale.net.client.ClientConnection;

/**
 *
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
class HttpSimpleConnection extends ClientConnection<HttpSimpleRequest, HttpSimpleResult> {

    public HttpSimpleConnection(HttpSimpleClient client, int index, AsyncConnection channel) {
        super(client, index, channel);
    }

    @Override
    protected ClientCodec createCodec() {
        return new HttpSimpleCodec(this);
    }

}
