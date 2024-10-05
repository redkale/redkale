/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.test.httpparser;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.*;
import org.redkale.net.http.HttpContext;
import org.redkale.net.http.HttpRequest;

/**
 *
 * @author zhangjx
 */
public class HttpRequestTest {

    private static final String REQ_TEXT =
            "GET /test/getinfo  HTTP/1.1\r\n" + "Connection: Keep-Alive\r\n" + "Content-Length: 0\r\n" + "\r\n";

    public static void main(String[] args) throws Throwable {
        HttpRequestTest test = new HttpRequestTest();
        test.run1();
        test.run2();
        test.run3();
        test.run4();
    }

    @Test
    public void run1() throws Exception {
        HttpContext.HttpContextConfig httpConfig = new HttpContext.HttpContextConfig();
        httpConfig.maxHeader = 16 * 1024;
        httpConfig.maxBody = 64 * 1024;
        HttpContext context = new HttpContext(httpConfig);
        HttpRequestX req = new HttpRequestX(context);
        Assertions.assertEquals(0, req.readHeader(ByteBuffer.wrap(REQ_TEXT.getBytes()), -1));

        req = new HttpRequestX(context);
        int sublen = "GET /test/getinfo  HTTP/1.1\r\n".length() + 3;
        String text1 = REQ_TEXT.substring(0, sublen);
        int r1 = req.readHeader(ByteBuffer.wrap(text1.getBytes()), -1);
        Assertions.assertEquals(1, r1);
        Assertions.assertEquals(3, req.headerHalfLen());

        text1 = REQ_TEXT.substring(sublen, sublen + 7);
        int r2 = req.readHeader(ByteBuffer.wrap(text1.getBytes()), -1);
        Assertions.assertEquals(1, r2);
        Assertions.assertEquals(7, req.headerHalfLen());

        text1 = REQ_TEXT.substring(sublen + 7);
        int r3 = req.readHeader(ByteBuffer.wrap(text1.getBytes()), -1);
        Assertions.assertEquals(0, r3);
    }

    @Test
    public void run2() throws Exception {
        HttpContext.HttpContextConfig httpConfig = new HttpContext.HttpContextConfig();
        httpConfig.lazyHeader = true;
        httpConfig.maxHeader = 16 * 1024;
        httpConfig.maxBody = 64 * 1024;
        HttpContext context = new HttpContext(httpConfig);
        HttpRequestX req = new HttpRequestX(context);
        Assertions.assertEquals(0, req.readHeader(ByteBuffer.wrap(REQ_TEXT.getBytes()), -1));

        req = new HttpRequestX(context);
        int sublen = "GET /test/getinfo  HTTP/1.1\r\n".length() + 3;
        String text1 = REQ_TEXT.substring(0, sublen);
        int r1 = req.readHeader(ByteBuffer.wrap(text1.getBytes()), -1);
        Assertions.assertEquals(1, r1);
        Assertions.assertEquals(3, req.headerHalfLen());

        text1 = REQ_TEXT.substring(sublen, sublen + 7);
        int r2 = req.readHeader(ByteBuffer.wrap(text1.getBytes()), -1);
        Assertions.assertEquals(1, r2);
        Assertions.assertEquals(10, req.headerHalfLen());

        text1 = REQ_TEXT.substring(sublen + 7);
        int r3 = req.readHeader(ByteBuffer.wrap(text1.getBytes()), -1);
        Assertions.assertEquals(0, r3);
    }

    @Test
    public void run3() throws Exception {
        HttpContext.HttpContextConfig httpConfig = new HttpContext.HttpContextConfig();
        httpConfig.lazyHeader = true;
        httpConfig.sameHeader = true;
        httpConfig.maxHeader = 16 * 1024;
        httpConfig.maxBody = 64 * 1024;
        HttpContext context = new HttpContext(httpConfig);
        HttpRequestX req = new HttpRequestX(context);
        Assertions.assertEquals(0, req.readHeader(ByteBuffer.wrap(REQ_TEXT.getBytes()), -1));
        int phLength = req.headerLength();

        req = new HttpRequestX(context);
        int sublen = "GET /test/getinfo  HTTP/1.1\r\n".length() + 3;
        String text1 = REQ_TEXT.substring(0, sublen);
        int r1 = req.readHeader(ByteBuffer.wrap(text1.getBytes()), phLength);
        Assertions.assertEquals(1, r1);
        Assertions.assertEquals(3, req.headerHalfLen());

        text1 = REQ_TEXT.substring(sublen, sublen + 7);
        int r2 = req.readHeader(ByteBuffer.wrap(text1.getBytes()), phLength);
        Assertions.assertEquals(1, r2);
        Assertions.assertEquals(10, req.headerHalfLen());

        text1 = REQ_TEXT.substring(sublen + 7);
        int r3 = req.readHeader(ByteBuffer.wrap(text1.getBytes()), phLength);
        Assertions.assertEquals(0, r3);
    }

