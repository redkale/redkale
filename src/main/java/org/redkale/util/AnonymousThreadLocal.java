/// *
// *
// */
// package org.redkale.util;
//
// import java.util.function.Function;
// import java.util.function.Supplier;
//
/// **
// * ThreadScopedLocal, 兼容虚拟线程的ThreadLocal
// *
// * <p>
// * 详情见: https://redkale.org
// *
// * @author zhangjx
// * @since 2.8.0
// */
// public class AnonymousThreadLocal<T> extends ThreadLocal<T> implements Function<Supplier<T>, ThreadLocal<T>> {
//
//    private final Supplier<T> supplier;
//
//    public AnonymousThreadLocal(Supplier<T> supplier) {
//        this.supplier = supplier;
//    }
//
//    public ThreadLocal<T> apply(Supplier<T> supplier) {
//        return new AnonymousThreadLocal<>(supplier);
//    }
//
//    @Override
//    protected T initialValue() {
//        return supplier.get();
//    }
//
//    @Override
//    public void set(T value) {
//        Thread t = Thread.currentThread();
//        if (!t.isVirtual()) {
//            super.set(value);
//        }
//    }
//
//    @Override
//    public T get() {
//        Thread t = Thread.currentThread();
//        return t.isVirtual() ? initialValue() : super.get();
//    }
// }
