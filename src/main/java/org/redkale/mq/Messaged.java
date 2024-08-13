/*
 *
 */
package org.redkale.mq;

import java.lang.annotation.Documented;
import static java.lang.annotation.ElementType.METHOD;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;
import org.redkale.convert.ConvertType;
import org.redkale.service.LoadMode;

/**
 * MQ资源注解, 只能标记在Service类方法上, 方法会被框架动态生成{@link org.redkale.mq.MessageConsumer}对象供内部调用  <br>
 * 1、方法必须是protected/public   <br>
 * 2、方法不能是final/static  <br>
 * 3、方法的参数只能是1个或者2个， 1个参数视为Message数据类型，2个参数则另一个必须是{@link org.redkale.mq.MessageConext} <br>
 *
 * <blockquote>
 * <pre>
 * public class MyMessageService extends AbstractService {
 *
 *    &#64;Messaged(mq="defaultmq", topics={"test-topic"})
 *    protected void onMessage(Record msg) {
 *        //打印msg
 *    }
 *
 *    &#64;Messaged(topics={"test-topic2"})
 *    protected void onMessage2(MessageConext context, Record msg) {
 *        //打印msg
 *    }
 *
 *    &#64;Messaged(topics={"test-topic3"})
 *    public void onMessage3(Record msg, MessageConext context) {
 *        //打印msg
 *    }
 * }
 * </pre>
 * </blockquote>
 *
 * <p>详情见: https://redkale.org
 *
 * @see org.redkale.mq.ResourceConsumer
 * @see org.redkale.mq.MessageConext
 * @author zhangjx
 * @since 2.8.0
 */
@Documented
@Target({METHOD})
@Retention(RUNTIME)
public @interface Messaged {

    /**
     * {@link org.redkale.mq.spi.MessageAgent}对象对应名称
     *
     * @return MQ名称
     */
    String mq() default "";

    /**
     * MQ客户端分组名称
     *
     * @return 组名称
     */
    String group() default "";

    /**
     * 是否必须要加载，为ture时若mq()值对应{@link org.redkale.mq.spi.MessageAgent}对象不存在的情况下会抛异常
     *
     * @return 是否必须要加载
     */
    boolean required() default true;

    /**
     * 监听的topic
     *
     * @return  topic
     */
    String[] topics();

    /**
     * 消息序列化类型
     *
     * @return  序列化类型
     */
    ConvertType convertType() default ConvertType.JSON;

    /**
     * Service加载模式
     *
     * @return 模式
     */
    LoadMode mode() default LoadMode.LOCAL;
}
