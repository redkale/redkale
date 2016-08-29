/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.boot;

import java.net.InetSocketAddress;
import java.util.*;

/**
 *
 * <p>
 * 详情见: http://redkale.org
 *
 * @author zhangjx
 */
public class GroupInfo {

    protected String name;

    protected String protocol;

    protected String kind;

    protected Set<InetSocketAddress> addrs;

    public GroupInfo() {
    }

    public GroupInfo(String name, String protocol, String kind, Set<InetSocketAddress> addrs) {
        this.name = name;
        this.protocol = protocol;
        this.kind = kind;
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

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
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
