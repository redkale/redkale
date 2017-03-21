/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.util.HashSet;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;
import javax.annotation.Resource;
import org.redkale.convert.bson.BsonConvert;
import org.redkale.convert.json.JsonConvert;
import org.redkale.net.*;
import org.redkale.net.http.*;
import org.redkale.net.sncp.*;
import org.redkale.service.Service;
import org.redkale.util.AnyValue.DefaultAnyValue;
import org.redkale.util.*;
import org.redkale.watch.WatchFactory;

/**
 *
 * @author zhangjx
 */
@RestService(name = "abmain")
public class ABMainService implements Service {

    @Resource
    private BCService bcService;

    public static void remotemain(String[] args) throws Throwable {
        System.out.println("------------------- 远程模式调用 -----------------------------------");
        final int abport = 8888;
        ResourceFactory factory = ResourceFactory.root();
        factory.register(JsonConvert.root());
        factory.register(BsonConvert.root());
        //------------------------ 初始化 CService ------------------------------------
        CService cservice = Sncp.createLocalService("", null, ResourceFactory.root(), CService.class, new InetSocketAddress("127.0.0.1", 5577), null, null);
        SncpServer cserver = new SncpServer();
        cserver.addSncpServlet(new ServiceWrapper(cservice, "", "", new HashSet<>(), null));
        cserver.init(DefaultAnyValue.create("port", 5577));
        cserver.start();

        //------------------------ 初始化 BCService ------------------------------------
        final Transport bctransport = new Transport("", WatchFactory.root(), "", newBufferPool(), newChannelGroup(), null, Utility.ofSet(new InetSocketAddress("127.0.0.1", 5577)));
        BCService bcservice = Sncp.createLocalService("", null, ResourceFactory.root(), BCService.class, new InetSocketAddress("127.0.0.1", 5588), bctransport, null);
        CService remoteCService = Sncp.createRemoteService("", null, CService.class, new InetSocketAddress("127.0.0.1", 5588), bctransport);
        factory.inject(remoteCService);
        factory.register("", remoteCService);
        SncpServer bcserver = new SncpServer();
        bcserver.addSncpServlet(new ServiceWrapper(bcservice, "", "", new HashSet<>(), null));
        bcserver.init(DefaultAnyValue.create("port", 5588));
        bcserver.start();

        //------------------------ 初始化 ABMainService ------------------------------------
        final Transport abtransport = new Transport("", WatchFactory.root(), "", newBufferPool(), newChannelGroup(), null, Utility.ofSet(new InetSocketAddress("127.0.0.1", 5588)));
        ABMainService service = Sncp.createLocalService("", null, ResourceFactory.root(), ABMainService.class, new InetSocketAddress("127.0.0.1", 5599), bctransport, null);
        BCService remoteBCService = Sncp.createRemoteService("", null, BCService.class, new InetSocketAddress("127.0.0.1", 5599), abtransport);
        factory.inject(remoteBCService);
        factory.register("", remoteBCService);

        HttpServer server = new HttpServer();

        server.addRestServlet("", ABMainService.class, service, DefaultRestServlet.class, "/pipes");

        factory.inject(cservice);
        factory.inject(bcservice);
        factory.inject(service);

        server.init(DefaultAnyValue.create("port", abport));
        server.start();
        Thread.sleep(100);

        //同步方法
        String url = "http://127.0.0.1:" + abport + "/pipes/abmain/syncabtime/张先生";
        System.out.println(Utility.postHttpContent(url));

        //异步方法
        url = "http://127.0.0.1:" + abport + "/pipes/abmain/asyncabtime/张先生";
        System.out.println(Utility.postHttpContent(url));

        server.shutdown();
    }

    public static void main(String[] args) throws Throwable {
        Logger.getLogger(Server.class.getSimpleName()).setLevel(Level.WARNING);
        Logger.getLogger(HttpServer.class.getSimpleName()).setLevel(Level.WARNING);
        Logger.getLogger(SncpServer.class.getSimpleName()).setLevel(Level.WARNING);
        System.out.println("------------------- 本地模式调用 -----------------------------------");
        final int abport = 8888;
        ResourceFactory factory = ResourceFactory.root();

        ABMainService service = new ABMainService();

        BCService bcservice = new BCService();
        factory.register("", bcservice);
        factory.register("", new CService());
        factory.inject(bcservice);
        factory.inject(service);

        HttpServer server = new HttpServer();

        server.addRestServlet("", ABMainService.class, service, DefaultRestServlet.class, "/pipes");

        server.init(DefaultAnyValue.create("port", "" + abport));
        server.start();
        Thread.sleep(100);

        //同步方法
        String url = "http://127.0.0.1:" + abport + "/pipes/abmain/syncabtime/张先生";
        System.out.println(Utility.postHttpContent(url));

        //异步方法
        url = "http://127.0.0.1:" + abport + "/pipes/abmain/asyncabtime/张先生";
        System.out.println(Utility.postHttpContent(url));

        server.shutdown();
        //远程模式
        remotemain(args);
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

    public static ObjectPool<ByteBuffer> newBufferPool() {
        return new ObjectPool<>(new AtomicLong(), new AtomicLong(), 16,
            (Object... params) -> ByteBuffer.allocateDirect(8192), null, (e) -> {
                if (e == null || e.isReadOnly() || e.capacity() != 8192) return false;
                e.clear();
                return true;
            });
    }

    @RestMapping(name = "syncabtime")
    public String abCurrentTime(@RestParam(name = "#") final String name) {
        String rs = "同步abCurrentTime: " + bcService.bcCurrentTime(name);
        System.out.println("执行了 ABMainService.abCurrentTime++++同步方法");
        return rs;
    }

    @RestMapping(name = "asyncabtime")
    public void abCurrentTime(final AsyncHandler<String, Void> handler, @RestParam(name = "#") final String name) {
        bcService.bcCurrentTime(AsyncHandler.create((v, a) -> {
            System.out.println("执行了 ABMainService.abCurrentTime----异步方法");
            String rs = "异步abCurrentTime: " + v;
            if (handler != null) handler.completed(rs, a);
        }, (t, a) -> {
            if (handler != null) handler.failed(t, a);
        }), name);
    }
}
