/*

*/

package org.redkale.test.mq;

import org.redkale.mq.MessageConext;
import org.redkale.mq.MessageConsumer;
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
    public void onMessage(MessageConext context, TestBean message) {
        System.out.println("TestMessageConsumer消费消息, context: " + context + ", message: " + message);
    }

    @Override
    public void destroy(AnyValue config) {
        System.out.println("执行 TestMessageConsumer.destroy");
    }
}
