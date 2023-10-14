/*
 *
 */
package org.redkale.mq;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 响应结果
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
public class MessageRespProcessor implements MessageProcessor {

    private final MessageClient messageClient;

    public MessageRespProcessor(MessageClient messageClient) {
        this.messageClient = messageClient;
    }

    @Override
    public void process(final MessageRecord msg, long time) {
        long now = System.currentTimeMillis();
        Logger logger = messageClient.logger;
        final boolean finest = logger.isLoggable(Level.FINEST);
        MessageRespFuture resp = messageClient.respQueue.remove(msg.getSeqid());
        if (resp == null) {
            logger.log(Level.WARNING, getClass().getSimpleName() + " process " + msg + " error， not found MessageRespFuture");
            return;
        }
        if (resp.scheduledFuture != null) {
            resp.scheduledFuture.cancel(true);
        }
        final long deplay = now - msg.createTime;
        if (finest) {
            logger.log(Level.FINEST, getClass().getSimpleName() + ".MessageRespFuture.receive (mq.delay = " + deplay + "ms, mq.seqid = " + msg.getSeqid() + ")");
        }
        messageClient.getMessageAgent().execute(() -> {
            resp.future.complete(msg);
            long comems = System.currentTimeMillis() - now;
            if ((deplay > 1000 || comems > 1000) && logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, getClass().getSimpleName() + ".MessageRespFuture.complete (mq.delay-slower = " + deplay + "ms, mq.complete-slower = " + comems + "ms) mqresp.msg: " + msg);
            } else if ((deplay > 50 || comems > 50) && logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, getClass().getSimpleName() + ".MessageRespFuture.complete (mq.delay-slowly = " + deplay + "ms, mq.complete-slowly = " + comems + "ms) mqresp.msg: " + msg);
            } else if (finest) {
                logger.log(Level.FINEST, getClass().getSimpleName() + ".MessageRespFuture.complete (mq.delay-normal = " + deplay + "ms, mq.complete-normal = " + comems + "ms) mqresp.msg: " + msg);
            }
        });
    }
}
