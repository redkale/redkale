/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.pb;

import java.io.File;
import java.io.Serializable;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Stream;
import org.redkale.convert.*;
import org.redkale.util.*;

/** @author zhangjx */
public class ProtobufFactory extends ConvertFactory<ProtobufReader, ProtobufWriter> {

    static final boolean INDEX_CHECK =
            "true".equalsIgnoreCase(System.getProperty("redkale.convert.protobuf.indexcheck", "false"));

    private static final ProtobufFactory instance = new ProtobufFactory(
            null,
            getSystemPropertyInt("redkale.convert.protobuf.tiny", "redkale.convert.tiny", true, Convert.FEATURE_TINY)
                    | getSystemPropertyInt(
                            "redkale.convert.protobuf.nullable",
                            "redkale.convert.nullable",
                            false,
                            Convert.FEATURE_NULLABLE),
            Boolean.parseBoolean(System.getProperty("redkale.convert.protobuf.enumtostring", "false")));

    protected final boolean enumtostring;

    protected boolean reversible = false;

    @SuppressWarnings("OverridableMethodCallInConstructor")
    private ProtobufFactory(ProtobufFactory parent, int features, boolean enumtostring) {
        super(parent, features);
        this.enumtostring = enumtostring;
    }

    @Override
    protected void initPrimitiveCoderInRoot() {
        this.register(boolean.class, ProtobufCoders.ProtobufBoolSimpledCoder.instance);
        this.register(Boolean.class, ProtobufCoders.ProtobufBoolSimpledCoder.instance);

        this.register(byte.class, ProtobufCoders.ProtobufByteSimpledCoder.instance);
        this.register(Byte.class, ProtobufCoders.ProtobufByteSimpledCoder.instance);

        this.register(char.class, ProtobufCoders.ProtobufCharSimpledCoder.instance);
        this.register(Character.class, ProtobufCoders.ProtobufCharSimpledCoder.instance);

        this.register(short.class, ProtobufCoders.ProtobufShortSimpledCoder.instance);
        this.register(Short.class, ProtobufCoders.ProtobufShortSimpledCoder.instance);

        this.register(int.class, ProtobufCoders.ProtobufIntSimpledCoder.instance);
        this.register(Integer.class, ProtobufCoders.ProtobufIntSimpledCoder.instance);

        this.register(float.class, ProtobufCoders.ProtobufFloatSimpledCoder.instance);
        this.register(Float.class, ProtobufCoders.ProtobufFloatSimpledCoder.instance);

        this.register(long.class, ProtobufCoders.ProtobufLongSimpledCoder.instance);
        this.register(Long.class, ProtobufCoders.ProtobufLongSimpledCoder.instance);

        this.register(double.class, ProtobufCoders.ProtobufDoubleSimpledCoder.instance);
        this.register(Double.class, ProtobufCoders.ProtobufDoubleSimpledCoder.instance);

        this.register(Number.class, ProtobufCoders.ProtobufNumberSimpledCoder.instance);
        this.register(String.class, ProtobufCoders.ProtobufStringSimpledCoder.instance);
    }

