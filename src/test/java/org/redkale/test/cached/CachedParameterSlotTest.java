package org.redkale.test.cached;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.redkale.cached.Cached;
import org.redkale.cached.CachedManager;
import org.redkale.cached.spi.CachedCodeMethodBoost;
import org.redkale.cached.spi.CachedManagerService;
import org.redkale.inject.ResourceFactory;
import org.redkale.net.sncp.Sncp;
import org.redkale.service.Service;
import org.redkale.source.CacheMemorySource;
import org.redkale.util.Environment;
import org.redkale.util.RedkaleClassLoader;

/** 验证缓存代理的宽参数、float 后续参数以及缓存键装箱，无需网络服务。 */
public class CachedParameterSlotTest {
    @Test
    public void mixedParametersAndCacheKeys() {
        CacheMemorySource source = new CacheMemorySource("slot-test");
        source.init(null);
        CachedManagerService manager = CachedManagerService.create(source);
        manager.init(null);
        try {
            ResourceFactory resources = ResourceFactory.create();
            resources.register(new Environment());
            resources.register("", CachedManager.class, manager);
            SlotService service = Sncp.createLocalService(
                    RedkaleClassLoader.currentClassLoader(),
                    "",
                    SlotService.class,
                    new CachedCodeMethodBoost(false, SlotService.class),
                    resources,
                    null,
                    null,
                    null,
                    null,
                    null);
            assertEquals("BTC:1d:null:20:1000", service.klines("BTC", "1d", null, 20L, 1000L));
            assertEquals("BTC:1d:null:20:1000", service.klines("BTC", "1d", null, 20L, 1000L));
            assertEquals(1, service.calls);
            assertEquals("BTC:1d:null:20:1001", service.klines("BTC", "1d", null, 20L, 1001L));
            assertEquals(2, service.calls);
            assertEquals("7:2.5:1.25:tail:3:true", service.mixed(7L, 2.5, 1.25f, "tail", 3, true));
            assertEquals("7:2.5:1.25:tail:3:true", service.mixed(7L, 2.5, 1.25f, "tail", 3, true));
            assertEquals(3, service.calls);
            assertEquals("7:3.5:1.25:tail:3:true", service.mixed(7L, 3.5, 1.25f, "tail", 3, true));
            assertEquals("7:3.5:1.5:tail:3:true", service.mixed(7L, 3.5, 1.5f, "tail", 3, true));
            assertEquals("7:3.5:1.5:next:3:true", service.mixed(7L, 3.5, 1.5f, "next", 3, true));
            assertEquals(6, service.calls);
            assertEquals("1.5:end", service.floats(1.5f, "end"));
            assertEquals("1.5:end", service.floats(1.5f, "end"));
            assertEquals(7, service.calls);
            assertEquals("8:2.5", service.async(8L, 2.5).join());
            assertEquals("8:2.5", service.async(8L, 2.5).join());
            assertEquals(8, service.calls);
            assertEquals("none", service.none());
            assertEquals("none", service.none());
            assertEquals(9, service.calls);
            assertEquals("4", service.boxed(4L));
            assertEquals("4", service.boxed(4L));
            assertEquals(10, service.calls);
        } finally {
            manager.destroy(null);
            source.destroy(null);
        }
    }

    public static class SlotService implements Service {
        public int calls;

        @Cached(name = "slotsKlines", key = "#{symbol}_#{interval}_#{start}_#{end}_#{limit}", localExpire = "60")
        public String klines(String symbol, String interval, Long start, Long end, long limit) {
            calls++;
            return symbol + ":" + interval + ":" + start + ":" + end + ":" + limit;
        }

        @Cached(name = "slotsMixed", key = "#{a}_#{b}_#{c}_#{d}_#{e}_#{f}", localExpire = "60")
        public String mixed(long a, double b, float c, String d, int e, boolean f) {
            calls++;
            return a + ":" + b + ":" + c + ":" + d + ":" + e + ":" + f;
        }

        @Cached(name = "slotsFloat", key = "#{a}_#{b}", localExpire = "60")
        public String floats(float a, String b) {
            calls++;
            return a + ":" + b;
        }

        @Cached(name = "slotsAsync", key = "#{a}_#{b}", localExpire = "60")
        public CompletableFuture<String> async(long a, double b) {
            calls++;
            return CompletableFuture.completedFuture(a + ":" + b);
        }

        @Cached(name = "slotsNone", key = "all", localExpire = "60")
        public String none() {
            calls++;
            return "none";
        }

        @Cached(name = "slotsBoxed", key = "#{a}", localExpire = "60")
        public String boxed(Long a) {
            calls++;
            return String.valueOf(a);
        }
    }
}
