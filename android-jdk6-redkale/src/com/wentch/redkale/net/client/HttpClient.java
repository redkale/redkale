/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.client;

import java.net.*;
import java.util.*;

/**
 *
 * @author zhangjx
 */
public final class HttpClient {

    protected List<HttpCookie> cookies;

    protected Map<String, String> headers;

    protected Map<String, String> params;

    protected final HttpFactory factory;

    protected final URL url;

    HttpClient(HttpFactory factory, URL url) {
        this.factory = factory;
        this.url = url;
    }

    public HttpClient setTimeoutListener(Runnable runner) {
        return this;
    }

    public Map<String, String> getHeaders() {
        return headers == null ? null : new HashMap<String, String>(headers);
    }

    public HttpClient addHeader(String name, String value) {
        if (headers == null) this.headers = new HashMap<String, String>();
        headers.put(name, value);
        return this;
    }

    public HttpClient removeHeader(String name) {
        if (this.headers == null) return this;
        headers.remove(name);
        return this;
    }

    public List<HttpCookie> getCookies() {
        return cookies == null ? null : new ArrayList<HttpCookie>(cookies);
    }

    public HttpClient addCookie(HttpCookie cookie) {
        if (cookies == null) this.cookies = new ArrayList<HttpCookie>();
        if (cookie != null) cookies.add(cookie);
        return this;
    }

    public HttpClient addCookie(HttpCookie... cookies) {
        if (cookies == null) this.cookies = new ArrayList<HttpCookie>();
        if (cookies != null) {
            for (HttpCookie c : cookies) {
                if (c != null) this.cookies.add(c);
            }
        }
        return this;
    }

    public HttpClient removeCookie(String name) {
        if (this.cookies == null) return this;
        HttpCookie cookie = null;
        for (HttpCookie c : cookies) {
            if (c.getName().equals(name)) {
                cookie = c;
                break;
            }
        }
        cookies.remove(cookie);
        return this;
    }

    public static void main(String[] args) throws Exception {
        URL url = new URL("https://www.wentch.com");
        System.out.println(url.openConnection().getClass());
    }
}
