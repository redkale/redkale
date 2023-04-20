/*
 *
 */
package org.redkale.mq;

import org.redkale.annotation.Component;
import org.redkale.service.*;

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
public interface MessageConsumerListener<T> extends Service {

    public void onMessage(String topic, T message);
}
