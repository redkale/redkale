/*
 * To change this license headers, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Level;
import org.redkale.convert.*;
import org.redkale.convert.json.JsonConvert;
import org.redkale.net.*;
import org.redkale.util.*;

/**
 * Http请求包 与javax.servlet.http.HttpServletRequest 基本类似。  <br>
 * 同时提供json的解析接口: public Object getJsonParameter(Type type, String name)  <br>
 * Redkale提倡带简单的参数的GET请求采用类似REST风格, 因此提供了 getRequstURIPath 系列接口。  <br>
 * 例如简单的翻页查询   <br>
 *      /pipes/user/query/offset:0/limit:20 <br>
 * 获取页号: int offset = request.getRequstURIPath("offset:", 0);   <br>
 * 获取行数: int limit = request.getRequstURIPath("limit:", 10);  <br>
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class HttpRequest extends Request<HttpContext> {

    private static final boolean pipelineSameHeaders = Boolean.getBoolean("redkale.http.request.pipeline.sameheaders");

    protected static final Serializable CURRUSERID_NIL = new Serializable() {
    };

    protected static final int READ_STATE_ROUTE = 1;

    protected static final int READ_STATE_HEADER = 2;

    protected static final int READ_STATE_BODY = 3;

    protected static final int READ_STATE_END = 4;

    protected static final byte[] EMPTY_BYTES = new byte[0];

    protected static final String KEY_GET = "GET";

    protected static final String KEY_POST = "POST";

    protected static final String KEY_HTTP_1_1 = "HTTP/1.1";

    protected static final String KEY_COOKIE = "Cookie";

    protected static final String KEY_CONNECTION = "Connection";

    protected static final String KEY_CONTENT_TYPE = "Content-Type";

    protected static final String KEY_ACCEPT = "Accept";

    protected static final String KEY_HOST = "Host";

    public static final String SESSIONID_NAME = "JSESSIONID";

    //---------- header 相关参数 开始 ----------
    protected int headerLength;

    protected int headerHalfLen;

    protected String contentType;

    protected long contentLength = -1;

    protected String host;

    @Comment("原始的cookie字符串，解析后值赋给HttpCookie[] cookies")
    protected String cookie;

    protected HttpCookie[] cookies;

    private boolean maybews = false; //是否可能是WebSocket

    protected boolean rpc;

    protected int readState = READ_STATE_ROUTE;

    // @since 2.1.0
    protected Serializable currentUserid = CURRUSERID_NIL;

    protected Supplier<Serializable> currentUserSupplier;

    protected boolean frombody;

    protected ConvertType reqConvertType;

    protected Convert reqConvert;

    protected ConvertType respConvertType;

    protected Convert respConvert;

    protected final Map<String, String> headers = new HashMap<>();
    //---------- header 相关参数 结束 ----------

    @Comment("Method GET/POST/...")
    protected String method;

    protected boolean getmethod;

    protected String protocol;

    protected String requestURI;

    protected byte[] queryBytes;

    protected String newsessionid;

    protected final Map<String, String> params = new HashMap<>();

    protected boolean boundary = false;

    protected int moduleid;

    protected int actionid;

    protected Annotation[] annotations;

    protected String remoteAddr;

    private String lastRequestURIString;

    private byte[] lastRequestURIBytes;

    private final ByteArray array;

    private byte[] headerBytes;

    private boolean headerParsed = false;

    private boolean bodyParsed = false;

    private final String remoteAddrHeader;

    final HttpRpcAuthenticator rpcAuthenticator;

    HttpServlet.ActionEntry actionEntry;  //仅供HttpServlet传递Entry使用

    public HttpRequest(HttpContext context) {
        this(context, new ByteArray());
    }

    protected HttpRequest(HttpContext context, ByteArray array) {
        super(context);
        this.array = array;
        this.remoteAddrHeader = context.remoteAddrHeader;
        this.rpcAuthenticator = context.rpcAuthenticator;
    }

    @SuppressWarnings("OverridableMethodCallInConstructor")
    protected HttpRequest(HttpContext context, HttpSimpleRequest req) {
        super(context);
        this.array = new ByteArray();
        this.remoteAddrHeader = null;
        this.rpcAuthenticator = null;
        if (req != null) initSimpleRequest(req, true);
    }

    protected HttpRequest initSimpleRequest(HttpSimpleRequest req, boolean needPath) {
        if (req != null) {
            this.rpc = req.rpc;
            if (req.getBody() != null) this.array.put(req.getBody());
            if (req.getHeaders() != null) this.headers.putAll(req.getHeaders());
            this.frombody = req.isFrombody();
            this.reqConvertType = req.getReqConvertType();
            this.reqConvert = req.getReqConvertType() == null ? null : ConvertFactory.findConvert(req.getReqConvertType());
            this.respConvertType = req.getRespConvertType();
            this.respConvert = req.getRespConvertType() == null ? null : ConvertFactory.findConvert(req.getRespConvertType());
            if (req.getParams() != null) this.params.putAll(req.getParams());
            this.hashid = req.getHashid();
            if (req.getCurrentUserid() != null) this.currentUserid = req.getCurrentUserid();
            this.contentType = req.getContentType();
            this.remoteAddr = req.getRemoteAddr();
            if (needPath) {
                this.requestURI = (req.getPath() == null || req.getPath().isEmpty()) ? req.getRequestURI() : (req.getPath() + req.getRequestURI());
            } else {
                this.requestURI = req.getRequestURI();
            }
            this.method = "POST";
            if (req.getSessionid() != null && !req.getSessionid().isEmpty()) {
                this.cookies = new HttpCookie[]{new HttpCookie(SESSIONID_NAME, req.getSessionid())};
            }
        }
        return this;
    }

    public HttpSimpleRequest createSimpleRequest(String prefix) {
        HttpSimpleRequest req = new HttpSimpleRequest();
        req.setBody(array.length() == 0 ? null : array.getBytes());
        if (!getHeaders().isEmpty()) {
            if (headers.containsKey(Rest.REST_HEADER_RPC)
                || headers.containsKey(Rest.REST_HEADER_CURRUSERID_NAME)) { //外部request不能包含RPC的header信息
                req.setHeaders(new HashMap<>(headers));
                req.removeHeader(Rest.REST_HEADER_RPC);
                req.removeHeader(Rest.REST_HEADER_CURRUSERID_NAME);
            } else {
                req.setHeaders(headers);
            }
        }
        parseBody();
        req.setParams(params.isEmpty() ? null : params);
        req.setRemoteAddr(getRemoteAddr());
        req.setContentType(getContentType());
        req.setPath(prefix);
        String uri = this.requestURI;
        if (prefix != null && !prefix.isEmpty() && uri.startsWith(prefix)) {
            uri = uri.substring(prefix.length());
        }
        req.setHashid(this.hashid);
        req.setRequestURI(uri);
        req.setSessionid(getSessionid(false));
        req.setRpc(this.rpc);
        return req;
    }

    protected boolean isWebSocket() {
        return maybews && "Upgrade".equalsIgnoreCase(getHeader("Connection")) && "GET".equalsIgnoreCase(method);
    }

    protected void setPipelineOver(boolean pipelineOver) {
        this.pipelineOver = pipelineOver;
    }

    protected void setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

    protected boolean isKeepAlive() {
        return this.keepAlive;
    }

    protected AsyncConnection getChannel() {
        return this.channel;
    }

    protected int getPipelineIndex() {
        return this.pipelineIndex;
    }

    protected int getPipelineCount() {
        return this.pipelineCount;
    }

    protected ConvertType getRespConvertType() {
        return this.respConvertType;
    }

    protected Convert getRespConvert() {
        return this.respConvert == null ? this.jsonConvert : this.respConvert;
    }

    @Override
    protected int readHeader(final ByteBuffer buffer, final Request last) {
        ByteArray bytes = array;
        if (this.readState == READ_STATE_ROUTE) {
            int rs = readMethodLine(buffer);
            if (rs != 0) return rs;
            this.readState = READ_STATE_HEADER;
        }
        if (this.readState == READ_STATE_HEADER) {
            if (last != null && ((HttpRequest) last).headerLength > 0) {
                final HttpRequest httplast = (HttpRequest) last;
                int bufremain = buffer.remaining();
                int remainhalf = httplast.headerLength - this.headerHalfLen;
                if (remainhalf > bufremain) {
                    bytes.put(buffer);
                    this.headerHalfLen += bufremain;
                    buffer.clear();
                    return 1;
                }
                buffer.position(buffer.position() + remainhalf);
                this.contentType = httplast.contentType;
                this.contentLength = httplast.contentLength;
                this.host = httplast.host;
                this.cookie = httplast.cookie;
                this.cookies = httplast.cookies;
                this.keepAlive = httplast.keepAlive;
                this.maybews = httplast.maybews;
                this.rpc = httplast.rpc;
                this.hashid = httplast.hashid;
                this.currentUserid = httplast.currentUserid;
                this.frombody = httplast.frombody;
                this.reqConvertType = httplast.reqConvertType;
                this.reqConvert = httplast.reqConvert;
                this.respConvertType = httplast.respConvertType;
                this.respConvert = httplast.respConvert;
                this.headerLength = httplast.headerLength;
                this.headerHalfLen = httplast.headerLength;
                this.headerBytes = httplast.headerBytes;
                this.headerParsed = httplast.headerParsed;
                this.headers.putAll(httplast.headers);
            } else if (context.lazyHeaders && getmethod) { //非GET必须要读header，会有Content-Length
                int rs = loadHeaderBytes(buffer);
                if (rs != 0) return rs;
                this.headerParsed = false;
            } else {
                int startpos = buffer.position();
                int rs = readHeaderLines(buffer, bytes);
                if (rs != 0) {
                    this.headerHalfLen = bytes.length();
                    return rs;
                }
                this.headerParsed = true;
                this.headerLength = buffer.position() - startpos + this.headerHalfLen;
                this.headerHalfLen = this.headerLength;
            }
            bytes.clear();
            this.readState = READ_STATE_BODY;
        }
        if (this.contentType != null && this.contentType.contains("boundary=")) this.boundary = true;
        if (this.boundary) this.keepAlive = false; //文件上传必须设置keepAlive为false，因为文件过大时用户不一定会skip掉多余的数据
        if (this.readState == READ_STATE_BODY) {
            if (this.contentLength > 0 && (this.contentType == null || !this.boundary)) {
                if (this.contentLength > context.getMaxbody()) return -1;
                bytes.put(buffer, Math.min((int) this.contentLength, buffer.remaining()));
                int lr = (int) this.contentLength - bytes.length();
                if (lr == 0) {
                    this.readState = READ_STATE_END;
                    if (bytes.isEmpty()) this.bodyParsed = true; //no body data
                }
                return lr > 0 ? lr : 0;
            }
            if (buffer.hasRemaining() && (this.boundary || !this.keepAlive)) bytes.put(buffer, buffer.remaining()); //文件上传、HTTP1.0或Connection:close
            this.readState = READ_STATE_END;
            if (bytes.isEmpty()) this.bodyParsed = true;  //no body data
        }
        //暂不考虑是keep-alive且存在body却没有指定Content-Length的情况
        return 0;
    }

    private int loadHeaderBytes(final ByteBuffer buffer) {
        ByteArray bytes = array;
        int remain = buffer.remaining();
        byte b1, b2, b3, b4;
        for (;;) {
            if (remain-- < 4) { //bytes不存放\r\n\r\n这4个字节
                bytes.put(buffer);
                buffer.clear();
                if (bytes.length() > 0) {
                    byte rn1 = 0, rn2 = 0, rn3 = 0;
                    byte b = bytes.getLastByte();
                    if (b == '\r' || b == '\n') {
                        rn3 = b;
                        bytes.backCount();
                        if (bytes.length() > 0) {
                            b = bytes.getLastByte();
                            if (b == '\r' || b == '\n') {
                                rn2 = b;
                                bytes.backCount();
                                if (bytes.length() > 0) {
                                    b = bytes.getLastByte();
                                    if (b == '\r' || b == '\n') {
                                        rn1 = b;
                                        bytes.backCount();
                                    }
                                }
                            }
                        }
                    }
                    if (rn1 != 0) buffer.put(rn1);
                    if (rn2 != 0) buffer.put(rn2);
                    if (rn3 != 0) buffer.put(rn3);
                }
                return 1;
            }
            b1 = buffer.get();
            bytes.put(b1);
            if (b1 == '\r') {
                remain--;
                b2 = buffer.get();
                bytes.put(b2);
                if (b2 == '\n') {
                    remain--;
                    b3 = buffer.get();
                    bytes.put(b3);
                    if (b3 == '\r') {
                        remain--;
                        b4 = buffer.get();
                        bytes.put(b4);
                        if (b4 == '\n') {
                            this.headerBytes = Utility.append(this.headerBytes, bytes.content(), 0, bytes.length());
                            this.headerLength = this.headerBytes.length;
                            this.headerHalfLen = this.headerLength;
                            bytes.clear();
                            return 0;
                        }
                    }
                }
            }
        }
    }

//    @Override
//    protected int readBody(ByteBuffer buffer, int length) {
//        int len = buffer.remaining();
//        array.put(buffer, len);
//        return len;
//    }
    //解析 GET /xxx HTTP/1.1
    private int readMethodLine(final ByteBuffer buffer) {
        Charset charset = this.context.getCharset();
        int remain = buffer.remaining();
        int size;
        ByteArray bytes = array;
        //读method
        if (this.method == null) {
            for (;;) {
                if (remain-- < 1) {
                    buffer.clear();
                    return 1;
                }
                byte b = buffer.get();
                if (b == ' ') break;
                bytes.put(b);
            }
            size = bytes.length();
            if (size == 3 && bytes.get(0) == 'G' && bytes.get(1) == 'E' && bytes.get(2) == 'T') {
                this.method = KEY_GET;
                this.getmethod = true;
            } else if (size == 4 && bytes.get(0) == 'P' && bytes.get(1) == 'O' && bytes.get(2) == 'S' && bytes.get(3) == 'T') {
                this.method = KEY_POST;
                this.getmethod = false;
            } else {
                this.method = bytes.toString(charset);
                this.getmethod = false;
            }
            bytes.clear();
        }
        //读uri
        if (this.requestURI == null) {
            int qst = -1;//?的位置
            boolean decodeable = false;
            for (;;) {
                if (remain-- < 1) {
                    buffer.clear();
                    return 1;
                }
                byte b = buffer.get();
                if (b == ' ') break;
                if (b == '?' && qst < 0) {
                    qst = bytes.length();
                } else if (!decodeable && (b == '+' || b == '%')) {
                    decodeable = true;
                }
                bytes.put(b);
            }
            size = bytes.length();
            if (qst > 0) {
                this.requestURI = decodeable ? toDecodeString(bytes, 0, qst, charset) : bytes.toString(0, qst, charset);
                this.queryBytes = bytes.getBytes(qst + 1, size - qst - 1);
                this.lastRequestURIString = null;
                this.lastRequestURIBytes = null;
                try {
                    addParameter(bytes, qst + 1, size - qst - 1);
                } catch (Exception e) {
                    this.context.getLogger().log(Level.WARNING, "HttpRequest.addParameter error: " + bytes.toString(), e);
                }
            } else {
                if (decodeable) {
                    this.requestURI = toDecodeString(bytes, 0, bytes.length(), charset);
                    this.lastRequestURIString = null;
                    this.lastRequestURIBytes = null;
                } else if (context.lazyHeaders) {
                    byte[] lastURIBytes = lastRequestURIBytes;
                    if (lastURIBytes != null && lastURIBytes.length == size && bytes.equal(lastURIBytes)) {
                        this.requestURI = this.lastRequestURIString;
                    } else {
                        this.requestURI = bytes.toString(charset);
                        this.lastRequestURIString = this.requestURI;
                        this.lastRequestURIBytes = bytes.getBytes();
                    }
                } else {
                    this.requestURI = bytes.toString(charset);
                }
                this.queryBytes = EMPTY_BYTES;
            }
            bytes.clear();
        }
        //读protocol
        for (;;) {
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
                    buffer.put((byte) '\r');
                    return 1;
                }
                if (buffer.get() != '\n') return -1;
                break;
            }
            bytes.put(b);
        }
        size = bytes.length();
        if (size == 8 && bytes.get(0) == 'H' && bytes.get(5) == '1' && bytes.get(7) == '1') {
            this.protocol = KEY_HTTP_1_1;
        } else {
            this.protocol = bytes.toString(charset);
        }
        bytes.clear();
        return 0;
    }

    //解析Header Connection: keep-alive
    private int readHeaderLines(final ByteBuffer buffer, ByteArray bytes) {
        Charset charset = this.context.getCharset();
        int remain = buffer.remaining();
        for (;;) {
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
            if (b1 == '\r' && b2 == '\n') return 0;
            bytes.put(b1, b2);
            for (;;) {  // name
                if (remain-- < 1) {
                    buffer.clear();
                    buffer.put(bytes.content(), 0, bytes.length());
                    return 1;
                }
                byte b = buffer.get();
                if (b == ':') break;
                bytes.put(b);
            }
            String name = parseHeaderName(bytes, charset);
            bytes.clear();
            boolean first = true;
            int space = 0;
            for (;;) {  // value
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
                    if (buffer.get() != '\n') return -1;
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
            int vallen = bytes.length();
            switch (name) {
                case "Content-Type":
                case "content-type":
                    value = bytes.toString(charset);
                    this.contentType = value;
                    break;
                case "Content-Length":
                case "content-length":
                    value = bytes.toString(charset);
                    this.contentLength = Long.decode(value);
                    break;
                case "Host":
                case "host":
                    value = bytes.toString(charset);
                    this.host = value;
                    break;
                case "Cookie":
                case "cookie":
                    value = bytes.toString(charset);
                    if (this.cookie == null || this.cookie.isEmpty()) {
                        this.cookie = value;
                    } else {
                        this.cookie += ";" + value;
                    }
                    break;
                case "Connection":
                case "connection":
                    if (vallen > 0) {
                        if (bytes.get(0) == 'c' && vallen == 5
                            && bytes.get(1) == 'l' && bytes.get(2) == 'o'
                            && bytes.get(3) == 's' && bytes.get(4) == 'e') {
                            value = "close";
                            this.setKeepAlive(false);
                        } else if (bytes.get(0) == 'k' && vallen == 10
                            && bytes.get(1) == 'e' && bytes.get(2) == 'e'
                            && bytes.get(3) == 'p' && bytes.get(4) == '-'
                            && bytes.get(5) == 'a' && bytes.get(6) == 'l'
                            && bytes.get(7) == 'i' && bytes.get(8) == 'v'
                            && bytes.get(9) == 'e') {
                            value = "keep-alive";
                            //if (context.getAliveTimeoutSeconds() >= 0) {
                            this.setKeepAlive(true);
                            //}
                        } else {
                            value = bytes.toString(charset);
                            this.setKeepAlive(true);
                        }
                    } else {
                        value = "";
                    }
                    headers.put("Connection", value);
                    break;
                case "Upgrade":
                case "upgrade":
                    value = bytes.toString(charset);
                    this.maybews = "websocket".equalsIgnoreCase(value);
                    headers.put("Upgrade", value);
                    break;
                case "user-agent":
                    value = bytes.toString(charset);
                    headers.put("User-Agent", value);
                    break;
                case Rest.REST_HEADER_RPC:
                    value = bytes.toString(charset);
                    this.rpc = "true".equalsIgnoreCase(value);
                    headers.put(name, value);
                    break;
                case Rest.REST_HEADER_CURRUSERID_NAME:
                    value = bytes.toString(charset);
                    this.hashid = value.hashCode();
                    this.currentUserid = value;
                    headers.put(name, value);
                    break;
                case Rest.REST_HEADER_PARAM_FROM_BODY:
                    value = bytes.toString(charset);
                    this.frombody = "true".equalsIgnoreCase(value);
                    headers.put(name, value);
                    break;
                case Rest.REST_HEADER_REQ_CONVERT_TYPE:
                    value = bytes.toString(charset);
                    reqConvertType = ConvertType.valueOf(value);
                    reqConvert = ConvertFactory.findConvert(reqConvertType);
                    headers.put(name, value);
                    break;
                case Rest.REST_HEADER_RESP_CONVERT_TYPE:
                    value = bytes.toString(charset);
                    respConvertType = ConvertType.valueOf(value);
                    respConvert = ConvertFactory.findConvert(respConvertType);
                    headers.put(name, value);
                    break;
                default:
                    value = bytes.toString(charset);
                    headers.put(name, value);
            }
        }
    }

    private void parseHeader() {
        if (headerParsed) return;
        headerParsed = true;
        if (headerBytes == null) return;
        if (array.isEmpty()) {
            readHeaderLines(ByteBuffer.wrap(headerBytes), array);
            array.clear();
        } else { //array存有body数据
            readHeaderLines(ByteBuffer.wrap(headerBytes), new ByteArray());
        }
    }

    static String parseHeaderName(ByteArray bytes, Charset charset) {
        final int size = bytes.length();
        final byte[] bs = bytes.content();
        final byte first = bs[0];
        if (first == 'H' && size == 4) {  //Host
            if (bs[1] == 'o' && bs[2] == 's' && bs[3] == 't') return KEY_HOST;
        } else if (first == 'A' && size == 6) {  //Accept
            if (bs[1] == 'c' && bs[2] == 'c' && bs[3] == 'e'
                && bs[4] == 'p' && bs[5] == 't') return KEY_ACCEPT;
        } else if (first == 'C') {
            if (size == 10) { //Connection
                if (bs[1] == 'o' && bs[2] == 'n' && bs[3] == 'n'
                    && bs[4] == 'e' && bs[5] == 'c' && bs[6] == 't'
                    && bs[7] == 'i' && bs[8] == 'o' && bs[9] == 'n') return KEY_CONNECTION;
            } else if (size == 12) { //Content-Type
                if (bs[1] == 'o' && bs[2] == 'n' && bs[3] == 't'
                    && bs[4] == 'e' && bs[5] == 'n' && bs[6] == 't'
                    && bs[7] == '-' && bs[8] == 'T' && bs[9] == 'y'
                    && bs[10] == 'p' && bs[11] == 'e') return KEY_CONTENT_TYPE;
            } else if (size == 6) { //Cookie
                if (bs[1] == 'o' && bs[2] == 'o' && bs[3] == 'k'
                    && bs[4] == 'i' && bs[5] == 'e') return KEY_COOKIE;
            }
        }
        return bytes.toString(charset);
    }

    @Override
    protected HttpRequest copyHeader() {
        if (!pipelineSameHeaders || !context.lazyHeaders) return null;
        HttpRequest req = new HttpRequest(context, this.array);
        req.headerLength = this.headerLength;
        req.headerBytes = this.headerBytes;
        req.headerParsed = this.headerParsed;
        req.contentType = this.contentType;
        req.contentLength = this.contentLength;
        req.host = this.host;
        req.cookie = this.cookie;
        req.cookies = this.cookies;
        req.keepAlive = this.keepAlive;
        req.maybews = this.maybews;
        req.rpc = this.rpc;
        req.hashid = this.hashid;
        req.currentUserid = this.currentUserid;
        req.currentUserSupplier = this.currentUserSupplier;
        req.frombody = this.frombody;
        req.reqConvertType = this.reqConvertType;
        req.reqConvert = this.reqConvert;
        req.respConvert = this.respConvert;
        req.respConvertType = this.respConvertType;
        req.headers.putAll(this.headers);
        return req;
    }

    @Override
    protected void prepare() {
        this.keepAlive = true; //默认HTTP/1.1
    }

    @Override
    protected void recycle() {
        //header    
        this.headerLength = 0;
        this.headerHalfLen = 0;
        this.headerBytes = null;
        this.headerParsed = false;
        this.contentType = null;
        this.contentLength = -1;
        this.host = null;
        this.cookie = null;
        this.cookies = null;
        this.maybews = false;
        this.rpc = false;
        this.readState = READ_STATE_ROUTE;
        this.currentUserid = CURRUSERID_NIL;
        this.currentUserSupplier = null;
        this.frombody = false;
        this.reqConvertType = null;
        this.reqConvert = null;
        this.respConvert = jsonConvert;
        this.respConvertType = null;
        this.headers.clear();
        //其他
        this.newsessionid = null;
        this.method = null;
        this.getmethod = false;
        this.protocol = null;
        this.requestURI = null;
        this.queryBytes = null;
        this.boundary = false;
        this.bodyParsed = false;
        this.moduleid = 0;
        this.actionid = 0;
        this.annotations = null;
        this.remoteAddr = null;
        this.params.clear();
        this.array.clear();
        //内部
        this.actionEntry = null;
        super.recycle();
    }

    protected void skipBodyParse() {
        this.bodyParsed = true;
    }

    private void parseBody() {
        if (this.boundary || bodyParsed) return;
        bodyParsed = true;
        if (this.contentType != null && this.contentType.toLowerCase().contains("x-www-form-urlencoded")) {
            addParameter(array, 0, array.length());
        }
    }

    private void addParameter(final ByteArray array, final int offset, final int len) {
        if (len < 1) return;
        Charset charset = this.context.getCharset();
        int limit = offset + len;
        int keypos = array.find(offset, limit, '=');
        int valpos = array.find(offset, limit, '&');
        if (keypos <= 0 || (valpos >= 0 && valpos < keypos)) {
            if (valpos > 0) addParameter(array, valpos + 1, limit - valpos - 1);
            return;
        }
        String name = toDecodeString(array, offset, keypos - offset, charset);
        if (!name.isEmpty() && name.charAt(0) == '<') return; //内容可能是xml格式; 如: <?xml version="1.0"
        ++keypos;
        String value = toDecodeString(array, keypos, (valpos < 0) ? (limit - keypos) : (valpos - keypos), charset);
        this.params.put(name, value);
        if (valpos >= 0) {
            addParameter(array, valpos + 1, limit - valpos - 1);
        }
    }

    protected HttpRequest setMethod(String method) {
        this.method = method;
        this.getmethod = "GET".equalsIgnoreCase(method);
        return this;
    }

    protected HttpRequest setRequestURI(String requestURI) {
        this.requestURI = requestURI;
        return this;
    }

    protected HttpRequest setRemoteAddr(String remoteAddr) {
        this.remoteAddr = remoteAddr;
        return this;
    }

    protected HttpRequest setParameter(String name, String value) {
        this.params.put(name, value);
        return this;
    }

    protected HttpRequest setHeader(String name, String value) {
        this.headers.put(name, value);
        return this;
    }

    protected static String toDecodeString(ByteArray array, int offset, int len, final Charset charset) {
        byte[] content = array.content();
        int start = offset;
        final int end = offset + len;
        boolean flag = false; //是否需要转义
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
        if (charset == null) return new String(bs, start, len, StandardCharsets.UTF_8);
        return new String(bs, start, len, charset);
    }

    private static int hexBit(byte b) {
        if ('0' <= b && '9' >= b) return b - '0';
        if ('a' <= b && 'z' >= b) return b - 'a' + 10;
        if ('A' <= b && 'Z' >= b) return b - 'A' + 10;
        return b;
    }

    @Override
    protected <T> T setProperty(String name, T value) {
        return super.setProperty(name, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <T> T getProperty(String name) {
        return super.getProperty(name);
    }

    @Override
    protected <T> T removeProperty(String name) {
        return super.removeProperty(name);
    }

    /**
     * 设置当前用户ID, 通常在HttpServlet.preExecute方法里设置currentUserid <br>
     * 数据类型只能是int、long、String、JavaBean
     *
     * @param <T>    泛型
     * @param userid 用户ID
     *
     * @return HttpRequest
     *
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
     *
     * @since 2.4.0
     */
    @SuppressWarnings("unchecked")
    public int currentIntUserid() {
        if (currentUserid == CURRUSERID_NIL || currentUserid == null) return 0;
        if (this.currentUserid instanceof Number) return ((Number) this.currentUserid).intValue();
        String uid = this.currentUserid.toString();
        return uid.isEmpty() ? 0 : Integer.parseInt(uid);
    }

    /**
     * 获取当前用户ID的long值<br>
     *
     * @return 用户ID
     *
     * @since 2.7.0
     */
    @SuppressWarnings("unchecked")
    public long currentLongUserid() {
        if (currentUserid == CURRUSERID_NIL || currentUserid == null) return 0L;
        if (this.currentUserid instanceof Number) return ((Number) this.currentUserid).longValue();
        String uid = this.currentUserid.toString();
        return uid.isEmpty() ? 0L : Long.parseLong(uid);
    }

    /**
     * 获取当前用户ID<br>
     *
     * @param <T>  数据类型只能是int、long、String、JavaBean
     * @param type 类型
     *
     * @return 用户ID
     *
     * @since 2.1.0
     */
    @SuppressWarnings("unchecked")
    public <T extends Serializable> T currentUserid(Class<T> type) {
        if (currentUserid == CURRUSERID_NIL || currentUserid == null) {
            if (type == int.class || type == Integer.class) return (T) (Integer) (int) 0;
            if (type == long.class || type == Long.class) return (T) (Long) (long) 0;
            return null;
        }
        if (type == int.class || type == Integer.class) {
            if (this.currentUserid instanceof Number) return (T) (Integer) ((Number) this.currentUserid).intValue();
            String uid = this.currentUserid.toString();
            return (T) (Integer) (uid.isEmpty() ? 0 : Integer.parseInt(uid));
        }
        if (type == long.class || type == Long.class) {
            if (this.currentUserid instanceof Number) return (T) (Long) ((Number) this.currentUserid).longValue();
            String uid = this.currentUserid.toString();
            return (T) (Long) (uid.isEmpty() ? 0L : Long.parseLong(uid));
        }
        if (type == String.class) return (T) this.currentUserid.toString();
        if (this.currentUserid instanceof CharSequence) return JsonConvert.root().convertFrom(type, this.currentUserid.toString());
        return (T) this.currentUserid;
    }

    /**
     * 建议使用 setCurrentUserid, 通过userid从Service或缓存中获取用户信息<br>
     * 设置当前用户信息, 通常在HttpServlet.preExecute方法里设置currentUser <br>
     * 数据类型由&#64;HttpUserType指定
     *
     * @param supplier currentUser对象方法
     *
     * @since 2.4.0
     *
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
     *
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
     * 获取当前操作Method上的注解集合
     *
     * @return Annotation[]
     */
    @ConvertDisabled
    public Annotation[] getAnnotations() {
        if (this.annotations == null) return new Annotation[0];
        Annotation[] newanns = new Annotation[this.annotations.length];
        System.arraycopy(this.annotations, 0, newanns, 0, newanns.length);
        return newanns;
    }

    /**
     * 获取当前操作Method上的注解
     *
     * @param <T>             注解泛型
     * @param annotationClass 注解类型
     *
     * @return Annotation
     */
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        if (this.annotations == null) return null;
        for (Annotation ann : this.annotations) {
            if (ann.getClass() == annotationClass) return (T) ann;
        }
        return null;
    }

    /**
     * 获取当前操作Method上的注解集合
     *
     * @param <T>             注解泛型
     * @param annotationClass 注解类型
     *
     * @return Annotation[]
     */
    public <T extends Annotation> T[] getAnnotationsByType(Class<T> annotationClass) {
        if (this.annotations == null) return (T[]) Array.newInstance(annotationClass, 0);
        T[] news = (T[]) Array.newInstance(annotationClass, this.annotations.length);
        int index = 0;
        for (Annotation ann : this.annotations) {
            if (ann.getClass() == annotationClass) {
                news[index++] = (T) ann;
            }
        }
        if (index < 1) return (T[]) Array.newInstance(annotationClass, 0);
        return Arrays.copyOf(news, index);
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
     * 获取客户端地址IP, 与getRemoteAddress() 的区别在于：本方法优先取header中指定为RemoteAddress名的值，没有则返回getRemoteAddress()的getHostAddress()。<br>
     * 本方法适用于服务前端有如nginx的代理服务器进行中转，通过 getRemoteAddress()是获取不到客户端的真实IP。
     *
     * @return 地址
     */
    public String getRemoteAddr() {
        if (this.remoteAddr != null) return this.remoteAddr;
        parseHeader();
        if (remoteAddrHeader != null) {
            String val = getHeader(remoteAddrHeader);
            if (val != null) {
                this.remoteAddr = val;
                return val;
            }
        }
        SocketAddress addr = getRemoteAddress();
        if (addr == null) return "";
        if (addr instanceof InetSocketAddress) {
            this.remoteAddr = ((InetSocketAddress) addr).getAddress().getHostAddress();
            return this.remoteAddr;
        }
        this.remoteAddr = String.valueOf(addr);
        return this.remoteAddr;
    }

    /**
     * 获取请求内容指定的编码字符串
     *
     * @param charset 编码
     *
     * @return 内容
     */
    public String getBody(final Charset charset) {
        return charset == null ? array.toString() : array.toString(charset);
    }

    /**
     * 获取请求内容的UTF-8编码字符串
     *
     * @return 内容
     */
    @ConvertDisabled
    public String getBodyUTF8() {
        return array.toString(StandardCharsets.UTF_8);
    }

    /**
     * 获取请求内容的JavaBean对象
     *
     * @param <T>  泛型
     * @param type 类型
     *
     * @return 内容
     */
    public <T> T getBodyJson(java.lang.reflect.Type type) {
        if (array == null || array.isEmpty()) return null;
        Convert convert = this.reqConvert;
        if (convert == null) convert = context.getJsonConvert();
        if (type == byte[].class) return (T) array.getBytes();
        return (T) convert.convertFrom(type, array.content());
    }

    /**
     * 获取请求内容的JavaBean对象
     *
     * @param <T>     泛型
     * @param convert Convert
     * @param type    类型
     *
     * @return 内容
     */
    public <T> T getBodyJson(Convert convert, java.lang.reflect.Type type) {
        if (array.isEmpty()) return null;
        if (type == byte[].class) return (T) array.getBytes();
        return (T) convert.convertFrom(type, array.content());
    }

    /**
     * 获取请求内容的byte[]
     *
     * @return 内容
     */
    public byte[] getBody() {
        return array.length() == 0 ? null : array.getBytes();
    }

    /**
     * 直接获取body对象
     *
     * @return body对象
     */
    @ConvertDisabled
    protected ByteArray getDirectBody() {
        return array;
    }

    @Override
    public String toString() {
        parseBody();
        return this.getClass().getSimpleName() + "{\r\n    method: " + this.method + ", \r\n    requestURI: " + this.requestURI
            + (this.frombody ? (", \r\n    frombody: " + this.frombody) : "")
            + (this.reqConvertType != null ? (", \r\n    reqConvertType: " + this.reqConvertType) : "")
            + (this.respConvertType != null ? (", \r\n    respConvertType: " + this.respConvertType) : "")
            + ", \r\n    currentUserid: " + (this.currentUserid == CURRUSERID_NIL ? null : this.currentUserid) + ", \r\n    remoteAddr: " + this.getRemoteAddr()
            + ", \r\n    cookies: " + this.cookie + ", \r\n    contentType: " + this.contentType
            + ", \r\n    protocol: " + this.protocol + ", \r\n    host: " + this.host
            + ", \r\n    contentLength: " + this.contentLength + ", \r\n    bodyLength: " + this.array.length()
            + (this.boundary || this.array.isEmpty() ? "" : (", \r\n    bodyContent: " + (this.respConvertType == null || this.respConvertType == ConvertType.JSON ? this.getBodyUTF8() : Arrays.toString(getBody()))))
            + ", \r\n    params: " + toMapString(this.params, 4) + ", \r\n    header: " + toMapString(this.headers, 4) + "\r\n}"; //this.headers.toString(4)
    }

    private static CharSequence toMapString(Map<String, String> map, int indent) {
        final String space = " ".repeat(indent);
        StringBuilder sb = new StringBuilder();
        sb.append("{\r\n");
        for (Map.Entry en : map.entrySet()) {
            sb.append(space).append("    '").append(en.getKey()).append("': '").append(en.getValue()).append("',\r\n");
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
        return new MultiContext(context.getCharset(), this.getContentType(), this.params,
            new BufferedInputStream(in, Math.max(array.length(), 8192)) {
            {
                array.copyTo(this.buf);
                this.count = array.length();
            }
        }, null);
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
     *
     * @throws IOException IO异常
     */
    @ConvertDisabled
    public final Iterable<MultiPart> multiParts() throws IOException {
        return getMultiContext().parts();
    }

    /**
     * 获取sessionid
     *
     * @param create 无sessionid是否自动创建
     *
     * @return sessionid
     */
    @ConvertDisabled
    public String getSessionid(boolean create) {
        String sessionid = getCookie(SESSIONID_NAME, null);
        if (create && (sessionid == null || sessionid.isEmpty())) {
            sessionid = context.createSessionid();
            this.newsessionid = sessionid;
        }
        return sessionid;
    }

    /**
     * 更新sessionid
     *
     * @return 新的sessionid值
     */
    public String changeSessionid() {
        this.newsessionid = context.createSessionid();
        return newsessionid;
    }

    /**
     * 指定值更新sessionid
     *
     * @param newsessionid 新sessionid值
     *
     * @return 新的sessionid值
     */
    public String changeSessionid(String newsessionid) {
        this.newsessionid = newsessionid == null ? context.createSessionid() : newsessionid.trim();
        return newsessionid;
    }

    /**
     * 使sessionid失效
     */
    public void invalidateSession() {
        this.newsessionid = ""; //为空表示删除sessionid
    }

    /**
     * 获取所有Cookie对象
     *
     * @return cookie对象数组
     */
    public HttpCookie[] getCookies() {
        parseHeader();
        if (this.cookies == null) this.cookies = parseCookies(this.cookie);
        return this.cookies.length == 0 ? null : this.cookies;
    }

    /**
     * 获取Cookie值
     *
     * @param name cookie名
     *
     * @return cookie值
     */
    public String getCookie(String name) {
        return getCookie(name, null);
    }

    /**
     * 获取Cookie值， 没有返回默认值
     *
     * @param name    cookie名
     * @param dfvalue 默认cookie值
     *
     * @return cookie值
     */
    public String getCookie(String name, String dfvalue) {
        HttpCookie[] cs = getCookies();
        if (cs == null) return dfvalue;
        for (HttpCookie c : cs) {
            if (name.equals(c.getName())) return c.getValue();
        }
        return dfvalue;
    }

    private static HttpCookie[] parseCookies(String cookiestr) {
        if (cookiestr == null || cookiestr.isEmpty()) return new HttpCookie[0];
        String str = cookiestr.replaceAll("(^;)|(;$)", "").replaceAll(";+", ";");
        if (str.isEmpty()) return new HttpCookie[0];
        String[] strs = str.split(";");
        HttpCookie[] cookies = new HttpCookie[strs.length];
        for (int i = 0; i < strs.length; i++) {
            String s = strs[i];
            int pos = s.indexOf('=');
            String v = (pos < 0 ? "" : s.substring(pos + 1));
            if (v.indexOf('"') == 0 && v.lastIndexOf('"') == v.length() - 1) v = v.substring(1, v.length() - 1);
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
        return contentType;
    }

    /**
     * 获取请求内容的长度, 为-1表示内容长度不确定
     *
     * @return 内容长度
     */
    public long getContentLength() {
        return contentLength;
    }

    /**
     * 获取Host的Header值
     *
     * @return Host
     */
    public String getHost() {
        return host;
    }

    /**
     * 获取请求的URL
     *
     * @return 请求的URL
     */
    public String getRequestURI() {
        return requestURI;
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
     * 截取getRequestURI最后的一个/后面的部分
     *
     * @return String
     */
    @ConvertDisabled
    public String getRequstURILastPath() {
        if (requestURI == null) return "";
        return requestURI.substring(requestURI.lastIndexOf('/') + 1);
    }

    /**
     * 获取请求URL最后的一个/后面的部分的short值   <br>
     * 例如请求URL /pipes/user/query/2   <br>
     * 获取type参数: short type = request.getRequstURILastPath((short)0); //type = 2
     *
     * @param defvalue 默认short值
     *
     * @return short值
     */
    public short getRequstURILastPath(short defvalue) {
        String val = getRequstURILastPath();
        if (val.isEmpty()) return defvalue;
        try {
            return Short.parseShort(val);
        } catch (NumberFormatException e) {
            return defvalue;
        }
    }

    /**
     * 获取请求URL最后的一个/后面的部分的short值   <br>
     * 例如请求URL /pipes/user/query/2   <br>
     * 获取type参数: short type = request.getRequstURILastPath(16, (short)0); //type = 2
     *
     * @param radix    进制数
     * @param defvalue 默认short值
     *
     * @return short值
     */
    public short getRequstURILastPath(int radix, short defvalue) {
        String val = getRequstURILastPath();
        if (val.isEmpty()) return defvalue;
        try {
            return Short.parseShort(val, radix);
        } catch (NumberFormatException e) {
            return defvalue;
        }
    }

    /**
     * 获取请求URL最后的一个/后面的部分的int值   <br>
     * 例如请求URL /pipes/user/query/2   <br>
     * 获取type参数: int type = request.getRequstURILastPath(0); //type = 2
     *
     * @param defvalue 默认int值
     *
     * @return int值
     */
    public int getRequstURILastPath(int defvalue) {
        String val = getRequstURILastPath();
        try {
            return val.isEmpty() ? defvalue : Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defvalue;
        }
    }

    /**
     * 获取请求URL最后的一个/后面的部分的int值   <br>
     * 例如请求URL /pipes/user/query/2   <br>
     * 获取type参数: int type = request.getRequstURILastPath(16, 0); //type = 2
     *
     * @param radix    进制数
     * @param defvalue 默认int值
     *
     * @return int值
     */
    public int getRequstURILastPath(int radix, int defvalue) {
        String val = getRequstURILastPath();
        try {
            return val.isEmpty() ? defvalue : Integer.parseInt(val, radix);
        } catch (NumberFormatException e) {
            return defvalue;
        }
    }

    /**
     * 获取请求URL最后的一个/后面的部分的float值   <br>
     * 例如请求URL /pipes/user/query/2   <br>
     * 获取type参数: float type = request.getRequstURILastPath(0.0f); //type = 2.0f
     *
     * @param defvalue 默认float值
     *
     * @return float值
     */
    public float getRequstURILastPath(float defvalue) {
        String val = getRequstURILastPath();
        try {
            return val.isEmpty() ? defvalue : Float.parseFloat(val);
        } catch (NumberFormatException e) {
            return defvalue;
        }
    }

    /**
     * 获取请求URL最后的一个/后面的部分的int值   <br>
     * 例如请求URL /pipes/user/query/2   <br>
     * 获取type参数: long type = request.getRequstURILastPath(0L); //type = 2
     *
     * @param defvalue 默认long值
     *
     * @return long值
     */
    public long getRequstURILastPath(long defvalue) {
        String val = getRequstURILastPath();
        try {
            return val.isEmpty() ? defvalue : Long.parseLong(val);
        } catch (NumberFormatException e) {
            return defvalue;
        }
    }

    /**
     * 获取请求URL最后的一个/后面的部分的int值   <br>
     * 例如请求URL /pipes/user/query/2   <br>
     * 获取type参数: long type = request.getRequstURILastPath(16, 0L); //type = 2
     *
     * @param radix    进制数
     * @param defvalue 默认long值
     *
     * @return long值
     */
    public long getRequstURILastPath(int radix, long defvalue) {
        String val = getRequstURILastPath();
        try {
            return val.isEmpty() ? defvalue : Long.parseLong(val, radix);
        } catch (NumberFormatException e) {
            return defvalue;
        }
    }

    /**
     * 获取请求URL最后的一个/后面的部分的double值   <br>
     * 例如请求URL /pipes/user/query/2   <br>
     * 获取type参数: double type = request.getRequstURILastPath(0.0); //type = 2.0
     *
     * @param defvalue 默认double值
     *
     * @return double值
     */
    public double getRequstURILastPath(double defvalue) {
        String val = getRequstURILastPath();
        try {
            return val.isEmpty() ? defvalue : Double.parseDouble(val);
        } catch (NumberFormatException e) {
            return defvalue;
        }
    }

    /**
     *
     * 从prefix之后截取getRequestURI再对"/"进行分隔
     * <p>
     * @param prefix 前缀
     *
     * @return String[]
     */
    public String[] getRequstURIPaths(String prefix) {
        if (requestURI == null || prefix == null) return new String[0];
        return requestURI.substring(requestURI.indexOf(prefix) + prefix.length() + (prefix.endsWith("/") ? 0 : 1)).split("/");
    }

    /**
     * 获取请求URL分段中含prefix段的值   <br>
     * 例如请求URL /pipes/user/query/name:hello   <br>
     * 获取name参数: String name = request.getRequstURIPath("name:", "none");
     *
     * @param prefix   prefix段前缀
     * @param defvalue 默认值
     *
     * @return prefix截断后的值
     */
    public String getRequstURIPath(String prefix, String defvalue) {
        if (requestURI == null || prefix == null || prefix.isEmpty()) return defvalue;
        int pos = requestURI.indexOf(prefix);
        if (pos < 0) return defvalue;
        String sub = requestURI.substring(pos + prefix.length());
        pos = sub.indexOf('/');
        return pos < 0 ? sub : sub.substring(0, pos);
    }

    /**
     * 获取请求URL分段中含prefix段的short值   <br>
     * 例如请求URL /pipes/user/query/type:10   <br>
     * 获取type参数: short type = request.getRequstURIPath("type:", (short)0);
     *
     * @param prefix   prefix段前缀
     * @param defvalue 默认short值
     *
     * @return short值
     */
    public short getRequstURIPath(String prefix, short defvalue) {
        String val = getRequstURIPath(prefix, null);
        try {
            return val == null ? defvalue : Short.parseShort(val);
        } catch (NumberFormatException e) {
            return defvalue;
        }
    }

    /**
     * 获取请求URL分段中含prefix段的short值   <br>
     * 例如请求URL /pipes/user/query/type:a   <br>
     * 获取type参数: short type = request.getRequstURIPath(16, "type:", (short)0); //type = 10
     *
     * @param radix    进制数
     * @param prefix   prefix段前缀
     * @param defvalue 默认short值
     *
     * @return short值
     */
    public short getRequstURIPath(int radix, String prefix, short defvalue) {
        String val = getRequstURIPath(prefix, null);
        try {
            return val == null ? defvalue : Short.parseShort(val, radix);
        } catch (NumberFormatException e) {
            return defvalue;
        }
    }

    /**
     * 获取请求URL分段中含prefix段的int值  <br>
     * 例如请求URL /pipes/user/query/offset:0/limit:50   <br>
     * 获取offset参数: int offset = request.getRequstURIPath("offset:", 0);   <br>
     * 获取limit参数: int limit = request.getRequstURIPath("limit:", 20);  <br>
     *
     * @param prefix   prefix段前缀
     * @param defvalue 默认int值
     *
     * @return int值
     */
    public int getRequstURIPath(String prefix, int defvalue) {
        String val = getRequstURIPath(prefix, null);
        try {
            return val == null ? defvalue : Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defvalue;
        }
    }

    /**
     * 获取请求URL分段中含prefix段的int值  <br>
     * 例如请求URL /pipes/user/query/offset:0/limit:50   <br>
     * 获取offset参数: int offset = request.getRequstURIPath("offset:", 0);   <br>
     * 获取limit参数: int limit = request.getRequstURIPath(16, "limit:", 20); // limit = 16  <br>
     *
     * @param radix    进制数
     * @param prefix   prefix段前缀
     * @param defvalue 默认int值
     *
     * @return int值
     */
    public int getRequstURIPath(int radix, String prefix, int defvalue) {
        String val = getRequstURIPath(prefix, null);
        try {
            return val == null ? defvalue : Integer.parseInt(val, radix);
        } catch (NumberFormatException e) {
            return defvalue;
        }
    }

    /**
     * 获取请求URL分段中含prefix段的float值   <br>
     * 例如请求URL /pipes/user/query/point:40.0   <br>
     * 获取time参数: float point = request.getRequstURIPath("point:", 0.0f);
     *
     * @param prefix   prefix段前缀
     * @param defvalue 默认float值
     *
     * @return float值
     */
    public float getRequstURIPath(String prefix, float defvalue) {
        String val = getRequstURIPath(prefix, null);
        try {
            return val == null ? defvalue : Float.parseFloat(val);
        } catch (NumberFormatException e) {
            return defvalue;
        }
    }

    /**
     * 获取请求URL分段中含prefix段的long值   <br>
     * 例如请求URL /pipes/user/query/time:1453104341363/id:40   <br>
     * 获取time参数: long time = request.getRequstURIPath("time:", 0L);
     *
     * @param prefix   prefix段前缀
     * @param defvalue 默认long值
     *
     * @return long值
     */
    public long getRequstURIPath(String prefix, long defvalue) {
        String val = getRequstURIPath(prefix, null);
        try {
            return val == null ? defvalue : Long.parseLong(val);
        } catch (NumberFormatException e) {
            return defvalue;
        }
    }

    /**
     * 获取请求URL分段中含prefix段的long值   <br>
     * 例如请求URL /pipes/user/query/time:1453104341363/id:40   <br>
     * 获取time参数: long time = request.getRequstURIPath(16, "time:", 0L);
     *
     * @param radix    进制数
     * @param prefix   prefix段前缀
     * @param defvalue 默认long值
     *
     * @return long值
     */
    public long getRequstURIPath(int radix, String prefix, long defvalue) {
        String val = getRequstURIPath(prefix, null);
        try {
            return val == null ? defvalue : Long.parseLong(val, radix);
        } catch (NumberFormatException e) {
            return defvalue;
        }
    }

    /**
     * 获取请求URL分段中含prefix段的double值   <br>
     * 例如请求URL /pipes/user/query/point:40.0   <br>
     * 获取time参数: double point = request.getRequstURIPath("point:", 0.0);
     *
     * @param prefix   prefix段前缀
     * @param defvalue 默认double值
     *
     * @return double值
     */
    public double getRequstURIPath(String prefix, double defvalue) {
        String val = getRequstURIPath(prefix, null);
        try {
            return val == null ? defvalue : Double.parseDouble(val);
        } catch (NumberFormatException e) {
            return defvalue;
        }
    }

    //------------------------------------------------------------------------------
    /**
     * 获取请求Header总对象
     *
     * @return AnyValue
     */
    public Map<String, String> getHeaders() {
        parseHeader();
        return headers;
    }

    /**
     * 将请求Header转换成Map
     *
     * @param map Map
     *
     * @return Map
     */
    @ConvertDisabled
    public Map<String, String> getHeadersToMap(Map<String, String> map) {
        parseHeader();
        if (map == null) map = new LinkedHashMap<>();
        final Map<String, String> map0 = map;
        headers.forEach((k, v) -> map0.put(k, v));
        return map0;
    }

    /**
     * 获取所有的header名
     *
     * @return header名数组
     */
    @ConvertDisabled
    public String[] getHeaderNames() {
        parseHeader();
        Set<String> names = headers.keySet();
        return names.toArray(new String[names.size()]);
    }

    /**
     * 获取指定的header值
     *
     * @param name header名
     *
     * @return header值
     */
    public String getHeader(String name) {
        parseHeader();
        return headers.get(name);
    }

    /**
     * 获取指定的header值, 没有返回默认值
     *
     * @param name         header名
     * @param defaultValue 默认值
     *
     * @return header值
     */
    public String getHeader(String name, String defaultValue) {
        parseHeader();
        return headers.getOrDefault(name, defaultValue);
    }

    /**
     * 获取指定的header的json值
     *
     * @param <T>  泛型
     * @param type 反序列化的类名
     * @param name header名
     *
     * @return header值
     */
    public <T> T getJsonHeader(java.lang.reflect.Type type, String name) {
        String v = getHeader(name);
        return v == null || v.isEmpty() ? null : jsonConvert.convertFrom(type, v);
    }

    /**
     * 获取指定的header的json值
     *
     * @param <T>     泛型
     * @param convert JsonConvert对象
     * @param type    反序列化的类名
     * @param name    header名
     *
     * @return header值
     */
    public <T> T getJsonHeader(JsonConvert convert, java.lang.reflect.Type type, String name) {
        String v = getHeader(name);
        return v == null || v.isEmpty() ? null : convert.convertFrom(type, v);
    }

    /**
     * 获取指定的header的boolean值, 没有返回默认boolean值
     *
     * @param name         header名
     * @param defaultValue 默认boolean值
     *
     * @return header值
     */
    public boolean getBooleanHeader(String name, boolean defaultValue) {
        //return headers.getBoolValue(name, defaultValue);
        parseHeader();
        String value = headers.get(name);
        return value == null || value.length() == 0 ? defaultValue : Boolean.parseBoolean(value);
    }

    /**
     * 获取指定的header的short值, 没有返回默认short值
     *
     * @param name         header名
     * @param defaultValue 默认short值
     *
     * @return header值
     */
    public short getShortHeader(String name, short defaultValue) {
        //return headers.getShortValue(name, defaultValue);    
        parseHeader();
        String value = headers.get(name);
        if (value == null || value.length() == 0) return defaultValue;
        try {
            return Short.decode(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取指定的header的short值, 没有返回默认short值
     *
     * @param radix        进制数
     * @param name         header名
     * @param defaultValue 默认short值
     *
     * @return header值
     */
    public short getShortHeader(int radix, String name, short defaultValue) {
        //return headers.getShortValue(name, defaultValue);
        parseHeader();
        String value = headers.get(name);
        if (value == null || value.length() == 0) return defaultValue;
        try {
            return (radix == 10 ? Short.decode(value) : Short.parseShort(value, radix));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取指定的header的short值, 没有返回默认short值
     *
     * @param name         header名
     * @param defaultValue 默认short值
     *
     * @return header值
     */
    public short getShortHeader(String name, int defaultValue) {
        //return headers.getShortValue(name, (short) defaultValue);
        parseHeader();
        String value = headers.get(name);
        if (value == null || value.length() == 0) return (short) defaultValue;
        try {
            return Short.decode(value);
        } catch (NumberFormatException e) {
            return (short) defaultValue;
        }
    }

    /**
     * 获取指定的header的short值, 没有返回默认short值
     *
     * @param radix        进制数
     * @param name         header名
     * @param defaultValue 默认short值
     *
     * @return header值
     */
    public short getShortHeader(int radix, String name, int defaultValue) {
        //return headers.getShortValue(radix, name, (short) defaultValue);
        parseHeader();
        String value = headers.get(name);
        if (value == null || value.length() == 0) return (short) defaultValue;
        try {
            return (radix == 10 ? Short.decode(value) : Short.parseShort(value, radix));
        } catch (NumberFormatException e) {
            return (short) defaultValue;
        }
    }

    /**
     * 获取指定的header的int值, 没有返回默认int值
     *
     * @param name         header名
     * @param defaultValue 默认int值
     *
     * @return header值
     */
    public int getIntHeader(String name, int defaultValue) {
        //return headers.getIntValue(name, defaultValue);
        parseHeader();
        String value = headers.get(name);
        if (value == null || value.length() == 0) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取指定的header的int值, 没有返回默认int值
     *
     * @param radix        进制数
     * @param name         header名
     * @param defaultValue 默认int值
     *
     * @return header值
     */
    public int getIntHeader(int radix, String name, int defaultValue) {
        //return headers.getIntValue(radix, name, defaultValue);
        parseHeader();
        String value = headers.get(name);
        if (value == null || value.length() == 0) return defaultValue;
        try {
            return (radix == 10 ? Integer.decode(value) : Integer.parseInt(value, radix));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取指定的header的long值, 没有返回默认long值
     *
     * @param name         header名
     * @param defaultValue 默认long值
     *
     * @return header值
     */
    public long getLongHeader(String name, long defaultValue) {
        //return headers.getLongValue(name, defaultValue);
        parseHeader();
        String value = headers.get(name);
        if (value == null || value.length() == 0) return defaultValue;
        try {
            return Long.decode(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取指定的header的long值, 没有返回默认long值
     *
     * @param radix        进制数
     * @param name         header名
     * @param defaultValue 默认long值
     *
     * @return header值
     */
    public long getLongHeader(int radix, String name, long defaultValue) {
        //return headers.getLongValue(radix, name, defaultValue);
        parseHeader();
        String value = headers.get(name);
        if (value == null || value.length() == 0) return defaultValue;
        try {
            return (radix == 10 ? Long.decode(value) : Long.parseLong(value, radix));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取指定的header的float值, 没有返回默认float值
     *
     * @param name         header名
     * @param defaultValue 默认float值
     *
     * @return header值
     */
    public float getFloatHeader(String name, float defaultValue) {
        //return headers.getFloatValue(name, defaultValue);
        parseHeader();
        String value = headers.get(name);
        if (value == null || value.length() == 0) return defaultValue;
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取指定的header的double值, 没有返回默认double值
     *
     * @param name         header名
     * @param defaultValue 默认double值
     *
     * @return header值
     */
    public double getDoubleHeader(String name, double defaultValue) {
        //return headers.getDoubleValue(name, defaultValue);
        parseHeader();
        String value = headers.get(name);
        if (value == null || value.length() == 0) return defaultValue;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    //------------------------------------------------------------------------------
    /**
     * 获取请求参数总对象
     *
     * @return AnyValue
     */
    public Map<String, String> getParameters() {
        parseBody();
        return params;
    }

    /**
     * 将请求参数转换成Map
     *
     * @param map Map
     *
     * @return Map
     */
    @ConvertDisabled
    public Map<String, String> getParametersToMap(Map<String, String> map) {
        if (map == null) map = new LinkedHashMap<>();
        final Map<String, String> map0 = map;
        getParameters().forEach((k, v) -> map0.put(k, v));
        return map0;
    }

    /**
     * 将请求参数转换成String, 字符串格式为: bean1={}&amp;id=13&amp;name=xxx <br>
     * 不会返回null，没有参数返回空字符串
     *
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
     *
     * @return String
     */
    public String getParametersToString(String prefix) {
        byte[] rbs = queryBytes;
        if (rbs == null || rbs.length < 1) return "";
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
        Set<String> names = params.keySet();
        return names.toArray(new String[names.size()]);
    }

    /**
     * 获取指定的参数值
     *
     * @param name 参数名
     *
     * @return 参数值
     */
    public String getParameter(String name) {
        if (this.frombody) {
            if (array.isEmpty()) return null;
            Convert convert = this.reqConvert;
            if (convert == null) convert = jsonConvert;
            return (String) convert.convertFrom(String.class, array.content());
        }
        parseBody();
        return params.get(name);
    }

    /**
     * 获取指定的参数值, 没有返回默认值
     *
     * @param name         参数名
     * @param defaultValue 默认值
     *
     * @return 参数值
     */
    public String getParameter(String name, String defaultValue) {
        if (this.frombody) {
            if (array.isEmpty()) return defaultValue;
            Convert convert = this.reqConvert;
            if (convert == null) convert = jsonConvert;
            return (String) convert.convertFrom(String.class, array.content());
        }
        parseBody();
        return params.getOrDefault(name, defaultValue);
    }

    /**
     * 获取指定的参数json值
     *
     * @param <T>  泛型
     * @param type 反序列化的类名
     * @param name 参数名
     *
     * @return 参数值
     */
    public <T> T getJsonParameter(java.lang.reflect.Type type, String name) {
        if (this.frombody) {
            if (array.isEmpty()) return null;
            Convert convert = this.reqConvert;
            if (convert == null) convert = jsonConvert;
            if (type == byte[].class) return (T) array.getBytes();
            return (T) convert.convertFrom(type, array.content());
        }
        String v = getParameter(name);
        return v == null || v.isEmpty() ? null : jsonConvert.convertFrom(type, v);
    }

    /**
     * 获取指定的参数json值
     *
     * @param <T>     泛型
     * @param convert JsonConvert对象
     * @param type    反序列化的类名
     * @param name    参数名
     *
     * @return 参数值
     */
    public <T> T getJsonParameter(JsonConvert convert, java.lang.reflect.Type type, String name) {
        String v = getParameter(name);
        return v == null || v.isEmpty() ? null : convert.convertFrom(type, v);
    }

    /**
     * 获取指定的参数boolean值, 没有返回默认boolean值
     *
     * @param name         参数名
     * @param defaultValue 默认boolean值
     *
     * @return 参数值
     */
    public boolean getBooleanParameter(String name, boolean defaultValue) {
        if (this.frombody) {
            if (array.isEmpty()) return defaultValue;
            Convert convert = this.reqConvert;
            if (convert == null) convert = jsonConvert;
            return (boolean) convert.convertFrom(boolean.class, array.content());
        }
        parseBody();
        String value = params.get(name);
        return value == null || value.length() == 0 ? defaultValue : Boolean.parseBoolean(value);
    }

    /**
     * 获取指定的参数short值, 没有返回默认short值
     *
     * @param name         参数名
     * @param defaultValue 默认short值
     *
     * @return 参数值
     */
    public short getShortParameter(String name, short defaultValue) {
        if (this.frombody) {
            if (array.isEmpty()) return defaultValue;
            Convert convert = this.reqConvert;
            if (convert == null) convert = jsonConvert;
            return (short) convert.convertFrom(short.class, array.content());
        }
        parseBody();
        String value = params.get(name);
        if (value == null || value.length() == 0) return defaultValue;
        try {
            return Short.decode(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取指定的参数short值, 没有返回默认short值
     *
     * @param radix        进制数
     * @param name         参数名
     * @param defaultValue 默认short值
     *
     * @return 参数值
     */
    public short getShortParameter(int radix, String name, short defaultValue) {
        if (this.frombody) {
            if (array.isEmpty()) return (short) defaultValue;
            Convert convert = this.reqConvert;
            if (convert == null) convert = jsonConvert;
            return (short) convert.convertFrom(short.class, array.content());
        }
        parseBody();
        String value = params.get(name);
        if (value == null || value.length() == 0) return defaultValue;
        try {
            return (radix == 10 ? Short.decode(value) : Short.parseShort(value, radix));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取指定的参数short值, 没有返回默认short值
     *
     * @param name         参数名
     * @param defaultValue 默认short值
     *
     * @return 参数值
     */
    public short getShortParameter(String name, int defaultValue) {
        if (this.frombody) {
            if (array.isEmpty()) return (short) defaultValue;
            Convert convert = this.reqConvert;
            if (convert == null) convert = jsonConvert;
            return (short) convert.convertFrom(short.class, array.content());
        }
        parseBody();
        String value = params.get(name);
        if (value == null || value.length() == 0) return (short) defaultValue;
        try {
            return Short.decode(value);
        } catch (NumberFormatException e) {
            return (short) defaultValue;
        }
    }

    /**
     * 获取指定的参数int值, 没有返回默认int值
     *
     * @param name         参数名
     * @param defaultValue 默认int值
     *
     * @return 参数值
     */
    public int getIntParameter(String name, int defaultValue) {
        if (this.frombody) {
            if (array.isEmpty()) return defaultValue;
            Convert convert = this.reqConvert;
            if (convert == null) convert = jsonConvert;
            return (int) convert.convertFrom(int.class, array.content());
        }
        parseBody();
        String value = params.get(name);
        if (value == null || value.length() == 0) return defaultValue;
        try {
            return Integer.decode(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取指定的参数int值, 没有返回默认int值
     *
     * @param radix        进制数
     * @param name         参数名
     * @param defaultValue 默认int值
     *
     * @return 参数值
     */
    public int getIntParameter(int radix, String name, int defaultValue) {
        if (this.frombody) {
            if (array.isEmpty()) return defaultValue;
            Convert convert = this.reqConvert;
            if (convert == null) convert = jsonConvert;
            return (int) convert.convertFrom(int.class, array.content());
        }
        parseBody();
        String value = params.get(name);
        if (value == null || value.length() == 0) return defaultValue;
        try {
            return (radix == 10 ? Integer.decode(value) : Integer.parseInt(value, radix));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取指定的参数long值, 没有返回默认long值
     *
     * @param name         参数名
     * @param defaultValue 默认long值
     *
     * @return 参数值
     */
    public long getLongParameter(String name, long defaultValue) {
        if (this.frombody) {
            if (array.isEmpty()) return defaultValue;
            Convert convert = this.reqConvert;
            if (convert == null) convert = jsonConvert;
            return (long) convert.convertFrom(long.class, array.content());
        }
        parseBody();
        String value = params.get(name);
        if (value == null || value.length() == 0) return defaultValue;
        try {
            return Long.decode(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取指定的参数long值, 没有返回默认long值
     *
     * @param radix        进制数
     * @param name         参数名
     * @param defaultValue 默认long值
     *
     * @return 参数值
     */
    public long getLongParameter(int radix, String name, long defaultValue) {
        if (this.frombody) {
            if (array.isEmpty()) return defaultValue;
            Convert convert = this.reqConvert;
            if (convert == null) convert = jsonConvert;
            return (long) convert.convertFrom(long.class, array.content());
        }
        parseBody();
        String value = params.get(name);
        if (value == null || value.length() == 0) return defaultValue;
        try {
            return (radix == 10 ? Long.decode(value) : Long.parseLong(value, radix));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取指定的参数float值, 没有返回默认float值
     *
     * @param name         参数名
     * @param defaultValue 默认float值
     *
     * @return 参数值
     */
    public float getFloatParameter(String name, float defaultValue) {
        if (this.frombody) {
            if (array.isEmpty()) return defaultValue;
            Convert convert = this.reqConvert;
            if (convert == null) convert = jsonConvert;
            return (float) convert.convertFrom(float.class, array.content());
        }
        parseBody();
        String value = params.get(name);
        if (value == null || value.length() == 0) return defaultValue;
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取指定的参数double值, 没有返回默认double值
     *
     * @param name         参数名
     * @param defaultValue 默认double值
     *
     * @return 参数值
     */
    public double getDoubleParameter(String name, double defaultValue) {
        if (this.frombody) {
            if (array.isEmpty()) return defaultValue;
            Convert convert = this.reqConvert;
            if (convert == null) convert = jsonConvert;
            return (double) convert.convertFrom(double.class, array.content());
        }
        parseBody();
        String value = params.get(name);
        if (value == null || value.length() == 0) return defaultValue;
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
    public org.redkale.source.Flipper getFlipper() {
        return getFlipper(false, 0);
    }

    /**
     * 获取翻页对象 同 getFlipper("flipper", autoCreate, 0);
     *
     * @param autoCreate 无参数时是否创建新Flipper对象
     *
     * @return Flipper翻页对象
     */
    public org.redkale.source.Flipper getFlipper(boolean autoCreate) {
        return getFlipper(autoCreate, 0);
    }

    /**
     * 获取翻页对象 同 getFlipper("flipper", false, maxLimit);
     *
     * @param maxLimit 最大行数， 小于1则值为Flipper.DEFAULT_LIMIT
     *
     * @return Flipper翻页对象
     */
    public org.redkale.source.Flipper getFlipper(int maxLimit) {
        return getFlipper(false, maxLimit);
    }

    /**
     * 获取翻页对象 同 getFlipper("flipper", autoCreate, maxLimit)
     *
     * @param autoCreate 无参数时是否创建新Flipper对象
     * @param maxLimit   最大行数， 小于1则值为Flipper.DEFAULT_LIMIT
     *
     * @return Flipper翻页对象
     */
    public org.redkale.source.Flipper getFlipper(boolean autoCreate, int maxLimit) {
        return getFlipper("flipper", autoCreate, maxLimit);
    }

    /**
     * 获取翻页对象 https://redkale.org/pipes/users/list?flipper={'offset':0,'limit':20, 'sort':'createtime ASC'}  <br>
     *
     *
     * @param name       Flipper对象的参数名，默认为 "flipper"
     * @param autoCreate 无参数时是否创建新Flipper对象
     * @param maxLimit   最大行数， 小于1则值为Flipper.DEFAULT_LIMIT
     *
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
        if (flipper != null || !autoCreate) return flipper;
        if (maxLimit < 1) maxLimit = org.redkale.source.Flipper.DEFAULT_LIMIT;
        return new org.redkale.source.Flipper(maxLimit);
    }
}
