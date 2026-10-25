package org.redkale.cached.spi;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;

class CachedLocalSourceConcurrencyTest {
    @Test
    void expirySweepCannotRemoveConcurrentlyRefreshedItem() throws Exception {
        var refresh = new AtomicBoolean();
        var cache = new CachedLocalSource.CacheMap(0) {
            @Override
            public void forEach(BiConsumer<String, CachedLocalSource.CacheItem> action) {
                super.forEach((key, item) -> {
                    if (refresh.compareAndSet(false, true)) {
                        Thread writer = Thread.ofPlatform().daemon().start(() -> set(key, "fresh", 60000));
                        try {
                            writer.join(2000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        assertFalse(writer.isAlive());
                    }
                    action.accept(key, item);
                });
            }
        };
        cache.set("key", "old", 1);
        long expiredAt = cache.get("key").getCreateTime() + 1;
        cache.removeExpired(expiredAt);
        assertEquals("fresh", cache.get("key").getValue());
        assertFalse(cache.get("key").isExpired(expiredAt));
    }

    @Test
    void expiredEntriesAreRemovedButPermanentAndLiveEntriesRemain() {
        var cache = new CachedLocalSource.CacheMap(0);
        cache.set("expired", "old", 1);
        long now = cache.get("expired").getCreateTime() + 1;
        cache.set("permanent", "forever", 0);
        cache.set("live", "fresh", 60000);
        cache.removeExpired(now);
        assertNull(cache.get("expired"));
        assertEquals(2, cache.size());
    }

    @Test
    void refreshPublishesNewValueAndExpiryAsOneSnapshot() {
        var cache = new CachedLocalSource.CacheMap(0);
        cache.set("key", "old", 1);
        var old = cache.get("key");
        cache.set("key", "new", 60000);
        var fresh = cache.get("key");
        assertNotSame(old, fresh);
        assertEquals("old", old.getValue());
        assertEquals("new", fresh.getValue());
        long now = old.getCreateTime() + 1;
        assertTrue(old.isExpired(now));
        assertFalse(fresh.isExpired(now));
    }

    @Test
    void capacityEvictionKeepsTheMostRecentWriteWhenTimestampsTie() {
        var cache = new CachedLocalSource.CacheMap(1);
        for (int i = 0; i < 100; i++) {
            cache.set("key-" + i, "value-" + i, 0);
            assertEquals(1, cache.size());
            assertEquals("value-" + i, cache.get("key-" + i).getValue());
        }
    }

    @Test
    void concurrentWritesAndLimitChangesKeepCapacityBounded() throws Exception {
        var source = new CachedLocalSource();
        source.set("group", "initial", 32, 0, String.class, "initial");
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new CountDownLatch(1);
            List<Future<?>> tasks = new ArrayList<>();
            for (int worker = 0; worker < 8; worker++) {
                int id = worker;
                tasks.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < 300; i++) source.set("group", id + "-" + i, 32, 60000, String.class, "value");
                    return null;
                }));
            }
            tasks.add(executor.submit(() -> {
                start.await();
                for (int i = 0; i < 300; i++) source.updateLimit("group", i % 2 == 0 ? 16 : 32);
                return null;
            }));
            start.countDown();
            for (var task : tasks) task.get(10, TimeUnit.SECONDS);
        }
        source.updateLimit("group", 16);
        assertTrue(source.getSize("group") <= 16);
        source.set("group", "latest", 32, 0, String.class, "latest");
        assertEquals("latest", source.get("group", "latest", String.class));
    }

    @Test
    void asyncWritePublishesCompleteEntryAndCanBeDeleted() throws Exception {
        var source = new CachedLocalSource();
        source.setAsync("group", "key", 0, 60000, String.class, "value").get(2, TimeUnit.SECONDS);
        assertEquals("value", source.get("group", "key", String.class));
        source.set("group", "key", 0, 0, String.class, "permanent");
        assertEquals("permanent", source.getAsync("group", "key", String.class).get(2, TimeUnit.SECONDS));
        assertEquals(1, source.del("group", "key"));
        assertNull(source.get("group", "key", String.class));
    }
}
