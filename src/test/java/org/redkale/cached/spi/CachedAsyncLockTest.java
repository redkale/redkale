package org.redkale.cached.spi;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class CachedAsyncLockTest {
    @Test
    void callbacksCanEnterAnotherCompletingLockWithoutDeadlock() throws Exception {
        var map = new ConcurrentHashMap<String, CachedAsyncLock>();
        var left = new CachedAsyncLock(map, "left");
        var right = new CachedAsyncLock(map, "right");
        var first = new CompletableFuture<String>();
        var second = new CompletableFuture<String>();
        assertTrue(left.compareAddFuture(first));
        assertTrue(right.compareAddFuture(second));
        var barrier = new CyclicBarrier(2);
        CompletableFuture<Void> firstCallback = first.thenRun(() -> {
            await(barrier);
            right.compareAddFuture(new CompletableFuture<>());
        });
        CompletableFuture<Void> secondCallback = second.thenRun(() -> {
            await(barrier);
            left.compareAddFuture(new CompletableFuture<>());
        });
        Thread a = Thread.ofPlatform().daemon().start(() -> left.success("L"));
        Thread b = Thread.ofPlatform().daemon().start(() -> right.success("R"));
        a.join(3000);
        b.join(3000);
        assertFalse(a.isAlive(), "left completion deadlocked");
        assertFalse(b.isAlive(), "right completion deadlocked");
        firstCallback.get(1, TimeUnit.SECONDS);
        secondCallback.get(1, TimeUnit.SECONDS);
    }

    @Test
    void alreadyCompletedLockAlsoRunsCallbacksOutsideLock() throws Exception {
        for (boolean failed : new boolean[] {false, true}) {
            var lock = new CachedAsyncLock(new ConcurrentHashMap<>(), "key");
            if (failed) {
                lock.fail(new IllegalStateException("failed"));
            } else {
                lock.success("value");
            }
            var future = new CompletableFuture<String>();
            CompletableFuture<Void> callback = future.handle((value, error) -> {
                var entered = new CompletableFuture<Void>();
                Thread.ofPlatform().daemon().start(() -> {
                    lock.compareAddFuture(new CompletableFuture<>());
                    entered.complete(null);
                });
                try {
                    entered.get(2, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
                return null;
            });
            assertFalse(lock.compareAddFuture(future));
            callback.get(3, TimeUnit.SECONDS);
        }
    }

    @Test
    void completionIsTerminalAndCannotRemoveReplacementLock() {
        var map = new ConcurrentHashMap<String, CachedAsyncLock>();
        var old = new CachedAsyncLock(map, "key");
        map.put("key", old);
        var first = new CompletableFuture<String>();
        assertTrue(old.compareAddFuture(first));
        old.fail(new IllegalStateException("failed"));
        var replacement = new CachedAsyncLock(map, "key");
        map.put("key", replacement);
        old.success("late result");
        old.fail(new IllegalArgumentException("late failure"));
        assertSame(replacement, map.get("key"));
        var late = new CompletableFuture<String>();
        assertFalse(old.compareAddFuture(late));
        assertTrue(first.isCompletedExceptionally());
        assertThrows(CompletionException.class, late::join);
        var unregistered = new CachedAsyncLock(map, "key");
        unregistered.success(null);
        assertSame(replacement, map.get("key"));
    }

    @Test
    void completionRemovesLockBeforeExecutingUserCallbacks() {
        var map = new ConcurrentHashMap<String, CachedAsyncLock>();
        var lock = new CachedAsyncLock(map, "key");
        map.put("key", lock);
        var future = new CompletableFuture<String>();
        lock.compareAddFuture(future);
        var replacement = new CachedAsyncLock(map, "key");
        var callback = future.thenRun(() -> assertNull(map.putIfAbsent("key", replacement)));
        lock.success(null);
        callback.join();
        assertSame(replacement, map.get("key"));
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CompletionException(e);
        }
    }
}
