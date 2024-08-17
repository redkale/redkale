/*

*/

package org.redkale.test.mq;

import org.redkale.mq.MessageEvent;
import org.redkale.mq.MessageProducer;
import org.redkale.mq.Messaged;
import org.redkale.mq.ResourceProducer;
import org.redkale.service.AbstractService;
import org.redkale.util.AnyValue;

/**
 *
 * @author zhangjx
 */
public class TestMessageService extends AbstractService {

    @ResourceProducer(mq = "mymq")
    private MessageProducer producer;

    @Override
    public void init(AnyValue config) {
        sendMessage();
    }

    public void sendMessage() {
        TestBean bean = new TestBean(12345, "this is a message");
        System.out.println("生产消息: " + bean);
        producer.sendMessage("test_bean_topic", bean).whenComplete((v, t) -> {
            if (t != null) {
                t.printStackTrace();
            }
            System.out.println("消息发送完成: " + v);
        });
    }

    @Messaged(mq = "mymq", topics = "test_bean_topic", group = "group_2")
    protected void runMessage2(MessageEvent<TestBean>[] events) {
        for (MessageEvent<TestBean> event : events) {
            System.out.println("TestMessageService 消费消息2,  message: " + event.getMessage());
        }
    }

    @Messaged(mq = "mymq", topics = "test_bean_topic", group = "group_3")
    protected void runMessage3(MessageEvent<TestBean>[] events) {
        for (MessageEvent<TestBean> event : events) {
            System.out.println("TestMessageService 消费消息3,  message: " + event.getMessage());
        }
    }

    @Messaged(mq = "mymq", topics = "test_bean_topic", group = "group_4")
    protected int runMessage4(MessageEvent<TestBean>[] events) {
        for (MessageEvent<TestBean> event : events) {
            System.out.println("TestMessageService 消费消息4,  message: " + event.getMessage());
        }
        return 0;
    }
}
