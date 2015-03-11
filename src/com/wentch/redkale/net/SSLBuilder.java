/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net;

import java.security.*;
import javax.net.ssl.*;

/**
 *
 * @author zhangjx
 */
public class SSLBuilder {

    private static char[] keypasswd;

    public static void main(String[] args) throws Exception {
        final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, keypasswd);
        final String algorithm = System.getProperty("ssl.algorithm", KeyManagerFactory.getDefaultAlgorithm());
        final KeyManagerFactory kmf = KeyManagerFactory.getInstance(algorithm);
        kmf.init(keyStore, keypasswd);

        final SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, new SecureRandom());
        //-------------------------
        final SSLEngine sslEngine = sslContext.createSSLEngine();
        //sslEngine.setEnabledCipherSuites(null);
        //sslEngine.setEnabledProtocols(null);

        sslEngine.setUseClientMode(false);
        sslEngine.setWantClientAuth(false);
        sslEngine.setNeedClientAuth(false);

    }
}
