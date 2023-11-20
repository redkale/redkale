/*
 *
 */
package org.redkale.net.http;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import org.redkale.convert.TextConvert;
import org.redkale.convert.json.JsonConvert;
import org.redkale.util.RedkaleException;

/**
 * Http Header Object
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public class HttpHeaders implements RestHeaders, Serializable {

    //value值只能是String、List<String>
    protected LinkedHashMap<String, Serializable> map;

    protected HttpHeaders() {
    }

    public static HttpHeaders create() {
        return new HttpHeaders();
    }

    public static HttpHeaders of(String... items) {
        HttpHeaders header = new HttpHeaders();
        int len = items.length / 2;
        for (int i = 0; i < len; i++) {
            header.add(items[i * 2], items[i * 2 + 1]);
        }
        return header;
    }

    /**
     * 无需校验参数合法性
     *
     * @param map 参数
     *
     * @return HttpHeaders
     */
    public static HttpHeaders ofValid(Map<String, Serializable> map) {
        HttpHeaders header = new HttpHeaders();
        if (map != null) {
            header.map = map instanceof LinkedHashMap ? (LinkedHashMap) map : new LinkedHashMap(map);
        }
        return header;
    }

    @Override
    public String firstValue(String name) {
        return firstValue(name, null);
    }

    @Override
    public String firstValue(String name, String defaultValue) {
        if (map == null) {
            return defaultValue;
        }
        Serializable val = map.get(name);
        if (val == null) {
            return defaultValue;
        }
        if (val instanceof Collection) {
            for (Object item : (Collection) val) {
                return String.valueOf(item); //return fisrt value
            }
            return defaultValue;
        }
        return String.valueOf(val);
    }

    @Override
    public List<String> listValue(String name) {
        if (this.map == null) {
            return null;
        }
        Serializable val = this.map.get(name);
        if (val == null) {
            return null;
        }
        if (val instanceof Collection) {
            return new ArrayList<>((Collection) val);
        }
        List list = new ArrayList<>();
        list.add(val);
        return list;
    }

    @Override
    public void forEach(BiConsumer<String, String> consumer) {
        if (map != null) {
            map.forEach((k, v) -> {
                if (v instanceof Collection) {
                    for (Object item : (Collection) v) {
                        consumer.accept(k, item == null ? null : item.toString());
                    }
                } else {
                    consumer.accept(k, v == null ? null : v.toString());
                }
            });
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
    public boolean contains(String key) {
        return this.map != null && this.map.containsKey(key);
    }

    public HttpHeaders addAll(HttpHeaders header) {
        if (header.map != null) {
            if (this.map == null) {
                this.map = new LinkedHashMap<>(header.map);
            } else {
                header.forEach(this::add);
            }
        }
        return this;
    }

    public HttpHeaders add(Map<String, String> values) {
        if (values != null) {
            values.forEach(this::add);
        }
        return this;
    }

    //服务端接收，无需校验参数合法性
    void addValid(String key, String value) {
        if (this.map == null) {
            this.map = new LinkedHashMap<>();
            this.map.put(key, value);
        } else {
            Serializable old = this.map.get(key);
            if (old == null) {
                this.map.put(key, value);
            } else if (old instanceof Collection) {
                ((Collection) old).add(value);
            } else {
                ArrayList list = new ArrayList();
                list.add(old);
                list.add(value);
                this.map.put(key, list);
            }
        }
    }

    public HttpHeaders add(String key, String value) {
        check(key, value);
        if (this.map == null) {
            this.map = new LinkedHashMap<>();
            this.map.put(key, value);
        } else {
            Serializable old = this.map.get(key);
            if (old == null) {
                this.map.put(key, value);
            } else if (old instanceof Collection) {
                ((Collection) old).add(value);
            } else {
                ArrayList list = new ArrayList();
                list.add(old);
                list.add(value);
                this.map.put(key, list);
            }
        }
        return this;
    }

    public HttpHeaders add(String key, List<String> value) {
        if (value.isEmpty()) {
            return this;
        }
        for (String val : value) {
            check(key, val);
        }
        if (this.map == null) {
            this.map = new LinkedHashMap<>();
            this.map.put(key, new ArrayList(value));
        } else {
            Serializable old = this.map.get(key);
            if (old == null) {
                this.map.put(key, new ArrayList(value));
            } else if (old instanceof Collection) {
                ((Collection) old).addAll(value);
            } else {
                ArrayList list = new ArrayList();
                list.add(old);
                list.addAll(value);
                this.map.put(key, list);
            }
        }
        return this;
    }

    public HttpHeaders add(String key, TextConvert convert, Object value) {
        return add(key, (convert == null ? JsonConvert.root() : convert).convertTo(value));
    }

    public HttpHeaders add(String key, Object value) {
        return add(key, JsonConvert.root().convertTo(value));
    }

    public HttpHeaders add(String key, boolean value) {
        return add(key, String.valueOf(value));
    }

    public HttpHeaders add(String key, short value) {
        return add(key, String.valueOf(value));
    }

    public HttpHeaders add(String key, int value) {
        return add(key, String.valueOf(value));
    }

    public HttpHeaders add(String key, float value) {
        return add(key, String.valueOf(value));
    }

    public HttpHeaders add(String key, long value) {
        return add(key, String.valueOf(value));
    }

    public HttpHeaders add(String key, double value) {
        return add(key, String.valueOf(value));
    }

    public HttpHeaders add(String key, BigInteger value) {
        return add(key, String.valueOf(value));
    }

    public HttpHeaders setAll(HttpHeaders header) {
        if (header.map != null) {
            if (this.map == null) {
                this.map = new LinkedHashMap<>();
            }
            this.map.putAll(header.map);
        }
        return this;
    }

    public HttpHeaders set(Map<String, String> values) {
        if (values != null) {
            values.forEach(this::set);
        }
        return this;
    }

    //服务端接收，无需校验参数合法性
    void setValid(String key, String value) {
        if (this.map == null) {
            this.map = new LinkedHashMap<>();
        }
        this.map.put(key, value);
    }

    public HttpHeaders set(String key, String value) {
        check(key, value);
        if (this.map == null) {
            this.map = new LinkedHashMap<>();
        }
        this.map.put(key, value);
        return this;
    }

    public HttpHeaders set(String key, List<String> value) {
        if (value.isEmpty()) {
            return this;
        }
        for (String val : value) {
            check(key, val);
        }
        if (this.map == null) {
            this.map = new LinkedHashMap<>();
        }
        this.map.put(key, new ArrayList(value));
        return this;
    }

    public HttpHeaders set(String key, TextConvert convert, Object value) {
        return set(key, (convert == null ? JsonConvert.root() : convert).convertTo(value));
    }

    public HttpHeaders set(String key, Object value) {
        return set(key, JsonConvert.root().convertTo(value));
    }

    public HttpHeaders set(String key, boolean value) {
        return set(key, String.valueOf(value));
    }

    public HttpHeaders set(String key, short value) {
        return set(key, String.valueOf(value));
    }

    public HttpHeaders set(String key, int value) {
        return set(key, String.valueOf(value));
    }

    public HttpHeaders set(String key, float value) {
        return set(key, String.valueOf(value));
    }

    public HttpHeaders set(String key, long value) {
        return set(key, String.valueOf(value));
    }

    public HttpHeaders set(String key, double value) {
        return set(key, String.valueOf(value));
    }

    public HttpHeaders set(String key, BigInteger value) {
        return set(key, String.valueOf(value));
    }

    public HttpHeaders remove(String key) {
        if (this.map != null) {
            this.map.remove(key);
        }
        return this;
    }

    @Override
    public Map<String, Serializable> map() {
        return this.map;
    }

    public boolean isEmpty() {
        return this.map == null || this.map.isEmpty();
    }

    public HttpHeaders clear() {
        if (this.map != null) {
            this.map.clear();
        }
        return this;
    }

    protected String check(String key, String value) {
        if (key.indexOf('\r') >= 0 || key.indexOf('\n') >= 0) {
            throw new RedkaleException("http-header name(name = " + key + ") is illegal");
        }
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new RedkaleException("http-header value(name = " + key + ", value = " + value + ") is illegal");
        }
        return value;
    }

    @Override
    public String toString() {
        return String.valueOf(this.map);
    }
}
