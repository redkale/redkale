/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.Collection;

/**
 * userid 与 sncpaddress组合对象
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public interface WebSocketUserAddress {

    Serializable userid();

    InetSocketAddress sncpAddress();

    Collection<InetSocketAddress> sncpAddresses();
}
