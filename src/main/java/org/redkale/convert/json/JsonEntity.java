/*
 *
 */
package org.redkale.convert.json;

import org.redkale.util.Utility;

/**
 * 常规json实体
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public interface JsonEntity extends java.io.Serializable {

    public boolean isObject();

    public boolean isArray();

    public static JsonEntity convertFrom(String text) {
        return convertFrom(Utility.charArray(text));
    }

    public static JsonEntity convertFrom(char[] text) {
        return convertFrom(text, 0, text.length);
    }

    public static JsonEntity convertFrom(char[] text, final int offset, final int length) {
        return JsonEntityDecoder.instance.convertFrom(new JsonReader(text, offset, length));
    }

}
