/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import static org.redkale.net.http.WebClient.*;
import static org.redkale.util.Utility.isNotEmpty;

import java.io.Serializable;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.redkale.annotation.Comment;
import org.redkale.annotation.Nullable;
import org.redkale.convert.*;
import org.redkale.convert.json.JsonConvert;
import org.redkale.net.client.ClientConnection;
import org.redkale.net.client.ClientRequest;
import org.redkale.util.ByteArray;
import org.redkale.util.Copier;
import org.redkale.util.RedkaleException;
import org.redkale.util.Traces;
import org.redkale.util.Utility;

/**
 * HttpRequest的缩减版, 只提供部分字段
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.1.0
 */
public class WebRequest extends ClientRequest implements java.io.Serializable {

	private static final Function<WebRequest, WebRequest> copyFunc = Copier.func(WebRequest.class, WebRequest.class);

	@ConvertColumn(index = 13)
	@Comment("是否RPC请求, 该类通常是为RPC创建的，故默认是true")
	protected boolean rpc = true;

	@ConvertColumn(index = 14)
	@Comment("请求参数的ConvertType")
	protected ConvertType reqConvertType;

	@ConvertColumn(index = 15)
	@Comment("输出结果的ConvertType")
	protected ConvertType respConvertType;

	@Comment("Method GET/POST/...")
	@ConvertColumn(index = 16)
	protected String method;

	@ConvertColumn(index = 17)
	@Comment("请求的Path")
	protected String path;

	@ConvertColumn(index = 18)
	@Comment("请求的前缀")
	protected String contextPath;

	@ConvertColumn(index = 19)
	@Comment("客户端IP")
	protected String remoteAddr;

	@ConvertColumn(index = 20)
	@Comment("Locale国际化")
	protected String locale;

	@ConvertColumn(index = 21)
	@Comment("会话ID")
	protected String sessionid;

	@ConvertColumn(index = 22)
	@Comment("Content-Type")
	protected String contentType;

	@ConvertColumn(index = 23) // @since 2.5.0 由int改成Serializable, 具体数据类型只能是int、long、BigInteger、String
	protected Serializable currentUserid;

	@ConvertColumn(index = 24)
	@Comment("http header信息")
	protected HttpHeaders headers;

	@ConvertColumn(index = 25)
	@Comment("参数信息")
	protected HttpParameters params;

	@ConvertColumn(index = 26)
	@Comment("http body信息")
	protected byte[] body; // 对应HttpRequest.array

	public static WebRequest createPath(String path) {
		return new WebRequest().path(path).traceid(Traces.currentTraceid());
	}

	public static WebRequest createPath(String path, HttpHeaders header) {
		return createPath(path).headers(header);
	}

	public static WebRequest createPath(String path, Object... params) {
		return createPath(path, (HttpHeaders) null, params);
	}

	public static WebRequest createPath(String path, HttpHeaders header, Object... params) {
		WebRequest req = createPath(path).headers(header);
		if (params.length > 0) {
			int len = params.length / 2;
			for (int i = 0; i < len; i++) {
				req.param(params[i * 2].toString(), params[i * 2 + 1]);
			}
		}
		return req;
	}

	public static WebRequest createGetPath(String path) {
		return createPath(path).method("GET");
	}

	public static WebRequest createGetPath(String path, HttpHeaders header) {
		return createPath(path, header).method("GET");
	}

	public static WebRequest createGetPath(String path, Object... params) {
		return createPath(path, params).method("GET");
	}

	public static WebRequest createGetPath(String path, HttpHeaders header, Object... params) {
		return createPath(path, header, params).method("GET");
	}

	public static WebRequest createPostPath(String path) {
		return createPath(path).method("POST");
	}

	public static WebRequest createPostPath(String path, HttpHeaders header) {
		return createPath(path, header).method("POST");
	}

	public static WebRequest createPostPath(String path, Object... params) {
		return createPath(path, params).method("POST");
	}

