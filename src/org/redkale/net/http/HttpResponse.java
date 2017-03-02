/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.io.*;
import java.lang.reflect.Type;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.redkale.convert.json.JsonConvert;
import org.redkale.net.*;
import org.redkale.util.AnyValue.DefaultAnyValue;
import org.redkale.util.AnyValue.Entry;
import org.redkale.util.*;

/**
 * Http响应包 与javax.servlet.http.HttpServletResponse 基本类似。  <br>
 * 同时提供发送json的系列接口: public void finishJson(Type type, Object obj)  <br>
 * Redkale提倡http+json的接口风格， 所以主要输出的数据格式为json， 同时提供异步接口。  <br>
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class HttpResponse extends Response<HttpContext, HttpRequest> {

    /**
     * HttpResponse.finish 方法内调用
     * 主要给@HttpCacheable使用
     */
    protected static interface BufferHandler {

        public ByteBuffer[] execute(final HttpResponse response, final ByteBuffer[] buffers);
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

    private BufferHandler bufferHandler;
    //------------------------------------------------

    private final DefaultAnyValue header = new DefaultAnyValue();

    private final String[][] defaultAddHeaders;

    private final String[][] defaultSetHeaders;

    private final HttpCookie defcookie;

    public static ObjectPool<Response> createPool(AtomicLong creatCounter, AtomicLong cycleCounter, int max, Creator<Response> creator) {
        return new ObjectPool<>(creatCounter, cycleCounter, max, creator, (x) -> ((HttpResponse) x).prepare(), (x) -> ((HttpResponse) x).recycle());
    }

    public HttpResponse(HttpContext context, HttpRequest request, String[][] defaultAddHeaders, String[][] defaultSetHeaders, HttpCookie defcookie) {
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
        this.bufferHandler = null;
        return super.recycle();
    }

    @Override
    protected void init(AsyncConnection channel) {
        super.init(channel);
    }

    /**
     * 获取状态码对应的状态描述
     *
     * @param status 状态码
     *
     * @return 状态描述
     */
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

    /**
     * 增加Cookie值
     *
     * @param cookies cookie
     *
     * @return HttpResponse
     */
    public HttpResponse addCookie(HttpCookie... cookies) {
        this.cookies = Utility.append(this.cookies, cookies);
        return this;
    }

    /**
     * 增加Cookie值
     *
     * @param cookies cookie
     *
     * @return HttpResponse
     */
    public HttpResponse addCookie(Collection<HttpCookie> cookies) {
        this.cookies = Utility.append(this.cookies, cookies);
        return this;
    }

    /**
     * 将对象以JSON格式输出
     *
     * @param obj 输出对象
     */
    public void finishJson(final Object obj) {
        this.contentType = "text/plain; charset=utf-8";
        if (this.recycleListener != null) this.output = obj;
        finish(request.getJsonConvert().convertTo(context.getBufferSupplier(), obj));
    }

    /**
     * 将对象以JSON格式输出
     *
     * @param convert 指定的JsonConvert
     * @param obj     输出对象
     */
    public void finishJson(final JsonConvert convert, final Object obj) {
        this.contentType = "text/plain; charset=utf-8";
        if (this.recycleListener != null) this.output = obj;
        finish(convert.convertTo(context.getBufferSupplier(), obj));
    }

    /**
     * 将对象以JSON格式输出
     *
     * @param type 指定的类型
     * @param obj  输出对象
     */
    public void finishJson(final Type type, final Object obj) {
        this.contentType = "text/plain; charset=utf-8";
        this.output = obj;
        finish(request.getJsonConvert().convertTo(context.getBufferSupplier(), type, obj));
    }

    /**
     * 将对象以JSON格式输出
     *
     * @param convert 指定的JsonConvert
     * @param type    指定的类型
     * @param obj     输出对象
     */
    public void finishJson(final JsonConvert convert, final Type type, final Object obj) {
        this.contentType = "text/plain; charset=utf-8";
        if (this.recycleListener != null) this.output = obj;
        finish(convert.convertTo(context.getBufferSupplier(), type, obj));
    }

    /**
     * 将对象以JSON格式输出
     *
     * @param objs 输出对象
     */
    public void finishJson(final Object... objs) {
        this.contentType = "text/plain; charset=utf-8";
        if (this.recycleListener != null) this.output = objs;
        finish(request.getJsonConvert().convertTo(context.getBufferSupplier(), objs));
    }

    /**
     * 将RetResult对象以JSON格式输出
     *
     * @param ret RetResult输出对象
     */
    public void finishJson(final org.redkale.service.RetResult ret) {
        this.contentType = "text/plain; charset=utf-8";
        if (this.recycleListener != null) this.output = ret;
        if (ret != null && !ret.isSuccess()) {
            this.header.addValue("retcode", String.valueOf(ret.getRetcode()));
            this.header.addValue("retinfo", ret.getRetinfo());
        }
        finish(request.getJsonConvert().convertTo(context.getBufferSupplier(), ret));
    }

    /**
     * 将RetResult对象以JSON格式输出
     *
     * @param convert 指定的JsonConvert
     * @param ret     RetResult输出对象
     */
    public void finishJson(final JsonConvert convert, final org.redkale.service.RetResult ret) {
        this.contentType = "text/plain; charset=utf-8";
        if (this.recycleListener != null) this.output = ret;
        if (ret != null && !ret.isSuccess()) {
            this.header.addValue("retcode", String.valueOf(ret.getRetcode()));
            this.header.addValue("retinfo", ret.getRetinfo());
        }
        finish(convert.convertTo(context.getBufferSupplier(), ret));
    }

    /**
     * 将对象以JavaScript格式输出
     *
     * @param var    js变量名
     * @param result 输出对象
     */
    public void finishJsResult(String var, Object result) {
        this.contentType = "application/javascript; charset=utf-8";
        if (this.recycleListener != null) this.output = result;
        finish("var " + var + " = " + request.getJsonConvert().convertTo(result) + ";");
    }

    /**
     * 将对象以JavaScript格式输出
     *
     * @param jsonConvert 指定的JsonConvert
     * @param var         js变量名
     * @param result      输出对象
     */
    public void finishJsResult(JsonConvert jsonConvert, String var, Object result) {
        this.contentType = "application/javascript; charset=utf-8";
        if (this.recycleListener != null) this.output = result;
        finish("var " + var + " = " + jsonConvert.convertTo(result) + ";");
    }

    /**
     * 将指定字符串以响应结果输出
     *
     * @param obj 输出内容
     */
    public void finish(String obj) {
        if (this.recycleListener != null) this.output = obj;
        if (obj == null || obj.isEmpty()) {
            final ByteBuffer headbuf = createHeader();
            headbuf.flip();
            super.finish(headbuf);
            return;
        }
        if (context.getCharset() == null) {
            if (bufferHandler != null) {
                bufferHandler.execute(this, new ByteBuffer[]{ByteBuffer.wrap(Utility.encodeUTF8(obj))});
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
            if (bufferHandler != null) {
                ByteBuffer[] bufs = bufferHandler.execute(this, new ByteBuffer[]{buffer});
                if (bufs != null) buffer = bufs[0];
            }
            this.contentLength = buffer.remaining();
            final ByteBuffer headbuf = createHeader();
            headbuf.flip();
            super.finish(headbuf, buffer);
        }
    }

    /**
     * 以指定响应码附带内容输出
     *
     * @param status  响应码
     * @param message 输出内容
     */
    public void finish(int status, String message) {
        this.status = status;
        if (status != 200) super.refuseAlive();
        finish(message);
    }

    /**
     * 以304状态码输出
     */
    public void finish304() {
        super.finish(buffer304.duplicate());
    }

    /**
     * 以404状态码输出
     */
    public void finish404() {
        super.finish(buffer404.duplicate());
    }

    /**
     * 将指定ByteBuffer按响应结果输出
     *
     * @param buffer 输出内容
     */
    @Override
    public void finish(ByteBuffer buffer) {
        finish(false, buffer);
    }

    /**
     * 将指定ByteBuffer按响应结果输出
     *
     * @param kill   输出后是否强制关闭连接
     * @param buffer 输出内容
     */
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

    /**
     * 将指定ByteBuffer数组按响应结果输出
     *
     * @param buffers 输出内容
     */
    @Override
    public void finish(ByteBuffer... buffers) {
        finish(false, buffers);
    }

    /**
     * 将指定ByteBuffer数组按响应结果输出
     *
     * @param kill    输出后是否强制关闭连接
     * @param buffers 输出内容
     */
    @Override
    public void finish(boolean kill, ByteBuffer... buffers) {
        if (bufferHandler != null) {
            ByteBuffer[] bufs = bufferHandler.execute(this, buffers);
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

    /**
     * 异步输出指定内容
     *
     * @param <A>        泛型
     * @param buffer     输出内容
     * @param attachment 异步回调参数
     * @param handler    异步回调函数
     */
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

    /**
     * 将指定文件按响应结果输出
     *
     * @param file 输出文件
     *
     * @throws IOException IO异常
     */
    public void finish(File file) throws IOException {
        finishFile(null, file, null);
    }

    /**
     * 将文件按指定文件名输出
     *
     * @param filename 输出文件名
     * @param file     输出文件
     *
     * @throws IOException IO异常
     */
    public void finish(final String filename, File file) throws IOException {
        finishFile(filename, file, null);
    }

    /**
     * 将指定文件句柄或文件内容按响应结果输出，若fileBody不为null则只输出fileBody内容
     *
     * @param file     输出文件
     * @param fileBody 文件内容， 没有则输出file
     *
     * @throws IOException IO异常
     */
    protected void finishFile(final File file, ByteBuffer fileBody) throws IOException {
        finishFile(null, file, fileBody);
    }

    /**
     * 将指定文件句柄或文件内容按指定文件名输出，若fileBody不为null则只输出fileBody内容
     * file 与 fileBody 不能同时为空
     * file 与 filename 也不能同时为空
     *
     * @param filename 输出文件名
     * @param file     输出文件
     * @param fileBody 文件内容， 没有则输出file
     *
     * @throws IOException IO异常
     */
    protected void finishFile(final String filename, final File file, ByteBuffer fileBody) throws IOException {
        if ((file == null || !file.isFile() || !file.canRead()) && fileBody == null) {
            finish404();
            return;
        }
        if (fileBody != null) fileBody = fileBody.duplicate().asReadOnlyBuffer();
        final long length = file == null ? fileBody.remaining() : file.length();
        final String match = request.getHeader("If-None-Match");
        final String etag = (file == null ? 0L : file.lastModified()) + "-" + length;
        if (match != null && etag.equals(match)) {
            finish304();
            return;
        }
        this.contentLength = length;
        if (filename != null && !filename.isEmpty() && file != null) {
            addHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(filename, "UTF-8"));
        }
        this.contentType = MimeType.getByFilename(filename == null || filename.isEmpty() ? file.getName() : filename);
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
            long clen = end > 0 ? (end - start + 1) : (length - start);
            this.status = 206;
            addHeader("Accept-Ranges", "bytes");
            addHeader("Content-Range", "bytes " + start + "-" + (end > 0 ? end : length - 1) + "/" + length);
            this.contentLength = clen;
            len = end > 0 ? clen : end;
        }
        this.addHeader("ETag", etag);
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

    private void finishFile(ByteBuffer hbuffer, File file, long offset, long length) throws IOException {
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
                if (headers.length > 3) {
                    String v = request.getParameter(headers[2]);
                    if (v != null) this.header.addValue(headers[0], v);
                } else if (headers.length > 2) {
                    String v = request.getHeader(headers[2]);
                    if (v != null) this.header.addValue(headers[0], v);
                } else {
                    this.header.addValue(headers[0], headers[1]);
                }
            }
        }
        if (this.defaultSetHeaders != null) {
            for (String[] headers : this.defaultSetHeaders) {
                if (headers.length > 3) {
                    String v = request.getParameter(headers[2]);
                    if (v != null) this.header.setValue(headers[0], v);
                } else if (headers.length > 2) {
                    String v = request.getHeader(headers[2]);
                    if (v != null) this.header.setValue(headers[0], v);
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
            if (path == null || path.isEmpty()) path = "/";
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
        sb.append(cookie.getName()).append("=").append(cookie.getValue()).append("; Version=1");
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

    /**
     * 跳过header的输出
     * 通常应用场景是，调用者的输出内容里已经包含了HTTP的响应头信息，因此需要调用此方法避免重复输出HTTP响应头信息。
     *
     * @return HttpResponse
     */
    public HttpResponse skipHeader() {
        this.headsended = true;
        return this;
    }

    protected DefaultAnyValue duplicateHeader() {
        return this.header.duplicate();
    }

    /**
     * 设置Header值
     *
     * @param name  header名
     * @param value header值
     *
     * @return HttpResponse
     */
    public HttpResponse setHeader(String name, Object value) {
        this.header.setValue(name, String.valueOf(value));
        return this;
    }

    /**
     * 添加Header值
     *
     * @param name  header名
     * @param value header值
     *
     * @return HttpResponse
     */
    public HttpResponse addHeader(String name, Object value) {
        this.header.addValue(name, String.valueOf(value));
        return this;
    }

    /**
     * 添加Header值
     *
     * @param map header值
     *
     * @return HttpResponse
     */
    public HttpResponse addHeader(Map<String, ?> map) {
        if (map == null || map.isEmpty()) return this;
        for (Map.Entry<String, ?> en : map.entrySet()) {
            this.header.addValue(en.getKey(), String.valueOf(en.getValue()));
        }
        return this;
    }

    /**
     * 设置状态码
     *
     * @param status 状态码
     *
     * @return HttpResponse
     */
    public HttpResponse setStatus(int status) {
        this.status = status;
        return this;
    }

    /**
     * 获取状态码
     *
     * @return 状态码
     */
    public int getStatus() {
        return this.status;
    }

    /**
     * 获取 ContentType
     *
     * @return ContentType
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * 设置 ContentType
     *
     * @param contentType ContentType
     *
     * @return HttpResponse
     */
    public HttpResponse setContentType(String contentType) {
        this.contentType = contentType;
        return this;
    }

    /**
     * 获取内容长度
     *
     * @return 内容长度
     */
    public long getContentLength() {
        return contentLength;
    }

    /**
     * 设置内容长度
     *
     * @param contentLength 内容长度
     *
     * @return HttpResponse
     */
    public HttpResponse setContentLength(long contentLength) {
        this.contentLength = contentLength;
        return this;
    }

    /**
     * 获取输出时的拦截器
     *
     * @return 拦截器
     */
    protected BufferHandler getBufferHandler() {
        return bufferHandler;
    }

    /**
     * 设置输出时的拦截器
     *
     * @param bufferHandler 拦截器
     */
    protected void setBufferHandler(BufferHandler bufferHandler) {
        this.bufferHandler = bufferHandler;
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
