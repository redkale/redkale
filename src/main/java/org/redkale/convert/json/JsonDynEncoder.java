/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.json;

import static java.lang.classfile.ClassFile.*;
import static java.lang.constant.ConstantDescs.*;

import java.lang.classfile.*;
import java.lang.classfile.attribute.SignatureAttribute;
import java.lang.constant.*;
import java.lang.reflect.*;
import java.util.*;
import org.redkale.convert.*;
import org.redkale.convert.ext.*;
import org.redkale.util.*;

/**
 * 简单对象的JSON序列化操作类
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.3.0
 * @param <T> 序列化的数据类型
 */
@SuppressWarnings("unchecked")
public abstract class JsonDynEncoder<T> extends ObjectEncoder<JsonWriter, T> {

    protected final ObjectEncoder<JsonWriter, T> objectEncoderSelf;

    protected JsonDynEncoder(JsonFactory factory, Type type, ObjectEncoder objectEncoderSelf) {
        super(type);
        this.factory = factory;
        this.objectEncoderSelf = objectEncoderSelf;
        this.members = objectEncoderSelf.getMembers();
        this.inited = true;
        factory.register(type, this);
    }

    protected static JsonDynEncoder generateDyncEncoder(
            final JsonFactory factory, final Class clazz, final List<AccessibleObject> elements) {
        final ObjectEncoder selfObjEncoder = factory.createObjectEncoder(clazz);
        selfObjEncoder.init(factory); // 必须执行，初始化EnMember内部信息
        if (selfObjEncoder.getMembers().length != elements.size()) {
            return null; // 存在ignore等定制配置
        }
        final int features = factory.getFeatures();
        final Map<String, AccessibleObject> mixedNames = new HashMap<>();
        StringBuilder elementb = new StringBuilder();
        for (AccessibleObject element : elements) {
            final String fieldName = factory.readConvertFieldName(clazz, element);
            elementb.append(fieldName).append(',');
            final Class fieldType = readGetSetFieldType(element);
            if (fieldType != boolean.class
                    && fieldType != byte.class
                    && fieldType != short.class
                    && fieldType != char.class
                    && fieldType != int.class
                    && fieldType != float.class
                    && fieldType != long.class
                    && fieldType != double.class
                    && fieldType != Boolean.class
                    && fieldType != Byte.class
                    && fieldType != Short.class
                    && fieldType != Character.class
                    && fieldType != Integer.class
                    && fieldType != Float.class
                    && fieldType != Long.class
                    && fieldType != Double.class
                    && fieldType != String.class) {
                mixedNames.put(fieldName, element);
            }
        }
        RedkaleClassLoader loader = RedkaleClassLoader.currentClassLoader();
        final String newDynName = "org/redkaledyn/convert/json/_Dyn" + JsonDynEncoder.class.getSimpleName() + "__"
                + clazz.getName().replace('.', '_').replace('$', '_') + "_" + features + "_"
                + Utility.md5Hex(elementb.toString()); // tiny必须要加上, 同一个类会有多个字段定制Convert
        try {
            Class newClazz = loader.loadClass(newDynName.replace('/', '.'));
            JsonDynEncoder resultEncoder =
                    (JsonDynEncoder) newClazz.getConstructor(JsonFactory.class, Type.class, ObjectEncoder.class)
                            .newInstance(factory, clazz, selfObjEncoder);
            for (Map.Entry<String, AccessibleObject> en : mixedNames.entrySet()) {
                Field f = newClazz.getDeclaredField(en.getKey() + "Encoder");
                f.setAccessible(true);
                f.set(
                        resultEncoder,
                        factory.loadEncoder(
                                en.getValue() instanceof Field
                                        ? ((Field) en.getValue()).getGenericType()
                                        : ((Method) en.getValue()).getGenericReturnType()));
            }
            return resultEncoder;
        } catch (Throwable ex) {
            // do nothing
        }

        final ClassDesc dynDesc = ClassDesc.ofInternalName(newDynName);
        final ClassDesc superDesc = ClassDesc.ofDescriptor(JsonDynEncoder.class.descriptorString());
        final ClassDesc valueDesc = ClassDesc.ofDescriptor(clazz.descriptorString());
        final ClassDesc writerDesc = ClassDesc.ofDescriptor(JsonWriter.class.descriptorString());
        final ClassDesc objectEncoderDesc = ClassDesc.ofDescriptor(ObjectEncoder.class.descriptorString());
        final ClassDesc typeDesc = ClassDesc.ofDescriptor(Type.class.descriptorString());
        final ClassDesc factoryDesc = ClassDesc.ofDescriptor(JsonFactory.class.descriptorString());
        final ClassDesc encodeableDesc = ClassDesc.ofDescriptor(Encodeable.class.descriptorString());
        final MethodTypeDesc constructorDesc = MethodTypeDesc.of(CD_void, factoryDesc, typeDesc, objectEncoderDesc);
        final MethodTypeDesc convertDesc = MethodTypeDesc.of(CD_void, writerDesc, valueDesc);
        // ------------------------------------------------------------------------------
        byte[] bytes = ClassFile.of().build(dynDesc, cb -> {
            cb.withVersion(JAVA_11_VERSION, 0)
                    .withFlags(ACC_PUBLIC | ACC_FINAL | ACC_SUPER)
                    .withSuperclass(superDesc);
            cb.with(SignatureAttribute.of(ClassSignature.parseFrom("L"
                    + JsonDynEncoder.class.getName().replace('.', '/') + "<" + valueDesc.descriptorString() + ">;")));
            for (AccessibleObject element : elements) {
                final String fieldName = factory.readConvertFieldName(clazz, element);
                cb.withField(fieldName + "FieldBytes", CD_byte.arrayType(), ACC_PROTECTED | ACC_FINAL);
                cb.withField(fieldName + "FieldChars", CD_char.arrayType(), ACC_PROTECTED | ACC_FINAL);
                final Class fieldType = readGetSetFieldType(element);
                if (fieldType != String.class && !fieldType.isPrimitive()) {
                    cb.withField(fieldName + "Encoder", encodeableDesc, ACC_PROTECTED);
                }
            }
            cb.withMethodBody("<init>", constructorDesc, ACC_PUBLIC, code -> {
                code.aload(0).aload(1).aload(2).aload(3).invokespecial(superDesc, "<init>", constructorDesc);
                for (AccessibleObject element : elements) {
                    final String fieldName = factory.readConvertFieldName(clazz, element);
                    code.aload(0)
                            .loadConstant("\"" + fieldName + "\":")
                            .invokevirtual(CD_String, "getBytes", MethodTypeDesc.of(CD_byte.arrayType()))
                            .putfield(dynDesc, fieldName + "FieldBytes", CD_byte.arrayType());
                    code.aload(0)
                            .loadConstant("\"" + fieldName + "\":")
                            .invokevirtual(CD_String, "toCharArray", MethodTypeDesc.of(CD_char.arrayType()))
                            .putfield(dynDesc, fieldName + "FieldChars", CD_char.arrayType());
                }
                code.return_();
            });
            cb.withMethodBody("convertTo", convertDesc, ACC_PUBLIC, code -> {
                // if (value == null) { out.writeObjectNull(null); return; }
                Label nonNull = code.newLabel();
                code.aload(2).ifnonnull(nonNull);
                code.aload(1)
                        .aconst_null()
                        .invokevirtual(writerDesc, "writeObjectNull", MethodTypeDesc.of(CD_void, CD_Class))
                        .return_();
                code.labelBinding(nonNull);
                // if (!out.isExtFuncEmpty()) { objectEncoderSelf.convertTo(out, value); return; }
                Label noExtFunc = code.newLabel();
                code.aload(1)
                        .invokevirtual(writerDesc, "isExtFuncEmpty", MethodTypeDesc.of(CD_boolean))
                        .ifne(noExtFunc);
                code.aload(0)
                        .getfield(dynDesc, "objectEncoderSelf", objectEncoderDesc)
                        .aload(1)
                        .aload(2)
                        .invokevirtual(
                                objectEncoderDesc,
                                "convertTo",
                                MethodTypeDesc.of(
                                        CD_void, ClassDesc.ofDescriptor(Writer.class.descriptorString()), CD_Object))
                        .return_();
                code.labelBinding(noExtFunc);
                code.aload(1)
                        .loadConstant((int) '{')
                        .invokevirtual(writerDesc, "writeTo", MethodTypeDesc.of(CD_void, CD_byte));
                Class firstType = readGetSetFieldType(elements.get(0));
                final boolean trackComma = elements.size() > 1
                        && !ConvertFactory.checkNullableFeature(features)
                        && !((!ConvertFactory.checkTinyFeature(features) || firstType != boolean.class)
                                && firstType.isPrimitive());
                if (trackComma) {
                    code.iconst_0().istore(3);
                }
                Label byteMode = code.newLabel();
                Label end = code.newLabel();
                code.aload(1)
                        .invokevirtual(writerDesc, "charsMode", MethodTypeDesc.of(CD_boolean))
                        .ifeq(byteMode);
                dynConvertToMethod(clazz, dynDesc, code, factory, mixedNames, elements, trackComma, true);
                code.goto_(end).labelBinding(byteMode);
                dynConvertToMethod(clazz, dynDesc, code, factory, mixedNames, elements, trackComma, false);
                code.labelBinding(end);
                code.aload(1)
                        .loadConstant((int) '}')
                        .invokevirtual(writerDesc, "writeTo", MethodTypeDesc.of(CD_void, CD_byte));
                code.return_();
            });
            cb.withMethodBody(
                    "convertTo",
                    MethodTypeDesc.of(CD_void, writerDesc, CD_Object),
                    ACC_PUBLIC | ACC_BRIDGE | ACC_SYNTHETIC,
                    code -> code.aload(0)
                            .aload(1)
                            .aload(2)
                            .checkcast(valueDesc)
                            .invokevirtual(dynDesc, "convertTo", convertDesc)
                            .return_());
        });
        // ------------------------------------------------------------------------------
        Class<?> newClazz = loader.loadClass(newDynName.replace('/', '.'), bytes);
        RedkaleClassLoader.putReflectionDeclaredConstructors(
                newClazz, newDynName.replace('/', '.'), JsonFactory.class, Type.class);
        try {
            JsonDynEncoder resultEncoder =
                    (JsonDynEncoder) newClazz.getConstructor(JsonFactory.class, Type.class, ObjectEncoder.class)
                            .newInstance(factory, clazz, selfObjEncoder);
            for (Map.Entry<String, AccessibleObject> en : mixedNames.entrySet()) {
                Field f = newClazz.getDeclaredField(en.getKey() + "Encoder");
                f.setAccessible(true);
                f.set(
                        resultEncoder,
                        factory.loadEncoder(
                                en.getValue() instanceof Field
                                        ? ((Field) en.getValue()).getGenericType()
                                        : ((Method) en.getValue()).getGenericReturnType()));
                RedkaleClassLoader.putReflectionField(newDynName.replace('/', '.'), f);
            }
            return resultEncoder;
        } catch (Exception ex) {
            throw new ConvertException(ex);
        }
    }