    @Test
    public void run4() throws Exception {
        HttpContext.HttpContextConfig httpConfig = new HttpContext.HttpContextConfig();
        httpConfig.lazyHeader = true;
        httpConfig.sameHeader = true;
        httpConfig.maxHeader = 16 * 1024;
        httpConfig.maxBody = 64 * 1024;
        HttpContext context = new HttpContext(httpConfig);
        Method method = HttpContext.class.getDeclaredMethod("addUriPath", String.class);
        method.setAccessible(true);
        method.invoke(context, "/test/sleep200");
        method.invoke(context, "/test/aaa");

        HttpRequestX req = new HttpRequestX(context);
        String text = "GET /test/azzzz  HTTP/1.1\r\nConnection: Keep-Alive\r\nContent-Length: 0\r\n\r\n";
        int r1 = req.readHeader(ByteBuffer.wrap(text.getBytes()), 0);
        Assertions.assertEquals(0, r1);
        Assertions.assertEquals("/test/azzzz", req.getRequestPath());

        req = new HttpRequestX(context);
        text = "GET /test/aaaa  HTTP/1.1\r\nConnection: Keep-Alive\r\nContent-Length: 0\r\n\r\n";
        r1 = req.readHeader(ByteBuffer.wrap(text.getBytes()), 0);
        Assertions.assertEquals(0, r1);
        Assertions.assertEquals("/test/aaaa", req.getRequestPath());

        req = new HttpRequestX(context);
        text = "GET /test/sleep200  HTTP/1.1\r\nConnection: Keep-Alive\r\nContent-Length: 0\r\n\r\n";
        r1 = req.readHeader(ByteBuffer.wrap(text.getBytes()), 0);
        Assertions.assertEquals(0, r1);
        Assertions.assertEquals("/test/sleep200", req.getRequestPath());

        req = new HttpRequestX(context);
        text = "GET /test/sleep201  HTTP/1.1\r\nConnection: Keep-Alive\r\nContent-Length: 0\r\n\r\n";
        r1 = req.readHeader(ByteBuffer.wrap(text.getBytes()), 0);
        Assertions.assertEquals(0, r1);
        Assertions.assertEquals("/test/sleep201", req.getRequestPath());

        req = new HttpRequestX(context);
        text = "GET /test/sleep20?  HTTP/1.1\r\nConnection: Keep-Alive\r\nContent-Length: 0\r\n\r\n";
        r1 = req.readHeader(ByteBuffer.wrap(text.getBytes()), 0);
        Assertions.assertEquals(0, r1);
        Assertions.assertEquals("/test/sleep20", req.getRequestPath());

        req = new HttpRequestX(context);
        text = "GET /test/sleep20?n=haha  HTTP/1.1\r\nConnection: Keep-Alive\r\nContent-Length: 0\r\n\r\n";
        r1 = req.readHeader(ByteBuffer.wrap(text.getBytes()), 0);
        Assertions.assertEquals(0, r1);
        Assertions.assertEquals("/test/sleep20", req.getRequestPath());
        Assertions.assertEquals("haha", req.getParameter("n"));
    }

    public static class HttpRequestX extends HttpRequest {

        public HttpRequestX(HttpContext context) {
            super(context);
        }

        @Override
        public int readHeader(final ByteBuffer buf, final int pipelineHeaderLength) {
            return super.readHeader(buf, pipelineHeaderLength);
        }

        public int headerLength() {
            return this.headerLength;
        }

        public int headerHalfLen() {
            return this.headerHalfLen;
        }
    }
}
