/*
 *
 */
package org.redkale.net.http;

import java.nio.ByteBuffer;
import org.redkale.net.client.ClientCodec;
import org.redkale.util.ByteArray;

/**
 *
 * @author zhangjx
 */
class HttpSimpleCodec extends ClientCodec<HttpSimpleRequest, HttpSimpleResult> {

    public HttpSimpleCodec(HttpSimpleConnection connection) {
        super(connection);
    }

    @Override
    public void decodeMessages(ByteBuffer buffer, ByteArray array) {
        //do nothing
    }

}
