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
import java.nio.file.StandardOpenOption;
import java.time.ZoneId;
import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.logging.*;
import org.redkale.annotation.Nonnull;
import org.redkale.convert.*;
import org.redkale.convert.json.*;
import org.redkale.net.*;
import org.redkale.net.Filter;
import org.redkale.service.RetException;
import org.redkale.service.RetResult;
import org.redkale.util.*;
import org.redkale.util.AnyValue.Entry;
import static org.redkale.util.Utility.append;

/**
 * Http响应包 与javax.servlet.http.HttpServletResponse 基本类似。 <br>
 * 同时提供发送json的系列接口: public void finishJson(Type type, Object obj) <br>
 * Redkale提倡http+json的接口风格， 所以主要输出的数据格式为json， 同时提供异步接口。 <br>
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class HttpResponse extends Response<HttpContext, HttpRequest> {

    protected static final byte[] EMPTY_BTYES = new byte[0];

    protected static final byte[] bytes304 = "HTTP/1.1 304 Not Modified\r\nContent-Length:0\r\n\r\n".getBytes();

    protected static final byte[] bytes404 = "HTTP/1.1 404 Not Found\r\nContent-Length:0\r\n\r\n".getBytes();

    protected static final byte[] bytes405 = "HTTP/1.1 405 Method Not Allowed\r\nContent-Length:0\r\n\r\n".getBytes();

    protected static final byte[] bytes500 =
            "HTTP/1.1 500 Internal Server Error\r\nContent-Length:0\r\n\r\n".getBytes();

    protected static final byte[] bytes504 = "HTTP/1.1 504 Gateway Timeout\r\nContent-Length:0\r\n\r\n".getBytes();

    protected static final byte[] status200Bytes = "HTTP/1.1 200 OK\r\n".getBytes();

    protected static final byte[] LINE = new byte[] {'\r', '\n'};

    protected static final byte[] serverNameBytes = ("Server: "
                    + System.getProperty(
                            "redkale.http.response.header.server", "redkale" + "/" + Redkale.getDotedVersion())
                    + "\r\n")
            .getBytes();

    protected static final byte[] connectCloseBytes =
            "none".equalsIgnoreCase(System.getProperty("redkale.http.response.header.connection"))
                    ? new byte[0]
                    : "Connection: close\r\n".getBytes();

    protected static final byte[] connectAliveBytes =
            "none".equalsIgnoreCase(System.getProperty("redkale.http.response.header.connection"))
                    ? new byte[0]
                    : "Connection: keep-alive\r\n".getBytes();

    protected static final String CONTENT_TYPE_HTML_UTF8 = "text/html; charset=utf-8";

    private static final int CACHE_MAX_CONTENT_LENGTH = 1000;

    private static final byte[] status200_server_live_Bytes =
            append(append(status200Bytes, serverNameBytes), connectAliveBytes);

    private static final byte[] status200_server_close_Bytes =
            append(append(status200Bytes, serverNameBytes), connectCloseBytes);

    private static final ZoneId ZONE_GMT = ZoneId.of("GMT");

    private static final Map<Integer, String> httpCodes = new HashMap<>();

    private static final byte[][] contentLengthArray = new byte[CACHE_MAX_CONTENT_LENGTH][];

    private static final JsonConvert jsonRootConvert = JsonConvert.root();

    static {
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
        httpCodes.put(426, "Upgrade Required");
        httpCodes.put(428, "Precondition Required");
        httpCodes.put(429, "Too Many Requests");
        httpCodes.put(431, "Request Header Fields Too Large");
        httpCodes.put(451, "Unavailable For Legal Reasons");

        httpCodes.put(500, "Internal Server Error");
        httpCodes.put(501, "Not Implemented");
        httpCodes.put(502, "Bad Gateway");
        httpCodes.put(503, "Service Unavailable");
        httpCodes.put(504, "Gateway Timeout");
        httpCodes.put(505, "HTTP Version Not Supported");
        httpCodes.put(511, "Network Authentication Required");

        for (int i = 0; i < CACHE_MAX_CONTENT_LENGTH; i++) {
            contentLengthArray[i] = ("Content-Length: " + i + "\r\n").getBytes();
        }
    }

    private int status = 200;

    private String contentType = "";

    private long contentLength = -1;

    private HttpCookie[] cookies;

    private boolean respHeadContainsConnection;

    // 0表示跳过header，正数表示header的字节长度。
    private int headWritedSize = -1;

    private BiConsumer<HttpResponse, byte[]> cacheHandler;

    private BiFunction<HttpRequest, RetResult, RetResult> retResultHandler;

    private BiFunction<HttpResponse, ByteArray, ByteArray> sendHandler;

    // ------------------------------------------------
    private final String plainContentType;

    private final byte[] plainContentTypeBytes;

    private final String jsonContentType;

    private final byte[] jsonContentTypeBytes;

    private final AnyValueWriter header = new AnyValueWriter();

    private final String[][] defaultAddHeaders;

    private final String[][] defaultSetHeaders;

    private final boolean autoOptions;

    private final Supplier<byte[]> dateSupplier;

    private final HttpCookie defaultCookie;

    private final HttpRender httpRender;

    private final ByteArray headerArray = new ByteArray();

    private final byte[][] plainLiveContentLengthArray;

    private final byte[][] jsonLiveContentLengthArray;

    private final byte[][] plainCloseContentLengthArray;

    private final byte[][] jsonCloseContentLengthArray;

    private final JsonBytesWriter jsonWriter = new JsonBytesWriter();

    private final Consumer<byte[]> headerArrayConsumer;

    private final Consumer<byte[]> headerBufferConsumer;

    @SuppressWarnings("Convert2Lambda")
    protected final ConvertBytesHandler convertHandler = new ConvertBytesHandler() {
        @Override
        public <A> void completed(byte[] bs, int offset, int length, Consumer<A> callback, A attachment) {
            finish(bs, offset, length, callback, attachment);
        }
    };

    public HttpResponse(HttpContext context, HttpRequest request, HttpResponseConfig config) {
        super(context, request);
        this.defaultAddHeaders = config == null ? null : config.defaultAddHeaders;
        this.defaultSetHeaders = config == null ? null : config.defaultSetHeaders;
        this.defaultCookie = config == null ? null : config.defaultCookie;
        this.autoOptions = config != null && config.autoOptions;
        this.dateSupplier = config == null ? null : config.dateSupplier;
        this.httpRender = config == null ? null : config.httpRender;

        this.plainContentType = config == null ? "text/plain; charset=utf-8" : config.plainContentType;
        this.jsonContentType = config == null ? "application/json; charset=utf-8" : config.jsonContentType;
        this.plainContentTypeBytes = config == null
                ? ("Content-Type: " + this.plainContentType + "\r\n").getBytes()
                : config.plainContentTypeBytes;
        this.jsonContentTypeBytes = config == null
                ? ("Content-Type: " + this.jsonContentType + "\r\n").getBytes()
                : config.jsonContentTypeBytes;
        this.plainLiveContentLengthArray = config == null ? null : config.plainLiveContentLengthArray;
        this.plainCloseContentLengthArray = config == null ? null : config.plainCloseContentLengthArray;
        this.jsonLiveContentLengthArray = config == null ? null : config.jsonLiveContentLengthArray;
        this.jsonCloseContentLengthArray = config == null ? null : config.jsonCloseContentLengthArray;
        this.contentType = this.plainContentType;
        this.headerArrayConsumer = headerArray::put;
        this.headerBufferConsumer = writeBuffer()::put;
    }

    @Override
    protected AsyncConnection removeChannel() {
        return super.removeChannel();
    }

    protected AsyncConnection getChannel() {
        return channel;
    }

    @Override
    protected void prepare() {
        super.prepare();
    }

    @Override
    protected boolean recycle() {
        this.status = 200;
        this.contentLength = -1;
        this.contentType = null;
        this.cookies = null;
        this.headWritedSize = -1;
        // this.headBuffer = null;
        this.header.clear();
        this.headerArray.clear();
        this.cacheHandler = null;
        this.retResultHandler = null;
        this.sendHandler = null;
        this.respHeadContainsConnection = false;
        this.jsonWriter.recycle();
        return super.recycle();
    }

    @Override
    protected ExecutorService getWorkExecutor() {
        return super.getWorkExecutor();
    }

    @Override
    protected void updateNonBlocking(boolean nonBlocking) {
        super.updateNonBlocking(nonBlocking);
    }

    @Override
    protected boolean inNonBlocking() {
        return super.inNonBlocking();
    }

    //    protected Supplier<ByteBuffer> getBodyBufferSupplier() {
    //        return bodyBufferSupplier;
    //    }
    @Override
    protected void init(AsyncConnection channel) {
        super.init(channel);
    }

    /**
     * 获取状态码对应的状态描述
     *
     * @param status 状态码
     * @return 状态描述
     */
    protected String getHttpCode(int status) {
        return httpCodes.get(status);
    }

    /**
     * 获取HttpRequest
     *
     * @return HttpRequest
     */
    public HttpRequest getRequest() {
        return request;
    }

    protected String getHttpCode(int status, String defValue) {
        String v = httpCodes.get(status);
        return v == null ? defValue : v;
    }

    @Override
    protected void setFilter(Filter filter) {
        super.setFilter(filter);
    }

    @Override
    protected void thenEvent(Servlet servlet) {
        super.thenEvent(servlet);
    }

    @Override
    protected void thenEvent(Filter filter) {
        super.thenEvent(filter);
    }

    protected boolean isAutoOptions() {
        return this.autoOptions;
    }

    /**
     * 增加Cookie值
     *
     * @param cookies cookie
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
     * @return HttpResponse
     */
    public HttpResponse addCookie(Collection<HttpCookie> cookies) {
        this.cookies = Utility.append(this.cookies, cookies);
        return this;
    }

    /**
     * 创建CompletionHandler实例
     *
     * @return CompletionHandler
     */
    public CompletionHandler createAsyncHandler() {
        return Utility.createAsyncHandler(
                (v, a) -> {
                    finish(v);
                },
                (t, a) -> {
                    context.getLogger()
                            .log(
                                    Level.WARNING,
                                    "Servlet occur, force to close channel. request = " + request
                                            + ", result is CompletionHandler",
                                    (Throwable) t);
                    finishError(t);
                });
    }

    @Override
    protected void defaultError(Throwable t) {
        if (t instanceof RetException) {
            finish(jsonRootConvert, RetResult.TYPE_RET_STRING, ((RetException) t).retResult());
        } else {
            finish(500, null);
        }
    }

    /**
     * 创建CompletionHandler子类的实例 <br>
     * 传入的CompletionHandler子类必须是public，且保证其子类可被继承且completed、failed可被重载且包含空参数的构造函数。
     *
     * @param <H> 泛型
     * @param handlerClass CompletionHandler子类
     * @return CompletionHandler
     */
    @SuppressWarnings("unchecked")
    public <H extends CompletionHandler> H createAsyncHandler(Class<H> handlerClass) {
        if (handlerClass == null || handlerClass == CompletionHandler.class) {
            return (H) createAsyncHandler();
        }
        return context.loadAsyncHandlerCreator(handlerClass).create(createAsyncHandler());
    }

    /**
     * 将对象以JSON格式输出
     *
     * @param obj 输出对象
     */
    public final void finishJson(final Object obj) {
        finishJson((Convert) null, (Type) null, obj);
    }

    /**
     * 将对象以JSON格式输出
     *
     * @param convert 指定的JsonConvert
     * @param obj 输出对象
     */
    public final void finishJson(final Convert convert, final Object obj) {
        finishJson(convert, (Type) null, obj);
    }

    /**
     * 将对象以JSON格式输出
     *
     * @param type 指定的类型
     * @param obj 输出对象
     */
    public final void finishJson(final Type type, final Object obj) {
        if (sendHandler == null
                && cacheHandler == null
                && headerBufferConsumer != null
                && request.pipelineIndex() <= 0
                && !this.channel.hasPipelineData()
                && request.getRespConvert() == jsonRootConvert
                && this.header.getStringEntrys().length == 0) {
            this.contentType = this.jsonContentType;
            if (this.recycleListener != null) {
                this.output = obj;
            }
            JsonBytesWriter writer = jsonWriter;
            jsonRootConvert.convertTo(writer.clear(), type, obj);
            this.contentLength = writer.length();
            createHeader(headerBufferConsumer); // 写进writeBuffer

            ByteBuffer buffer = writeBuffer();
            if (buffer.capacity() >= contentLength) {
                buffer.put(writer.content(), 0, writer.length());
                buffer.flip();
                writeInIOThread(buffer);
            } else {
                finish(this.jsonContentType, writer);
            }
        } else {
            finishJson((Convert) null, type, obj);
        }
    }

    /**
     * 将对象以JSON格式输出
     *
     * @param convert 指定的Convert
     * @param type 指定的类型
     * @param obj 输出对象
     */
    public void finishJson(final Convert convert, final Type type, final Object obj) {
        this.contentType = this.jsonContentType;
        if (this.recycleListener != null) {
            this.output = obj;
        }
        Convert c = convert == null ? request.getRespConvert() : convert;
        Type t = type == null ? (obj == null ? null : obj.getClass()) : type;
        if (c == jsonRootConvert) {
            JsonBytesWriter writer = jsonWriter;
            c.convertTo(writer.clear(), t, obj);
            finish(false, (String) null, writer.content(), writer.offset(), writer.length(), null, null);
        } else {
            c.convertToBytes(t, obj, convertHandler);
        }
    }

    /**
     * 将对象以JSON格式输出
     *
     * @param objs 输出对象
     */
    //    @Deprecated  //@since 2.3.0
    //    void finishJson(final Object... objs) {
    //        this.contentType = this.jsonContentType;
    //        if (this.recycleListener != null) this.output = objs;
    //        request.getRespConvert().convertToBytes(objs, convertHandler);
    //    }
    /**
     * 将RetResult对象以JSON格式输出
     *
     * @param type 指定的RetResult泛型类型
     * @param ret RetResult输出对象
     */
    //    @Deprecated //@since 2.5.0
    //    public void finishJson(Type type, RetResult ret) {
    //        this.contentType = this.jsonContentType;
    //        if (this.retResultHandler != null) {
    //            ret = this.retResultHandler.apply(this.request, ret);
    //        }
    //        if (this.recycleListener != null) this.output = ret;
    //        if (ret != null && !ret.isSuccess()) {
    //            this.header.addValue("retcode", String.valueOf(ret.getRetcode()));
    //            this.header.addValue("retinfo", ret.getRetinfo());
    //        }
    //        Convert convert = ret == null ? null : ret.convert();
    //        if (convert == null) convert = request.getRespConvert();
    //        if (convert == jsonRootConvert) {
    //            JsonBytesWriter writer = jsonWriter;
    //            convert.convertTo(writer.clear(), type, ret);
    //            finishFuture(false, (String) null, writer.content(), writer.offset(), writer.length(), null, null);
    //        } else {
    //            convert.convertToBytes(type, ret, convertHandler);
    //        }
    //    }
    /**
     * 将RetResult对象以JSON格式输出
     *
     * @param convert 指定的Convert
     * @param type 指定的RetResult泛型类型
     * @param ret RetResult输出对象
     */
    //    @Deprecated //@since 2.5.0
    //    public void finishJson(final Convert convert, Type type, RetResult ret) {
    //        this.contentType = this.jsonContentType;
    //        if (this.retResultHandler != null) {
    //            ret = this.retResultHandler.apply(this.request, ret);
    //        }
    //        if (this.recycleListener != null) this.output = ret;
    //        if (ret != null && !ret.isSuccess()) {
    //            this.header.addValue("retcode", String.valueOf(ret.getRetcode()));
    //            this.header.addValue("retinfo", ret.getRetinfo());
    //        }
    //        if (convert == jsonRootConvert) {
    //            JsonBytesWriter writer = jsonWriter;
    //            convert.convertTo(writer.clear(), type, ret);
    //            finishFuture(false, (String) null, writer.content(), writer.offset(), writer.length(), null, null);
    //        } else {
    //            convert.convertToBytes(type, ret, convertHandler);
    //        }
    //    }
    /**
     * 将CompletableFuture的结果对象以JSON格式输出
     *
     * @param convert 指定的Convert
     * @param valueType 指定CompletableFuture.value的泛型类型
     * @param future 输出对象的句柄
     */
    //    @Deprecated //@since 2.5.0
    //    @SuppressWarnings("unchecked")
    //    public void finishJson(final Convert convert, final Type valueType, final CompletableFuture future) {
    //        finishFuture(convert, valueType, future);
    //    }
    /**
     * 将RetResult对象输出
     *
     * @param type 指定的RetResult泛型类型
     * @param ret RetResult输出对象
     */
    public final void finish(Type type, RetResult ret) {
        finish((Convert) null, type, ret);
    }

    /**
     * 将RetResult对象输出
     *
     * @param convert 指定的Convert
     * @param type 指定的RetResult泛型类型
     * @param ret RetResult输出对象
     */
    @SuppressWarnings("null")
    public void finish(final Convert convert, Type type, RetResult ret) {
        Objects.requireNonNull(type);
        if (this.retResultHandler != null) {
            ret = this.retResultHandler.apply(this.request, ret);
        }
        if (this.recycleListener != null) {
            this.output = ret;
        }
        if (ret != null && !ret.isSuccess()) {
            this.header.addValue("retcode", String.valueOf(ret.getRetcode()));
            this.header.addValue("retinfo", ret.getRetinfo());
        }
        Convert cc = convert;
        if (cc == null && ret != null) {
            cc = ret.convert();
        }
        if (cc == null) {
            cc = request.getRespConvert();
        }
        if (cc instanceof JsonConvert) {
            this.contentType = this.jsonContentType;
        } else if (cc instanceof TextConvert) {
            this.contentType = this.plainContentType;
        }
        if (cc == jsonRootConvert) {
            JsonBytesWriter writer = jsonWriter;
            cc.convertTo(writer.clear(), type, ret);
            finish(false, (String) null, writer.content(), writer.offset(), writer.length(), null, null);
        } else {
            cc.convertToBytes(type, ret, convertHandler);
        }
    }

    /**
     * 将HttpResult对象输出
     *
     * @param resultType HttpResult.result的泛型类型
     * @param result HttpResult输出对象
     */
    public final void finish(Type resultType, HttpResult result) {
        finish((Convert) null, resultType, result);
    }

    /**
     * 将HttpResult对象输出
     *
     * @param convert 指定的Convert
     * @param resultType HttpResult.result的泛型类型
     * @param result HttpResult输出对象
     */
    public void finish(final Convert convert, Type resultType, HttpResult result) {
        if (result.getContentType() != null) {
            setContentType(result.getContentType());
        }
        addHeader(result.getHeaders())
                .addCookie(result.getCookies())
                .setStatus(result.getStatus() < 1 ? 200 : result.getStatus());
        Object val = result.getResult();
        if (val == null) {
            finish((String) null, EMPTY_BTYES);
        } else if (val instanceof CharSequence) {
            finish(getStatus(), val.toString());
        } else {
            Convert cc = result.convert();
            if (cc == null) {
                cc = convert;
            }
            finish(cc, resultType, val);
        }
    }

    /**
     * 将CompletionStage对象输出
     *
     * @param valueType CompletionFuture.value的泛型类型
     * @param future CompletionStage输出对象
     */
    public final void finishFuture(Type valueType, CompletionStage future) {
        finishFuture((Convert) null, valueType, future);
    }

    /**
     * 将CompletionStage对象输出
     *
     * @param convert 指定的Convert
     * @param valueType CompletionFuture.value的泛型类型
     * @param future CompletionStage输出对象
     */
    public void finishFuture(final Convert convert, Type valueType, CompletionStage future) {
        future.whenComplete((v, e) -> {
            Traces.currentTraceid(request.getTraceid());
            if (e != null) {
                context.getLogger()
                        .log(
                                Level.WARNING,
                                "Servlet occur exception. request = " + request + ", result is CompletionStage",
                                (Throwable) e);
                if (e instanceof TimeoutException) {
                    finish504();
                } else {
                    finish500();
                }
                return;
            }
            finish(convert, valueType, v);
            Traces.removeTraceid();
        });
    }

    /**
     * 将CompletionStage对象输出
     *
     * @param valueType CompletionFuture.value的泛型类型
     * @param future CompletionStage输出对象
     */
    public final void finishJsonFuture(Type valueType, CompletionStage future) {
        finishJsonFuture(request.getRespConvert(), valueType, future);
    }

    /**
     * 将CompletionStage对象输出
     *
     * @param convert 指定的Convert
     * @param valueType CompletionFuture.value的泛型类型
     * @param future CompletionStage输出对象
     */
    public void finishJsonFuture(final Convert convert, Type valueType, CompletionStage future) {
        future.whenComplete((v, e) -> {
            Traces.currentTraceid(request.getTraceid());
            if (e != null) {
                context.getLogger()
                        .log(
                                Level.WARNING,
                                "Servlet occur exception. request = " + request + ", result is CompletionStage",
                                (Throwable) e);
                if (e instanceof TimeoutException) {
                    finish504();
                } else {
                    finish500();
                }
                return;
            }
            finishJson(convert, valueType, v);
            Traces.removeTraceid();
        });
    }

    /**
     * 将Flow.Publisher对象输出
     *
     * @param <T> 泛型
     * @param valueType Publisher的泛型类型
     * @param publisher Publisher输出对象
     */
    public final <T> void finishPublisher(Type valueType, Flow.Publisher<T> publisher) {
        finishPublisher(request.getRespConvert(), valueType, publisher);
    }

    /**
     * 将Flow.Publisher对象输出
     *
     * @param <T> 泛型
     * @param convert 指定的Convert
     * @param valueType Publisher的泛型类型
     * @param publisher Publisher输出对象
     */
    public final <T> void finishPublisher(final Convert convert, Type valueType, Flow.Publisher<T> publisher) {
        finishFuture(convert, valueType, (CompletionStage) Flows.createMonoFuture(publisher));
    }

    /**
     * 将第三方类Flow.Publisher对象(如: Mono/Flux)输出
     *
     * @param valueType Publisher的泛型类型
     * @param publisher Publisher输出对象
     */
    public final void finishPublisher(Type valueType, Object publisher) {
        finishPublisher((Convert) null, valueType, publisher);
    }

    /**
     * 将第三方类Flow.Publisher对象(如: Mono/Flux)输出
     *
     * @param convert 指定的Convert
     * @param valueType Publisher的泛型类型
     * @param publisher Publisher输出对象
     */
    public final void finishPublisher(final Convert convert, Type valueType, Object publisher) {
        finishFuture(convert, valueType, (CompletionStage) Flows.maybePublisherToFuture(publisher));
    }

    /**
     * 将HttpScope对象输出
     *
     * @param future HttpScope输出异步对象
     */
    public final void finishScopeFuture(CompletionStage<HttpScope> future) {
        finishScopeFuture((Convert) null, future);
    }

    /**
     * 将HttpScope对象输出
     *
     * @param convert 指定的Convert
     * @param future HttpScope输出异步对象
     */
    public void finishScopeFuture(final Convert convert, CompletionStage<HttpScope> future) {
        future.whenComplete((v, e) -> {
            if (e != null) {
                context.getLogger()
                        .log(
                                Level.WARNING,
                                "Servlet occur, force to close channel. request = " + request
                                        + ", result is CompletionStage",
                                (Throwable) e);
                if (e instanceof TimeoutException) {
                    finish504();
                } else {
                    finish500();
                }
                return;
            }
            finish(convert, v);
        });
    }

    /**
     * 将HttpScope对象输出
     *
     * @param result HttpScope输出对象
     */
    public final void finish(HttpScope result) {
        finish((Convert) null, result);
    }

    /**
     * 将HttpScope对象输出
     *
     * @param convert 指定的Convert
     * @param result HttpScope输出对象
     */
    public void finish(final Convert convert, HttpScope result) {
        if (result == null) {
            finish("null");
            return;
        }
        if (httpRender != null) {
            setContentType(CONTENT_TYPE_HTML_UTF8);
            if (result.getHeaders() != null) {
                addHeader(result.getHeaders());
            }
            if (result.getCookies() != null) {
                addCookie(result.getCookies());
            }
            httpRender.renderTo(this.request, this, convert, result);
            return;
        }
        finish("");
    }

    /**
     * 将结果对象输出
     *
     * @param obj 输出对象
     */
    public final void finish(final Object obj) {
        finish((Convert) null, (Type) null, obj);
    }

    /**
     * 将结果对象输出
     *
     * @param convert 指定的Convert
     * @param obj 输出对象
     */
    public final void finish(final Convert convert, final Object obj) {
        finish(convert, (Type) null, obj);
    }

    /**
     * 将结果对象输出
     *
     * @param type 指定的类型, 不一定是obj的数据类型，必然obj为CompletableFuture， type应该为Future的元素类型
     * @param obj 输出对象
     */
    public final void finish(final Type type, Object obj) {
        finish((Convert) null, type, obj);
    }

    /**
     * 将结果对象输出
     *
     * @param convert 指定的Convert
     * @param type 指定的类型, 不一定是obj的数据类型，必然obj为CompletionStage， type应该为Future的元素类型
     * @param obj 输出对象
     */
    @SuppressWarnings({"unchecked", "null"})
    public void finish(final Convert convert, final Type type, Object obj) {
        Object val = obj;
        // 以下if条件会被Rest类第2440行左右的地方用到
        if (val == null) {
            Convert cc = convert;
            if (cc == null) {
                cc = request.getRespConvert();
            }
            if (cc instanceof JsonConvert) {
                this.contentType = this.jsonContentType;
            } else if (cc instanceof TextConvert) {
                this.contentType = this.plainContentType;
            }
            cc.convertToBytes(val, convertHandler);
        } else if (val instanceof CompletionStage) {
            finishFuture(convert, val == obj ? type : null, (CompletionStage) val);
        } else if (val instanceof CharSequence) {
            finish(val.toString());
        } else if (val instanceof byte[]) {
            finish((byte[]) val);
        } else if (val instanceof File) {
            try {
                finish((File) val);
            } catch (IOException e) {
                context.getLogger()
                        .log(
                                Level.WARNING,
                                "HttpServlet finish File occur, force to close channel. request = " + getRequest(),
                                e);
                finish(500, null);
            }
        } else if (val instanceof RetResult) {
            finish(convert, type, (RetResult) val);
        } else if (val instanceof HttpResult) {
            finish(convert, type, (HttpResult) val);
        } else if (val instanceof HttpScope) {
            finish(convert, (HttpScope) val);
        } else {
            Convert cc = convert;
            if (cc == null) {
                cc = request.getRespConvert();
            }
            if (cc instanceof JsonConvert) {
                this.contentType = this.jsonContentType;
            } else if (cc instanceof TextConvert) {
                this.contentType = this.plainContentType;
            }
            if (this.recycleListener != null) {
                this.output = val;
            }
            // this.channel == null为虚拟的HttpResponse
            if (type == null) {
                if (cc == jsonRootConvert) {
                    JsonBytesWriter writer = jsonWriter;
                    cc.convertTo(writer.clear(), val);
                    finish(false, (String) null, writer.content(), writer.offset(), writer.length(), null, null);
                } else {
                    cc.convertToBytes(val, convertHandler);
                }
            } else {
                if (cc == jsonRootConvert) {
                    JsonBytesWriter writer = jsonWriter;
                    cc.convertTo(writer.clear(), type, val);
                    finish(false, (String) null, writer.content(), writer.offset(), writer.length(), null, null);
                } else {
                    cc.convertToBytes(type, val, convertHandler);
                }
            }
        }
    }

    /**
     * 将指定字符串以响应结果输出
     *
     * @param obj 输出内容
     */
    public void finish(String obj) {
        finish(200, obj);
    }

    /**
     * 以指定响应码附带内容输出
     *
     * @param status 响应码
     * @param message 输出内容
     */
    public void finish(int status, String message) {
        if (isClosed()) {
            return;
        }
        this.status = status;
        // if (status != 200) super.refuseAlive();
        final byte[] val = message == null
                ? HttpRequest.EMPTY_BYTES
                : (context.getCharset() == null ? Utility.encodeUTF8(message) : message.getBytes(context.getCharset()));
        finish(false, null, val, 0, val.length, null, null);
    }

    @Override
    public void finish(boolean kill, final byte[] bs, int offset, int length) {
        finish(false, null, bs, offset, length, null, null);
    }

    public <A> void finish(final byte[] bs, int offset, int length, Consumer<A> callback, A attachment) {
        finish(false, null, bs, offset, length, callback, attachment);
    }

    /**
     * 将指定byte[]按响应结果输出
     *
     * @param contentType ContentType
     * @param bs 输出内容
     */
    public void finish(final String contentType, final byte[] bs) {
        finish(false, contentType, bs, 0, bs == null ? 0 : bs.length, null, null);
    }

    /**
     * 将ByteTuple按响应结果输出
     *
     * @param contentType ContentType
     * @param array 输出内容
     */
    public void finish(final String contentType, final ByteTuple array) {
        finish(false, contentType, array.content(), array.offset(), array.length(), null, null);
    }

    /**
     * 将指定byte[]按响应结果输出
     *
     * @param kill kill
     * @param contentType ContentType
     * @param bs 输出内容
     * @param offset 偏移量
     * @param length 长度
     */
    protected void finish(boolean kill, final String contentType, final byte[] bs, int offset, int length) {
        finish(kill, contentType, bs, offset, length, null, null);
    }

    /**
     * 将指定byte[]按响应结果输出
     *
     * @param kill kill
     * @param contentType ContentType
     * @param bodyContent 输出内容
     * @param bodyOffset 偏移量
     * @param bodyLength 长度
     * @param callback Consumer
     * @param attachment ConvertWriter
     * @param <A> A
     */
    protected <A> void finish(
            boolean kill,
            final String contentType,
            @Nonnull byte[] bodyContent,
            int bodyOffset,
            int bodyLength,
            Consumer<A> callback,
            A attachment) {
        if (isClosed()) {
            return; // 避免重复关闭
        }
        if (sendHandler != null) {
            ByteArray bodyArray = new ByteArray(bodyContent, bodyOffset, bodyLength);
            bodyArray = sendHandler.apply(this, bodyArray);
            bodyContent = bodyArray.content();
            bodyOffset = bodyArray.offset();
            bodyLength = bodyArray.length();
        }

        if (this.headWritedSize < 0) {
            if (contentType != null) {
                this.contentType = contentType;
            }
            this.contentLength = bodyLength;
            createHeader();
        }
        ByteArray data = headerArray;
        data.put(bodyContent, bodyOffset, bodyLength);
        if (callback != null) {
            callback.accept(attachment);
        }
        if (cacheHandler != null) {
            cacheHandler.accept(this, data.getBytes());
        }
        // 不能用finish(boolean kill, final ByteTuple array) 否则会调this.finishFuture
        super.finish(false, data.content(), 0, data.length());
    }

    void kill() {
        refuseAlive();
        this.responseConsumer.accept(this);
    }

    /** 以304状态码输出 */
    public void finish304() {
        skipHeader();
        super.finish(false, bytes304);
    }

    /** 以404状态码输出 */
    public void finish404() {
        skipHeader();
        super.finish(false, bytes404);
    }

    /** 以405状态码输出 */
    public void finish405() {
        skipHeader();
        super.finish(false, bytes405);
    }

    /** 以500状态码输出 */
    public void finish500() {
        skipHeader();
        super.finish(false, bytes500);
    }

    /** 以504状态码输出 */
    public void finish504() {
        skipHeader();
        super.finish(false, bytes504);
    }

    // Header大小
    protected void createHeader() {
        createHeader(headerArrayConsumer);
        this.headWritedSize = headerArray.length();
    }

    // Header大小
    protected void createHeader(Consumer<byte[]> writer) {
        if (this.status == 200
                && !this.respHeadContainsConnection
                && !this.request.isWebSocket()
                && (this.contentType == null
                        || this.contentType == this.jsonContentType
                        || this.contentType == this.plainContentType)
                && (this.contentLength >= 0 && this.contentLength < jsonLiveContentLengthArray.length)) {
            byte[][] lengthArray = this.plainLiveContentLengthArray;
            if (this.request.isKeepAlive()) {
                if (this.contentType == this.jsonContentType) {
                    lengthArray = this.jsonLiveContentLengthArray;
                }
            } else {
                if (this.contentType == this.jsonContentType) {
                    lengthArray = this.jsonCloseContentLengthArray;
                } else {
                    lengthArray = this.plainCloseContentLengthArray;
                }
            }
            writer.accept(lengthArray[(int) this.contentLength]);
        } else {
            if (this.status == 200 && !this.respHeadContainsConnection && !this.request.isWebSocket()) {
                if (this.request.isKeepAlive()) {
                    writer.accept(status200_server_live_Bytes);
                } else {
                    writer.accept(status200_server_close_Bytes);
                }
            } else {
                if (this.status == 200) {
                    writer.accept(status200Bytes);
                } else {
                    writer.accept(("HTTP/1.1 " + this.status + " " + httpCodes.get(this.status) + "\r\n").getBytes());
                }
                writer.accept(serverNameBytes);
                if (!this.respHeadContainsConnection) {
                    byte[] bs = this.request.isKeepAlive() ? connectAliveBytes : connectCloseBytes;
                    if (bs.length > 0) {
                        writer.accept(bs);
                    }
                }
            }
            if (!this.request.isWebSocket()) {
                if (this.contentType == this.jsonContentType) {
                    writer.accept(this.jsonContentTypeBytes);
                } else if (this.contentType == null || this.contentType == this.plainContentType) {
                    writer.accept(this.plainContentTypeBytes);
                } else {
                    writer.accept(("Content-Type: " + this.contentType + "\r\n").getBytes());
                }
            }
            if (this.contentLength >= 0) {
                if (this.contentLength < contentLengthArray.length) {
                    writer.accept(contentLengthArray[(int) this.contentLength]);
                } else {
                    writer.accept(("Content-Length: " + this.contentLength + "\r\n").getBytes());
                }
            }
        }
        if (dateSupplier != null) {
            writer.accept(dateSupplier.get());
        }

        if (this.defaultAddHeaders != null) {
            for (String[] headers : this.defaultAddHeaders) {
                if (headers.length > 3) {
                    String v = request.getParameter(headers[2]);
                    if (v != null) {
                        this.header.addValue(headers[0], v);
                    }
                } else if (headers.length > 2) {
                    String v = request.getHeader(headers[2]);
                    if (v != null) {
                        this.header.addValue(headers[0], v);
                    }
                } else {
                    this.header.addValue(headers[0], headers[1]);
                }
            }
        }
        if (this.defaultSetHeaders != null) {
            for (String[] headers : this.defaultSetHeaders) {
                if (headers.length > 3) {
                    String v = request.getParameter(headers[2]);
                    if (v != null) {
                        this.header.setValue(headers[0], v);
                    }
                } else if (headers.length > 2) {
                    String v = request.getHeader(headers[2]);
                    if (v != null) {
                        this.header.setValue(headers[0], v);
                    }
                } else {
                    this.header.setValue(headers[0], headers[1]);
                }
            }
        }
        for (Entry<String> en : this.header.getStringEntrys()) {
            writer.accept((en.name + ": " + en.getValue() + "\r\n").getBytes());
        }
        if (request.newSessionid != null) {
            String domain = defaultCookie == null ? null : defaultCookie.getDomain();
            if (domain == null || domain.isEmpty()) {
                domain = "";
            } else {
                domain = "Domain=" + domain + "; ";
            }
            //            String path = defaultCookie == null ? null : defaultCookie.getPath();
            //            if (path == null || path.isEmpty()) {
            //                path = "/";
            //            }
            if (request.newSessionid.isEmpty()) {
                writer.accept(("Set-Cookie: " + HttpRequest.SESSIONID_NAME + "=; " + domain
                                + "Path=/; Max-Age=0; HttpOnly\r\n")
                        .getBytes());
            } else {
                writer.accept(("Set-Cookie: " + HttpRequest.SESSIONID_NAME + "=" + request.newSessionid + "; " + domain
                                + "Path=/; HttpOnly\r\n")
                        .getBytes());
            }
        }
        if (this.cookies != null) {
            for (HttpCookie cookie : this.cookies) {
                if (cookie == null) {
                    continue;
                }
                if (defaultCookie != null) {
                    if (defaultCookie.getDomain() != null && cookie.getDomain() == null) {
                        cookie.setDomain(defaultCookie.getDomain());
                    }
                    if (defaultCookie.getPath() != null && cookie.getPath() == null) {
                        cookie.setPath(defaultCookie.getPath());
                    }
                }
                writer.accept(("Set-Cookie: " + cookieString(cookie) + "\r\n").getBytes());
            }
        }
        writer.accept(LINE);
    }

    private CharSequence cookieString(HttpCookie cookie) {
        StringBuilder sb = new StringBuilder();
        sb.append(cookie.getName()).append("=").append(cookie.getValue()).append("; Version=1");
        if (cookie.getDomain() != null) {
            sb.append("; Domain=").append(cookie.getDomain());
        }
        if (cookie.getPath() != null) {
            sb.append("; Path=").append(cookie.getPath());
        }
        if (cookie.getPortlist() != null) {
            sb.append("; Port=").append(cookie.getPortlist());
        }
        if (cookie.getMaxAge() > 0) {
            sb.append("; Max-Age=").append(cookie.getMaxAge());
            sb.append("; Expires=")
                    .append(RFC_1123_DATE_TIME.format(
                            java.time.ZonedDateTime.now(ZONE_GMT).plusSeconds(cookie.getMaxAge())));
        }
        if (cookie.getSecure()) {
            sb.append("; Secure");
        }
        if (cookie.isHttpOnly()) {
            sb.append("; HttpOnly");
        }
        return sb;
    }

    /**
     * 异步输出指定内容
     *
     * @param buffer 输出内容
     * @param handler 异步回调函数
     */
    protected void sendBody(ByteBuffer buffer, CompletionHandler<Integer, Void> handler) {
        if (this.headWritedSize < 0) {
            if (this.contentLength < 0) {
                this.contentLength = buffer == null ? 0 : buffer.remaining();
            }
            createHeader();
            if (buffer == null) { // 只发header
                super.send(headerArray, handler);
            } else {
                ByteBuffer headbuf = channel.pollWriteBuffer();
                headbuf.put(headerArray.content(), 0, headerArray.length());
                headbuf.flip();
                super.send(new ByteBuffer[] {headbuf, buffer}, null, handler);
            }
        } else {
            super.send(buffer, null, handler);
        }
    }

    /**
     * 将指定文件按响应结果输出
     *
     * @param file 输出文件
     * @throws IOException IO异常
     */
    public void finish(File file) throws IOException {
        finishFile(null, file, null);
    }

    /**
     * 将文件按指定文件名输出
     *
     * @param fileName 输出文件名
     * @param file 输出文件
     * @throws IOException IO异常
     */
    public void finish(final String fileName, File file) throws IOException {
        finishFile(fileName, file, null);
    }

    /**
     * 将指定文件句柄或文件内容按响应结果输出，若fileBody不为null则只输出fileBody内容
     *
     * @param file 输出文件
     * @param fileBody 文件内容， 没有则输出file
     * @throws IOException IO异常
     */
    protected void finishFile(final File file, ByteArray fileBody) throws IOException {
        finishFile(null, file, fileBody);
    }

    /**
     * 将指定文件句柄或文件内容按指定文件名输出，若fileBody不为null则只输出fileBody内容 file 与 fileBody 不能同时为空 file 与 fileName 也不能同时为空
     *
     * @param fileName 输出文件名
     * @param file 输出文件
     * @param fileBody 文件内容， 没有则输出file
     * @throws IOException IO异常
     */
    protected void finishFile(final String fileName, final File file, ByteArray fileBody) throws IOException {
        if ((file == null || !file.isFile() || !file.canRead()) && fileBody == null) {
            finish404();
            return;
        }
        final long length = file == null ? fileBody.length() : file.length();
        final String match = request.getHeader("If-None-Match");
        final String etag = (file == null ? 0L : file.lastModified()) + "-" + length;
        if (match != null && etag.equals(match)) {
            // finish304();
            // return;
        }
        this.contentLength = length;
        if (Utility.isNotEmpty(fileName) && file != null) {
            if (this.header.getValue("Content-Disposition") == null) {
                addHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(fileName, "UTF-8"));
            }
        }
        this.contentType =
                MimeType.getByFilename(Utility.isEmpty(fileName) && file != null ? file.getName() : fileName);
        if (this.contentType == null) {
            this.contentType = "application/octet-stream";
        }
        String range = request.getHeader("Range");
        if (range != null && (!range.startsWith("bytes=") || range.indexOf(',') >= 0)) {
            range = null;
        }
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
        createHeader();
        ByteArray data = headerArray;
        if (fileBody == null) {
            if (this.recycleListener != null) {
                this.output = file;
            }
            finishFile(data, file, start, len);
        } else { // 一般HttpResourceServlet缓存file内容时fileBody不为空
            if (start >= 0) {
                data.put(fileBody, (int) start, (int) ((len > 0) ? len : fileBody.length() - start));
            }
            super.finish(false, data.content(), 0, data.length());
        }
    }

    // offset、length 为 -1 表示输出整个文件
    private void finishFile(ByteArray headerData, File file, long offset, long length) throws IOException {
        // this.channel.write(headerData,  new TransferFileHandler(file, offset, length));
        final Logger logger = context.getLogger();
        this.channel.write(headerData, new CompletionHandler<Integer, Void>() {

            FileChannel fileChannel;

            long limit;

            long sends;

            ByteBuffer buffer;

            @Override
            public void completed(Integer result, Void attachment) {
                try {
                    if (fileChannel != null && sends >= limit) {
                        if (buffer != null) {
                            channel.offerWriteBuffer(buffer);
                        }
                        try {
                            fileChannel.close();
                        } catch (IOException ie) {
                            // do nothing
                        }
                        completeFinishBytes(result, attachment);
                        return;
                    }
                    if (fileChannel == null) {
                        fileChannel = FileChannel.open(file.toPath(), StandardOpenOption.READ);
                        if (offset > 0) {
                            fileChannel = fileChannel.position(offset);
                        }
                        limit = length > 0 ? length : (file.length() - (offset > 0 ? offset : 0));
                        sends = 0;
                        buffer = channel.ssl() ? channel.pollWriteSSLBuffer() : channel.pollWriteBuffer();
                    }

                    buffer.clear();
                    int len = fileChannel.read(buffer);
                    if (len < 1) {
                        throw new IOException("read " + file + " error: " + len);
                    }
                    buffer.flip();
                    if (sends + len > limit) {
                        buffer.limit((int) (len - limit + sends));
                        sends = limit;
                    } else {
                        sends += len;
                    }
                    channel.write(buffer, attachment, this);
                } catch (Exception e) {
                    if (fileChannel != null) {
                        try {
                            fileChannel.close();
                        } catch (IOException ie) {
                            // do nothing
                        }
                    }
                    failed(e, attachment);
                }
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                if (buffer != null) {
                    channel.offerWriteBuffer(buffer);
                }
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "finishFile error", exc);
                }
                finish(true);
            }
        });
    }

    /**
     * 跳过header的输出 通常应用场景是，调用者的输出内容里已经包含了HTTP的响应头信息，因此需要调用此方法避免重复输出HTTP响应头信息。
     *
     * @return HttpResponse
     */
    public HttpResponse skipHeader() {
        this.headWritedSize = 0;
        return this;
    }

    protected AnyValueWriter duplicateHeader() {
        return this.header.duplicate();
    }

    /**
     * 判断是否存在Header值
     *
     * @param name header-name
     * @return 是否存在
     */
    public boolean existsHeader(String name) {
        return this.header.getValue(name) != null;
    }

    /**
     * 获取Header值
     *
     * @param name header名
     * @return header值
     */
    public String getHeader(String name) {
        return this.header.getValue(name);
    }

    /**
     * 设置Header值
     *
     * @param name header名
     * @param value header值
     * @return HttpResponse
     */
    public HttpResponse setHeader(String name, Object value) {
        this.header.setValue(name, String.valueOf(value));
        if ("Connection".equalsIgnoreCase(name)) {
            this.respHeadContainsConnection = true;
        }
        return this;
    }

    /**
     * 添加Header值
     *
     * @param name header名
     * @param value header值
     * @return HttpResponse
     */
    public HttpResponse addHeader(String name, Object value) {
        this.header.addValue(name, String.valueOf(value));
        if ("Connection".equalsIgnoreCase(name)) {
            this.respHeadContainsConnection = true;
        }
        return this;
    }

    /**
     * 添加Header值
     *
     * @param map header值
     * @return HttpResponse
     */
    public HttpResponse addHeader(Map<String, ?> map) {
        if (map == null || map.isEmpty()) {
            return this;
        }
        for (Map.Entry<String, ?> en : map.entrySet()) {
            this.header.addValue(en.getKey(), String.valueOf(en.getValue()));
            if (!respHeadContainsConnection && "Connection".equalsIgnoreCase(en.getKey())) {
                this.respHeadContainsConnection = true;
            }
        }
        return this;
    }

    /**
     * 设置状态码
     *
     * @param status 状态码
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
    protected BiConsumer<HttpResponse, byte[]> getCacheHandler() {
        return cacheHandler;
    }

    /**
     * 设置输出时的拦截器
     *
     * @param cacheHandler 拦截器
     */
    protected void setCacheHandler(BiConsumer<HttpResponse, byte[]> cacheHandler) {
        this.cacheHandler = cacheHandler;
    }

    /**
     * 获取输出RetResult时的拦截器
     *
     * @return 拦截器
     */
    protected BiFunction<HttpRequest, RetResult, RetResult> getRetResultHandler() {
        return retResultHandler;
    }

    /**
     * 设置输出RetResult时的拦截器
     *
     * @param retResultHandler 拦截器
     */
    public void retResultHandler(BiFunction<HttpRequest, RetResult, RetResult> retResultHandler) {
        this.retResultHandler = retResultHandler;
    }

    /**
     * 获取输出RetResult时的拦截器
     *
     * @return 拦截器
     */
    protected BiFunction<HttpResponse, ByteArray, ByteArray> getSendHandler() {
        return sendHandler;
    }

    /**
     * 设置输出结果时的拦截器
     *
     * @param sendHandler 拦截器
     */
    public void sendHandler(BiFunction<HttpResponse, ByteArray, ByteArray> sendHandler) {
        this.sendHandler = sendHandler;
    }

    //    protected final class TransferFileHandler implements CompletionHandler<Integer, Void> {
    //
    //        private final File file;
    //
    //        private final AsynchronousFileChannel filechannel;
    //
    //        private final long max; //需要读取的字节数， -1表示读到文件结尾
    //
    //        private long count;//读取文件的字节数
    //
    //        private long readpos = 0;
    //
    //        private boolean hdwrite = true; //写入Header
    //
    //        private boolean read = false;
    //
    //        public TransferFileHandler(File file) throws IOException {
    //            this.file = file;
    //            this.filechannel = AsynchronousFileChannel.open(file.toPath(), options);
    //            this.readpos = 0;
    //            this.max = file.length();
    //        }
    //
    //        public TransferFileHandler(File file, long offset, long len) throws IOException {
    //            this.file = file;
    //            this.filechannel = AsynchronousFileChannel.open(file.toPath(), options);
    //            this.readpos = offset <= 0 ? 0 : offset;
    //            this.max = len <= 0 ? file.length() : len;
    //        }
    //
    //        @Override
    //        public void completed(Integer result, Void attachment) {
    //            //(Utility.now() + "---" + Thread.currentThread().getName() + "-----------" + file +
    // "-------------------result: " + result + ", max = " + max + ", readpos = " + readpos + ", count = " + count + ",
    // " + (hdwrite ? "正在写Header" : (read ? "准备读" : "准备写")));
    //            if (result < 0 || count >= max) {
    //                failed(null, attachment);
    //                return;
    //            }
    //            if (hdwrite && attachment.hasRemaining()) { //Header还没写完
    //                channel.write(attachment, attachment, this);
    //                return;
    //            }
    //            if (hdwrite) {
    //                //(Utility.now() + "---" + Thread.currentThread().getName() + "-----------" + file +
    // "-------------------Header写入完毕， 准备读取文件.");
    //                hdwrite = false;
    //                read = true;
    //                result = 0;
    //            }
    //            if (read) {
    //                count += result;
    //            } else {
    //                readpos += result;
    //            }
    //            if (read && attachment.hasRemaining()) { //Buffer还没写完
    //                channel.write(attachment, attachment, this);
    //                return;
    //            }
    //
    //            if (read) {
    //                read = false;
    //                attachment.clear();
    //                filechannel.read(attachment, readpos, attachment, this);
    //            } else {
    //                read = true;
    //                if (count > max) {
    //                    attachment.limit((int) (attachment.position() + max - count));
    //                }
    //                attachment.flip();
    //                if (attachment.hasRemaining()) {
    //                    channel.write(attachment, attachment, this);
    //                } else {
    //                    failed(null, attachment);
    //                }
    //            }
    //        }
    //
    //        @Override
    //        public void failed(Throwable exc, Void attachment) {
    //            finishFuture(true);
    //            try {
    //                filechannel.close();
    //            } catch (IOException e) {
    //            }
    //        }
    //
    //    }
    public static class HttpResponseConfig {

        public String plainContentType;

        public String jsonContentType;

        public byte[] plainContentTypeBytes;

        public byte[] jsonContentTypeBytes;

        public String[][] defaultAddHeaders;

        public String[][] defaultSetHeaders;

        public HttpCookie defaultCookie;

        public boolean autoOptions;

        public Supplier<byte[]> dateSupplier;

        public HttpRender httpRender;

        public AnyValue renderConfig;

        public final byte[][] plainLiveContentLengthArray = new byte[CACHE_MAX_CONTENT_LENGTH][];

        public final byte[][] jsonLiveContentLengthArray = new byte[CACHE_MAX_CONTENT_LENGTH][];

        public final byte[][] plainCloseContentLengthArray = new byte[CACHE_MAX_CONTENT_LENGTH][];

        public final byte[][] jsonCloseContentLengthArray = new byte[CACHE_MAX_CONTENT_LENGTH][];

        public HttpResponseConfig init(AnyValue config) {
            if (this.plainContentTypeBytes == null) {
                String plainct = plainContentType == null || plainContentType.isEmpty()
                        ? "text/plain; charset=utf-8"
                        : plainContentType;
                String jsonct = jsonContentType == null || jsonContentType.isEmpty()
                        ? "application/json; charset=utf-8"
                        : jsonContentType;
                this.plainContentType = plainct;
                this.jsonContentType = jsonct;
                this.plainContentTypeBytes = ("Content-Type: " + plainct + "\r\n").getBytes();
                this.jsonContentTypeBytes = ("Content-Type: " + jsonct + "\r\n").getBytes();
                for (int i = 0; i < CACHE_MAX_CONTENT_LENGTH; i++) {
                    byte[] lenbytes = ("Content-Length: " + i + "\r\n").getBytes();
                    plainLiveContentLengthArray[i] =
                            append(append(status200_server_live_Bytes, plainContentTypeBytes), lenbytes);
                    plainCloseContentLengthArray[i] =
                            append(append(status200_server_close_Bytes, plainContentTypeBytes), lenbytes);
                    jsonLiveContentLengthArray[i] =
                            append(append(status200_server_live_Bytes, jsonContentTypeBytes), lenbytes);
                    jsonCloseContentLengthArray[i] =
                            append(append(status200_server_close_Bytes, jsonContentTypeBytes), lenbytes);
                }
            }
            return this;
        }

        @Override
        public String toString() {
            return JsonConvert.root().convertTo(this);
        }
    }
}