    @Override
    protected void initSimpleCoderInRoot() {
        super.initSimpleCoderInRoot();
        this.register(Object.class, ProtobufCoders.ProtobufAnyDecoder.instance);
        this.register(Object.class, ProtobufCoders.ProtobufAnyEncoder.instance);
        this.register(StringWrapper.class, ProtobufCoders.ProtobufStringWrapperSimpledCoder.instance);
        this.register(CharSequence.class, ProtobufCoders.ProtobufCharSequenceSimpledCoder.instance);
        this.register(StringBuilder.class, ProtobufCoders.ProtobufStringBuilderSimpledCoder.instance);
        this.register(java.util.Date.class, ProtobufCoders.ProtobufDateSimpledCoder.instance);
        this.register(java.time.Instant.class, ProtobufCoders.ProtobufInstantSimpledCoder.instance);
        this.register(java.time.LocalDate.class, ProtobufCoders.ProtobufLocalDateSimpledCoder.instance);
        this.register(java.time.LocalTime.class, ProtobufCoders.ProtobufLocalTimeSimpledCoder.instance);
        this.register(java.time.LocalDateTime.class, ProtobufCoders.ProtobufLocalDateTimeSimpledCoder.instance);
        this.register(java.time.Duration.class, ProtobufCoders.ProtobufDurationSimpledCoder.instance);
        this.register(AtomicBoolean.class, ProtobufCoders.ProtobufAtomicBooleanSimpledCoder.instance);
        this.register(AtomicInteger.class, ProtobufCoders.ProtobufAtomicIntegerSimpledCoder.instance);
        this.register(AtomicLong.class, ProtobufCoders.ProtobufAtomicLongSimpledCoder.instance);
        this.register(BigInteger.class, ProtobufCoders.ProtobufBigIntegerSimpledCoder.instance);
        this.register(BigDecimal.class, ProtobufCoders.ProtobufBigDecimalSimpledCoder.instance);
        this.register(InetAddress.class, ProtobufCoders.ProtobufInetAddressSimpledCoder.instance);
        this.register(InetSocketAddress.class, ProtobufCoders.ProtobufInetSocketAddressSimpledCoder.instance);
        this.register(LongAdder.class, ProtobufCoders.ProtobufLongAdderSimpledCoder.instance);
        this.register(Uint128.class, ProtobufCoders.ProtobufUint128SimpledCoder.instance);
        this.register(File.class, ProtobufCoders.ProtobufFileSimpledCoder.instance);
        this.register(Serializable.class, ProtobufCoders.ProtobufSerializableSimpledCoder.instance);

        this.register(boolean[].class, ProtobufCoders.ProtobufBoolArraySimpledCoder.instance);
        this.register(byte[].class, ProtobufCoders.ProtobufByteArraySimpledCoder.instance);
        this.register(char[].class, ProtobufCoders.ProtobufCharArraySimpledCoder.instance);
        this.register(short[].class, ProtobufCoders.ProtobufShortArraySimpledCoder.instance);
        this.register(int[].class, ProtobufCoders.ProtobufIntArraySimpledCoder.instance);
        this.register(float[].class, ProtobufCoders.ProtobufFloatArraySimpledCoder.instance);
        this.register(long[].class, ProtobufCoders.ProtobufLongArraySimpledCoder.instance);
        this.register(double[].class, ProtobufCoders.ProtobufDoubleArraySimpledCoder.instance);

        this.register(Boolean[].class, ProtobufCoders.ProtobufBoolArraySimpledCoder2.instance);
        this.register(Byte[].class, ProtobufCoders.ProtobufByteArraySimpledCoder2.instance);
        this.register(Character[].class, ProtobufCoders.ProtobufCharArraySimpledCoder2.instance);
        this.register(Short[].class, ProtobufCoders.ProtobufShortArraySimpledCoder2.instance);
        this.register(Integer[].class, ProtobufCoders.ProtobufIntArraySimpledCoder2.instance);
        this.register(Float[].class, ProtobufCoders.ProtobufFloatArraySimpledCoder2.instance);
        this.register(Long[].class, ProtobufCoders.ProtobufLongArraySimpledCoder2.instance);
        this.register(Double[].class, ProtobufCoders.ProtobufDoubleArraySimpledCoder2.instance);

        this.register(String[].class, this.createArrayDecoder(String[].class));
        this.register(String[].class, this.createArrayEncoder(String[].class));
    }

    @Override
    protected ConvertFactory rootFactory() {
        return instance;
    }

    public static ProtobufFactory root() {
        return instance;
    }

    @Override
    public ProtobufFactory withFeatures(int features) {
        return super.withFeatures(features);
    }

    @Override
    public ProtobufFactory addFeature(int feature) {
        return super.addFeature(feature);
    }

    @Override
    public ProtobufFactory removeFeature(int feature) {
        return super.removeFeature(feature);
    }

    @Override
    public ProtobufFactory withTinyFeature(boolean tiny) {
        return super.withTinyFeature(tiny);
    }

    @Override
    public ProtobufFactory withNullableFeature(boolean nullable) {
        return super.withNullableFeature(nullable);
    }

    public static ProtobufFactory create() {
        return new ProtobufFactory(null, instance.features, instance.enumtostring);
    }

    @Override
    protected <E> Encodeable<ProtobufWriter, E> createDyncEncoder(Type type) {
        return ProtobufDynEncoder.createDyncEncoder(this, type);
    }

    @Override
    protected SimpledCoder createEnumSimpledCoder(Class enumClass) {
        return new ProtobufEnumSimpledCoder(enumClass, this.enumtostring);
    }

    @Override
    protected ObjectDecoder createObjectDecoder(Type type) {
        return new ProtobufObjectDecoder(type);
    }

    @Override
    protected ObjectEncoder createObjectEncoder(Type type) {
        return new ProtobufObjectEncoder(type);
    }

    @Override
    protected <E> Decodeable<ProtobufReader, E> createMapDecoder(Type type) {
        return new ProtobufMapDecoder(this, type);
    }

    @Override
    protected <E> Encodeable<ProtobufWriter, E> createMapEncoder(Type type) {
        return new ProtobufMapEncoder(this, type);
    }

