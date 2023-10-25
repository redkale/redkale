/*
 *
 */
package org.redkale.net.http;

import org.redkale.net.client.ClientResult;

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

    @Override
    public boolean isKeepAlive() {
        return true;
    }

}
