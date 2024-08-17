/*

*/

package org.redkale.mq;

import org.redkale.convert.ConvertColumn;
import org.redkale.convert.json.JsonConvert;

/**
 * MessageConsumer的消息实体类
 *
 * <p>详情见: https://redkale.org
 *
 * @see org.redkale.mq.MessageConsumer
 * @author zhangjx
 * @since 2.8.0
 * @param <T> T
 */
public final class MessageEvent<T> {

    @ConvertColumn(index = 1)
    protected String topic;

    @ConvertColumn(index = 2)
    protected Integer partition;

    @ConvertColumn(index = 3)
    protected String traceid;

    @ConvertColumn(index = 4)
    protected T message;

    public MessageEvent() {
        //
    }

    public MessageEvent(String topic, Integer partition, String traceid, T message) {
        this.topic = topic;
        this.partition = partition;
        this.traceid = traceid;
        this.message = message;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public Integer getPartition() {
        return partition;
    }

    public void setPartition(Integer partition) {
        this.partition = partition;
    }

    public String getTraceid() {
        return traceid;
    }

    public void setTraceid(String traceid) {
        this.traceid = traceid;
    }

    public T getMessage() {
        return message;
    }

    public void setMessage(T message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }
}