	public static WebRequest createPostPath(String path, HttpHeaders header, Object... params) {
		return createPath(path, header, params).method("POST");
	}

	public WebRequest copy() {
		WebRequest rs = copyFunc.apply(this);
		rs.workThread = this.workThread;
		rs.createTime = this.createTime;
		return rs;
	}

	@Override
	public void writeTo(ClientConnection conn, ByteArray array) {
		// 组装path和body
		String requestPath = requestPath();
		String contentType0 = Utility.orElse(this.contentType, "x-www-form-urlencoded");
		byte[] clientBody = null;
		if (isNotEmpty(body)) {
			String paramstr = getParametersToString();
			if (paramstr != null) {
				if (getPath().indexOf('?') > 0) {
					requestPath += "&" + paramstr;
				} else {
					requestPath += "?" + paramstr;
				}
			}
			clientBody = getBody();
		} else {
			String paramstr = getParametersToString();
			if (paramstr != null) {
				clientBody = paramstr.getBytes(StandardCharsets.UTF_8);
			}
			contentType0 = "x-www-form-urlencoded";
		}
		// 写status
		array.put(((method == null ? "GET" : method.toUpperCase()) + " " + requestPath + " HTTP/1.1\r\n")
				.getBytes(StandardCharsets.UTF_8));
		// 写header
		if (traceid != null && !containsHeader(Rest.REST_HEADER_TRACEID)) {
			array.put((Rest.REST_HEADER_TRACEID + ": " + traceid + "\r\n").getBytes(StandardCharsets.UTF_8));
		}
		if (currentUserid != null && !containsHeader(Rest.REST_HEADER_CURRUSERID)) {
			array.put((Rest.REST_HEADER_CURRUSERID + ": " + currentUserid + "\r\n").getBytes(StandardCharsets.UTF_8));
		}
		if (!containsHeader("User-Agent")) {
			array.put(header_bytes_useragent);
		}
		if (!containsHeader("Connection")) {
			array.put(header_bytes_connalive);
		}
		array.put(("Content-Type: " + contentType0 + "\r\n").getBytes(StandardCharsets.UTF_8));
		array.put(contentLengthBytes(clientBody));
		if (headers != null) {
			headers.forEach(
					k -> !k.equalsIgnoreCase("Content-Type") && !k.equalsIgnoreCase("Content-Length"),
					(k, v) -> array.put((k + ": " + v + "\r\n").getBytes(StandardCharsets.UTF_8)));
		}
		array.put((byte) '\r', (byte) '\n');
		// 写body
		if (clientBody != null) {
			array.put(clientBody);
		}
	}

	protected boolean containsHeader(String name) {
		return headers != null && headers.contains(name);
	}

	protected static byte[] contentLengthBytes(byte[] clientBody) {
		int len = clientBody == null ? 0 : clientBody.length;
		if (len < contentLengthArray.length) {
			return contentLengthArray[len];
		}
		return ("Content-Length: " + len + "\r\n").getBytes(StandardCharsets.UTF_8);
	}

	@Nullable
	@ConvertDisabled
	public String getParametersToString() {
		if (this.params == null || this.params.isEmpty()) {
			return null;
		}
		final StringBuilder sb = new StringBuilder();
		AtomicBoolean no2 = new AtomicBoolean(false);
		this.params.forEach((n, v) -> {
			if (no2.get()) {
				sb.append('&');
			}
			sb.append(n).append('=').append(URLEncoder.encode(v, StandardCharsets.UTF_8));
			no2.set(true);
		});
		return sb.toString();
	}

	public String requestPath() {
		if (this.contextPath == null) {
			return this.path;
		}
		return this.contextPath + this.path;
	}

	public WebRequest formUrlencoded() {
		this.headers.set("Content-Type", "x-www-form-urlencoded");
		return this;
	}

	public WebRequest rpc(boolean rpc) {
		this.rpc = rpc;
		return this;
	}

