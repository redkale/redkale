/*
 *
 */
package org.redkale.net.sncp;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import org.redkale.convert.ConvertColumn;
import org.redkale.convert.json.JsonConvert;
import org.redkale.util.Utility;

/**
 * 协议地址组合对象, 对应application.xml 中 resources-&#62;group 节点信息
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public class SncpRpcGroup {

    protected final ReentrantLock lock = new ReentrantLock();

    // 地址
    @ConvertColumn(index = 1)
    protected String name;

    // 协议 取值范围:  TCP、UDP
    @ConvertColumn(index = 2)
    protected String protocol;

    // 地址列表， 对应 resources-&#62;group-&#62;node节点信息
    @ConvertColumn(index = 3)
    protected Set<InetSocketAddress> addresses;

    public SncpRpcGroup() {}

    public SncpRpcGroup(String name, InetSocketAddress... addrs) {
        this(name, "TCP", Utility.ofSet(addrs));
    }

    public SncpRpcGroup(String name, Set<InetSocketAddress> addrs) {
        this(name, "TCP", addrs);
    }

    public SncpRpcGroup(String name, String protocol, InetSocketAddress... addrs) {
        this(name, protocol, Utility.ofSet(addrs));
    }

    public SncpRpcGroup(String name, String protocol, Set<InetSocketAddress> addresses) {
        Objects.requireNonNull(name, "rpc.group.name can not null");
        this.name = name;
        this.protocol = protocol == null ? "TCP" : protocol;
        this.addresses = addresses;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        Objects.requireNonNull(name, "rpc.group.name can not null");
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
        lock.lock();
        try {
            return addresses == null ? null : new LinkedHashSet<>(addresses);
        } finally {
            lock.unlock();
        }
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
                this.addresses = new LinkedHashSet<>();
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
                this.addresses = new LinkedHashSet<>();
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
