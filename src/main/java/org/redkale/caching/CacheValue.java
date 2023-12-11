/*
 *
 */
package org.redkale.caching;

import java.time.Duration;
import org.redkale.convert.ConvertColumn;
import org.redkale.convert.json.JsonConvert;

/**
 *
 * 内部缓存对象
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <T> 泛型
 *
 * @since 2.8.0
 */
public class CacheValue<T> extends CacheExpire {

    @ConvertColumn(index = 2)
    private T value;

    public CacheValue() {
    }

    protected CacheValue(T value, Duration expire) {
        this.value = value;
        this.time = expire == null ? 0 : (System.currentTimeMillis() + expire.toMillis());
    }

    public static <T> CacheValue<T> create(T value, Duration expire) {
        return new CacheValue(value, expire);
    }

    public static boolean isValid(CacheValue val) {
        return val != null && !val.isExpired();
    }

    public static <T> T get(CacheValue val) {
        return isValid(val) ? (T) val.getValue() : null;
    }

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }

    public String toString() {
        return JsonConvert.root().convertTo(this);
    }
}
