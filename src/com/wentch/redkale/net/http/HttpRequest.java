/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.http;

import com.wentch.redkale.util.ByteArray;
import com.wentch.redkale.convert.json.*;
import com.wentch.redkale.net.*;
import com.wentch.redkale.util.AnyValue.DefaultAnyValue;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;

/**
 *
 * @author zhangjx
 */
public final class HttpRequest extends Request {

    protected static final Charset UTF8 = Charset.forName("UTF-8");

    protected static final String SESSIONID_NAME = "JSESSIONID";

    private static final byte[] flashRequestContent1 = "<policy-file-request/>\0".getBytes();

    private static final byte[] flashRequestContent2 = "<policy-file-request/>".getBytes();

    private String method;

    private String protocol;

    protected String requestURI;

    private long contentLength = -1;

    private String contentType;

    private String host;

    private String connection;

    protected String cookiestr;

    private HttpCookie[] cookies;

    protected String newsessionid;

    protected final JsonConvert convert;

    protected final DefaultAnyValue header = new DefaultAnyValue();

    protected final DefaultAnyValue params = new DefaultAnyValue();

    private final ByteArray array = new ByteArray();

    private boolean bodyparsed = false;

    protected boolean flashPolicy = false;

    protected boolean boundary = false;

    private final String remoteAddrHeader;

