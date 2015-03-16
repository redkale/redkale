/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.http;

import com.wentch.redkale.net.*;
import com.wentch.redkale.util.*;
import com.wentch.redkale.util.AnyValue.DefaultAnyValue;
import com.wentch.redkale.util.AnyValue.Entry;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/**
 *
 * @author zhangjx
 */
public final class HttpResponse extends Response<HttpRequest> {

    private static final ByteBuffer buffer304 = ByteBuffer.wrap("HTTP/1.1 304 Not Modified\r\n\r\n".getBytes()).asReadOnlyBuffer();

    private static final ByteBuffer buffer404 = ByteBuffer.wrap("HTTP/1.1 404 Not Found\r\nContent-Length:0\r\n\r\n".getBytes()).asReadOnlyBuffer();

    protected static final byte[] LINE = new byte[]{'\r', '\n'};

    private static final Set<OpenOption> options = new HashSet<>();

    private static final DateFormat GMT_DATE_FORMAT = new SimpleDateFormat("EEE, dd-MMM-yyyy HH:mm:ss z", Locale.ENGLISH);

    private static final Map<Integer, String> httpCodes = new HashMap<>();

    static {
        options.add(StandardOpenOption.READ);
        GMT_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));

        httpCodes.put(100, "Continue");
        httpCodes.put(101, "Switching Protocols");

        httpCodes.put(200, "OK");
        httpCodes.put(201, "Created");
        httpCodes.put(202, "Accepted");
        httpCodes.put(203, "Non-Authoritative Information");
        httpCodes.put(204, "No Content");
        httpCodes.put(205, "Reset Content");
        httpCodes.put(206, "Partial Content");

        httpCodes.put(300, "Multiple Choices");
        httpCodes.put(301, "Moved Permanently");
        httpCodes.put(302, "Found");
        httpCodes.put(303, "See Other");
        httpCodes.put(304, "Not Modified");
        httpCodes.put(305, "Use Proxy");
        httpCodes.put(307, "Temporary Redirect");

        httpCodes.put(400, "Bad Request");
        httpCodes.put(401, "Unauthorized");
        httpCodes.put(402, "Payment Required");
        httpCodes.put(403, "Forbidden");
        httpCodes.put(404, "Not Found");
        httpCodes.put(405, "Method Not Allowed");
        httpCodes.put(406, "Not Acceptable");
        httpCodes.put(407, "Proxy Authentication Required");
        httpCodes.put(408, "Request Timeout");
        httpCodes.put(409, "Conflict");
        httpCodes.put(410, "Gone");
        httpCodes.put(411, "Length Required");
        httpCodes.put(412, "Precondition Failed");
        httpCodes.put(413, "Request Entity Too Large");
        httpCodes.put(414, "Request URI Too Long");
        httpCodes.put(415, "Unsupported Media Type");
        httpCodes.put(416, "Requested Range Not Satisfiable");
        httpCodes.put(417, "Expectation Failed");

        httpCodes.put(500, "Internal Server Error");
        httpCodes.put(501, "Not Implemented");
        httpCodes.put(502, "Bad Gateway");
        httpCodes.put(503, "Service Unavailable");
        httpCodes.put(504, "Gateway Timeout");
        httpCodes.put(505, "HTTP Version Not Supported");
    }

    private int status = 200;

    private String contentType = "text/plain; charset=utf-8";

    private long contentLength = -1;

    private HttpCookie[] cookies;

    private boolean headsended = false;

    private final DefaultAnyValue header = new DefaultAnyValue();

    public static ObjectPool<Response> createPool(AtomicLong creatCounter, AtomicLong cycleCounter, int max, Creator<Response> creator) {
        return new ObjectPool<>(creatCounter, cycleCounter, max, creator, (x) -> ((HttpResponse) x).recycle());
    }

    protected HttpResponse(HttpContext context, HttpRequest request) {
        super(context, request);
    }

    @Override
    protected AsyncConnection removeChannel() {
        return super.removeChannel();
    }

    @Override
    protected boolean recycle() {
        this.status = 200;
        this.contentLength = -1;
        this.contentType = null;
        this.cookies = null;
        this.headsended = false;
        this.header.clear();
        return super.recycle();
    }

    protected String getHttpCode(int status) {
        return httpCodes.get(status);
    }

    protected String getHttpCode(int status, String defValue) {
        String v = httpCodes.get(status);
        return v == null ? defValue : v;
    }

    @Override
    public HttpContext getContext() {
        return (HttpContext) context;
    }

    public void addCookie(HttpCookie... cookies) {
        if (this.cookies == null) {
            this.cookies = cookies;
        } else {
            HttpCookie[] news = new HttpCookie[this.cookies.length + cookies.length];
            System.arraycopy(this.cookies, 0, news, 0, this.cookies.length);
            System.arraycopy(cookies, 0, news, this.cookies.length, cookies.length);
            this.cookies = news;
        }
    }

    public void finishJson(Object obj) {
        this.contentType = "text/plain; charset=utf-8";
        finishString(request.convert.convertTo(obj));
    }

    public void finishJson(Type type, Object obj) {
        this.contentType = "text/plain; charset=utf-8";
        finishString(request.convert.convertTo(type, obj));
    }

    public void finishJson(Object... objs) {
        this.contentType = "text/plain; charset=utf-8";
        finishString(request.convert.convertTo(objs));
    }

    public void finishString(String obj) {
        if (obj == null) obj = "null";
        if (context.getCharset() == null) {
            final char[] chars = Utility.charArray(obj);
            this.contentLength = Utility.encodeUTF8Length(chars);
            final ByteBuffer headbuf = createHeader();
            ByteBuffer buf2 = Utility.encodeUTF8(headbuf, (int) this.contentLength, chars);
            headbuf.flip();
            if (buf2 == null) {
                super.send(headbuf, headbuf, finishHandler);
            } else {
                super.send(headbuf, buf2, new AsyncWriteHandler<>(this.context, headbuf, this.channel, buf2, buf2, finishHandler));
            }
        } else {
            ByteBuffer buffer = context.getCharset().encode(obj);
            this.contentLength = buffer.remaining();
            send(buffer, buffer, finishHandler);
        }
    }

    public void finish(int status, String message) {
        this.status = status;
        if (status != 200) super.refuseAlive();
        if (message == null || message.isEmpty()) {
            ByteBuffer headbuf = createHeader();
            headbuf.flip();
            super.send(headbuf, headbuf, finishHandler);
        } else {
            finishString(message);
        }
    }

    public void finish304() {
        super.finish(buffer304.duplicate());
    }

    public void finish404() {
        super.finish(buffer404.duplicate());
    }

    @Override
    public <A> void send(ByteBuffer buffer, A attachment, CompletionHandler<Integer, A> handler) {
        if (!this.headsended) {
            ByteBuffer headbuf = createHeader();
            headbuf.flip();
            if (buffer == null) {
                super.send(headbuf, attachment, handler);
            } else {
                super.send(headbuf, attachment, new AsyncWriteHandler<>(this.context, headbuf, this.channel, buffer, attachment, handler));
            }
        } else {
            super.send(buffer, attachment, handler);
        }
    }

    public <A> void finish(File file) throws IOException {
        finishFile(file, null);
    }

    protected <A> void finishFile(final File file, final ByteBuffer fileBody) throws IOException {
        if (file == null || !file.isFile() || !file.canRead()) {
            finish404();
            return;
        }
        final long length = file.length();
        final String match = request.getHeader("If-None-Match");
        if (match != null && (file.lastModified() + "-" + length).equals(match)) {
            finish304();
            return;
        }
        this.contentLength = file.length();
        if (this.contentType == null) this.contentType = MimeType.getByFilename(file.getName());
        if (this.contentType == null) this.contentType = "application/octet-stream";
        String range = request.getHeader("Range");
        if (range != null && (!range.startsWith("bytes=") || range.indexOf(',') >= 0)) range = null;
        long start = -1;
        long len = -1;
        if (range != null) {
            range = range.substring("bytes=".length());
            int pos = range.indexOf('-');
            start = pos == 0 ? 0 : Integer.parseInt(range.substring(0, pos));
            long end = (pos == range.length() - 1) ? -1 : Long.parseLong(range.substring(pos + 1));
            long clen = end > 0 ? (end - start + 1) : (file.length() - start);
            this.status = 206;
            addHeader("Accept-Ranges", "bytes");
            addHeader("Content-Range", "bytes " + start + "-" + (end > 0 ? end : length - 1) + "/" + length);
            this.contentLength = clen;
            len = end > 0 ? clen : end;
        }
        ByteBuffer buffer = createHeader();
        buffer.flip();
        if (fileBody == null) {
            HttpResponse.this.finishFile(buffer, file, start, len);
        } else {
            final ByteBuffer body = fileBody.duplicate().asReadOnlyBuffer();
            if (start >= 0) {
                body.position((int) start);
                if (len > 0) body.limit((int) (body.position() + len));
            }
            send(buffer, buffer, new CompletionHandler<Integer, ByteBuffer>() {

                @Override
                public void completed(Integer result, ByteBuffer attachment) {
                    context.offerBuffer(attachment);
                    finish(body);
                }

                @Override
                public void failed(Throwable exc, ByteBuffer attachment) {
                    if (attachment.limit() != attachment.capacity()) {
                        context.offerBuffer(attachment);
                    }
                    finish(true);
                }
            });
        }
    }

    protected <A> void finishFile(ByteBuffer buffer, File file) throws IOException {
        finishFile(buffer, file, -1L, -1L);
    }

    protected <A> void finishFile(ByteBuffer buffer, File file, long offset, long length) throws IOException {
        send(buffer, buffer, new TransferFileHandler(AsynchronousFileChannel.open(file.toPath(), options, ((HttpContext) context).getExecutor()), offset, length));
    }

    private ByteBuffer createHeader() {
        this.headsended = true;
        ByteBuffer buffer = this.context.pollBuffer();
        buffer.put(("HTTP/1.1 " + this.status + " " + (this.status == 200 ? "OK" : httpCodes.get(this.status)) + "\r\n").getBytes());

        buffer.put(("Content-Type: " + (this.contentType == null ? "text/plain; charset=utf-8" : this.contentType) + "\r\n").getBytes());

        if (this.contentLength > 0) {
            buffer.put(("Content-Length: " + this.contentLength + "\r\n").getBytes());
        }
        if (!this.request.isKeepAlive()) {
            buffer.put("Connection: close\r\n".getBytes());
        }
        for (Entry<String> en : this.header.getStringEntrys()) {
            buffer.put((en.name + ": " + en.getValue() + "\r\n").getBytes());
        }
        if (request.newsessionid != null) {
            if (request.newsessionid.isEmpty()) {
                buffer.put(("Set-Cookie: " + HttpRequest.SESSIONID_NAME + "=; path=/; Max-Age=0; HttpOnly\r\n").getBytes());
            } else {
                buffer.put(("Set-Cookie: " + HttpRequest.SESSIONID_NAME + "=" + request.newsessionid + "; path=/; HttpOnly\r\n").getBytes());
            }
        }
        if (this.cookies != null) {
            for (HttpCookie cookie : this.cookies) {
                if (cookie == null) continue;
                buffer.put(("Set-Cookie: " + genString(cookie) + "\r\n").getBytes());
            }
        }
        buffer.put(LINE);
        return buffer;
    }

    private CharSequence genString(HttpCookie cookie) {
        StringBuilder sb = new StringBuilder();
        sb.append(cookie.getName()).append("=\"").append(cookie.getValue()).append('"').append("; Version=1");
        if (cookie.getPath() != null) sb.append("; Path=").append(cookie.getPath());
        if (cookie.getDomain() != null) sb.append("; Domain=").append(cookie.getDomain());
        if (cookie.getPortlist() != null) sb.append("; Port=").append(cookie.getPortlist());
        if (cookie.getMaxAge() > 0) {
            sb.append("; Max-Age=").append(cookie.getMaxAge());
            synchronized (GMT_DATE_FORMAT) {
                sb.append("; Expires=").append(GMT_DATE_FORMAT.format(new Date(System.currentTimeMillis() + cookie.getMaxAge() * 1000)));
            }
        }
        if (cookie.getSecure()) sb.append("; Secure");
        if (cookie.isHttpOnly()) sb.append("; HttpOnly");
        return sb;
    }

    public void skipHeader() {
        this.headsended = true;
    }

    public void setHeader(String name, Object value) {
        this.header.setValue(name, String.valueOf(value));
    }

    public void addHeader(String name, Object value) {
        this.header.addValue(name, String.valueOf(value));
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public int getStatus() {
        return this.status;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getContentLength() {
        return contentLength;
    }

    public void setContentLength(long contentLength) {
        this.contentLength = contentLength;
    }

    protected final class TransferFileHandler implements CompletionHandler<Integer, ByteBuffer> {

        private final AsynchronousFileChannel filechannel;

        private final long max; //需要读取的字节数， -1表示读到文件结尾

        private long count;//读取文件的字节数

        private long position = 0;

        private boolean next = false;

        private boolean read = true;

        public TransferFileHandler(AsynchronousFileChannel channel) {
            this.filechannel = channel;
            this.max = -1;
        }

        public TransferFileHandler(AsynchronousFileChannel channel, long offset, long len) {
            this.filechannel = channel;
            this.position = offset <= 0 ? 0 : offset;
            this.max = len;
        }

        @Override
        public void completed(Integer result, ByteBuffer attachment) {
            if (result < 0 || (max > 0 && count >= max)) {
                failed(null, attachment);
                return;
            }
            if (read) {
                read = false;
                if (next) {
                    position += result;
                } else {
                    next = true;
                }
                attachment.clear();
                filechannel.read(attachment, position, attachment, this);
            } else {
                read = true;
                if (max > 0) {
                    count += result;
                    if (count > max) {
                        attachment.limit((int) (attachment.position() + max - count));
                    }
                }
                attachment.flip();
                send(attachment, attachment, this);
            }
        }

        @Override
        public void failed(Throwable exc, ByteBuffer attachment) {
            getContext().offerBuffer(attachment);
            finish(true);
            try {
                filechannel.close();
            } catch (IOException e) {
            }
        }

    }
}
