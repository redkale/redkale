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

    //
    public boolean createTopic(String... topics);

    // 删除topic，如果不存在则跳过
    public boolean deleteTopic(String... topics);

    // 查询所有topic
    public abstract List<String> queryTopic();
}
