package org.redkale.net;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.*;
import org.redkale.net.http.HttpContext;
import org.redkale.net.http.HttpRequest;

@Timeout(10)
class AsyncConnectionInputStreamTest {

    private static AsyncIOGroup group;

    @BeforeAll
    static void startGroup() {
        group = new AsyncIOGroup(8192, 16).start();
    }

    @AfterAll
    static void stopGroup() {
        group.close();
    }

    @Test
    void readsUnsignedBytesAndHonorsTargetOffsetWithoutPrefetching() throws Exception {
        TestConnection connection = new TestConnection();
        connection.enqueue(3, new byte[] {(byte) 0xff, 2, 3});
        connection.enqueue(1, new byte[] {4});
        connection.enqueue(-1, new byte[0]);
        try (InputStream stream = new AsyncConnectionInputStream(connection)) {
            assertEquals(0, stream.available());
            assertEquals(255, stream.read());
            assertEquals(2, stream.available());
            byte[] target = new byte[] {9, 9, 9, 9, 9};
            assertEquals(2, stream.read(target, 2, 3));
            assertArrayEquals(new byte[] {9, 9, 2, 3, 9}, target);
            assertEquals(1, connection.reads.get());
            assertEquals(4, stream.read());
            assertEquals(-1, stream.read());
            assertEquals(-1, stream.read());
            assertEquals(3, connection.reads.get());
            assertEquals(3, connection.recycled.get());
        }
    }

    @Test
    void retriesZeroReadsAndUsesPlaintextSizeInsteadOfNetworkCount() throws Exception {
        TestConnection connection = new TestConnection();
        connection.enqueue(0, new byte[0]);
        connection.enqueue(20, new byte[0]);
        connection.enqueue(80, new byte[] {5, 6});
        connection.enqueue(-1, new byte[0]);
        try (InputStream stream = new AsyncConnectionInputStream(connection)) {
            assertArrayEquals(new byte[] {5, 6}, stream.readAllBytes());
            assertEquals(4, connection.recycled.get());
        }
    }

    @Test
    void tlsReadRetriesEmptyPlaintextAndRecyclesNetworkAndPlaintextBuffers() throws Exception {
        AtomicInteger unwraps = new AtomicInteger();
        TestConnection connection = new TestConnection() {
            @Override
            protected ByteBuffer sslUnwrap(boolean handshake, ByteBuffer netBuffer) {
                return unwraps.getAndIncrement() == 0
                        ? ByteBuffer.allocate(8)
                        : ByteBuffer.allocate(8).put(new byte[] {10, 11});
            }
        };
        connection.sslEngine = SSLContext.getDefault().createSSLEngine();
        connection.enqueue(40, new byte[] {1});
        connection.enqueue(80, new byte[] {2});
        connection.enqueue(-1, new byte[0]);
        try (InputStream stream = new AsyncConnectionInputStream(connection, 2000)) {
            assertArrayEquals(new byte[] {10, 11}, stream.readAllBytes());
            assertEquals(2, unwraps.get());
            assertEquals(5, connection.recycled.get());
        }
    }

    @Test
    void tlsCloseIsEOFAndDoesNotRecycleTheNetworkBufferTwice() throws Exception {
        TestConnection connection = new TestConnection() {
            @Override
            protected ByteBuffer sslUnwrap(boolean handshake, ByteBuffer netBuffer) {
                offerReadBuffer(netBuffer);
                return null;
            }
        };
        connection.sslEngine = SSLContext.getDefault().createSSLEngine();
        connection.enqueue(30, new byte[] {1});
        try (InputStream stream = new AsyncConnectionInputStream(connection, 2000)) {
            assertEquals(-1, stream.read());
            assertEquals(-1, stream.read());
            assertEquals(1, connection.recycled.get());
            assertEquals(1, connection.reads.get());
        }
    }

