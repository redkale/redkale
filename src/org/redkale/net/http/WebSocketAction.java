/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.io.Serializable;
import java.util.Map;
import org.redkale.convert.json.JsonConvert;

/**
 * WebSocket.broadcastAction时的参数
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class WebSocketAction implements Serializable {

    protected String action;

    protected Map<String, String> attach;

    public WebSocketAction() {
    }

    public WebSocketAction(String action) {
        this.action = action;
    }

    public WebSocketAction(String action, Map<String, String> attach) {
        this.action = action;
        this.attach = attach;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Map<String, String> getAttach() {
        return attach;
    }

    public void setAttach(Map<String, String> attach) {
        this.attach = attach;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }
}
