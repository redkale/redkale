/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.io.Serializable;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.redkale.annotation.Comment;
import org.redkale.convert.*;
import org.redkale.convert.json.JsonConvert;
import org.redkale.net.client.ClientConnection;
import org.redkale.net.client.ClientRequest;
import org.redkale.util.ByteArray;
import org.redkale.util.Traces;

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
public class HttpSimpleRequest extends ClientRequest implements java.io.Serializable {

    @ConvertColumn(index = 2)
    @Comment("是否RPC请求, 该类通常是为RPC创建的，故默认是true")
    protected boolean rpc = true;

    @ConvertColumn(index = 3)
    @Comment("链路ID")
    protected String traceid;

    @ConvertColumn(index = 4)
    @Comment("请求参数的ConvertType")
    protected ConvertType reqConvertType;

    @ConvertColumn(index = 5)
    @Comment("输出结果的ConvertType")
    protected ConvertType respConvertType;

    @Comment("Method GET/POST/...")
    @ConvertColumn(index = 6)
    protected String method;

    @ConvertColumn(index = 7)
    @Comment("请求的URI")
    protected String requestURI;

    @ConvertColumn(index = 8)
    @Comment("请求的前缀")
    protected String path;

    @ConvertColumn(index = 9)
    @Comment("客户端IP")
    protected String remoteAddr;

    @ConvertColumn(index = 10)
    @Comment("Locale国际化")
    protected String locale;

    @ConvertColumn(index = 11)
    @Comment("会话ID")
    protected String sessionid;

    @ConvertColumn(index = 12)
    @Comment("Content-Type")
    protected String contentType;

    @ConvertColumn(index = 13) //@since 2.5.0 由int改成Serializable, 具体数据类型只能是int、long、String
    protected Serializable currentUserid;

    @ConvertColumn(index = 14)
    @Comment("http header信息")
    protected Map<String, String> headers;

    @ConvertColumn(index = 15)
    @Comment("参数信息")
    protected Map<String, String> params;

    @ConvertColumn(index = 16)
    @Comment("http body信息")
    protected byte[] body; //对应HttpRequest.array

    public static HttpSimpleRequest create(String requestURI) {
        return new HttpSimpleRequest().requestURI(requestURI).method("POST").traceid(Traces.currentTraceid());
    }

    public static HttpSimpleRequest create(String requestURI, Object... params) {
        HttpSimpleRequest req = new HttpSimpleRequest().requestURI(requestURI).method("POST").traceid(Traces.currentTraceid());
        int len = params.length / 2;
        for (int i = 0; i < len; i++) {
            req.param(params[i * 2].toString(), params[i * 2 + 1]);
        }
        return req;
    }

    public static HttpSimpleRequest create(String requestURI, Map<String, String> headers) {
        return new HttpSimpleRequest().requestURI(requestURI).method("POST").headers(headers).traceid(Traces.currentTraceid());
    }

    @Override
    public void writeTo(ClientConnection conn, ByteArray array) {
        array.put((method.toUpperCase() + " " + requestURI + " HTTP/1.1\r\n"
            + Rest.REST_HEADER_TRACEID + ": " + traceid + "\r\n"
            + "Content-Length: " + (body == null ? 0 : body.length) + "\r\n").getBytes(StandardCharsets.UTF_8));
        if (headers != null) {
            headers.forEach((k, v) -> {
                array.put((k + ": " + v + "\r\n").getBytes(StandardCharsets.UTF_8));
            });
        }
        array.put((byte) '\r', (byte) '\n');
        if (body != null) {
            array.put(body);
        }
    }

    @ConvertDisabled
    public String getParametersToString() {
        if (this.params == null || this.params.isEmpty()) {
            return null;
        }
        final StringBuilder sb = new StringBuilder();
        AtomicBoolean no2 = new AtomicBoolean(false);
        this.params.forEach((n, v) -> {
            if (no2.get()) {
                sb.append('&');
            }
            sb.append(n).append('=').append(URLEncoder.encode(v, StandardCharsets.UTF_8));
            no2.set(true);
        });
        return sb.toString();
    }

    public HttpSimpleRequest formUrlencoded() {
        this.headers.put("Content-Type", "x-www-form-urlencoded");
        return this;
    }

    public HttpSimpleRequest rpc(boolean rpc) {
        this.rpc = rpc;
        return this;
    }

    public HttpSimpleRequest traceid(String traceid) {
        this.traceid = traceid;
        return this;
    }

    public HttpSimpleRequest requestURI(String requestURI) {
        this.requestURI = requestURI;
        return this;
    }

