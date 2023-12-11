/*
 *
 */
package org.redkale.caching;

import java.time.Duration;
import org.redkale.convert.ConvertColumn;
import org.redkale.convert.ConvertDisabled;
import org.redkale.convert.json.JsonConvert;

/**
 *
 * 缓存对象
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <T> 泛型
 *
 * @since 2.8.0
 */
public class CacheValue<T> {

    @ConvertColumn(index = 1)
    private T value;

    //为0表示不过期
    @ConvertColumn(index = 2)
    private long time;

    public CacheValue() {
    }

    protected CacheValue(T value, Duration expire) {
        this.value = value;
        this.time = expire == null ? 0 : (System.currentTimeMillis() + expire.toMillis());
    }

    public static <T> CacheValue<T> create(T value, Duration expire) {
        return new CacheValue(value, expire);
    }

    @ConvertDisabled
    public boolean isExpired() {
        return time > 0 && System.currentTimeMillis() > time;
    }

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public String toString() {
        return JsonConvert.root().convertTo(this);
    }
}
