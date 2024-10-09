/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.sncp;

import java.net.InetSocketAddress;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.redkale.boot.*;
import org.redkale.convert.pb.ProtobufConvert;
import org.redkale.convert.pb.ProtobufFactory;
import org.redkale.inject.ResourceFactory;
import org.redkale.net.*;
import org.redkale.net.client.ClientAddress;
import org.redkale.net.sncp.*;
import org.redkale.service.Service;
import org.redkale.util.*;

/** @author zhangjx */
public class SncpTest {

    private static final String myhost = "127.0.0.1";

    private static int port = 0;

    private static int port2 = 1;

    private static final String protocol = "SNCP.TCP"; // TCP UDP

    private static final int clientCapacity =
            protocol.endsWith(".UDP") ? ByteBufferPool.DEFAULT_BUFFER_UDP_CAPACITY : 8192;

    private static ResourceFactory factory;

    private static Application application;

    private static SncpRpcGroups rpcGroups;

    private boolean main;

    public static void main(String[] args) throws Throwable {
        SncpTest test = new SncpTest();
        test.main = true;
        test.run();
    }

    @Test
    public void run() throws Exception {
        LoggingBaseHandler.initDebugLogConfig();
        application = Application.create(true);
        rpcGroups = application.getSncpRpcGroups();
        factory = application.getResourceFactory();
        factory.register("", ProtobufConvert.class, ProtobufFactory.root().getConvert());
        factory.register("", Application.class, application);

        if (System.getProperty("client") == null) {
            runServer();
            if (port2 > 0) {
                runServer2();
            }
        }
        if (System.getProperty("server") == null) {
            runClient();
        }
        if (System.getProperty("server") != null) {
            System.in.read();
        }
    }

    private void runClient() throws Exception {
        InetSocketAddress addr = new InetSocketAddress(myhost, port);
        rpcGroups
                .computeIfAbsent("client", protocol.endsWith(".UDP") ? "UDP" : "TCP")
                .putAddress(addr);
        if (port2 > 0) {
            rpcGroups
                    .computeIfAbsent("client", protocol.endsWith(".UDP") ? "UDP" : "TCP")
                    .putAddress(new InetSocketAddress(myhost, port2));
        }
        final AsyncIOGroup asyncGroup = new AsyncIOGroup(clientCapacity, 16);
        asyncGroup.start();

        InetSocketAddress sncpAddress = addr;
        final SncpClient client = new SncpClient(
                "",
                asyncGroup,
                "0",
                sncpAddress,
                new ClientAddress(sncpAddress),
                protocol.endsWith(".UDP") ? "UDP" : "TCP",
                16,
                100);

        final SncpTestIService service = Sncp.createSimpleRemoteService(
                SncpTestIService.class,
                factory,
                rpcGroups,
                client,
                "client"); // Sncp.createSimpleRemoteService(SncpTestIService.class, null, transFactory, addr,
        // "client");
        factory.inject(service);

        //        SncpTestBean bean = new SncpTestBean();
        //        StringBuilder sb = new StringBuilder();
        //        for (int i = 0; i < 2000; i++) {
        //            sb.append("_").append(i).append("_0123456789");
        //        }
        //        bean.setContent(sb.toString());
        //        bean.setContent("hello sncp");
        SncpTestBean callbean = new SncpTestBean();
        callbean.setId(1);
        callbean.setContent("数据X");
        service.queryLongResult("f", 3, 33L);

        callbean = service.insert(callbean);
        System.out.println("bean： " + callbean);
        System.out.println("\r\n\r\n\r\n\r\n---------------------------------------------------");
        Utility.sleep(200);
        final int count = main ? 40 : 11;
        final CountDownLatch cld = new CountDownLatch(count);
        final AtomicInteger ai = new AtomicInteger();
        long s = System.currentTimeMillis();
        for (int i = 10; i < count + 10; i++) {
            final int k = i + 1;
            new Thread() {
                @Override
                public void run() {
                    try {
                        // Thread.sleep(k);
                        SncpTestBean bean = new SncpTestBean();
                        bean.setId(k);
                        bean.setContent("数据: " + k);
                        StringBuilder sb = new StringBuilder();
                        sb.append(k).append("--------");
                        for (int j = 0; j < 1000; j++) {
                            sb.append("_").append(j % 10).append("_").append(k).append("7890_0123456789");
                        }
                        bean.setContent(sb.toString());

                        service.queryResult(bean);
                        // service.updateBean(bean);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        long a = ai.incrementAndGet();
                        System.out.println(
                                "运行了 " + (a == 100 ? "--------------------------------------------------" : "") + a);
                        cld.countDown();
                    }
                }
            }.start();
        }
        cld.await();
        System.out.println("---并发" + count + "次耗时: " + (System.currentTimeMillis() - s) / 1000.0 + "s");
        if (count == 1) {
            if (main) {
                System.exit(0);
            }
            return;
        }
        Utility.sleep(200);
        final CountDownLatch cld2 = new CountDownLatch(1);
        long s2 = System.currentTimeMillis();
        final CompletableFuture<String> future = service.queryResultAsync(callbean);
        future.whenComplete((v, e) -> {
            System.out.println(
                    "异步执行结果: " + v + ", 异常为: " + e + ", 耗时: " + (System.currentTimeMillis() - s2) / 1000.0 + "s");
            cld2.countDown();
        });
        cld2.await();
        System.out.println("---全部运行完毕---");
    }