    public HttpSimpleRequest path(String path) {
        this.path = path;
        return this;
    }

    public HttpSimpleRequest bothConvertType(ConvertType convertType) {
        this.reqConvertType = convertType;
        this.respConvertType = convertType;
        return this;
    }

    public HttpSimpleRequest reqConvertType(ConvertType reqConvertType) {
        this.reqConvertType = reqConvertType;
        return this;
    }

    public HttpSimpleRequest respConvertType(ConvertType respConvertType) {
        this.respConvertType = respConvertType;
        return this;
    }

    public HttpSimpleRequest remoteAddr(String remoteAddr) {
        this.remoteAddr = remoteAddr;
        return this;
    }

    public HttpSimpleRequest locale(String locale) {
        this.locale = locale;
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

    public HttpSimpleRequest currentUserid(Serializable userid) {
        this.currentUserid = userid;
        return this;
    }

    public HttpSimpleRequest removeHeader(String name) {
        if (this.headers != null) {
            this.headers.remove(name);
        }
        return this;
    }

    public HttpSimpleRequest removeParam(String name) {
        if (this.params != null) {
            this.params.remove(name);
        }
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

    public HttpSimpleRequest method(String method) {
        this.method = method;
        return this;
    }

    public HttpSimpleRequest header(String key, String value) {
        if (this.headers == null) {
            this.headers = new HashMap<>();
        }
        this.headers.put(key, value);
        return this;
    }

    public HttpSimpleRequest header(String key, TextConvert convert, Object value) {
        if (value == null) {
            return this;
        }
        if (this.headers == null) {
            this.headers = new HashMap<>();
        }
        if (convert == null) {
            convert = JsonConvert.root();
        }
        this.headers.put(key, convert.convertTo(value));
        return this;
    }

    public HttpSimpleRequest header(String key, Object value) {
        if (value == null) {
            return this;
        }
        if (this.headers == null) {
            this.headers = new HashMap<>();
        }
        this.headers.put(key, JsonConvert.root().convertTo(value));
        return this;
    }

    public HttpSimpleRequest header(String key, int value) {
        if (this.headers == null) {
            this.headers = new HashMap<>();
        }
        this.headers.put(key, String.valueOf(value));
        return this;
    }

    public HttpSimpleRequest header(String key, long value) {
        if (this.headers == null) {
            this.headers = new HashMap<>();
        }
        this.headers.put(key, String.valueOf(value));
        return this;
    }

    public HttpSimpleRequest param(String key, String value) {
        if (this.params == null) {
            this.params = new HashMap<>();
        }
        this.params.put(key, value);
        return this;
    }

    public HttpSimpleRequest param(String key, TextConvert convert, Object value) {
        if (value == null) {
            return this;
        }
        if (this.params == null) {
            this.params = new HashMap<>();
        }
        if (convert == null) {
            convert = JsonConvert.root();
        }
        this.params.put(key, convert.convertTo(value));
        return this;
    }

    public HttpSimpleRequest param(String key, Object value) {
        if (value == null) {
            return this;
        }
        if (this.params == null) {
            this.params = new HashMap<>();
        }
        this.params.put(key, value instanceof CharSequence ? value.toString() : JsonConvert.root().convertTo(value));
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

    public HttpSimpleRequest clearLocale() {
        this.locale = null;
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

    public String getHeader(String name) {
        return headers == null ? null : headers.get(name);
    }

    public String getHeader(String name, String defaultValue) {
        return headers == null ? defaultValue : headers.getOrDefault(name, defaultValue);
    }

    public boolean isRpc() {
        return rpc;
    }

    public void setRpc(boolean rpc) {
        this.rpc = rpc;
    }

    public String getTraceid() {
        return traceid;
    }

    public void setTraceid(String traceid) {
        this.traceid = traceid;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getRequestURI() {
        return requestURI;
    }

    public void setRequestURI(String requestURI) {
        this.requestURI = requestURI;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
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

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public Serializable getCurrentUserid() {
        return currentUserid;
    }

    public void setCurrentUserid(Serializable currentUserid) {
        this.currentUserid = currentUserid;
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

    public ConvertType getReqConvertType() {
        return reqConvertType;
    }

    public void setReqConvertType(ConvertType reqConvertType) {
        this.reqConvertType = reqConvertType;
    }

    public ConvertType getRespConvertType() {
        return respConvertType;
    }

    public void setRespConvertType(ConvertType respConvertType) {
        this.respConvertType = respConvertType;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }

}