    @Test
    void preservesReadFailureAndReturnsFailedBuffer() throws Exception {
        TestConnection connection = new TestConnection();
        IOException failure = new IOException("broken connection");
        connection.actions.add(handler -> handler.failed(failure, ByteBuffer.allocate(8)));
        try (InputStream stream = new AsyncConnectionInputStream(connection)) {
            assertSame(failure, assertThrows(IOException.class, stream::read));
            assertSame(failure, assertThrows(IOException.class, stream::read));
            assertEquals(1, connection.recycled.get());
            assertEquals(1, connection.reads.get());
        }
    }

    @Test
    void deliversExceptionsThrownInsideTheIOReadTask() throws Exception {
        TestConnection connection = new TestConnection();
        IllegalStateException failure = new IllegalStateException("cannot start read");
        connection.actions.add(handler -> {
            throw failure;
        });
        try (InputStream stream = new AsyncConnectionInputStream(connection, 2000)) {
            assertSame(failure, assertThrows(IOException.class, stream::read).getCause());
        }
    }

    @Test
    void closeWakesPendingReaderAndLateCallbackReturnsItsBuffer() throws Exception {
        TestConnection connection = new TestConnection();
        AsyncConnectionInputStream stream = new AsyncConnectionInputStream(connection);
        CompletableFuture<IOException> result = readFailureAsync(stream);
        assertTrue(connection.started.await(2, TimeUnit.SECONDS));
        stream.close();
        assertTrue(result.get(2, TimeUnit.SECONDS).getMessage().contains("closed"));
        connection.completeLate(12, new byte[] {7});
        assertEquals(1, connection.recycled.get());
        stream.close();
        assertEquals(1, connection.closes.get());
        assertThrows(IOException.class, stream::available);
        assertThrows(IOException.class, stream::read);
    }

    @Test
    void timeoutClosesConnectionAndLateCallbackReturnsItsBuffer() throws Exception {
        TestConnection connection = new TestConnection();
        try (InputStream stream = new AsyncConnectionInputStream(connection, 100)) {
            assertThrows(SocketTimeoutException.class, stream::read);
            assertEquals(1, connection.closes.get());
            connection.completeLate(3, new byte[] {1, 2, 3});
            assertEquals(1, connection.recycled.get());
        }
    }

    @Test
    void interruptionClosesConnectionAndPreservesInterruptFlag() throws Exception {
        TestConnection connection = new TestConnection();
        try (InputStream stream = new AsyncConnectionInputStream(connection)) {
            CompletableFuture<Boolean> result = new CompletableFuture<>();
            Thread reader = Thread.ofVirtual().start(() -> {
                try {
                    stream.read();
                    result.completeExceptionally(new AssertionError("Expected interrupted read"));
                } catch (InterruptedIOException e) {
                    result.complete(Thread.currentThread().isInterrupted());
                } catch (Throwable e) {
                    result.completeExceptionally(e);
                }
            });
            assertTrue(connection.started.await(2, TimeUnit.SECONDS));
            reader.interrupt();
            assertTrue(result.get(2, TimeUnit.SECONDS));
            assertEquals(1, connection.closes.get());
            connection.completeLate(1, new byte[] {9});
            assertEquals(1, connection.recycled.get());
        }
    }

    @Test
    void noticesExternalConnectionCloseWithoutCallback() throws Exception {
        TestConnection connection = new TestConnection();
        try (InputStream stream = new AsyncConnectionInputStream(connection)) {
            CompletableFuture<IOException> result = readFailureAsync(stream);
            assertTrue(connection.started.await(2, TimeUnit.SECONDS));
            connection.close();
            assertTrue(result.get(2, TimeUnit.SECONDS).getMessage().contains("closed"));
        }
    }

