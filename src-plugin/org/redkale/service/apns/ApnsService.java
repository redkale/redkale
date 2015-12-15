/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.service.apns;

import org.redkale.util.AnyValue;
import org.redkale.util.Utility;
import org.redkale.convert.json.JsonConvert;
import org.redkale.util.AutoLoad;
import org.redkale.service.LocalService;
import org.redkale.service.Service;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.*;
import java.security.*;
import java.util.concurrent.*;
import java.util.logging.*;
import javax.annotation.*;
import javax.net.ssl.*;

/**
 *
 * @see http://www.redkale.org
 * @author zhangjx
 */
@AutoLoad(false)
@LocalService
public class ApnsService implements Service {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    @Resource
    protected JsonConvert convert;

    @Resource(name = "property.apns.certpwd")
    protected String apnscertpwd = "1"; //证书的密码

    @Resource(name = "property.apns.certpath") //用来加载证书用
    protected String apnscertpath = "apnspushdev_cert.p12";

    @Resource(name = "property.apns.pushaddr") //
    protected String apnspushaddr = "gateway.sandbox.push.apple.com";

    @Resource(name = "property.apns.pushport") //
    protected int apnspushport = 2195;

    @Resource(name = "property.apns.buffersize") //
    protected int apnsbuffersize = 4096;

    private boolean inited = false;

    private final CountDownLatch cdl = new CountDownLatch(1);

    private SSLSocketFactory sslFactory;

    @Override
    public void init(AnyValue conf) {
        new Thread() {
            {
                setDaemon(true);
                setPriority(Thread.MAX_PRIORITY);
            }

            @Override
            public void run() {
                try {
                    final String path = "/" + this.getClass().getPackage().getName().replace('.', '/') + "/" + apnscertpath;
                    KeyStore ks = KeyStore.getInstance("PKCS12");
                    InputStream in = ApnsService.class.getResourceAsStream(path);
                    KeyManagerFactory kf = null;
                    if (in != null) {
                        ks.load(in, apnscertpwd.toCharArray());
                        in.close();
                        kf = KeyManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                        kf.init(ks, apnscertpwd.toCharArray());
                    }

                    TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                    tmf.init((KeyStore) null);
                    SSLContext context = SSLContext.getInstance("TLS");
                    context.init(kf == null ? new KeyManager[0] : kf.getKeyManagers(), tmf.getTrustManagers(), null);
                    ApnsService.this.sslFactory = context.getSocketFactory();
                } catch (Exception e) {
                    logger.log(Level.SEVERE, this.getClass().getSimpleName() + " init SSLContext error", e);
                } finally {
                    inited = true;
                    cdl.countDown();
                }
            }
        }.start();
    }

    @Override
    public void destroy(AnyValue conf) {
    }

    private Socket getPushSocket() throws IOException {
        if (!this.inited) {
            try {
                cdl.await();
            } catch (InterruptedException e) {
            }
        }
        Socket pushSocket = sslFactory.createSocket(apnspushaddr, apnspushport);
        pushSocket.setTcpNoDelay(true);
        return pushSocket;
    }

    public void pushApnsMessage(ApnsMessage message) throws IOException {
        final byte[] tokens = Utility.hexToBin(message.getToken().replaceAll("\\s+", ""));
        ByteBuffer buffer = ByteBuffer.allocate(apnsbuffersize);
        buffer.put((byte) 2); //固定命令号 
        buffer.putInt(0); //下面数据的长度

        buffer.put((byte) 1); //token
        buffer.putShort((short) tokens.length);
        buffer.put(tokens);

        buffer.put((byte) 2);  //payload
        final byte[] payload = message.getPayload().toString().getBytes(UTF8);
        buffer.putShort((short) payload.length);
        buffer.put(payload);

        if (message.getIdentifier() > 0) {
            buffer.put((byte) 3);  //Notification identifier
            buffer.putShort((short) 4);
            buffer.putInt(message.getIdentifier());
        }
        if (message.getExpiredate() > 0) {
            buffer.put((byte) 4); //Expiration date
            buffer.putShort((short) 4);
            buffer.putInt(message.getExpiredate());
        }
        buffer.put((byte) 5);  //Priority
        buffer.putShort((short) 1);
        buffer.put((byte) message.getPriority());

        final int pos = buffer.position();
        buffer.position(1);
        buffer.putInt(pos - 5);
        buffer.position(pos);
        buffer.flip();

        Socket socket = getPushSocket();
        socket.getOutputStream().write(buffer.array(), 0, buffer.remaining());
        socket.close();
    }

//    public static void main(String[] args) throws Exception {
//        ApnsService service = new ApnsService();
//        service.convert = JsonFactory.root().getConvert();
//        service.init(null);
//
//        final String token = "01727b19 b9f8abf4 0891e31d 3446479d a43902e1 819edc44 a073d951 b8b7db90";
//        ApnsPayload payload = new ApnsPayload("您有新的消息", "这是消息内容", 1);
//        System.out.println(payload);
//        service.pushApnsMessage(new ApnsMessage(token, payload));
//    }

}
