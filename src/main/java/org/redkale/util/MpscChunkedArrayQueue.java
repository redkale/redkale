/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.util.*;
import java.util.function.Supplier;

/**
 * 参考 https://github.com/JCTools/JCTools/blob/master/jctools-core/src/main/java/org/jctools/queues/MpscChunkedArrayQueue.java version: v3.3.0实现的MPSC队列
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @param <E> 泛型
 *
 * @author zhangjx
 * @since 2.5.0
 */
public class MpscChunkedArrayQueue<E> extends AbstractQueue<E> {

    byte b000, b001, b002, b003, b004, b005, b006, b007;//  8b

    byte b010, b011, b012, b013, b014, b015, b016, b017;// 16b

    byte b020, b021, b022, b023, b024, b025, b026, b027;// 24b

    byte b030, b031, b032, b033, b034, b035, b036, b037;// 32b

    byte b040, b041, b042, b043, b044, b045, b046, b047;// 40b

    byte b050, b051, b052, b053, b054, b055, b056, b057;// 48b

    byte b060, b061, b062, b063, b064, b065, b066, b067;// 56b

    byte b070, b071, b072, b073, b074, b075, b076, b077;// 64b

    byte b100, b101, b102, b103, b104, b105, b106, b107;// 72b

    byte b110, b111, b112, b113, b114, b115, b116, b117;// 80b

    byte b120, b121, b122, b123, b124, b125, b126, b127;// 88b

    byte b130, b131, b132, b133, b134, b135, b136, b137;// 96b

    byte b140, b141, b142, b143, b144, b145, b146, b147;//104b

    byte b150, b151, b152, b153, b154, b155, b156, b157;//112b

    byte b160, b161, b162, b163, b164, b165, b166, b167;//120b

    byte b170, b171, b172, b173, b174, b175, b176, b177;//128b

    //----------------------------------------------
    private static final Unsafe UNSAFE = Utility.unsafe();

    private static final long REF_ARRAY_BASE;

    private static final int REF_ELEMENT_SHIFT;

    static {
        final int scale = UNSAFE == null ? 4 : UNSAFE.arrayIndexScale(Object[].class);
        if (4 == scale) {
            REF_ELEMENT_SHIFT = 2;
        } else if (8 == scale) {
            REF_ELEMENT_SHIFT = 3;
        } else {
            throw new IllegalStateException("Unknown pointer size: " + scale);
        }
        REF_ARRAY_BASE = UNSAFE == null ? 0L : UNSAFE.arrayBaseOffset(Object[].class);
    }

    // No post padding here, subclasses must add
    private static final Object JUMP = new Object();

    private static final Object BUFFER_CONSUMED = new Object();

    private static final int CONTINUE_TO_P_INDEX_CAS = 0;

    private static final int RETRY = 1;

    private static final int QUEUE_FULL = 2;

    private static final int QUEUE_RESIZE = 3;

    protected final long maxQueueCapacity;

    private final static long P_LIMIT_OFFSET = fieldOffset(MpscChunkedArrayQueue.class, "producerLimit");

    private volatile long producerLimit;

    protected long producerMask;

    protected E[] producerBuffer;

    private final static long C_INDEX_OFFSET = fieldOffset(MpscChunkedArrayQueue.class, "consumerIndex");

    private volatile long consumerIndex;

    protected long consumerMask;

    protected E[] consumerBuffer;

    private final static long P_INDEX_OFFSET = fieldOffset(MpscChunkedArrayQueue.class, "producerIndex");

    private volatile long producerIndex;

    public MpscChunkedArrayQueue(int maxCapacity) {
        this(Math.max(2, Math.min(1024, Utility.roundToPowerOfTwo(maxCapacity / 8))), maxCapacity);
    }

