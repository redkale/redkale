/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.bson;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Stream;
import org.redkale.convert.*;
import org.redkale.convert.ext.*;
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

    static final Decodeable collectionBooleanDecoder = instance.loadDecoder(new TypeToken<Collection<Boolean>>() {
    }.getType());

    static final Decodeable collectionByteDecoder = instance.loadDecoder(new TypeToken<Collection<Byte>>() {
    }.getType());

    static final Decodeable collectionShortDecoder = instance.loadDecoder(new TypeToken<Collection<Short>>() {
    }.getType());

    static final Decodeable collectionCharacterDecoder = instance.loadDecoder(new TypeToken<Collection<Character>>() {
    }.getType());

    static final Decodeable collectionIntegerDecoder = instance.loadDecoder(new TypeToken<Collection<Integer>>() {
    }.getType());

    static final Decodeable collectionLongDecoder = instance.loadDecoder(new TypeToken<Collection<Long>>() {
    }.getType());

    static final Decodeable collectionFloatDecoder = instance.loadDecoder(new TypeToken<Collection<Float>>() {
    }.getType());

    static final Decodeable collectionDoubleDecoder = instance.loadDecoder(new TypeToken<Collection<Double>>() {
    }.getType());

    static final Decodeable collectionStringDecoder = instance.loadDecoder(new TypeToken<Collection<String>>() {
    }.getType());

    static final Decodeable collectionObjectDecoder = instance.loadDecoder(new TypeToken<Collection<Object>>() {
    }.getType());

    static final Decodeable mapStringBooleanDecoder = instance.loadDecoder(new TypeToken<Map<String, Boolean>>() {
    }.getType());

    static final Decodeable mapStringByteDecoder = instance.loadDecoder(new TypeToken<Map<String, Byte>>() {
    }.getType());

    static final Decodeable mapStringShortDecoder = instance.loadDecoder(new TypeToken<Map<String, Short>>() {
    }.getType());

    static final Decodeable mapStringCharacterDecoder = instance.loadDecoder(new TypeToken<Map<String, Character>>() {
    }.getType());

    static final Decodeable mapStringIntegerDecoder = instance.loadDecoder(new TypeToken<Map<String, Integer>>() {
    }.getType());

    static final Decodeable mapStringLongDecoder = instance.loadDecoder(new TypeToken<Map<String, Long>>() {
    }.getType());

    static final Decodeable mapStringFloatDecoder = instance.loadDecoder(new TypeToken<Map<String, Float>>() {
    }.getType());

    static final Decodeable mapStringDoubleDecoder = instance.loadDecoder(new TypeToken<Map<String, Double>>() {
    }.getType());

    static final Decodeable mapStringStringDecoder = instance.loadDecoder(new TypeToken<Map<String, String>>() {
    }.getType());

    static final Decodeable mapStringObjectDecoder = instance.loadDecoder(new TypeToken<Map<String, Object>>() {
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

    protected static byte typeEnum(final Class type) {
        Objects.requireNonNull(type);
        byte typeval = 127;  //字段的类型值
        if (type == boolean.class || type == Boolean.class) {
            typeval = 11;
        } else if (type == byte.class || type == Byte.class) {
            typeval = 12;
        } else if (type == short.class || type == Short.class) {
            typeval = 13;
        } else if (type == char.class || type == Character.class) {
            typeval = 14;
        } else if (type == int.class || type == Integer.class) {
            typeval = 15;
        } else if (type == long.class || type == Long.class) {
            typeval = 16;
        } else if (type == float.class || type == Float.class) {
            typeval = 17;
        } else if (type == double.class || type == Double.class) {
            typeval = 18;
        } else if (type == String.class) {
            typeval = 19;
        } else if (type == boolean[].class || type == Boolean[].class) {
            typeval = 21;
        } else if (type == byte[].class || type == Byte[].class) {
            typeval = 22;
        } else if (type == short[].class || type == Short[].class) {
            typeval = 23;
        } else if (type == char[].class || type == Character[].class) {
            typeval = 24;
        } else if (type == int[].class || type == Integer[].class) {
            typeval = 25;
        } else if (type == long[].class || type == Long[].class) {
            typeval = 26;
        } else if (type == float[].class || type == Float[].class) {
            typeval = 27;
        } else if (type == double[].class || type == Double[].class) {
            typeval = 28;
        } else if (type == String[].class) {
            typeval = 29;
        } else if (type.isArray()) {
            typeval = 81;
        } else if (Collection.class.isAssignableFrom(type)) {
            typeval = 82;
        } else if (Stream.class.isAssignableFrom(type)) {
            typeval = 83;
        } else if (Map.class.isAssignableFrom(type)) {
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
            default:
                return BsonFactory.objectDecoder;
        }
    }
}
