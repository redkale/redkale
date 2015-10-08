/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.client;

import java.net.*;

/**
 *
 * @author zhangjx
 */
public class HttpClient {

    private final URL url;

    public HttpClient(URL url) {
        this.url = url;
    }

    public HttpClient setTimeoutListener(Runnable runner) {
        return this;
    }
    
    public static void main(String[] args) throws Exception {
        URL url = new URL("https://www.3wyc.com");
        System.out.println(url.openConnection().getClass()); 
    }
}
