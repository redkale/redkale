/*
 *
 */
package org.redkale.scheduled;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import org.redkale.convert.json.JsonConvert;
import org.redkale.util.Utility;

/**
 * 定时任务的参数
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public final class ScheduledEvent {

    private final Map<String, Object> map;

    public ScheduledEvent() {
        this.map = new HashMap<>();
    }

    public ScheduledEvent(Map<String, Object> map) {
        this.map = map;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String name) {
        return (T) map.get(name);
    }

    public <T> T getJson(String name, Type type) {
        Object obj = get(name);
        if (obj == null) {
            return null;
        } else if (type instanceof Class && ((Class) type).isAssignableFrom(obj.getClass())) {
            return (T) obj;
        } else {
            return JsonConvert.root().convertFrom(type, obj.toString());
        }
    }

    public String getString(String name) {
        return Utility.convertValue(String.class, map.get(name));
    }

    public Integer getInteger(String name) {
        return Utility.convertValue(int.class, map.get(name));
    }

    public Long getLong(String name) {
        return Utility.convertValue(Long.class, map.get(name));
    }

    public int getInt(String name, int defValue) {
        Object val = map.get(name);
        if (val == null) {
            return defValue;
        }
        return Utility.convertValue(int.class, val);
    }

    public long getLong(String name, long defValue) {
        Object val = map.get(name);
        if (val == null) {
            return defValue;
        }
        return Utility.convertValue(long.class, val);
    }

    public ScheduledEvent clear() {
        map.clear();
        return this;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(map);
    }
}
