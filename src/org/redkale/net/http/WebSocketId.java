/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.io.Serializable;
import org.redkale.convert.json.JsonConvert;

/**
 *
 * @author zhangjx
 */
public class WebSocketId {
    
    protected long websocketid;
    
    protected Serializable groupid;
    
    protected Serializable sessionid;
    
    protected String remoteAddr;
    
    public WebSocketId(long websocketid, Serializable groupid, Serializable sessionid, String remoteAddr) {
        this.websocketid = websocketid;
        this.groupid = groupid;
        this.sessionid = sessionid;
        this.remoteAddr = remoteAddr;
    }
    
    public long getWebsocketid() {
        return websocketid;
    }
    
    public Serializable getGroupid() {
        return groupid;
    }
    
    public Serializable getSessionid() {
        return sessionid;
    }
    
    public String getRemoteAddr() {
        return remoteAddr;
    }
    
    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this); 
    }
}
