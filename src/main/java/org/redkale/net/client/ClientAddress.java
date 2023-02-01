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
 *
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

    public void updateAddress(SocketAddress addr, List<WeightAddress> addrs) {
        if (addr == null && (addrs == null || addrs.isEmpty())) {
            throw new NullPointerException("address is empty");
        }
        setWeights(addrs);
        setAddress(addr);
    }

    public void checkValid() {
        if (address == null && (weights == null || weights.isEmpty())) {
            throw new NullPointerException("address is empty");
        }
    }

    public CompletableFuture<AsyncConnection> createClient(final boolean tcp, final AsyncGroup group, int readTimeoutSeconds, int writeTimeoutSeconds) {
        SocketAddress addr = address;
        if (addr == null) {
            SocketAddress[] addrs = this.addresses;
            if (addrs == null) {
                this.addresses = createAddressArray(this.weights);
                addrs = this.addresses;
            }
            addr = addrs[ThreadLocalRandom.current().nextInt(addrs.length)];
        }
        return group.createClient(tcp, addr, readTimeoutSeconds, writeTimeoutSeconds);
    }

    private static SocketAddress[] createAddressArray(List<WeightAddress> ws) {
        int min = 0;
        int size = 0; //20,35,45去掉最大公约数，数组长度为:4+7+9=20
        for (WeightAddress w : ws) {
            size += w.getWeight();
            if (min == 0 || w.getWeight() < min) {
                min = w.getWeight();
            }
        }
        int divisor = 1; //最大公约数
        for (int i = 2; i <= min; i++) {
            boolean all = true;
            for (WeightAddress w : ws) {
                if (w.getWeight() % i > 0) {
                    all = false;
                    break;
                }
            }
            if (all) {
                divisor = i;
            }
        }
        size /= divisor;
        SocketAddress[] newAddrs = new SocketAddress[size];
        int index = -1;
        for (int i = 0; i < ws.size(); i++) {
            WeightAddress w = ws.get(i);
            int z = w.getWeight() / divisor;
            for (int j = 0; j < z; j++) {
                newAddrs[++index] = w.getAddress();
            }
        }
        return newAddrs;
    }

    public SocketAddress getAddress() {
        return address;
    }

    public void setAddress(SocketAddress address) {
        this.address = address;
    }

    public List<WeightAddress> getWeights() {
        return weights;
    }

    public void setWeights(List<WeightAddress> weights) {
        this.weights = weights == null ? null : new ArrayList<>(weights);
        this.addresses = null;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }

}
