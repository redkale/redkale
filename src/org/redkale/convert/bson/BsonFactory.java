/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.bson;

import java.io.Serializable;
import java.util.*;
import org.redkale.convert.*;
import org.redkale.util.*;

/**
 * BSON的ConvertFactory
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@SuppressWarnings("unchecked")
public final class BsonFactory extends ConvertFactory<BsonReader, BsonWriter> {

    private static final BsonFactory instance = new BsonFactory(null, Boolean.getBoolean("convert.bson.tiny"));

    static final Decodeable objectDecoder = instance.loadDecoder(Object.class);

    static final Encodeable objectEncoder = instance.loadEncoder(Object.class);

    static final Decodeable collectionIntegerDecoder = instance.loadDecoder(new TypeToken<Collection<Integer>>() {
    }.getType());

    static final Decodeable collectionLongDecoder = instance.loadDecoder(new TypeToken<Collection<Long>>() {
    }.getType());

    static final Decodeable collectionStringDecoder = instance.loadDecoder(new TypeToken<Collection<String>>() {
    }.getType());

    static final Decodeable mapStringIntegerDecoder = instance.loadDecoder(new TypeToken<Map<String, Integer>>() {
    }.getType());

    static final Decodeable mapStringLongDecoder = instance.loadDecoder(new TypeToken<Map<String, Long>>() {
    }.getType());

    static final Decodeable mapStringStringDecoder = instance.loadDecoder(new TypeToken<Map<String, String>>() {
    }.getType());

    static {
        instance.register(Serializable.class, objectDecoder);
        instance.register(Serializable.class, objectEncoder);

        instance.register(AnyValue.class, instance.loadDecoder(AnyValue.DefaultAnyValue.class));
        instance.register(AnyValue.class, instance.loadEncoder(AnyValue.DefaultAnyValue.class));
    }

    private BsonFactory(BsonFactory parent, boolean tiny) {
        super(parent, tiny);
    }

    @Override
    public BsonFactory tiny(boolean tiny) {
        this.tiny = tiny;
        return this;
    }

    @Override
    public BsonFactory skipAllIgnore(final boolean skipIgnore) {
        this.registerSkipAllIgnore(skipIgnore);
        return this;
    }

    public static BsonFactory root() {
        return instance;
    }

    public static BsonFactory create() {
        return new BsonFactory(null, Boolean.getBoolean("convert.bson.tiny"));
    }

    @Override
    public final BsonConvert getConvert() {
        if (convert == null) convert = new BsonConvert(this, tiny);
        return (BsonConvert) convert;
    }

    @Override
    public BsonFactory createChild() {
        return new BsonFactory(this, this.tiny);
    }

    @Override
    public BsonFactory createChild(boolean tiny) {
        return new BsonFactory(this, tiny);
    }

    @Override
    public ConvertType getConvertType() {
        return ConvertType.BSON;
    }

    @Override
    public boolean isReversible() {
        return true;
    }

    @Override
    public boolean isFieldSort() {
        return true;
    }

}
