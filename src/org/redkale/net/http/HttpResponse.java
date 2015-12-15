/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import org.redkale.util.AnyValue.DefaultAnyValue;
import org.redkale.util.AnyValue.Entry;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.redkale.net.*;
import org.redkale.util.*;

/**
 *
 * @see http://www.redkale.org
 * @author zhangjx
 * @param <R>
 */
public class HttpResponse<R extends HttpRequest> extends Response<R> {

    /**
     * HttpResponse.finish 方法内调用
     *
     */
    public static interface Interceptor {

        public ByteBuffer[] invoke(final HttpResponse response, final ByteBuffer[] buffers);
    }

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

    private Interceptor interceptor;
    //------------------------------------------------

    private final DefaultAnyValue header = new DefaultAnyValue();

    private final String[][] defaultAddHeaders;

    private final String[][] defaultSetHeaders;

    private final HttpCookie defcookie;

    public static ObjectPool<Response> createPool(AtomicLong creatCounter, AtomicLong cycleCounter, int max, Creator<Response> creator) {
        return new ObjectPool<>(creatCounter, cycleCounter, max, creator, (x) -> ((HttpResponse) x).prepare(), (x) -> ((HttpResponse) x).recycle());
    }

    public HttpResponse(Context context, R request, String[][] defaultAddHeaders, String[][] defaultSetHeaders, HttpCookie defcookie) {
        super(context, request);
        this.defaultAddHeaders = defaultAddHeaders;
        this.defaultSetHeaders = defaultSetHeaders;
        this.defcookie = defcookie;
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
        this.interceptor = null;
        return super.recycle();
    }

    @Override
    protected void init(AsyncConnection channel) {
        super.init(channel);
    }

    protected String getHttpCode(int status) {
        return httpCodes.get(status);
    }

    protected HttpRequest getRequest() {
        return request;
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
        finish(request.getJsonConvert().convertTo(context.getCharset(), context.getBufferSupplier(), obj));
    }

    public void finishJson(Type type, Object obj) {
        this.contentType = "text/plain; charset=utf-8";
        finish(request.getJsonConvert().convertTo(context.getCharset(), context.getBufferSupplier(), type, obj));
    }

    public void finishJson(Object... objs) {
        this.contentType = "text/plain; charset=utf-8";
        finish(request.getJsonConvert().convertTo(context.getCharset(), context.getBufferSupplier(), objs));
    }

    public void finish(String obj) {
        if (obj == null || obj.isEmpty()) {
            final ByteBuffer headbuf = createHeader();
            headbuf.flip();
            super.finish(headbuf);
            return;
        }
        if (context.getCharset() == null) {
            if (interceptor != null) {
                interceptor.invoke(this, new ByteBuffer[]{ByteBuffer.wrap(Utility.encodeUTF8(obj))});
            }
            final char[] chars = Utility.charArray(obj);
            this.contentLength = Utility.encodeUTF8Length(chars);
            final ByteBuffer headbuf = createHeader();
            ByteBuffer buf2 = Utility.encodeUTF8(headbuf, (int) this.contentLength, chars);
            headbuf.flip();
            if (buf2 == null) {
                super.finish(headbuf);
            } else {
                super.finish(headbuf, buf2);
            }
        } else {
            ByteBuffer buffer = context.getCharset().encode(obj);
            if (interceptor != null) {
                ByteBuffer[] bufs = interceptor.invoke(this, new ByteBuffer[]{buffer});
                if (bufs != null) buffer = bufs[0];
            }
            this.contentLength = buffer.remaining();
            final ByteBuffer headbuf = createHeader();
            headbuf.flip();
            super.finish(headbuf, buffer);
        }
    }

    public void finish(int status, String message) {
        this.status = status;
        if (status != 200) super.refuseAlive();
        finish(message);
    }

    public void finish304() {
        super.finish(buffer304.duplicate());
    }

    public void finish404() {
        super.finish(buffer404.duplicate());
    }

    @Override
    public void finish(ByteBuffer buffer) {
        finish(false, buffer);
    }

    @Override
    public void finish(boolean kill, ByteBuffer buffer) {
        if (!this.headsended) {
            this.contentLength = buffer == null ? 0 : buffer.remaining();
            ByteBuffer headbuf = createHeader();
            headbuf.flip();
            if (buffer == null) {
                super.finish(kill, headbuf);
            } else {
                super.finish(kill, new ByteBuffer[]{headbuf, buffer});
            }
        } else {
            super.finish(kill, buffer);
        }
    }

    @Override
    public void finish(ByteBuffer... buffers) {
        finish(false, buffers);
    }

    @Override
    public void finish(boolean kill, ByteBuffer... buffers) {
        if (interceptor != null) {
            ByteBuffer[] bufs = interceptor.invoke(this, buffers);
            if (bufs != null) buffers = bufs;
        }
        if (kill) refuseAlive();
        if (!this.headsended) {
            long len = 0;
            for (ByteBuffer buf : buffers) {
                len += buf.remaining();
            }
            this.contentLength = len;
            ByteBuffer headbuf = createHeader();
            headbuf.flip();
            if (buffers == null) {
                super.finish(kill, headbuf);
            } else {
                ByteBuffer[] newbuffers = new ByteBuffer[buffers.length + 1];
                newbuffers[0] = headbuf;
                System.arraycopy(buffers, 0, newbuffers, 1, buffers.length);
                super.finish(kill, newbuffers);
            }
        } else {
            super.finish(kill, buffers);
        }
    }

