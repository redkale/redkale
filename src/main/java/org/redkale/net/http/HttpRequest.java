/*
 * To change this license headers, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.io.*;
import java.lang.annotation.Annotation;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import org.redkale.annotation.ClassDepends;
import org.redkale.annotation.Comment;
import org.redkale.convert.*;
import org.redkale.convert.json.JsonConvert;
import org.redkale.net.Request;
import org.redkale.util.*;
import static org.redkale.util.Utility.isEmpty;
import static org.redkale.util.Utility.isNotEmpty;

/**
 * Http请求包 与javax.servlet.http.HttpServletRequest 基本类似。 <br>
 * 同时提供json的解析接口: public Object getJsonParameter(Type type, String name) <br>
 * Redkale提倡带简单的参数的GET请求采用类似REST风格, 因此提供了 getPathParam 系列接口。 <br>
 * 例如简单的翻页查询 <br>
 * /pipes/user/query/offset:0/limit:20 <br>
 * 获取页号: int offset = request.getPathParam("offset:", 0); <br>
 * 获取行数: int limit = request.getPathParam("limit:", 10); <br>
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class HttpRequest extends Request<HttpContext> {

    protected static final Serializable CURRUSERID_NIL = new Serializable() {};

    protected static final int READ_STATE_ROUTE = 1;

    protected static final int READ_STATE_HEADER = 2;

    protected static final int READ_STATE_BODY = 3;

    protected static final int READ_STATE_END = 4;

    protected static final byte[] EMPTY_BYTES = new byte[0];

    protected static final String METHOD_GET = "GET";

    protected static final String METHOD_PUT = "PUT";

    protected static final String METHOD_POST = "POST";

    protected static final String METHOD_HEAD = "HEAD";

    protected static final String METHOD_OPTIONS = "OPTIONS";

    protected static final String HTTP_1_1 = "HTTP/1.1";

    protected static final String HTTP_2_0 = "HTTP/2.0";

    protected static final String HEAD_COOKIE = "Cookie";

    protected static final String HEAD_CONNECTION = "Connection";

    protected static final String HEAD_CONTENT_TYPE = "Content-Type";

    protected static final String HEAD_CONTENT_LENGTH = "Content-Length";

    protected static final String HEAD_CONTENT_ENCODING = "Content-Encoding";

    protected static final String HEAD_TRANSFER_ENCODING = "Transfer-Encoding";
    /*
     * Maximum chunk header size of 2KB + 2 bytes for CRLF
     */
    protected static final int MAX_CHUNK_HEADER_SIZE = 2050;

    protected static final String HEAD_ACCEPT = "Accept";

    protected static final String HEAD_HOST = "Host";

    protected static final String HEAD_UPGRADE = "Upgrade";

    protected static final String HEAD_USER_AGENT = "User-Agent";

    protected static final String HEAD_EXPECT = "Expect";

    public static final String SESSIONID_NAME = "JSESSIONID";

    // ---------- header 相关参数 开始 ----------
    protected int headerLength;

    protected int headerHalfLen;

    protected String contentType;

    protected long contentLength = -1;

    protected String contentEncoding;

    protected String host;

    @Comment("原始的cookie字符串，解析后值赋给HttpCookie[] cookies")
    protected String cookie;

    protected HttpCookie[] cookies;

    private boolean maybews = false; // 是否可能是WebSocket

    private boolean expect = false; // 是否Expect:100-continue

    // @since 2.8.0
    protected boolean chunked = false;
    // 是否已读\r
    protected boolean chunkedCR = false;

    protected int chunkedLength = -1;

    protected int chunkedCurrOffset = -1;

    protected byte[] chunkedHalfLenBytes;

    protected boolean rpc;

    protected int readState = READ_STATE_ROUTE;

    // @since 2.1.0
    protected Serializable currentUserid = CURRUSERID_NIL;

    protected Supplier<Serializable> currentUserSupplier;

    protected ConvertType reqConvertType;

    protected Convert reqConvert;

    protected ConvertType respConvertType;

    protected Convert respConvert;

    protected final HttpHeaders headers = HttpHeaders.create();

    // ---------- header 相关参数 结束 ----------
    @Comment("Method GET/POST/...")
    protected String method;

    protected boolean getmethod;

    protected String protocol;

    protected String requestPath;

    protected byte[] queryBytes;

    protected String newSessionid;

    protected final HttpParameters params = HttpParameters.create();

    protected boolean boundary = false;

    protected int moduleid;

    protected int actionid;

    protected String remoteAddr;

    protected String locale;

    // 主要给chunked和unzip用的
    private ByteArray array;

    private final ByteArray bodyBytes;

    private final ByteArray headerBytes;

    private boolean headerParsed = false;

    private boolean bodyParsed = false;

    private final String remoteAddrHeader;

    // 用逗号隔开的多个header
    private final String[] remoteAddrHeaders;

    private final String localHeader;

    private final String localParameter;

    final HttpRpcAuthenticator rpcAuthenticator;

    // 仅供HttpServlet传递Entry使用
    HttpServlet.ActionEntry actionEntry;

    public HttpRequest(HttpContext context) {
        this(context, new ByteArray(), new ByteArray());
    }

    protected HttpRequest(HttpContext context, ByteArray headerBytes, ByteArray bodyBytes) {
        super(context);
        this.headerBytes = headerBytes;
        this.bodyBytes = bodyBytes;
        this.remoteAddrHeader = context.remoteAddrHeader;
        this.remoteAddrHeaders = context.remoteAddrHeaders;
        this.localHeader = context.localHeader;
        this.localParameter = context.localParameter;
        this.rpcAuthenticator = context.rpcAuthenticator;
    }

    @SuppressWarnings("OverridableMethodCallInConstructor")
    protected HttpRequest(HttpContext context, WebRequest req) {
        super(context);
        this.headerBytes = new ByteArray();
        this.bodyBytes = new ByteArray();
        this.remoteAddrHeader = null;
        this.remoteAddrHeaders = null;
        this.localHeader = null;
        this.localParameter = null;
        this.rpcAuthenticator = null;
        if (req != null) {
            initWebRequest(req, true);
        }
    }

    protected HttpRequest initWebRequest(WebRequest req, boolean needPath) {
        if (req != null) {
            this.rpc = req.rpc;
            this.traceid = req.getTraceid();
            if (req.getBody() != null) {
                this.bodyBytes.put(req.getBody());
            }
            if (req.getHeaders() != null) {
                this.headers.setAll(req.getHeaders());
            }
            this.headerParsed = true;
            this.reqConvertType = req.getReqConvertType();
            this.reqConvert =
                    req.getReqConvertType() == null ? null : ConvertFactory.findConvert(req.getReqConvertType());
            this.respConvertType = req.getRespConvertType();
            this.respConvert =
                    req.getRespConvertType() == null ? null : ConvertFactory.findConvert(req.getRespConvertType());
            if (req.getParams() != null) {
                this.params.putAll(req.getParams());
            }
            if (req.getCurrentUserid() != null) {
                this.currentUserid = req.getCurrentUserid();
            }
            this.contentType = req.getContentType();
            this.remoteAddr = req.getRemoteAddr();
            this.locale = req.getLocale();
            this.requestPath = needPath ? req.requestPath() : req.getPath();
            this.method = req.getMethod();
            if (isNotEmpty(req.getSessionid())) {
                this.cookies = new HttpCookie[] {new HttpCookie(SESSIONID_NAME, req.getSessionid())};
            }
        }
        return this;
    }

    public WebRequest createSimpleRequest(String contextPath) {
        WebRequest req = new WebRequest();
        req.setBody(bodyBytes.length() == 0 ? null : bodyBytes.getBytes());
        if (!getHeaders().isEmpty()) {
            req.setHeaders(headers);
            if (headers.contains(Rest.REST_HEADER_RPC)) { // 外部request不能包含RPC的header信息
                req.removeHeader(Rest.REST_HEADER_RPC);
            }
            if (headers.contains(Rest.REST_HEADER_RESNAME)) { // 外部request不能包含RPC的header信息
                req.removeHeader(Rest.REST_HEADER_RESNAME);
            }
            if (headers.contains(Rest.REST_HEADER_CURRUSERID)) { // 外部request不能包含RPC的header信息
                req.removeHeader(Rest.REST_HEADER_CURRUSERID);
            }
        }
        parseBody();
        req.setParams(params.isEmpty() ? null : params);
        req.setRemoteAddr(getRemoteAddr());
        req.setLocale(getLocale());
        req.setContentType(getContentType());
        req.setContextPath(contextPath);
        req.setMethod(this.method);
        String path0 = this.requestPath;
        if (isNotEmpty(contextPath) && path0.startsWith(contextPath)) {
            path0 = path0.substring(contextPath.length());
        }
        req.setPath(path0);
        req.setSessionid(getSessionid(false));
        req.setRpc(this.rpc);
        req.setTraceid(this.traceid);
        return req;
    }

    protected boolean isWebSocket() {
        return maybews && getmethod && "Upgrade".equalsIgnoreCase(getHeader("Connection"));
    }

    protected boolean isExpect() {
        return expect;
    }

    protected boolean isChunked() {
        return chunked;
    }

    protected void setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

    protected void setAnnotations(Annotation[] annotations) {
        this.annotations = annotations;
    }

    protected ConvertType getRespConvertType() {
        return this.respConvertType;
    }

    protected Convert getRespConvert() {
        return this.respConvert == null ? this.jsonConvert : this.respConvert;
    }

    @Override
    protected int pipelineHeaderLength() {
        return (!context.sameHeader || !context.lazyHeader) ? -1 : headerLength;
    }

    @Override
    protected int readHeader(final ByteBuffer buf, final int pipelineHeaderLength) {
        final ByteBuffer buffer = buf;
        if (this.readState == READ_STATE_ROUTE) {
            int rs = readMethodUriLine(buffer);
            if (rs != 0) {
                return rs;
            }
            this.headerBytes.clear();
            this.headerLength = 0;
            this.headerHalfLen = 0;
            this.readState = READ_STATE_HEADER;
        }
        if (this.readState == READ_STATE_HEADER) {
            if (pipelineHeaderLength > 0) {
                ByteArray hbytes = this.headerBytes;
                int bufremain = buffer.remaining();
                int remainHalf = pipelineHeaderLength - this.headerHalfLen;
                if (remainHalf > bufremain) {
                    hbytes.put(buffer);
                    this.headerHalfLen += bufremain;
                    buffer.clear();
                    return 1;
                }
                hbytes.put(buffer, remainHalf);
                this.headerLength = hbytes.length();
                this.headerHalfLen = this.headerLength;
                this.headerParsed = false;
            } else if (context.lazyHeader && getmethod) { // 非GET必须要读header，会有Content-Length
                int rs = loadHeaderBytes(buffer);
                if (rs >= 0 && this.headerLength > context.getMaxHeader()) {
                    context.getLogger()
                            .log(
                                    Level.WARNING,
                                    "http header.length must lower " + context.getMaxHeader() + ", but "
                                            + this.headerLength + ", path: " + requestPath);
                    return -1;
                }
                if (rs != 0) {
                    buffer.clear();
                    return rs;
                }
                this.headerParsed = false;
            } else {
                int startpos = buffer.position();
                ByteArray hbytes = this.headerBytes;
                int rs = readHeaderLines(buffer, hbytes);
                this.headerLength = buffer.position() - startpos + this.headerHalfLen;
                if (rs >= 0 && this.headerLength > context.getMaxHeader()) {
                    context.getLogger()
                            .log(
                                    Level.WARNING,
                                    "http header.length must lower " + context.getMaxHeader() + ", but "
                                            + this.headerLength + ", path: " + requestPath);
                    return -1;
                }
                if (rs != 0) {
                    this.headerHalfLen = hbytes.length();
                    buffer.clear();
                    return rs;
                }
                this.headerHalfLen = this.headerLength;
                this.headerParsed = true;
            }
            if (this.contentType != null && this.contentType.contains("boundary=")) {
                this.boundary = true;
            }
            if (this.boundary) {
                // 文件上传必须设置keepAlive为false，因为文件过大时用户不一定会skip掉多余的数据
                this.keepAlive = false;
            }
            // completed=true时ProtocolCodec会继续读下一个request
            this.completed = !this.boundary && !maybews;
            this.bodyBytes.clear();
            this.readState = READ_STATE_BODY;
        }
        if (this.readState == READ_STATE_BODY) {
            if (this.chunked) {
                return readChunkedBody(buffer);
            }
            ByteArray bbytes = this.bodyBytes;
            if (this.contentLength > 0 && (this.contentType == null || !this.boundary)) {
                if (this.contentLength > context.getMaxBody()) {
                    context.getLogger()
                            .log(
                                    Level.WARNING,
                                    "http body.length must lower " + context.getMaxBody() + ", but "
                                            + this.contentLength + ", path: " + requestPath);
                    return -1;
                }
                bbytes.put(buffer, Math.min((int) this.contentLength, buffer.remaining()));
                int lr = (int) this.contentLength - bbytes.length();
                if (lr == 0) {
                    this.readState = READ_STATE_END;
                    if (bbytes.isEmpty()) {
                        this.bodyParsed = true; // no body data
                    }
                } else {
                    buffer.clear();
                }
                return lr > 0 ? lr : 0;
            }
            // 文件上传、HTTP1.0或Connection:close
            if (buffer.hasRemaining() && (this.boundary || !this.keepAlive)) {
                bbytes.put(buffer, buffer.remaining());
            }
            this.readState = READ_STATE_END;
            if (bbytes.isEmpty()) {
                this.bodyParsed = true; // no body data
            } else if (!getmethod && this.contentLength < 0 && keepAlive) {
                // keep-alive=true:  Content-Length和chunk必然是二选一。
                // keep-alive=false: Content-Length可有可无.
                context.getLogger().log(Level.WARNING, "http not found content-length or chunk, path: " + requestPath);
                return -1;
            }
        }
        // 暂不考虑是keep-alive且存在body却没有指定Content-Length的情况
        return 0;
    }

    private int readChunkedBody(final ByteBuffer buf) {
        final ByteBuffer buffer = buf;
        int remain = buffer.remaining();
        if (this.chunkedLength < 0) { // 需要读取length
            ByteArray input = array().clear();
            if (this.chunkedHalfLenBytes != null) {
                input.put(this.chunkedHalfLenBytes);
                this.chunkedHalfLenBytes = null;
            }
            for (; ; ) {
                if (remain-- < 1) {
                    buffer.clear();
                    if (input.length() > 0) {
                        this.chunkedHalfLenBytes = input.getBytes();
                    }
                    return 1;
                }
                byte b = buffer.get();
                if (b == '\n') {
                    break;
                }
                input.put(b);
            }
            this.chunkedLength = parseHexLength(input);
            this.chunkedCurrOffset = 0;
            this.chunkedCR = false;
        }
        if (this.chunkedLength == 0) {
            if (remain < 1) {
                buffer.clear();
                return 1;
            }
            if (!this.chunkedCR) { // 读\r
                remain--;
                if (buffer.get() != '\r') {
                    throw new RedkaleException("invalid chunk end");
                }
                this.chunkedCR = true;
                if (remain < 1) {
                    buffer.clear();
                    return 1;
                }
            }
            // 读\n
            remain--;
            if (buffer.get() != '\n') {
                throw new RedkaleException("invalid chunk end");
            }
            this.readState = READ_STATE_END;
            if (this.bodyBytes.isEmpty()) {
                this.bodyParsed = true; // no bodyBytes data
            }
            return 0;
        } else {
            ByteArray body = this.bodyBytes;
            if (this.chunkedCurrOffset < this.chunkedLength) {
                for (; ; ) {
                    if (remain-- < 1) {
                        buffer.clear();
                        return 1;
                    }
                    byte b = buffer.get();
                    body.put(b);
                    this.chunkedCurrOffset++;
                    if (this.chunkedCurrOffset == this.chunkedLength) {
                        this.chunkedCR = false;
                        break;
                    }
                }
            }
            if (remain < 1) {
                buffer.clear();
                return 1;
            }
            // 读\r
            if (!this.chunkedCR) {
                remain--;
                if (buffer.get() != '\r') {
                    throw new RedkaleException("invalid chunk end");
                }
                this.chunkedCR = true;
                if (remain < 1) {
                    buffer.clear();
                    return 1;
                }
            }
            // 读\n
            remain--;
            if (buffer.get() != '\n') {
                throw new RedkaleException("invalid chunk end");
            }
            this.chunkedLength = -1;
            // 继续读下一个chunk
            return readChunkedBody(buffer);
        }
    }

    static int parseHexLength(ByteArray input) {
        int count = input.length();
        int len = 0;
        for (int i = 0; i < count; i++) {
            byte c = input.get(i);
            int val = 0;
            if (c >= '0' && c <= '9') {
                val = c - '0';
            } else if (c >= 'a' && c <= 'f') {
                val = c - 'a' + 10;
            } else if (c >= 'A' && c <= 'F') {
                val = c - 'A' + 10;
            } else if (c == '\r' || c == ';') { // ;后面是注释内容
                break;
            } else {
                throw new RedkaleException("invalid chunk length");
            }
            len = len * 16 + val;
        }
        return len;
    }

    private int loadHeaderBytes(final ByteBuffer buf) {
        final ByteBuffer buffer = buf;
        final ByteArray hbytes = this.headerBytes;
        int remain = buffer.remaining();
        final byte[] content = hbytes.expand(remain);
        int pos = hbytes.length();
        int rs = 1;
        while (remain-- > 0) {
            byte b = buffer.get();
            content[pos++] = b;
            if (b == '\n'
                    && pos > 3
                    && content[pos - 3] == '\n'
                    && content[pos - 2] == '\r'
                    && content[pos - 4] == '\r') {
                rs = 0;
                break;
            }
        }
        if (rs == 1) {
            buffer.clear();
        }
        hbytes.position(pos);
        this.headerLength = hbytes.length();
        this.headerHalfLen = this.headerLength;
        return rs;
    }

    // 解析 GET /xxx HTTP/1.1
    private int readMethodUriLine(final ByteBuffer buf) {
        final ByteBuffer buffer = buf;
        Charset charset = this.context.getCharset();
        int remain = buffer.remaining();
        int size;
        ByteArray bytes = bodyBytes; // body当temp buffer使用
        // 读method
        if (this.method == null) {
            boolean bigger = false;
            if (remain >= 5) {
                byte b1 = buffer.get();
                byte b2 = buffer.get();
                if (b2 == ' ') {
                    remain -= 2;
                    this.method = Character.toString(b1);
                    this.getmethod = false;
                } else {
                    byte b3 = buffer.get();
                    if (b3 == ' ') {
                        remain -= 3;
                        this.method = new String(new byte[] {b1, b2});
                        this.getmethod = false;
                    } else {
                        byte b4 = buffer.get();
                        if (b4 == ' ') {
                            remain -= 4;
                            if (b1 == 'G' && b2 == 'E' && b3 == 'T') {
                                this.method = METHOD_GET;
                                this.getmethod = true;
                            } else if (b1 == 'P' && b2 == 'U' && b3 == 'T') {
                                this.method = METHOD_PUT;
                                this.getmethod = false;
                            } else {
                                this.method = new String(new byte[] {b1, b2, b3});
                                this.getmethod = false;
                            }
                        } else {
                            byte b5 = buffer.get();
                            remain -= 5;
                            if (b5 == ' ') {
                                if (b1 == 'P' && b2 == 'O' && b3 == 'S' && b3 == 'T') {
                                    this.method = METHOD_POST;
                                    this.getmethod = false;
                                } else if (b1 == 'H' && b2 == 'E' && b3 == 'A' && b3 == 'D') {
                                    this.method = METHOD_HEAD;
                                    this.getmethod = false;
                                } else {
                                    this.method = new String(new byte[] {b1, b2, b3, b4});
                                    this.getmethod = false;
                                }
                            } else {
                                bigger = true;
                                bytes.put(b1, b2, b3, b4, b5);
                            }
                        }
                    }
                }
            }
            if (bigger) { // method长度大于4
                for (; ; ) {
                    if (remain-- < 1) {
                        buffer.clear();
                        return 1;
                    }
                    byte b = buffer.get();
                    if (b == ' ') {
                        break;
                    }
                    bytes.put(b);
                }
                size = bytes.length();
                byte[] content = bytes.content();
                if (size == 3) {
                    if (content[0] == 'G' && content[1] == 'E' && content[2] == 'T') {
                        this.method = METHOD_GET;
                        this.getmethod = true;
                    } else if (content[0] == 'P' && content[1] == 'U' && content[2] == 'T') {
                        this.method = METHOD_PUT;
                        this.getmethod = false;
                    } else {
                        this.method = bytes.toString(true, charset);
                        this.getmethod = false;
                    }
                } else if (size == 4) {
                    if (content[0] == 'P' && content[1] == 'O' && content[2] == 'S' && content[3] == 'T') {
                        this.method = METHOD_POST;
                    } else if (content[0] == 'H' && content[1] == 'E' && content[2] == 'A' && content[3] == 'D') {
                        this.method = METHOD_HEAD;
                    } else {
                        this.method = bytes.toString(true, charset);
                    }
                    this.getmethod = false;
                } else if (size == 7
                        && content[0] == 'O'
                        && content[1] == 'P'
                        && content[2] == 'T'
                        && content[3] == 'I'
                        && content[4] == 'O'
                        && content[5] == 'N'
                        && content[6] == 'S') {
                    this.method = METHOD_OPTIONS;
                    this.getmethod = false;
                } else {
                    this.method = bytes.toString(true, charset);
                    this.getmethod = false;
                }
                bytes.clear();
            }
        }

        // 读uri
        if (this.requestPath == null) {
            int qst = -1; // ?的位置
            boolean decodeable = false;
            boolean latin1 = true;
            for (; ; ) {
                if (remain-- < 1) {
                    buffer.clear();
                    return 1;
                }
                byte b = buffer.get();
                if (b == ' ') {
                    break;
                }
                if (b == '?' && qst < 0) {
                    qst = bytes.length();
                } else if (!decodeable && (b == '+' || b == '%')) {
                    decodeable = true;
                } else if (latin1 && (b < 0x20 || b >= 0x80)) {
                    latin1 = false;
                }
                bytes.put(b);
            }

            size = bytes.length();
            if (qst > 0) { // 带?参数
                if (decodeable) { // 需要转义
                    this.requestPath = toDecodeString(bytes, 0, qst, charset);
                } else {
                    this.requestPath = context.loadUriPath(bytes, qst, latin1, charset);
                }
                int qlen = size - qst - 1;
                this.queryBytes = bytes.getBytes(qst + 1, qlen);
                try {
                    addParameter(bytes, false, qst + 1, qlen);
                } catch (Exception e) {
                    this.context
                            .getLogger()
                            .log(Level.WARNING, "HttpRequest.addParameter error: " + bytes.toString(), e);
                }
            } else { // 没有带?参数
                if (decodeable) { // 需要转义
                    this.requestPath = toDecodeString(bytes, 0, bytes.length(), charset);
                } else {
                    this.requestPath = context.loadUriPath(bytes, latin1, charset);
                }
                this.queryBytes = EMPTY_BYTES;
            }
            bytes.clear();
        }
        // 读protocol
        for (; ; ) {
            if (remain-- < 1) {
                this.params.clear();
                buffer.clear();
                return 1;
            }
            byte b = buffer.get();
            if (b == '\r') {
                if (remain-- < 1) {
                    this.params.clear();
                    buffer.clear();
                    buffer.put(b);
                    return 1;
                }
                if (buffer.get() != '\n') {
                    return -1;
                }
                break;
            }
            bytes.put(b);
        }
        size = bytes.length();
        byte[] content = bytes.content();
        if (size == 8 && content[0] == 'H' && content[5] == '1' && content[7] == '1') {
            this.protocol = HTTP_1_1;
        } else if (size == 8 && content[0] == 'H' && content[5] == '2' && content[7] == '0') {
            this.protocol = HTTP_2_0;
        } else {
            this.protocol = bytes.toString(true, charset);
        }
        bytes.clear();
        return 0;
    }

    // 解析Header Connection: keep-alive
    private int readHeaderLines(final ByteBuffer buf, ByteArray bytes) {
        final ByteBuffer buffer = buf;
        Charset charset = this.context.getCharset();
        int remain = buffer.remaining();
        for (; ; ) {
            bytes.clear();
            if (remain-- < 2) {
                if (remain == 1) {
                    byte one = buffer.get();
                    buffer.clear();
                    buffer.put(one);
                    return 1;
                }
                buffer.clear();
                return 1;
            }
            remain--;
            byte b1 = buffer.get();
            byte b2 = buffer.get();
            if (b1 == '\r' && b2 == '\n') {
                return 0;
            }
            boolean latin1 = true;
            if (latin1 && (b1 < 0x20 || b1 >= 0x80)) {
                latin1 = false;
            }
            if (latin1 && (b2 < 0x20 || b2 >= 0x80)) {
                latin1 = false;
            }
            bytes.put(b1, b2);
            for (; ; ) { // name
                if (remain-- < 1) {
                    buffer.clear();
                    buffer.put(bytes.content(), 0, bytes.length());
                    return 1;
                }
                byte b = buffer.get();
                if (b == ':') {
                    break;
                } else if (latin1 && (b < 0x20 || b >= 0x80)) {
                    latin1 = false;
                }
                bytes.put(b);
            }
            String name = parseHeaderName(latin1, bytes, charset);
            bytes.clear();
            boolean first = true;
            int space = 0;
            for (; ; ) { // value
                if (remain-- < 1) {
                    buffer.clear();
                    buffer.put(name.getBytes());
                    buffer.put((byte) ':');
                    if (space == 1) {
                        buffer.put((byte) ' ');
                    } else if (space > 0) {
                        for (int i = 0; i < space; i++) buffer.put((byte) ' ');
                    }
                    buffer.put(bytes.content(), 0, bytes.length());
                    return 1;
                }
                byte b = buffer.get();
                if (b == '\r') {
                    if (remain-- < 1) {
                        buffer.clear();
                        buffer.put(name.getBytes());
                        buffer.put((byte) ':');
                        if (space == 1) {
                            buffer.put((byte) ' ');
                        } else if (space > 0) {
                            for (int i = 0; i < space; i++) buffer.put((byte) ' ');
                        }
                        buffer.put(bytes.content(), 0, bytes.length());
                        buffer.put((byte) '\r');
                        return 1;
                    }
                    if (buffer.get() != '\n') {
                        return -1;
                    }
                    break;
                }
                if (first) {
                    if (b <= ' ') {
                        space++;
                        continue;
                    }
                    first = false;
                }
                bytes.put(b);
            }
            String value;
            int vlen = bytes.length();
            byte[] content = bytes.content();
            switch (name) {
                case HEAD_CONTENT_TYPE: // Content-Type
                    this.contentType = bytes.toString(true, charset);
                    break;
                case HEAD_CONTENT_LENGTH: // Content-Length
                    this.contentLength = Long.decode(bytes.toString(true, charset));
                    break;
                case HEAD_CONTENT_ENCODING: // Content-Encoding
                    this.contentEncoding = bytes.toString(true, charset);
                    break;
                case HEAD_HOST: // Host
                    this.host = bytes.toString(charset);
                    break;
                case HEAD_COOKIE: // Cookie
                    if (this.cookie == null || this.cookie.isEmpty()) {
                        this.cookie = bytes.toString(charset);
                    } else {
                        this.cookie += ";" + bytes.toString(charset);
                    }
                    break;
                case HEAD_CONNECTION: // Connection
                    if (vlen > 0) {
                        if (vlen == 5
                                && content[0] == 'c'
                                && content[1] == 'l'
                                && content[2] == 'o'
                                && content[3] == 's'
                                && content[4] == 'e') {
                            value = "close";
                            this.setKeepAlive(false);
                        } else if (vlen == 10
                                && content[0] == 'k'
                                && content[1] == 'e'
                                && content[2] == 'e'
                                && content[3] == 'p'
                                && content[4] == '-'
                                && content[5] == 'a'
                                && content[6] == 'l'
                                && content[7] == 'i'
                                && content[8] == 'v'
                                && content[9] == 'e') {
                            value = "keep-alive";
                            this.setKeepAlive(true);
                        } else {
                            value = bytes.toString(charset);
                            this.setKeepAlive(true);
                        }
                    } else {
                        value = "";
                    }
                    headers.setValid(HEAD_CONNECTION, value);
                    break;
                case HEAD_UPGRADE: // Upgrade
                    this.maybews = vlen == 9
                            && content[0] == 'w'
                            && content[1] == 'e'
                            && content[2] == 'b'
                            && content[3] == 's'
                            && content[4] == 'o'
                            && content[5] == 'c'
                            && content[6] == 'k'
                            && content[7] == 'e'
                            && content[8] == 't';
                    headers.setValid(HEAD_UPGRADE, this.maybews ? "websocket" : bytes.toString(true, charset));
                    break;
                case HEAD_TRANSFER_ENCODING: // Transfer-Encoding
                    this.chunked = vlen == 7
                            && content[0] == 'c'
                            && content[1] == 'h'
                            && content[2] == 'u'
                            && content[3] == 'n'
                            && content[4] == 'k'
                            && content[5] == 'e'
                            && content[6] == 'd';
                    headers.setValid(HEAD_TRANSFER_ENCODING, this.chunked ? "chunked" : bytes.toString(true, charset));
                    break;
                case HEAD_EXPECT: // Expect
                    this.expect = vlen == 12
                            && content[0] == '1'
                            && content[1] == '0'
                            && content[2] == '0'
                            && content[3] == '-'
                            && content[4] == 'c'
                            && content[5] == 'o'
                            && content[6] == 'n'
                            && content[7] == 't'
                            && content[8] == 'i'
                            && content[9] == 'n'
                            && content[10] == 'u'
                            && content[11] == 'e';
                    headers.setValid(HEAD_EXPECT, this.expect ? "100-continue" : bytes.toString(true, charset));
                    break;
                case Rest.REST_HEADER_RPC: // rest-rpc
                    this.rpc = vlen == 4
                            && content[0] == 't'
                            && content[1] == 'r'
                            && content[2] == 'u'
                            && content[3] == 'e';
                    headers.setValid(
                            name,
                            this.rpc
                                    ? "true"
                                    : (vlen == 5
                                                    && content[0] == 'f'
                                                    && content[1] == 'a'
                                                    && content[2] == 'l'
                                                    && content[3] == 's'
                                                    && content[4] == 'e'
                                            ? "false"
                                            : bytes.toString(true, charset)));
                    break;
                case Rest.REST_HEADER_TRACEID: // rest-traceid
                    value = bytes.toString(true, charset);
                    this.traceid = value;
                    headers.setValid(name, value);
                    break;
                case Rest.REST_HEADER_CURRUSERID: // rest-curruserid
                    value = bytes.toString(true, charset);
                    this.currentUserid = value;
                    headers.setValid(name, value);
                    break;
                case Rest.REST_HEADER_REQ_CONVERT: // rest-req-convert-type
                    value = bytes.toString(true, charset);
                    reqConvertType = ConvertType.valueOf(value);
                    reqConvert = ConvertFactory.findConvert(reqConvertType);
                    headers.setValid(name, value);
                    break;
                case Rest.REST_HEADER_RESP_CONVERT: // rest-resp-convert-type
                    value = bytes.toString(true, charset);
                    respConvertType = ConvertType.valueOf(value);
                    respConvert = ConvertFactory.findConvert(respConvertType);
                    headers.setValid(name, value);
                    break;
                default:
                    headers.addValid(name, bytes.toString(charset));
            }
        }
    }

    private void parseHeader() {
        if (headerParsed) {
            return;
        }
        headerParsed = true;
        if (headerBytes.length() > 0) {
            readHeaderLines(this.headerBytes.wrapByteBuffer(), array().clear());
        }
    }

    static String parseHeaderName(boolean latin1, ByteArray bytes, Charset charset) {
        final int size = bytes.length();
        final byte[] bs = bytes.content();
        final byte first = bs[0];
        if ((first == 'H' || first == 'h') && size == 4) { // Host
            if (bs[1] == 'o' && bs[2] == 's' && bs[3] == 't') {
                return HEAD_HOST;
            }
        } else if ((first == 'A' || first == 'a') && size == 6) { // Accept
            if (bs[1] == 'c' && bs[2] == 'c' && bs[3] == 'e' && bs[4] == 'p' && bs[5] == 't') {
                return HEAD_ACCEPT;
            }
        } else if ((first == 'T' || first == 't') && size == 17) { // Transfer-Encoding
            if (bs[1] == 'r'
                    && bs[2] == 'a'
                    && bs[3] == 'n'
                    && bs[4] == 's'
                    && bs[5] == 'f'
                    && bs[6] == 'e'
                    && bs[7] == 'r'
                    && bs[8] == '-'
                    && (bs[9] == 'E' || bs[9] == 'e')
                    && bs[10] == 'n'
                    && bs[11] == 'c'
                    && bs[12] == 'o'
                    && bs[13] == 'd'
                    && bs[14] == 'i'
                    && bs[15] == 'n'
                    && bs[16] == 'g') {
                return HEAD_TRANSFER_ENCODING;
            }
        } else if (first == 'C' || first == 'c') {
            if (size == 10) { // Connection
                if (bs[1] == 'o'
                        && bs[2] == 'n'
                        && bs[3] == 'n'
                        && bs[4] == 'e'
                        && bs[5] == 'c'
                        && bs[6] == 't'
                        && bs[7] == 'i'
                        && bs[8] == 'o'
                        && bs[9] == 'n') {
                    return HEAD_CONNECTION;
                }
            } else if (size == 12) { // Content-Type
                if (bs[1] == 'o'
                        && bs[2] == 'n'
                        && bs[3] == 't'
                        && bs[4] == 'e'
                        && bs[5] == 'n'
                        && bs[6] == 't'
                        && bs[7] == '-'
                        && (bs[8] == 'T' || bs[8] == 't')
                        && bs[9] == 'y'
                        && bs[10] == 'p'
                        && bs[11] == 'e') {
                    return HEAD_CONTENT_TYPE;
                }
            } else if (size == 14) { // Content-Length
                if (bs[1] == 'o'
                        && bs[2] == 'n'
                        && bs[3] == 't'
                        && bs[4] == 'e'
                        && bs[5] == 'n'
                        && bs[6] == 't'
                        && bs[7] == '-'
                        && (bs[8] == 'L' || bs[8] == 'l')
                        && bs[9] == 'e'
                        && bs[10] == 'n'
                        && bs[11] == 'g'
                        && bs[12] == 't'
                        && bs[13] == 'h') {
                    return HEAD_CONTENT_LENGTH;
                }
            } else if (size == 16) { // Content-Encoding
                if (bs[1] == 'o'
                        && bs[2] == 'n'
                        && bs[3] == 't'
                        && bs[4] == 'e'
                        && bs[5] == 'n'
                        && bs[6] == 't'
                        && bs[7] == '-'
                        && (bs[8] == 'E' || bs[8] == 'e')
                        && bs[9] == 'n'
                        && bs[10] == 'c'
                        && bs[11] == 'o'
                        && bs[12] == 'd'
                        && bs[13] == 'i'
                        && bs[14] == 'n'
                        && bs[15] == 'g') {
                    return HEAD_CONTENT_ENCODING;
                }
            } else if (size == 6) { // Cookie
                if (bs[1] == 'o' && bs[2] == 'o' && bs[3] == 'k' && bs[4] == 'i' && bs[5] == 'e') {
                    return HEAD_COOKIE;
                }
            }
        } else if (first == 'U' || first == 'u') {
            if (size == 7) { // Upgrade
                if (bs[1] == 'p' && bs[2] == 'g' && bs[3] == 'r' && bs[4] == 'a' && bs[5] == 'd' && bs[6] == 'e') {
                    return HEAD_UPGRADE;
                }
            } else if (size == 10) { // User-Agent
                if (bs[1] == 's'
                        && bs[2] == 'e'
                        && bs[3] == 'r'
                        && bs[4] == '-'
                        && (bs[5] == 'A' || bs[5] == 'a')
                        && bs[6] == 'g'
                        && bs[7] == 'e'
                        && bs[8] == 'n'
                        && bs[9] == 't') {
                    return HEAD_USER_AGENT;
                }
            }
        } else if ((first == 'E' || first == 'e') && size == 6) { // Expect
            if (bs[1] == 'x' && bs[2] == 'p' && bs[3] == 'e' && bs[4] == 'c' && bs[5] == 't') {
                return HEAD_EXPECT;
            }
        } else if ((first == 'R' || first == 'r')
                && size > 5
                && bs[1] == 'e'
                && bs[2] == 's'
                && bs[3] == 't'
                && bs[4] == '-') { // Rest-XXX
            if (size == 8 && (bs[5] == 'R' || bs[5] == 'r') && bs[6] == 'p' && bs[7] == 'c') { // Rest-Rpc
                return Rest.REST_HEADER_RPC;
            } else if (size == 12
                    && (bs[5] == 'R' || bs[5] == 'r')
                    && bs[6] == 'e'
                    && bs[7] == 's'
                    && bs[8] == 'n'
                    && bs[9] == 'a'
                    && bs[10] == 'm'
                    && bs[11] == 'e') { // Rest-Resname
                return Rest.REST_HEADER_RESNAME;
            } else if (size == 12
                    && (bs[5] == 'T' || bs[5] == 't')
                    && bs[6] == 'r'
                    && bs[7] == 'a'
                    && bs[8] == 'c'
                    && bs[9] == 'e'
                    && bs[10] == 'i'
                    && bs[11] == 'd') { // Rest-Traceid
                return Rest.REST_HEADER_TRACEID;
            } else if (size == 15
                    && (bs[5] == 'C' || bs[5] == 'c')
                    && bs[6] == 'u'
                    && bs[7] == 'r'
                    && bs[8] == 'r'
                    && bs[9] == 'u'
                    && bs[10] == 's'
                    && bs[11] == 'e'
                    && bs[12] == 'r'
                    && bs[13] == 'i'
                    && bs[14] == 'd') { // Rest-Curruserid
                return Rest.REST_HEADER_CURRUSERID;
            } else if (size == 16
                    && (bs[5] == 'R' || bs[5] == 'r')
                    && bs[6] == 'e'
                    && bs[7] == 'q'
                    && bs[8] == '-'
                    && (bs[9] == 'C' || bs[9] == 'c')
                    && bs[10] == 'o'
                    && bs[11] == 'n'
                    && bs[12] == 'v'
                    && bs[13] == 'e'
                    && bs[14] == 'r'
                    && bs[15] == 't') { // Rest-Req-Convert
                return Rest.REST_HEADER_REQ_CONVERT;
            } else if (size == 17
                    && (bs[5] == 'R' || bs[5] == 'r')
                    && bs[6] == 'e'
                    && bs[7] == 's'
                    && bs[8] == 'p'
                    && bs[9] == '-'
                    && (bs[10] == 'C' || bs[10] == 'c')
                    && bs[11] == 'o'
                    && bs[12] == 'n'
                    && bs[13] == 'v'
                    && bs[14] == 'e'
                    && bs[15] == 'r'
                    && bs[16] == 't') { // Rest-Resp-Convert
                return Rest.REST_HEADER_RESP_CONVERT;
            }
        }
        return bytes.toString(latin1, charset);
    }

    @Override
    protected final Serializable getRequestid() {
        return null;
    }

    @Override
    protected void prepare() {
        this.keepAlive = true; // 默认HTTP/1.1
    }

    @Override
    protected void recycle() {
        // header
        this.headerLength = 0;
        this.headerHalfLen = 0;
        this.headerBytes.clear();
        this.headerParsed = false;
        this.contentType = null;
        this.contentLength = -1;
        this.contentEncoding = null;
        this.host = null;
        this.cookie = null;
        this.cookies = null;
        this.maybews = false;
        this.expect = false;
        this.chunked = false;
        this.chunkedCR = false;
        this.chunkedLength = -1;
        this.chunkedCurrOffset = -1;
        this.chunkedHalfLenBytes = null;
        this.rpc = false;
        this.readState = READ_STATE_ROUTE;
        this.currentUserid = CURRUSERID_NIL;
        this.currentUserSupplier = null;
        this.reqConvertType = null;
        this.reqConvert = null;
        this.respConvert = jsonConvert;
        this.respConvertType = null;
        this.headers.clear();
        // 其他
        this.newSessionid = null;
        this.method = null;
        this.getmethod = false;
        this.protocol = null;
        this.requestPath = null;
        this.queryBytes = null;
        this.boundary = false;
        this.moduleid = 0;
        this.actionid = 0;
        this.remoteAddr = null;
        this.params.clear();
        this.bodyBytes.clear();
        this.bodyParsed = false;
        // 内部
        this.actionEntry = null;
        super.recycle();
    }

    protected void skipBodyParse() {
        this.bodyParsed = true;
    }

    private void parseBody() {
        if (this.boundary || bodyParsed) {
            return;
        }
        bodyParsed = true;
        if (this.contentEncoding != null) {
            unzipEncoding();
        }
        if (this.getContentType() != null && this.contentType.toLowerCase().contains("x-www-form-urlencoded")) {
            addParameter(bodyBytes, true, 0, bodyBytes.length());
        }
    }

    protected ByteArray array() {
        if (array == null) {
            array = new ByteArray();
        }
        return array;
    }

    protected void unzipEncoding() {
        try {
            if ("gzip".equalsIgnoreCase(this.contentEncoding)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ByteArrayInputStream in = new ByteArrayInputStream(bodyBytes.content(), 0, bodyBytes.length());
                GZIPInputStream ungzip = new GZIPInputStream(in);
                int n;
                byte[] buffer = array().clear().content();
                while ((n = ungzip.read(buffer)) > 0) {
                    out.write(buffer, 0, n);
                }
                bodyBytes.clear();
                bodyBytes.put(out.toByteArray());
            } else if ("deflate".equalsIgnoreCase(this.contentEncoding)) {
                Inflater infl = new Inflater();
                infl.setInput(bodyBytes.content(), 0, bodyBytes.length());
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int n;
                byte[] buffer = array().clear().content();
                while (!infl.finished()) {
                    n = infl.inflate(buffer);
                    if (n == 0) {
                        break;
                    }
                    out.write(buffer, 0, n);
                }
                infl.end();
                bodyBytes.clear();
                bodyBytes.put(out.toByteArray());
            }
        } catch (Exception e) {
            throw new RedkaleException("invalid encoding content");
        }
    }

    private void addParameter(final ByteArray bytes, final boolean body, final int offset, final int len) {
        if (len < 1) {
            return;
        }
        Charset charset = this.context.getCharset();
        int limit = offset + len;
        int keypos = bytes.indexOf(offset, limit, '=');
        int valpos = bytes.indexOf(offset, limit, '&');
        if (keypos <= 0 || (valpos >= 0 && valpos < keypos)) {
            if (valpos > 0) {
                addParameter(bytes, body, valpos + 1, limit - valpos - 1);
            }
            return;
        }
        String name = toDecodeString(bytes, offset, keypos - offset, charset);
        if (body && !name.isEmpty() && name.charAt(0) == '<') {
            return; // 内容可能是xml格式; 如: <?xml version="1.0"
        }
        ++keypos;
        String value = toDecodeString(bytes, keypos, (valpos < 0) ? (limit - keypos) : (valpos - keypos), charset);
        this.params.put(name, value);
        if (valpos >= 0) {
            addParameter(bytes, body, valpos + 1, limit - valpos - 1);
        }
    }

    protected HttpRequest setMethod(String method) {
        this.method = method;
        this.getmethod = METHOD_GET.equalsIgnoreCase(method);
        return this;
    }

    protected HttpRequest setRequestPath(String path) {
        this.requestPath = path;
        return this;
    }

    protected HttpRequest setRemoteAddr(String remoteAddr) {
        this.remoteAddr = remoteAddr;
        return this;
    }

    protected HttpRequest setLocale(String locale) {
        this.locale = locale;
        return this;
    }

    protected HttpRequest setParameter(String name, String value) {
        this.params.put(name, value);
        return this;
    }

    protected HttpRequest setHeader(String name, String value) {
        this.headers.setValid(name, value);
        return this;
    }

    protected HttpRequest addHeader(String name, String value) {
        this.headers.add(name, value);
        return this;
    }

    protected HttpRequest removeParameter(String name) {
        this.params.remove(name);
        return this;
    }

    protected HttpRequest removeHeader(String name) {
        this.headers.remove(name);
        return this;
    }

    protected static String toDecodeString(ByteArray bytes, int offset, int len, final Charset charset) {
        byte[] content = bytes.content();
        if (len == 1) {
            return Character.toString(content[offset]);
        } else if (len == 2 && content[offset] >= 0x20 && content[offset] < 0x80) {
            return new String(content, 0, offset, len);
        } else if (len == 3 && content[offset + 1] >= 0x20 && content[offset + 1] < 0x80) {
            return new String(content, 0, offset, len);
        }
        int start = offset;
        final int end = offset + len;
        boolean flag = false; // 是否需要转义
        byte[] bs = content;
        for (int i = offset; i < end; i++) {
            if (content[i] == '+' || content[i] == '%') {
                flag = true;
                break;
            }
        }
        if (flag) {
            int index = 0;
            bs = new byte[len];
            for (int i = offset; i < end; i++) {
                switch (content[i]) {
                    case '+':
                        bs[index] = ' ';
                        break;
                    case '%':
                        bs[index] = (byte) ((hexBit(content[++i]) * 16 + hexBit(content[++i])));
                        break;
                    default:
                        bs[index] = content[i];
                        break;
                }
                index++;
            }
            start = 0;
            len = index;
        }
        return new String(bs, start, len, charset == null ? StandardCharsets.UTF_8 : charset);
    }

    private static int hexBit(byte b) {
        if ('0' <= b && '9' >= b) {
            return b - '0';
        }
        if ('a' <= b && 'z' >= b) {
            return b - 'a' + 10;
        }
        if ('A' <= b && 'Z' >= b) {
            return b - 'A' + 10;
        }
        return b;
    }

    /**
     * 设置当前用户ID, 通常在HttpServlet.preExecute方法里设置currentUserid <br>
     * 数据类型只能是int、long、String、JavaBean
     *
     * @param <T> 泛型
     * @param userid 用户ID
     * @return HttpRequest
     * @since 2.1.0
     */
    public <T extends Serializable> HttpRequest setCurrentUserid(T userid) {
        this.currentUserid = userid;
        return this;
    }

    /**
     * 获取当前用户ID的int值<br>
     *
     * @return 用户ID
     * @since 2.4.0
     */
    @ClassDepends
    @SuppressWarnings("unchecked")
    public int currentIntUserid() {
        if (currentUserid == CURRUSERID_NIL || currentUserid == null) {
            return 0;
        }
        if (this.currentUserid instanceof Number) {
            return ((Number) this.currentUserid).intValue();
        }
        String uid = this.currentUserid.toString();
        return uid.isEmpty() ? 0 : Integer.parseInt(uid);
    }

    /**
     * 获取当前用户ID的long值<br>
     *
     * @return 用户ID
     * @since 2.7.0
     */
    @ClassDepends
    @SuppressWarnings("unchecked")
    public long currentLongUserid() {
        if (currentUserid == CURRUSERID_NIL || currentUserid == null) {
            return 0L;
        }
        if (this.currentUserid instanceof Number) {
            return ((Number) this.currentUserid).longValue();
        }
        String uid = this.currentUserid.toString();
        return uid.isEmpty() ? 0L : Long.parseLong(uid);
    }

    /**
     * 获取当前用户ID的String值<br>
     *
     * @return 用户ID
     * @since 2.8.0
     */
    @ClassDepends
    @SuppressWarnings("unchecked")
    public String currentStringUserid() {
        if (currentUserid == CURRUSERID_NIL || currentUserid == null) {
            return null;
        }
        return this.currentUserid.toString();
    }

    /**
     * 获取当前用户ID<br>
     *
     * @param <T> 数据类型只能是int、long、String、JavaBean
     * @param type 类型
     * @return 用户ID
     * @since 2.1.0
     */
    @ClassDepends
    @SuppressWarnings("unchecked")
    public <T extends Serializable> T currentUserid(Class<T> type) {
        if (currentUserid == CURRUSERID_NIL || currentUserid == null) {
            if (type == int.class || type == Integer.class) {
                return (T) (Integer) 0;
            }
            if (type == long.class || type == Long.class) {
                return (T) (Long) (long) 0;
            }
            return null;
        }
        if (type == int.class || type == Integer.class) {
            if (this.currentUserid instanceof Number) {
                return (T) (Integer) ((Number) this.currentUserid).intValue();
            }
            String uid = this.currentUserid.toString();
            return (T) (Integer) (uid.isEmpty() ? 0 : Integer.parseInt(uid));
        }
        if (type == long.class || type == Long.class) {
            if (this.currentUserid instanceof Number) {
                return (T) (Long) ((Number) this.currentUserid).longValue();
            }
            String uid = this.currentUserid.toString();
            return (T) (Long) (uid.isEmpty() ? 0L : Long.parseLong(uid));
        }
        if (type == String.class) {
            return (T) this.currentUserid.toString();
        }
        if (this.currentUserid instanceof CharSequence) {
            return JsonConvert.root().convertFrom(type, this.currentUserid.toString());
        }
        return (T) this.currentUserid;
    }

    /**
     * 建议使用 setCurrentUserid, 通过userid从Service或缓存中获取用户信息<br>
     * 设置当前用户信息, 通常在HttpServlet.preExecute方法里设置currentUser <br>
     * 数据类型由&#64;HttpUserType指定
     *
     * @param supplier currentUser对象方法
     * @since 2.4.0
     * @return HttpRequest
     */
    public HttpRequest setCurrentUserSupplier(Supplier supplier) {
        this.currentUserSupplier = supplier;
        return this;
    }

    /**
     * 建议使用 currentUserid, 通过userid从Service或缓存中获取用户信息<br>
     * 获取当前用户信息<br>
     * 数据类型由&#64;HttpUserType指定
     *
     * @param <T> &#64;HttpUserType指定的用户信息类型
     * @return 用户信息
     */
    @SuppressWarnings("unchecked")
    public <T> T currentUser() {
        Supplier<Serializable> supplier = this.currentUserSupplier;
        return (T) (supplier == null ? null : supplier.get());
    }

    /**
     * 获取模块ID，来自&#64;HttpServlet.moduleid()
     *
     * @return 模块ID
     */
    @ConvertDisabled
    public int getModuleid() {
        return this.moduleid;
    }

    /**
     * 获取操作ID，来自&#64;HttpMapping.actionid()
     *
     * @return 模块ID
     */
    @ConvertDisabled
    public int getActionid() {
        return this.actionid;
    }

    /**
     * 获取客户端地址IP
     *
     * @return 地址
     */
    @ConvertDisabled
    public SocketAddress getRemoteAddress() {
        return this.channel == null || !this.channel.isOpen() ? null : this.channel.getRemoteAddress();
    }

    /**
     * 获取客户端地址IP, 与getRemoteAddress() 的区别在于：本方法优先取header中指定为RemoteAddress名的值，
     * 没有则返回{@link #getRemoteAddress()}的getHostAddress()。
     * <br>
     * 本方法适用于服务前端有如nginx的代理服务器进行中转，通过 {@link #getRemoteAddress()}是获取不到客户端的真实IP。
     *
     * @return 地址
     */
    public String getRemoteAddr() {
        if (this.remoteAddr != null) {
            return this.remoteAddr;
        }
        parseHeader();
        String ipValue = null;
        if (remoteAddrHeader != null) {
            ipValue = getHeader(remoteAddrHeader);
        } else if (remoteAddrHeaders != null) {
            for (String h : remoteAddrHeaders) {
                ipValue = getHeader(h);
                if (ipValue != null && !ipValue.isBlank()) {
                    break;
                }
            }
        }
        if (ipValue != null) {
            int pos = ipValue.indexOf(',');
            if (pos > 3) {
                ipValue = ipValue.substring(0, pos);
            }
            this.remoteAddr = ipValue;
            return ipValue;
        }

        SocketAddress addr = getRemoteAddress();
        if (addr == null) {
            return "";
        }
        if (addr instanceof InetSocketAddress) {
            this.remoteAddr = ((InetSocketAddress) addr).getAddress().getHostAddress();
            return this.remoteAddr;
        }
        this.remoteAddr = String.valueOf(addr);
        return this.remoteAddr;
    }

    /**
     * 获取国际化Locale，值可以取之于header或parameter
     *
     * @return 国际化Locale
     */
    public String getLocale() {
        if (this.locale != null) {
            return this.locale;
        }
        if (localHeader != null) {
            String val = getHeader(localHeader);
            if (val != null) {
                this.locale = val;
                return val;
            }
        }
        if (localParameter != null) {
            String val = getParameter(localParameter);
            if (val != null) {
                this.locale = val;
                return val;
            }
        }
        return this.locale;
    }

    /**
     * 获取请求内容指定的编码字符串
     *
     * @param charset 编码
     * @return 内容
     */
    public String getBody(final Charset charset) {
        return charset == null ? bodyBytes.toString() : bodyBytes.toString(charset);
    }

    /**
     * 获取请求内容的UTF-8编码字符串
     *
     * @return 内容
     */
    @ConvertDisabled
    public String getBodyUTF8() {
        return bodyBytes.toString(StandardCharsets.UTF_8);
    }

    /**
     * 获取请求内容的JavaBean对象
     *
     * @param <T> 泛型
     * @param type 类型
     * @return 内容
     */
    public <T> T getBodyJson(java.lang.reflect.Type type) {
        if (isEmpty(bodyBytes)) {
            return null;
        }
        Convert convert = this.reqConvert;
        if (convert == null) {
            convert = context.getJsonConvert();
        }
        if (type == byte[].class) {
            return (T) bodyBytes.getBytes();
        }
        return (T) convert.convertFrom(type, bodyBytes.content());
    }

    /**
     * 获取请求内容的JavaBean对象
     *
     * @param <T> 泛型
     * @param convert Convert
     * @param type 类型
     * @return 内容
     */
    public <T> T getBodyJson(Convert convert, java.lang.reflect.Type type) {
        if (isEmpty(bodyBytes)) {
            return null;
        }
        if (type == byte[].class) {
            return (T) bodyBytes.getBytes();
        }
        return (T) convert.convertFrom(type, bodyBytes.content());
    }

    /**
     * 获取请求内容的byte[]
     *
     * @return 内容
     */
    public byte[] getBody() {
        return bodyBytes.length() == 0 ? null : bodyBytes.getBytes();
    }

    /**
     * 直接获取body对象
     *
     * @return body对象
     */
    @ConvertDisabled
    protected ByteArray getDirectBody() {
        return bodyBytes;
    }

    protected void updateBody(byte[] body) {
        this.bodyBytes.clear();
        if (body != null) {
            this.bodyBytes.put(body);
        }
    }

    @Override
    public String toString() {
        parseBody();
        return this.getClass().getSimpleName() + "{\r\n    method: " + this.method + ", \r\n    path: "
                + this.requestPath
                + (this.reqConvertType != null ? (", \r\n    reqConvertType: " + this.reqConvertType) : "")
                + (this.respConvertType != null ? (", \r\n    respConvertType: " + this.respConvertType) : "")
                + (this.currentUserid != CURRUSERID_NIL
                        ? (", \r\n    currentUserid: "
                                + (this.currentUserid == CURRUSERID_NIL ? null : this.currentUserid))
                        : "")
                + (this.getRemoteAddr() != null ? (", \r\n    remoteAddr: " + this.getRemoteAddr()) : "")
                + (this.cookie != null ? (", \r\n    cookies: " + this.cookie) : "")
                + (this.getContentType() != null ? (", \r\n    contentType: " + this.contentType) : "")
                + (this.protocol != null ? (", \r\n    protocol: " + this.protocol) : "")
                + (this.getHost() != null ? (", \r\n    host: " + this.host) : "")
                + (this.getContentLength() >= 0 ? (", \r\n    contentLength: " + this.contentLength) : "")
                + (this.contentEncoding != null ? (", \r\n    contentEncoding: " + this.contentEncoding) : "")
                + (this.bodyBytes.length() > 0 ? (", \r\n    bodyLength: " + this.bodyBytes.length()) : "")
                + (this.boundary || this.bodyBytes.isEmpty()
                        ? ""
                        : (", \r\n    bodyContent: "
                                + (this.respConvertType == null || this.respConvertType == ConvertType.JSON
                                        ? this.getBodyUTF8()
                                        : Arrays.toString(getBody()))))
                + ", \r\n    params: " + toMapString(this.params.map, 4)
                + ", \r\n    header: " + toMapString(this.headers.map, 4)
                + "\r\n}"; // this.headers.toString(4)
    }

    private static CharSequence toMapString(Map<String, ?> map, int indent) {
        final String space = " ".repeat(indent);
        StringBuilder sb = new StringBuilder();
        sb.append("{\r\n");
        if (map != null) {
            for (Map.Entry en : map.entrySet()) {
                Object val = en.getValue();
                if (val instanceof Collection) {
                    for (Object item : (Collection) val) {
                        sb.append(space)
                                .append("    '")
                                .append(en.getKey())
                                .append("': '")
                                .append(item)
                                .append("',\r\n");
                    }
                } else {
                    sb.append(space)
                            .append("    '")
                            .append(en.getKey())
                            .append("': '")
                            .append(val)
                            .append("',\r\n");
                }
            }
        }
        sb.append(space).append('}');
        return sb;
    }

    /**
     * 获取文件上传对象
     *
     * @return 文件上传对象
     */
    @ConvertDisabled
    public final MultiContext getMultiContext() {
        final InputStream in = newInputStream();
        return new MultiContext(
                context.getCharset(),
                this.getContentType(),
                this.params.map(),
                new BufferedInputStream(in, Math.max(bodyBytes.length(), 8192)) {
                    {
                        bodyBytes.copyTo(this.buf);
                        this.count = bodyBytes.length();
                    }
                },
                null);
    }

    /**
     * 是否上传文件请求
     *
     * @return boolean
     */
    public final boolean isMultipart() {
        return boundary;
    }

    /**
     * 获取文件上传信息列表
     *
     * @return 文件上传对象集合
     * @throws IOException IO异常
     */
    @ConvertDisabled
    public final Iterable<MultiPart> multiParts() throws IOException {
        return getMultiContext().parts();
    }

    /**
     * 获取sessionid
     *
     * @param autoCreate 无sessionid是否自动创建
     * @return sessionid
     */
    @ConvertDisabled
    public String getSessionid(boolean autoCreate) {
        String sessionid = getCookie(SESSIONID_NAME, null);
        if (autoCreate && (sessionid == null || sessionid.isEmpty())) {
            sessionid = context.createSessionid();
            this.newSessionid = sessionid;
        }
        return sessionid;
    }

    /**
     * 更新sessionid
     *
     * @return 新的sessionid值
     */
    public String changeSessionid() {
        this.newSessionid = context.createSessionid();
        return newSessionid;
    }

    /**
     * 指定值更新sessionid
     *
     * @param newSessionid 新sessionid值
     * @return 新的sessionid值
     */
    public String changeSessionid(String newSessionid) {
        this.newSessionid = newSessionid == null ? context.createSessionid() : newSessionid.trim();
        return newSessionid;
    }

    /** 使sessionid失效 */
    public void invalidateSession() {
        this.newSessionid = ""; // 为空表示删除sessionid
    }

    /**
     * 获取所有Cookie对象
     *
     * @return cookie对象数组
     */
    public HttpCookie[] getCookies() {
        parseHeader();
        if (this.cookies == null) {
            this.cookies = parseCookies(this.cookie);
        }
        return this.cookies.length == 0 ? null : this.cookies;
    }

    /**
     * 获取Cookie值
     *
     * @param name cookie名
     * @return cookie值
     */
    public String getCookie(String name) {
        return getCookie(name, null);
    }

    /**
     * 获取Cookie值， 没有返回默认值
     *
     * @param name cookie名
     * @param dfvalue 默认cookie值
     * @return cookie值
     */
    public String getCookie(String name, String dfvalue) {
        HttpCookie[] cs = getCookies();
        if (cs == null) {
            return dfvalue;
        }
        for (HttpCookie c : cs) {
            if (name.equals(c.getName())) {
                return c.getValue();
            }
        }
        return dfvalue;
    }

    private static HttpCookie[] parseCookies(String cookiestr) {
        if (cookiestr == null || cookiestr.isEmpty()) {
            return new HttpCookie[0];
        }
        String str = cookiestr.replaceAll("(^;)|(;$)", "").replaceAll(";+", ";");
        if (str.isEmpty()) {
            return new HttpCookie[0];
        }
        String[] strs = str.split(";");
        HttpCookie[] cookies = new HttpCookie[strs.length];
        for (int i = 0; i < strs.length; i++) {
            String s = strs[i];
            int pos = s.indexOf('=');
            String v = (pos < 0 ? "" : s.substring(pos + 1));
            if (v.indexOf('"') == 0 && v.lastIndexOf('"') == v.length() - 1) {
                v = v.substring(1, v.length() - 1);
            }
            cookies[i] = new HttpCookie((pos < 0 ? s : s.substring(0, pos)), v);
        }
        return cookies;
    }

    /**
     * 获取协议名 http、https、ws、wss等
     *
     * @return protocol
     */
    public String getProtocol() {
        return protocol;
    }

    /**
     * 获取请求方法 GET、POST等
     *
     * @return method
     */
    public String getMethod() {
        return method;
    }

    /**
     * 获取Content-Type的header值
     *
     * @return contentType
     */
    public String getContentType() {
        if (contentType == null) {
            parseHeader();
        }
        return contentType;
    }

    /**
     * 获取请求内容的长度, 为-1表示内容长度不确定
     *
     * @return 内容长度
     */
    public long getContentLength() {
        if (contentLength < 1) {
            parseHeader();
        }
        return contentLength;
    }

    /**
     * 获取Host的Header值
     *
     * @return Host
     */
    public String getHost() {
        if (host == null) {
            parseHeader();
        }
        return host;
    }

    /**
     * 获取请求的URL
     *
     * @return 请求的URL
     */
    public String getRequestPath() {
        return requestPath;
    }

    /**
     * 获取请求参数的byte[]
     *
     * @return byte[]
     */
    public byte[] getQueryBytes() {
        return queryBytes;
    }

    /**
     * 截取getRequestPath最后的一个/后面的部分
     *
     * @return String
     */
    @ConvertDisabled
    public String getPathLastParam() {
        if (requestPath == null) {
            return "";
        }
        return requestPath.substring(requestPath.lastIndexOf('/') + 1);
    }

    /**
     * 获取请求URL最后的一个/后面的部分的short值 <br>
     * 例如请求URL /pipes/user/query/2 <br>
     * 获取type参数: short type = request.getPathLastParam((short)0); //type = 2
     *
     * @param defvalue 默认short值
     * @return short值
     */
    public short getPathLastParam(short defvalue) {
        String val = getPathLastParam();
        if (val.isEmpty()) {
            return defvalue;
        }
        try {
            return Short.parseShort(val);
        } catch (NumberFormatException e) {
            return defvalue;
        }
    }

    /**
     * 获取请求URL最后的一个/后面的部分的short值 <br>
     * 例如请求URL /pipes/user/query/2 <br>
     * 获取type参数: short type = request.getPathLastParam(16, (short)0); //type = 2
     *
     * @param radix 进制数
     * @param defvalue 默认short值
     * @return short值
     */
    public short getPathLastParam(int radix, short defvalue) {
        String val = getPathLastParam();
        if (val.isEmpty()) {
            return defvalue;
        }
        try {
            return Short.parseShort(val, radix);
        } catch (NumberFormatException e) {
            return defvalue;
        }
    }

    /**
     * 获取请求URL最后的一个/后面的部分的int值 <br>
     * 例如请求URL /pipes/user/query/2 <br>
     * 获取type参数: int type = request.getPathLastParam(0); //type = 2
     *
     * @param defvalue 默认int值
     * @return int值
     */
    public int getPathLastParam(int defvalue) {
        String val = getPathLastParam();
        try {
            return val.isEmpty() ? defvalue : Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defvalue;
        }
    }

    /**
     * 获取请求URL最后的一个/后面的部分的int值 <br>
     * 例如请求URL /pipes/user/query/2 <br>
     * 获取type参数: int type = request.getPathLastParam(16, 0); //type = 2
     *
     * @param radix 进制数
     * @param defvalue 默认int值
     * @return int值
     */
    public int getPathLastParam(int radix, int defvalue) {
        String val = getPathLastParam();
        try {
            return val.isEmpty() ? defvalue : Integer.parseInt(val, radix);
        } catch (NumberFormatException e) {
            return defvalue;
        }
    }

    /**
     * 获取请求URL最后的一个/后面的部分的float值 <br>
     * 例如请求URL /pipes/user/query/2 <br>
     * 获取type参数: float type = request.getPathLastParam(0.0f); //type = 2.0f
     *
     * @param defvalue 默认float值
     * @return float值
     */
    public float getPathLastParam(float defvalue) {
        String val = getPathLastParam();
        try {
            return val.isEmpty() ? defvalue : Float.parseFloat(val);
        } catch (NumberFormatException e) {
            return defvalue;
        }
    }

    /**
     * 获取请求URL最后的一个/后面的部分的int值 <br>
     * 例如请求URL /pipes/user/query/2 <br>
     * 获取type参数: long type = request.getPathLastParam(0L); //type = 2
     *
     * @param defvalue 默认long值
     * @return long值
     */
    public long getPathLastParam(long defvalue) {
        String val = getPathLastParam();
        try {
            return val.isEmpty() ? defvalue : Long.parseLong(val);
        } catch (NumberFormatException e) {
            return defvalue;
        }
    }

    /**
     * 获取请求URL最后的一个/后面的部分的int值 <br>
     * 例如请求URL /pipes/user/query/2 <br>
     * 获取type参数: long type = request.getPathLastParam(16, 0L); //type = 2
     *
     * @param radix 进制数
     * @param defvalue 默认long值
     * @return long值
     */
    public long getPathLastParam(int radix, long defvalue) {
        String val = getPathLastParam();
        try {
            return val.isEmpty() ? defvalue : Long.parseLong(val, radix);
        } catch (NumberFormatException e) {
            return defvalue;
        }
    }

    /**
     * 获取请求URL最后的一个/后面的部分的double值 <br>
     * 例如请求URL /pipes/user/query/2 <br>
     * 获取type参数: double type = request.getPathLastParam(0.0); //type = 2.0
     *
     * @param defvalue 默认double值
     * @return double值
     */
    public double getPathLastParam(double defvalue) {
        String val = getPathLastParam();
        try {
            return val.isEmpty() ? defvalue : Double.parseDouble(val);
        } catch (NumberFormatException e) {
            return defvalue;
        }
    }

    /**
     * 从prefix之后截取getPath再对"/"进行分隔
     *
     * <p>
     *
     * @param prefix 前缀
     * @return String[]
     */
    public String[] getPathParams(String prefix) {
        if (requestPath == null || prefix == null) {
            return new String[0];
        }
        return requestPath
                .substring(requestPath.indexOf(prefix) + prefix.length() + (prefix.endsWith("/") ? 0 : 1))
                .split("/");
    }

    /**
     * 获取请求URL分段中含prefix段的值 <br>
     * 例如请求URL /pipes/user/query/name:hello <br>
     * 获取name参数: String name = request.getPathParam("name:", "none");
     *
     * @param prefix prefix段前缀
     * @param defvalue 默认值
     * @return prefix截断后的值
     */
    public String getPathParam(String prefix, String defvalue) {
        if (requestPath == null || prefix == null || prefix.isEmpty()) {
            return defvalue;
        }
        int pos = requestPath.indexOf(prefix);
        if (pos < 0) {
            return defvalue;
        }
        String sub = requestPath.substring(pos + prefix.length());
        pos = sub.indexOf('/');
        return pos < 0 ? sub : sub.substring(0, pos);
    }

    /**
     * 获取请求URL分段中含prefix段的short值 <br>
     * 例如请求URL /pipes/user/query/type:10 <br>
     * 获取type参数: short type = request.getPathParam("type:", (short)0);
     *
     * @param prefix prefix段前缀
     * @param defvalue 默认short值
     * @return short值
     */
    public short getPathParam(String prefix, short defvalue) {
        String val = getPathParam(prefix, null);
        try {
            return val == null ? defvalue : Short.parseShort(val);
        } catch (NumberFormatException e) {
            return defvalue;
        }
    }

    /**
     * 获取请求URL分段中含prefix段的short值 <br>
     * 例如请求URL /pipes/user/query/type:a <br>
     * 获取type参数: short type = request.getPathParam(16, "type:", (short)0); //type = 10
     *
     * @param radix 进制数
     * @param prefix prefix段前缀
     * @param defvalue 默认short值
     * @return short值
     */
    public short getPathParam(int radix, String prefix, short defvalue) {
        String val = getPathParam(prefix, null);
        try {
            return val == null ? defvalue : Short.parseShort(val, radix);
        } catch (NumberFormatException e) {
            return defvalue;
        }
    }

    /**
     * 获取请求URL分段中含prefix段的int值 <br>
     * 例如请求URL /pipes/user/query/offset:0/limit:50 <br>
     * 获取offset参数: int offset = request.getPathParam("offset:", 0); <br>
     * 获取limit参数: int limit = request.getPathParam("limit:", 20); <br>
     *
     * @param prefix prefix段前缀
     * @param defvalue 默认int值
     * @return int值
     */
    public int getPathParam(String prefix, int defvalue) {
        String val = getPathParam(prefix, null);
        try {
            return val == null ? defvalue : Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defvalue;
        }
    }

    /**
     * 获取请求URL分段中含prefix段的int值 <br>
     * 例如请求URL /pipes/user/query/offset:0/limit:50 <br>
     * 获取offset参数: int offset = request.getPathParam("offset:", 0); <br>
     * 获取limit参数: int limit = request.getPathParam(16, "limit:", 20); // limit = 16 <br>
     *
     * @param radix 进制数
     * @param prefix prefix段前缀
     * @param defvalue 默认int值
     * @return int值
     */
    public int getPathParam(int radix, String prefix, int defvalue) {
        String val = getPathParam(prefix, null);
        try {
            return val == null ? defvalue : Integer.parseInt(val, radix);
        } catch (NumberFormatException e) {
            return defvalue;
        }
    }

    /**
     * 获取请求URL分段中含prefix段的float值 <br>
     * 例如请求URL /pipes/user/query/point:40.0 <br>
     * 获取time参数: float point = request.getPathParam("point:", 0.0f);
     *
     * @param prefix prefix段前缀
     * @param defvalue 默认float值
     * @return float值
     */
    public float getPathParam(String prefix, float defvalue) {
        String val = getPathParam(prefix, null);
        try {
            return val == null ? defvalue : Float.parseFloat(val);
        } catch (NumberFormatException e) {
            return defvalue;
        }
    }

    /**
     * 获取请求URL分段中含prefix段的long值 <br>
     * 例如请求URL /pipes/user/query/time:1453104341363/id:40 <br>
     * 获取time参数: long time = request.getPathParam("time:", 0L);
     *
     * @param prefix prefix段前缀
     * @param defvalue 默认long值
     * @return long值
     */
    public long getPathParam(String prefix, long defvalue) {
        String val = getPathParam(prefix, null);
        try {
            return val == null ? defvalue : Long.parseLong(val);
        } catch (NumberFormatException e) {
            return defvalue;
        }
    }

    /**
     * 获取请求URL分段中含prefix段的long值 <br>
     * 例如请求URL /pipes/user/query/time:1453104341363/id:40 <br>
     * 获取time参数: long time = request.getPathParam(16, "time:", 0L);
     *
     * @param radix 进制数
     * @param prefix prefix段前缀
     * @param defvalue 默认long值
     * @return long值
     */
    public long getPathParam(int radix, String prefix, long defvalue) {
        String val = getPathParam(prefix, null);
        try {
            return val == null ? defvalue : Long.parseLong(val, radix);
        } catch (NumberFormatException e) {
            return defvalue;
        }
    }

    /**
     * 获取请求URL分段中含prefix段的double值 <br>
     * 例如请求URL /pipes/user/query/point:40.0 <br>
     * 获取time参数: double point = request.getPathParam("point:", 0.0);
     *
     * @param prefix prefix段前缀
     * @param defvalue 默认double值
     * @return double值
     */
    public double getPathParam(String prefix, double defvalue) {
        String val = getPathParam(prefix, null);
        try {
            return val == null ? defvalue : Double.parseDouble(val);
        } catch (NumberFormatException e) {
            return defvalue;
        }
    }

    // ------------------------------------------------------------------------------
    /**
     * 获取请求Header总对象
     *
     * @return AnyValue
     */
    @ClassDepends
    public HttpHeaders getHeaders() {
        parseHeader();
        return headers;
    }

    /**
     * 获取所有的header名
     *
     * @return header名数组
     */
    @ConvertDisabled
    public String[] getHeaderNames() {
        parseHeader();
        return headers.names();
    }

    /**
     * 获取指定的header值
     *
     * @param name header名
     * @return header值
     */
    public String getHeader(String name) {
        return getHeader(name, null);
    }

    /**
     * 获取指定的header值, 没有返回默认值
     *
     * @param name header名
     * @param defaultValue 默认值
     * @return header值
     */
    @ClassDepends
    public String getHeader(String name, String defaultValue) {
        parseHeader();
        return headers.firstValue(name, defaultValue);
    }

    /**
     * 获取指定的header的json值
     *
     * @param <T> 泛型
     * @param type 反序列化的类名
     * @param name header名
     * @return header值
     */
    @ClassDepends
    public <T> T getJsonHeader(java.lang.reflect.Type type, String name) {
        String v = getHeader(name);
        return isEmpty(v) ? null : jsonConvert.convertFrom(type, v);
    }

    /**
     * 获取指定的header的json值
     *
     * @param <T> 泛型
     * @param convert JsonConvert对象
     * @param type 反序列化的类名
     * @param name header名
     * @return header值
     */
    @ClassDepends
    public <T> T getJsonHeader(JsonConvert convert, java.lang.reflect.Type type, String name) {
        String v = getHeader(name);
        return isEmpty(v) ? null : convert.convertFrom(type, v);
    }

    /**
     * 获取指定的header的boolean值, 没有返回默认boolean值
     *
     * @param name header名
     * @param defaultValue 默认boolean值
     * @return header值
     */
    @ClassDepends
    public boolean getBooleanHeader(String name, boolean defaultValue) {
        String value = getHeader(name);
        return isEmpty(value) ? defaultValue : Boolean.parseBoolean(value);
    }

    /**
     * 获取指定的header的short值, 没有返回默认short值
     *
     * @param name header名
     * @param defaultValue 默认short值
     * @return header值
     */
    @ClassDepends
    public short getShortHeader(String name, short defaultValue) {
        String value = getHeader(name);
        if (isEmpty(value)) {
            return defaultValue;
        }
        try {
            return Short.decode(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取指定的header的short值, 没有返回默认short值
     *
     * @param radix 进制数
     * @param name header名
     * @param defaultValue 默认short值
     * @return header值
     */
    @ClassDepends
    public short getShortHeader(int radix, String name, short defaultValue) {
        String value = getHeader(name);
        if (isEmpty(value)) {
            return defaultValue;
        }
        try {
            return (radix == 10 ? Short.decode(value) : Short.parseShort(value, radix));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取指定的header的short值, 没有返回默认short值
     *
     * @param name header名
     * @param defaultValue 默认short值
     * @return header值
     */
    @ClassDepends
    public short getShortHeader(String name, int defaultValue) {
        String value = getHeader(name);
        if (isEmpty(value)) {
            return (short) defaultValue;
        }
        try {
            return Short.decode(value);
        } catch (NumberFormatException e) {
            return (short) defaultValue;
        }
    }

    /**
     * 获取指定的header的short值, 没有返回默认short值
     *
     * @param radix 进制数
     * @param name header名
     * @param defaultValue 默认short值
     * @return header值
     */
    @ClassDepends
    public short getShortHeader(int radix, String name, int defaultValue) {
        String value = getHeader(name);
        if (isEmpty(value)) {
            return (short) defaultValue;
        }
        try {
            return (radix == 10 ? Short.decode(value) : Short.parseShort(value, radix));
        } catch (NumberFormatException e) {
            return (short) defaultValue;
        }
    }

    /**
     * 获取指定的header的int值, 没有返回默认int值
     *
     * @param name header名
     * @param defaultValue 默认int值
     * @return header值
     */
    @ClassDepends
    public int getIntHeader(String name, int defaultValue) {
        String value = getHeader(name);
        if (isEmpty(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取指定的header的int值, 没有返回默认int值
     *
     * @param radix 进制数
     * @param name header名
     * @param defaultValue 默认int值
     * @return header值
     */
    @ClassDepends
    public int getIntHeader(int radix, String name, int defaultValue) {
        String value = getHeader(name);
        if (isEmpty(value)) {
            return defaultValue;
        }
        try {
            return (radix == 10 ? Integer.decode(value) : Integer.parseInt(value, radix));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取指定的header的long值, 没有返回默认long值
     *
     * @param name header名
     * @param defaultValue 默认long值
     * @return header值
     */
    @ClassDepends
    public long getLongHeader(String name, long defaultValue) {
        String value = getHeader(name);
        if (isEmpty(value)) {
            return defaultValue;
        }
        try {
            return Long.decode(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取指定的header的long值, 没有返回默认long值
     *
     * @param radix 进制数
     * @param name header名
     * @param defaultValue 默认long值
     * @return header值
     */
    @ClassDepends
    public long getLongHeader(int radix, String name, long defaultValue) {
        String value = getHeader(name);
        if (isEmpty(value)) {
            return defaultValue;
        }
        try {
            return (radix == 10 ? Long.decode(value) : Long.parseLong(value, radix));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取指定的header的float值, 没有返回默认float值
     *
     * @param name header名
     * @param defaultValue 默认float值
     * @return header值
     */
    @ClassDepends
    public float getFloatHeader(String name, float defaultValue) {
        String value = getHeader(name);
        if (isEmpty(value)) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取指定的header的double值, 没有返回默认double值
     *
     * @param name header名
     * @param defaultValue 默认double值
     * @return header值
     */
    @ClassDepends
    public double getDoubleHeader(String name, double defaultValue) {
        String value = getHeader(name);
        if (isEmpty(value)) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // ------------------------------------------------------------------------------
    /**
     * 获取请求参数总对象
     *
     * @return AnyValue
     */
    @ClassDepends
    public HttpParameters getParameters() {
        parseBody();
        return params;
    }

    /**
     * 将请求参数转换成String, 字符串格式为: bean1={}&amp;id=13&amp;name=xxx <br>
     * 不会返回null，没有参数返回空字符串
     *
     * @return String
     */
    @ConvertDisabled
    public String getParametersToString() {
        return getParametersToString(null);
    }

    /**
     * 将请求参数转换成String, 字符串格式为: bean1={}&amp;id=13&amp;name=xxx <br>
     * 不会返回null，没有参数返回空字符串
     *
     * @param prefix 拼接前缀， 如果无参数，返回的字符串不会含有拼接前缀
     * @return String
     */
    public String getParametersToString(String prefix) {
        byte[] rbs = queryBytes;
        if (Utility.isEmpty(rbs)) {
            return "";
        }
        Charset charset = this.context.getCharset();
        String str = charset == null ? new String(rbs, StandardCharsets.UTF_8) : new String(rbs, charset);
        return (prefix == null) ? str : (prefix + str);
    }

    /**
     * 获取所有参数名
     *
     * @return 参数名数组
     */
    @ConvertDisabled
    public String[] getParameterNames() {
        parseBody();
        return params.names();
    }

    /**
     * 获取指定的参数值
     *
     * @param name 参数名
     * @return 参数值
     */
    public String getParameter(String name) {
        parseBody();
        return params.get(name);
    }

    /**
     * 获取指定的参数值, 没有返回默认值
     *
     * @param name 参数名
     * @param defaultValue 默认值
     * @return 参数值
     */
    public String getParameter(String name, String defaultValue) {
        parseBody();
        return params.get(name, defaultValue);
    }

    /**
     * 获取指定的参数json值
     *
     * @param <T> 泛型
     * @param type 反序列化的类名
     * @param name 参数名
     * @return 参数值
     */
    public <T> T getJsonParameter(java.lang.reflect.Type type, String name) {
        String v = getParameter(name);
        return v == null || v.isEmpty() ? null : jsonConvert.convertFrom(type, v);
    }

    /**
     * 获取指定的参数json值
     *
     * @param <T> 泛型
     * @param convert JsonConvert对象
     * @param type 反序列化的类名
     * @param name 参数名
     * @return 参数值
     */
    public <T> T getJsonParameter(JsonConvert convert, java.lang.reflect.Type type, String name) {
        String v = getParameter(name);
        return v == null || v.isEmpty() ? null : convert.convertFrom(type, v);
    }

    /**
     * 获取指定的参数boolean值, 没有返回默认boolean值
     *
     * @param name 参数名
     * @param defaultValue 默认boolean值
     * @return 参数值
     */
    public boolean getBooleanParameter(String name, boolean defaultValue) {
        parseBody();
        String value = params.get(name);
        return value == null || value.length() == 0 ? defaultValue : Boolean.parseBoolean(value);
    }

    /**
     * 获取指定的参数short值, 没有返回默认short值
     *
     * @param name 参数名
     * @param defaultValue 默认short值
     * @return 参数值
     */
    public short getShortParameter(String name, short defaultValue) {
        parseBody();
        String value = params.get(name);
        if (value == null || value.length() == 0) {
            return defaultValue;
        }
        try {
            return Short.decode(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取指定的参数short值, 没有返回默认short值
     *
     * @param radix 进制数
     * @param name 参数名
     * @param defaultValue 默认short值
     * @return 参数值
     */
    public short getShortParameter(int radix, String name, short defaultValue) {
        parseBody();
        String value = params.get(name);
        if (value == null || value.length() == 0) {
            return defaultValue;
        }
        try {
            return (radix == 10 ? Short.decode(value) : Short.parseShort(value, radix));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取指定的参数short值, 没有返回默认short值
     *
     * @param name 参数名
     * @param defaultValue 默认short值
     * @return 参数值
     */
    public short getShortParameter(String name, int defaultValue) {
        parseBody();
        String value = params.get(name);
        if (value == null || value.length() == 0) {
            return (short) defaultValue;
        }
        try {
            return Short.decode(value);
        } catch (NumberFormatException e) {
            return (short) defaultValue;
        }
    }

    /**
     * 获取指定的参数int值, 没有返回默认int值
     *
     * @param name 参数名
     * @param defaultValue 默认int值
     * @return 参数值
     */
    public int getIntParameter(String name, int defaultValue) {
        parseBody();
        String value = params.get(name);
        if (value == null || value.length() == 0) {
            return defaultValue;
        }
        try {
            return Integer.decode(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取指定的参数int值, 没有返回默认int值
     *
     * @param radix 进制数
     * @param name 参数名
     * @param defaultValue 默认int值
     * @return 参数值
     */
    public int getIntParameter(int radix, String name, int defaultValue) {
        parseBody();
        String value = params.get(name);
        if (value == null || value.length() == 0) {
            return defaultValue;
        }
        try {
            return (radix == 10 ? Integer.decode(value) : Integer.parseInt(value, radix));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取指定的参数long值, 没有返回默认long值
     *
     * @param name 参数名
     * @param defaultValue 默认long值
     * @return 参数值
     */
    public long getLongParameter(String name, long defaultValue) {
        parseBody();
        String value = params.get(name);
        if (value == null || value.length() == 0) {
            return defaultValue;
        }
        try {
            return Long.decode(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取指定的参数long值, 没有返回默认long值
     *
     * @param radix 进制数
     * @param name 参数名
     * @param defaultValue 默认long值
     * @return 参数值
     */
    public long getLongParameter(int radix, String name, long defaultValue) {
        parseBody();
        String value = params.get(name);
        if (value == null || value.length() == 0) {
            return defaultValue;
        }
        try {
            return (radix == 10 ? Long.decode(value) : Long.parseLong(value, radix));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取指定的参数float值, 没有返回默认float值
     *
     * @param name 参数名
     * @param defaultValue 默认float值
     * @return 参数值
     */
    public float getFloatParameter(String name, float defaultValue) {
        parseBody();
        String value = params.get(name);
        if (value == null || value.length() == 0) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取指定的参数double值, 没有返回默认double值
     *
     * @param name 参数名
     * @param defaultValue 默认double值
     * @return 参数值
     */
    public double getDoubleParameter(String name, double defaultValue) {
        parseBody();
        String value = params.get(name);
        if (value == null || value.length() == 0) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取翻页对象 同 getFlipper("flipper", false, 0);
     *
     * @return Flipper翻页对象
     */
    @ClassDepends
    public org.redkale.source.Flipper getFlipper() {
        return getFlipper(false, 0);
    }

    /**
     * 获取翻页对象 同 getFlipper("flipper", autoCreate, 0);
     *
     * @param autoCreate 无参数时是否创建新Flipper对象
     * @return Flipper翻页对象
     */
    public org.redkale.source.Flipper getFlipper(boolean autoCreate) {
        return getFlipper(autoCreate, 0);
    }

    /**
     * 获取翻页对象 同 getFlipper("flipper", false, maxLimit);
     *
     * @param maxLimit 最大行数， 小于1则值为Flipper.DEFAULT_LIMIT
     * @return Flipper翻页对象
     */
    public org.redkale.source.Flipper getFlipper(int maxLimit) {
        return getFlipper(false, maxLimit);
    }

    /**
     * 获取翻页对象 同 getFlipper("flipper", autoCreate, maxLimit)
     *
     * @param autoCreate 无参数时是否创建新Flipper对象
     * @param maxLimit 最大行数， 小于1则值为Flipper.DEFAULT_LIMIT
     * @return Flipper翻页对象
     */
    public org.redkale.source.Flipper getFlipper(boolean autoCreate, int maxLimit) {
        return getFlipper("flipper", autoCreate, maxLimit);
    }

    /**
     * 获取翻页对象 https://redkale.org/pipes/users/list?flipper={'offset':0,'limit':20, 'sort':'createtime ASC'} <br>
     *
     * @param name Flipper对象的参数名，默认为 "flipper"
     * @param autoCreate 无参数时是否创建新Flipper对象
     * @param maxLimit 最大行数， 小于1则值为Flipper.DEFAULT_LIMIT
     * @return Flipper翻页对象
     */
    public org.redkale.source.Flipper getFlipper(String name, boolean autoCreate, int maxLimit) {
        org.redkale.source.Flipper flipper = getJsonParameter(org.redkale.source.Flipper.class, name);
        if (flipper == null) {
            //            if (maxLimit < 1) maxLimit = org.redkale.source.Flipper.DEFAULT_LIMIT;
            //            String limitstr = getParameter("limit");
            //            if (limitstr != null && !limitstr.isEmpty()) {
            //                String offsetstr = getParameter("offset");
            //                if (offsetstr != null && !offsetstr.isEmpty()) {
            //                    int limit = Integer.parseInt(limitstr);
            //                    int offset = Integer.parseInt(offsetstr);
            //                    String sort = getParameter("sort");
            //                    if (limit > maxLimit) limit = maxLimit;
            //                    flipper = new org.redkale.source.Flipper(limit, offset, sort);
            //                }
            //            }
        } else if (flipper.getLimit() < 1 || (maxLimit > 0 && flipper.getLimit() > maxLimit)) {
            flipper.setLimit(maxLimit);
        }
        if (flipper != null || !autoCreate) {
            return flipper;
        }
        if (maxLimit < 1) {
            maxLimit = org.redkale.source.Flipper.getDefaultLimit();
        }
        return new org.redkale.source.Flipper(maxLimit);
    }
}
