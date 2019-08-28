/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.Collection;
import org.redkale.convert.json.JsonConvert;

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

    public static WebSocketUserAddress create(WebSocketUserAddress userAddress) {
        return new SimpleWebSocketUserAddress(userAddress);
    }

    public static WebSocketUserAddress create(Serializable userid, InetSocketAddress sncpAddress) {
        return new SimpleWebSocketUserAddress(userid, sncpAddress, null);
    }

    public static WebSocketUserAddress create(Serializable userid, Collection<InetSocketAddress> sncpAddresses) {
        return new SimpleWebSocketUserAddress(userid, null, sncpAddresses);
    }

    public static class SimpleWebSocketUserAddress implements WebSocketUserAddress {

        private Serializable userid;

        private InetSocketAddress sncpAddress;

        private Collection<InetSocketAddress> sncpAddresses;

        public SimpleWebSocketUserAddress() {
        }

        public SimpleWebSocketUserAddress(Serializable userid, InetSocketAddress sncpAddress, Collection<InetSocketAddress> sncpAddresses) {
            this.userid = userid;
            this.sncpAddress = sncpAddress;
            this.sncpAddresses = sncpAddresses;
        }

        public SimpleWebSocketUserAddress(WebSocketUserAddress userAddress) {
            if (userAddress == null) return;
            this.userid = userAddress.userid();
            this.sncpAddress = userAddress.sncpAddress();
            this.sncpAddresses = userAddress.sncpAddresses();
        }

        @Override
        public Serializable userid() {
            return userid;
        }

        @Override
        public InetSocketAddress sncpAddress() {
            return sncpAddress;
        }

        @Override
        public Collection<InetSocketAddress> sncpAddresses() {
            return sncpAddresses;
        }

        public Serializable getUserid() {
            return userid;
        }

        public void setUserid(Serializable userid) {
            this.userid = userid;
        }

        public InetSocketAddress getSncpAddress() {
            return sncpAddress;
        }

        public void setSncpAddress(InetSocketAddress sncpAddress) {
            this.sncpAddress = sncpAddress;
        }

        public Collection<InetSocketAddress> getSncpAddresses() {
            return sncpAddresses;
        }

        public void setSncpAddresses(Collection<InetSocketAddress> sncpAddresses) {
            this.sncpAddresses = sncpAddresses;
        }

        @Override
        public String toString() {
            return JsonConvert.root().convertTo(this);
        }
    }
}
