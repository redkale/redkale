/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.boot;

import java.net.InetSocketAddress;
import java.util.*;

/**
 * 协议地址组合对象, 对应application.xml 中 resources-&#62;group 节点信息
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class GroupInfo {

    protected String name;  //地址

    protected String protocol; //协议 取值范围:  TCP、UDP

    protected String subprotocol; //子协议，预留使用

    protected Set<InetSocketAddress> addrs; //地址列表， 对应 resources-&#62;group-&#62;node节点信息

    public GroupInfo() {
    }

    public GroupInfo(String name, String protocol, String subprotocol, Set<InetSocketAddress> addrs) {
        this.name = name;
        this.protocol = protocol;
        this.subprotocol = subprotocol;
        this.addrs = addrs;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getSubprotocol() {
        return subprotocol;
    }

    public void setSubprotocol(String subprotocol) {
        this.subprotocol = subprotocol;
    }

    public Set<InetSocketAddress> getAddrs() {
        return addrs;
    }

    public Set<InetSocketAddress> copyAddrs() {
        return addrs == null ? null : new LinkedHashSet<>(addrs);
    }

    public void setAddrs(Set<InetSocketAddress> addrs) {
        this.addrs = addrs;
    }

}
