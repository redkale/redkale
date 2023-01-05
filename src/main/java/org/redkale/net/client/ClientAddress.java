/*
 */
package org.redkale.net.client;

import java.net.SocketAddress;
import java.util.*;
import java.util.concurrent.*;
import org.redkale.convert.json.JsonConvert;
import org.redkale.net.*;

/**
 * Client连接地址
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.7.0
 */
public class ClientAddress implements java.io.Serializable {

    private SocketAddress address;

    private List<WeightAddress> weights;

    private SocketAddress[] addresses;

    public ClientAddress() {
    }

    public ClientAddress(SocketAddress address) {
        this.address = address;
    }

    public ClientAddress(List<WeightAddress> addrs) {
        this.weights = new ArrayList<>(addrs);
    }

    public ClientAddress addWeightAddress(WeightAddress... addrs) {
        if (this.weights == null) {
            this.weights = new ArrayList<>();
        }
        for (WeightAddress addr : addrs) {
            this.weights.add(addr);
        }
        return this;
    }

    public void updateWeightAddress(List<WeightAddress> addrs) {
        this.weights = new ArrayList<>(addrs);
        this.addresses = null;
    }

    public CompletableFuture<AsyncConnection> createClient(final boolean tcp, final AsyncGroup group, int readTimeoutSeconds, int writeTimeoutSeconds) {
        SocketAddress addr = address;
        if (addr == null) {
            SocketAddress[] addrs = this.addresses;
            if (addrs == null) {
                synchronized (this) {
                    if (this.addresses == null) {
                        int size = 0;
                        List<WeightAddress> ws = this.weights;
                        for (WeightAddress w : ws) {
                            size += w.getWeight();
                        }
                        SocketAddress[] newAddrs = new SocketAddress[size];
                        int index = -1;
                        for (int i = 0; i < ws.size(); i++) {
                            WeightAddress w = ws.get(i);
                            for (int j = 0; j < w.getWeight(); j++) {
                                newAddrs[++index] = w.getAddress();
                            }
                        }
                        addrs = newAddrs;
                        this.addresses = newAddrs;
                    }
                }
            }
            addr = addrs[ThreadLocalRandom.current().nextInt(addrs.length)];
        }
        return group.createClient(tcp, addr, readTimeoutSeconds, writeTimeoutSeconds);
    }

    public SocketAddress getAddress() {
        return address;
    }

    public void setAddress(SocketAddress address) {
        this.address = address;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }

}
