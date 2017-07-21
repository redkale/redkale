/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.net.SocketAddress;

/**
 * 远程请求的负载均衡策略
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public interface TransportStrategy {

    public AsyncConnection pollConnection(SocketAddress addr, Transport transport);
}
