/*

*/

package org.redkale.test.mq;

import org.redkale.annotation.AutoLoad;
import org.redkale.convert.ConvertType;
import org.redkale.mq.MessageConsumer;
import org.redkale.mq.MessageEvent;
import org.redkale.mq.ResourceConsumer;

@AutoLoad(false)
public class _DynLocalTestMessageService extends TestMessageService {
    public _DynLocalTestMessageService() {
        super();
        System.out.println("哈哈哈哈哈");
    }

    @AutoLoad(false)
    @ResourceConsumer(
            topics = {"test_bean_topic"},
            required = true,
            group = "group_4",
            convertType = ConvertType.JSON,
            mq = "mymq")
    public static class DynMessageConsumerx1 implements MessageConsumer<TestBean> {
        private _DynLocalTestMessageService service;

        public DynMessageConsumerx1(_DynLocalTestMessageService service) {
            this.service = service;
        }

        public void onMessage(MessageEvent<TestBean>[] events) {
            this.service.runMessage4(events);
        }
    }

    @AutoLoad(false)
    @ResourceConsumer(
            topics = {"test_bean_topic"},
            required = true,
            group = "group_3",
            convertType = ConvertType.JSON,
            mq = "mymq")
    public static class DynMessageConsumerx2 implements MessageConsumer<TestBean> {
        private _DynLocalTestMessageService service;

        public DynMessageConsumerx2(_DynLocalTestMessageService service) {
            this.service = service;
        }

        public void onMessage(MessageEvent<TestBean>[] events) {
            this.service.runMessage3(events);
        }
    }

    @AutoLoad(false)
    @ResourceConsumer(
            topics = {"test_bean_topic"},
            required = true,
            group = "group_2",
            convertType = ConvertType.JSON,
            mq = "mymq")
    public static class DynMessageConsumerx3 implements MessageConsumer<TestBean> {
        private _DynLocalTestMessageService service;

        public DynMessageConsumerx3(_DynLocalTestMessageService service) {
            this.service = service;
        }

        public void onMessage(MessageEvent<TestBean>[] events) {
            this.service.runMessage2(events);
        }
    }
}
