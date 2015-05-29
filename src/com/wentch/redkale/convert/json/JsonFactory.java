/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.convert.json;

import com.wentch.redkale.convert.ConvertType;
import com.wentch.redkale.convert.Factory;
import java.io.Serializable;

/**
 *
 * @author zhangjx
 */
public final class JsonFactory extends Factory<JsonReader, JsonWriter> {

    private static final JsonFactory instance = new JsonFactory(null, Boolean.getBoolean("convert.json.tiny"));

    static {
        instance.register(Serializable.class, instance.loadEncoder(Object.class));
    }

    private JsonFactory(JsonFactory parent, boolean tiny) {
        super(parent, tiny);
    }

    public static JsonFactory root() {
        return instance;
    }

    @Override
    public final JsonConvert getConvert() {
        if (convert == null) convert = new JsonConvert(this, tiny);
        return (JsonConvert) convert;
    }

    @Override
    public JsonFactory createChild() {
        return new JsonFactory(this, this.tiny);
    }

    @Override
    public JsonFactory createChild(boolean tiny) {
        return new JsonFactory(this, tiny);
    }

    @Override
    public ConvertType getConvertType() {
        return ConvertType.JSON;
    }

    @Override
    public boolean isReversible() {
        return false;
    }
}