    public <A> void sendBody(ByteBuffer buffer, A attachment, CompletionHandler<Integer, A> handler) {
        if (!this.headsended) {
            if (this.contentLength < 0) this.contentLength = buffer == null ? 0 : buffer.remaining();
            ByteBuffer headbuf = createHeader();
            headbuf.flip();
            if (buffer == null) {
                super.send(headbuf, attachment, handler);
            } else {
                super.send(new ByteBuffer[]{headbuf, buffer}, attachment, handler);
            }
        } else {
            super.send(buffer, attachment, handler);
        }
    }

    public <A> void finish(File file) throws IOException {
        finishFile(file, null);
    }

    protected <A> void finishFile(final File file, ByteBuffer fileBody) throws IOException {
        if (file == null || !file.isFile() || !file.canRead()) {
            finish404();
            return;
        }
        if (fileBody != null) fileBody = fileBody.duplicate().asReadOnlyBuffer();
        final long length = file.length();
        final String match = request.getHeader("If-None-Match");
        if (match != null && (file.lastModified() + "-" + length).equals(match)) {
            finish304();
            return;
        }
        this.contentLength = fileBody == null ? file.length() : fileBody.remaining();
        this.contentType = MimeType.getByFilename(file.getName());
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
        this.addHeader("ETag", file.lastModified() + "-" + length);
        ByteBuffer hbuffer = createHeader();
        hbuffer.flip();
        if (fileBody == null) {
            finishFile(hbuffer, file, start, len);
        } else {
            if (start >= 0) {
                fileBody.position((int) start);
                if (len > 0) fileBody.limit((int) (fileBody.position() + len));
            }
            super.finish(hbuffer, fileBody);
        }
    }

    private <A> void finishFile(ByteBuffer hbuffer, File file, long offset, long length) throws IOException {
        this.channel.write(hbuffer, hbuffer, new TransferFileHandler(AsynchronousFileChannel.open(file.toPath(), options, ((HttpContext) context).getExecutor()), offset, length));
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
        if (this.defaultAddHeaders != null) {
            for (String[] headers : this.defaultAddHeaders) {
                if (headers.length > 2) {
                    String v = request.getHeader(headers[2]);
                    if (v != null) this.header.addValue(headers[0], v);
                } else {
                    this.header.addValue(headers[0], headers[1]);
                }
            }
        }
        if (this.defaultSetHeaders != null) {
            for (String[] headers : this.defaultSetHeaders) {
                if (headers.length > 2) {
                    this.header.setValue(headers[0], request.getHeader(headers[2]));
                } else {
                    this.header.setValue(headers[0], headers[1]);
                }
            }
        }
        for (Entry<String> en : this.header.getStringEntrys()) {
            buffer.put((en.name + ": " + en.getValue() + "\r\n").getBytes());
        }
        if (request.newsessionid != null) {
            String domain = defcookie == null ? null : defcookie.getDomain();
            if (domain == null) {
                domain = "";
            } else {
                domain = "Domain=" + domain + "; ";
            }
            String path = defcookie == null ? null : defcookie.getPath();
            if (path == null) path = "/";
            if (request.newsessionid.isEmpty()) {
                buffer.put(("Set-Cookie: " + HttpRequest.SESSIONID_NAME + "=; " + domain + "Path=" + path + "; Max-Age=0; HttpOnly\r\n").getBytes());
            } else {
                buffer.put(("Set-Cookie: " + HttpRequest.SESSIONID_NAME + "=" + request.newsessionid + "; " + domain + "Path=" + path + "; HttpOnly\r\n").getBytes());
            }
        }
        if (this.cookies != null) {
            for (HttpCookie cookie : this.cookies) {
                if (cookie == null) continue;
                if (defcookie != null) {
                    if (defcookie.getDomain() != null && cookie.getDomain() == null) cookie.setDomain(defcookie.getDomain());
                    if (defcookie.getPath() != null && cookie.getPath() == null) cookie.setPath(defcookie.getPath());
                }
                buffer.put(("Set-Cookie: " + genString(cookie) + "\r\n").getBytes());
            }
        }
        buffer.put(LINE);
        return buffer;
    }

    private CharSequence genString(HttpCookie cookie) {
        StringBuilder sb = new StringBuilder();
        sb.append(cookie.getName()).append("=\"").append(cookie.getValue()).append('"').append("; Version=1");
        if (cookie.getDomain() != null) sb.append("; Domain=").append(cookie.getDomain());
        if (cookie.getPath() != null) sb.append("; Path=").append(cookie.getPath());
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

    protected DefaultAnyValue duplicateHeader() {
        return this.header.duplicate();
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

    public Interceptor getInterceptor() {
        return interceptor;
    }

    public void setInterceptor(Interceptor interceptor) {
        this.interceptor = interceptor;
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
                channel.write(attachment, attachment, this);
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
