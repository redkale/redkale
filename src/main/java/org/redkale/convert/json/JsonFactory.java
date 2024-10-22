/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.json;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.math.*;
import java.net.*;
import org.redkale.convert.*;
import org.redkale.util.Uint128;

/**
 * JSON的ConvertFactory
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
@SuppressWarnings("unchecked")
public final class JsonFactory extends ConvertFactory<JsonReader, JsonWriter> {

    private static final JsonFactory instance = new JsonFactory(
            null,
            getSystemPropertyInt("redkale.convert.json.tiny", "redkale.convert.tiny", false, Convert.FEATURE_TINY)
                    | getSystemPropertyInt(
                            "redkale.convert.json.nullable",
                            "redkale.convert.nullable",
                            false,
                            Convert.FEATURE_NULLABLE));

    static {
        instance.register(Serializable.class, instance.loadEncoder(Object.class));
    }

    private JsonFactory(JsonFactory parent, int features) {
        super(parent, features);
    }

    @Override
    protected void initSimpleCoderInRoot() {
        super.initSimpleCoderInRoot();
        this.register(Object.class, new JsonAnyDecoder(this));
        this.register(Object.class, new JsonAnyEncoder(this));
        this.register(InetAddress.class, JsonCoders.InetAddressJsonSimpledCoder.instance);
        this.register(InetSocketAddress.class, JsonCoders.InetSocketAddressJsonSimpledCoder.instance);
        this.register(Uint128.class, JsonCoders.Uint128JsonSimpledCoder.instance);
        this.register(BigInteger.class, JsonCoders.BigIntegerJsonSimpledCoder.instance);
        this.register(BigDecimal.class, JsonCoders.BigDecimalJsonSimpledCoder.instance);
        this.register(java.time.Instant.class, JsonCoders.InstantJsonSimpledCoder.instance);
        this.register(java.time.LocalDate.class, JsonCoders.LocalDateJsonSimpledCoder.instance);
        this.register(java.time.LocalTime.class, JsonCoders.LocalTimeJsonSimpledCoder.instance);
        this.register(java.time.LocalDateTime.class, JsonCoders.LocalDateTimeJsonSimpledCoder.instance);

        this.register(JsonElement.class, (Decodeable) JsonElementDecoder.instance);
        this.register(JsonString.class, (Decodeable) JsonElementDecoder.instance);
        this.register(JsonObject.class, (Decodeable) JsonElementDecoder.instance);
        this.register(JsonArray.class, (Decodeable) JsonElementDecoder.instance);
    }

    @Override
    public JsonFactory withFeatures(int features) {
        return super.withFeatures(features);
    }

    @Override
    public JsonFactory addFeature(int feature) {
        return super.addFeature(feature);
    }

    @Override
    public JsonFactory removeFeature(int feature) {
        return super.removeFeature(feature);
    }

    @Override
    public JsonFactory withTinyFeature(boolean tiny) {
        return super.withTinyFeature(tiny);
    }

    @Override
    public JsonFactory withNullableFeature(boolean nullable) {
        return super.withNullableFeature(nullable);
    }

    @Override
    public JsonFactory skipAllIgnore(final boolean skipIgnore) {
        this.registerSkipAllIgnore(skipIgnore);
        return this;
    }

    @Override
    protected ConvertFactory rootFactory() {
        return instance;
    }

    public static JsonFactory root() {
        return instance;
    }

    public static JsonFactory create() {
        return new JsonFactory(null, instance.getFeatures());
    }

    @Override
    protected <E> Encodeable<JsonWriter, E> createDyncEncoder(Type type) {
        return JsonDynEncoder.createDyncEncoder(this, type);
    }

    @Override
    protected <E> ObjectEncoder<JsonWriter, E> createObjectEncoder(Type type) {
        return super.createObjectEncoder(type);
    }

    @Override
    protected <E> Decodeable<JsonReader, E> createMultiImplDecoder(Class[] types) {
        return new JsonMultiImplDecoder(this, types);
    }

    @Override
    public final JsonConvert getConvert() {
        if (convert == null) {
            convert = new JsonConvert(this, features);
        }
        return (JsonConvert) convert;
    }

    @Override
    public JsonFactory createChild() {
        return new JsonFactory(this, this.features);
    }

    @Override
    public JsonFactory createChild(int features) {
        return new JsonFactory(this, features);
    }

    @Override
    public ConvertType getConvertType() {
        return ConvertType.JSON;
    }

    @Override
    public boolean isReversible() {
        return false;
    }

    @Override
    public boolean isFieldSort() {
        return true;
    }
}
