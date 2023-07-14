/*
 *
 */
package org.redkale.convert.json;

import java.util.*;
import org.redkale.convert.ConvertDisabled;
import org.redkale.util.Utility;

/**
 * 常规json对象
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public class JsonObject extends LinkedHashMap<String, Object> implements JsonEntity {

    public JsonObject() {
    }

    public JsonObject(Map map) {
        super(map);
    }

    public static JsonObject convertFrom(String text) {
        return convertFrom(Utility.charArray(text));
    }

    public static JsonObject convertFrom(char[] text) {
        return convertFrom(text, 0, text.length);
    }

    public static JsonObject convertFrom(char[] text, final int offset, final int length) {
        return (JsonObject) JsonEntityDecoder.instance.convertFrom(new JsonReader(text, offset, length));
    }

    @Override
    @ConvertDisabled
    public final boolean isObject() {
        return true;
    }

    @Override
    @ConvertDisabled
    public final boolean isArray() {
        return false;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }

}
