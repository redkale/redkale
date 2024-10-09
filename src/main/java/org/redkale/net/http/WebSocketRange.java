/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.io.Serializable;
import java.util.Map;
import org.redkale.convert.ConvertColumn;
import org.redkale.convert.json.JsonConvert;

/**
 * WebSocket.broadcastMessage时的过滤条件
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class WebSocketRange implements Serializable {

    @ConvertColumn(index = 1)
    protected String wskey;

    @ConvertColumn(index = 2)
    protected Map<String, String> attach;

    public WebSocketRange() {}

    public WebSocketRange(String wskey) {
        this.wskey = wskey;
    }

    public WebSocketRange(String wskey, Map<String, String> attach) {
        this.wskey = wskey;
        this.attach = attach;
    }

    public boolean containsAttach(String key) {
        return this.attach != null && this.attach.containsKey(key);
    }

    public String getAttach(String key) {
        return this.attach == null ? null : this.attach.get(key);
    }

    public String getAttach(String key, String defval) {
        return this.attach == null ? defval : this.attach.getOrDefault(key, defval);
    }

    public String getWskey() {
        return wskey;
    }

    public void setWskey(String wskey) {
        this.wskey = wskey;
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
