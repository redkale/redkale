/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.util.*;
import org.redkale.convert.ConvertColumn;
import org.redkale.convert.json.JsonConvert;

/**
 * HttpRequest的缩减版, 只提供部分字段
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class HttpSimpleRequest implements java.io.Serializable {

    @ConvertColumn(index = 1)
    protected String requestURI;

    @ConvertColumn(index = 2)
    protected String remoteAddr;

    @ConvertColumn(index = 3)
    protected String sessionid;

    @ConvertColumn(index = 4)
    protected Map<String, String> headers;

    @ConvertColumn(index = 5)
    protected Map<String, String> params;

    @ConvertColumn(index = 6)
    protected byte[] body; //对应HttpRequest.array

    public HttpSimpleRequest clearParams() {
        this.params = null;
        return this;
    }

    public HttpSimpleRequest clearHeaders() {
        this.headers = null;
        return this;
    }

    public HttpSimpleRequest clearRemoteAddr() {
        this.remoteAddr = null;
        return this;
    }

    public HttpSimpleRequest clearSessionid() {
        this.sessionid = null;
        return this;
    }

    public String getRequestURI() {
        return requestURI;
    }

    public void setRequestURI(String requestURI) {
        this.requestURI = requestURI;
    }

    public String getSessionid() {
        return sessionid;
    }

    public void setSessionid(String sessionid) {
        this.sessionid = sessionid;
    }

    public String getRemoteAddr() {
        return remoteAddr;
    }

    public void setRemoteAddr(String remoteAddr) {
        this.remoteAddr = remoteAddr;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public Map<String, String> getParams() {
        return params;
    }

    public void setParams(Map<String, String> params) {
        this.params = params;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }

}