	@Override
	public WebRequest traceid(String traceid) {
		if (traceid != null) {
			if (traceid.indexOf(' ') >= 0 || traceid.indexOf('\r') >= 0 || traceid.indexOf('\n') >= 0) {
				throw new RedkaleException("http-traceid(" + traceid + ") is illegal");
			}
		}
		this.traceid = traceid;
		return this;
	}

	public WebRequest path(String path) {
		if (path != null) {
			if (path.indexOf(' ') >= 0 || path.indexOf('\r') >= 0 || path.indexOf('\n') >= 0) {
				throw new RedkaleException("http-path(" + path + ") is illegal");
			}
		}
		this.path = path;
		return this;
	}

	public WebRequest contextPath(String contextPath) {
		if (contextPath != null) {
			if (contextPath.indexOf(' ') >= 0 || contextPath.indexOf('\r') >= 0 || contextPath.indexOf('\n') >= 0) {
				throw new RedkaleException("http-context-path(" + contextPath + ") is illegal");
			}
		}
		this.contextPath = contextPath;
		return this;
	}

	public WebRequest bothConvertType(ConvertType convertType) {
		this.reqConvertType = convertType;
		this.respConvertType = convertType;
		return this;
	}

	public WebRequest reqConvertType(ConvertType reqConvertType) {
		this.reqConvertType = reqConvertType;
		return this;
	}

	public WebRequest respConvertType(ConvertType respConvertType) {
		this.respConvertType = respConvertType;
		return this;
	}

	public WebRequest remoteAddr(String remoteAddr) {
		this.remoteAddr = remoteAddr;
		return this;
	}

	public WebRequest locale(String locale) {
		this.locale = locale;
		return this;
	}

	public WebRequest sessionid(String sessionid) {
		this.sessionid = sessionid;
		return this;
	}

	public WebRequest contentType(String contentType) {
		this.contentType = contentType;
		return this;
	}

	public WebRequest currentUserid(Serializable userid) {
		this.currentUserid = userid;
		return this;
	}

	public WebRequest removeHeader(String name) {
		if (this.headers != null) {
			this.headers.remove(name);
		}
		return this;
	}

	public WebRequest removeParam(String name) {
		if (this.params != null) {
			this.params.remove(name);
		}
		return this;
	}

	public WebRequest headers(HttpHeaders header) {
		this.headers = header;
		return this;
	}

	public WebRequest params(HttpParameters params) {
		this.params = params;
		return this;
	}

	public WebRequest method(String method) {
		if (method != null) {
			if (method.indexOf(' ') >= 0 || method.indexOf('\r') >= 0 || method.indexOf('\n') >= 0) {
				throw new RedkaleException("http-method(" + method + ") is illegal");
			}
		}
		this.method = method;
		return this;
	}

	public WebRequest addHeader(String key, String value) {
		if (this.headers == null) {
			this.headers = HttpHeaders.create();
		}
		this.headers.add(key, value);
		return this;
	}

	public WebRequest addHeader(String key, TextConvert convert, Object value) {
		return addHeader(key, (convert == null ? JsonConvert.root() : convert).convertTo(value));
	}

	public WebRequest addHeader(String key, Object value) {
		return addHeader(key, JsonConvert.root().convertTo(value));
	}

	public WebRequest addHeader(String key, int value) {
		return addHeader(key, String.valueOf(value));
	}

	public WebRequest addHeader(String key, long value) {
		return addHeader(key, String.valueOf(value));
	}

	public WebRequest setHeader(String key, String value) {
		if (this.headers == null) {
			this.headers = HttpHeaders.create();
		}
		this.headers.set(key, value);
		return this;
	}

	public WebRequest setHeader(String key, TextConvert convert, Object value) {
		return setHeader(key, (convert == null ? JsonConvert.root() : convert).convertTo(value));
	}

