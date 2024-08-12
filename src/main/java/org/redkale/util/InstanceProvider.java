/*
 */
package org.redkale.util;

import java.util.*;

/**
 * 配置源Agent的Provider
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <V> XXXAgent
 * @since 2.8.0
 */
public interface InstanceProvider<V> {

    public boolean acceptsConf(AnyValue config);

    public V createInstance();

    // 值大排前面
    public static <P extends InstanceProvider> List<P> sort(List<P> providers) {
        return Utility.sortPriority(providers);
    }
}
