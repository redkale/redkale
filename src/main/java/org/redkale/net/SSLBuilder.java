/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.io.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import java.util.logging.*;
import javax.net.ssl.*;
import org.redkale.util.*;

/**
 * 根据配置生成SSLContext
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class SSLBuilder {

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    protected String[] ciphers;

    protected String[] protocols;

    protected boolean wantClientAuth;

    protected boolean needClientAuth;

    public SSLContext createSSLContext(Server server, AnyValue sslConf) throws Exception {
        String protocol = sslConf.getValue("protocol", "TLS");
        String clientauth = sslConf.getValue("clientAuth", "none");
        String sslProviderImpl = sslConf.getValue("sslProvider");
        String jsseProviderImpl = sslConf.getValue("jsseProvider");
        String enabledProtocols = sslConf.getValue("protocols", "").replaceAll("\\s+", "")
            .replace(';', ',').replace(':', ',').replaceAll(",+", ",").replaceAll(",$", "");
        String enabledCiphers = sslConf.getValue("ciphers", "").replaceAll("\\s+", "")
            .replace(';', ',').replace(':', ',').replaceAll(",+", ",").replaceAll(",$", "");

        String keyfile = sslConf.getValue("keystoreFile");
        String keypass = sslConf.getValue("keystorePass", "");
        String keyType = sslConf.getValue("keystoreType", "JKS");
        String keyAlgorithm = sslConf.getValue("keystoreAlgorithm", "SunX509");

        String trustfile = sslConf.getValue("truststoreFile");
        String trustpass = sslConf.getValue("truststorePass", "");
        String trustType = sslConf.getValue("truststoreType", "JKS");
        String trustAlgorithm = sslConf.getValue("truststoreAlgorithm", "SunX509");

        Provider sslProvider = null;
        Provider jsseProvider = null;
        if (sslProviderImpl != null) {
            Class<Provider> providerClass = (Class) Thread.currentThread().getContextClassLoader().loadClass(sslProviderImpl);
            RedkaleClassLoader.putReflectionPublicConstructors(providerClass, providerClass.getName());
            sslProvider = providerClass.getConstructor().newInstance();
        }
        if (jsseProviderImpl != null) {
            Class<Provider> providerClass = (Class) Thread.currentThread().getContextClassLoader().loadClass(jsseProviderImpl);
            RedkaleClassLoader.putReflectionPublicConstructors(providerClass, providerClass.getName());
            jsseProvider = providerClass.getConstructor().newInstance();
        }

        KeyManager[] keyManagers = null;
        if (keyfile != null) {
            KeyManagerFactory kmf = jsseProvider == null ? KeyManagerFactory.getInstance(keyAlgorithm) : KeyManagerFactory.getInstance(keyAlgorithm, jsseProvider);
            KeyStore ks = jsseProvider == null ? KeyStore.getInstance(keyType) : KeyStore.getInstance(keyType, jsseProvider);
            ks.load(new FileInputStream(keyfile), keypass.toCharArray());
            kmf.init(ks, keypass.toCharArray());
            keyManagers = kmf.getKeyManagers();
        }

        if ("WANT".equalsIgnoreCase(clientauth)) {
            this.wantClientAuth = true;
        } else if ("NEED".equalsIgnoreCase(clientauth)) {
            this.needClientAuth = true;
        }

        TrustManager[] trustManagers;
        if (trustfile != null) {
            KeyStore ts = jsseProvider == null ? KeyStore.getInstance(trustType) : KeyStore.getInstance(trustType, jsseProvider);
            ts.load(new FileInputStream(trustfile), trustpass.toCharArray());
            TrustManagerFactory tmf = jsseProvider == null ? TrustManagerFactory.getInstance(trustAlgorithm) : TrustManagerFactory.getInstance(trustAlgorithm, jsseProvider);
            tmf.init(ts);
            trustManagers = tmf.getTrustManagers();
        } else {
            trustManagers = new TrustManager[]{new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }};
        }

        SSLContext sslContext;
        if (sslProvider == null) {
            sslContext = SSLContext.getInstance(protocol);
        } else {
            sslContext = SSLContext.getInstance(protocol, sslProvider);
        }
        sslContext.init(keyManagers, trustManagers, new SecureRandom());
        if (!enabledProtocols.isEmpty()) {
            HashSet<String> set = new HashSet<>();
            HashSet<String> unset = new HashSet<>();
            String[] protocolArray = sslContext.getSupportedSSLParameters().getProtocols();
            for (String p : enabledProtocols.split(",")) {
                if (Utility.contains(protocolArray, p)) {
                    set.add(p);
                } else {
                    unset.add(p);
                }
            }
            if (!set.isEmpty()) {
                this.protocols = set.toArray(new String[set.size()]);
            }
            if (!unset.isEmpty()) {
                logger.log(Level.WARNING, "protocols " + unset + " is not supported, only support: " + Arrays.toString(protocolArray));
            }
        }
        if (!enabledCiphers.isEmpty()) {
            HashSet<String> set = new HashSet<>();
            HashSet<String> unset = new HashSet<>();
            String[] cipherArray = sslContext.getSupportedSSLParameters().getCipherSuites();
            for (String c : enabledCiphers.split(",")) {
                if (Utility.contains(cipherArray, c)) {
                    set.add(c);
                } else {
                    unset.add(c);
                }
            }
            if (!set.isEmpty()) {
                this.ciphers = set.toArray(new String[set.size()]);
            }
            if (!unset.isEmpty()) {
                logger.log(Level.WARNING, "cipherSuites " + unset + " is not supported, only support: " + Arrays.toString(cipherArray));
            }
        }
        return sslContext;
    }

    public SSLEngine createSSLEngine(SSLContext sslContext, boolean client) {
        SSLEngine engine = sslContext.createSSLEngine();
        if (protocols != null) {
            engine.setEnabledProtocols(protocols);
        }
        if (ciphers != null) {
            engine.setEnabledCipherSuites(ciphers);
        }
        engine.setUseClientMode(client);
        if (wantClientAuth) {
            engine.setWantClientAuth(true);
        } else if (needClientAuth) {
            engine.setNeedClientAuth(true);
        }
        return engine;
    }
}
