/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.net.HttpCookie;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.redkale.convert.*;
import org.redkale.convert.json.*;
import org.redkale.util.Creator;
import org.redkale.util.RedkaleException;

/**
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <T> 结果对象的类型
 */
public class HttpResult<T> {

    public static final String SESSIONID_COOKIENAME = HttpRequest.SESSIONID_NAME;

    @ConvertColumn(index = 1)
    protected int status = 200; // 不设置则为 200

    @ConvertColumn(index = 2)
    protected String contentType;

    @ConvertColumn(index = 3) // 不使用HttpHeader因不易反序列化
    protected Map<String, String> headers;

    @ConvertColumn(index = 4)
    protected List<HttpCookie> cookies;

    @ConvertColumn(index = 5)
    @ConvertCoder(encoder = ResultJsonCoder.class)
    protected T result;

    protected Convert convert;

    public HttpResult() {}

    public HttpResult(Convert convert, T result) {
        this.convert = convert;
        this.result = result;
    }

    public HttpResult(T result) {
        this.result = result;
    }

    public HttpResult(String contentType, T result) {
        this.contentType = contentType;
        this.result = result;
    }

    public HttpResult<T> header(String name, Serializable value) {
        if (name.indexOf('\r') >= 0 || name.indexOf('\n') >= 0) {
            throw new RedkaleException("http-header name(name = " + name + ") is illegal");
        }
        String val = String.valueOf(value);
        if (val.indexOf('\r') >= 0 || val.indexOf('\n') >= 0) {
            throw new RedkaleException("http-header value(name = " + name + ", value = " + val + ") is illegal");
        }
        if (this.headers == null) {
            this.headers = new HashMap<>();
        }
        this.headers.put(name, val);
        return this;
    }

    public HttpResult<T> cookie(String name, Serializable value) {
        return cookie(new HttpCookie(name, String.valueOf(value)));
    }

    public HttpResult<T> cookie(String name, Serializable value, boolean httpOnly) {
        HttpCookie c = new HttpCookie(name, String.valueOf(value));
        c.setHttpOnly(httpOnly);
        return cookie(c);
    }

    public HttpResult<T> cookie(HttpCookie cookie) {
        if (this.cookies == null) {
            this.cookies = new ArrayList<>();
        }
        this.cookies.add(cookie);
        return this;
    }

    public HttpResult<T> contentType(String contentType) {
        this.contentType = contentType;
        return this;
    }

    public HttpResult<T> result(T result) {
        this.result = result;
        return this;
    }

    public HttpResult<T> status(int status) {
        this.status = status;
        return this;
    }

    public Convert convert() {
        return convert;
    }

    public HttpResult<T> convert(Convert convert) {
        this.convert = convert;
        return this;
    }

    public String getHeader(String name) {
        return headers == null ? null : headers.get(name);
    }

    public String getHeader(String name, String dfvalue) {
        return headers == null ? dfvalue : headers.getOrDefault(name, dfvalue);
    }

    public CompletableFuture<HttpResult<T>> toFuture() {
        return CompletableFuture.completedFuture(this);
    }

    public CompletableFuture toAnyFuture() {
        return CompletableFuture.completedFuture(this);
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public List<HttpCookie> getCookies() {
        return cookies;
    }

    public void setCookies(List<HttpCookie> cookies) {
        this.cookies = cookies;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public T getResult() {
        return result;
    }

    public void setResult(T result) {
        this.result = result;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public boolean isSuccess() {
        return status == 0 || status == 200;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }

    private static class ResultJsonCoder implements Encodeable<JsonWriter, Object> {

        private final ConvertFactory factory;

        public static Creator<ResultJsonCoder> createCreator() {

            return new Creator<ResultJsonCoder>() {
                @Override
                public ResultJsonCoder create(Object... params) {
                    return new ResultJsonCoder((ConvertFactory) params[0]);
                }

                @Override
                public Class[] paramTypes() {
                    return new Class[] {ConvertFactory.class};
                }
            };
        }

        public ResultJsonCoder(ConvertFactory factory) {
            this.factory = factory;
        }

        @Override
        public void convertTo(JsonWriter out, Object value) {
            if (value instanceof byte[]) {
                out.writeString(new String((byte[]) value, StandardCharsets.UTF_8));
            } else {
                factory.loadEncoder(Object.class).convertTo(out, value);
            }
        }

        @Override
        public Type getType() {
            return Object.class;
        }
    }
}
