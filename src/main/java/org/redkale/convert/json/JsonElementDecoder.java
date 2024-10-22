/*
 *
 */
package org.redkale.convert.json;

import java.lang.reflect.Type;
import java.util.*;
import org.redkale.convert.ext.StringSimpledCoder;
import org.redkale.util.*;

/**
 * 常规json
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
class JsonElementDecoder extends JsonAnyDecoder<JsonElement> {

    private static final Type arrayType = new TypeToken<Collection<JsonElement>>() {}.getType();

    private static final Type objectType = new TypeToken<Map<String, JsonElement>>() {}.getType();

    private static final Creator<JsonArray> arrayCreator = t -> new JsonArray();

    private static final Creator<JsonObject> objectCreator = t -> new JsonObject();

    public static final JsonElementDecoder instance = new JsonElementDecoder();

    public JsonElementDecoder() {
        super(objectCreator, objectType, arrayCreator, arrayType, StringSimpledCoder.instance);
    }

    @Override
    public Type getType() {
        return JsonElement.class;
    }
}
