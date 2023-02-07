/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.sncp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousChannelGroup;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.redkale.boot.*;
import org.redkale.convert.bson.*;
import org.redkale.net.*;
import org.redkale.net.sncp.*;
import org.redkale.service.Service;
import org.redkale.util.*;

/**
 *
 * @author zhangjx
 */
public class SncpTest {

    private static final String myhost = "127.0.0.1";

    private static int port = 0;

    private static int port2 = 4240;

    private static final String protocol = "SNCP.UDP";

    private static final int clientCapacity = protocol.endsWith(".UDP") ? AsyncGroup.UDP_BUFFER_CAPACITY : 8192;

    private static final ResourceFactory factory = ResourceFactory.create();

    public static void main(String[] args) throws Exception {
        LoggingBaseHandler.initDebugLogConfig();
        factory.register("", BsonConvert.class, BsonFactory.root().getConvert());
        factory.register("", Application.class, Application.create(true));
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

    public static AsynchronousChannelGroup newChannelGroup() throws IOException {
        final AtomicInteger counter = new AtomicInteger();
        ExecutorService transportExec = Executors.newFixedThreadPool(16, (Runnable r) -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("Transport-Thread-" + counter.incrementAndGet());
            return t;
        });
        return AsynchronousChannelGroup.withCachedThreadPool(transportExec, 1);
    }

    private static void runClient() throws Exception {
        InetSocketAddress addr = new InetSocketAddress(myhost, port);
        Set<InetSocketAddress> set = new LinkedHashSet<>();
        set.add(addr);
        if (port2 > 0) {
            set.add(new InetSocketAddress(myhost, port2));
        }
        final AsyncIOGroup asyncGroup = new AsyncIOGroup(clientCapacity, 16);
        asyncGroup.start();
        final TransportFactory transFactory = TransportFactory.create(asyncGroup, protocol.endsWith(".UDP") ? "UDP" : "TCP", 0, 0);
        transFactory.addGroupInfo("client", set);
        final SncpTestIService service = Sncp.createSimpleRemoteService(SncpTestIService.class, null, transFactory, addr, "client");
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
        System.out.println("---------------------------------------------------");
        Thread.sleep(200);
        final int count = 10;
        final CountDownLatch cld = new CountDownLatch(count);
        final AtomicInteger ai = new AtomicInteger();
        long s = System.currentTimeMillis();
        for (int i = 10; i < count + 10; i++) {
            final int k = i + 1;
            new Thread() {
                @Override
                public void run() {
                    try {
                        //Thread.sleep(k);
                        SncpTestBean bean = new SncpTestBean();
                        bean.setId(k);
                        bean.setContent("数据: " + k);
                        StringBuilder sb = new StringBuilder();
                        sb.append(k).append("--------");
                        for (int j = 0; j < 2000; j++) {
                            sb.append("_").append(j).append("_").append(k).append("_0123456789");
                        }
                        bean.setContent(sb.toString());

                        service.queryResult(bean);
                        //service.updateBean(bean);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        long a = ai.incrementAndGet();
                        System.out.println("运行了 " + (a == 100 ? "--------------------------------------------------" : "") + a);
                        cld.countDown();
                    }
                }
            }.start();
        }
        cld.await();
        System.out.println("---并发" + count + "次耗时: " + (System.currentTimeMillis() - s) / 1000.0 + "s");
        if (count == 1) {
            System.exit(0);
            return;
        }
        final CountDownLatch cld2 = new CountDownLatch(1);
        long s2 = System.currentTimeMillis();
        final CompletableFuture<String> future = service.queryResultAsync(callbean);
        future.whenComplete((v, e) -> {
            System.out.println("异步执行完毕: " + v + ", 异常为: " + e + ", 耗时: " + (System.currentTimeMillis() - s2) / 1000.0 + "s");
            cld2.countDown();
        });
        cld2.await();
        System.out.println("---全部运行完毕---");
        System.exit(0);
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
                    AnyValue.DefaultAnyValue conf = new AnyValue.DefaultAnyValue();
                    conf.addValue("host", "0.0.0.0");
                    conf.addValue("port", "" + port);
                    conf.addValue("protocol", protocol);
                    SncpServer server = new SncpServer(null, System.currentTimeMillis(), conf, factory);
                    Set<InetSocketAddress> set = new LinkedHashSet<>();
                    if (port2 > 0) {
                        set.add(new InetSocketAddress(myhost, port2));
                    }
                    final TransportFactory transFactory = TransportFactory.create(asyncGroup, protocol.endsWith(".UDP") ? "UDP" : "TCP", 0, 0);
                    transFactory.addGroupInfo("server", set);
                    SncpTestIService service = Sncp.createSimpleLocalService(SncpTestServiceImpl.class, null, factory, transFactory, addr, "server");
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
                    AnyValue.DefaultAnyValue conf = new AnyValue.DefaultAnyValue();
                    conf.addValue("host", "0.0.0.0");
                    conf.addValue("port", "" + (port2 < 10 ? 0 : port2));
                    conf.addValue("protocol", protocol);
                    SncpServer server = new SncpServer(null, System.currentTimeMillis(), conf, factory);
                    Set<InetSocketAddress> set = new LinkedHashSet<>();
                    set.add(new InetSocketAddress(myhost, port));

                    final TransportFactory transFactory = TransportFactory.create(asyncGroup, protocol.endsWith(".UDP") ? "UDP" : "TCP", 0, 0);
                    transFactory.addGroupInfo("server", set);
                    Service service = Sncp.createSimpleLocalService(SncpTestServiceImpl.class, null, factory, transFactory, addr, "server");
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
