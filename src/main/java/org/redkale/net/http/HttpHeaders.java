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
    public boolean contains(String name) {
        return this.map != null && this.map.containsKey(name);
    }

    @Override
    public boolean containsIgnoreCase(String name) {
        if (this.map == null || name == null) {
            return false;
        }
        return !this.map.keySet().stream().filter(name::equalsIgnoreCase).findFirst().isEmpty();
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
    void addValid(String name, String value) {
        if (this.map == null) {
            this.map = new LinkedHashMap<>();
            this.map.put(name, value);
        } else {
            Serializable old = this.map.get(name);
            if (old == null) {
                this.map.put(name, value);
            } else if (old instanceof Collection) {
                ((Collection) old).add(value);
            } else {
                ArrayList list = new ArrayList();
                list.add(old);
                list.add(value);
                this.map.put(name, list);
            }
        }
    }

    public HttpHeaders add(String name, String value) {
        check(name, value);
        if (this.map == null) {
            this.map = new LinkedHashMap<>();
            this.map.put(name, value);
        } else {
            Serializable old = this.map.get(name);
            if (old == null) {
                this.map.put(name, value);
            } else if (old instanceof Collection) {
                ((Collection) old).add(value);
            } else {
                ArrayList list = new ArrayList();
                list.add(old);
                list.add(value);
                this.map.put(name, list);
            }
        }
        return this;
    }

    public HttpHeaders add(String name, List<String> value) {
        if (value.isEmpty()) {
            return this;
        }
        for (String val : value) {
            check(name, val);
        }
        if (this.map == null) {
            this.map = new LinkedHashMap<>();
            this.map.put(name, new ArrayList(value));
        } else {
            Serializable old = this.map.get(name);
            if (old == null) {
                this.map.put(name, new ArrayList(value));
            } else if (old instanceof Collection) {
                ((Collection) old).addAll(value);
            } else {
                ArrayList list = new ArrayList();
                list.add(old);
                list.addAll(value);
                this.map.put(name, list);
            }
        }
        return this;
    }

    public HttpHeaders add(String name, TextConvert convert, Object value) {
        return add(name, (convert == null ? JsonConvert.root() : convert).convertTo(value));
    }

    public HttpHeaders add(String name, Object value) {
        return add(name, JsonConvert.root().convertTo(value));
    }

    public HttpHeaders add(String name, boolean value) {
        return add(name, String.valueOf(value));
    }

    public HttpHeaders add(String name, short value) {
        return add(name, String.valueOf(value));
    }

    public HttpHeaders add(String name, int value) {
        return add(name, String.valueOf(value));
    }

    public HttpHeaders add(String name, float value) {
        return add(name, String.valueOf(value));
    }

    public HttpHeaders add(String name, long value) {
        return add(name, String.valueOf(value));
    }

    public HttpHeaders add(String name, double value) {
        return add(name, String.valueOf(value));
    }

    public HttpHeaders add(String name, BigInteger value) {
        return add(name, String.valueOf(value));
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
    void setValid(String name, String value) {
        if (this.map == null) {
            this.map = new LinkedHashMap<>();
        }
        this.map.put(name, value);
    }

    public HttpHeaders set(String name, String value) {
        check(name, value);
        if (this.map == null) {
            this.map = new LinkedHashMap<>();
        }
        this.map.put(name, value);
        return this;
    }

    public HttpHeaders set(String name, List<String> value) {
        if (value.isEmpty()) {
            return this;
        }
        for (String val : value) {
            check(name, val);
        }
        if (this.map == null) {
            this.map = new LinkedHashMap<>();
        }
        this.map.put(name, new ArrayList(value));
        return this;
    }

    public HttpHeaders set(String name, TextConvert convert, Object value) {
        return set(name, (convert == null ? JsonConvert.root() : convert).convertTo(value));
    }

    public HttpHeaders set(String name, Object value) {
        return set(name, JsonConvert.root().convertTo(value));
    }

    public HttpHeaders set(String name, boolean value) {
        return set(name, String.valueOf(value));
    }

    public HttpHeaders set(String name, short value) {
        return set(name, String.valueOf(value));
    }

    public HttpHeaders set(String name, int value) {
        return set(name, String.valueOf(value));
    }

    public HttpHeaders set(String name, float value) {
        return set(name, String.valueOf(value));
    }

    public HttpHeaders set(String name, long value) {
        return set(name, String.valueOf(value));
    }

    public HttpHeaders set(String name, double value) {
        return set(name, String.valueOf(value));
    }

    public HttpHeaders set(String name, BigInteger value) {
        return set(name, String.valueOf(value));
    }

    public HttpHeaders remove(String name) {
        if (this.map != null) {
            this.map.remove(name);
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

    protected String check(String name, String value) {
        if (name.indexOf('\r') >= 0 || name.indexOf('\n') >= 0) {
            throw new RedkaleException("http-header name(name = " + name + ") is illegal");
        }
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new RedkaleException("http-header value(name = " + name + ", value = " + value + ") is illegal");
        }
        return value;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this.map);
    }
}
