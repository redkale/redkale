/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.json;

import java.io.Serializable;
import java.lang.reflect.*;
import java.math.BigInteger;
import java.net.*;
import org.redkale.convert.*;
import org.redkale.convert.ext.*;
import org.redkale.util.*;

/**
 * JSON的ConvertFactory
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@SuppressWarnings("unchecked")
public final class JsonFactory extends ConvertFactory<JsonReader, JsonWriter> {

    private static final JsonFactory instance = new JsonFactory(null, getSystemPropertyBoolean("redkaleconvert.json.tiny", "redkale.convert.tiny", true));

    static {
        instance.register(Serializable.class, instance.loadEncoder(Object.class));

        //instance.register(AnyValue.class, instance.loadDecoder(AnyValue.DefaultAnyValue.class));
        //instance.register(AnyValue.class, instance.loadEncoder(AnyValue.DefaultAnyValue.class));
    }

    private JsonFactory(JsonFactory parent, boolean tiny) {
        super(parent, tiny);
        if (parent == null) {
            this.register(InetAddress.class, InetAddressSimpledCoder.InetAddressJsonSimpledCoder.instance);
            this.register(InetSocketAddress.class, InetAddressSimpledCoder.InetSocketAddressJsonSimpledCoder.instance);
            this.register(DLong.class, DLongSimpledCoder.DLongJsonSimpledCoder.instance);
            this.register(BigInteger.class, BigIntegerSimpledCoder.BigIntegerJsonSimpledCoder.instance);
            this.register(java.time.Instant.class, InstantSimpledCoder.InstantJsonSimpledCoder.instance);
            this.register(java.time.LocalDate.class, LocalDateSimpledCoder.LocalDateJsonSimpledCoder.instance);
            this.register(java.time.LocalTime.class, LocalTimeSimpledCoder.LocalTimeJsonSimpledCoder.instance);
            this.register(java.time.LocalDateTime.class, LocalDateTimeSimpledCoder.LocalDateTimeJsonSimpledCoder.instance);
        }
    }

    @Override
    public JsonFactory tiny(boolean tiny) {
        this.tiny = tiny;
        return this;
    }

    @Override
    public JsonFactory skipAllIgnore(final boolean skipIgnore) {
        this.registerSkipAllIgnore(skipIgnore);
        return this;
    }

    public static JsonFactory root() {
        return instance;
    }

    public static JsonFactory create() {
        return new JsonFactory(null, getSystemPropertyBoolean("redkale.convert.json.tiny", "convert.tiny", true));
    }

    @Override
    protected <E> Encodeable<JsonWriter, E> createDyncEncoder(Type type) {
        return JsonDynEncoder.createDyncEncoder(this, type);
    }

    @Override
    protected <E> ObjectEncoder<JsonWriter, E> createObjectEncoder(Type type) {
        return super.createObjectEncoder(type);
    }

    protected boolean tiny() {
        return this.tiny;
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

    @Override
    public boolean isFieldSort() {
        return true;
    }
}
