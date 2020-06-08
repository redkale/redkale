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
 *
 * @author zhangjx
 */
public interface WebSocketUserAddress extends Serializable {

    Serializable userid();

    String mqtopic();

    Collection<String> mqtopics();

    InetSocketAddress sncpAddress();

    Collection<InetSocketAddress> sncpAddresses();

    public static WebSocketUserAddress create(WebSocketUserAddress userAddress) {
        return new SimpleWebSocketUserAddress(userAddress);
    }

    public static WebSocketUserAddress createTopic(Serializable userid, String mqtopic) {
        return new SimpleWebSocketUserAddress(userid, mqtopic, null, null, null);
    }

    public static WebSocketUserAddress createTopic(Serializable userid, Collection<String> mqtopics) {
        return new SimpleWebSocketUserAddress(userid, null, mqtopics, null, null);
    }

    public static WebSocketUserAddress create(Serializable userid, InetSocketAddress sncpAddress) {
        return new SimpleWebSocketUserAddress(userid, null, null, sncpAddress, null);
    }

    public static WebSocketUserAddress create(Serializable userid, Collection<InetSocketAddress> sncpAddresses) {
        return new SimpleWebSocketUserAddress(userid, null, null, null, sncpAddresses);
    }

    public static class SimpleWebSocketUserAddress implements WebSocketUserAddress {

        private Serializable userid;

        private String mqtopic;

        private Collection<String> mqtopics;

        private InetSocketAddress sncpAddress;

        private Collection<InetSocketAddress> sncpAddresses;

        public SimpleWebSocketUserAddress() {
        }

        public SimpleWebSocketUserAddress(Serializable userid, String mqtopic, InetSocketAddress sncpAddress) {
            this.userid = userid;
            this.mqtopic = mqtopic;
            this.sncpAddress = sncpAddress;
        }

        public SimpleWebSocketUserAddress(Serializable userid, Collection<String> mqtopics, Collection<InetSocketAddress> sncpAddresses) {
            this.userid = userid;
            this.mqtopics = mqtopics;
            this.sncpAddresses = sncpAddresses;
        }

        public SimpleWebSocketUserAddress(Serializable userid, String mqtopic, Collection<String> mqtopics, InetSocketAddress sncpAddress, Collection<InetSocketAddress> sncpAddresses) {
            this.userid = userid;
            this.mqtopic = mqtopic;
            this.mqtopics = mqtopics;
            this.sncpAddress = sncpAddress;
            this.sncpAddresses = sncpAddresses;
        }

        public SimpleWebSocketUserAddress(WebSocketUserAddress userAddress) {
            if (userAddress == null) return;
            this.userid = userAddress.userid();
            this.mqtopic = userAddress.mqtopic();
            this.mqtopics = userAddress.mqtopics();
            this.sncpAddress = userAddress.sncpAddress();
            this.sncpAddresses = userAddress.sncpAddresses();
        }

        @Override
        public Serializable userid() {
            return userid;
        }

        @Override
        public String mqtopic() {
            return mqtopic;
        }

        @Override
        public Collection<String> mqtopics() {
            return mqtopics;
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

        public String getMqtopic() {
            return mqtopic;
        }

        public void setMqtopic(String mqtopic) {
            this.mqtopic = mqtopic;
        }

        public Collection<String> getMqtopics() {
            return mqtopics;
        }

        public void setMqtopics(Collection<String> mqtopics) {
            this.mqtopics = mqtopics;
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
