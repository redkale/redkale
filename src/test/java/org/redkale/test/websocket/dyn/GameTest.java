/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.test.websocket.dyn;

import org.junit.jupiter.api.Test;
import org.redkale.net.http.Rest;
import org.redkale.net.http.WebSocketServlet;
import org.redkale.util.RedkaleClassLoader;

/**
 *
 * @author zhangjx
 */
public class GameTest {

    public static void main(String[] args) throws Throwable {
        GameTest test = new GameTest();
        test.run();
    }

    @Test
    public void run() throws Exception {
        RedkaleClassLoader classLoader = RedkaleClassLoader.getRedkaleClassLoader();
        WebSocketServlet servlet = Rest.createRestWebSocketServlet(classLoader, GameWebSocket.class, null);
    }
}
