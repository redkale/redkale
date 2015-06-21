/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.test.service;

import com.wentch.redkale.net.http.WebSocketServlet;
import com.wentch.redkale.net.http.WebSocket;
import java.util.Map;
import javax.annotation.Resource;

/**
 *
 * @author zhangjx
 */
public class IMServlet extends WebSocketServlet {

    @Resource(name = "^IMNODE.+$")
    private Map<String, IMService> nodemaps;

    @Override
    protected WebSocket createWebSocket() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
