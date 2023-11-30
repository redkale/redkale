/*
 *
 */
package org.redkale.test.http;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redkale.boot.Application;
import org.redkale.net.AsyncIOGroup;
import org.redkale.net.http.HttpServer;
import org.redkale.net.http.HttpSimpleClient;
import org.redkale.net.http.HttpSimpleRequest;
import org.redkale.util.AnyValue;
import org.redkale.util.ResourceFactory;

/**
 *
 * @author zhangjx
 */
public class HttpSimpleClientTest {

    private static int port = 0;

    private static Application application;

    private static ResourceFactory factory;

    private static HttpServer server;

    private boolean main;

    public static void main(String[] args) throws Throwable {
        HttpSimpleClientTest test = new HttpSimpleClientTest();
        test.main = true;
        test.run();
    }

    @Test
    public void run() throws Exception {
        runServer();
        //Utility.sleep(50000); 
        final AsyncIOGroup asyncGroup = new AsyncIOGroup(8192, 16);
        asyncGroup.start();
        HttpSimpleClient client = HttpSimpleClient.create(asyncGroup);
        InetSocketAddress addr = new InetSocketAddress("127.0.0.1", port);
        {
            HttpSimpleRequest req = HttpSimpleRequest.createPostPath("/test").param("id", 100);
            System.out.println(client.getAsync("http://127.0.0.1:" + port + req.getPath() + "?id=100").join());
            System.out.println(client.sendAsync(addr, req).join());
        }
        final int count = 10;
        {
            final CountDownLatch cdl = new CountDownLatch(count);
            for (int i = 100; i < 100 + count; i++) {
                final int index = i;
                HttpSimpleRequest req = HttpSimpleRequest.createPostPath("/test").param("id", index);
                client.getAsync("http://127.0.0.1:" + port + req.getPath() + "?id=" + index).whenComplete((v, t) -> {
                    cdl.countDown();
                    Assertions.assertEquals("ok-" + index, new String((byte[]) v.getResult()));
                });
            }
            cdl.await();
            System.out.println("结束并发1");
        }
        {
            final CountDownLatch cdl = new CountDownLatch(count);
            for (int i = 100; i < 100 + count; i++) {
                final int index = i;
                HttpSimpleRequest req = HttpSimpleRequest.createPostPath("/test").param("id", index);
                client.sendAsync(addr, req).whenComplete((v, t) -> {
                    cdl.countDown();
                    System.out.println("输出: " + new String((byte[]) v.getResult()));
                    Assertions.assertEquals("ok-" + index, new String((byte[]) v.getResult()));
                });
            }
            cdl.await();
            System.out.println("结束并发2");
        }
        server.shutdown();
    }

    private static void runServer() throws Exception {
        application = Application.create(true);
        factory = application.getResourceFactory();
        factory.register("", Application.class, application);
        final CountDownLatch cdl = new CountDownLatch(1);
        final AsyncIOGroup asyncGroup = new AsyncIOGroup(8192, 16);
        asyncGroup.start();
        new Thread() {
            {
                setName("Thread-Server-01");
            }

            @Override
            public void run() {
                try {
                    AnyValue.DefaultAnyValue conf = new AnyValue.DefaultAnyValue();
                    conf.addValue("host", "0.0.0.0");
                    conf.addValue("port", "" + port);
                    conf.addValue("protocol", "HTTP");
                    conf.addValue("maxbody", "" + (100 * 1024 * 1024));
                    server = new HttpServer(factory);
                    server.init(conf);
                    server.addHttpServlet(new HttpSimpleServlet(), "/test");
                    server.start();
                    port = server.getSocketAddress().getPort();
                    cdl.countDown();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();
        cdl.await();
    }
}
