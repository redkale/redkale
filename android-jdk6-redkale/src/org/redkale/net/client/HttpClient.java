/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.client;

import java.net.*;
import java.util.*;

/**
 *
 * 待开发……
 *
 * @see http://www.redkale.org
 * @author zhangjx
 */
public final class HttpClient {

    protected Map<String, HttpCookie> cookies;

    protected Map<String, String> headers;

    protected Map<String, String> params;

    protected final HttpFactory root;

    protected final URL url;

    private String method = "GET";

    HttpClient(HttpFactory root, URL url) {
        this.root = root;
        this.url = url;
    }

    public HttpClient setTimeoutListener(Runnable runner) {
        return this;
    }

    public HttpClient getMethod() {
        this.method = "GET";
        return this;
    }

    public HttpClient postMethod() {
        this.method = "POST";
        return this;
    }

    public Map<String, String> getHeaders() {
        return this.headers == null ? null : new HashMap<String, String>(this.headers);
    }

    public HttpClient addHeader(String name, String value) {
        if (this.headers == null && name != null) this.headers = new HashMap<String, String>();
        this.headers.put(name, value);
        return this;
    }

    public HttpClient addHeader(final Map<String, String> headerMap) {
        if (headerMap == null || headerMap.isEmpty()) return this;
        if (this.headers == null) this.headers = new HashMap<String, String>();
        this.headers.putAll(headerMap);
        return this;
    }

    public HttpClient removeHeader(String name) {
        if (this.headers == null || name == null) return this;
        headers.remove(name);
        return this;
    }

    public Map<String, HttpCookie> getCookies() {
        return this.cookies == null ? null : new HashMap<String, HttpCookie>(this.cookies);
    }

    public HttpClient addCookie(final HttpCookie cookie) {
        if (this.cookies == null) this.cookies = new HashMap<String, HttpCookie>();
        if (cookie != null) this.cookies.put(cookie.getName(), cookie);
        return this;
    }

    public HttpClient addCookie(final HttpCookie... httpCookies) {
        if (httpCookies == null || httpCookies.length < 1) return this;
        if (this.cookies == null) this.cookies = new HashMap<String, HttpCookie>();
        for (HttpCookie c : httpCookies) {
            if (c != null) this.cookies.put(c.getName(), c);
        }
        return this;
    }

    public HttpClient removeCookie(final String name) {
        if (this.cookies != null && name != null) this.cookies.remove(name);
        return this;
    }

    public static void main(String[] args) throws Exception {
        URL url = new URL("https://www.redkale.org");
        System.out.println(url.openConnection().getClass());
    }
}
