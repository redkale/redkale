/*

*/

package org.redkale.test.mq;

import org.redkale.annotation.Component;
import org.redkale.mq.Messaged;
import org.redkale.service.AbstractService;

/**
 *
 * @author zhangjx
 */
@Component
public class TestMessageFacade extends AbstractService {

    @Messaged(mq = "mymq", topics = "test_bean_topic", group = "group_5")
    public int runMessage5(TestBean message) {
        System.out.println("TestMessageFacde 消费消息5,  message: " + message);
        return 0;
    }
}
