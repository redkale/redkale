/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.service.weixin;

import org.redkale.convert.json.JsonFactory;
import java.util.*;
import java.util.function.*;

/**
 * 微信企业号Service
 *
 * @see http://www.redkale.org
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

    private Supplier<String> contentSupplier;

    public WeiXinQYMessage() {
    }

    public WeiXinQYMessage(String agentid, String text) {
        this.agentid = agentid;
        setTextMessage(text);
    }

    public WeiXinQYMessage(String agentid, Supplier<String> contentSupplier) {
        this.agentid = agentid;
        this.contentSupplier = contentSupplier;
    }

    public final void setTextMessage(String content) {
        if (text == null) text = new HashMap<>();
        text.put("content", content);
    }

    public void supplyContent() {
        if (contentSupplier != null) setTextMessage(contentSupplier.get());
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