    protected HttpRequest(Context context, JsonFactory factory, String remoteAddrHeader) {
        super(context);
        this.convert = factory.getConvert();
        this.remoteAddrHeader = remoteAddrHeader;
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

    @Override
    protected int readHeader(final ByteBuffer buffer) {
        if (!readLine(buffer, array)) {
            if (array.equal(flashRequestContent1) || array.equal(flashRequestContent2)) { //兼容 flash socket <policy-file-request/>
                this.flashPolicy = true;
                return 0;
            }
            return -1;
        }
        Charset charset = this.context.getCharset();
        int index = 0;
        int offset = array.find(index, ' ');
        if (offset <= 0) return -1;
        this.method = array.toString(index, offset, charset).trim();
        index = ++offset;
        offset = array.find(index, ' ');
        if (offset <= 0) return -1;
        int off = array.find(index, '#');
        if (off > 0) offset = off;
        int qst = array.find(index, offset, (byte) '?');
        if (qst > 0) {
            this.requestURI = array.toDecodeString(index, qst - index, charset).trim();
            addParameter(array, qst + 1, offset - qst - 1);
        } else {
            this.requestURI = array.toDecodeString(index, offset - index, charset).trim();
        }
        if (this.requestURI.contains("../")) return -1;
        index = ++offset;
        this.protocol = array.toString(index, array.count() - index, charset).trim();
        while (readLine(buffer, array)) {
            if (array.count() < 2) break;
            index = 0;
            offset = array.find(index, ':');
            if (offset <= 0) return -1;
            String name = array.toString(index, offset, charset).trim();
            index = offset + 1;
            String value = array.toString(index, array.count() - index, charset).trim();
            switch (name) {
                case "Content-Type":
                    this.contentType = value;
                    break;
                case "Content-Length":
                    this.contentLength = Long.decode(value);
                    break;
                case "Host":
                    this.host = value;
                    break;
                case "Cookie":
                    if (this.cookiestr == null || this.cookiestr.isEmpty()) {
                        this.cookiestr = value;
                    } else {
                        this.cookiestr += ";" + value;
                    }
                    break;
                case "Connection":
                    this.connection = value;
                    this.setKeepAlive("Keep-Alive".equalsIgnoreCase(value));
                    break;
                default:
                    header.addValue(name, value);
            }
        }
        array.clear();
        if (buffer.hasRemaining()) array.add(buffer, buffer.remaining());
        if (this.contentType != null && this.contentType.contains("boundary=")) {
            this.boundary = true;
        }
        if (this.contentLength > 0 && (this.contentType == null || !this.boundary)) {
            if (this.contentLength > context.getMaxbody()) return -1;
            int lr = (int) this.contentLength - array.count();
            return lr > 0 ? lr : 0;
        }
        return 0;
    }

    @Override
    protected void readBody(ByteBuffer buffer) {
        array.add(buffer, buffer.remaining());
    }

    @Override
    protected void prepare() {
    }

    private void parseBody() {
        if (this.boundary || bodyparsed) return;
        addParameter(array, 0, array.count());
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
        ++keypos;
        String value = array.toDecodeString(keypos, (valpos < 0) ? (limit - keypos) : (valpos - keypos), charset);
        this.params.addValue(name, value);
        if (valpos >= 0) {
            addParameter(array, valpos + 1, limit - valpos - 1);
        }
    }

    private boolean readLine(ByteBuffer buffer, ByteArray bytes) {
        byte lasted = '\r';
        bytes.clear();
        for (;;) {
            if (!buffer.hasRemaining()) {
                if (lasted != '\r') bytes.add(lasted);
                return false;
            }
            byte b = buffer.get();
            if (b == -1 || (lasted == '\r' && b == '\n')) break;
            if (lasted != '\r') bytes.add(lasted);
            lasted = b;
        }
        return true;
    }

    @Override
    protected void setProperty(String name, Object value) {
        super.setProperty(name, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <T> T getProperty(String name) {
        return super.getProperty(name);
    }

    @Override
    protected void removeProperty(String name) {
        super.removeProperty(name);
    }

    @Override
    public HttpContext getContext() {
        return (HttpContext) this.context;
    }

    public String getRemoteAddr() {
        if (remoteAddrHeader != null) {
            String val = getHeader(remoteAddrHeader);
            if (val != null) return val;
        }
        SocketAddress addr = getRemoteAddress();
        if (addr == null) return "";
        if (addr instanceof InetSocketAddress) return ((InetSocketAddress) addr).getAddress().getHostAddress();
        return String.valueOf(addr);
    }

    public String getBody(final Charset charset) {
        return array.toString(charset);
    }

    public String getBody() {
        return array.toString();
    }

    public String getBodyUTF8() {
        return array.toString(UTF8);
    }

    public SocketAddress getRemoteAddress() {
        return this.channel.getRemoteAddress();
    }

    @Override
    public String toString() {
        parseBody();
        return this.getClass().getSimpleName() + "{method:" + this.method + ", requestURI:" + this.requestURI
                + ", contentType:" + this.contentType + ", connection:" + this.connection + ", protocol:" + this.protocol
                + ", contentLength:" + this.contentLength + ", cookiestr:" + this.cookiestr
                + ", host:" + this.host + ", params:" + this.params + ", header:" + this.header + "body:" + getBody() + "}";
    }

    public final MultiContext getMultiContext() {
        return new MultiContext(context.getCharset(), this.getContentType(),
                new BufferedInputStream(Channels.newInputStream(this.channel), Math.max(array.count(), 8192)) {
                    {
                        array.write(this.buf);
                        this.count = array.count();
                    }
                });
    }

    @Override
    protected void recycle() {
        this.cookiestr = null;
        this.cookies = null;
        this.newsessionid = null;
        this.method = null;
        this.protocol = null;
        this.requestURI = null;
        this.contentType = null;
        this.host = null;
        this.connection = null;
        this.contentLength = -1;
        this.boundary = false;
        this.bodyparsed = false;
        this.flashPolicy = false;

        this.header.clear();
        this.params.clear();
        super.recycle();
    }

    public String getSessionid(boolean create) {
        String sessionid = getCookie(SESSIONID_NAME, null);
        if (create && (sessionid == null || sessionid.isEmpty())) {
            sessionid = ((HttpContext) context).createSessionid();
            this.newsessionid = sessionid;
        }
        return sessionid;
    }

    public String changeSessionid() {
        this.newsessionid = ((HttpContext) context).createSessionid();
        return newsessionid;
    }

    public void invalidateSession() {
        this.newsessionid = ""; //为空表示删除sessionid
    }

    public HttpCookie[] getCookies() {
        if (this.cookies == null) this.cookies = parseCookies(this.cookiestr);
        return this.cookies;
    }

    public String getCookie(String name) {
        return getCookie(name, null);
    }

    public String getCookie(String name, String dfvalue) {
        for (HttpCookie cookie : getCookies()) {
            if (name.equals(cookie.getName())) return cookie.getValue();
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

    public String getConnection() {
        return connection;
    }

    public String getMethod() {
        return method;
    }

    public String getProtocol() {
        return protocol;
    }

    public String getHost() {
        return host;
    }

    protected static InetSocketAddress parseSocketAddress(String host) {
        if (host == null || host.isEmpty()) return null;
        int pos = host.indexOf(':');
        String hostname = pos < 0 ? host : host.substring(0, pos);
        int port = pos < 0 ? 80 : Integer.parseInt(host.substring(pos + 1));
        return new InetSocketAddress(hostname, port);
    }

    protected InetSocketAddress getHostSocketAddress() {
        return parseSocketAddress(host);
    }

    /**
     * 截取getRequestURI最后的一个/后面的部分
     *
     * @return
     */
    public String getRequstURILastPath() {
        if (requestURI == null) return "";
        return requestURI.substring(requestURI.lastIndexOf('/') + 1);
    }

    public String[] getRequstURIPaths(String prefix) {
        if (requestURI == null || prefix == null) return new String[0];
        return requestURI.substring(requestURI.indexOf(prefix) + prefix.length() + (prefix.endsWith("/") ? 0 : 1)).split("//");
    }

    public String getRequestURI() {
        return requestURI;
    }

    public long getContentLength() {
        return contentLength;
    }

    public String getContentType() {
        return contentType;
    }

    //------------------------------------------------------------------------------
    public String[] getHeaderNames() {
        return header.getNames();
    }

    public String getHeader(String name) {
        return header.getValue(name);
    }

    public <T> T getJsonHeader(Class<T> clazz, String name) {
        String v = getHeader(name);
        return v == null || v.isEmpty() ? null : convert.convertFrom(clazz, v);
    }

    public boolean getBooleanHeader(String name, boolean defaultValue) {
        return header.getBoolValue(name, defaultValue);
    }

    public short getShortHeader(String name, short defaultValue) {
        return header.getShortValue(name, defaultValue);
    }

    public int getIntHeader(String name, int defaultValue) {
        return header.getIntValue(name, defaultValue);
    }

    public long getLongHeader(String name, long defaultValue) {
        return header.getLongValue(name, defaultValue);
    }

    public float getFloatHeader(String name, float defaultValue) {
        return header.getFloatValue(name, defaultValue);
    }

    public double getDoubleHeader(String name, double defaultValue) {
        return header.getDoubleValue(name, defaultValue);
    }

    public String getHeader(String name, String defaultValue) {
        return header.getValue(name, defaultValue);
    }

    //------------------------------------------------------------------------------
    public String[] getParameterNames() {
        parseBody();
        return params.getNames();
    }

    public String getParameter(String name) {
        parseBody();
        return params.getValue(name);
    }

    public <T> T getJsonParameter(Class<T> clazz, String name) {
        String v = getParameter(name);
        return v == null || v.isEmpty() ? null : convert.convertFrom(clazz, v);
    }

    public boolean getBooleanParameter(String name, boolean defaultValue) {
        parseBody();
        return params.getBoolValue(name, defaultValue);
    }

    public short getShortParameter(String name, short defaultValue) {
        parseBody();
        return params.getShortValue(name, defaultValue);
    }

    public int getIntParameter(String name, int defaultValue) {
        parseBody();
        return params.getIntValue(name, defaultValue);
    }

    public long getLongParameter(String name, long defaultValue) {
        parseBody();
        return params.getLongValue(name, defaultValue);
    }

    public float getFloatParameter(String name, float defaultValue) {
        parseBody();
        return params.getFloatValue(name, defaultValue);
    }

    public double getDoubleParameter(String name, double defaultValue) {
        parseBody();
        return params.getDoubleValue(name, defaultValue);
    }

    public String getParameter(String name, String defaultValue) {
        parseBody();
        return params.getValue(name, defaultValue);
    }
}
