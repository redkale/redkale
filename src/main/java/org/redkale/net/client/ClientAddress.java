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
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.7.0
 */
public class ClientAddress implements java.io.Serializable {

    private SocketAddress[] addresses;

    public ClientAddress() {
    }

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
        this.addresses = createAddressArray(addrs);
    }

    public void updateAddress(List<WeightAddress> addrs) {
        if (addrs == null || addrs.isEmpty()) {
            throw new NullPointerException("addresses is empty");
        }
        this.addresses = createAddressArray(addrs);
    }

    public SocketAddress randomAddress() {
        SocketAddress[] addrs = this.addresses;
        if (addrs.length == 1) {
            return addrs[0];
        }
        return addrs[ThreadLocalRandom.current().nextInt(addrs.length)];
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

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }

}
