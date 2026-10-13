package org.redkale.net.http;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.atomic.LongAdder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MultiPartLimitTest {
    @TempDir
    Path home;

    private MultiPart part(int length) {
        return new MultiPart(
                "upload",
                "file",
                "application/octet-stream",
                new LongAdder(),
                new ByteArrayInputStream(new byte[length]));
    }

    @Test
    void oversizedUploadReturnsFalseWithoutExceedingLimit() throws Exception {
        for (int length : new int[] {10, 4097, 8193}) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            assertFalse(part(length).save(length - 1, out));
            assertTrue(out.size() <= length - 1);
            assertNull(part(length).getContentBytes(length - 1));
        }
    }

    @Test
    void exactLimitAndEmptyUploadsAreAccepted() throws Exception {
        assertEquals(4096, part(4096).getContentBytes(4096).length);
        assertEquals(0, part(0).getContentBytes(0).length);
        assertNull(part(1).getContentBytes(0));
    }

    @Test
    void failedUploadClosesFileOutput() throws Exception {
        InputStream in = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("read failed");
            }
        };
        MultiPart part = new MultiPart("upload", "file", "application/octet-stream", new LongAdder(), in);
        Path file = home.resolve("upload.bin");
        assertThrows(IOException.class, () -> part.save(16, file.toFile()));
        Files.delete(file);
        assertFalse(Files.exists(file));
    }
}
