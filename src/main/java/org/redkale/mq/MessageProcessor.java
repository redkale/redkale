/*
 *
 */
package org.redkale.mq;

/**
 *
 * @author zhangjx
 */
public interface MessageProcessor {

    public void process(final MessageRecord msg, long time);
}