    /**
     * @param initialCapacity the queue initial capacity. If chunk size is fixed this will be the chunk size.
     *                        Must be 2 or more.
     * @param maxCapacity     the maximum capacity will be rounded up to the closest power of 2 and will be the
     *                        upper limit of number of elements in this queue. Must be 4 or more and round up to a larger
     *                        power of 2 than initialCapacity.
     */
    public MpscChunkedArrayQueue(int initialCapacity, int maxCapacity) {
        if (initialCapacity < 2) throw new IllegalArgumentException("initialCapacity: " + initialCapacity + " (expected: >= 2)");
        int p2capacity = Utility.roundToPowerOfTwo(initialCapacity);
        // leave lower bit of mask clear
        long mask = (p2capacity - 1) << 1;
        // need extra element to point at next array
        E[] buffer = (E[]) new Object[p2capacity + 1];
        producerBuffer = buffer;
        producerMask = mask;
        consumerBuffer = buffer;
        consumerMask = mask;
        soProducerLimit(mask); // we know it's all empty to start with

        if (maxCapacity < 4) throw new IllegalArgumentException("maxCapacity: " + maxCapacity + " (expected: >= 4)");
        int p2max = Utility.roundToPowerOfTwo(maxCapacity);
        if (Utility.roundToPowerOfTwo(initialCapacity) > p2max) {
            throw new IllegalArgumentException("initialCapacity: " + Utility.roundToPowerOfTwo(initialCapacity) + " (expected: <= " + p2max + ")");
        }
        maxQueueCapacity = ((long) Utility.roundToPowerOfTwo(maxCapacity)) << 1;
    }

