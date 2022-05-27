/*
 */
package org.redkale.util;

import java.io.IOException;
import java.net.*;
import java.util.List;

/**
 * 简单的http代理器
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.7.0
 */
public class SimpleProxySelector extends ProxySelector {

    private static final List<Proxy> NO_PROXY_LIST = List.of(Proxy.NO_PROXY);

    final List<Proxy> list;

    SimpleProxySelector(Proxy... proxys) {
        list = proxys.length == 0 ? NO_PROXY_LIST : List.of(proxys);
    }

    public static SimpleProxySelector create(Proxy... proxys) {
        return new SimpleProxySelector(proxys);
    }

    @Override
    public void connectFailed(URI uri, SocketAddress sa, IOException e) {
        /* ignore */
    }

    @Override
    public synchronized List<Proxy> select(URI uri) {
        String scheme = uri.getScheme().toLowerCase();
        if (scheme.equals("http") || scheme.equals("https")) {
            return list;
        } else {
            return NO_PROXY_LIST;
        }
    }
}
