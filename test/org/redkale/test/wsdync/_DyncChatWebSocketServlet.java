/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.wsdync;

import java.io.Serializable;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.annotation.Resource;
import org.redkale.net.http.*;
import org.redkale.test.ws.ChatMessage;
import org.redkale.test.ws.ChatService;
import org.redkale.test.ws.ChatWebSocket;

/**
 *
 * @author zhangjx
 */
//@WebServlet("/ws/chat")
public final class _DyncChatWebSocketServlet extends WebSocketServlet {

    @Resource
    private ChatService service;

    public _DyncChatWebSocketServlet() {
        super();
        this.messageTextType = _DyncChatWebSocketMessage.class;
    }

    @Override
    protected <G extends Serializable, T> WebSocket<G, T> createWebSocket() {
        return (WebSocket) new _DyncChatWebSocket(service);
    }

    protected BiConsumer<WebSocket, Object> createRestOnMessageConsumer() {
        return new RestOnMessageConsumer();
    }

    public static class _DyncChatWebSocket extends ChatWebSocket {

        public _DyncChatWebSocket(ChatService service) {
            super();
            this.service = service;
        }
    }

    public static class _DyncChatWebSocketMessage {

        public _DyncChatWebSocketMessage_sendmessagee sendmessage;

        public _DyncChatWebSocketMessage_joinroom joinroom;

    }

    public static class _DyncChatWebSocketMessage_sendmessagee {

        public ChatMessage message;

        public Map<String, String> extmap;

    }

    public static class _DyncChatWebSocketMessage_joinroom {

        public int roomid;

    }

    public static class RestOnMessageConsumer implements BiConsumer<WebSocket, Object> {

        @Override
        public void accept(WebSocket websocket0, Object message0) {
            ChatWebSocket websocket = (ChatWebSocket) websocket0;
            _DyncChatWebSocketMessage message = (_DyncChatWebSocketMessage) message0;
            if (message.sendmessage != null) {
                websocket.onChatMessage(message.sendmessage.message, message.sendmessage.extmap);
            } else if (message.sendmessage != null) {
                websocket.onJoinRoom(message.joinroom.roomid);
            }
        }

    }
}
