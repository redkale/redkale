package org.redkale.test.sncp;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.*;
import org.redkale.boot.Application;
import org.redkale.convert.json.JsonConvert;
import org.redkale.convert.pb.ProtobufConvert;
import org.redkale.inject.ResourceFactory;
import org.redkale.net.AsyncIOGroup;
import org.redkale.net.WorkThread;
import org.redkale.net.client.ClientAddress;
import org.redkale.net.sncp.*;
import org.redkale.util.*;

/** @author zhangjx */
public class SncpSleepTest {

    private boolean main;

    public static void main(String[] args) throws Throwable {
        SncpSleepTest test = new SncpSleepTest();
        test.main = true;
        test.run();
    }

    @Test
    public void run() throws Exception {
        Creator.create(List.class);
        System.out.println("------------------- 并发调用 -----------------------------------");
        final Application application = Application.create(true);
        final ExecutorService workExecutor = WorkThread.createWorkExecutor(10, "Thread-Work-%s");
        final AsyncIOGroup asyncGroup = new AsyncIOGroup("Redkale-TestClient-IOThread-%s", workExecutor, 8192, 16);
        asyncGroup.start();
        final ResourceFactory resFactory = ResourceFactory.create();
        resFactory.register(Application.RESNAME_APP_EXECUTOR, ExecutorService.class, workExecutor);
        resFactory.register(JsonConvert.root());
        resFactory.register(ProtobufConvert.root());

        // ------------------------ 初始化 CService ------------------------------------
        SncpSleepService service = Sncp.createSimpleLocalService(SncpSleepService.class, resFactory);
        resFactory.inject(service);
        SncpServer server = new SncpServer(application, System.currentTimeMillis(), null, resFactory);
        server.getResourceFactory().register(application);
        server.addSncpServlet(service);
        server.init(AnyValueWriter.create("port", 0));
        server.start();

        int port = server.getSocketAddress().getPort();
        System.out.println("SNCP服务器启动端口: " + port);
        InetSocketAddress sncpAddress = new InetSocketAddress("127.0.0.1", port);
        final SncpClient client =
                new SncpClient("", asyncGroup, "0", sncpAddress, new ClientAddress(sncpAddress), "TCP", 16, 100);
        final SncpRpcGroups rpcGroups = application.getSncpRpcGroups();
        rpcGroups.computeIfAbsent("cs", "TCP").putAddress(sncpAddress);
        SncpSleepService remoteCService =
                Sncp.createSimpleRemoteService(SncpSleepService.class, resFactory, rpcGroups, client, "cs");
        long s = System.currentTimeMillis();
        CompletableFuture[] futures =
                new CompletableFuture[] {remoteCService.sleep200(), remoteCService.sleep300(), remoteCService.sleep500()
                };
        CompletableFuture.allOf(futures).join();
        long e = System.currentTimeMillis() - s;
        System.out.println("耗时: " + e + " ms");
        remoteCService.test(333L, new String[] {"aaa", "bbb"}, List.of(new File("D:/a.txt"), new File("D:/b.txt")));
        server.shutdown();
        workExecutor.shutdown();
        Assertions.assertTrue(e < 600);
    }
}
