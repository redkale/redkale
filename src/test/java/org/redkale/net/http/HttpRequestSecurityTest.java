package org.redkale.net.http;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.redkale.util.RedkaleException;

class HttpRequestSecurityTest {

    private static class Request extends HttpRequest {
        Request(int maxBody, boolean lazyHeader) {
            super(context(maxBody, lazyHeader));
        }

        static HttpContext context(int maxBody, boolean lazyHeader) {
            var config = new HttpContext.HttpContextConfig();
            config.maxHeader = 16384;
            config.maxBody = maxBody;
            config.lazyHeader = lazyHeader;
            return new HttpContext(config);
        }

        int feed(ByteBuffer buffer) {
            return readHeader(buffer, -1);
        }

        int feed(String text) {
            return feed(ByteBuffer.wrap(text.getBytes(StandardCharsets.ISO_8859_1)));
        }
    }

    @Test
    void fragmentedBodyPreservesNextRequest() {
        for (boolean lazy : new boolean[] {false, true}) {
            Request request = new Request(128, lazy);
            assertEquals(3, request.feed("POST / HTTP/1.1\r\nContent-Length: 5\r\n\r\nab"));
            assertEquals(2, request.feed("c"));
            String next = "GET /next HTTP/1.1\r\n\r\n";
            ByteBuffer buffer = ByteBuffer.wrap(("de" + next).getBytes(StandardCharsets.US_ASCII));
            assertEquals(0, request.feed(buffer));
            assertEquals("abcde", request.getBodyUTF8());
            assertEquals(next, StandardCharsets.US_ASCII.decode(buffer).toString());
        }
    }

    @Test
    void lazyGetHeadersStillFrameTheBody() {
        Request request = new Request(128, true);
        ByteBuffer buffer = ByteBuffer.wrap("GET / HTTP/1.1\r\nContent-Length: 3\r\n\r\nabcNEXT".getBytes());
        assertEquals(0, request.feed(buffer));
        assertEquals("abc", request.getBodyUTF8());
        assertEquals("NEXT", StandardCharsets.US_ASCII.decode(buffer).toString());
    }

    @Test
    void contentLengthIsDecimalAndHeadersAreCaseInsensitive() {
        for (boolean lazy : new boolean[] {false, true}) {
            Request request = new Request(128, lazy);
            assertEquals(0, request.feed("GET / HTTP/1.1\r\nCONTENT-LENGTH: 010\r\n\r\nabcdefghij"));
            assertEquals(10, request.getContentLength());
            assertEquals("abcdefghij", request.getBodyUTF8());
        }
        assertEquals(Long.MAX_VALUE, HttpRequest.parseContentLength(Long.toString(Long.MAX_VALUE)));
    }

    @Test
    void invalidContentLengthsAreRejected() {
        for (String value : new String[] {"", "-1", "+1", "0x10", "1, 2", "1 0", "9223372036854775808"}) {
            assertThrows(RedkaleException.class, () -> HttpRequest.parseContentLength(value), value);
        }
    }

    @Test
    void conflictingFramingIsRejected() {
        for (boolean lazy : new boolean[] {false, true}) {
            for (String headers : new String[] {
                "Content-Length: 1\r\nContent-Length: 2\r\n",
                "Content-Length: 1\r\nTransfer-Encoding: chunked\r\n",
                "Transfer-Encoding: chunked\r\nContent-Length: 1\r\n",
                "Transfer-Encoding: identity\r\n",
                "Transfer-Encoding: chunked\r\nTransfer-Encoding: chunked\r\n"
            }) {
                Request request = new Request(128, lazy);
                try {
                    assertEquals(-1, request.feed("GET / HTTP/1.1\r\n" + headers + "\r\n"), headers);
                } catch (RedkaleException expected) {
                    // Lazy header parsing reports malformed headers as an exception.
                }
            }
        }
    }

    @Test
    void identicalDuplicateContentLengthsAreAccepted() {
        Request request = new Request(128, false);
        assertEquals(0, request.feed("POST / HTTP/1.1\r\nContent-Length: 3\r\nContent-Length: 03\r\n\r\nabc"));
        assertEquals("abc", request.getBodyUTF8());
    }

