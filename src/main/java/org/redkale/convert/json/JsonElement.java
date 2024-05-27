/*
 *
 */
package org.redkale.convert.json;

import org.redkale.util.Utility;

/**
 * 常规json实体
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public interface JsonElement extends java.io.Serializable {

	public boolean isObject();

	public boolean isArray();

	public boolean isString();

	public static JsonElement convertFrom(String text) {
		return convertFrom(Utility.charArray(text));
	}

	public static JsonElement convertFrom(char[] text) {
		return convertFrom(text, 0, text.length);
	}

	public static JsonElement convertFrom(char[] text, final int offset, final int length) {
		Object val = JsonConvert.root().convertFrom(JsonElement.class, text, offset, length);
		if (val instanceof CharSequence) {
			return new JsonString(val.toString());
		}
		return (JsonElement) val;
	}
}
