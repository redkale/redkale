/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import org.redkale.convert.json.JsonConvert;
import org.redkale.util.Utility;

/**
 * 被net.client模块代替
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
@Deprecated(since = "2.8.0")
public class TransportGroupInfo {

    protected final ReentrantLock lock = new ReentrantLock();

    protected String name; // 地址

    protected String protocol; // 协议 取值范围:  TCP、UDP

    protected Set<InetSocketAddress> addresses; // 地址列表， 对应 resources-&#62;group-&#62;node节点信息

    public TransportGroupInfo() {}

    public TransportGroupInfo(String name, InetSocketAddress... addrs) {
        this(name, "TCP", Utility.ofSet(addrs));
    }

    public TransportGroupInfo(String name, Set<InetSocketAddress> addrs) {
        this(name, "TCP", addrs);
    }

    public TransportGroupInfo(String name, String protocol, InetSocketAddress... addrs) {
        this(name, protocol, Utility.ofSet(addrs));
    }

    public TransportGroupInfo(String name, String protocol, Set<InetSocketAddress> addrs) {
        Objects.requireNonNull(name, "Transport.group.name can not null");
        this.name = name;
        this.protocol = protocol == null ? "TCP" : protocol;
        this.addresses = addrs;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        Objects.requireNonNull(name, "Transport.group.name can not null");
        this.name = name;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol == null ? "TCP" : protocol;
    }

    public Set<InetSocketAddress> getAddresses() {
        return addresses;
    }

    public Set<InetSocketAddress> copyAddresses() {
        return addresses == null ? null : new LinkedHashSet<>(addresses);
    }

    public void setAddresses(Set<InetSocketAddress> addresses) {
        this.addresses = addresses;
    }

    public boolean containsAddress(InetSocketAddress addr) {
        lock.lock();
        try {
            if (this.addresses == null) {
                return false;
            }
            return this.addresses.contains(addr);
        } finally {
            lock.unlock();
        }
    }

    public void removeAddress(InetSocketAddress addr) {
        if (addr == null) {
            return;
        }
        lock.lock();
        try {
            if (this.addresses == null) {
                return;
            }
            this.addresses.remove(addr);
        } finally {
            lock.unlock();
        }
    }

    public void putAddress(InetSocketAddress addr) {
        if (addr == null) {
            return;
        }
        lock.lock();
        try {
            if (this.addresses == null) {
                this.addresses = new HashSet<>();
            }
            this.addresses.add(addr);
        } finally {
            lock.unlock();
        }
    }

    public void putAddress(Set<InetSocketAddress> addrs) {
        if (addrs == null) {
            return;
        }
        lock.lock();
        try {
            if (this.addresses == null) {
                this.addresses = new HashSet<>();
            }
            this.addresses.addAll(addrs);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }
}