	public WebRequest setHeader(String key, Object value) {
		return setHeader(key, JsonConvert.root().convertTo(value));
	}

	public WebRequest setHeader(String key, int value) {
		return setHeader(key, String.valueOf(value));
	}

	public WebRequest setHeader(String key, long value) {
		return setHeader(key, String.valueOf(value));
	}

	public WebRequest param(String key, String value) {
		if (this.params == null) {
			this.params = HttpParameters.create();
		}
		this.params.put(key, value);
		return this;
	}

	public WebRequest param(String key, TextConvert convert, Object value) {
		if (this.params == null) {
			this.params = HttpParameters.create();
		}
		if (convert == null) {
			convert = JsonConvert.root();
		}
		this.params.put(key, convert, value);
		return this;
	}

	public WebRequest param(String key, Object value) {
		return param(key, JsonConvert.root(), value);
	}

	public WebRequest body(byte[] body) {
		this.body = body;
		return this;
	}

	public WebRequest clearParams() {
		this.params = null;
		return this;
	}

	public WebRequest clearHeaders() {
		this.headers = null;
		return this;
	}

	public WebRequest clearRemoteAddr() {
		this.remoteAddr = null;
		return this;
	}

	public WebRequest clearLocale() {
		this.locale = null;
		return this;
	}

	public WebRequest clearSessionid() {
		this.sessionid = null;
		return this;
	}

	public WebRequest clearContentType() {
		this.contentType = null;
		return this;
	}

	public String getHeader(String name) {
		return getHeader(name, null);
	}

	public String getHeader(String name, String defaultValue) {
		return headers == null ? defaultValue : headers.firstValue(name, defaultValue);
	}

	public boolean isRpc() {
		return rpc;
	}

	public void setRpc(boolean rpc) {
		rpc(rpc);
	}

	public void setTraceid(String traceid) {
		traceid(traceid);
	}

	public String getMethod() {
		return method;
	}

	public void setMethod(String method) {
		method(method);
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		path(path);
	}

	public String getContextPath() {
		return contextPath;
	}

	public void setContextPath(String contextPath) {
		contextPath(contextPath);
	}

	public String getSessionid() {
		return sessionid;
	}

	public void setSessionid(String sessionid) {
		sessionid(sessionid);
	}

	public String getRemoteAddr() {
		return remoteAddr;
	}

	public void setRemoteAddr(String remoteAddr) {
		remoteAddr(remoteAddr);
	}

	public String getLocale() {
		return locale;
	}

	public void setLocale(String locale) {
		locale(locale);
	}

	public Serializable getCurrentUserid() {
		return currentUserid;
	}

	public void setCurrentUserid(Serializable currentUserid) {
		currentUserid(currentUserid);
	}

	public String getContentType() {
		return contentType;
	}

	public void setContentType(String contentType) {
		contentType(contentType);
	}

	public HttpHeaders getHeaders() {
		return headers;
	}

	public void setHeaders(HttpHeaders headers) {
		headers(headers);
	}

	public HttpParameters getParams() {
		return params;
	}

	public void setParams(HttpParameters params) {
		params(params);
	}

	public byte[] getBody() {
		return body;
	}

	public void setBody(byte[] body) {
		body(body);
	}

	public ConvertType getReqConvertType() {
		return reqConvertType;
	}

	public void setReqConvertType(ConvertType reqConvertType) {
		reqConvertType(reqConvertType);
	}

	public ConvertType getRespConvertType() {
		return respConvertType;
	}

	public void setRespConvertType(ConvertType respConvertType) {
		respConvertType(respConvertType);
	}

	@Override
	public String toString() {
		return JsonConvert.root().convertTo(this);
	}

	private static final byte[][] contentLengthArray = new byte[1000][];

	static {
		for (int i = 0; i < contentLengthArray.length; i++) {
			contentLengthArray[i] = ("Content-Length: " + i + "\r\n").getBytes();
		}
	}
}
