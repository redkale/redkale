/*
 */
package org.redkale.net.http;

import org.redkale.util.AnyValue;

/**
 * rpc鉴权验证器Secret key的实现类 <br>
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.7.0
 */
public class HttpRpcSecretAuthenticator implements HttpRpcAuthenticator {

    protected String secretKey = "";

    @Override
    public void init(AnyValue config) {
        this.secretKey = config.getValue("secret").trim();
    }

    @Override
    public boolean auth(HttpRequest request, HttpResponse response) {
        String key = request.getHeader("rest-rpc-secret");
        if (key == null) {
            response.finish(404, null);
            return false;
        }
        if (!secretKey.equals(key)) {
            response.finish(404, null);
            return false;
        }
        return true;
    }
}
