/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.Level;
import org.redkale.annotation.Resource;
import org.redkale.boot.*;
import org.redkale.convert.json.JsonConvert;
import org.redkale.convert.pb.ProtobufConvert;
import org.redkale.inject.ResourceFactory;
import org.redkale.net.AsyncIOGroup;
import org.redkale.net.client.ClientAddress;
import org.redkale.net.http.*;
import org.redkale.net.sncp.*;
import org.redkale.service.Service;
import org.redkale.util.*;

/** @author zhangjx */
@RestService(name = "abmain")
public class ABMainService implements Service {

    private static final int abport = 8866;

    @Resource
    private BCService bcService;

    public static void remote(String[] args) throws Throwable {
        System.out.println("------------------- 远程模式调用 -----------------------------------");
        final Application application = Application.create(true);
        final AsyncIOGroup asyncGroup = new AsyncIOGroup(8192, 16);
        asyncGroup.start();
        InetSocketAddress sncpAddress = new InetSocketAddress("127.0.0.1", abport);
        final SncpClient client =
                new SncpClient("", asyncGroup, "0", sncpAddress, new ClientAddress(sncpAddress), "TCP", 16, 100);
        final ResourceFactory resFactory = ResourceFactory.create();
        resFactory.register(JsonConvert.root());
        resFactory.register(ProtobufConvert.root());
        final SncpRpcGroups rpcGroups = application.getSncpRpcGroups();
        rpcGroups.computeIfAbsent("g77", "TCP").putAddress(new InetSocketAddress("127.0.0.1", 5577));
        rpcGroups.computeIfAbsent("g88", "TCP").putAddress(new InetSocketAddress("127.0.0.1", 5588));
        rpcGroups.computeIfAbsent("g99", "TCP").putAddress(new InetSocketAddress("127.0.0.1", 5599));

        // ------------------------ 初始化 CService ------------------------------------
        CService cservice = Sncp.createSimpleLocalService(CService.class, resFactory);
        SncpServer cserver = new SncpServer();
        cserver.getResourceFactory().register(application);
        // cserver.getLogger().setLevel(Level.WARNING);
        cserver.addSncpServlet(cservice);
        cserver.init(AnyValueWriter.create("port", 5577));
        cserver.start();

        // ------------------------ 初始化 BCService ------------------------------------
        BCService bcservice = Sncp.createSimpleLocalService(BCService.class, resFactory);
        CService remoteCService = Sncp.createSimpleRemoteService(CService.class, resFactory, rpcGroups, client, "g77");
        if (remoteCService != null) {
            resFactory.inject(remoteCService);
            resFactory.register("", remoteCService);
        }
        SncpServer bcserver = new SncpServer();
        bcserver.getResourceFactory().register(application);
        // bcserver.getLogger().setLevel(Level.WARNING);
        bcserver.addSncpServlet(bcservice);
        bcserver.init(AnyValueWriter.create("port", 5588));
        bcserver.start();

        // ------------------------ 初始化 ABMainService ------------------------------------
        ABMainService service = Sncp.createSimpleLocalService(ABMainService.class, resFactory);
        BCService remoteBCService =
                Sncp.createSimpleRemoteService(BCService.class, resFactory, rpcGroups, client, "g88");
        if (remoteBCService != null) {
            resFactory.inject(remoteBCService);
            resFactory.register("", remoteBCService);
        }

        resFactory.inject(cservice);
        resFactory.inject(bcservice);
        resFactory.inject(service);

        HttpServer server = new HttpServer();
        server.getResourceFactory().register(application);
        // server.getLogger().setLevel(Level.WARNING);

        server.init(AnyValueWriter.create("port", abport));
        server.addRestServlet(null, service, null, HttpServlet.class, "/pipes");
        server.start();
        Utility.sleep(100);
        System.out.println("开始请求");

        // 不声明一个新的HttpClient会导致Utility.postHttpContent操作
        // 同一url在Utility里的httpClient会缓存导致调用是吧，应该是httpClient的bug
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
        System.out.println("httpclient类: " + httpClient.getClass().getName());
        // 同步方法
        String url = "http://127.0.0.1:" + abport + "/pipes/abmain/sab/张先生";
        System.out.println(Utility.postHttpContentAsync(httpClient, url, StandardCharsets.UTF_8, null)
                .join());

        // 异步方法
        url = "http://127.0.0.1:" + abport + "/pipes/abmain/abc/张先生";
        System.out.println(Utility.postHttpContentAsync(httpClient, url, StandardCharsets.UTF_8, null)
                .join());

        // 异步方法
        url = "http://127.0.0.1:" + abport + "/pipes/abmain/abc2/张先生";
        System.out.println(Utility.postHttpContentAsync(httpClient, url, StandardCharsets.UTF_8, null)
                .join());

        server.shutdown();
        bcserver.shutdown();
        cserver.shutdown();
    }

    public static void main(String[] args) throws Throwable {
        LoggingBaseHandler.initDebugLogConfig();
        System.out.println("------------------- 本地模式调用 -----------------------------------");
        final Application application = Application.create(true);
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

        server.init(AnyValueWriter.create("port", "" + abport));
        server.start();
        Thread.sleep(100);

        System.out.println("开始请求");
        // 同步方法
        String url = "http://127.0.0.1:" + abport + "/pipes/abmain/sab/张先生";
        System.out.println(Utility.postHttpContent(url));

        // 异步方法
        url = "http://127.0.0.1:" + abport + "/pipes/abmain/abc/张先生";
        System.out.println(Utility.postHttpContent(url));

        // 异步方法
        url = "http://127.0.0.1:" + abport + "/pipes/abmain/abc2/张先生";
        System.out.println(Utility.postHttpContent(url));

        server.shutdown();
        // 远程模式
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
        return ObjectPool.createSafePool(
                new LongAdder(),
                new LongAdder(),
                16,
                (Object... params) -> ByteBuffer.allocateDirect(8192),
                null,
                (e) -> {
                    if (e == null || e.isReadOnly() || e.capacity() != 8192) {
                        return false;
                    }
                    e.clear();
                    return true;
                });
    }

    @RestMapping(name = "sab")
    public String abCurrentTime(@RestParam(name = "#") final String name) {
        System.out.println("准备执行ABMainService.sab方法");
        String rs = "同步abCurrentTime: " + bcService.bcCurrentTime1(name);
        System.out.println("执行了 ABMainService.abCurrentTime++++同步方法");
        return rs;
    }

    @RestMapping(name = "abc")
    public void abCurrentTime(final CompletionHandler<String, Void> handler, @RestParam(name = "#") final String name) {
        bcService.bcCurrentTime2(
                Utility.createAsyncHandler(
                        (v, a) -> {
                            System.out.println("执行了 ABMainService.abCurrentTime----异步方法");
                            String rs = "异步abCurrentTime: " + v;
                            if (handler != null) {
                                handler.completed(rs, a);
                            }
                        },
                        (t, a) -> {
                            if (handler != null) {
                                handler.failed(t, a);
                            }
                        }),
                name);
    }

    @RestMapping(name = "abc2")
    public void abCurrentTime(final MyAsyncHandler<String, Void> handler, @RestParam(name = "#") final String name) {
        bcService.bcCurrentTime3(
                new MyAsyncHandler<String, Void>() {
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
                    public void failed(Throwable exc, Void attachment) {}

                    @Override
                    public int id2() {
                        return 2;
                    }
                },
                name);
    }
}
