package org.redkale.cached.spi;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.redkale.util.ThrowSupplier;

class CachedManagerConcurrencyTest {
    private static class Manager extends CachedManagerService {
        final ConcurrentHashMap<String, CachedValue<String>> values = new ConcurrentHashMap<>();
        final AtomicInteger writes = new AtomicInteger();
        Function<String, CompletableFuture<CachedValue<String>>> read =
                key -> CompletableFuture.completedFuture(values.get(key));
        Function<CachedValue<?>, CompletableFuture<Void>> write = value -> CompletableFuture.completedFuture(null);

        Manager() {
            super(null);
            schema = "test";
        }

        CompletableFuture<String> request(boolean nullable, ThrowSupplier<CompletableFuture<String>> supplier) {
            return this.<String>getSetAsync(
                    (n, k, l, e, t) -> read.apply(k),
                    (n, k, l, e, t, v) -> {
                        writes.incrementAndGet();
                        return write.apply(v).thenRun(() -> values.put(k, (CachedValue<String>) v));
                    },
                    "name",
                    "key",
                    String.class,
                    nullable,
                    0,
                    Duration.ofMinutes(1),
                    supplier);
        }

        int lockCount() {
            return asyncLockMap.size();
        }
    }

    @Test
    void failedSupplierDoesNotCacheNullAndCanRetry() throws Exception {
        for (boolean nullable : new boolean[] {false, true}) {
            var manager = new Manager();
            var business = new CompletableFuture<String>();
            var first = manager.request(nullable, () -> business);
            var follower = manager.request(nullable, () -> fail("follower must not load"));
            var failure = new IllegalStateException("business failed");
            business.completeExceptionally(failure);
            assertSame(failure, failure(first));
            assertSame(failure, failure(follower));
            assertEquals(0, manager.writes.get());
            assertTrue(manager.values.isEmpty());
            assertEquals(0, manager.lockCount());
            assertEquals(
                    "retry",
                    manager.request(nullable, () -> CompletableFuture.completedFuture("retry"))
                            .get(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void synchronousSetterFailureCompletesAllWaitersAndReleasesLock() throws Exception {
        var manager = new Manager();
        var error = new IllegalStateException("setter threw");
        manager.write = value -> {
            throw error;
        };
        var business = new CompletableFuture<String>();
        var first = manager.request(false, () -> business);
        var second = manager.request(false, () -> fail("duplicate supplier"));
        business.complete("result");
        assertSame(error, failure(first));
        assertSame(error, failure(second));
        assertEquals(0, manager.lockCount());
    }

    @Test
    void failedSetterFutureIsPropagatedAndDoesNotCacheValue() throws Exception {
        var manager = new Manager();
        var pendingWrite = new CompletableFuture<Void>();
        manager.write = value -> pendingWrite;
        var result = manager.request(false, () -> CompletableFuture.completedFuture("result"));
        assertFalse(result.isDone());
        var error = new IllegalStateException("async write failed");
        pendingWrite.completeExceptionally(error);
        assertSame(error, failure(result));
        assertTrue(manager.values.isEmpty());
        assertEquals(0, manager.lockCount());
    }

    @Test
    void supplierThrowAndNullFutureAreTerminalFailures() throws Exception {
        var manager = new Manager();
        var error = new IllegalArgumentException("supplier threw");
        assertSame(error, failure(manager.request(false, () -> {
            throw error;
        })));
        assertEquals(0, manager.lockCount());
        assertInstanceOf(NullPointerException.class, failure(manager.request(false, () -> null)));
        assertEquals(0, manager.lockCount());
        assertEquals(
                "retry",
                manager.request(false, () -> CompletableFuture.completedFuture("retry"))
                        .get(2, TimeUnit.SECONDS));
    }

    @Test
    void recheckFailuresAlsoReleaseLock() throws Exception {
        for (boolean synchronous : new boolean[] {false, true}) {
            var manager = new Manager();
            var reads = new AtomicInteger();
            var error = new IllegalStateException("read failed");
            manager.read = key -> {
                if (reads.incrementAndGet() == 1) return CompletableFuture.completedFuture(null);
                if (synchronous) throw error;
                return CompletableFuture.failedFuture(error);
            };
            assertSame(error, failure(manager.request(false, () -> fail("must not load"))));
            assertEquals(0, manager.lockCount());
        }
    }

    @Test
    void staleMissRechecksCacheInsteadOfLoadingAgain() throws Exception {
        var manager = new Manager();
        var delayed = new CompletableFuture<CachedValue<String>>();
        var reads = new AtomicInteger();
        manager.read = key ->
                reads.incrementAndGet() == 1 ? delayed : CompletableFuture.completedFuture(manager.values.get(key));
        var loads = new AtomicInteger();
        ThrowSupplier<CompletableFuture<String>> supplier =
                () -> CompletableFuture.completedFuture("value-" + loads.incrementAndGet());
        var first = manager.request(false, supplier);
        assertEquals("value-1", manager.request(false, supplier).get(2, TimeUnit.SECONDS));
        delayed.complete(null);
        assertEquals("value-1", first.get(2, TimeUnit.SECONDS));
        assertEquals(1, loads.get());
        assertEquals(0, manager.lockCount());
    }

    @Test
    void concurrentRequestsShareLoadUntilCacheWriteCompletes() throws Exception {
        var manager = new Manager();
        var business = new CompletableFuture<String>();
        var pendingWrite = new CompletableFuture<Void>();
        manager.write = value -> pendingWrite;
        var loads = new AtomicInteger();
        List<CompletableFuture<String>> results = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new CountDownLatch(1);
            var requests = new ArrayList<Future<CompletableFuture<String>>>();
            for (int i = 0; i < 32; i++) {
                requests.add(executor.submit(() -> {
                    start.await();
                    return manager.request(false, () -> {
                        loads.incrementAndGet();
                        return business;
                    });
                }));
            }
            start.countDown();
            for (var request : requests) results.add(request.get(3, TimeUnit.SECONDS));
        }
        results.getFirst().cancel(false);
        business.complete("shared");
        assertFalse(results.getLast().isDone());
        pendingWrite.complete(null);
        for (int i = 1; i < results.size(); i++)
            assertEquals("shared", results.get(i).get(2, TimeUnit.SECONDS));
        assertEquals(1, loads.get());
        assertEquals(1, manager.writes.get());
        assertEquals(0, manager.lockCount());
    }

    @Test
    void successfulNullRespectsNullableSetting() throws Exception {
        for (boolean nullable : new boolean[] {false, true}) {
            var manager = new Manager();
            var loads = new AtomicInteger();
            ThrowSupplier<CompletableFuture<String>> supplier = () -> {
                loads.incrementAndGet();
                return CompletableFuture.completedFuture(null);
            };
            assertNull(manager.request(nullable, supplier).get(2, TimeUnit.SECONDS));
            assertNull(manager.request(nullable, supplier).get(2, TimeUnit.SECONDS));
            assertEquals(nullable ? 1 : 2, loads.get());
            assertEquals(nullable ? 1 : 0, manager.writes.get());
            assertEquals(0, manager.lockCount());
        }
    }

    private static Throwable failure(CompletableFuture<?> future) throws Exception {
        var error = assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
        Throwable cause = error.getCause();
        while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
        return cause;
    }
}
