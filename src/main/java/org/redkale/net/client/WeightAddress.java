/*
 *
 */
package org.redkale.net.client;

import java.net.SocketAddress;
import java.util.List;
import java.util.Objects;
import org.redkale.annotation.ConstructorParameters;
import org.redkale.convert.ConvertColumn;
import org.redkale.convert.json.JsonConvert;

/**
 * 带权重的地址
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public class WeightAddress implements Comparable<WeightAddress>, java.io.Serializable {

    @ConvertColumn(index = 1)
    private final SocketAddress address;

    // 权重值，取值范围[0-100]
    @ConvertColumn(index = 2)
    private final int weight;

    @ConstructorParameters({"address", "weight"})
    public WeightAddress(SocketAddress address, int weight) {
        Objects.requireNonNull(address);
        if (weight < 0 || weight > 100) {
            throw new IndexOutOfBoundsException("weight must be [0 - 100]");
        }
        this.address = address;
        this.weight = weight;
    }

    public static SocketAddress[] createAddressArray(List<WeightAddress> ws) {
        int min = 0;
        int size = 0; // 20,35,45去掉最大公约数，数组长度为:4+7+9=20
        for (WeightAddress w : ws) {
            size += w.getWeight();
            if (min == 0 || w.getWeight() < min) {
                min = w.getWeight();
            }
        }
        int divisor = 1; // 最大公约数
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
    public int compareTo(WeightAddress o) {
        return this.weight - (o == null ? 0 : o.weight);
    }

    public SocketAddress getAddress() {
        return address;
    }

    public int getWeight() {
        return this.weight;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }
}
