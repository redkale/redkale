/*
 *
 */
package org.redkale.cache.spi;

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
public class CacheValue<T> {

	@ConvertColumn(index = 1)
	private T val;

	public CacheValue() {}

	protected CacheValue(T value) {
		this.val = value;
	}

	public static <T> CacheValue<T> create(T value) {
		return new CacheValue(value);
	}

	public static boolean isValid(CacheValue val) {
		return val != null;
	}

	public static <T> T get(CacheValue val) {
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
