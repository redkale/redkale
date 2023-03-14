/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.service;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;
import org.redkale.annotation.*;
import org.redkale.boot.*;
import org.redkale.convert.bson.*;
import org.redkale.convert.json.*;
import org.redkale.net.*;
import org.redkale.net.http.*;
import org.redkale.net.sncp.*;
import org.redkale.service.*;
import org.redkale.util.AnyValue.DefaultAnyValue;
import org.redkale.util.*;

/**
 *
 * @author zhangjx
 */
@RestService(name = "abmain")
public class ABMainService implements Service {

    @Resource
    private BCService bcService;

    public static void remote(String[] args) throws Throwable {
        System.out.println("------------------- 远程模式调用 -----------------------------------");
        final Application application = Application.create(true);
        final int abport = 8888;
        final AsyncIOGroup asyncGroup = new AsyncIOGroup(8192, 16);
        asyncGroup.start();
        ResourceFactory resFactory = ResourceFactory.create();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        final TransportFactory transFactory = TransportFactory.create(asyncGroup, 0, 0);
        transFactory.addGroupInfo("g77", new InetSocketAddress("127.0.0.1", 5577));
        transFactory.addGroupInfo("g88", new InetSocketAddress("127.0.0.1", 5588));
        transFactory.addGroupInfo("g99", new InetSocketAddress("127.0.0.1", 5599));

        resFactory.register(JsonConvert.root());
        resFactory.register(BsonConvert.root());

        //------------------------ 初始化 CService ------------------------------------
        CService cservice = Sncp.createSimpleLocalService(CService.class, null, resFactory, transFactory, new InetSocketAddress("127.0.0.1", 5577), "g77");
        SncpServer cserver = new SncpServer();
        cserver.getResourceFactory().register(application);
        cserver.getLogger().setLevel(Level.WARNING);
        cserver.addSncpServlet(cservice);
        cserver.init(DefaultAnyValue.create("port", 5577));
        cserver.start();

        //------------------------ 初始化 BCService ------------------------------------
        BCService bcservice = Sncp.createSimpleLocalService(BCService.class, null, resFactory, transFactory, new InetSocketAddress("127.0.0.1", 5588), "g88");
        CService remoteCService = Sncp.createSimpleRemoteService(CService.class, null, transFactory, new InetSocketAddress("127.0.0.1", 5588), "g77");
        resFactory.inject(remoteCService);
        resFactory.register("", remoteCService);
        SncpServer bcserver = new SncpServer();
        bcserver.getResourceFactory().register(application);
        bcserver.getLogger().setLevel(Level.WARNING);
        bcserver.addSncpServlet(bcservice);
        bcserver.init(DefaultAnyValue.create("port", 5588));
        bcserver.start();

        //------------------------ 初始化 ABMainService ------------------------------------
        ABMainService service = Sncp.createSimpleLocalService(ABMainService.class, null, resFactory, transFactory, new InetSocketAddress("127.0.0.1", 5599), "g99");
        BCService remoteBCService = Sncp.createSimpleRemoteService(BCService.class, null, transFactory, new InetSocketAddress("127.0.0.1", 5599), "g88");
        resFactory.inject(remoteBCService);
        resFactory.register("", remoteBCService);

        HttpServer server = new HttpServer();
        server.getResourceFactory().register(application);
        server.getLogger().setLevel(Level.WARNING);

        server.addRestServlet(null, service, null, HttpServlet.class, "/pipes");

        resFactory.inject(cservice);
        resFactory.inject(bcservice);
        resFactory.inject(service);

        server.init(DefaultAnyValue.create("port", abport));
        server.start();
        Thread.sleep(100);
        System.out.println("开始请求");
        //同步方法
        String url = "http://127.0.0.1:" + abport + "/pipes/abmain/syncabtime/张先生";
        System.out.println(Utility.postHttpContent(url));

        //异步方法
        url = "http://127.0.0.1:" + abport + "/pipes/abmain/asyncabtime/张先生";
        System.out.println(Utility.postHttpContent(url));

        //异步方法
        url = "http://127.0.0.1:" + abport + "/pipes/abmain/asyncabtime2/张先生";
        System.out.println(Utility.postHttpContent(url));

        server.shutdown();
    }

    public static void main(String[] args) throws Throwable {
        System.out.println("------------------- 本地模式调用 -----------------------------------");
        final Application application = Application.create(true);
        final int abport = 8888;
        ResourceFactory factory = ResourceFactory.create();

        ABMainService service = new ABMainService();

        BCService bcservice = new BCService();
        factory.register("", bcservice);
        factory.register("", new CService());
        factory.inject(bcservice);
        factory.inject(service);
        System.out.println("bcservice.name = " + bcservice.serviceName());
        System.out.println("bcservice.type = " + bcservice.serviceType());

        HttpServer server = new HttpServer();
        server.getResourceFactory().register(application);
        server.getLogger().setLevel(Level.WARNING);

        server.addRestServlet(null, service, null, HttpServlet.class, "/pipes");

        server.init(DefaultAnyValue.create("port", "" + abport));
        server.start();
        Thread.sleep(100);

        //同步方法
        String url = "http://127.0.0.1:" + abport + "/pipes/abmain/syncabtime/张先生";
        System.out.println(Utility.postHttpContent(url));

        //异步方法
        url = "http://127.0.0.1:" + abport + "/pipes/abmain/asyncabtime/张先生";
        System.out.println(Utility.postHttpContent(url));

        //异步方法
        url = "http://127.0.0.1:" + abport + "/pipes/abmain/asyncabtime2/张先生";
        System.out.println(Utility.postHttpContent(url));

        server.shutdown();
        //远程模式
        remote(args);
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
        return ObjectPool.createSafePool(new LongAdder(), new LongAdder(), 16,
            (Object... params) -> ByteBuffer.allocateDirect(8192), null, (e) -> {
                if (e == null || e.isReadOnly() || e.capacity() != 8192) {
                    return false;
                }
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
    public void abCurrentTime(final CompletionHandler<String, Void> handler, @RestParam(name = "#") final String name) {
        bcService.bcCurrentTime(Utility.createAsyncHandler((v, a) -> {
            System.out.println("执行了 ABMainService.abCurrentTime----异步方法");
            String rs = "异步abCurrentTime: " + v;
            if (handler != null) {
                handler.completed(rs, a);
            }
        }, (t, a) -> {
            if (handler != null) {
                handler.failed(t, a);
            }
        }), name);
    }

    @RestMapping(name = "asyncabtime2")
    public void abCurrentTime(final MyAsyncHandler<String, Void> handler, @RestParam(name = "#") final String name) {
        bcService.bcCurrentTime(new MyAsyncHandler<String, Void>() {
            @Override
            public int id() {
                return 1;
            }

            @Override
            public void completed(String v, Void a) {
                System.out.println("执行了 ABMainService.abCurrentTime----异步方法2");
                String rs = "异步abCurrentTime: " + v;
                if (handler != null) {
                    handler.completed(rs, a);
                }
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
            }

            @Override
            public int id2() {
                return 2;
            }
        }, name);
    }
}
