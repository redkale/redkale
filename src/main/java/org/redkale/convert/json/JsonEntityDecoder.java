/*
 *
 */
package org.redkale.convert.json;

import java.lang.reflect.Type;
import java.util.*;
import org.redkale.convert.AnyDecoder;
import org.redkale.convert.ext.StringSimpledCoder;
import org.redkale.util.*;

/**
 * 常规json
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
class JsonEntityDecoder extends AnyDecoder<JsonEntity> {

    private static final Type arrayType = new TypeToken<Collection<JsonEntity>>() {
    }.getType();

    private static final Type objectType = new TypeToken<Map<String, JsonEntity>>() {
    }.getType();

    private static final Creator<JsonArray> arrayCreator = Creator.create(JsonArray.class);

    private static final Creator<JsonObject> objectCreator = Creator.create(JsonObject.class);

    public static final JsonEntityDecoder instance = new JsonEntityDecoder();

    public JsonEntityDecoder() {
        super(objectCreator, objectType, arrayCreator, arrayType, StringSimpledCoder.instance);
    }

    @Override
    public Type getType() {
        return JsonEntity.class;
    }
}