    @Override
    protected <E> Decodeable<ProtobufReader, E> createArrayDecoder(Type type) {
        return new ProtobufArrayDecoder(this, type);
    }

    @Override
    protected <E> Encodeable<ProtobufWriter, E> createArrayEncoder(Type type) {
        return new ProtobufArrayEncoder(this, type);
    }

    @Override
    protected <E> Decodeable<ProtobufReader, E> createCollectionDecoder(Type type) {
        Class createClazz = TypeToken.typeToClass(type);
        Type componentType = getCollectionComponentType(type);
        if (componentType == Boolean.class) {
            Creator<? extends Collection> creator = loadCreator(createClazz);
            return (Decodeable) new ProtobufCoders.ProtobufBoolCollectionSimpledCoder(creator);
        } else if (componentType == Byte.class) {
            Creator<? extends Collection> creator = loadCreator(createClazz);
            return (Decodeable) new ProtobufCoders.ProtobufByteCollectionSimpledCoder(creator);
        } else if (componentType == Character.class) {
            Creator<? extends Collection> creator = loadCreator(createClazz);
            return (Decodeable) new ProtobufCoders.ProtobufCharCollectionSimpledCoder(creator);
        } else if (componentType == Short.class) {
            Creator<? extends Collection> creator = loadCreator(createClazz);
            return (Decodeable) new ProtobufCoders.ProtobufShortCollectionSimpledCoder(creator);
        } else if (componentType == Integer.class) {
            Creator<? extends Collection> creator = loadCreator(createClazz);
            return (Decodeable) new ProtobufCoders.ProtobufIntCollectionSimpledCoder(creator);
        } else if (componentType == Float.class) {
            Creator<? extends Collection> creator = loadCreator(createClazz);
            return (Decodeable) new ProtobufCoders.ProtobufFloatCollectionSimpledCoder(creator);
        } else if (componentType == Long.class) {
            Creator<? extends Collection> creator = loadCreator(createClazz);
            return (Decodeable) new ProtobufCoders.ProtobufLongCollectionSimpledCoder(creator);
        } else if (componentType == Double.class) {
            Creator<? extends Collection> creator = loadCreator(createClazz);
            return (Decodeable) new ProtobufCoders.ProtobufDoubleCollectionSimpledCoder(creator);
        }
        return new ProtobufCollectionDecoder(this, type);
    }

    @Override
    protected <E> Encodeable<ProtobufWriter, E> createCollectionEncoder(Type type) {
        Creator<? extends Collection> creator = ProtobufCoders.LIST_CREATOR;
        Type componentType = getCollectionComponentType(type);
        if (componentType == Boolean.class) {
            return (Encodeable) new ProtobufCoders.ProtobufBoolCollectionSimpledCoder(creator);
        } else if (componentType == Byte.class) {
            return (Encodeable) new ProtobufCoders.ProtobufByteCollectionSimpledCoder(creator);
        } else if (componentType == Character.class) {
            return (Encodeable) new ProtobufCoders.ProtobufCharCollectionSimpledCoder(creator);
        } else if (componentType == Short.class) {
            return (Encodeable) new ProtobufCoders.ProtobufShortCollectionSimpledCoder(creator);
        } else if (componentType == Integer.class) {
            return (Encodeable) new ProtobufCoders.ProtobufIntCollectionSimpledCoder(creator);
        } else if (componentType == Float.class) {
            return (Encodeable) new ProtobufCoders.ProtobufFloatCollectionSimpledCoder(creator);
        } else if (componentType == Long.class) {
            return (Encodeable) new ProtobufCoders.ProtobufLongCollectionSimpledCoder(creator);
        } else if (componentType == Double.class) {
            return (Encodeable) new ProtobufCoders.ProtobufDoubleCollectionSimpledCoder(creator);
        }
        return new ProtobufCollectionEncoder(this, type);
    }

    @Override
    protected <E> Decodeable<ProtobufReader, E> createStreamDecoder(Type type) {
        return new ProtobufStreamDecoder(this, type);
    }

    @Override
    protected <E> Encodeable<ProtobufWriter, E> createStreamEncoder(Type type) {
        return new ProtobufStreamEncoder(this, type);
    }

    @Override
    public final ProtobufConvert getConvert() {
        if (convert == null) {
            convert = new ProtobufConvert(this, features);
        }
        return (ProtobufConvert) convert;
    }

    @Override
    public ProtobufFactory createChild() {
        return new ProtobufFactory(this, features, this.enumtostring);
    }

    @Override
    public ProtobufFactory createChild(int features) {
        return new ProtobufFactory(this, features, this.enumtostring);
    }

