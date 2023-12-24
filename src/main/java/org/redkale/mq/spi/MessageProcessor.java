/*
 *
 */
package org.redkale.mq.spi;

/**
 *
 * @author zhangjx
 */
public interface MessageProcessor {

    public void process(final MessageRecord msg, long time);
}
