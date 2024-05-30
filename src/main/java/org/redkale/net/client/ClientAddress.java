/*
 */
package org.redkale.net.client;

import java.net.SocketAddress;
import java.util.*;
import java.util.concurrent.*;
import org.redkale.convert.json.JsonConvert;

/**
 * Client连接地址
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.7.0
 */
public class ClientAddress implements java.io.Serializable {

    private SocketAddress[] addresses;

    public ClientAddress() {}

    public ClientAddress(SocketAddress... addresses) {
        if (addresses == null || addresses.length == 0) {
            throw new NullPointerException("addresses is empty");
        }
        for (SocketAddress addr : addresses) {
            Objects.requireNonNull(addr);
        }
        this.addresses = addresses;
    }

    public ClientAddress(List<WeightAddress> addrs) {
        if (addrs == null || addrs.isEmpty()) {
            throw new NullPointerException("addresses is empty");
        }
        this.addresses = WeightAddress.createAddressArray(addrs);
    }

    void updateAddress(List<SocketAddress> addrs) {
        if (addrs == null || addrs.isEmpty()) {
            throw new NullPointerException("addresses is empty");
        }
        for (SocketAddress addr : addrs) {
            Objects.requireNonNull(addr);
        }
        this.addresses = addrs.toArray(new SocketAddress[addrs.size()]);
    }

    public SocketAddress randomAddress() {
        SocketAddress[] addrs = this.addresses;
        if (addrs.length == 1) {
            return addrs[0];
        }
        return addrs[ThreadLocalRandom.current().nextInt(addrs.length)];
    }

    public Set<SocketAddress> getAddresses() {
        return Set.of(addresses);
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }
}
