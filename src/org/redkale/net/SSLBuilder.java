/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.nio.*;
import java.security.*;
import javax.net.ssl.*;

/**
 *
 * @author zhangjx
 */
public class SSLBuilder {

    private static SSLContext sslContext;

    static {
        try {
            char[] keypasswd = new char[32];
            final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, keypasswd);
            final String algorithm = System.getProperty("ssl.algorithm", KeyManagerFactory.getDefaultAlgorithm());
            final KeyManagerFactory kmf = KeyManagerFactory.getInstance(algorithm);
            kmf.init(keyStore, keypasswd);
            SSLContext sslContext0 = SSLContext.getInstance("TLS");
            sslContext0.init(kmf.getKeyManagers(), null, new SecureRandom());
            sslContext = sslContext0;
        } catch (Exception e) {
            throw new Error(e);
        }
    }

    private final SSLEngine sslEngine;

    private int appBufferSize;

    private int netBufferSize;

    public SSLBuilder() {
        sslEngine = sslContext.createSSLEngine();
        //sslEngine.setEnabledCipherSuites(null);
        //sslEngine.setEnabledProtocols(null);

        sslEngine.setUseClientMode(false);
        sslEngine.setWantClientAuth(false);
        sslEngine.setNeedClientAuth(false);
        //---------------------------
        updateBufferSizes();
    }

    private void updateBufferSizes() {
        final SSLSession session = sslEngine.getSession();
        appBufferSize = session.getApplicationBufferSize();
        netBufferSize = session.getPacketBufferSize();
    }

    public static void main(String[] args) throws Exception {

    }

    private static int getSSLPacketSize(final ByteBuffer buf) throws SSLException {

        /*
         * SSLv2 length field is in bytes 0/1
         * SSLv3/TLS length field is in bytes 3/4
         */
        if (buf.remaining() < 5) return -1;

        final byte byte0;
        final byte byte1;
        final byte byte2;
        final byte byte3;
        final byte byte4;

        if (buf.hasArray()) {
            final byte[] array = buf.array();
            int pos = buf.arrayOffset() + buf.position();
            byte0 = array[pos++];
            byte1 = array[pos++];
            byte2 = array[pos++];
            byte3 = array[pos++];
            byte4 = array[pos];
        } else {
            int pos = buf.position();
            byte0 = buf.get(pos++);
            byte1 = buf.get(pos++);
            byte2 = buf.get(pos++);
            byte3 = buf.get(pos++);
            byte4 = buf.get(pos);
        }

        int len;

        /*
         * If we have already verified previous packets, we can
         * ignore the verifications steps, and jump right to the
         * determination.  Otherwise, try one last hueristic to
         * see if it's SSL/TLS.
         */
        if (byte0 >= 20 && byte0 <= 23) {
            /*
             * Last sanity check that it's not a wild record
             */
            final byte major = byte1;
            final byte minor = byte2;
            final int v = (major << 8) | minor & 0xff;

            // Check if too old (currently not possible)
            // or if the major version does not match.
            // The actual version negotiation is in the handshaker classes
            if ((v < 0x0300) || (major > 0x03)) {
                throw new SSLException("Unsupported record version major=" + major + " minor=" + minor);
            }

            /*
             * One of the SSLv3/TLS message types.
             */
            len = ((byte3 & 0xff) << 8) + (byte4 & 0xff) + 5; // SSLv3 record header

        } else {
            /*
             * Must be SSLv2 or something unknown.
             * Check if it's short (2 bytes) or
             * long (3) header.
             *
             * Internals can warn about unsupported SSLv2
             */
            boolean isShort = ((byte0 & 0x80) != 0);

            if (isShort && ((byte2 == 1) || byte2 == 4)) {

                final byte major = byte3;
                final byte minor = byte4;
                final int v = (major << 8) | minor & 0xff;

                // Check if too old (currently not possible)
                // or if the major version does not match.
                // The actual version negotiation is in the handshaker classes
                if ((v < 0x0300) || (major > 0x03)) {
                    // if it's not SSLv2, we're out of here.
                    if (v != 0x0002) throw new SSLException("Unsupported record version major=" + major + " minor=" + minor);
                }

                /*
                 * Client or Server Hello
                 */
                int mask = 0x7f;
                len = ((byte0 & mask) << 8) + (byte1 & 0xff) + (2);
            } else {
                // Gobblygook!
                throw new SSLException("Unrecognized SSL message, plaintext connection?");
            }
        }

        return len;
    }
}
