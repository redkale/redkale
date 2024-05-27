/*
 *
 */
package org.redkale.cluster;

import java.util.concurrent.CompletableFuture;

/**
 * cluster模式下的rpc client
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> message
 * @param <P> result
 * @since 2.8.0
 */
public interface ClusterRpcClient<R, P> {

    /**
     * 发送消息，需要响应
     *
     * @param message 消息体
     * @return 应答消息
     */
    public CompletableFuture<P> sendMessage(final R message);

    /**
     * 发送消息，无需响应
     *
     * @param message 消息体
     * @return 应答
     */
    public CompletableFuture<Void> produceMessage(R message);
}
