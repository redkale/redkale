/*
 *
 */
package org.redkale.convert.json;

import org.redkale.convert.ConvertDisabled;

/**
 * 常规json字符串
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public class JsonString implements CharSequence, JsonElement, Comparable<JsonString> {

	private String value;

	public JsonString() {}

	public JsonString(String value) {
		this.value = value;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	@ConvertDisabled
	public boolean isNull() {
		return value == null;
	}

	@Override
	public int length() {
		return value.length();
	}

	@Override
	public char charAt(int index) {
		return value.charAt(index);
	}

	@Override
	public CharSequence subSequence(int start, int end) {
		return value.substring(end, end);
	}

	@Override
	public int compareTo(JsonString o) {
		return o == null || o.value == null
				? (value == null ? 0 : 1)
				: (this.value == null ? -1 : this.value.compareTo(o.value));
	}

	@Override
	@ConvertDisabled
	public final boolean isObject() {
		return false;
	}

	@Override
	@ConvertDisabled
	public final boolean isArray() {
		return false;
	}

	@Override
	@ConvertDisabled
	public final boolean isString() {
		return true;
	}

	@Override
	public String toString() {
		return value;
	}
}
