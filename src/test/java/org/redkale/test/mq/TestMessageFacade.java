/*

*/

package org.redkale.test.mq;

import org.redkale.annotation.Component;
import org.redkale.mq.MessageEvent;
import org.redkale.mq.Messaged;
import org.redkale.service.AbstractService;

/**
 *
 * @author zhangjx
 */
@Component
public class TestMessageFacade extends AbstractService {

    @Messaged(mq = "mymq", topics = "test_bean_topic", group = "group_5")
    public int runMessage5(MessageEvent<TestBean>[] events) {
        for (MessageEvent<TestBean> event : events) {
            System.out.println("TestMessageFacde 消费消息5,  message: " + event.getMessage());
        }
        return 0;
    }
}
