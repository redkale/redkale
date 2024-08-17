/*

*/

package org.redkale.test.mq;

import org.redkale.mq.MessageConsumer;
import org.redkale.mq.MessageEvent;
import org.redkale.mq.ResourceConsumer;
import org.redkale.util.AnyValue;

/**
 *
 * @author zhangjx
 */
@ResourceConsumer(mq = "mymq", topics = "test_bean_topic")
public class TestMessageConsumer implements MessageConsumer<TestBean> {

    @Override
    public void init(AnyValue config) {
        System.out.println("执行 TestMessageConsumer.init");
    }

    @Override
    public void onMessage(MessageEvent<TestBean>[] events) {
        for (MessageEvent<TestBean> event : events) {
            System.out.println("TestMessageConsumer消费消息, message: " + event.getMessage());
        }
    }

    @Override
    public void destroy(AnyValue config) {
        System.out.println("执行 TestMessageConsumer.destroy");
    }
}
