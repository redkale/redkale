/*
 *
 */
package org.redkale.source;

/**
 * CacheSource订阅频道的消费监听器
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public interface CacheEventListener<T> {

    public void onMessage(String topic, T message);
}