    @Override
    public ConvertType getConvertType() {
        return ConvertType.PROTOBUF;
    }

    public ProtobufFactory reversible(boolean reversible) {
        this.reversible = reversible;
        return this;
    }

    @Override
    public boolean isReversible() {
        return reversible;
    }

    @Override
    public boolean isFieldSort() {
        return true;
    }

    // 对应ProtobufWriter.writeFieldValue方法
    protected static boolean isSimpleType(Class fieldClass) {
        return fieldClass.isPrimitive()
                || fieldClass == Boolean.class
                || fieldClass == Byte.class
                || fieldClass == Character.class
                || fieldClass == Short.class
                || fieldClass == Integer.class
                || fieldClass == Float.class
                || fieldClass == Long.class
                || fieldClass == Double.class
                || fieldClass == String.class
                || fieldClass == boolean[].class
                || fieldClass == byte[].class
                || fieldClass == char[].class
                || fieldClass == short[].class
                || fieldClass == int[].class
                || fieldClass == float[].class
                || fieldClass == long[].class
                || fieldClass == double[].class
                || fieldClass == Boolean[].class
                || fieldClass == Byte[].class
                || fieldClass == Character[].class
                || fieldClass == Short[].class
                || fieldClass == Integer[].class
                || fieldClass == Float[].class
                || fieldClass == Long[].class
                || fieldClass == Double[].class
                || fieldClass == String[].class;
    }

    protected boolean supportSimpleCollectionType(Type type) {
        Type componentType = getCollectionComponentType(type);
        return componentType == Boolean.class
                || componentType == Byte.class
                || componentType == Character.class
                || componentType == Short.class
                || componentType == Integer.class
                || componentType == Float.class
                || componentType == Long.class
                || componentType == Double.class
                || componentType == String.class;
    }

    // see io.protostuff.ProtobufOutput
    protected static int computeRawVarint32Size(final int value) {
        if (value == 0) return 1;
        if ((value & (0xffffffff << 7)) == 0) return 1;
        if ((value & (0xffffffff << 14)) == 0) return 2;
        if ((value & (0xffffffff << 21)) == 0) return 3;
        if ((value & (0xffffffff << 28)) == 0) return 4;
        return 5;
    }

    protected static int computeRawVarint64Size(final long value) {
        if (value == 0) return 1;
        if ((value & (0xffffffffffffffffL << 7)) == 0) return 1;
        if ((value & (0xffffffffffffffffL << 14)) == 0) return 2;
        if ((value & (0xffffffffffffffffL << 21)) == 0) return 3;
        if ((value & (0xffffffffffffffffL << 28)) == 0) return 4;
        if ((value & (0xffffffffffffffffL << 35)) == 0) return 5;
        if ((value & (0xffffffffffffffffL << 42)) == 0) return 6;
        if ((value & (0xffffffffffffffffL << 49)) == 0) return 7;
        if ((value & (0xffffffffffffffffL << 56)) == 0) return 8;
        if ((value & (0xffffffffffffffffL << 63)) == 0) return 9;
        return 10;
    }

    // see com.google.protobuf.CodedOutputStream
    protected static int computeInt32SizeNoTag(final int value) {
        if (value == 0) return 1;
        return computeUInt64SizeNoTag(value);
    }

    protected static int computeUInt64SizeNoTag(final long value) {
        if (value == 0) return 1;
        int clz = Long.numberOfLeadingZeros(value);
        return ((Long.SIZE * 9 + (1 << 6)) - (clz * 9)) >>> 6;
    }

    protected static int computeSInt32SizeNoTag(final int value) {
        if (value == 0) return 1;
        return computeUInt32SizeNoTag(encodeZigZag32(value));
    }

    protected static int computeSInt64SizeNoTag(final long value) {
        if (value == 0) return 1;
        return computeUInt64SizeNoTag(encodeZigZag64(value));
    }

    protected static int computeUInt32SizeNoTag(final int value) {
        if (value == 0) return 1;
        int clz = Integer.numberOfLeadingZeros(value);
        return ((Integer.SIZE * 9 + (1 << 6)) - (clz * 9)) >>> 6;
    }

    protected static int encodeZigZag32(final int n) {
        if (n == 0) return 0;
        return (n << 1) ^ (n >> 31);
    }

    protected static long encodeZigZag64(final long n) {
        if (n == 0) return 0L;
        return (n << 1) ^ (n >> 63);
    }

    protected Class getSimpleCollectionComponentType(Type type) {
        return supportSimpleCollectionType(type) ? TypeToken.typeToClass(getCollectionComponentType(type)) : null;
    }

