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
import java.nio.channels.Channels;
import java.nio.charset.*;
import java.util.*;
import java.util.logging.Level;
import org.redkale.convert.ConvertDisabled;
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

    public static final String SESSIONID_NAME = "JSESSIONID";

    protected boolean rpc;

    @Comment("Method GET/POST/...")
    protected String method;

    protected String protocol;

    protected String requestURI;

    protected byte[] queryBytes;

    protected long contentLength = -1;

    protected String contentType;

    protected String host;

    protected String connection;

    @Comment("原始的cookie字符串，解析后值赋给HttpCookie[] cookies")
    protected String cookie;

    protected HttpCookie[] cookies;

    protected String newsessionid;

    //protected final DefaultAnyValue headers = new DefaultAnyValue();
    protected final Map<String, String> headers = new HashMap<>();

    protected final Map<String, String> params = new HashMap<>();

    protected boolean boundary = false;

    protected int moduleid;

    protected int actionid;

    protected Annotation[] annotations;

    // @since 2.1.0
    protected Serializable currentUserid;

    protected Object currentUser;

    protected String remoteAddr;

    private final ByteArray array = new ByteArray();

    private boolean bodyparsed = false;

    private final String remoteAddrHeader;

    Object attachment; //仅供HttpServlet传递Entry使用

    public HttpRequest(HttpContext context, ObjectPool<ByteBuffer> bufferPool) {
        super(context, bufferPool);
        this.remoteAddrHeader = context.remoteAddrHeader;
    }

    public HttpRequest(HttpContext context, HttpSimpleRequest req) {
        super(context, null);
        this.remoteAddrHeader = null;
        if (req != null) {
            this.rpc = req.rpc;
            if (req.getBody() != null) this.array.write(req.getBody());
            if (req.getHeaders() != null) this.headers.putAll(req.getHeaders());
            if (req.getParams() != null) this.params.putAll(req.getParams());
            if (req.getCurrentUserid() != 0) this.currentUserid = req.getCurrentUserid();
            this.contentType = req.getContentType();
            this.remoteAddr = req.getRemoteAddr();
            this.requestURI = req.getRequestURI();
            if (req.getSessionid() != null && !req.getSessionid().isEmpty()) {
                this.cookies = new HttpCookie[]{new HttpCookie(SESSIONID_NAME, req.getSessionid())};
            }
        }
    }

    public HttpSimpleRequest createSimpleRequest(String prefix) {
        HttpSimpleRequest req = new HttpSimpleRequest();
        req.setBody(array.size() == 0 ? null : array.getBytes());
        req.setHeaders(headers.isEmpty() ? null : headers);
        req.setParams(params.isEmpty() ? null : params);
        req.setRemoteAddr(getRemoteAddr());
        req.setContentType(getContentType());
        String uri = this.requestURI;
        if (prefix != null && !prefix.isEmpty() && uri.startsWith(prefix)) {
            uri = uri.substring(prefix.length());
        }
        req.setRequestURI(uri);
        req.setSessionid(getSessionid(false));
        req.setRpc(this.rpc);
        return req;
    }

    protected boolean isWebSocket() {
        return connection != null && connection.contains("Upgrade") && "GET".equalsIgnoreCase(method) && "websocket".equalsIgnoreCase(getHeader("Upgrade"));
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

    protected JsonConvert getJsonConvert() {
        return this.jsonConvert;
    }

    @Override
    protected int readHeader(final ByteBuffer buffer) {
        ByteArray bytes = array;
        if (!readLine(buffer, bytes)) return -1;
        Charset charset = this.context.getCharset();
        int index = 0;
        int offset = bytes.find(index, ' ');
        if (offset <= 0) return -1;
        this.method = bytes.toString(index, offset, charset);
        index = ++offset;
        offset = bytes.find(index, ' ');
        if (offset <= 0) return -1;
        int off = bytes.find(index, '#');
        if (off > 0) offset = off;
        int qst = bytes.find(index, offset, (byte) '?');
        if (qst > 0) {
            this.requestURI = bytes.toDecodeString(index, qst - index, charset);
            this.queryBytes = bytes.getBytes(qst + 1, offset - qst - 1);
            try {
                addParameter(bytes, qst + 1, offset - qst - 1);
            } catch (Exception e) {
                this.context.getLogger().log(Level.WARNING, "HttpRequest.addParameter error: " + bytes.toString(), e);
            }
        } else {
            this.requestURI = bytes.toDecodeString(index, offset - index, charset);
            this.queryBytes = new byte[0];
        }
        index = ++offset;
        this.protocol = bytes.toString(index, bytes.size() - index, charset);

        //header
        while (readLine(buffer, bytes)) {
            if (bytes.size() < 2) break;
            index = 0;
            offset = bytes.find(index, ':');
            if (offset <= 0) return -1;
            String name = bytes.toString(index, offset, charset);
            index = offset + 1;
            //Upgrade: websocket 前面有空格，所以需要trim()
            String value = bytes.toString(index, bytes.size() - index, charset).trim();
            switch (name) {
                case "Content-Type":
                case "content-type":
                    this.contentType = value;
                    break;
                case "Content-Length":
                case "content-length":
                    this.contentLength = Long.decode(value);
                    break;
                case "Host":
                case "host":
                    this.host = value;
                    break;
                case "Cookie":
                case "cookie":
                    if (this.cookie == null || this.cookie.isEmpty()) {
                        this.cookie = value;
                    } else {
                        this.cookie += ";" + value;
                    }
                    break;
                case "Connection":
                case "connection":
                    this.connection = value;
                    if (context.getAliveTimeoutSeconds() >= 0) {
                        this.setKeepAlive(!"close".equalsIgnoreCase(value));
                    }
                    break;
                case "user-agent":
                    headers.put("User-Agent", value);
                    break;
                default:
                    headers.put(name, value);
            }
        }
        if (this.contentType != null && this.contentType.contains("boundary=")) this.boundary = true;
        if (this.boundary) this.keepAlive = false; //文件上传必须设置keepAlive为false，因为文件过大时用户不一定会skip掉多余的数据

        bytes.clear();
        if (this.contentLength > 0 && (this.contentType == null || !this.boundary)) {
            if (this.contentLength > context.getMaxbody()) return -1;
            bytes.write(buffer, Math.min((int) this.contentLength, buffer.remaining()));
            int lr = (int) this.contentLength - bytes.size();
            return lr > 0 ? lr : 0;
        }
        if (buffer.hasRemaining() && (this.boundary || !this.keepAlive)) bytes.write(buffer, buffer.remaining()); //文件上传、HTTP1.0或Connection:close
        //暂不考虑是keep-alive且存在body却没有指定Content-Length的情况
        return 0;
    }

    @Override
    protected int readBody(ByteBuffer buffer) {
        int len = buffer.remaining();
        array.write(buffer, len);
        return len;
    }

    @Override
    protected void prepare() {
    }

    protected void skipBodyParse() {
        this.bodyparsed = true;
    }

    private void parseBody() {
        if (this.boundary || bodyparsed) return;
        if (this.contentType != null && this.contentType.toLowerCase().contains("x-www-form-urlencoded")) {
            addParameter(array, 0, array.size());
        }
        bodyparsed = true;
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
        String name = array.toDecodeString(offset, keypos - offset, charset);
        if (name.charAt(0) == '<') return; //内容可能是xml格式; 如: <?xml version="1.0"
        ++keypos;
        String value = array.toDecodeString(keypos, (valpos < 0) ? (limit - keypos) : (valpos - keypos), charset);
        this.params.put(name, value);
        if (valpos >= 0) {
            addParameter(array, valpos + 1, limit - valpos - 1);
        }
    }

    private boolean readLine(ByteBuffer buffer, ByteArray bytes) {
        byte lasted = '\r';
        bytes.clear();
        for (;;) {
            if (!buffer.hasRemaining()) {
                if (lasted != '\r') bytes.write(lasted);
                return false;
            }
            byte b = buffer.get();
            if (b == -1 || (lasted == '\r' && b == '\n')) break;
            if (lasted != '\r') bytes.write(lasted);
            lasted = b;
        }
        return true;
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
     * 获取当前用户ID<br>
     *
     * @param <T> 数据类型只能是int、long、String、JavaBean
     *
     * @return 用户ID
     *
     * @since 2.1.0
     */
    @SuppressWarnings("unchecked")
    public <T extends Serializable> T currentUserid() {
        return (T) this.currentUserid;
    }

    /**
     * @Deprecated
     * 建议使用 setCurrentUserid, 通过userid从Service或缓存中获取用户信息<br>
     * 设置当前用户信息, 通常在HttpServlet.preExecute方法里设置currentUser <br>
     * 数据类型由&#64;HttpUserType指定
     *
     * @param <T>  泛型
     * @param user 用户信息
     *
     * @return HttpRequest
     */
    @Deprecated
    public <T> HttpRequest setCurrentUser(T user) {
        this.currentUser = user;
        return this;
    }

    /**
     * @Deprecated
     * 建议使用 currentUserid, 通过userid从Service或缓存中获取用户信息<br>
     * 获取当前用户信息<br>
     * 数据类型由&#64;HttpUserType指定
     *
     * @param <T> &#64;HttpUserType指定的用户信息类型
     *
     * @return 用户信息
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    public <T> T currentUser() {
        return (T) this.currentUser;
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
        String str = array.toString(StandardCharsets.UTF_8);
        if (str == null || str.isEmpty()) return null;
        return context.getJsonConvert().convertFrom(type, str);
    }

    /**
     * 获取请求内容的JavaBean对象
     *
     * @param <T>     泛型
     * @param convert JsonConvert
     * @param type    类型
     *
     * @return 内容
     */
    public <T> T getBodyJson(JsonConvert convert, java.lang.reflect.Type type) {
        String str = array.toString(StandardCharsets.UTF_8);
        if (str == null || str.isEmpty()) return null;
        return convert.convertFrom(type, str);
    }

    /**
     * 获取请求内容的byte[]
     *
     * @return 内容
     */
    public byte[] getBody() {
        return array.size() == 0 ? null : array.getBytes();
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
            + ", \r\n    remoteAddr: " + this.getRemoteAddr() + ", \r\n    cookies: " + this.cookie + ", \r\n    contentType: " + this.contentType
            + ", \r\n    connection: " + this.connection + ", \r\n    protocol: " + this.protocol + ", \r\n    host: " + this.host
            + ", \r\n    contentLength: " + this.contentLength + ", \r\n    bodyLength: " + this.array.size() + (this.boundary || this.array.isEmpty() ? "" : (", \r\n    bodyContent: " + this.getBodyUTF8()))
            + ", \r\n    params: " + toMapString(this.params, 4) + ", \r\n    header: " + toMapString(this.headers, 4) + "\r\n}"; //this.headers.toString(4)
    }

    private static CharSequence toMapString(Map<String, String> map, int indent) {
        char[] chars = new char[indent];
        Arrays.fill(chars, ' ');
        final String space = new String(chars);
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
        return new MultiContext(context.getCharset(), this.getContentType(), this.params,
            new BufferedInputStream(Channels.newInputStream(this.channel.readableByteChannel()), Math.max(array.size(), 8192)) {
            {
                array.copyTo(this.buf);
                this.count = array.size();
            }
        }, null);
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

    @Override
    protected void recycle() {
        this.cookie = null;
        this.cookies = null;
        this.newsessionid = null;
        this.method = null;
        this.protocol = null;
        this.requestURI = null;
        this.queryBytes = null;
        this.contentType = null;
        this.host = null;
        this.connection = null;
        this.contentLength = -1;
        this.boundary = false;
        this.bodyparsed = false;
        this.moduleid = 0;
        this.actionid = 0;
        this.annotations = null;
        this.currentUserid = null;
        this.currentUser = null;
        this.remoteAddr = null;

        this.attachment = null;

        this.headers.clear();
        this.params.clear();
        this.array.clear();
        super.recycle();
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
     * 获取Connection的Header值
     *
     * @return Connection
     */
    public String getConnection() {
        return connection;
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
     * 获取翻页对象 同 getFlipper("flipper", needcreate, 0);
     *
     * @param needcreate 无参数时是否创建新Flipper对象
     *
     * @return Flipper翻页对象
     */
    public org.redkale.source.Flipper getFlipper(boolean needcreate) {
        return getFlipper(needcreate, 0);
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
     * 获取翻页对象 同 getFlipper("flipper", needcreate, maxLimit)
     *
     * @param needcreate 无参数时是否创建新Flipper对象
     * @param maxLimit   最大行数， 小于1则值为Flipper.DEFAULT_LIMIT
     *
     * @return Flipper翻页对象
     */
    public org.redkale.source.Flipper getFlipper(boolean needcreate, int maxLimit) {
        return getFlipper("flipper", needcreate, maxLimit);
    }

    /**
     * 获取翻页对象 https://redkale.org/pipes/users/list/offset:0/limit:20/sort:createtime%20ASC  <br>
     * https://redkale.org/pipes/users/list?flipper={'offset':0,'limit':20, 'sort':'createtime ASC'}  <br>
     * 以上两种接口都可以获取到翻页对象
     *
     *
     * @param name       Flipper对象的参数名，默认为 "flipper"
     * @param needcreate 无参数时是否创建新Flipper对象
     * @param maxLimit   最大行数， 小于1则值为Flipper.DEFAULT_LIMIT
     *
     * @return Flipper翻页对象
     */
    public org.redkale.source.Flipper getFlipper(String name, boolean needcreate, int maxLimit) {
        org.redkale.source.Flipper flipper = getJsonParameter(org.redkale.source.Flipper.class, name);
        if (flipper == null) {
            if (maxLimit < 1) maxLimit = org.redkale.source.Flipper.DEFAULT_LIMIT;
            int limit = getRequstURIPath("limit:", 0);
            int offset = getRequstURIPath("offset:", 0);
            String sort = getRequstURIPath("sort:", "");
            if (limit > 0) {
                if (limit > maxLimit) limit = maxLimit;
                flipper = new org.redkale.source.Flipper(limit, offset, sort);
            }
        } else if (flipper.getLimit() < 1 || (maxLimit > 0 && flipper.getLimit() > maxLimit)) {
            flipper.setLimit(maxLimit);
        }
        if (flipper != null || !needcreate) return flipper;
        if (maxLimit < 1) maxLimit = org.redkale.source.Flipper.DEFAULT_LIMIT;
        return new org.redkale.source.Flipper(maxLimit);
    }
}
