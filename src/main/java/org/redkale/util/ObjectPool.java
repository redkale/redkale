/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.*;
import java.util.logging.*;

/**
 * 对象池
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <T> 对象池元素的数据类型
 */
public class ObjectPool<T> implements Supplier<T>, Consumer<T> {

	protected static final Logger logger = Logger.getLogger(ObjectPool.class.getSimpleName());

	protected final boolean debug;

	protected Creator<T> creator;

	protected int max;

	protected final Consumer<T> prepare;

	protected final Predicate<T> recycler;

	protected final LongAdder creatCounter;

	protected final LongAdder cycleCounter;

	protected final Queue<T> queue;

	protected final boolean unsafeDequeable;

	protected final ObjectPool<T> parent;

	protected Thread unsafeThread;

	// true表示unsafeThread不为空且当前为非线程安全版且parent为线程安全版
	protected final boolean safeCombine;

	protected ObjectPool(
			ObjectPool<T> parent,
			LongAdder creatCounter,
			LongAdder cycleCounter,
			Thread unsafeThread,
			int max,
			Creator<T> creator,
			Consumer<T> prepare,
			Predicate<T> recycler,
			Queue<T> queue) {
		this.parent = parent;
		this.creatCounter = creatCounter;
		this.cycleCounter = cycleCounter;
		this.unsafeThread = unsafeThread;
		this.creator = creator;
		this.prepare = prepare;
		this.recycler = recycler;
		this.max = max;
		this.debug = logger.isLoggable(Level.FINEST);
		this.queue = queue;
		this.unsafeDequeable = queue instanceof ArrayDeque;
		this.safeCombine = unsafeThread != null && unsafeDequeable && parent != null && !parent.unsafeDequeable;
	}

	// 非线程安全版
	public static <T> ObjectPool<T> createUnsafePool(Class<T> clazz, Consumer<T> prepare, Predicate<T> recycler) {
		return createUnsafePool(2, clazz, prepare, recycler);
	}

	// 非线程安全版
	public static <T> ObjectPool<T> createUnsafePool(
			int max, Class<T> clazz, Consumer<T> prepare, Predicate<T> recycler) {
		return createUnsafePool(max, Creator.create(clazz), prepare, recycler);
	}

	// 非线程安全版
	public static <T> ObjectPool<T> createUnsafePool(Creator<T> creator, Consumer<T> prepare, Predicate<T> recycler) {
		return createUnsafePool(2, creator, prepare, recycler);
	}

	// 非线程安全版
	public static <T> ObjectPool<T> createUnsafePool(
			int max, Creator<T> creator, Consumer<T> prepare, Predicate<T> recycler) {
		return createUnsafePool(null, null, max, creator, prepare, recycler);
	}

	// 非线程安全版
	public static <T> ObjectPool<T> createUnsafePool(
			int max, Supplier<T> creator, Consumer<T> prepare, Predicate<T> recycler) {
		return createUnsafePool(null, null, max, creator, prepare, recycler);
	}

	// 非线程安全版
	public static <T> ObjectPool<T> createUnsafePool(
			LongAdder creatCounter,
			LongAdder cycleCounter,
			int max,
			Supplier<T> creator,
			Consumer<T> prepare,
			Predicate<T> recycler) {
		return createUnsafePool(creatCounter, cycleCounter, max, c -> creator.get(), prepare, recycler);
	}

	// 非线程安全版
	public static <T> ObjectPool<T> createUnsafePool(
			LongAdder creatCounter,
			LongAdder cycleCounter,
			int max,
			Creator<T> creator,
			Consumer<T> prepare,
			Predicate<T> recycler) {
		return createUnsafePool(null, creatCounter, cycleCounter, max, creator, prepare, recycler);
	}

	// 非线程安全版
	public static <T> ObjectPool<T> createUnsafePool(
			ObjectPool<T> parent, Class<T> clazz, Consumer<T> prepare, Predicate<T> recycler) {
		return createUnsafePool(parent, 2, clazz, prepare, recycler);
	}

