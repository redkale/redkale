/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.io.Serializable;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.redkale.annotation.Comment;
import org.redkale.annotation.Nullable;
import org.redkale.convert.*;
import org.redkale.convert.json.JsonConvert;
import org.redkale.net.client.ClientConnection;
import org.redkale.net.client.ClientRequest;
import static org.redkale.net.http.HttpSimpleClient.*;
import org.redkale.util.ByteArray;
import org.redkale.util.RedkaleException;
import org.redkale.util.Traces;
import static org.redkale.util.Utility.isNotEmpty;

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
    @Comment("请求的Path")
    protected String path;

    @ConvertColumn(index = 8)
    @Comment("请求的前缀")
    protected String contextPath;

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

    @ConvertColumn(index = 13) //@since 2.5.0 由int改成Serializable, 具体数据类型只能是int、long、BigInteger、String
    protected Serializable currentUserid;

    @ConvertColumn(index = 14)
    @Comment("http header信息")
    protected HttpHeaders headers;

    @ConvertColumn(index = 15)
    @Comment("参数信息")
    protected HttpParameters params;

    @ConvertColumn(index = 16)
    @Comment("http body信息")
    protected byte[] body; //对应HttpRequest.array

    public static HttpSimpleRequest createPath(String path) {
        return new HttpSimpleRequest().path(path).method("POST").traceid(Traces.currentTraceid());
    }

    public static HttpSimpleRequest createPath(String path, Object... params) {
        HttpSimpleRequest req = new HttpSimpleRequest().path(path).method("POST").traceid(Traces.currentTraceid());
        int len = params.length / 2;
        for (int i = 0; i < len; i++) {
            req.param(params[i * 2].toString(), params[i * 2 + 1]);
        }
        return req;
    }

    public static HttpSimpleRequest createPath(String path, HttpHeaders header) {
        return new HttpSimpleRequest().path(path).method("POST").headers(header).traceid(Traces.currentTraceid());
    }

    @Override
    public void writeTo(ClientConnection conn, ByteArray array) {
        //组装path和body
        String requestPath = requestPath();
        String contentType0 = this.contentType;
        byte[] clientBody = null;
        if (isNotEmpty(body)) {
            String paramstr = getParametersToString();
            if (paramstr != null) {
                if (getPath().indexOf('?') > 0) {
                    requestPath += "&" + paramstr;
                } else {
                    requestPath += "?" + paramstr;
                }
            }
            clientBody = getBody();
        } else {
            String paramstr = getParametersToString();
            if (paramstr != null) {
                clientBody = paramstr.getBytes(StandardCharsets.UTF_8);
            }
            contentType0 = "x-www-form-urlencoded";
        }
        //写status
        array.put((method.toUpperCase() + " " + requestPath + " HTTP/1.1\r\n").getBytes(StandardCharsets.UTF_8));
        //写header
        if (traceid != null && !containsHeaderIgnoreCase(Rest.REST_HEADER_TRACEID)) {
            array.put((Rest.REST_HEADER_TRACEID + ": " + traceid + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        if (!containsHeaderIgnoreCase("User-Agent")) {
            array.put(header_bytes_useragent);
        }
        if (!containsHeaderIgnoreCase("Connection")) {
            array.put(header_bytes_connalive);
        }
        array.put(("Content-Type: " + contentType0 + "\r\n").getBytes(StandardCharsets.UTF_8));
        array.put(contentLengthBytes(clientBody));
        if (headers != null) {
            headers.forEach((k, v) -> array.put((k + ": " + v + "\r\n").getBytes(StandardCharsets.UTF_8)));
        }
        array.put((byte) '\r', (byte) '\n');
        //写body    
        if (clientBody != null) {
            array.put(clientBody);
        }
    }

    protected boolean containsHeaderIgnoreCase(String name) {
        return headers != null && headers.containsIgnoreCase(name);
    }

    protected byte[] contentLengthBytes(byte[] clientBody) {
        int len = clientBody == null ? 0 : clientBody.length;
        if (len < contentLengthArray.length) {
            return contentLengthArray[len];
        }
        return ("Content-Length: " + len + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    @Nullable
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

    public String requestPath() {
        if (this.contextPath == null) {
            return this.path;
        }
        return this.contextPath + this.path;
    }

    public HttpSimpleRequest formUrlencoded() {
        this.headers.set("Content-Type", "x-www-form-urlencoded");
        return this;
    }

    public HttpSimpleRequest rpc(boolean rpc) {
        this.rpc = rpc;
        return this;
    }

    public HttpSimpleRequest traceid(String traceid) {
        if (traceid != null) {
            if (traceid.indexOf(' ') >= 0 || traceid.indexOf('\r') >= 0 || traceid.indexOf('\n') >= 0) {
                throw new RedkaleException("http-traceid(" + traceid + ") is illegal");
            }
        }
        this.traceid = traceid;
        return this;
    }

    public HttpSimpleRequest path(String path) {
        if (path.indexOf(' ') >= 0 || path.indexOf('\r') >= 0 || path.indexOf('\n') >= 0) {
            throw new RedkaleException("http-path(" + path + ") is illegal");
        }
        this.path = path;
        return this;
    }

    public HttpSimpleRequest contextPath(String contextPath) {
        if (contextPath.indexOf(' ') >= 0 || contextPath.indexOf('\r') >= 0 || contextPath.indexOf('\n') >= 0) {
            throw new RedkaleException("http-context-path(" + contextPath + ") is illegal");
        }
        this.contextPath = contextPath;
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

    public HttpSimpleRequest headers(HttpHeaders header) {
        this.headers = header;
        return this;
    }

    public HttpSimpleRequest params(HttpParameters params) {
        this.params = params;
        return this;
    }

    public HttpSimpleRequest method(String method) {
        this.method = method;
        return this;
    }

    public HttpSimpleRequest addHeader(String key, String value) {
        if (this.headers == null) {
            this.headers = HttpHeaders.create();
        }
        this.headers.add(key, value);
        return this;
    }

    public HttpSimpleRequest addHeader(String key, TextConvert convert, Object value) {
        return addHeader(key, (convert == null ? JsonConvert.root() : convert).convertTo(value));
    }

    public HttpSimpleRequest addHeader(String key, Object value) {
        return addHeader(key, JsonConvert.root().convertTo(value));
    }

    public HttpSimpleRequest addHeader(String key, int value) {
        return addHeader(key, String.valueOf(value));
    }

    public HttpSimpleRequest addHeader(String key, long value) {
        return addHeader(key, String.valueOf(value));
    }

    public HttpSimpleRequest setHeader(String key, String value) {
        if (this.headers == null) {
            this.headers = HttpHeaders.create();
        }
        this.headers.set(key, value);
        return this;
    }

    public HttpSimpleRequest setHeader(String key, TextConvert convert, Object value) {
        return setHeader(key, (convert == null ? JsonConvert.root() : convert).convertTo(value));
    }

    public HttpSimpleRequest setHeader(String key, Object value) {
        return setHeader(key, JsonConvert.root().convertTo(value));
    }

    public HttpSimpleRequest setHeader(String key, int value) {
        return setHeader(key, String.valueOf(value));
    }

    public HttpSimpleRequest setHeader(String key, long value) {
        return setHeader(key, String.valueOf(value));
    }

    public HttpSimpleRequest param(String key, String value) {
        if (this.params == null) {
            this.params = HttpParameters.create();
        }
        this.params.put(key, value);
        return this;
    }

    public HttpSimpleRequest param(String key, TextConvert convert, Object value) {
        if (this.params == null) {
            this.params = HttpParameters.create();
        }
        if (convert == null) {
            convert = JsonConvert.root();
        }
        this.params.put(key, convert, value);
        return this;
    }

    public HttpSimpleRequest param(String key, Object value) {
        return param(key, JsonConvert.root(), value);
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
        return getHeader(name, null);
    }

    public String getHeader(String name, String defaultValue) {
        return headers == null ? defaultValue : headers.firstValue(name, defaultValue);
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

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getContextPath() {
        return contextPath;
    }

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
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

    public HttpHeaders getHeaders() {
        return headers;
    }

    public void setHeaders(HttpHeaders headers) {
        headers(headers);
    }

    public HttpParameters getParams() {
        return params;
    }

    public void setParams(HttpParameters params) {
        params(params);
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

    private static final byte[][] contentLengthArray = new byte[1000][];

    static {
        for (int i = 0; i < contentLengthArray.length; i++) {
            contentLengthArray[i] = ("Content-Length: " + i + "\r\n").getBytes();
        }
    }
}