    @Test
    void chunkedBodyLimitIncludesAllChunks() {
        Request oversized = new Request(8, false);
        assertThrows(
                RedkaleException.class,
                () -> oversized.feed(
                        "POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n9\r\n123456789\r\n0\r\n\r\n"));
        Request cumulative = new Request(8, false);
        assertEquals(1, cumulative.feed("POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nabcde\r\n"));
        assertThrows(RedkaleException.class, () -> cumulative.feed("4\r\nfghi\r\n0\r\n\r\n"));
        Request exact = new Request(8, false);
        assertEquals(
                0,
                exact.feed("POST / HTTP/1.1\r\nTRANSFER-ENCODING: Chunked\r\n\r\n4\r\nabcd\r\n4\r\nefgh\r\n0\r\n\r\n"));
        assertEquals("abcdefgh", exact.getBodyUTF8());
    }

    @Test
    void chunkedParsingHandlesEveryBodySplit() {
        String chunks = "3;ext=yes\r\nabc\r\n2\r\nde\r\n0\r\n\r\n";
        for (int split = 1; split < chunks.length(); split++) {
            Request request = new Request(5, false);
            assertEquals(1, request.feed("POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n"));
            assertEquals(1, request.feed(chunks.substring(0, split)), "split=" + split);
            assertEquals(0, request.feed(chunks.substring(split) + "NEXT"), "split=" + split);
            assertEquals("abcde", request.getBodyUTF8());
        }
    }

    @Test
    void excessiveAndInvalidChunkHeadersAreRejected() {
        for (String chunk : new String[] {"80000000\r\n", "100000000\r\n", "\r\n", "1\n", "1;" + "x".repeat(2050)}) {
            Request request = new Request(128, false);
            assertThrows(
                    RedkaleException.class,
                    () -> request.feed("POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n" + chunk));
        }
        Request fragmented = new Request(128, false);
        assertEquals(1, fragmented.feed("POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n1;" + "x".repeat(1000)));
        assertThrows(RedkaleException.class, () -> fragmented.feed("x".repeat(1050)));
    }

    @Test
    void manySmallChunksDoNotOverflowTheStack() {
        Request request = new Request(10000, false);
        assertEquals(
                0,
                request.feed("POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n" + "1\r\na\r\n".repeat(10000)
                        + "0\r\n\r\n"));
        assertEquals(10000, request.getBodyUTF8().length());
    }

    @Test
    void decompressionRejectsOversizedOutputAndCannotBeBypassedByRetry() throws Exception {
        for (String encoding : new String[] {"gzip", "deflate"}) {
            Request request = compressedRequest(encoding, 256, new byte[65536]);
            assertThrows(RedkaleException.class, () -> request.getParameter("test"));
            assertThrows(RedkaleException.class, () -> request.getParameter("test"));
        }
    }

    @Test
    void decompressionAcceptsExactLimit() throws Exception {
        byte[] data = new byte[256];
        Arrays.fill(data, (byte) 'a');
        for (String encoding : new String[] {"gzip", "deflate"}) {
            Request request = compressedRequest(encoding, 256, data);
            request.getParameter("test");
            assertEquals("a".repeat(256), request.getBodyUTF8());
        }
    }

    private Request compressedRequest(String encoding, int maxBody, byte[] data) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (var stream = encoding.equals("gzip") ? new GZIPOutputStream(bytes) : new DeflaterOutputStream(bytes)) {
            stream.write(data);
        }
        byte[] compressed = bytes.toByteArray();
        byte[] header = ("POST / HTTP/1.1\r\nContent-Encoding: " + encoding + "\r\nContent-Length: " + compressed.length
                        + "\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII);
        ByteBuffer buffer = ByteBuffer.allocate(header.length + compressed.length);
        buffer.put(header).put(compressed).flip();
        Request request = new Request(maxBody, false);
        assertEquals(0, request.feed(buffer));
        return request;
    }
}
