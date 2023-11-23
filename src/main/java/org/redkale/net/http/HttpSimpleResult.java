/*
 *
 */
package org.redkale.net.http;

import org.redkale.net.client.ClientResult;
import static org.redkale.net.http.HttpSimpleClient.ClientReadCompletionHandler.READ_STATE_ROUTE;

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
class HttpSimpleResult<T> extends HttpResult<T> implements ClientResult {

    int readState = READ_STATE_ROUTE;

    int contentLength = -1;

    byte[] headerBytes;

    boolean headerParsed = false;

    @Override
    public boolean isKeepAlive() {
        return true;
    }

}
