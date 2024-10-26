/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.wsdync;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.function.BiConsumer;
import org.redkale.annotation.Resource;
import org.redkale.convert.ConvertDisabled;
import org.redkale.convert.json.JsonConvert;
import org.redkale.net.http.*;
import org.redkale.test.rest.UserService;
import org.redkale.test.ws.ChatMessage;
import org.redkale.test.ws.ChatService;
import org.redkale.test.ws.ChatWebSocket;

/** @author zhangjx */
// @WebServlet("/ws/chat")
public final class _DyncChatWebSocketServlet extends WebSocketServlet {

    @Resource
    private ChatService _redkale_resource_0;

    @Resource
    private UserService _redkale_resource_1;

    public static Map<String, Annotation[]> _redkale_annotations;

    public _DyncChatWebSocketServlet() {
        super();
        this.messageRestType = _DyncChatWebSocketMessage.class;
    }

    @Override
    protected <G extends Serializable> WebSocket<G> createWebSocket() {
        return (WebSocket) new _DyncChatWebSocket(_redkale_resource_0, _redkale_resource_1);
    }

    @Override
    protected BiConsumer<WebSocket, Object> createRestOnMessageConsumer() {
        return new _DynRestOnMessageConsumer();
    }

    public static class _DyncChatWebSocket extends ChatWebSocket {

        public _DyncChatWebSocket(ChatService service, UserService userService) {
            super();
            this.service = service;
            this.userService = userService;
        }
    }

    public static class _DyncChatWebSocketMessage implements WebSocketParam, Runnable {

        public _DyncChatWebSocketMessage_sendmessagee_00 sendmessage;

        public _DyncChatWebSocketMessage_joinroom_01 joinroom;

        @ConvertDisabled
        public _DyncChatWebSocket _redkale_websocket;

        public int roomid;

        public String name;

        @Override
        public String[] getNames() {
            return new String[] {"roomid", "name"};
        }

        @Override
        public <T> T getValue(String name) {
            if ("roomid".equals(name)) return (T) (Integer) roomid;
            if ("name".equals(name)) return (T) (String) name;
            return null;
        }

        @Override
        public Annotation[] getAnnotations() {
            Annotation[] annotations = _redkale_annotations.get(
                    "org/redkale/test/wsdync/_DyncChatWebSocketServlet$_DyncChatWebSocketMessage");
            if (annotations == null) return new Annotation[0];
            return Arrays.copyOf(annotations, annotations.length);
        }

        public void execute(_DyncChatWebSocket websocket) {
            this._redkale_websocket = websocket;
            websocket.preOnMessage("*", this, this);
        }

        @Override
        public void run() {
            _redkale_websocket.other(this.roomid, this.name);
        }

        @Override
        public String toString() {
            return JsonConvert.root().convertTo(this);
        }
    }

    public static class _DyncChatWebSocketMessage_sendmessagee_00 implements WebSocketParam, Runnable {

        public ChatMessage message;

        public Map<String, String> extmap;

        @ConvertDisabled
        public _DyncChatWebSocket _redkale_websocket;

        @Override
        public String[] getNames() {
            return new String[] {"message", "extmap"};
        }

        @Override
        public <T> T getValue(String name) {
            if ("message".equals(name)) return (T) message;
            if ("extmap".equals(name)) return (T) extmap;
            return null;
        }

        @Override
        public Annotation[] getAnnotations() {
            Annotation[] annotations = _redkale_annotations.get(
                    "org/redkale/test/wsdync/_DyncChatWebSocketServlet$_DyncChatWebSocketMessage_sendmessagee_00");
            if (annotations == null) return new Annotation[0];
            return Arrays.copyOf(annotations, annotations.length);
        }

        public void execute(_DyncChatWebSocket websocket) {
            this._redkale_websocket = websocket;
            websocket.preOnMessage("sendmessage", this, this);
        }

        @Override
        public void run() {
            _redkale_websocket.onChatMessage(this.message, this.extmap);
        }

        @Override
        public String toString() {
            return JsonConvert.root().convertTo(this);
        }
    }

    public static class _DyncChatWebSocketMessage_joinroom_01 implements WebSocketParam, Runnable {

        public int roomid;

        @ConvertDisabled
        public _DyncChatWebSocket _redkale_websocket;

        @Override
        public String[] getNames() {
            return new String[] {"roomid"};
        }

        @Override
        public <T> T getValue(String name) {
            if ("roomid".equals(name)) return (T) (Integer) roomid;
            return null;
        }

        @Override
        public Annotation[] getAnnotations() {
            Annotation[] annotations = _redkale_annotations.get(
                    "org/redkale/test/wsdync/_DyncChatWebSocketServlet$_DyncChatWebSocketMessage_joinroom_01");
            if (annotations == null) return new Annotation[0];
            return Arrays.copyOf(annotations, annotations.length);
        }

        public void execute(_DyncChatWebSocket websocket) {
            this._redkale_websocket = websocket;
            websocket.preOnMessage("joinroom", this, this);
        }

        @Override
        public void run() {
            _redkale_websocket.onJoinRoom(this.roomid);
        }

        @Override
        public String toString() {
            return JsonConvert.root().convertTo(this);
        }
    }

    public static class _DynRestOnMessageConsumer implements BiConsumer<WebSocket, Object> {

        @Override
        public void accept(WebSocket websocket0, Object message0) {
            _DyncChatWebSocket websocket = (_DyncChatWebSocket) websocket0;
            _DyncChatWebSocketMessage message = (_DyncChatWebSocketMessage) message0;
            if (message.sendmessage != null) {
                message.sendmessage.execute(websocket);
                return;
            }
            if (message.joinroom != null) {
                message.joinroom.execute(websocket);
                return;
            }
            message.execute(websocket);
        }
    }
}
