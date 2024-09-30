/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.bson;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Stream;
import org.redkale.convert.*;
import org.redkale.convert.ext.*;
import org.redkale.util.TypeToken;

/**
 * BSON的ConvertFactory
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
@SuppressWarnings("unchecked")
public final class BsonFactory extends ConvertFactory<BsonReader, BsonWriter> {

    private static final BsonFactory instance = new BsonFactory(
            null,
            getSystemPropertyInt("redkale.convert.bson.tiny", "redkale.convert.tiny", true, Convert.FEATURE_TINY)
                    | getSystemPropertyInt(
                            "redkale.convert.bson.nullable",
                            "redkale.convert.nullable",
                            false,
                            Convert.FEATURE_NULLABLE));

    static final Decodeable objectDecoder = instance.loadDecoder(Object.class);

    static final Encodeable objectEncoder = instance.loadEncoder(Object.class);

    // only for BsonRead.skipValue
    static final Decodeable skipArrayDecoder = new BsonArrayDecoder(instance, Object[].class);

    // only for BsonRead.skipValue
    static final Decodeable skipCollectionDecoder = new BsonCollectionDecoder(instance, Collection.class);

    // only for BsonRead.skipValue
    static final Decodeable skipStreamDecoder = new BsonStreamDecoder(instance, Stream.class);

    // only for BsonRead.skipValue
    static final Decodeable skipMapDecoder = new BsonMapDecoder(instance, Map.class);

    static {
        instance.register(Serializable.class, objectDecoder);
        instance.register(Serializable.class, objectEncoder);

        // instance.register(AnyValue.class, instance.loadDecoder(SimpleAnyValue.class));
        // instance.register(AnyValue.class, instance.loadEncoder(SimpleAnyValue.class));
    }

    private BsonFactory(BsonFactory parent, int features) {
        super(parent, features);
    }

    @Override
    public BsonFactory withFeatures(int features) {
        return super.withFeatures(features);
    }

    @Override
    public BsonFactory addFeature(int feature) {
        return super.addFeature(feature);
    }

    @Override
    public BsonFactory removeFeature(int feature) {
        return super.removeFeature(feature);
    }

    @Override
    public BsonFactory withTinyFeature(boolean tiny) {
        return super.withTinyFeature(tiny);
    }

    @Override
    public BsonFactory withNullableFeature(boolean nullable) {
        return super.withNullableFeature(nullable);
    }

    @Override
    public BsonFactory skipAllIgnore(final boolean skipIgnore) {
        this.registerSkipAllIgnore(skipIgnore);
        return this;
    }

    @Override
    protected ConvertFactory rootFactory() {
        return instance;
    }

    public static BsonFactory root() {
        return instance;
    }

    public static BsonFactory create() {
        return new BsonFactory(null, instance.features);
    }

    @Override
    public final BsonConvert getConvert() {
        if (convert == null) {
            convert = new BsonConvert(this, features);
        }
        return (BsonConvert) convert;
    }

    @Override
    public BsonFactory createChild() {
        return new BsonFactory(this, features);
    }

    @Override
    public BsonFactory createChild(int features) {
        return new BsonFactory(this, features);
    }

    @Override
    protected <E> Decodeable<BsonReader, E> createArrayDecoder(Type type) {
        return new BsonArrayDecoder(this, type);
    }

    @Override
    protected <E> Decodeable<BsonReader, E> createCollectionDecoder(Type type) {
        return new BsonCollectionDecoder(this, type);
    }

    @Override
    protected <E> Decodeable<BsonReader, E> createStreamDecoder(Type type) {
        return new BsonStreamDecoder(this, type);
    }

    @Override
    protected <E> Decodeable<BsonReader, E> createMapDecoder(Type type) {
        return new BsonMapDecoder(this, type);
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

    protected static byte typeEnum(final Type type) {
        Objects.requireNonNull(type);
        Class clazz = TypeToken.typeToClass(type);
        byte typeval = 127; // 字段的类型值
        if (clazz == boolean.class || clazz == Boolean.class) {
            typeval = 11;
        } else if (clazz == byte.class || clazz == Byte.class) {
            typeval = 12;
        } else if (clazz == short.class || clazz == Short.class) {
            typeval = 13;
        } else if (clazz == char.class || clazz == Character.class) {
            typeval = 14;
        } else if (clazz == int.class || clazz == Integer.class) {
            typeval = 15;
        } else if (clazz == long.class || clazz == Long.class) {
            typeval = 16;
        } else if (clazz == float.class || clazz == Float.class) {
            typeval = 17;
        } else if (clazz == double.class || clazz == Double.class) {
            typeval = 18;
        } else if (clazz == String.class) {
            typeval = 19;
        } else if (clazz == boolean[].class || clazz == Boolean[].class) {
            typeval = 21;
        } else if (clazz == byte[].class || clazz == Byte[].class) {
            typeval = 22;
        } else if (clazz == short[].class || clazz == Short[].class) {
            typeval = 23;
        } else if (clazz == char[].class || clazz == Character[].class) {
            typeval = 24;
        } else if (clazz == int[].class || clazz == Integer[].class) {
            typeval = 25;
        } else if (clazz == long[].class || clazz == Long[].class) {
            typeval = 26;
        } else if (clazz == float[].class || clazz == Float[].class) {
            typeval = 27;
        } else if (clazz == double[].class || clazz == Double[].class) {
            typeval = 28;
        } else if (clazz == String[].class) {
            typeval = 29;
        } else if (clazz.isArray()) {
            typeval = 81;
        } else if (Collection.class.isAssignableFrom(clazz)) {
            typeval = 82;
        } else if (Stream.class.isAssignableFrom(clazz)) {
            typeval = 83;
        } else if (Map.class.isAssignableFrom(clazz)) {
            typeval = 84;
        }
        return typeval;
    }

    protected static Decodeable typeEnum(final byte typeval) {
        switch (typeval) {
            case 11:
                return BoolSimpledCoder.instance;
            case 12:
                return ByteSimpledCoder.instance;
            case 13:
                return ShortSimpledCoder.instance;
            case 14:
                return CharSimpledCoder.instance;
            case 15:
                return IntSimpledCoder.instance;
            case 16:
                return LongSimpledCoder.instance;
            case 17:
                return FloatSimpledCoder.instance;
            case 18:
                return DoubleSimpledCoder.instance;
            case 19:
                return StringSimpledCoder.instance;
            case 21:
                return BoolArraySimpledCoder.instance;
            case 22:
                return ByteArraySimpledCoder.instance;
            case 23:
                return ShortArraySimpledCoder.instance;
            case 24:
                return CharArraySimpledCoder.instance;
            case 25:
                return IntArraySimpledCoder.instance;
            case 26:
                return LongArraySimpledCoder.instance;
            case 27:
                return FloatArraySimpledCoder.instance;
            case 28:
                return DoubleArraySimpledCoder.instance;
            case 29:
                return StringArraySimpledCoder.instance;
            case 81:
                return skipArrayDecoder;
            case 82:
                return skipCollectionDecoder;
            case 83:
                return skipStreamDecoder;
            case 84:
                return skipMapDecoder;
            case 127:
                return objectDecoder;
            default:
                return null;
        }
    }
}
