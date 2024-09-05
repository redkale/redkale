/*
 *
 */
package org.redkale.net.http;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import org.redkale.convert.ConvertDisabled;
import org.redkale.convert.TextConvert;
import org.redkale.convert.json.JsonConvert;
import org.redkale.util.RedkaleException;

/** @author zhangjx */
public class HttpParameters implements RestParams, Serializable {

    protected HashMap<String, String> map;

    protected HttpParameters() {}

    public static HttpParameters create() {
        return new HttpParameters();
    }

    public static HttpParameters of(String... items) {
        HttpParameters params = new HttpParameters();
        int len = items.length / 2;
        for (int i = 0; i < len; i++) {
            params.put(items[i * 2], items[i * 2 + 1]);
        }
        return params;
    }

    /**
     * 无需校验参数合法性
     *
     * @param map 参数
     * @return HttpParameters
     */
    public static HttpParameters ofValid(Map<String, String> map) {
        HttpParameters params = new HttpParameters();
        if (map != null) {
            params.map = map instanceof HashMap ? (HashMap) map : new HashMap(map);
        }
        return params;
    }

    @Override
    public String get(String name) {
        return get(name, null);
    }

    @Override
    public String get(String name, String defaultValue) {
        if (map == null) {
            return defaultValue;
        }
        return map.getOrDefault(name, defaultValue);
    }

    @Override
    public void forEach(BiConsumer<String, String> consumer) {
        if (map != null) {
            map.forEach(consumer);
        }
    }

    @Override
    public String[] names() {
        if (this.map == null) {
            return new String[0];
        }
        Set<String> names = this.map.keySet();
        return names.toArray(new String[names.size()]);
    }

    @Override
    public boolean contains(String name) {
        return this.map != null && this.map.containsKey(name);
    }

    public HttpParameters putAll(HttpParameters params) {
        if (params.map != null) {
            if (this.map == null) {
                this.map = new LinkedHashMap<>();
            }
            this.map.putAll(params.map);
        }
        return this;
    }

    public HttpParameters put(Map<String, String> values) {
        if (values != null) {
            values.forEach(this::put);
        }
        return this;
    }

    // 服务端接收，无需校验参数合法性
    void setValid(String name, String value) {
        if (this.map == null) {
            this.map = new LinkedHashMap<>();
        }
        this.map.put(name, value);
    }

    public HttpParameters put(String name, String value) {
        check(name, value);
        if (this.map == null) {
            this.map = new LinkedHashMap<>();
        }
        this.map.put(name, value);
        return this;
    }

    public HttpParameters put(String name, TextConvert convert, Object value) {
        return put(name, (convert == null ? JsonConvert.root() : convert).convertTo(value));
    }

    public HttpParameters put(String name, Object value) {
        return put(name, JsonConvert.root().convertTo(value));
    }

    public HttpParameters put(String name, boolean value) {
        return put(name, String.valueOf(value));
    }

    public HttpParameters put(String name, short value) {
        return put(name, String.valueOf(value));
    }

    public HttpParameters put(String name, int value) {
        return put(name, String.valueOf(value));
    }

    public HttpParameters put(String name, float value) {
        return put(name, String.valueOf(value));
    }

    public HttpParameters put(String name, long value) {
        return put(name, String.valueOf(value));
    }

    public HttpParameters put(String name, double value) {
        return put(name, String.valueOf(value));
    }

    public HttpParameters put(String name, BigInteger value) {
        return put(name, String.valueOf(value));
    }

    public HttpParameters remove(String name) {
        if (this.map != null) {
            this.map.remove(name);
        }
        return this;
    }

    @Override
    public Map<String, String> map() {
        return this.map;
    }

    @ConvertDisabled
    public boolean isEmpty() {
        return this.map == null || this.map.isEmpty();
    }

    public HttpParameters clear() {
        if (this.map != null) {
            this.map.clear();
        }
        return this;
    }

    protected String check(String name, String value) {
        if (name.indexOf(' ') >= 0 || name.indexOf('\r') >= 0 || name.indexOf('\n') >= 0) {
            throw new RedkaleException("http-param name(name = " + name + ") is illegal");
        }
        return value;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this.map);
    }
}
