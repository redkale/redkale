/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.pb;

import java.io.Serializable;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Stream;
import org.redkale.convert.*;
import org.redkale.convert.pb.ProtobufCoders.ProtobufAtomicIntegerArraySimpledCoder;
import org.redkale.convert.pb.ProtobufCoders.ProtobufAtomicIntegerCollectionSimpledCoder;
import org.redkale.convert.pb.ProtobufCoders.ProtobufAtomicIntegerStreamSimpledCoder;
import org.redkale.convert.pb.ProtobufCoders.ProtobufAtomicLongArraySimpledCoder;
import org.redkale.convert.pb.ProtobufCoders.ProtobufAtomicLongCollectionSimpledCoder;
import org.redkale.convert.pb.ProtobufCoders.ProtobufAtomicLongStreamSimpledCoder;
import org.redkale.convert.pb.ProtobufCoders.ProtobufBoolArraySimpledCoder2;
import org.redkale.convert.pb.ProtobufCoders.ProtobufBoolCollectionSimpledCoder;
import org.redkale.convert.pb.ProtobufCoders.ProtobufBoolStreamSimpledCoder;
import org.redkale.convert.pb.ProtobufCoders.ProtobufByteArraySimpledCoder;
import org.redkale.convert.pb.ProtobufCoders.ProtobufByteArraySimpledCoder2;
import org.redkale.convert.pb.ProtobufCoders.ProtobufByteCollectionSimpledCoder;
import org.redkale.convert.pb.ProtobufCoders.ProtobufByteStreamSimpledCoder;
import org.redkale.convert.pb.ProtobufCoders.ProtobufCharArraySimpledCoder;
import org.redkale.convert.pb.ProtobufCoders.ProtobufCharArraySimpledCoder2;
import org.redkale.convert.pb.ProtobufCoders.ProtobufCharCollectionSimpledCoder;
import org.redkale.convert.pb.ProtobufCoders.ProtobufCharStreamSimpledCoder;
import org.redkale.convert.pb.ProtobufCoders.ProtobufDoubleArraySimpledCoder;
import org.redkale.convert.pb.ProtobufCoders.ProtobufDoubleArraySimpledCoder2;
import org.redkale.convert.pb.ProtobufCoders.ProtobufDoubleCollectionSimpledCoder;
import org.redkale.convert.pb.ProtobufCoders.ProtobufDoubleStreamSimpledCoder;
import org.redkale.convert.pb.ProtobufCoders.ProtobufFloatArraySimpledCoder;
import org.redkale.convert.pb.ProtobufCoders.ProtobufFloatArraySimpledCoder2;
import org.redkale.convert.pb.ProtobufCoders.ProtobufFloatCollectionSimpledCoder;
import org.redkale.convert.pb.ProtobufCoders.ProtobufFloatStreamSimpledCoder;
import org.redkale.convert.pb.ProtobufCoders.ProtobufIntArraySimpledCoder;
import org.redkale.convert.pb.ProtobufCoders.ProtobufIntArraySimpledCoder2;
import org.redkale.convert.pb.ProtobufCoders.ProtobufIntCollectionSimpledCoder;
import org.redkale.convert.pb.ProtobufCoders.ProtobufIntStreamSimpledCoder;
import org.redkale.convert.pb.ProtobufCoders.ProtobufLongArraySimpledCoder;
import org.redkale.convert.pb.ProtobufCoders.ProtobufLongArraySimpledCoder2;
import org.redkale.convert.pb.ProtobufCoders.ProtobufLongCollectionSimpledCoder;
import org.redkale.convert.pb.ProtobufCoders.ProtobufLongStreamSimpledCoder;
import org.redkale.convert.pb.ProtobufCoders.ProtobufShortArraySimpledCoder;
import org.redkale.convert.pb.ProtobufCoders.ProtobufShortArraySimpledCoder2;
import org.redkale.convert.pb.ProtobufCoders.ProtobufShortCollectionSimpledCoder;
import org.redkale.convert.pb.ProtobufCoders.ProtobufShortStreamSimpledCoder;
import org.redkale.util.*;

/** @author zhangjx */
public class ProtobufFactory extends ConvertFactory<ProtobufReader, ProtobufWriter> {

    private static final ProtobufFactory instance = new ProtobufFactory(
            null,
            getSystemPropertyInt("redkale.convert.protobuf.tiny", "redkale.convert.tiny", true, Convert.FEATURE_TINY)
                    | getSystemPropertyInt(
                            "redkale.convert.protobuf.nullable",
                            "redkale.convert.nullable",
                            false,
                            Convert.FEATURE_NULLABLE),
            Boolean.parseBoolean(System.getProperty("redkale.convert.protobuf.enumtostring", "false")));

    static final Decodeable objectDecoder = instance.loadDecoder(Object.class);

    static final Encodeable objectEncoder = instance.loadEncoder(Object.class);

    protected final boolean enumtostring;

    protected boolean reversible = false;