    @Test
    void rejectsBlockingReadOnIOThread() throws Exception {
        TestConnection connection = new TestConnection();
        try (InputStream stream = new AsyncConnectionInputStream(connection)) {
            CompletableFuture<IOException> result = new CompletableFuture<>();
            connection.executeRead(() -> {
                try {
                    stream.read();
                    result.completeExceptionally(new AssertionError("Expected IO thread rejection"));
                } catch (IOException e) {
                    result.complete(e);
                }
            });
            assertTrue(result.get(2, TimeUnit.SECONDS).getMessage().contains("IO threads"));
            assertEquals(0, connection.reads.get());
        }
    }

    @Test
    void validatesArgumentsAndDoesNotReadForZeroLength() throws Exception {
        TestConnection connection = new TestConnection();
        assertThrows(NullPointerException.class, () -> new AsyncConnectionInputStream(null));
        assertThrows(IllegalArgumentException.class, () -> new AsyncConnectionInputStream(connection, -1));
        try (InputStream stream = new AsyncConnectionInputStream(connection)) {
            assertThrows(NullPointerException.class, () -> stream.read(null, 0, 1));
            assertThrows(IndexOutOfBoundsException.class, () -> stream.read(new byte[4], 3, 2));
            assertEquals(0, stream.read(new byte[4], 4, 0));
            assertEquals(0, connection.reads.get());
        }
    }

    @Test
    void readsRealNonBlockingSocketAcrossDelayedWrites() throws Exception {
        try (ServerSocket server = new ServerSocket(0);
                SocketChannel client = SocketChannel.open()) {
            // 使用 SocketChannel 接入同一个监听端口，确保底层是非阻塞 NIO 连接。
            client.connect(new InetSocketAddress("127.0.0.1", server.getLocalPort()));
            try (Socket source = server.accept()) {
                client.configureBlocking(false);
                AsyncIOThread ioThread = group.nextReadIOThread();
                AsyncConnection connection =
                        new AsyncNioTcpConnection(true, group, ioThread, ioThread, client, null, null, null);
                try (InputStream stream = new AsyncConnectionInputStream(connection, 2000)) {
                    source.getOutputStream().write(new byte[] {1, 2});
                    assertEquals(1, stream.read());
                    assertEquals(2, stream.read());
                    CompletableFuture<Integer> next = new CompletableFuture<>();
                    Thread.ofVirtual().start(() -> {
                        try {
                            next.complete(stream.read());
                        } catch (Throwable e) {
                            next.completeExceptionally(e);
                        }
                    });
                    source.getOutputStream().write(3);
                    assertEquals(3, next.get(2, TimeUnit.SECONDS));
                    source.shutdownOutput();
                    assertEquals(-1, stream.read());
                }
            }
        }
    }

