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
import com.wentch.redkale.util.*;
import com.wentch.redkale.watch.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

/**
 *
 * @author zhangjx
 */
public class SncpTest {

    private static final String protocol = Sncp.DEFAULT_PROTOCOL;

    private static final String serviceName = "";

    private static final int port = 4040;

    private static final int port2 = 4240;

    public static void main(String[] args) throws Exception {
        ResourceFactory.root().register("", BsonConvert.class, BsonFactory.root().getConvert());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        final PrintStream ps = new PrintStream(out);
        ps.println("handlers = java.util.logging.ConsoleHandler");
        ps.println(".handlers = java.util.logging.ConsoleHandler");
        ps.println(".level = FINEST");
        ps.println("java.util.logging.ConsoleHandler.level = FINEST");
        LogManager.getLogManager().readConfiguration(new ByteArrayInputStream(out.toByteArray()));
        runServer();
        runServer2();
        runClient();
    }

    private static void runClient() throws Exception {
        InetSocketAddress addr = new InetSocketAddress("127.0.0.1", port);
        Set<InetSocketAddress> set = new LinkedHashSet<>();
        set.add(addr);
        set.add(new InetSocketAddress("127.0.0.1", port2));
        Transport transport = new Transport("", protocol, WatchFactory.root(), 100, set);
        SncpTestService service = Sncp.createRemoteService(serviceName, SncpTestService.class, null, transport);
        ResourceFactory.root().inject(service);

        SncpTestBean bean = new SncpTestBean();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2000; i++) {
            sb.append("_").append(i).append("_0123456789");
        }
        bean.setContent(sb.toString());
        bean.setContent("hello sncp");
        System.out.println(service.queryResult(bean));
        bean.setContent("xxxxx");
        System.out.println(service.updateBean(bean)); 
    }

    private static void runServer() throws Exception {
        InetSocketAddress addr = new InetSocketAddress("127.0.0.1", port);
        final CountDownLatch cdl = new CountDownLatch(1);
        new Thread() {
            @Override
            public void run() {
                try {
                    SncpServer server = new SncpServer(protocol);
                    Set<InetSocketAddress> set = new LinkedHashSet<>();
                    set.add(new InetSocketAddress("127.0.0.1", port2));
                    Transport transport = new Transport("", protocol, WatchFactory.root(), 100, set);
                    List<Transport> sameTransports = new ArrayList<>();
                    sameTransports.add(transport);
                    SncpTestService service = Sncp.createLocalService("", SncpTestService.class, addr, sameTransports, null);
                    ResourceFactory.root().inject(service);
                    server.addService(new ServiceWrapper(SncpTestService.class, service, new ClassFilter.FilterEntry(SncpTestService.class, null)));
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
        InetSocketAddress addr = new InetSocketAddress("127.0.0.1", port2);
        final CountDownLatch cdl = new CountDownLatch(1);
        new Thread() {
            @Override
            public void run() {
                try {
                    SncpServer server = new SncpServer(protocol);
                    Set<InetSocketAddress> set = new LinkedHashSet<>();
                    set.add(new InetSocketAddress("127.0.0.1", port));
                    Transport transport = new Transport("", protocol, WatchFactory.root(), 100, set);
                    List<Transport> sameTransports = new ArrayList<>();
                    sameTransports.add(transport);
                    server.addService(new ServiceWrapper(SncpTestService.class, Sncp.createLocalService("", SncpTestService.class, addr, sameTransports, null), new ClassFilter.FilterEntry(SncpTestService.class, null)));
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