    private static void runServer() throws Exception {
        InetSocketAddress addr = new InetSocketAddress(myhost, port);
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
                    AnyValueWriter conf = new AnyValueWriter();
                    conf.addValue("host", "0.0.0.0");
                    conf.addValue("port", "" + port);
                    conf.addValue("protocol", protocol);
                    conf.addValue("maxbody", "" + (100 * 1024 * 1024));
                    SncpServer server = new SncpServer(null, System.currentTimeMillis(), conf, factory);
                    if (port2 > 0) {
                        rpcGroups
                                .computeIfAbsent("server", protocol.endsWith(".UDP") ? "UDP" : "TCP")
                                .putAddress(new InetSocketAddress(myhost, port2));
                    }

                    SncpTestIService service = Sncp.createSimpleLocalService(
                            SncpTestServiceImpl.class,
                            factory); // Sncp.createSimpleLocalService(SncpTestServiceImpl.class, null, factory,
                    // transFactory, addr, "server");
                    factory.inject(service);
                    server.addSncpServlet(service);
                    System.out.println(service);
                    server.init(conf);
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

    private static void runServer2() throws Exception {
        InetSocketAddress addr = new InetSocketAddress(myhost, port2);
        final CountDownLatch cdl = new CountDownLatch(1);
        final AsyncIOGroup asyncGroup = new AsyncIOGroup(8196, 16);
        asyncGroup.start();
        new Thread() {
            {
                setName("Thread-Server-02");
            }

            @Override
            public void run() {
                try {
                    AnyValueWriter conf = new AnyValueWriter();
                    conf.addValue("host", "0.0.0.0");
                    conf.addValue("port", "" + (port2 < 10 ? 0 : port2));
                    conf.addValue("protocol", protocol);
                    conf.addValue("maxbody", "" + (100 * 1024 * 1024));
                    SncpServer server = new SncpServer(null, System.currentTimeMillis(), conf, factory);
                    rpcGroups
                            .computeIfAbsent("server", protocol.endsWith(".UDP") ? "UDP" : "TCP")
                            .putAddress(new InetSocketAddress(myhost, port));

                    Service service = Sncp.createSimpleLocalService(
                            SncpTestServiceImpl.class,
                            factory); // Sncp.createSimpleLocalService(SncpTestServiceImpl.class, null, factory,
                    // transFactory, addr, "server");
                    server.addSncpServlet(service);
                    server.init(conf);
                    server.start();
                    port2 = server.getSocketAddress().getPort();
                    cdl.countDown();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();
        cdl.await();
    }
}
