/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.service.apns;

import org.redkale.convert.json.JsonFactory;

/**
 *
 * @see http://www.redkale.org
 * @author zhangjx
 */
public class ApnsMessage {

    public static final int PRIORITY_IMMEDIATELY = 10;

    public static final int PRIORITY_A_TIME = 5;

    private ApnsPayload payload;

    private int expiredate;

    private int priority = PRIORITY_IMMEDIATELY;

    private int identifier;

    private String token;

    public ApnsMessage() {
    }

    public ApnsMessage(String token, ApnsPayload payload) {
        this(token, payload, 0);
    }

    public ApnsMessage(String token, ApnsPayload payload, int expiredate) {
        this(token, payload, expiredate, PRIORITY_IMMEDIATELY);
    }

    public ApnsMessage(String token, ApnsPayload payload, int expiredate, int priority) {
        this.token = token;
        this.payload = payload;
        this.expiredate = expiredate;
        this.priority = priority;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public int getExpiredate() {
        return expiredate;
    }

    public void setExpiredate(int expiredate) {
        this.expiredate = expiredate;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public ApnsPayload getPayload() {
        return payload;
    }

    public void setPayload(ApnsPayload payload) {
        this.payload = payload;
    }

    public int getIdentifier() {
        return identifier;
    }

    public void setIdentifier(int identifier) {
        this.identifier = identifier;
    }

    @Override
    public String toString() {
        return JsonFactory.root().getConvert().convertTo(this);
    }

}
