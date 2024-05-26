/*
 */
package org.redkale.util;

import java.util.*;
import org.redkale.annotation.Priority;

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
        Collections.sort(providers, (a, b) -> {
            Priority p1 = a == null ? null : a.getClass().getAnnotation(Priority.class);
            Priority p2 = b == null ? null : b.getClass().getAnnotation(Priority.class);
            return (p2 == null ? 0 : p2.value()) - (p1 == null ? 0 : p1.value());
        });
        return providers;
    }
}
