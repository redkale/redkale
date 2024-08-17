/*
 *
 */
package org.redkale.net.http;

import org.redkale.convert.ConvertDisabled;
import org.redkale.convert.json.JsonConvert;
import org.redkale.net.client.ClientResult;

/**
 * 详情见: https://redkale.org
 *
 * @see org.redkale.net.http.WebClient
 * @see org.redkale.net.http.WebConnection
 * @see org.redkale.net.http.WebRequest
 *
 * @author zhangjx
 * @param <T> T
 * @since 2.8.0
 */
public class WebResult<T> extends HttpResult<T> implements ClientResult {

    int readState;

    int contentLength = -1;

    @Override
    @ConvertDisabled
    public boolean isKeepAlive() {
        return true;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(HttpResult.class, this);
    }
}
