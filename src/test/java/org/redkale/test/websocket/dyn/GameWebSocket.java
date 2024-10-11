/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.test.websocket.dyn;

import java.util.concurrent.CompletableFuture;
import org.redkale.net.http.HttpRequest;
import org.redkale.net.http.RestOnMessage;
import org.redkale.net.http.RestWebSocket;
import org.redkale.net.http.WebSocket;

/**
 *
 * @author zhangjx
 */
@RestWebSocket(name = "wstest", catalog = "ws", wsmaxconns = 100, comment = "WebSocket服务", repair = false)
public class GameWebSocket extends WebSocket<Long, Object> {

    @Override
    protected CompletableFuture<String> onOpen(final HttpRequest request) {
        return CompletableFuture.completedFuture("uuid001");
    }

    @Override
    protected CompletableFuture<Long> createUserid() {
        return CompletableFuture.completedFuture(111222L);
    }

    @RestOnMessage(name = "joinRoom", comment = "加入房间")
    public void joinRoom(GameRoomBean bean) {
        System.out.println("加入房间-参数: " + bean);
    }

    @RestOnMessage(name = "enterGame", comment = "加入游戏")
    public void enterGame(String game, long time) {
        System.out.println("加入游戏-参数: " + game + ", " + time);
    }
}
