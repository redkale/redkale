# 消息队列
&emsp;&emsp; MessageAgent是消息中心抽象接口。
## 配置
```xml
<mq name="mymq" type="kafka">
    <servers value="127.0.0.1:9092"/>
    <consumer autoload="true"/>
</mq>
```

## 消费消息
&emsp;&emsp; 通过```@ResourceConsumer```和```MessageConsumer```接口实现消费
```java
@ResourceConsumer(mq = "mymq", topics = "test_bean_topic")
public class TestMessageConsumer implements MessageConsumer<TestBean> {

    @Override
    public void init(AnyValue config) {
        System.out.println("执行 TestMessageConsumer.init");
    }

    @Override
    public void onMessage(MessageConext context, TestBean message) {
        System.out.println("消费消息, message: " + message);
    }

    @Override
    public void destroy(AnyValue config) {
        System.out.println("执行 TestMessageConsumer.destroy");
    }
}
```

&emsp;&emsp;通过Service里标记```@Messaged```的方法实现消费, 方法只能是```protected```或```public```， 不能是```final```、```static```。
```java
public class TestMessageService extends AbstractService {

    @Messaged(mq = "mymq", topics = "test_bean_topic")
    protected void runMessage(TestBean message) {
        System.out.println("消费消息,  message: " + message);
    }
}
```

&emsp;&emsp;通过```@Component```的Service里标记```@Messaged```的方法实现消费, 方法只能是```public```。
```java
@Component
public final class TestMessageService extends AbstractService {

    @Messaged(mq = "mymq", topics = "test_bean_topic")
    public int runMessage(TestBean message) {
        System.out.println("消费消息,  message: " + message);
        return 0;
    }
}
```

## 生产消息
&emsp;&emsp;通过```@Component```的Service里标记```@Messaged```的方法实现消费, 方法只能是```public```。
```java
@Component
public class TestMessageService extends AbstractService {

    @ResourceProducer(mq = "mymq")
    private MessageProducer producer;

    public void sendMessage() {
        TestBean bean = new TestBean(12345, "this is a message");
        System.out.println("生产消息: " + bean);
        producer.sendMessage("test_bean_topic", bean);
    }
}
```

## Topic管理
&emsp;&emsp;通过```MessageManager```操作topic。
```java
public class TestMessageManager extends AbstractService {

    @Resource(name = "mymq")
    private MessageManager manager;

    // 创建topic
    public void initTopic() {
        manager.createTopic("topic_1", "topic_2").join();
    }

    // 删除topic
    public void deleteTopic() {
        manager.deleteTopic("topic_1", "topic_2").join();
    }

    // 查询topic
    public void printTopic() {
        List<String> topics = manager.queryTopic().join();
    }
}
```
