/*
 *
 */
package org.redkale.util;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.LongAdder;

/**
 * ByteBuffer的对象池 <br>
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public class ByteBufferPool extends ObjectPool<ByteBuffer> {

    public static final int DEFAULT_BUFFER_POOL_SIZE = Utility.cpus() * 4;
    
    public static final int DEFAULT_BUFFER_CAPACITY = 16 * 1024;

    private final int bufferCapacity;

    protected ByteBufferPool(
            ObjectPool<ByteBuffer> parent,
            LongAdder creatCounter,
            LongAdder cycleCounter,
            Thread unsafeThread,
            int max,
            int bufferCapacity,
            Queue<ByteBuffer> queue) {
        super(
                parent,
                creatCounter,
                cycleCounter,
                unsafeThread,
                max,
                (Object... params) -> ByteBuffer.allocateDirect(bufferCapacity),
                null,
                e -> {
                    if (e == null || e.isReadOnly() || e.capacity() != bufferCapacity) {
                        return false;
                    }
                    e.clear();
                    return true;
                },
                queue);
        this.bufferCapacity = bufferCapacity;
    }

    // 非线程安全版
    public static ByteBufferPool createUnsafePool(int max, int bufferCapacity) {
        return createUnsafePool(null, null, max, bufferCapacity);
    }

    // 非线程安全版
    public static ByteBufferPool createUnsafePool(
            LongAdder creatCounter, LongAdder cycleCounter, int max, int bufferCapacity) {
        return createUnsafePool(null, creatCounter, cycleCounter, max, bufferCapacity);
    }

    // 非线程安全版
    public static ByteBufferPool createUnsafePool(ByteBufferPool parent, int bufferCapacity) {
        return createUnsafePool(parent, 2, bufferCapacity);
    }

    // 非线程安全版
    public static ByteBufferPool createUnsafePool(ByteBufferPool parent, int max, int bufferCapacity) {
        return createUnsafePool(parent, null, null, max, bufferCapacity);
    }

    // 非线程安全版
    public static ByteBufferPool createUnsafePool(
            ByteBufferPool parent, LongAdder creatCounter, LongAdder cycleCounter, int max, int bufferCapacity) {
        return new ByteBufferPool(
                parent,
                creatCounter,
                cycleCounter,
                null,
                Math.max(Utility.cpus(), max),
                bufferCapacity,
                new ArrayDeque<>(Math.max(Utility.cpus(), max)));
    }

    // 非线程安全版
    public static ByteBufferPool createUnsafePool(Thread unsafeThread, int max, ByteBufferPool safePool) {
        return createUnsafePool(
                safePool,
                safePool.getCreatCounter(),
                safePool.getCycleCounter(),
                unsafeThread,
                max,
                safePool.getBufferCapacity());
    }

    // 非线程安全版
    public static ByteBufferPool createUnsafePool(
            ByteBufferPool parent,
            LongAdder creatCounter,
            LongAdder cycleCounter,
            Thread unsafeThread,
            int max,
            int bufferCapacity) {
        return new ByteBufferPool(
                parent,
                creatCounter,
                cycleCounter,
                unsafeThread,
                Math.max(Utility.cpus(), max),
                bufferCapacity,
                new ArrayDeque<>(Math.max(Utility.cpus(), max)));
    }

    // 线程安全版
    public static ByteBufferPool createSafePool(int bufferCapacity) {
        return createSafePool(2, bufferCapacity);
    }

    // 线程安全版
    public static ByteBufferPool createSafePool(int max, int bufferCapacity) {
        return createSafePool(null, null, max, bufferCapacity);
    }

    // 线程安全版
    public static ByteBufferPool createSafePool(
            LongAdder creatCounter, LongAdder cycleCounter, int max, int bufferCapacity) {
        return new ByteBufferPool(
                null,
                creatCounter,
                cycleCounter,
                null,
                Math.max(Utility.cpus(), max),
                bufferCapacity,
                new LinkedBlockingQueue<>(Math.max(Utility.cpus(), max)));
    }

    public int getBufferCapacity() {
        return bufferCapacity;
    }
}
