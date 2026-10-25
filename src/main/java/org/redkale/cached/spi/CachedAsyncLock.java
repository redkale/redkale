/*

*/

package org.redkale.cached.spi;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 缓存信异步操作锁
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public class CachedAsyncLock {

    private final ConcurrentHashMap<String, CachedAsyncLock> asyncLockMap;

    private boolean started;

    private boolean completed;

    private final List<CompletableFuture> futures = new ArrayList<>();

    private final ReentrantLock lock = new ReentrantLock();

    private final String lockId;

    private Object resultObj;

    private Throwable resultExp;

    public CachedAsyncLock(ConcurrentHashMap<String, CachedAsyncLock> asyncLockMap, String lockId) {
        this.asyncLockMap = asyncLockMap;
        this.lockId = lockId;
    }

    public boolean compareAddFuture(CompletableFuture future) {
        Objects.requireNonNull(future);
        Object value;
        Throwable error;
        lock.lock();
        try {
            if (!completed) {
                boolean owner = !started;
                started = true;
                this.futures.add(future);
                return owner;
            }
            value = resultObj;
            error = resultExp;
        } finally {
            lock.unlock();
        }
        // Completing a future can synchronously execute arbitrary user callbacks.
        complete(future, value, error);
        return false;
    }

    public void fail(Throwable t) {
        finish(null, Objects.requireNonNull(t));
    }

    public <T> void success(T val) {
        finish(val, null);
    }

    private void finish(Object value, Throwable error) {
        List<CompletableFuture> waiting;
        lock.lock();
        try {
            if (completed) {
                return;
            }
            completed = true;
            this.resultObj = value;
            this.resultExp = error;
            waiting = new ArrayList<>(this.futures);
            this.futures.clear();
            asyncLockMap.remove(lockId, this);
        } finally {
            lock.unlock();
        }
        for (CompletableFuture future : waiting) {
            complete(future, value, error);
        }
    }

    private static void complete(CompletableFuture future, Object value, Throwable error) {
        if (error == null) {
            future.complete(value);
        } else {
            future.completeExceptionally(error);
        }
    }
}