	// 非线程安全版
	public static <T> ObjectPool<T> createUnsafePool(
			ObjectPool<T> parent, int max, Class<T> clazz, Consumer<T> prepare, Predicate<T> recycler) {
		return createUnsafePool(parent, max, Creator.create(clazz), prepare, recycler);
	}

	// 非线程安全版
	public static <T> ObjectPool<T> createUnsafePool(
			ObjectPool<T> parent, Creator<T> creator, Consumer<T> prepare, Predicate<T> recycler) {
		return createUnsafePool(parent, 2, creator, prepare, recycler);
	}

	// 非线程安全版
	public static <T> ObjectPool<T> createUnsafePool(
			ObjectPool<T> parent, int max, Creator<T> creator, Consumer<T> prepare, Predicate<T> recycler) {
		return createUnsafePool(parent, null, null, max, creator, prepare, recycler);
	}

	// 非线程安全版
	public static <T> ObjectPool<T> createUnsafePool(
			ObjectPool<T> parent, int max, Supplier<T> creator, Consumer<T> prepare, Predicate<T> recycler) {
		return createUnsafePool(parent, null, null, max, creator, prepare, recycler);
	}

	// 非线程安全版
	public static <T> ObjectPool<T> createUnsafePool(
			ObjectPool<T> parent,
			LongAdder creatCounter,
			LongAdder cycleCounter,
			int max,
			Supplier<T> creator,
			Consumer<T> prepare,
			Predicate<T> recycler) {
		return createUnsafePool(parent, creatCounter, cycleCounter, max, c -> creator.get(), prepare, recycler);
	}

	// 非线程安全版
	public static <T> ObjectPool<T> createUnsafePool(
			ObjectPool<T> parent,
			LongAdder creatCounter,
			LongAdder cycleCounter,
			int max,
			Creator<T> creator,
			Consumer<T> prepare,
			Predicate<T> recycler) {
		return new ObjectPool(
				parent,
				creatCounter,
				cycleCounter,
				null,
				Math.max(Utility.cpus(), max),
				creator,
				prepare,
				recycler,
				new ArrayDeque<>(Math.max(Utility.cpus(), max)));
	}

	// 非线程安全版
	public static <T> ObjectPool<T> createUnsafePool(
			ObjectPool<T> parent,
			LongAdder creatCounter,
			LongAdder cycleCounter,
			Thread unsafeThread,
			int max,
			Creator<T> creator,
			Consumer<T> prepare,
			Predicate<T> recycler) {
		return new ObjectPool(
				parent,
				creatCounter,
				cycleCounter,
				unsafeThread,
				Math.max(Utility.cpus(), max),
				creator,
				prepare,
				recycler,
				new ArrayDeque<>(Math.max(Utility.cpus(), max)));
	}

	// 非线程安全版
	public static <T> ObjectPool<T> createUnsafePool(Thread unsafeThread, int max, ObjectPool<T> safePool) {
		return createUnsafePool(
				safePool,
				safePool.getCreatCounter(),
				safePool.getCycleCounter(),
				unsafeThread,
				max,
				safePool.getCreator(),
				safePool.getPrepare(),
				safePool.getRecycler());
	}

	// 线程安全版
	public static <T> ObjectPool<T> createSafePool(Class<T> clazz, Consumer<T> prepare, Predicate<T> recycler) {
		return createSafePool(2, clazz, prepare, recycler);
	}

	// 线程安全版
	public static <T> ObjectPool<T> createSafePool(
			int max, Class<T> clazz, Consumer<T> prepare, Predicate<T> recycler) {
		return createSafePool(max, Creator.create(clazz), prepare, recycler);
	}

	// 线程安全版
	public static <T> ObjectPool<T> createSafePool(Creator<T> creator, Consumer<T> prepare, Predicate<T> recycler) {
		return createSafePool(2, creator, prepare, recycler);
	}

