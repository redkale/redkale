/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.test.ws;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redkale.net.http.Rest;
import org.redkale.net.http.WebSocketServlet;
import org.redkale.util.RedkaleClassLoader;

/**
 *
 * @author zhangjx
 */
public class WebSocketTest {
    public static void main(String[] args) throws Throwable {
        WebSocketTest test = new WebSocketTest();
        test.run1();
    }

    @Test
    public void run1() throws Exception {
        RedkaleClassLoader classLoader = RedkaleClassLoader.currentClassLoader();
        WebSocketServlet ws = Rest.createRestWebSocketServlet(classLoader, ChatWebSocket.class, null);
        Assertions.assertTrue(ws != null);
    }
}
