/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.client;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 *
 * @see http://www.redkale.org
 * @author zhangjx
 */
public class HttpFactory {

    private static final HttpFactory instance = new HttpFactory(null);

    final HttpFactory parent;

    final List<HttpCookie> defaultCookies = new CopyOnWriteArrayList<HttpCookie>();

    final Map<String, String> defaultHeaders = new ConcurrentHashMap<String, String>();

    private HttpFactory(HttpFactory parent) {
        this.parent = parent;
    }

    public HttpFactory parent() {
        return this.parent;
    }

    public static HttpFactory root() {
        return instance;
    }

    public HttpFactory createChild() {
        return new HttpFactory(this);
    }

    public Map<String, String> getDefaultHeader() {
        return new HashMap<String, String>(defaultHeaders);
    }

    public HttpFactory addDefaultHeader(String name, String value) {
        defaultHeaders.put(name, value);
        return this;
    }

    public HttpFactory removeDefaultHeader(String name) {
        defaultHeaders.remove(name);
        return this;
    }

    public HttpFactory addDefaultCookie(HttpCookie cookie) {
        if (cookie != null) defaultCookies.add(cookie);
        return this;
    }

    public HttpFactory addDefaultCookie(HttpCookie... cookies) {
        if (cookies != null) {
            for (HttpCookie c : cookies) {
                if (c != null) defaultCookies.add(c);
            }
        }
        return this;
    }

    public HttpFactory removeDefaultCookie(String name) {
        HttpCookie cookie = null;
        for (HttpCookie c : defaultCookies) {
            if (c.getName().equals(name)) {
                cookie = c;
                break;
            }
        }
        defaultCookies.remove(cookie);
        return this;
    }

    public HttpClient open(URL url) {
        return new HttpClient(this, url);
    }
}