	// 线程安全版
	public static <T> ObjectPool<T> createSafePool(
			int max, Creator<T> creator, Consumer<T> prepare, Predicate<T> recycler) {
		return createSafePool(null, null, max, creator, prepare, recycler);
	}

	// 线程安全版
	public static <T> ObjectPool<T> createSafePool(
			int max, Supplier<T> creator, Consumer<T> prepare, Predicate<T> recycler) {
		return createSafePool(null, null, max, creator, prepare, recycler);
	}

	// 线程安全版
	public static <T> ObjectPool<T> createSafePool(
			LongAdder creatCounter,
			LongAdder cycleCounter,
			int max,
			Supplier<T> creator,
			Consumer<T> prepare,
			Predicate<T> recycler) {
		return createSafePool(creatCounter, cycleCounter, max, c -> creator.get(), prepare, recycler);
	}

	// 线程安全版
	public static <T> ObjectPool<T> createSafePool(
			LongAdder creatCounter,
			LongAdder cycleCounter,
			int max,
			Creator<T> creator,
			Consumer<T> prepare,
			Predicate<T> recycler) {
		return new ObjectPool(
				null,
				creatCounter,
				cycleCounter,
				null,
				Math.max(Utility.cpus(), max),
				creator,
				prepare,
				recycler,
				new LinkedBlockingQueue<>(Math.max(Utility.cpus(), max)));
	}

	public void setCreator(Creator<T> creator) {
		this.creator = creator;
	}

	public Creator<T> getCreator() {
		return this.creator;
	}

	public int getMax() {
		return max;
	}

	public Consumer<T> getPrepare() {
		return prepare;
	}

	public Predicate<T> getRecycler() {
		return recycler;
	}

	public LongAdder getCreatCounter() {
		return creatCounter;
	}

	public LongAdder getCycleCounter() {
		return cycleCounter;
	}

	@Override
	public T get() {
		if (safeCombine) {
			if (Thread.currentThread() != unsafeThread) {
				return parent.get();
			}
		} else if (unsafeDequeable) {
			if (unsafeThread == null) {
				unsafeThread = Thread.currentThread();
			} else if (unsafeThread != Thread.currentThread()) {
				throw new RedkaleException(
						"unsafeThread is " + unsafeThread + ", but currentThread is " + Thread.currentThread());
			}
		}
		T result = queue.poll();
		if (result == null) {
			if (parent != null) {
				result = parent.queue.poll();
			}
			if (result == null) {
				if (creatCounter != null) {
					creatCounter.increment();
				}
				result = this.creator.create();
			}
		}
		if (prepare != null) {
			prepare.accept(result);
		}
		return result;
	}

	@Override
	public void accept(final T e) {
		if (e == null) {
			return;
		}
		if (safeCombine) {
			if (Thread.currentThread() != unsafeThread) {
				parent.accept(e);
				return;
			}
		} else if (unsafeDequeable) {
			if (unsafeThread == null) {
				unsafeThread = Thread.currentThread();
			} else if (unsafeThread != Thread.currentThread()) {
				throw new RedkaleException(
						"unsafeThread is " + unsafeThread + ", but currentThread is " + Thread.currentThread());
			}
		}
		if (recycler.test(e)) {
			if (cycleCounter != null) {
				cycleCounter.increment();
			}
			//            if (debug) {
			//                for (T t : queue) {
			//                    if (t == e) {
			//                        logger.log(Level.WARNING, "repeat offer the same object(" + e + ")", new
			// Exception());
			//                        return;
			//                    }
			//                }
			//            }
			boolean rs = unsafeDequeable ? queue.size() < max && queue.offer(e) : queue.offer(e);
			if (!rs && parent != null) {
				parent.accept(e);
			}
		}
	}

	public long getCreatCount() {
		return creatCounter == null ? -1 : creatCounter.longValue();
	}

	public long getCycleCount() {
		return cycleCounter == null ? -1 : cycleCounter.longValue();
	}
}
