/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.Collection;
import org.redkale.convert.ConvertColumn;
import org.redkale.convert.json.JsonConvert;

/**
 * userid 与 sncpaddress组合对象, 不能实现Serializable
 *
 * @author zhangjx
 */
public interface WebSocketUserAddress {

    Serializable userid();

    WebSocketAddress address();

    Collection<WebSocketAddress> addresses();

    public static WebSocketUserAddress create(WebSocketUserAddress userAddress) {
        return new SimpleWebSocketUserAddress(userAddress);
    }

    public static WebSocketUserAddress createTopic(Serializable userid, String mqtopic, InetSocketAddress sncpAddress) {
        return new SimpleWebSocketUserAddress(userid, mqtopic, sncpAddress);
    }

    public static WebSocketUserAddress create(Serializable userid, WebSocketAddress address) {
        return new SimpleWebSocketUserAddress(userid, address);
    }

    public static WebSocketUserAddress create(Serializable userid, Collection<WebSocketAddress> addresses) {
        return new SimpleWebSocketUserAddress(userid, addresses);
    }

    public static class SimpleWebSocketUserAddress implements WebSocketUserAddress {

        @ConvertColumn(index = 1)
        private Serializable userid;

        @ConvertColumn(index = 2)
        private WebSocketAddress address;

        @ConvertColumn(index = 3)
        private Collection<WebSocketAddress> addresses;

        public SimpleWebSocketUserAddress() {}

        public SimpleWebSocketUserAddress(Serializable userid, String mqtopic, InetSocketAddress sncpAddress) {
            this.userid = userid;
            this.address = new WebSocketAddress(mqtopic, sncpAddress);
        }

        public SimpleWebSocketUserAddress(Serializable userid, WebSocketAddress address) {
            this.userid = userid;
            this.address = address;
        }

        public SimpleWebSocketUserAddress(Serializable userid, Collection<WebSocketAddress> addresses) {
            this.userid = userid;
            this.addresses = addresses;
        }

        public SimpleWebSocketUserAddress(WebSocketUserAddress userAddress) {
            if (userAddress == null) {
                return;
            }
            this.userid = userAddress.userid();
            this.address = userAddress.address();
            this.addresses = userAddress.addresses();
        }

        @Override
        public Serializable userid() {
            return userid;
        }

        @Override
        public WebSocketAddress address() {
            return address;
        }

        @Override
        public Collection<WebSocketAddress> addresses() {
            return addresses;
        }

        public Serializable getUserid() {
            return userid;
        }

        public void setUserid(Serializable userid) {
            this.userid = userid;
        }

        public WebSocketAddress getAddress() {
            return address;
        }

        public void setAddress(WebSocketAddress address) {
            this.address = address;
        }

        public Collection<WebSocketAddress> getAddresses() {
            return addresses;
        }

        public void setAddresses(Collection<WebSocketAddress> addresses) {
            this.addresses = addresses;
        }

        @Override
        public String toString() {
            return JsonConvert.root().convertTo(this);
        }
    }
}
