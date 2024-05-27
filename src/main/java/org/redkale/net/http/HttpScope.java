/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this referid file, choose Tools | Templates
 * and open the referid in the editor.
 */
package org.redkale.net.http;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.function.*;
import org.redkale.convert.*;
import org.redkale.convert.json.*;
import org.redkale.persistence.*;

/**
 * HTTP输出引擎的对象域 <br>
 * 输出引擎的核心类, 业务开发人员只有通过本类对象才能调用到输出引擎功能。 <br>
 *
 * <p>HttpServlet调用: <br>
 *
 * <pre>
 *    &#064;HttpMapping(url = "/hello.html", auth = false)
 *    public void hello(HttpRequest req, HttpResponse resp) throws IOException {
 *        resp.finish(HttpScope.refer("/hello.html").attr("content", "哈哈"));
 *    }
 * </pre>
 *
 * <p>RestService调用: <br>
 *
 * <pre>
 *    &#064;RestMapping(name = "hello.html", auth = false)
 *    public HttpScope hello() {
 *       return HttpScope.refer("hello.html").attr("content", "哈哈");
 *    }
 * </pre>
 *
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class HttpScope {

	public static final Object NIL = new Object();

	@ConvertColumn(index = 1)
	protected String referid;

	// @since 2.7.0
	@ConvertColumn(index = 2)
	protected Object referObj;

	@ConvertColumn(index = 3)
	protected Map<String, Object> attributes;

	// @since 2.4.0
	@Transient
	protected Function<String, Object> attrFunction;

	// @since 2.4.0
	@ConvertColumn(index = 4)
	protected Map<String, String> headers;

	// @since 2.4.0
	@ConvertColumn(index = 5)
	protected List<HttpCookie> cookies;

	public static HttpScope refer(String template) {
		HttpScope rs = new HttpScope();
		rs.setReferid(template);
		return rs;
	}

	public static HttpScope create(String template, String name, Object value) {
		HttpScope rs = new HttpScope();
		rs.setReferid(template);
		rs.attr(name, value);
		return rs;
	}

	public static HttpScope create(String template, String name1, Object value1, String name2, Object value2) {
		HttpScope rs = new HttpScope();
		rs.setReferid(template);
		rs.attr(name1, value1).attr(name2, value2);
		return rs;
	}

	public static HttpScope create(
			String template, String name1, Object value1, String name2, Object value2, String name3, Object value3) {
		HttpScope rs = new HttpScope();
		rs.setReferid(template);
		rs.attr(name1, value1).attr(name2, value2).attr(name3, value3);
		return rs;
	}

	public static HttpScope create(
			String template,
			String name1,
			Object value1,
			String name2,
			Object value2,
			String name3,
			Object value3,
			String name4,
			Object value4) {
		HttpScope rs = new HttpScope();
		rs.setReferid(template);
		rs.attr(name1, value1).attr(name2, value2).attr(name3, value3).attr(name4, value4);
		return rs;
	}

	public static HttpScope create(
			String template,
			String name1,
			Object value1,
			String name2,
			Object value2,
			String name3,
			Object value3,
			String name4,
			Object value4,
			String name5,
			Object value5) {
		HttpScope rs = new HttpScope();
		rs.setReferid(template);
		rs.attr(name1, value1)
				.attr(name2, value2)
				.attr(name3, value3)
				.attr(name4, value4)
				.attr(name5, value5);
		return rs;
	}

	public static HttpScope create(String template, Function<String, Object> attrFunction) {
		HttpScope rs = new HttpScope();
		rs.setReferid(template);
		rs.attrFunction = attrFunction;
		return rs;
	}

	public boolean recycle() {
		this.referid = null;
		this.referObj = null;
		this.attributes = null;
		this.attrFunction = null;
		this.headers = null;
		this.cookies = null;
		return true;
	}

	public HttpScope referObj(Object value) {
		this.referObj = value;
		return this;
	}

	public HttpScope attrFunc(Function<String, Object> attrFunction) {
		this.attrFunction = attrFunction;
		return this;
	}

	public HttpScope appendAttrFunc(final String key, Supplier supplier) {
		if (supplier == null) {
			return this;
		}
		return appendAttrFunc(k -> k.equals(key) ? supplier.get() : null);
	}

	public HttpScope appendAttrFunc(final Function<String, Object> attrFunc) {
		if (attrFunc == null) {
			return this;
		}
		final Function<String, Object> old = this.attrFunction;
		if (old == null) {
			this.attrFunction = attrFunc;
		} else {
			this.attrFunction = key -> {
				Object r = old.apply(key);
				return r == null ? attrFunc.apply(key) : r;
			};
		}
		return this;
	}

	public HttpScope attr(Map<String, ?> map) {
		if (map == null) {
			return this;
		}
		if (this.attributes == null) {
			this.attributes = new LinkedHashMap<>();
		}
		this.attributes.putAll(map);
		return this;
	}

	public HttpScope attr(String name, Object value) {
		if (name == null || value == null) {
			return this;
		}
		if (this.attributes == null) {
			this.attributes = new LinkedHashMap<>();
		}
		this.attributes.put(name, value);
		return this;
	}

	@SuppressWarnings("unchecked")
	public <T> T find(String name) {
		return this.attributes == null ? null : (T) this.attributes.get(name);
	}

	@SuppressWarnings("unchecked")
	public <T> T find(HttpScope parent, String name) {
		T rs = this.attributes == null ? null : (T) this.attributes.get(name);
		if (rs != null) {
			return rs;
		}
		return parent == null ? null : parent.find(name);
	}

	public void forEach(BiConsumer<String, Object> action) {
		if (this.attributes == null) {
			return;
		}
		this.attributes.forEach(action);
	}

	public HttpScope header(String name, Serializable value) {
		if (this.headers == null) {
			this.headers = new HashMap<>();
		}
		this.headers.put(name, String.valueOf(value));
		return this;
	}

	public HttpScope cookie(String name, Serializable value) {
		return cookie(new HttpCookie(name, String.valueOf(value)));
	}

	public HttpScope cookie(String name, Serializable value, boolean httpOnly) {
		HttpCookie c = new HttpCookie(name, String.valueOf(value));
		c.setHttpOnly(httpOnly);
		return cookie(c);
	}

	public HttpScope cookie(HttpCookie cookie) {
		if (this.cookies == null) {
			this.cookies = new ArrayList<>();
		}
		this.cookies.add(cookie);
		return this;
	}

	public String getHeader(String name) {
		return headers == null ? null : headers.get(name);
	}

	public String getHeader(String name, String dfvalue) {
		return headers == null ? null : headers.getOrDefault(name, dfvalue);
	}

	public Map<String, String> getHeaders() {
		return headers;
	}

	public void setHeaders(Map<String, String> headers) {
		this.headers = headers;
	}

	public List<HttpCookie> getCookies() {
		return cookies;
	}

	public void setCookies(List<HttpCookie> cookies) {
		this.cookies = cookies;
	}

	public String getReferid() {
		return referid;
	}

	public void setReferid(String referid) {
		this.referid = referid;
	}

	public Object getReferObj() {
		return referObj;
	}

	public void setReferObj(Object referObj) {
		this.referObj = referObj;
	}

	public Map<String, Object> getAttributes() {
		final Function<String, Object> attrFunc = this.attrFunction;
		if (attrFunc != null) {
			if (this.attributes == null) {
				this.attributes = new LinkedHashMap<>();
			}
			return new LinkedHashMap(this.attributes) {
				@Override
				public Object get(Object key) {
					if (containsKey(key)) {
						return super.get(key);
					} else {
						Object val = attrFunc.apply(key.toString());
						if (val == NIL) {
							return null;
						}
						put(key.toString(), val);
						return val;
					}
				}
			};
		}
		return this.attributes;
	}

	@ConvertDisabled(type = ConvertType.JSON)
	public void setAttributes(Map<String, Object> attributes) {
		this.attributes = attributes;
	}

	@Override
	public String toString() {
		return JsonConvert.root().convertTo(this);
	}
}
