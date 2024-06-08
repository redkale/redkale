/*
 *
 */
package org.redkale.test.cached;

import java.net.InetSocketAddress;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.redkale.cached.CachedManager;
import org.redkale.cached.spi.CachedAsmMethodBoost;
import org.redkale.cached.spi.CachedManagerService;
import org.redkale.inject.ResourceFactory;
import org.redkale.net.AsyncGroup;
import org.redkale.net.client.ClientAddress;
import org.redkale.net.sncp.Sncp;
import org.redkale.net.sncp.SncpClient;
import org.redkale.net.sncp.SncpRpcGroups;
import org.redkale.source.CacheMemorySource;
import org.redkale.util.Environment;
import org.redkale.util.Utility;

/** @author zhangjx */
public class CachedInstanceTest {

    private static ResourceFactory resourceFactory;

    private static CachedManagerService manager;

    private static ResourceFactory resourceFactory2;

    private static CachedManagerService manager2;

    public static void main(String[] args) throws Throwable {
        CachedInstanceTest test = new CachedInstanceTest();
        init();
        test.run1();
        test.run2();
    }

    @BeforeAll
    public static void init() throws Exception {

        CacheMemorySource remoteSource = new CacheMemorySource("cache-remote");
        remoteSource.init(null);
        resourceFactory = ResourceFactory.create();
        resourceFactory.register(new Environment());
        manager = CachedManagerService.create(remoteSource);
        manager.init(null);
        resourceFactory.register("", CachedManager.class, manager);

        resourceFactory2 = ResourceFactory.create();
        resourceFactory2.register(new Environment());
        manager2 = CachedManagerService.create(remoteSource);
        manager2.init(null);
        resourceFactory2.register("", CachedManager.class, manager2);
    }

    @Test
    public void run1() throws Exception {
        Class<CachedInstance> serviceClass = CachedInstance.class;
        CachedAsmMethodBoost boost = new CachedAsmMethodBoost(false, serviceClass);
        CachedAsmMethodBoost boost2 = new CachedAsmMethodBoost(false, serviceClass);
        SncpRpcGroups grous = new SncpRpcGroups();
        AsyncGroup iGroup = AsyncGroup.create("", Utility.newScheduledExecutor(1), 0, 0);
        SncpClient client = new SncpClient(
                "", iGroup, "0", new InetSocketAddress("127.0.0.1", 8080), new ClientAddress(), "TCP", 1, 16);
        CachedInstance instance = Sncp.createLocalService(
                null, "", serviceClass, boost, resourceFactory, grous, client, null, null, null);
        resourceFactory.inject(instance);
        CachedInstance instance2 = Sncp.createLocalService(
                null, "", serviceClass, boost2, resourceFactory2, grous, client, null, null, null);
        resourceFactory2.inject(instance2);
        System.out.println(instance.getName2());
        System.out.println(instance.getClass());
        Assertions.assertEquals("haha", instance.getName2());
        Assertions.assertEquals("haha", instance2.getName2());
        System.out.println("准备设置 updateName");
        System.out.println("instance1.manager = " + instance.getCacheManager());
        System.out.println("instance2.manager = " + instance2.getCacheManager());
        manager.updateBroadcastable(false);
        instance.updateName("gege");
        Assertions.assertEquals("gege", instance.getName2());
        Assertions.assertEquals("haha", instance2.getName2());
        manager.updateBroadcastable(true);
        System.out.println("准备设置 updateName");
        instance.updateName("gege");
        System.out.println("设置结束 updateName");
        Utility.sleep(10);
        Assertions.assertEquals("gege", instance.getName2());
        Assertions.assertEquals("gege", instance2.getName2());
    }

    @Test
    public void run2() throws Exception {}
}
