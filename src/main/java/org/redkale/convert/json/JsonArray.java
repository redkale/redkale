/*
 *
 */
package org.redkale.convert.json;

import java.util.*;
import org.redkale.convert.ConvertDisabled;
import org.redkale.util.Utility;

/**
 * 常规json数组
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public class JsonArray extends ArrayList<Object> implements JsonEntity {

    public JsonArray() {
    }

    public JsonArray(Collection collection) {
        super(collection);
    }

    public JsonArray(Object... array) {
        super(Arrays.asList(array));
    }

    public static JsonArray convertFrom(String text) {
        return convertFrom(Utility.charArray(text));
    }

    public static JsonArray convertFrom(char[] text) {
        return convertFrom(text, 0, text.length);
    }

    public static JsonArray convertFrom(char[] text, final int offset, final int length) {
        return (JsonArray) JsonEntityDecoder.instance.convertFrom(new JsonReader(text, offset, length));
    }

    @Override
    @ConvertDisabled
    public final boolean isObject() {
        return false;
    }

    @Override
    @ConvertDisabled
    public final boolean isArray() {
        return true;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }
}
