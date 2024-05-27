/*
 *
 */
package org.redkale.net.client;

/**
 * 接收消息事件
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public abstract class ClientMessageListener {

    public abstract void onMessage(ClientConnection conn, ClientResponse resp);

    public void onClose(ClientConnection conn) {}
}