    @Test
    void requestMultipartCombinesPrefetchedBodyWithAsyncRemainder() throws Exception {
        TestConnection connection = new TestConnection();
        byte[] body = ("--test\r\nContent-Disposition: form-data; name=\"file\"; filename=\"test.txt\"\r\n"
                        + "Content-Type: text/plain\r\n\r\nhello upload\r\n--test--\r\n")
                .getBytes(StandardCharsets.UTF_8);
        int prefix = 7;
        for (int i = prefix; i < body.length; i += 3) {
            byte[] chunk = java.util.Arrays.copyOfRange(body, i, Math.min(i + 3, body.length));
            connection.enqueue(chunk.length, chunk);
        }
        connection.enqueue(-1, new byte[0]);
        HttpContext.HttpContextConfig config = new HttpContext.HttpContextConfig();
        config.maxHeader = 16384;
        config.maxBody = 65536;
        config.readTimeoutSeconds = 2;
        TestRequest request = new TestRequest(new HttpContext(config), connection);
        byte[] header = ("POST /upload HTTP/1.1\r\nContent-Type: multipart/form-data; boundary=test\r\n"
                        + "Content-Length: " + body.length + "\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8);
        ByteBuffer initial = ByteBuffer.allocate(header.length + prefix);
        initial.put(header).put(body, 0, prefix).flip();
        assertEquals(0, request.parse(initial));
        assertArrayEquals(
                "hello upload".getBytes(StandardCharsets.UTF_8),
                request.getMultiContext().partsFirstBytes(1024, null, null));
        assertTrue(connection.reads.get() > 1);
        assertEquals(connection.reads.get(), connection.recycled.get());
        connection.close();
    }

    private static CompletableFuture<IOException> readFailureAsync(InputStream stream) {
        CompletableFuture<IOException> result = new CompletableFuture<>();
        Thread.ofVirtual().start(() -> {
            try {
                stream.read();
                result.completeExceptionally(new AssertionError("Expected read failure"));
            } catch (IOException e) {
                result.complete(e);
            }
        });
        return result;
    }

    private static final class TestRequest extends HttpRequest {
        TestRequest(HttpContext context, AsyncConnection connection) {
            super(context);
            this.channel = connection;
        }

        int parse(ByteBuffer buffer) {
            return readHeader(buffer, -1);
        }
    }

    private static class TestConnection extends AsyncConnection {
        final ConcurrentLinkedQueue<Consumer<CompletionHandler<Integer, ByteBuffer>>> actions =
                new ConcurrentLinkedQueue<>();
        final AtomicInteger reads = new AtomicInteger();
        final AtomicInteger recycled = new AtomicInteger();
        final AtomicInteger closes = new AtomicInteger();
        final CountDownLatch started = new CountDownLatch(1);
        volatile CompletionHandler<Integer, ByteBuffer> lateHandler;
        volatile boolean open = true;

        TestConnection() {
            super(true, group, group.ioReadThreads[0], group.ioReadThreads[0], 8192, null, null);
        }

        void enqueue(int count, byte[] data) {
            actions.add(handler -> handler.completed(
                    count, ByteBuffer.allocate(data.length + 8).put(data)));
        }

        void completeLate(int count, byte[] data) {
            lateHandler.completed(count, ByteBuffer.allocate(data.length + 8).put(data));
        }

        @Override
        protected void readImpl(CompletionHandler<Integer, ByteBuffer> handler) {
            reads.incrementAndGet();
            Consumer<CompletionHandler<Integer, ByteBuffer>> action = actions.poll();
            if (action == null) {
                lateHandler = handler;
                started.countDown();
            } else {
                action.accept(handler);
            }
        }

        @Override
        protected void readRegisterImpl(CompletionHandler<Integer, ByteBuffer> handler) {
            readImpl(handler);
        }

        @Override
        public void offerReadBuffer(ByteBuffer buffer) {
            if (buffer != null) {
                recycled.incrementAndGet();
            }
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
            closes.incrementAndGet();
        }

        @Override
        public boolean isTCP() {
            return true;
        }

        @Override
        public boolean shutdownInput() {
            return false;
        }

        @Override
        public boolean shutdownOutput() {
            return false;
        }

        @Override
        public <T> boolean setOption(SocketOption<T> name, T value) {
            return false;
        }

        @Override
        public Set<SocketOption<?>> supportedOptions() {
            return Set.of();
        }

        @Override
        public SocketAddress getRemoteAddress() {
            return null;
        }

        @Override
        public SocketAddress getLocalAddress() {
            return null;
        }

        @Override
        protected <A> void writeImpl(ByteBuffer src, A attachment, CompletionHandler<Integer, ? super A> handler) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected <A> void writeImpl(
                ByteBuffer[] srcs,
                int offset,
                int length,
                A attachment,
                CompletionHandler<Integer, ? super A> handler) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected <A> void writeImpl(
                ByteBuffer src,
                Consumer<ByteBuffer> consumer,
                A attachment,
                CompletionHandler<Integer, ? super A> handler) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected <A> void writeImpl(
                ByteBuffer[] srcs,
                int offset,
                int length,
                Consumer<ByteBuffer> consumer,
                A attachment,
                CompletionHandler<Integer, ? super A> handler) {
            throw new UnsupportedOperationException();
        }
    }
}
