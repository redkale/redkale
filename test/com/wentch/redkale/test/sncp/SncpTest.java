/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.test.sncp;

import com.wentch.redkale.boot.*;
import com.wentch.redkale.convert.bson.*;
import com.wentch.redkale.net.*;
import com.wentch.redkale.net.sncp.*;
import com.wentch.redkale.service.*;
import com.wentch.redkale.util.*;
import com.wentch.redkale.watch.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;

/**
 *
 * @author zhangjx
 */
public class SncpTest {

    private static final String serviceName = "";

    private static final String myhost = Utility.localInetAddress().getHostAddress();

    private static final int port = 4040;

    private static final int port2 = 0; // 4240;

    public static void main(String[] args) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        final PrintStream ps = new PrintStream(out);
        ps.println("handlers = java.util.logging.ConsoleHandler");
        ps.println(".handlers = java.util.logging.ConsoleHandler");
        ps.println(".level = FINEST");
        ps.println("java.util.logging.ConsoleHandler.level = FINEST");
        LogManager.getLogManager().readConfiguration(new ByteArrayInputStream(out.toByteArray()));
        ResourceFactory.root().register("", BsonConvert.class, BsonFactory.root().getConvert());
        if (System.getProperty("client") == null) {
            runServer();
            if (port2 > 0) runServer2();
        }
        if (System.getProperty("server") == null) {
            runClient();
        }
        if (System.getProperty("server") != null) {
            System.in.read();
        }
    }

    private static void runClient() throws Exception {
        InetSocketAddress addr = new InetSocketAddress(myhost, port);
        Set<InetSocketAddress> set = new LinkedHashSet<>();
        set.add(addr);
        if (port2 > 0) set.add(new InetSocketAddress(myhost, port2));
        final Transport transport = new Transport("", WatchFactory.root(), 50, set);
        final SncpTestService service = Sncp.createRemoteService(serviceName, null, SncpTestService.class, null, new LinkedHashSet<>(), transport);
        ResourceFactory.root().inject(service);

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

        service.insert(callbean);
        System.out.println("bean.id应该会被修改： " + callbean);
        System.out.println("---------------------------------------------------");
        final int count = 10;
        final CountDownLatch cld = new CountDownLatch(count);
        final AtomicInteger ai = new AtomicInteger();
        for (int i = 0; i < count; i++) {
            final int k = i + 1;
            new Thread() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(k);
                        SncpTestBean bean = new SncpTestBean();
                        bean.setId(k);
                        bean.setContent("数据: " + (k < 10 ? "0" : "") + k);
                        StringBuilder sb = new StringBuilder();
                        sb.append(k).append("------");
                        for (int i = 0; i < 1200; i++) {
                            sb.append("_").append(i).append("_").append(k).append("_0123456789");
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
        System.out.println("---全部运行完毕---");
        System.exit(0);
    }

    private static void runServer() throws Exception {
        InetSocketAddress addr = new InetSocketAddress(myhost, port);
        final CountDownLatch cdl = new CountDownLatch(1);
        new Thread() {
            @Override
            public void run() {
                try {
                    SncpServer server = new SncpServer();
                    Set<InetSocketAddress> set = new LinkedHashSet<>();
                    if (port2 > 0) set.add(new InetSocketAddress(myhost, port2));
                    Transport transport = new Transport("", WatchFactory.root(), 50, set);
                    List<Transport> sameTransports = new ArrayList<>();
                    if (port2 > 0) sameTransports.add(transport);
                    SncpTestService service = Sncp.createLocalService("", null, SncpTestService.class, addr, new LinkedHashSet<>(), sameTransports, null);
                    ResourceFactory.root().inject(service);
                    server.addService(new ServiceWrapper(SncpTestService.class, service, "", new ClassFilter.FilterEntry(SncpTestService.class, null)));
                    System.out.println(service);
                    AnyValue.DefaultAnyValue conf = new AnyValue.DefaultAnyValue();
                    conf.addValue("host", "0.0.0.0");
                    conf.addValue("port", "" + port);
                    server.init(conf);
                    server.start();
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
        new Thread() {
            @Override
            public void run() {
                try {
                    SncpServer server = new SncpServer();
                    Set<InetSocketAddress> set = new LinkedHashSet<>();
                    set.add(new InetSocketAddress(myhost, port));
                    Transport transport = new Transport("", WatchFactory.root(), 50, set);
                    List<Transport> sameTransports = new ArrayList<>();
                    sameTransports.add(transport);
                    Service service = Sncp.createLocalService("", null, SncpTestService.class, addr, new LinkedHashSet<>(), sameTransports, null);
                    server.addService(new ServiceWrapper(SncpTestService.class, service, "", new ClassFilter.FilterEntry(SncpTestService.class, null)));
                    AnyValue.DefaultAnyValue conf = new AnyValue.DefaultAnyValue();
                    conf.addValue("host", "0.0.0.0");
                    conf.addValue("port", "" + port2);
                    server.init(conf);
                    server.start();
                    cdl.countDown();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();
        cdl.await();
    }
}
