/*
 *
 */
package org.redkale.test.cache;

import java.net.InetSocketAddress;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.redkale.cache.CacheManager;
import org.redkale.cache.spi.CacheAsmMethodBoost;
import org.redkale.cache.spi.CacheManagerService;
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
public class CacheInstanceTest {

    private static ResourceFactory resourceFactory;

    private static CacheManagerService manager;

    public static void main(String[] args) throws Throwable {
        CacheInstanceTest test = new CacheInstanceTest();
        init();
        test.run1();
        test.run2();
    }

    @BeforeAll
    public static void init() throws Exception {
        resourceFactory = ResourceFactory.create();
        resourceFactory.register(new Environment());
        CacheMemorySource remoteSource = new CacheMemorySource("cache-remote");
        remoteSource.init(null);
        manager = CacheManagerService.create(remoteSource);
        manager.init(null);
        resourceFactory.register("", CacheManager.class, manager);
    }

    @Test
    public void run1() throws Exception {
        Class<CacheInstance> serviceClass = CacheInstance.class;
        CacheAsmMethodBoost boost = new CacheAsmMethodBoost(false, serviceClass);
        SncpRpcGroups grous = new SncpRpcGroups();
        AsyncGroup iGroup = AsyncGroup.create("", Utility.newScheduledExecutor(1), 0, 0);
        SncpClient client = new SncpClient(
                "", iGroup, "0", new InetSocketAddress("127.0.0.1", 8080), new ClientAddress(), "TCP", 1, 16);
        CacheInstance instance = Sncp.createLocalService(
                null, "", serviceClass, boost, resourceFactory, grous, client, null, null, null);
        // System.out.println(instance.getName());
    }

    @Test
    public void run2() throws Exception {}
}
