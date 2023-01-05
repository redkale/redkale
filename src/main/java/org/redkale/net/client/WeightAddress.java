/*
 *
 */
package org.redkale.net.client;

import java.net.SocketAddress;
import java.util.Objects;
import org.redkale.annotation.ConstructorParameters;
import org.redkale.convert.ConvertColumn;
import org.redkale.convert.json.JsonConvert;

/**
 * 带权重的地址
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
public class WeightAddress implements Comparable<WeightAddress>, java.io.Serializable {

    @ConvertColumn(index = 1)
    private final SocketAddress address;

    //权重值，取值范围[0-100]
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
