/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.Objects;
import org.redkale.convert.ConvertColumn;
import org.redkale.convert.json.JsonConvert;

/**
 * 存放用户WS连接的SNCP地址和MQ topic， 当消息使用MQ代理时，topic才会有值
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.1.0
 */
public class WebSocketAddress implements Serializable {

    @ConvertColumn(index = 1)
    protected InetSocketAddress addr;

    @ConvertColumn(index = 2)
    protected String topic;

    public WebSocketAddress() {}

    public WebSocketAddress(String topic, InetSocketAddress addr) {
        this.topic = topic;
        this.addr = addr;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 37 * hash + Objects.hashCode(this.addr);
        hash = 37 * hash + Objects.hashCode(this.topic);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final WebSocketAddress other = (WebSocketAddress) obj;
        return Objects.equals(this.topic, other.topic) && Objects.equals(this.addr, other.addr);
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public InetSocketAddress getAddr() {
        return addr;
    }

    public void setAddr(InetSocketAddress addr) {
        this.addr = addr;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }
}
