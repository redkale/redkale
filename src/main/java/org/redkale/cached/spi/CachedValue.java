/*
 *
 */
package org.redkale.cached.spi;

import org.redkale.convert.ConvertColumn;
import org.redkale.convert.json.JsonConvert;

/**
 * 内部缓存对象
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <T> 泛型
 * @since 2.8.0
 */
public class CachedValue<T> {

    @ConvertColumn(index = 1)
    private T val;

    public CachedValue() {}

    protected CachedValue(T value) {
        this.val = value;
    }

    public static <T> CachedValue<T> create(T value) {
        return new CachedValue(value);
    }

    public static boolean isValid(CachedValue val) {
        return val != null;
    }

    public static <T> T get(CachedValue val) {
        return isValid(val) ? (T) val.getVal() : null;
    }

    public T getVal() {
        return val;
    }

    public void setVal(T val) {
        this.val = val;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }
}
