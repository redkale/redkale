/*
 *
 */
package org.redkale.mq;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.redkale.inject.Resourcable;

/**
 * MQ消息管理器
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public interface MessageManager extends Resourcable {

    /**
     * 创建topic
     *
     * @param topics topic集合
     * @return 是否完成
     */
    public CompletableFuture<Void> createTopic(String... topics);

    /**
     * 删除topic，如果不存在则跳过
     *
     * @param topics topic集合
     * @return 是否完成
     */
    public CompletableFuture<Void> deleteTopic(String... topics);

    /**
     * 查询所有topic
     *
     * @return topic集合
     */
    public abstract CompletableFuture<List<String>> queryTopic();
}
