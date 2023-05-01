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
 *
 * @since 2.8.0
 */
@Local
@Component
public interface MessageConsumerListener<T> {

    default void init(AnyValue config) {        
    }

    public void onMessage(String topic, T message);

    default void destroy(AnyValue config) {        
    }
}
