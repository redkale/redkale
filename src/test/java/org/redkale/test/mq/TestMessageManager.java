/*

*/

package org.redkale.test.mq;

import java.util.List;
import org.redkale.annotation.Resource;
import org.redkale.mq.MessageManager;
import org.redkale.service.AbstractService;

/**
 *
 * @author zhangjx
 */
public class TestMessageManager extends AbstractService {

    @Resource(name = "mymq")
    private MessageManager manager;

    // 创建topic
    public void initTopic() {
        manager.createTopic("topic_1", "topic_2").join();
    }
    
    // 创建topic
    public void deleteTopic() {
        manager.deleteTopic("topic_1", "topic_2").join();
    }
    
    // 查询topic
    public void printTopic() {
        List<String> topics = manager.queryTopic().join();
    }
}
