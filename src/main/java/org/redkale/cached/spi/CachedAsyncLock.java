/*

*/

package org.redkale.cached.spi;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 缓存信异步操作锁
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 *
 */
public class CachedAsyncLock {

    private static final Object NIL = new Object();

    private final ConcurrentHashMap<String, CachedAsyncLock> asyncLockMap;

    private final AtomicBoolean state = new AtomicBoolean();

    private final List<CompletableFuture> futures = new ArrayList<>();

    private final ReentrantLock lock = new ReentrantLock();

    private final String lockId;

    private Object resultObj = NIL;

    private Throwable resultExp;

    public CachedAsyncLock(ConcurrentHashMap<String, CachedAsyncLock> asyncLockMap, String lockId) {
        this.asyncLockMap = asyncLockMap;
        this.lockId = lockId;
    }

    public boolean compareAddFuture(CompletableFuture future) {
        lock.lock();
        try {
            if (resultObj != NIL) {
                future.complete(resultObj);
                return false;
            } else if (resultExp != null) {
                future.completeExceptionally(resultExp);
                return false;
            }
            boolean rs = state.compareAndSet(false, true);
            this.futures.add(future);
            return rs;
        } finally {
            lock.unlock();
        }
    }

    public void fail(Throwable t) {
        lock.lock();
        try {
            this.resultExp = t;
            for (CompletableFuture future : futures) {
                future.completeExceptionally(t);
            }
            this.futures.clear();
        } finally {
            asyncLockMap.remove(lockId);
            lock.unlock();
        }
    }

    public <T> void success(T val) {
        lock.lock();
        try {
            this.resultObj = val;
            for (CompletableFuture future : futures) {
                future.complete(val);
            }
            this.futures.clear();
        } finally {
            asyncLockMap.remove(lockId);
            lock.unlock();
        }
    }
}
