package org.redkale.test.sncp;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.*;
import org.redkale.boot.Application;
import org.redkale.convert.bson.BsonConvert;
import org.redkale.convert.json.JsonConvert;
import org.redkale.net.AsyncIOGroup;
import org.redkale.net.client.ClientAddress;
import org.redkale.net.sncp.*;
import org.redkale.util.*;

/**
 *
 * @author zhangjx
 */
public class SncpSleepTest {

    private boolean main;

    public static void main(String[] args) throws Throwable {
        SncpSleepTest test = new SncpSleepTest();
        test.main = true;
        test.run();
    }

    @Test
    public void run() throws Exception {
        System.out.println("------------------- 并发调用 -----------------------------------");
        final Application application = Application.create(true);
        final AsyncIOGroup asyncGroup = new AsyncIOGroup(8192, 16);
        asyncGroup.start();
        final ResourceFactory resFactory = ResourceFactory.create();
        resFactory.register(JsonConvert.root());
        resFactory.register(BsonConvert.root());

        //------------------------ 初始化 CService ------------------------------------
        SncpSleepService service = Sncp.createSimpleLocalService(SncpSleepService.class, resFactory);
        SncpServer server = new SncpServer(application, System.currentTimeMillis(), null, resFactory);
        server.getResourceFactory().register(application);
        server.addSncpServlet(service);
        server.init(AnyValue.DefaultAnyValue.create("port", 0));
        server.start();

        int port = server.getSocketAddress().getPort();
        System.out.println("SNCP服务器启动端口: " + port);
        InetSocketAddress sncpAddress = new InetSocketAddress("127.0.0.1", port);
        final SncpClient client = new SncpClient("", asyncGroup, 0, sncpAddress, new ClientAddress(sncpAddress), "TCP", 16, 100);
        final SncpRpcGroups rpcGroups = application.getSncpRpcGroups();
        rpcGroups.computeIfAbsent("cs", "TCP").putAddress(sncpAddress);
        SncpSleepService remoteCService = Sncp.createSimpleRemoteService(SncpSleepService.class, resFactory, rpcGroups, client, "cs");
        long s = System.currentTimeMillis();
        CompletableFuture[] futures = new CompletableFuture[]{
            remoteCService.sleep200(),
            remoteCService.sleep300(),
            remoteCService.sleep500()
        };
        CompletableFuture.allOf(futures).join();
        long e = System.currentTimeMillis() - s;
        System.out.println("耗时: " + e + " ms");
        server.shutdown();
        Assertions.assertTrue(e < 600);
    }
}