    static long fieldOffset(Class clz, String fieldName) throws RuntimeException {
        if (UNSAFE == null) return 0L;
        try {
            return UNSAFE.objectFieldOffset(clz.getDeclaredField(fieldName));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    static long modifiedCalcCircularRefElementOffset(long index, long mask) {
        return REF_ARRAY_BASE + ((index & mask) << (REF_ELEMENT_SHIFT - 1));
    }

    static <E> void spRefElement(E[] buffer, long offset, E e) {
        UNSAFE.putObject(buffer, offset, e);
    }

    @SuppressWarnings("unchecked")
    static <E> E lpRefElement(E[] buffer, long offset) {
        return (E) UNSAFE.getObject(buffer, offset);
    }

    /**
     * A volatile load of an element from a given offset.
     *
     * @param buffer this.buffer
     * @param offset computed via
     *
     * @return the element at the offset
     */
    @SuppressWarnings("unchecked")
    static <E> E lvRefElement(E[] buffer, long offset) {
        return (E) UNSAFE.getObjectVolatile(buffer, offset);
    }

    /**
     * An ordered store of an element to a given offset
     *
     * @param buffer this.buffer
     * @param offset computed via
     * @param e      an orderly kitty
     */
    public static <E> void soRefElement(E[] buffer, long offset, E e) {
        UNSAFE.putOrderedObject(buffer, offset, e);
    }

    public static long calcRefElementOffset(long index) {
        return REF_ARRAY_BASE + (index << REF_ELEMENT_SHIFT);
    }

    /**
     * Note: circular arrays are assumed a power of 2 in length and the `mask` is (length - 1).
     *
     * @param index desirable element index
     * @param mask  (length - 1)
     *
     * @return the offset in bytes within the circular array for a given index
     */
    public static long calcCircularRefElementOffset(long index, long mask) {
        return REF_ARRAY_BASE + ((index & mask) << REF_ELEMENT_SHIFT);
    }

    public final long lvProducerIndex() {
        return producerIndex;
    }

    final void soProducerIndex(long newValue) {
        UNSAFE.putOrderedLong(this, P_INDEX_OFFSET, newValue);
    }

    final boolean casProducerIndex(long expect, long newValue) {
        return UNSAFE.compareAndSwapLong(this, P_INDEX_OFFSET, expect, newValue);
    }

    public final long lvConsumerIndex() {
        return consumerIndex;
    }

    final long lpConsumerIndex() {
        return UNSAFE.getLong(this, C_INDEX_OFFSET);
    }

    final void soConsumerIndex(long newValue) {
        UNSAFE.putOrderedLong(this, C_INDEX_OFFSET, newValue);
    }

    final long lvProducerLimit() {
        return producerLimit;
    }

    final boolean casProducerLimit(long expect, long newValue) {
        return UNSAFE.compareAndSwapLong(this, P_LIMIT_OFFSET, expect, newValue);
    }

    final void soProducerLimit(long newValue) {
        UNSAFE.putOrderedLong(this, P_LIMIT_OFFSET, newValue);
    }

    @Override
    public int size() {
        // NOTE: because indices are on even numbers we cannot use the size util.

        /*
         * It is possible for a thread to be interrupted or reschedule between the read of the producer and
         * consumer indices, therefore protection is required to ensure size is within valid range. In the
         * event of concurrent polls/offers to this method the size is OVER estimated as we read consumer
         * index BEFORE the producer index.
         */
        long after = lvConsumerIndex();
        long size;
        while (true) {
            final long before = after;
            final long currentProducerIndex = lvProducerIndex();
            after = lvConsumerIndex();
            if (before == after) {
                size = ((currentProducerIndex - after) >> 1);
                break;
            }
        }
        // Long overflow is impossible, so size is always positive. Integer overflow is possible for the unbounded
        // indexed queues.
        if (size > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        } else {
            return (int) size;
        }
    }

    @Override
    public boolean isEmpty() {
        // Order matters!
        // Loading consumer before producer allows for producer increments after consumer index is read.
        // This ensures this method is conservative in it's estimate. Note that as this is an MPMC there is
        // nothing we can do to make this an exact method.
        return (this.lvConsumerIndex() == this.lvProducerIndex());
    }

    @Override
    public String toString() {
        return this.getClass().getName();
    }

    @Override
    public boolean offer(final E e) {
        if (null == e) throw new NullPointerException();

        long mask;
        E[] buffer;
        long pIndex;

        while (true) {
            long producerLimit0 = lvProducerLimit();
            pIndex = lvProducerIndex();
            // lower bit is indicative of resize, if we see it we spin until it's cleared
            if ((pIndex & 1) == 1) {
                continue;
            }
            // pIndex is even (lower bit is 0) -> actual index is (pIndex >> 1)

            // mask/buffer may get changed by resizing -> only use for array access after successful CAS.
            mask = this.producerMask;
            buffer = this.producerBuffer;
            // a successful CAS ties the ordering, lv(pIndex) - [mask/buffer] -> cas(pIndex)

            // assumption behind this optimization is that queue is almost always empty or near empty
            if (producerLimit0 <= pIndex) {
                int result = offerSlowPath(mask, pIndex, producerLimit0);
                switch (result) {
                    case CONTINUE_TO_P_INDEX_CAS:
                        break;
                    case RETRY:
                        continue;
                    case QUEUE_FULL:
                        return false;
                    case QUEUE_RESIZE:
                        resize(mask, buffer, pIndex, e, null);
                        return true;
                }
            }

            if (casProducerIndex(pIndex, pIndex + 2)) {
                break;
            }
        }
        // INDEX visible before ELEMENT
        final long offset = modifiedCalcCircularRefElementOffset(pIndex, mask);
        soRefElement(buffer, offset, e); // release element e
        return true;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation is correct for single consumer thread use only.
     */
    @SuppressWarnings("unchecked")
    @Override
    public E poll() {
        final E[] buffer = consumerBuffer;
        final long index = lpConsumerIndex();
        final long mask = consumerMask;

        final long offset = modifiedCalcCircularRefElementOffset(index, mask);
        Object e = lvRefElement(buffer, offset);
        if (e == null) {
            if (index != lvProducerIndex()) {
                // poll() == null iff queue is empty, null element is not strong enough indicator, so we must
                // check the producer index. If the queue is indeed not empty we spin until element is
                // visible.
                do {
                    e = lvRefElement(buffer, offset);
                } while (e == null);
            } else {
                return null;
            }
        }

        if (e == JUMP) {
            final E[] nextBuffer = nextBuffer(buffer, mask);
            return newBufferPoll(nextBuffer, index);
        }

        soRefElement(buffer, offset, null); // release element null
        soConsumerIndex(index + 2); // release cIndex
        return (E) e;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation is correct for single consumer thread use only.
     */
    @SuppressWarnings("unchecked")
    @Override
    public E peek() {
        final E[] buffer = consumerBuffer;
        final long index = lpConsumerIndex();
        final long mask = consumerMask;

        final long offset = modifiedCalcCircularRefElementOffset(index, mask);
        Object e = lvRefElement(buffer, offset);
        if (e == null && index != lvProducerIndex()) {
            // peek() == null iff queue is empty, null element is not strong enough indicator, so we must
            // check the producer index. If the queue is indeed not empty we spin until element is visible.
            do {
                e = lvRefElement(buffer, offset);
            } while (e == null);
        }
        if (e == JUMP) {
            return newBufferPeek(nextBuffer(buffer, mask), index);
        }
        return (E) e;
    }

    /**
     * We do not inline resize into this method because we do not resize on fill.
     */
    private int offerSlowPath(long mask, long pIndex, long producerLimit) {
        final long cIndex = lvConsumerIndex();
        long bufferCapacity = getCurrentBufferCapacity(mask);

        if (cIndex + bufferCapacity > pIndex) {
            if (!casProducerLimit(producerLimit, cIndex + bufferCapacity)) {
                // retry from top
                return RETRY;
            } else {
                // continue to pIndex CAS
                return CONTINUE_TO_P_INDEX_CAS;
            }
        } // full and cannot grow
        else if (availableInQueue(pIndex, cIndex) <= 0) {
            // offer should return false;
            return QUEUE_FULL;
        } // grab index for resize -> set lower bit
        else if (casProducerIndex(pIndex, pIndex + 1)) {
            // trigger a resize
            return QUEUE_RESIZE;
        } else {
            // failed resize attempt, retry from top
            return RETRY;
        }
    }

    @SuppressWarnings("unchecked")
    private E[] nextBuffer(final E[] buffer, final long mask) {
        final long offset = nextArrayOffset(mask);
        final E[] nextBuffer = (E[]) lvRefElement(buffer, offset);
        consumerBuffer = nextBuffer;
        consumerMask = (nextBuffer.length - 2) << 1;
        soRefElement(buffer, offset, BUFFER_CONSUMED);
        return nextBuffer;
    }

    private static long nextArrayOffset(long mask) {
        return modifiedCalcCircularRefElementOffset(mask + 2, Long.MAX_VALUE);
    }

    private E newBufferPoll(E[] nextBuffer, long index) {
        final long offset = modifiedCalcCircularRefElementOffset(index, consumerMask);
        final E n = lvRefElement(nextBuffer, offset);
        if (n == null) {
            throw new IllegalStateException("new buffer must have at least one element");
        }
        soRefElement(nextBuffer, offset, null);
        soConsumerIndex(index + 2);
        return n;
    }

    private E newBufferPeek(E[] nextBuffer, long index) {
        final long offset = modifiedCalcCircularRefElementOffset(index, consumerMask);
        final E n = lvRefElement(nextBuffer, offset);
        if (null == n) {
            throw new IllegalStateException("new buffer must have at least one element");
        }
        return n;
    }

    public long currentProducerIndex() {
        return lvProducerIndex() / 2;
    }

    public long currentConsumerIndex() {
        return lvConsumerIndex() / 2;
    }

    public boolean relaxedOffer(E e) {
        return offer(e);
    }

    @SuppressWarnings("unchecked")
    public E relaxedPoll() {
        final E[] buffer = consumerBuffer;
        final long index = lpConsumerIndex();
        final long mask = consumerMask;

        final long offset = modifiedCalcCircularRefElementOffset(index, mask);
        Object e = lvRefElement(buffer, offset);
        if (e == null) {
            return null;
        }
        if (e == JUMP) {
            final E[] nextBuffer = nextBuffer(buffer, mask);
            return newBufferPoll(nextBuffer, index);
        }
        soRefElement(buffer, offset, null);
        soConsumerIndex(index + 2);
        return (E) e;
    }

    @SuppressWarnings("unchecked")
    public E relaxedPeek() {
        final E[] buffer = consumerBuffer;
        final long index = lpConsumerIndex();
        final long mask = consumerMask;

        final long offset = modifiedCalcCircularRefElementOffset(index, mask);
        Object e = lvRefElement(buffer, offset);
        if (e == JUMP) {
            return newBufferPeek(nextBuffer(buffer, mask), index);
        }
        return (E) e;
    }

    public int fill(Supplier<E> s) {
        long result = 0;// result is a long because we want to have a safepoint check at regular intervals
        final int capacity = capacity();
        do {
            final int filled = fill(s, Utility.cpus() * 4);
            if (filled == 0) {
                return (int) result;
            }
            result += filled;
        } while (result <= capacity);
        return (int) result;
    }

    public int fill(Supplier<E> s, int limit) {
        if (null == s)
            throw new IllegalArgumentException("supplier is null");
        if (limit < 0)
            throw new IllegalArgumentException("limit is negative:" + limit);
        if (limit == 0)
            return 0;

        long mask;
        E[] buffer;
        long pIndex;
        int claimedSlots;
        while (true) {
            long producerLimit0 = lvProducerLimit();
            pIndex = lvProducerIndex();
            // lower bit is indicative of resize, if we see it we spin until it's cleared
            if ((pIndex & 1) == 1) {
                continue;
            }
            // pIndex is even (lower bit is 0) -> actual index is (pIndex >> 1)

            // NOTE: mask/buffer may get changed by resizing -> only use for array access after successful CAS.
            // Only by virtue offloading them between the lvProducerIndex and a successful casProducerIndex are they
            // safe to use.
            mask = this.producerMask;
            buffer = this.producerBuffer;
            // a successful CAS ties the ordering, lv(pIndex) -> [mask/buffer] -> cas(pIndex)

            // we want 'limit' slots, but will settle for whatever is visible to 'producerLimit'
            long batchIndex = Math.min(producerLimit0, pIndex + 2l * limit); //  -> producerLimit >= batchIndex

            if (pIndex >= producerLimit0) {
                int result = offerSlowPath(mask, pIndex, producerLimit0);
                switch (result) {
                    case CONTINUE_TO_P_INDEX_CAS:
                    // offer slow path verifies only one slot ahead, we cannot rely on indication here
                    case RETRY:
                        continue;
                    case QUEUE_FULL:
                        return 0;
                    case QUEUE_RESIZE:
                        resize(mask, buffer, pIndex, null, s);
                        return 1;
                }
            }

            // claim limit slots at once
            if (casProducerIndex(pIndex, batchIndex)) {
                claimedSlots = (int) ((batchIndex - pIndex) / 2);
                break;
            }
        }

        for (int i = 0; i < claimedSlots; i++) {
            final long offset = modifiedCalcCircularRefElementOffset(pIndex + 2l * i, mask);
            soRefElement(buffer, offset, s.get());
        }
        return claimedSlots;
    }

    /**
     * Get an iterator for this queue. This method is thread safe.
     * <p>
     * The iterator provides a best-effort snapshot of the elements in the queue.
     * The returned iterator is not guaranteed to return elements in queue order,
     * and races with the consumer thread may cause gaps in the sequence of returned elements.
     * Like {link #relaxedPoll}, the iterator may not immediately return newly inserted elements.
     *
     * @return The iterator.
     */
    @Override
    public Iterator<E> iterator() {
        return new WeakIterator(consumerBuffer, lvConsumerIndex(), lvProducerIndex());
    }

    private static class WeakIterator<E> implements Iterator<E> {

        private final long pIndex;

        private long nextIndex;

        private E nextElement;

        private E[] currentBuffer;

        private int mask;

        WeakIterator(E[] currentBuffer, long cIndex, long pIndex) {
            this.pIndex = pIndex >> 1;
            this.nextIndex = cIndex >> 1;
            setBuffer(currentBuffer);
            nextElement = getNext();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("remove");
        }

        @Override
        public boolean hasNext() {
            return nextElement != null;
        }

        @Override
        public E next() {
            final E e = nextElement;
            if (e == null) {
                throw new NoSuchElementException();
            }
            nextElement = getNext();
            return e;
        }

        private void setBuffer(E[] buffer) {
            this.currentBuffer = buffer;
            this.mask = buffer.length - 2;
        }

        private E getNext() {
            while (nextIndex < pIndex) {
                long index = nextIndex++;
                E e = lvRefElement(currentBuffer, calcCircularRefElementOffset(index, mask));
                // skip removed/not yet visible elements
                if (e == null) {
                    continue;
                }

                // not null && not JUMP -> found next element
                if (e != JUMP) {
                    return e;
                }

                // need to jump to the next buffer
                int nextBufferIndex = mask + 1;
                Object nextBuffer = lvRefElement(currentBuffer,
                    calcRefElementOffset(nextBufferIndex));

                if (nextBuffer == BUFFER_CONSUMED || nextBuffer == null) {
                    // Consumer may have passed us, or the next buffer is not visible yet: drop out early
                    return null;
                }

                setBuffer((E[]) nextBuffer);
                // now with the new array retry the load, it can't be a JUMP, but we need to repeat same index
                e = lvRefElement(currentBuffer, calcCircularRefElementOffset(index, mask));
                // skip removed/not yet visible elements
                if (e == null) {
                    continue;
                } else {
                    return e;
                }

            }
            return null;
        }
    }

    private void resize(long oldMask, E[] oldBuffer, long pIndex, E e, Supplier<E> s) {
        assert (e != null && s == null) || (e == null || s != null);
        int newBufferLength = getNextBufferSize(oldBuffer);
        final E[] newBuffer;
        try {
            newBuffer = (E[]) new Object[newBufferLength];
        } catch (OutOfMemoryError oom) {
            assert lvProducerIndex() == pIndex + 1;
            soProducerIndex(pIndex);
            throw oom;
        }

        producerBuffer = newBuffer;
        final int newMask = (newBufferLength - 2) << 1;
        producerMask = newMask;

        final long offsetInOld = modifiedCalcCircularRefElementOffset(pIndex, oldMask);
        final long offsetInNew = modifiedCalcCircularRefElementOffset(pIndex, newMask);

        soRefElement(newBuffer, offsetInNew, e == null ? s.get() : e);// element in new array
        soRefElement(oldBuffer, nextArrayOffset(oldMask), newBuffer);// buffer linked

        // ASSERT code
        final long cIndex = lvConsumerIndex();
        final long availableInQueue = availableInQueue(pIndex, cIndex);
        if (availableInQueue < 0) throw new IllegalArgumentException("availableInQueue: " + availableInQueue + " (expected: > 0)");

        // Invalidate racing CASs
        // We never set the limit beyond the bounds of a buffer
        soProducerLimit(pIndex + Math.min(newMask, availableInQueue));

        // make resize visible to the other producers
        soProducerIndex(pIndex + 2);

        // INDEX visible before ELEMENT, consistent with consumer expectation
        // make resize visible to consumer
        soRefElement(oldBuffer, offsetInOld, JUMP);
    }

    protected long availableInQueue(long pIndex, long cIndex) {
        return maxQueueCapacity - (pIndex - cIndex);
    }

    public int capacity() {
        return (int) (maxQueueCapacity / 2);
    }

    protected int getNextBufferSize(E[] buffer) {
        return buffer.length;
    }

    protected long getCurrentBufferCapacity(long mask) {
        return mask;
    }
}
