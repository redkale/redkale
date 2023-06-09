/*
 *
 */
package org.redkale.mq;

import org.redkale.annotation.Component;
import org.redkale.service.Local;
import org.redkale.util.AnyValue;

/**
 * MQ资源注解
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <T> 泛型
 *
 * @since 2.8.0
 */
@Local
@Component
public interface MessageConsumer<T> {

    default void init(AnyValue config) {
    }

    public void onMessage(String topic, T[] messages);

    default void destroy(AnyValue config) {
    }
}
