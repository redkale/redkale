/*
 *
 */
package org.redkale.source;

import java.util.EventListener;

/**
 * CacheSource订阅频道的消费监听器
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <T> 泛型
 * @since 2.8.0
 */
public interface CacheEventListener<T> extends EventListener {

    public void onMessage(String topic, T message);
}
