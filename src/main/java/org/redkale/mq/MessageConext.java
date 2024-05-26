/*
 *
 */
package org.redkale.mq;

import java.util.Objects;
import org.redkale.convert.ConvertColumn;
import org.redkale.convert.json.JsonConvert;

/**
 * MessageConsumer回调的上下文
 *
 * <p>详情见: https://redkale.org
 *
 * @see org.redkale.mq.MessageConsumer
 * @author zhangjx
 * @since 2.8.0
 */
public class MessageConext {

    @ConvertColumn(index = 1)
    protected String topic;

    @ConvertColumn(index = 2)
    protected Integer partition;

    public MessageConext(String topic, Integer partition) {
        this.topic = topic;
        this.partition = partition;
    }

    public String getTopic() {
        return topic;
    }

    public Integer getPartition() {
        return partition;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.topic, this.partition);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final MessageConext other = (MessageConext) obj;
        return Objects.equals(this.topic, other.topic) && Objects.equals(this.partition, other.partition);
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }
}
