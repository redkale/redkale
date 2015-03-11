/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net;

import java.io.*;
import java.lang.invoke.*;
import java.lang.ref.SoftReference;
import java.lang.reflect.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import sun.misc.Cleaner;
import sun.security.action.GetIntegerAction;

/**
 *
 * @author zhangjx
 */
@SuppressWarnings("unchecked")
public final class AsyncDatagramChannel implements AsynchronousByteChannel, MulticastChannel {

    private final DatagramChannel dc;

    private final AsynchronousChannelGroupProxy group;

    private final Object attachKey;

    private boolean closed;

    // used to coordinate timed and blocking reads
    private final Object readLock = new Object();

    // channel blocking mode (requires readLock)
    private boolean isBlocking = true;

    // number of blocking readers (requires readLock)
    private int blockingReaderCount;

    // true if timed read attempted while blocking read in progress (requires readLock)
    private boolean transitionToNonBlocking;

    // true if a blocking read is cancelled (requires readLock)
    private boolean blockingReadKilledByCancel;

    // temporary Selectors used by timed reads (requires readLock)
    private Selector firstReader;

    private Set<Selector> otherReaders;

    private static final sun.misc.Unsafe UNSAFE;

    private static final long fdoffset;

    static {
        sun.misc.Unsafe usafe = null;
        long fd = 0L;
        try {
            Field safeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            safeField.setAccessible(true);
            usafe = (sun.misc.Unsafe) safeField.get(null);
            fd = usafe.objectFieldOffset(DatagramChannel.open().getClass().getDeclaredField("fd"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        UNSAFE = usafe;
        fdoffset = fd;
    }

    private AsyncDatagramChannel(ProtocolFamily family, AsynchronousChannelGroup group0)
            throws IOException {
        this.dc = (family == null) ? DatagramChannel.open() : DatagramChannel.open(family);
        if (group0 == null) group0 = AsynchronousChannelGroup.withThreadPool(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2));
        this.group = new AsynchronousChannelGroupProxy(group0);

        // attach this channel to the group as foreign channel
        boolean registered = false;
        try {
            attachKey = group.attachForeignChannel(this, (FileDescriptor) UNSAFE.getObject(dc, fdoffset));
            registered = true;
        } finally {
            if (!registered)
                dc.close();
        }
    }

    public static AsyncDatagramChannel open(AsynchronousChannelGroup group) throws IOException {
        return open(null, group);
    }

    public static AsyncDatagramChannel open(ProtocolFamily family, AsynchronousChannelGroup group)
            throws IOException {
        return new AsyncDatagramChannel(family, group);
    }

    @Override
    public final <A> void read(ByteBuffer dst, A attachment, CompletionHandler<Integer, ? super A> handler) {
        read(dst, 0L, TimeUnit.MILLISECONDS, attachment, handler);
    }

    // throws RuntimeException if blocking read has been cancelled
    private void ensureBlockingReadNotKilled() {
        assert Thread.holdsLock(readLock);
        if (blockingReadKilledByCancel) throw new RuntimeException("Reading not allowed due to cancellation");
    }

    // invoke prior to non-timed read/receive
    private void beginNoTimeoutRead() {
        synchronized (readLock) {
            ensureBlockingReadNotKilled();
            if (isBlocking) blockingReaderCount++;
        }
    }

    // invoke after non-timed read/receive has completed
    private void endNoTimeoutRead() {
        synchronized (readLock) {
            if (isBlocking) {
                if (--blockingReaderCount == 0 && transitionToNonBlocking) {
                    // notify any threads waiting to make channel non-blocking
                    readLock.notifyAll();
                }
            }
        }
    }

    // invoke prior to timed read
    // returns the timeout remaining
    private long prepareForTimedRead(PendingFuture<?, ?> result, long timeout) throws IOException {
        synchronized (readLock) {
            ensureBlockingReadNotKilled();
            if (isBlocking) {
                transitionToNonBlocking = true;
                while (blockingReaderCount > 0 && timeout > 0L && !result.isCancelled()) {
                    long st = System.currentTimeMillis();
                    try {
                        readLock.wait(timeout);
                    } catch (InterruptedException e) {
                    }
                    timeout -= System.currentTimeMillis() - st;
                }
                if (blockingReaderCount == 0) {
                    // re-check that blocked read wasn't cancelled
                    ensureBlockingReadNotKilled();
                    // no blocking reads so change channel to non-blocking
                    dc.configureBlocking(false);
                    isBlocking = false;
                }
            }
            return timeout;
        }
    }

    // returns a temporary Selector
    private Selector getSelector() throws IOException {
        Selector sel = getTemporarySelector(dc);
        synchronized (readLock) {
            if (firstReader == null) {
                firstReader = sel;
            } else {
                if (otherReaders == null) otherReaders = new HashSet<>();
                otherReaders.add(sel);
            }
        }
        return sel;
    }

    // releases a temporary Selector
    private void releaseSelector(Selector sel) throws IOException {
        synchronized (readLock) {
            if (firstReader == sel) {
                firstReader = null;
            } else {
                otherReaders.remove(sel);
            }
        }
        releaseTemporarySelector(sel);
    }

    // wakeup all Selectors currently in use
    private void wakeupSelectors() {
        synchronized (readLock) {
            if (firstReader != null)
                firstReader.wakeup();
            if (otherReaders != null) {
                for (Selector sel : otherReaders) {
                    sel.wakeup();
                }
            }
        }
    }

    public AsynchronousChannelGroupProxy group() {
        return group;
    }

    @Override
    public boolean isOpen() {
        return dc.isOpen();
    }

    public void onCancel(PendingFuture<?, ?> task) {
        synchronized (readLock) {
            if (blockingReaderCount > 0) {
                blockingReadKilledByCancel = true;
                readLock.notifyAll();
                return;
            }
        }
        wakeupSelectors();
    }

    @Override
    public void close() throws IOException {
        synchronized (dc) {
            if (closed) return;
            closed = true;
        }
        // detach from group and close underlying channel
        group.detachForeignChannel(attachKey);
        dc.close();

        // wakeup any threads blocked in timed read/receives
        wakeupSelectors();
    }

    public AsyncDatagramChannel connect(SocketAddress remote)
            throws IOException {
        dc.connect(remote);
        return this;
    }

    public AsyncDatagramChannel disconnect() throws IOException {
        dc.disconnect();
        return this;
    }

    private static class WrappedMembershipKey extends MembershipKey {

        private final MulticastChannel channel;

        private final MembershipKey key;

        WrappedMembershipKey(MulticastChannel channel, MembershipKey key) {
            this.channel = channel;
            this.key = key;
        }

        @Override
        public boolean isValid() {
            return key.isValid();
        }

        @Override
        public void drop() {
            key.drop();
        }

        @Override
        public MulticastChannel channel() {
            return channel;
        }

        @Override
        public InetAddress group() {
            return key.group();
        }

        @Override
        public NetworkInterface networkInterface() {
            return key.networkInterface();
        }

        @Override
        public InetAddress sourceAddress() {
            return key.sourceAddress();
        }

        @Override
        public MembershipKey block(InetAddress toBlock) throws IOException {
            key.block(toBlock);
            return this;
        }

        @Override
        public MembershipKey unblock(InetAddress toUnblock) {
            key.unblock(toUnblock);
            return this;
        }

        @Override
        public String toString() {
            return key.toString();
        }
    }

    @Override
    public MembershipKey join(InetAddress group,
            NetworkInterface interf)
            throws IOException {
        MembershipKey key = ((MulticastChannel) dc).join(group, interf);
        return new WrappedMembershipKey(this, key);
    }

    @Override
    public MembershipKey join(InetAddress group, NetworkInterface interf, InetAddress source) throws IOException {
        MembershipKey key = ((MulticastChannel) dc).join(group, interf, source);
        return new WrappedMembershipKey(this, key);
    }

    private <A> Future<Integer> implSend(ByteBuffer src, SocketAddress target, A attachment, CompletionHandler<Integer, ? super A> handler) {
        int n = 0;
        Throwable exc = null;
        try {
            n = dc.send(src, target);
        } catch (IOException ioe) {
            exc = ioe;
        }
        if (handler == null)
            return CompletedFuture.withResult(n, exc);
        Invoker.invoke(this, handler, attachment, n, exc);
        return null;
    }

    public Future<Integer> send(ByteBuffer src, SocketAddress target) {
        return implSend(src, target, null, null);
    }

    public <A> void send(ByteBuffer src, SocketAddress target, A attachment, CompletionHandler<Integer, ? super A> handler) {
        if (handler == null) throw new NullPointerException("'handler' is null");
        implSend(src, target, attachment, handler);
    }

    private <A> Future<Integer> implWrite(ByteBuffer src, A attachment, CompletionHandler<Integer, ? super A> handler) {
        int n = 0;
        Throwable exc = null;
        try {
            n = dc.write(src);
        } catch (IOException ioe) {
            exc = ioe;
        }
        if (handler == null) return CompletedFuture.withResult(n, exc);
        Invoker.invoke(this, handler, attachment, n, exc);
        return null;

    }

    @Override
    public Future<Integer> write(ByteBuffer src) {
        return implWrite(src, null, null);
    }

    @Override
    public <A> void write(ByteBuffer src, A attachment, CompletionHandler<Integer, ? super A> handler) {
        if (handler == null) throw new NullPointerException("'handler' is null");
        implWrite(src, attachment, handler);
    }

    /**
     * Receive into the given buffer with privileges enabled and restricted by the given AccessControlContext (can be null).
     */
    private SocketAddress doRestrictedReceive(final ByteBuffer dst,
            AccessControlContext acc)
            throws IOException {
        if (acc == null) {
            return dc.receive(dst);
        } else {
            try {
                return AccessController.doPrivileged(
                        new PrivilegedExceptionAction<SocketAddress>() {
                            public SocketAddress run() throws IOException {
                                return dc.receive(dst);
                            }
                        }, acc);
            } catch (PrivilegedActionException pae) {
                Exception cause = pae.getException();
                if (cause instanceof SecurityException)
                    throw (SecurityException) cause;
                throw (IOException) cause;
            }
        }
    }

    private <A> Future<SocketAddress> implReceive(final ByteBuffer dst, final long timeout,
            final TimeUnit unit, A attachment, final CompletionHandler<SocketAddress, ? super A> handler) {
        if (dst.isReadOnly()) throw new IllegalArgumentException("Read-only buffer");
        if (timeout < 0L) throw new IllegalArgumentException("Negative timeout");
        if (unit == null) throw new NullPointerException();

        // complete immediately if channel closed
        if (!isOpen()) {
            Throwable exc = new ClosedChannelException();
            if (handler == null) return CompletedFuture.withFailure(exc);
            Invoker.invoke(this, handler, attachment, null, exc);
            return null;
        }

        final AccessControlContext acc = (System.getSecurityManager() == null) ? null : AccessController.getContext();
        final PendingFuture<SocketAddress, A> result = new PendingFuture<SocketAddress, A>(this, handler, attachment);
        Runnable task = new Runnable() {
            public void run() {
                try {
                    SocketAddress remote = null;
                    long to;
                    if (timeout == 0L) {
                        beginNoTimeoutRead();
                        try {
                            remote = doRestrictedReceive(dst, acc);
                        } finally {
                            endNoTimeoutRead();
                        }
                        to = 0L;
                    } else {
                        to = prepareForTimedRead(result, unit.toMillis(timeout));
                        if (to <= 0L) throw new InterruptedByTimeoutException();
                        remote = doRestrictedReceive(dst, acc);
                    }
                    if (remote == null) {
                        Selector sel = getSelector();
                        SelectionKey sk = null;
                        try {
                            sk = dc.register(sel, SelectionKey.OP_READ);
                            for (;;) {
                                if (!dc.isOpen()) throw new AsynchronousCloseException();
                                if (result.isCancelled()) break;
                                long st = System.currentTimeMillis();
                                int ns = sel.select(to);
                                if (ns > 0) {
                                    remote = doRestrictedReceive(dst, acc);
                                    if (remote != null) break;
                                }
                                sel.selectedKeys().remove(sk);
                                if (timeout != 0L) {
                                    to -= System.currentTimeMillis() - st;
                                    if (to <= 0) throw new InterruptedByTimeoutException();
                                }
                            }
                        } finally {
                            if (sk != null)
                                sk.cancel();
                            releaseSelector(sel);
                        }
                    }
                    result.setResult(remote);
                } catch (Exception x) {
                    if (x instanceof ClosedChannelException)
                        x = new AsynchronousCloseException();
                    result.setFailure(x);
                }
                Invoker.invokeUnchecked(result);
            }
        };
        try {
            group.executeOnPooledThread(task);
        } catch (RejectedExecutionException ree) {
            throw new ShutdownChannelGroupException();
        }
        return result;
    }

    public Future<SocketAddress> receive(ByteBuffer dst) {
        return implReceive(dst, 0L, TimeUnit.MILLISECONDS, null, null);
    }

    public <A> void receive(ByteBuffer dst, A attachment, CompletionHandler<SocketAddress, ? super A> handler) {
        receive(dst, 0L, TimeUnit.MILLISECONDS, attachment, handler);
    }

    public <A> void receive(ByteBuffer dst, long timeout, TimeUnit unit, A attachment, CompletionHandler<SocketAddress, ? super A> handler) {
        if (handler == null) throw new NullPointerException("'handler' is null");
        implReceive(dst, timeout, unit, attachment, handler);
    }

    private <A> Future<Integer> implRead(final ByteBuffer dst, long timeout, TimeUnit unit, A attachment, CompletionHandler<Integer, ? super A> handler) {
        if (dst.isReadOnly()) throw new IllegalArgumentException("Read-only buffer");
        if (timeout < 0L) throw new IllegalArgumentException("Negative timeout");
        if (unit == null) throw new NullPointerException();

        // complete immediately if channel closed
        if (!isOpen()) {
            Throwable exc = new ClosedChannelException();
            if (handler == null) return CompletedFuture.withFailure(exc);
            Invoker.invoke(this, handler, attachment, null, exc);
            return null;
        }

        // another thread may disconnect before read is initiated
        if (!dc.isConnected()) throw new NotYetConnectedException();

        final PendingFuture<Integer, A> result = new PendingFuture<Integer, A>(this, handler, attachment);
        Runnable task = new Runnable() {
            public void run() {
                try {
                    int n = 0;
                    long to;
                    if (timeout == 0L) {
                        beginNoTimeoutRead();
                        try {
                            n = dc.read(dst);
                        } finally {
                            endNoTimeoutRead();
                        }
                        to = 0L;
                    } else {
                        to = prepareForTimedRead(result, unit.toMillis(timeout));
                        if (to <= 0L) throw new InterruptedByTimeoutException();
                        n = dc.read(dst);
                    }
                    if (n == 0) {
                        Selector sel = getSelector();
                        SelectionKey sk = null;
                        try {
                            sk = dc.register(sel, SelectionKey.OP_READ);
                            for (;;) {
                                if (!dc.isOpen()) throw new AsynchronousCloseException();
                                if (result.isCancelled()) break;
                                long st = System.currentTimeMillis();
                                int ns = sel.select(to);
                                if (ns > 0) {
                                    if ((n = dc.read(dst)) != 0) break;
                                }
                                sel.selectedKeys().remove(sk);
                                if (timeout != 0L) {
                                    to -= System.currentTimeMillis() - st;
                                    if (to <= 0) throw new InterruptedByTimeoutException();
                                }
                            }
                        } finally {
                            if (sk != null)
                                sk.cancel();
                            releaseSelector(sel);
                        }
                    }
                    result.setResult(n);
                } catch (Exception x) {
                    if (x instanceof ClosedChannelException) x = new AsynchronousCloseException();
                    result.setFailure(x);
                }
                Invoker.invokeUnchecked(result);
            }
        };
        try {
            group.executeOnPooledThread(task);
        } catch (RejectedExecutionException ree) {
            throw new ShutdownChannelGroupException();
        }
        return result;
    }

    @Override
    public Future<Integer> read(ByteBuffer dst) {
        return implRead(dst, 0L, TimeUnit.MILLISECONDS, null, null);
    }

    public <A> void read(ByteBuffer dst, long timeout, TimeUnit unit, A attachment, CompletionHandler<Integer, ? super A> handler) {
        if (handler == null) throw new NullPointerException("'handler' is null");
        implRead(dst, timeout, unit, attachment, handler);
    }

    @Override
    public AsyncDatagramChannel bind(SocketAddress local) throws IOException {
        dc.bind(local);
        return this;
    }

    @Override
    public SocketAddress getLocalAddress() throws IOException {
        return dc.getLocalAddress();
    }

    @Override
    public <T> AsyncDatagramChannel setOption(SocketOption<T> name, T value) throws IOException {
        dc.setOption(name, value);
        return this;
    }

    @Override
    public <T> T getOption(SocketOption<T> name) throws IOException {
        return dc.getOption(name);
    }

    @Override
    public Set<SocketOption<?>> supportedOptions() {
        return dc.supportedOptions();
    }

    public SocketAddress getRemoteAddress() throws IOException {
        return dc.getRemoteAddress();
    }

    private static class SelectorWrapper {

        private Selector sel;

        private SelectorWrapper(Selector sel) {
            this.sel = sel;
            Cleaner.create(this, new Closer(sel));
        }

        private static class Closer implements Runnable {

            private Selector sel;

            private Closer(Selector sel) {
                this.sel = sel;
            }

            public void run() {
                try {
                    sel.close();
                } catch (Exception th) {
                    //throw new Error(th);
                }
            }
        }

        public Selector get() {
            return sel;
        }
    }

    private static ThreadLocal<SoftReference<SelectorWrapper>> localSelector
            = new ThreadLocal<SoftReference<SelectorWrapper>>();

    // Hold a reference to the selWrapper object to prevent it from
    // being cleaned when the temporary selector wrapped is on lease.
    private static ThreadLocal<SelectorWrapper> localSelectorWrapper
            = new ThreadLocal<SelectorWrapper>();

    static Selector getTemporarySelector(SelectableChannel sc)
            throws IOException {
        SoftReference<SelectorWrapper> ref = localSelector.get();
        SelectorWrapper selWrapper = null;
        Selector sel = null;
        if (ref == null
                || ((selWrapper = ref.get()) == null)
                || ((sel = selWrapper.get()) == null)
                || (sel.provider() != sc.provider())) {
            sel = sc.provider().openSelector();
            selWrapper = new SelectorWrapper(sel);
            localSelector.set(new SoftReference<SelectorWrapper>(selWrapper));
        }
        localSelectorWrapper.set(selWrapper);
        return sel;
    }

    static void releaseTemporarySelector(Selector sel)
            throws IOException {
        // Selector should be empty
        sel.selectNow();                // Flush cancelled keys
        assert sel.keys().isEmpty() : "Temporary selector not empty";
        localSelectorWrapper.set(null);
    }

}

final class AsynchronousChannelGroupProxy extends AsynchronousChannelGroup {

    private final AsynchronousChannelGroup group;

    private final MethodHandle executeOnPooledThread;

    private final MethodHandle attachForeignChannel;

    private final MethodHandle detachForeignChannel;

    public AsynchronousChannelGroupProxy(AsynchronousChannelGroup group) {
        super(group.provider());
        this.group = group;
        MethodHandle method1 = null, method2 = null, method3 = null;
        try {
            Method m = findGroupMethod(group.getClass(), "executeOnPooledThread", Runnable.class);
            m.setAccessible(true);
            method1 = MethodHandles.lookup().unreflect(m);

            m = findGroupMethod(group.getClass(), "attachForeignChannel", Channel.class, FileDescriptor.class);
            m.setAccessible(true);
            method2 = MethodHandles.lookup().unreflect(m);

            m = findGroupMethod(group.getClass(), "detachForeignChannel", Object.class);
            m.setAccessible(true);
            method3 = MethodHandles.lookup().unreflect(m);
        } catch (Exception e) {
            e.printStackTrace();
        }
        this.executeOnPooledThread = method1;
        this.attachForeignChannel = method2;
        this.detachForeignChannel = method3;
    }

    private static Method findGroupMethod(Class clazz, String methodname, Class... params) throws Exception {
        if (clazz == Object.class) return null;
        try {
            return clazz.getDeclaredMethod(methodname, params);
        } catch (NoSuchMethodException e) {
            return findGroupMethod(clazz.getSuperclass(), methodname, params);
        }
    }

    @Override
    public boolean isShutdown() {
        return group.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return group.isTerminated();
    }

    @Override
    public void shutdown() {
        group.shutdown();
    }

    @Override
    public void shutdownNow() throws IOException {
        group.shutdownNow();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return group.awaitTermination(timeout, unit);
    }

    Object attachForeignChannel(Channel channel, FileDescriptor fdo) throws IOException {
        try {
            return attachForeignChannel.invoke(group, channel, fdo);
        } catch (Throwable e) {
            throw new IOException(e);
        }
    }

    void detachForeignChannel(Object key) {
        try {
            detachForeignChannel.invoke(group, key);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    final void executeOnPooledThread(Runnable task) {
        try {
            executeOnPooledThread.invoke(group, task);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

}

final class PendingFuture<V, A> implements Future<V> {

    private static final CancellationException CANCELLED = new CancellationException();

    private final AsynchronousChannel channel;

    private final CompletionHandler<V, ? super A> handler;

    private final A attachment;

    // true if result (or exception) is available
    private volatile boolean haveResult;

    private volatile V result;

    private volatile Throwable exc;

    // latch for waiting (created lazily if needed)
    private CountDownLatch latch;

    // optional timer task that is cancelled when result becomes available
    private Future<?> timeoutTask;

    // optional context object
    private volatile Object context;

    PendingFuture(AsynchronousChannel channel, CompletionHandler<V, ? super A> handler, A attachment, Object context) {
        this.channel = channel;
        this.handler = handler;
        this.attachment = attachment;
        this.context = context;
    }

    PendingFuture(AsynchronousChannel channel, CompletionHandler<V, ? super A> handler, A attachment) {
        this.channel = channel;
        this.handler = handler;
        this.attachment = attachment;
    }

    PendingFuture(AsynchronousChannel channel) {
        this(channel, null, null);
    }

    PendingFuture(AsynchronousChannel channel, Object context) {
        this(channel, null, null, context);
    }

    AsynchronousChannel channel() {
        return channel;
    }

    CompletionHandler<V, ? super A> handler() {
        return handler;
    }

    A attachment() {
        return attachment;
    }

    void setContext(Object context) {
        this.context = context;
    }

    Object getContext() {
        return context;
    }

    void setTimeoutTask(Future<?> task) {
        synchronized (this) {
            if (haveResult) {
                task.cancel(false);
            } else {
                this.timeoutTask = task;
            }
        }
    }

    // creates latch if required; return true if caller needs to wait
    private boolean prepareForWait() {
        synchronized (this) {
            if (haveResult) {
                return false;
            } else {
                if (latch == null)
                    latch = new CountDownLatch(1);
                return true;
            }
        }
    }

    /**
     * Sets the result, or a no-op if the result or exception is already set.
     */
    void setResult(V res) {
        synchronized (this) {
            if (haveResult)
                return;
            result = res;
            haveResult = true;
            if (timeoutTask != null)
                timeoutTask.cancel(false);
            if (latch != null)
                latch.countDown();
        }
    }

    /**
     * Sets the result, or a no-op if the result or exception is already set.
     */
    void setFailure(Throwable x) {
        if (!(x instanceof IOException) && !(x instanceof SecurityException))
            x = new IOException(x);
        synchronized (this) {
            if (haveResult)
                return;
            exc = x;
            haveResult = true;
            if (timeoutTask != null)
                timeoutTask.cancel(false);
            if (latch != null)
                latch.countDown();
        }
    }

    /**
     * Sets the result
     */
    void setResult(V res, Throwable x) {
        if (x == null) {
            setResult(res);
        } else {
            setFailure(x);
        }
    }

    @Override
    public V get() throws ExecutionException, InterruptedException {
        if (!haveResult) {
            boolean needToWait = prepareForWait();
            if (needToWait)
                latch.await();
        }
        if (exc != null) {
            if (exc == CANCELLED)
                throw new CancellationException();
            throw new ExecutionException(exc);
        }
        return result;
    }

    @Override
    public V get(long timeout, TimeUnit unit)
            throws ExecutionException, InterruptedException, TimeoutException {
        if (!haveResult) {
            boolean needToWait = prepareForWait();
            if (needToWait)
                if (!latch.await(timeout, unit)) throw new TimeoutException();
        }
        if (exc != null) {
            if (exc == CANCELLED)
                throw new CancellationException();
            throw new ExecutionException(exc);
        }
        return result;
    }

    Throwable exception() {
        return (exc != CANCELLED) ? exc : null;
    }

    V value() {
        return result;
    }

    @Override
    public boolean isCancelled() {
        return (exc == CANCELLED);
    }

    @Override
    public boolean isDone() {
        return haveResult;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        synchronized (this) {
            if (haveResult)
                return false;    // already completed

            // notify channel
            if (channel() instanceof AsyncDatagramChannel)
                ((AsyncDatagramChannel) channel()).onCancel(this);

            // set result and cancel timer
            exc = CANCELLED;
            haveResult = true;
            if (timeoutTask != null)
                timeoutTask.cancel(false);
        }

        // close channel if forceful cancel
        if (mayInterruptIfRunning) {
            try {
                channel().close();
            } catch (IOException ignore) {
            }
        }

        // release waiters
        if (latch != null)
            latch.countDown();
        return true;
    }
}

final class CompletedFuture<V> implements Future<V> {

    private final V result;

    private final Throwable exc;

    private CompletedFuture(V result, Throwable exc) {
        this.result = result;
        this.exc = exc;
    }

    static <V> CompletedFuture<V> withResult(V result) {
        return new CompletedFuture<V>(result, null);
    }

    static <V> CompletedFuture<V> withFailure(Throwable exc) {
        // exception must be IOException or SecurityException
        if (!(exc instanceof IOException) && !(exc instanceof SecurityException))
            exc = new IOException(exc);
        return new CompletedFuture<V>(null, exc);
    }

    static <V> CompletedFuture<V> withResult(V result, Throwable exc) {
        if (exc == null) {
            return withResult(result);
        } else {
            return withFailure(exc);
        }
    }

    @Override
    public V get() throws ExecutionException {
        if (exc != null)
            throw new ExecutionException(exc);
        return result;
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws ExecutionException {
        if (unit == null)
            throw new NullPointerException();
        if (exc != null)
            throw new ExecutionException(exc);
        return result;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
        return true;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }
}

class Invoker {

    private Invoker() {
    }

    // maximum number of completion handlers that may be invoked on the current
    // thread before it re-directs invocations to the thread pool. This helps
    // avoid stack overflow and lessens the risk of starvation.
    private static final int maxHandlerInvokeCount = AccessController.doPrivileged(
            new GetIntegerAction("sun.nio.ch.maxCompletionHandlersOnStack", 16));

    // Per-thread object with reference to channel group and a counter for
    // the number of completion handlers invoked. This should be reset to 0
    // when all completion handlers have completed.
    static class GroupAndInvokeCount {

        private final AsynchronousChannelGroup group;

        private int handlerInvokeCount;

        GroupAndInvokeCount(AsynchronousChannelGroup group) {
            this.group = group;
        }

        AsynchronousChannelGroup group() {
            return group;
        }

        int invokeCount() {
            return handlerInvokeCount;
        }

        void setInvokeCount(int value) {
            handlerInvokeCount = value;
        }

        void resetInvokeCount() {
            handlerInvokeCount = 0;
        }

        void incrementInvokeCount() {
            handlerInvokeCount++;
        }
    }

    private static final ThreadLocal<GroupAndInvokeCount> myGroupAndInvokeCount
            = new ThreadLocal<GroupAndInvokeCount>() {
                @Override
                protected GroupAndInvokeCount initialValue() {
                    return null;
                }
            };

    /**
     * Binds this thread to the given group
     */
    static void bindToGroup(AsynchronousChannelGroup group) {
        myGroupAndInvokeCount.set(new GroupAndInvokeCount(group));
    }

    /**
     * Returns the GroupAndInvokeCount object for this thread.
     */
    static GroupAndInvokeCount getGroupAndInvokeCount() {
        return myGroupAndInvokeCount.get();
    }

    /**
     * Returns true if the current thread is in a channel group's thread pool
     */
    static boolean isBoundToAnyGroup() {
        return myGroupAndInvokeCount.get() != null;
    }

    /*
     * Returns true if the current thread is in the given channel's thread pool 
     * and we haven't exceeded the maximum number of handler frames on the stack.
     */
    static boolean mayInvokeDirect(GroupAndInvokeCount myGroupAndInvokeCount,
            AsynchronousChannelGroup group) {
        if ((myGroupAndInvokeCount != null)
                && (myGroupAndInvokeCount.group() == group)
                && (myGroupAndInvokeCount.invokeCount() < maxHandlerInvokeCount)) {
            return true;
        }
        return false;
    }

    /**
     * Invoke handler without checking the thread identity or number of handlers on the thread stack.
     */
    static <V, A> void invokeUnchecked(CompletionHandler<V, ? super A> handler,
            A attachment,
            V value,
            Throwable exc) {
        if (exc == null) {
            handler.completed(value, attachment);
        } else {
            handler.failed(exc, attachment);
        }

        // clear interrupt
        Thread.interrupted();
    }

    /**
     * Invoke handler assuming thread identity already checked
     */
    static <V, A> void invokeDirect(GroupAndInvokeCount myGroupAndInvokeCount,
            CompletionHandler<V, ? super A> handler,
            A attachment,
            V result,
            Throwable exc) {
        myGroupAndInvokeCount.incrementInvokeCount();
        Invoker.invokeUnchecked(handler, attachment, result, exc);
    }

    /**
     * Invokes the handler. If the current thread is in the channel group's thread pool then the handler is invoked directly, otherwise it is invoked indirectly.
     */
    static <V, A> void invoke(AsynchronousChannel channel,
            CompletionHandler<V, ? super A> handler,
            A attachment,
            V result,
            Throwable exc) {
        boolean invokeDirect = false;
        boolean identityOkay = false;
        GroupAndInvokeCount thisGroupAndInvokeCount = myGroupAndInvokeCount.get();
        if (thisGroupAndInvokeCount != null) {
            if ((thisGroupAndInvokeCount.group() == ((AsyncDatagramChannel) channel).group()))
                identityOkay = true;
            if (identityOkay
                    && (thisGroupAndInvokeCount.invokeCount() < maxHandlerInvokeCount)) {
                // group match
                invokeDirect = true;
            }
        }
        if (invokeDirect) {
            invokeDirect(thisGroupAndInvokeCount, handler, attachment, result, exc);
        } else {
            try {
                invokeIndirectly(channel, handler, attachment, result, exc);
            } catch (RejectedExecutionException ree) {
                // channel group shutdown; fallback to invoking directly
                // if the current thread has the right identity.
                if (identityOkay) {
                    invokeDirect(thisGroupAndInvokeCount,
                            handler, attachment, result, exc);
                } else {
                    throw new ShutdownChannelGroupException();
                }
            }
        }
    }

    /**
     * Invokes the handler indirectly via the channel group's thread pool.
     */
    static <V, A> void invokeIndirectly(AsynchronousChannel channel,
            final CompletionHandler<V, ? super A> handler,
            final A attachment,
            final V result,
            final Throwable exc) {
        try {
            ((AsyncDatagramChannel) channel).group().executeOnPooledThread(new Runnable() {
                public void run() {
                    GroupAndInvokeCount thisGroupAndInvokeCount
                            = myGroupAndInvokeCount.get();
                    if (thisGroupAndInvokeCount != null)
                        thisGroupAndInvokeCount.setInvokeCount(1);
                    invokeUnchecked(handler, attachment, result, exc);
                }
            });
        } catch (RejectedExecutionException ree) {
            throw new ShutdownChannelGroupException();
        }
    }

    /**
     * Invokes the handler "indirectly" in the given Executor
     */
    static <V, A> void invokeIndirectly(final CompletionHandler<V, ? super A> handler,
            final A attachment,
            final V value,
            final Throwable exc,
            Executor executor) {
        try {
            executor.execute(new Runnable() {
                public void run() {
                    invokeUnchecked(handler, attachment, value, exc);
                }
            });
        } catch (RejectedExecutionException ree) {
            throw new ShutdownChannelGroupException();
        }
    }

    /**
     * Invokes the given task on the thread pool associated with the given channel. If the current thread is in the thread pool then the task is invoked directly.
     */
    static void invokeOnThreadInThreadPool(AsyncDatagramChannel channel,
            Runnable task) {
        boolean invokeDirect;
        GroupAndInvokeCount thisGroupAndInvokeCount = myGroupAndInvokeCount.get();
        AsynchronousChannelGroupProxy targetGroup = channel.group();
        if (thisGroupAndInvokeCount == null) {
            invokeDirect = false;
        } else {
            invokeDirect = (thisGroupAndInvokeCount.group == targetGroup);
        }
        try {
            if (invokeDirect) {
                task.run();
            } else {
                targetGroup.executeOnPooledThread(task);
            }
        } catch (RejectedExecutionException ree) {
            throw new ShutdownChannelGroupException();
        }
    }

    /**
     * Invoke handler with completed result. This method does not check the thread identity or the number of handlers on the thread stack.
     */
    static <V, A> void invokeUnchecked(PendingFuture<V, A> future) {
        assert future.isDone();
        CompletionHandler<V, ? super A> handler = future.handler();
        if (handler != null) {
            invokeUnchecked(handler,
                    future.attachment(),
                    future.value(),
                    future.exception());
        }
    }

    /**
     * Invoke handler with completed result. If the current thread is in the channel group's thread pool then the handler is invoked directly, otherwise it is invoked indirectly.
     */
    static <V, A> void invoke(PendingFuture<V, A> future) {
        assert future.isDone();
        CompletionHandler<V, ? super A> handler = future.handler();
        if (handler != null) {
            invoke(future.channel(),
                    handler,
                    future.attachment(),
                    future.value(),
                    future.exception());
        }
    }

    /**
     * Invoke handler with completed result. The handler is invoked indirectly, via the channel group's thread pool.
     */
    static <V, A> void invokeIndirectly(PendingFuture<V, A> future) {
        assert future.isDone();
        CompletionHandler<V, ? super A> handler = future.handler();
        if (handler != null) {
            invokeIndirectly(future.channel(),
                    handler,
                    future.attachment(),
                    future.value(),
                    future.exception());
        }
    }
}
