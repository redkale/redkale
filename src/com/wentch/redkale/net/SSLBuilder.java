/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net;

import java.nio.*;
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

    private static final byte CHANGE_CIPHER_SPECT_CONTENT_TYPE = 20;

    private static final byte APPLICATION_DATA_CONTENT_TYPE = 23;

    private static final int SSLV3_RECORD_HEADER_SIZE = 5; // SSLv3 record header

    private static final int SSL20_HELLO_VERSION = 0x0002;

    private static final int MIN_VERSION = 0x0300;

    private static final int MAX_MAJOR_VERSION = 0x03;

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
        if (byte0 >= CHANGE_CIPHER_SPECT_CONTENT_TYPE && byte0 <= APPLICATION_DATA_CONTENT_TYPE) {
            /*
             * Last sanity check that it's not a wild record
             */
            final byte major = byte1;
            final byte minor = byte2;
            final int v = (major << 8) | minor & 0xff;

            // Check if too old (currently not possible)
            // or if the major version does not match.
            // The actual version negotiation is in the handshaker classes
            if ((v < MIN_VERSION) || (major > MAX_MAJOR_VERSION)) {
                throw new SSLException("Unsupported record version major=" + major + " minor=" + minor);
            }

            /*
             * One of the SSLv3/TLS message types.
             */
            len = ((byte3 & 0xff) << 8) + (byte4 & 0xff) + SSLV3_RECORD_HEADER_SIZE;

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
                if ((v < MIN_VERSION) || (major > MAX_MAJOR_VERSION)) {
                    // if it's not SSLv2, we're out of here.
                    if (v != SSL20_HELLO_VERSION) throw new SSLException("Unsupported record version major=" + major + " minor=" + minor);

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
