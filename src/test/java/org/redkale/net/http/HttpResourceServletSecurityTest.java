package org.redkale.net.http;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HttpResourceServletSecurityTest {
    @TempDir
    Path home;

    private static class Servlet extends HttpResourceServlet {
        boolean accessible(String uri) {
            return createFileEntry(uri) != null;
        }

        void index(String index) {
            this.indexHtml = index;
        }
    }

    private Servlet servlet() throws IOException {
        Path root = Files.createDirectory(home.resolve("root"));
        Files.writeString(root.resolve("index.html"), "index");
        Files.createDirectory(root.resolve("sub"));
        Files.writeString(root.resolve("sub/index.html"), "sub");
        Files.writeString(home.resolve("secret.txt"), "secret");
        Servlet servlet = new Servlet();
        servlet.setRoot(root.toFile());
        return servlet;
    }

    @Test
    void traversalAndInvalidPathsAreRejected() throws Exception {
        Servlet servlet = servlet();
        for (String uri : new String[] {
            "/../secret.txt", "/..\\secret.txt", "/sub/../../secret.txt", "/sub\\..\\..\\secret.txt", "/bad\0file"
        }) {
            assertFalse(servlet.accessible(uri), uri);
        }
    }

    @Test
    void normalResourcesAndIndexesRemainAccessible() throws Exception {
        Servlet servlet = servlet();
        assertTrue(servlet.accessible("/index.html"));
        assertTrue(servlet.accessible("/"));
        assertTrue(servlet.accessible("/sub/"));
        assertTrue(servlet.accessible("/sub/../index.html"));
        assertFalse(servlet.accessible("/missing"));
    }

    @Test
    void indexCannotEscapeRoot() throws Exception {
        Servlet servlet = servlet();
        servlet.index("../secret.txt");
        assertFalse(servlet.accessible("/"));
    }

    @Test
    void symbolicLinkCannotEscapeRoot() throws Exception {
        Servlet servlet = servlet();
        try {
            Files.createSymbolicLink(home.resolve("root/link.txt"), home.resolve("secret.txt"));
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            assumeTrue(false, "Symbolic links unavailable: " + e.getMessage());
        }
        assertFalse(servlet.accessible("/link.txt"));
    }
}
