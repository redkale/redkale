/*
 *
 */
package org.redkale.net.http;

import org.redkale.net.AsyncConnection;
import org.redkale.net.client.ClientCodec;
import org.redkale.net.client.ClientConnection;

/**
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
class WebConnection extends ClientConnection<WebRequest, WebResult> {

    public WebConnection(WebClient client, AsyncConnection channel) {
        super(client, channel);
    }

    @Override
    protected ClientCodec createCodec() {
        return new WebCodec(this);
    }
}
