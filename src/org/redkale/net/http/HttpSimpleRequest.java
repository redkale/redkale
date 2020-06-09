/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.util.*;
import org.redkale.convert.ConvertColumn;
import org.redkale.convert.json.JsonConvert;
import org.redkale.util.Comment;

/**
 * HttpRequest的缩减版, 只提供部分字段
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.1.0
 */
public class HttpSimpleRequest implements java.io.Serializable {

    @ConvertColumn(index = 1)
    @Comment("请求的URI")
    protected String requestURI;

    @ConvertColumn(index = 2)
    @Comment("客户端IP")
    protected String remoteAddr;

    @ConvertColumn(index = 3)
    @Comment("会话ID")
    protected String sessionid;

    @ConvertColumn(index = 4)
    @Comment("Content-Type")
    protected String contentType;

    @ConvertColumn(index = 5)
    @Comment("http header信息")
    protected Map<String, String> headers;

    @ConvertColumn(index = 6)
    @Comment("参数信息")
    protected Map<String, String> params;

    @ConvertColumn(index = 7)
    @Comment("http body信息")
    protected byte[] body; //对应HttpRequest.array

    public HttpSimpleRequest requestURI(String requestURI) {
        this.requestURI = requestURI;
        return this;
    }

    public HttpSimpleRequest remoteAddr(String remoteAddr) {
        this.remoteAddr = remoteAddr;
        return this;
    }

    public HttpSimpleRequest sessionid(String sessionid) {
        this.sessionid = sessionid;
        return this;
    }

    public HttpSimpleRequest contentType(String contentType) {
        this.contentType = contentType;
        return this;
    }

    public HttpSimpleRequest headers(Map<String, String> headers) {
        this.headers = headers;
        return this;
    }

    public HttpSimpleRequest params(Map<String, String> params) {
        this.params = params;
        return this;
    }

    public HttpSimpleRequest header(String key, String value) {
        if (this.headers == null) this.headers = new HashMap<>();
        this.headers.put(key, value);
        return this;
    }

    public HttpSimpleRequest param(String key, String value) {
        if (this.params == null) this.params = new HashMap<>();
        this.params.put(key, value);
        return this;
    }

    public HttpSimpleRequest body(byte[] body) {
        this.body = body;
        return this;
    }

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

    public HttpSimpleRequest clearContentType() {
        this.contentType = null;
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

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
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
