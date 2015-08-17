/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.service.weixin;

import com.wentch.redkale.convert.json.*;
import java.util.*;

/**
 * 微信企业号Service
 *
 * @author zhangjx
 */
public class WeiXinQYMessage {

    private String agentid = "1";

    private String msgtype = "text";

    private Map<String, String> text;

    private String touser = "@all";

    private String toparty;

    private String totag;

    private String safe;

    public WeiXinQYMessage() {
    }

    public WeiXinQYMessage(String agentid, String text) {
        this.agentid = agentid;
        setTextMessage(text);
    }

    public final void setTextMessage(String content) {
        if (text == null) text = new HashMap<>();
        text.put("content", content);
    }

    public String getAgentid() {
        return agentid;
    }

    public void setAgentid(String agentid) {
        this.agentid = agentid;
    }

    public String getMsgtype() {
        return msgtype;
    }

    public void setMsgtype(String msgtype) {
        this.msgtype = msgtype;
    }

    public Map<String, String> getText() {
        return text;
    }

    public void setText(Map<String, String> text) {
        this.text = text;
    }

    public String getTouser() {
        return touser;
    }

    public void setTouser(String touser) {
        this.touser = touser;
    }

    public String getToparty() {
        return toparty;
    }

    public void setToparty(String toparty) {
        this.toparty = toparty;
    }

    public String getTotag() {
        return totag;
    }

    public void setTotag(String totag) {
        this.totag = totag;
    }

    public String getSafe() {
        return safe;
    }

    public void setSafe(String safe) {
        this.safe = safe;
    }

    @Override
    public String toString() {
        return JsonFactory.root().getConvert().convertTo(this);
    }
}
