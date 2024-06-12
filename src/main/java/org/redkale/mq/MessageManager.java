/*
 *
 */
package org.redkale.mq;

import java.util.List;
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
     * @return 是否成功
     */
    public boolean createTopic(String... topics);

    /**
     * 删除topic，如果不存在则跳过
     *
     * @param topics topic集合
     * @return 是否成功
     */
    public boolean deleteTopic(String... topics);

    /**
     * 查询所有topic
     *
     * @return topic集合
     */
    public abstract List<String> queryTopic();
}
