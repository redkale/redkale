package org.redkale.test.source;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.redkale.source.CacheMemorySource;

class CacheMemoryFloatTest {
    @Test
    void floatIncrementAndReadsUseNumericValues() {
        CacheMemorySource source = new CacheMemorySource("float-test");
        source.init(null);
        try {
            assertEquals(1.0, source.incrbyFloat("number", 1.0));
            assertEquals(2.0, source.incrbyFloat("number", 1.0));
            assertEquals(1.75, source.incrbyFloat("number", -0.25));
            assertEquals(1.75, source.<Double>get("number", Double.class));
            source.setString("existing", "2.5");
            assertEquals(3.0, source.incrbyFloat("existing", 0.5));
            source.incrby("integer", 2);
            assertEquals(2.5, source.incrbyFloat("integer", 0.5));
        } finally {
            source.destroy(null);
        }
    }

    @Test
    void hashFloatIncrementStoresReadableNumericValue() {
        CacheMemorySource source = new CacheMemorySource("hash-float-test");
        source.init(null);
        try {
            assertEquals(1.0, source.hincrbyFloat("hash", "field", 1.0));
            assertEquals(2.0, source.hincrbyFloat("hash", "field", 1.0));
            assertEquals(1.75, source.hincrbyFloat("hash", "field", -0.25));
            assertEquals(1.75, source.<Double>hget("hash", "field", Double.class));
            source.hset("hash", "existing", Double.class, 2.5);
            assertEquals(3.0, source.hincrbyFloat("hash", "existing", 0.5));
            source.hincrby("hash", "integer", 2);
            assertEquals(2.5, source.hincrbyFloat("hash", "integer", 0.5));
        } finally {
            source.destroy(null);
        }
    }

    @Test
    void concurrentFloatIncrementsDoNotLoseUpdates() throws Exception {
        CacheMemorySource source = new CacheMemorySource("concurrent-float-test");
        source.init(null);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch start = new CountDownLatch(1);
            Future<?>[] tasks = new Future<?>[8];
            for (int i = 0; i < tasks.length; i++) {
                tasks[i] = executor.submit(() -> {
                    start.await();
                    for (int j = 0; j < 1000; j++) {
                        source.incrbyFloat("number", 0.5);
                        source.hincrbyFloat("hash", "field", 0.5);
                    }
                    return null;
                });
            }
            start.countDown();
            for (Future<?> task : tasks) {
                task.get(30, TimeUnit.SECONDS);
            }
            assertEquals(4000.0, source.<Double>get("number", Double.class));
            assertEquals(4000.0, source.<Double>hget("hash", "field", Double.class));
        } finally {
            source.destroy(null);
        }
    }
}
