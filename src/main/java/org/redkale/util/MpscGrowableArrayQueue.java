/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

/**
 * 参考 https://github.com/JCTools/JCTools/blob/master/jctools-core/src/main/java/org/jctools/queues/MpscGrowableArrayQueue.java version: v3.3.0实现的MPSC队列 <br>
 *  与基类的区别在于: 每次都会将连接块容量加倍，直到底层的数组可以完全容纳所有的元素。
 * <p>
 * 详情见: https://redkale.org
 *
 * @param <E> 泛型
 *
 * @author zhangjx
 * @since 2.5.0
 */
public class MpscGrowableArrayQueue<E> extends MpscChunkedArrayQueue<E> {

    public MpscGrowableArrayQueue(int maxCapacity) {
        super(Math.max(2, Utility.roundToPowerOfTwo(maxCapacity / 8)), maxCapacity);
    }

    public MpscGrowableArrayQueue(int initialCapacity, int maxCapacity) {
        super(initialCapacity, maxCapacity);
    }

    @Override
    protected int getNextBufferSize(E[] buffer) {
        final long maxSize = maxQueueCapacity / 2;
        int len = buffer.length;
        //checkLessThanOrEqual
        if (len > maxSize) throw new IllegalArgumentException("buffer.length: " + len + " (expected: <= " + maxSize + ")");
        final int newSize = 2 * (len - 1);
        return newSize + 1;
    }

    @Override
    protected long getCurrentBufferCapacity(long mask) {
        return (mask + 2 == maxQueueCapacity) ? maxQueueCapacity : mask;
    }
}
