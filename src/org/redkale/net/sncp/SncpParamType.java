/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.sncp;

/**
 *
 * @see http://www.redkale.org
 * @author zhangjx
 */
public enum SncpParamType {
    /**
     * SNCP协议中标记为来源地址参数, 该注解只能标记在类型为SocketAddress或InetSocketAddress的参数上。
     */
    SourceAddress,
    /**
     * SNCP协议中标记为目标地址参数, 该注解只能标记在类型为SocketAddress或InetSocketAddress的参数上。
     */
    TargetAddress;
}