    // 字段全部是primitive或String类型，且没有泛型的类才能动态生成JsonDynEncoder， 不支持的返回null
    public static JsonDynEncoder createDyncEncoder(final JsonFactory factory, final Type type) {
        if (!(type instanceof Class)) {
            return null;
        }
        // 发现有自定义的基础数据类型Encoder就不动态生成JsonDynEncoder了
        if (factory.loadEncoder(boolean.class) != BoolSimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(byte.class) != ByteSimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(short.class) != ShortSimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(char.class) != CharSimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(int.class) != IntSimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(float.class) != FloatSimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(long.class) != LongSimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(double.class) != DoubleSimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(String.class) != StringSimpledCoder.instance) {
            return null;
        }
        // array
        if (factory.loadEncoder(boolean[].class) != BoolArraySimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(byte[].class) != ByteArraySimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(short[].class) != ShortArraySimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(char[].class) != CharArraySimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(int[].class) != IntArraySimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(float[].class) != FloatArraySimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(long[].class) != LongArraySimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(double[].class) != DoubleArraySimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(String[].class) != StringArraySimpledCoder.instance) {
            return null;
        }

        final Class clazz = (Class) type;
        List<AccessibleObject> members = null;
        Set<String> names = new HashSet<>();
        try {
            ConvertColumnEntry ref;
            RedkaleClassLoader.putReflectionPublicFields(clazz.getName());
            for (final Field field : clazz.getFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (factory.isConvertDisabled(field)) {
                    continue;
                }
                ref = factory.findRef(clazz, field);
                if (ref != null && ref.ignore()) {
                    continue;
                }
                if (factory.findFieldCoder(clazz, field.getName()) != null) {
                    return null;
                }
                Type fieldType = field.getGenericType();
                if (!(factory.findEncoder(fieldType) instanceof JsonDynEncoder)
                        && !factory.isSimpleMemberType(clazz, fieldType, field.getType())) {
                    return null;
                }
                String name = factory.readConvertFieldName(clazz, field);
                if (names.contains(name)) {
                    continue;
                }
                names.add(name);
                if (members == null) {
                    members = new ArrayList<>();
                }
                members.add(field);
            }
            RedkaleClassLoader.putReflectionPublicMethods(clazz.getName());
            for (final Method method : clazz.getMethods()) {
                if (Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                if (Modifier.isAbstract(method.getModifiers())) {
                    continue;
                }
                if (method.isSynthetic()) {
                    continue;
                }
                if (method.getName().length() < 3) {
                    continue;
                }
                if (method.getName().equals("getClass")) {
                    continue;
                }
                if (!(method.getName().startsWith("is") && method.getName().length() > 2)
                        && !(method.getName().startsWith("get")
                                && method.getName().length() > 3)) {
                    continue;
                }
                if (factory.isConvertDisabled(method)) {
                    continue;
                }
                if (method.getParameterTypes().length != 0) {
                    continue;
                }
                if (method.getReturnType() == void.class) {
                    continue;
                }
                ref = factory.findRef(clazz, method);
                if (ref != null && ref.ignore()) {
                    continue;
                }
                if (ref != null && ref.fieldFunc() != null) {
                    return null;
                }
                Type getterType = method.getGenericReturnType();
                if (!(factory.findEncoder(getterType) instanceof JsonDynEncoder)
                        && !factory.isSimpleMemberType(clazz, getterType, method.getReturnType())) {
                    return null;
                }
                String name = factory.readConvertFieldName(clazz, method);
                if (names.contains(name)) {
                    continue;
                }
                if (factory.findFieldCoder(clazz, name) != null) {
                    return null;
                }
                names.add(name);
                if (members == null) {
                    members = new ArrayList<>();
                }
                members.add(method);
            }
            if (members == null) {
                return null;
            }
            factory.sortFieldIndex(clazz, members);
            return generateDyncEncoder(factory, clazz, members);
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    private static void dynConvertToMethod(
            final Class clazz,
            final ClassDesc dynDesc,
            final CodeBuilder code,
            final JsonFactory factory,
            final Map<String, AccessibleObject> mixedNames,
            final List<AccessibleObject> elements,
            final boolean trackComma,
            final boolean charMode) {
        final ClassDesc valueDesc = ClassDesc.ofDescriptor(clazz.descriptorString());
        final ClassDesc writerDesc = ClassDesc.ofDescriptor(JsonWriter.class.descriptorString());
        final ClassDesc encodeableDesc = ClassDesc.ofDescriptor(Encodeable.class.descriptorString());
        int elementIndex = -1;
        for (AccessibleObject element : elements) {
            elementIndex++;
            final String fieldName = factory.readConvertFieldName(clazz, element);
            final Class fieldType = readGetSetFieldType(element);
            final ClassDesc fieldDesc = ClassDesc.ofDescriptor(fieldType.descriptorString());
            code.aload(1).aload(0);
            if (charMode) {
                code.getfield(dynDesc, fieldName + "FieldChars", CD_char.arrayType());
            } else {
                code.getfield(dynDesc, fieldName + "FieldBytes", CD_byte.arrayType());
            }
            if (trackComma) {
                code.iload(3);
            } else {
                code.loadConstant(elementIndex == 0 ? 0 : 1);
            }
            if (mixedNames.containsKey(fieldName)) {
                code.aload(0).getfield(dynDesc, fieldName + "Encoder", encodeableDesc);
            }
            code.aload(2);
            if (element instanceof Field) {
                code.getfield(valueDesc, ((Field) element).getName(), fieldDesc);
            } else {
                Method method = (Method) element;
                if (clazz.isInterface()) {
                    code.invokeinterface(valueDesc, method.getName(), MethodTypeDesc.of(fieldDesc));
                } else {
                    code.invokevirtual(valueDesc, method.getName(), MethodTypeDesc.of(fieldDesc));
                }
            }
            String writeFieldName;
            if (fieldType == boolean.class || fieldType == Boolean.class) {
                writeFieldName = "writeFieldBooleanValue";
            } else if (fieldType == byte.class || fieldType == Byte.class) {
                writeFieldName = "writeFieldByteValue";
            } else if (fieldType == short.class || fieldType == Short.class) {
                writeFieldName = "writeFieldShortValue";
            } else if (fieldType == char.class || fieldType == Character.class) {
                writeFieldName = "writeFieldCharValue";
            } else if (fieldType == int.class || fieldType == Integer.class) {
                writeFieldName = "writeFieldIntValue";
            } else if (fieldType == float.class || fieldType == Float.class) {
                writeFieldName = "writeFieldFloatValue";
            } else if (fieldType == long.class || fieldType == Long.class) {
                writeFieldName = "writeFieldLongValue";
            } else if (fieldType == double.class || fieldType == Double.class) {
                writeFieldName = "writeFieldDoubleValue";
            } else if (fieldType == String.class) {
                writeFieldName = isConvertStandardString(factory, element)
                        ? "writeFieldStandardStringValue"
                        : "writeFieldStringValue";
            } else {
                writeFieldName = "writeFieldObjectValue";
            }
            MethodTypeDesc writeDesc = mixedNames.containsKey(fieldName)
                    ? MethodTypeDesc.of(CD_boolean, CD_Object, CD_boolean, encodeableDesc, CD_Object)
                    : MethodTypeDesc.of(CD_boolean, CD_Object, CD_boolean, fieldDesc);
            code.invokevirtual(writerDesc, writeFieldName, writeDesc);
            if (trackComma && elementIndex + 1 < elements.size()) {
                code.istore(3);
            } else {
                code.pop();
            }
        }
    }

    protected static boolean isConvertStandardString(JsonFactory factory, AccessibleObject element) {
        if (element instanceof Field) {
            return ((Field) element).getAnnotation(ConvertStandardString.class) != null;
        }
        Method method = (Method) element;
        ConvertStandardString standard = method.getAnnotation(ConvertStandardString.class);
        if (standard == null) {
            try {
                Field f = method.getDeclaringClass().getDeclaredField(factory.readGetSetFieldName(method));
                if (f != null) {
                    standard = f.getAnnotation(ConvertStandardString.class);
                }
            } catch (Exception e) {
                // do nothing
            }
        }
        return standard != null;
    }

    protected static Class readGetSetFieldType(AccessibleObject element) {
        if (element instanceof Field) {
            return ((Field) element).getType();
        }
        return ((Method) element).getReturnType();
    }

    @Override
    public abstract void convertTo(JsonWriter out, T value);

    @Override
    public boolean specifyable() {
        return false;
    }

    @Override
    public Type getType() {
        return typeClass;
    }
}