    static {
        instance.register(Serializable.class, objectDecoder);
        instance.register(Serializable.class, objectEncoder);

        instance.register(AnyValue.class, instance.loadDecoder(AnyValueWriter.class));
        instance.register(AnyValue.class, instance.loadEncoder(AnyValueWriter.class));
        instance.register(boolean[].class, ProtobufCoders.ProtobufBoolArraySimpledCoder.instance);
        instance.register(byte[].class, ProtobufByteArraySimpledCoder.instance);
        instance.register(char[].class, ProtobufCharArraySimpledCoder.instance);
        instance.register(short[].class, ProtobufShortArraySimpledCoder.instance);
        instance.register(int[].class, ProtobufIntArraySimpledCoder.instance);
        instance.register(float[].class, ProtobufFloatArraySimpledCoder.instance);
        instance.register(long[].class, ProtobufLongArraySimpledCoder.instance);
        instance.register(double[].class, ProtobufDoubleArraySimpledCoder.instance);
        instance.register(Boolean[].class, ProtobufBoolArraySimpledCoder2.instance);
        instance.register(Byte[].class, ProtobufByteArraySimpledCoder2.instance);
        instance.register(Character[].class, ProtobufCharArraySimpledCoder2.instance);
        instance.register(Short[].class, ProtobufShortArraySimpledCoder2.instance);
        instance.register(Integer[].class, ProtobufIntArraySimpledCoder2.instance);
        instance.register(Float[].class, ProtobufFloatArraySimpledCoder2.instance);
        instance.register(Long[].class, ProtobufLongArraySimpledCoder2.instance);
        instance.register(Double[].class, ProtobufDoubleArraySimpledCoder2.instance);
        instance.register(AtomicInteger[].class, ProtobufAtomicIntegerArraySimpledCoder.instance);
        instance.register(AtomicLong[].class, ProtobufAtomicLongArraySimpledCoder.instance);
    }

