/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.io.Serializable;
import java.net.HttpCookie;
import java.util.*;

/**
 *
 * <p>
 * 详情见: http://redkale.org
 *
 * @author zhangjx
 * @param <T> 结果对象的类型
 */
public class RestOutput<T> {

    private Map<String, String> headers;

    private List<HttpCookie> cookies;

    private T result;

    public RestOutput() {
    }

    public RestOutput(T result) {
        this.result = result;
    }

    public void addHeader(String name, Serializable value) {
        if (this.headers == null) this.headers = new HashMap<>();
        this.headers.put(name, String.valueOf(value));
    }

    public void addCookie(HttpCookie cookie) {
        if (this.cookies == null) this.cookies = new ArrayList<>();
        this.cookies.add(cookie);
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

    public T getResult() {
        return result;
    }

    public void setResult(T result) {
        this.result = result;
    }

}