    public static int getTag(String fieldName, Type fieldType, int fieldPos, boolean enumtostring) {
        ProtobufTypeEnum typeEnum = ProtobufTypeEnum.valueOf(fieldType, enumtostring);
        return (fieldPos << 3 | typeEnum.getValue());
    }

    public static int getTag(int index, ProtobufTypeEnum typeEnum) {
        return index << 3 | typeEnum.getValue();
    }

    public static int getTag(DeMember member, boolean enumtostring) {
        ProtobufTypeEnum typeEnum =
                ProtobufTypeEnum.valueOf(member.getAttribute().type(), enumtostring);
        return (member.getPosition() << 3 | typeEnum.getValue());
    }

    public static String wireTypeString(Type javaType, boolean enumtostring) {
        if (javaType == double.class || javaType == Double.class) {
            return "double";
        }
        if (javaType == long.class || javaType == Long.class) {
            return "sint64";
        }
        if (javaType == float.class || javaType == Float.class) {
            return "float";
        }
        if (javaType == int.class || javaType == Integer.class) {
            return "sint32";
        }
        if (javaType == short.class || javaType == Short.class) {
            return "sint32";
        }
        if (javaType == char.class || javaType == Character.class) {
            return "sint32";
        }
        if (javaType == byte.class || javaType == Byte.class) {
            return "sint32";
        }
        if (javaType == boolean.class || javaType == Boolean.class) {
            return "bool";
        }
        if (javaType == AtomicLong.class) {
            return "sint64";
        }
        if (javaType == AtomicInteger.class) {
            return "sint32";
        }
        if (javaType == AtomicBoolean.class) {
            return "bool";
        }

        if (javaType == double[].class || javaType == Double[].class) {
            return "repeated double";
        }
        if (javaType == long[].class || javaType == Long[].class) {
            return "repeated sint64";
        }
        if (javaType == float[].class || javaType == Float[].class) {
            return "repeated float";
        }
        if (javaType == int[].class || javaType == Integer[].class) {
            return "repeated sint32";
        }
        if (javaType == short[].class || javaType == Short[].class) {
            return "repeated sint32";
        }
        if (javaType == char[].class || javaType == Character[].class) {
            return "repeated sint32";
        }
        if (javaType == byte[].class || javaType == Byte[].class) {
            return "bytes";
        }
        if (javaType == boolean[].class || javaType == Boolean[].class) {
            return "repeated bool";
        }
        if (javaType == AtomicLong[].class) {
            return "repeated sint64";
        }
        if (javaType == AtomicInteger[].class) {
            return "repeated sint32";
        }
        if (javaType == AtomicBoolean[].class) {
            return "repeated bool";
        }

        if (javaType == java.util.Properties.class) {
            return "map<string,string>";
        }
        if (javaType instanceof Class) {
            Class javaClazz = (Class) javaType;
            if (javaClazz.isArray()) {
                return "repeated " + wireTypeString(javaClazz.getComponentType(), enumtostring);
            }
            if (javaClazz.isEnum()) {
                return enumtostring ? "string" : javaClazz.getSimpleName();
            }
            if (CharSequence.class.isAssignableFrom(javaClazz)) {
                return "string";
            }
            return javaClazz == Object.class ? "Any" : javaClazz.getSimpleName();
        } else if (javaType instanceof ParameterizedType) { // Collection、Stream、Map 必须是泛型
            final ParameterizedType pt = (ParameterizedType) javaType;
            final Class rawType = (Class) pt.getRawType();
            if (Map.class.isAssignableFrom(rawType)) {
                Type keyType = pt.getActualTypeArguments()[0];
                Type valueType = pt.getActualTypeArguments()[1];
                return "map<" + wireTypeString(keyType, enumtostring) + "," + wireTypeString(valueType, enumtostring)
                        + ">";
            } else if (Collection.class.isAssignableFrom(rawType)
                    || Stream.class.isAssignableFrom(rawType)
                    || rawType.isArray()) {
                return "repeated " + wireTypeString(pt.getActualTypeArguments()[0], enumtostring);
            } else if (pt.getActualTypeArguments().length == 1 && (pt.getActualTypeArguments()[0] instanceof Class)) {
                return rawType.getSimpleName() + "_" + ((Class) pt.getActualTypeArguments()[0]).getSimpleName();
            }
        } else if (javaType instanceof GenericArrayType) {
            return "repeated " + wireTypeString(((GenericArrayType) javaType).getGenericComponentType(), enumtostring);
        }
        throw new UnsupportedOperationException("ProtobufConvert not supported type(" + javaType + ")");
    }
}