    @SuppressWarnings("OverridableMethodCallInConstructor")
    private ProtobufFactory(ProtobufFactory parent, int features, boolean enumtostring) {
        super(parent, features);
        this.enumtostring = enumtostring;
        if (parent == null) { // root
            this.register(String[].class, this.createArrayDecoder(String[].class));
            this.register(String[].class, this.createArrayEncoder(String[].class));
        }
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
        if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            Type componentType = pt.getActualTypeArguments()[0];
            if (componentType == Boolean.class) {
                Creator<? extends Collection> creator = loadCreator((Class) pt.getRawType());
                return (Decodeable) new ProtobufBoolCollectionSimpledCoder(creator);
            } else if (componentType == Byte.class) {
                Creator<? extends Collection> creator = loadCreator((Class) pt.getRawType());
                return (Decodeable) new ProtobufByteCollectionSimpledCoder(creator);
            } else if (componentType == Character.class) {
                Creator<? extends Collection> creator = loadCreator((Class) pt.getRawType());
                return (Decodeable) new ProtobufCharCollectionSimpledCoder(creator);
            } else if (componentType == Short.class) {
                Creator<? extends Collection> creator = loadCreator((Class) pt.getRawType());
                return (Decodeable) new ProtobufShortCollectionSimpledCoder(creator);
            } else if (componentType == Integer.class) {
                Creator<? extends Collection> creator = loadCreator((Class) pt.getRawType());
                return (Decodeable) new ProtobufIntCollectionSimpledCoder(creator);
            } else if (componentType == Float.class) {
                Creator<? extends Collection> creator = loadCreator((Class) pt.getRawType());
                return (Decodeable) new ProtobufFloatCollectionSimpledCoder(creator);
            } else if (componentType == Long.class) {
                Creator<? extends Collection> creator = loadCreator((Class) pt.getRawType());
                return (Decodeable) new ProtobufLongCollectionSimpledCoder(creator);
            } else if (componentType == Double.class) {
                Creator<? extends Collection> creator = loadCreator((Class) pt.getRawType());
                return (Decodeable) new ProtobufDoubleCollectionSimpledCoder(creator);
            } else if (componentType == AtomicInteger.class) {
                Creator<? extends Collection> creator = loadCreator((Class) pt.getRawType());
                return (Decodeable) new ProtobufAtomicIntegerCollectionSimpledCoder(creator);
            } else if (componentType == AtomicLong.class) {
                Creator<? extends Collection> creator = loadCreator((Class) pt.getRawType());
                return (Decodeable) new ProtobufAtomicLongCollectionSimpledCoder(creator);
            }
        }
        return new ProtobufCollectionDecoder(this, type);
    }

    @Override
    protected <E> Encodeable<ProtobufWriter, E> createCollectionEncoder(Type type) {
        return new ProtobufCollectionEncoder(this, type);
    }

    @Override
    protected <E> Decodeable<ProtobufReader, E> createStreamDecoder(Type type) {
        if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            Type componentType = pt.getActualTypeArguments()[0];
            if (componentType == Boolean.class) {
                return (Decodeable) ProtobufBoolStreamSimpledCoder.instance;
            } else if (componentType == Byte.class) {
                return (Decodeable) ProtobufByteStreamSimpledCoder.instance;
            } else if (componentType == Character.class) {
                return (Decodeable) ProtobufCharStreamSimpledCoder.instance;
            } else if (componentType == Short.class) {
                return (Decodeable) ProtobufShortStreamSimpledCoder.instance;
            } else if (componentType == Integer.class) {
                return (Decodeable) ProtobufIntStreamSimpledCoder.instance;
            } else if (componentType == Float.class) {
                return (Decodeable) ProtobufFloatStreamSimpledCoder.instance;
            } else if (componentType == Long.class) {
                return (Decodeable) ProtobufLongStreamSimpledCoder.instance;
            } else if (componentType == Double.class) {
                return (Decodeable) ProtobufDoubleStreamSimpledCoder.instance;
            } else if (componentType == AtomicInteger.class) {
                return (Decodeable) ProtobufAtomicIntegerStreamSimpledCoder.instance;
            } else if (componentType == AtomicLong.class) {
                return (Decodeable) ProtobufAtomicLongStreamSimpledCoder.instance;
            }
        }
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
                || fieldClass == AtomicInteger.class
                || fieldClass == AtomicLong.class
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
                || fieldClass == AtomicInteger[].class
                || fieldClass == AtomicLong[].class
                || fieldClass == String[].class;
    }

    protected static boolean supportSimpleCollectionType(Type type) {
        if (!(type instanceof ParameterizedType)) {
            return false;
        }
        ParameterizedType ptype = (ParameterizedType) type;
        if (!(ptype.getRawType() instanceof Class)) {
            return false;
        }
        Type[] ptargs = ptype.getActualTypeArguments();
        if (ptargs == null || ptargs.length != 1) {
            return false;
        }
        Class ownerType = (Class) ptype.getRawType();
        if (!Collection.class.isAssignableFrom(ownerType)) {
            return false;
        }
        Type componentType = ptargs[0];
        return componentType == Boolean.class
                || componentType == Byte.class
                || componentType == Character.class
                || componentType == Short.class
                || componentType == Integer.class
                || componentType == Float.class
                || componentType == Long.class
                || componentType == Double.class
                || componentType == AtomicInteger.class
                || componentType == AtomicLong.class
                || componentType == String.class;
    }

    // see com.google.protobuf.CodedOutputStream
    protected static int computeInt32SizeNoTag(final int value) {
        if (value == 0) {
            return 1;
        }
        return computeUInt64SizeNoTag(value);
    }

    protected static int computeUInt64SizeNoTag(final long value) {
        if (value == 0) {
            return 1;
        }
        int clz = Long.numberOfLeadingZeros(value);
        return ((Long.SIZE * 9 + (1 << 6)) - (clz * 9)) >>> 6;
    }

    protected static int computeSInt32SizeNoTag(final int value) {
        if (value == 0) {
            return 1;
        }
        return computeUInt32SizeNoTag(encodeZigZag32(value));
    }

    protected static int computeSInt64SizeNoTag(final long value) {
        if (value == 0) {
            return 1;
        }
        return computeUInt64SizeNoTag(encodeZigZag64(value));
    }

    protected static int computeUInt32SizeNoTag(final int value) {
        if (value == 0) {
            return 1;
        }
        int clz = Integer.numberOfLeadingZeros(value);
        return ((Integer.SIZE * 9 + (1 << 6)) - (clz * 9)) >>> 6;
    }

    protected static int encodeZigZag32(final int n) {
        if (n == 0) {
            return 0;
        }
        return (n << 1) ^ (n >> 31);
    }

    protected static long encodeZigZag64(final long n) {
        if (n == 0) {
            return 0L;
        }
        return (n << 1) ^ (n >> 63);
    }

    public static Class getSimpleCollectionComponentType(Type type) {
        return supportSimpleCollectionType(type)
                ? (Class) ((ParameterizedType) type).getActualTypeArguments()[0]
                : null;
    }

    public static int getTag(String fieldName, Type fieldType, int fieldPos, boolean enumtostring) {
        int wiretype = ProtobufFactory.wireType(fieldType, enumtostring);
        return (fieldPos << 3 | wiretype);
    }

    public static int getTag(DeMember member, boolean enumtostring) {
        int wiretype = ProtobufFactory.wireType(member.getAttribute().type(), enumtostring);
        return (member.getPosition() << 3 | wiretype);
    }

    public static int wireType(Type javaType, boolean enumtostring) {
        if (javaType == double.class || javaType == Double.class) {
            return 1;
        } else if (javaType == float.class || javaType == Float.class) {
            return 5;
        } else if ((javaType == boolean.class || javaType == Boolean.class)
                || (javaType == byte.class || javaType == Byte.class)
                || (javaType == char.class || javaType == Character.class)
                || (javaType == short.class || javaType == Short.class)
                || (javaType == int.class || javaType == Integer.class)
                || (javaType == long.class || javaType == Long.class)
                || (javaType == AtomicInteger.class || javaType == AtomicLong.class)) {
            return 0;
        } else if (!enumtostring && (javaType instanceof Class) && ((Class) javaType).isEnum()) {
            return 0;
        } else { // byte[]
            return 2;
        }
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
