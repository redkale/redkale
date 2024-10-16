/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.json;

import java.lang.reflect.*;
import java.util.*;
import org.redkale.asm.ClassWriter;
import static org.redkale.asm.ClassWriter.COMPUTE_FRAMES;
import org.redkale.asm.FieldVisitor;
import org.redkale.asm.Label;
import org.redkale.asm.MethodVisitor;
import org.redkale.asm.Opcodes;
import static org.redkale.asm.Opcodes.*;
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

    protected final Class typeClass;

    protected final ObjectEncoder<JsonWriter, T> objectEncoderSelf;

    protected JsonDynEncoder(JsonFactory factory, Type type, ObjectEncoder objectEncoderSelf) {
        super(type);
        this.typeClass = (Class) type;
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
                + clazz.getName().replace('.', '_').replace('$', '_') + "_" + factory.getFeatures() + "_"
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

        final String supDynName = JsonDynEncoder.class.getName().replace('.', '/');
        final String valtypeName = clazz.getName().replace('.', '/');
        final String writerName = JsonWriter.class.getName().replace('.', '/');
        final String objEncoderName = ObjectEncoder.class.getName().replace('.', '/');
        final String typeDesc = org.redkale.asm.Type.getDescriptor(Type.class);
        final String jsonfactoryDesc = org.redkale.asm.Type.getDescriptor(JsonFactory.class);
        final String jsonwriterDesc = org.redkale.asm.Type.getDescriptor(JsonWriter.class);
        final String encodeableDesc = org.redkale.asm.Type.getDescriptor(Encodeable.class);
        final String objEncoderDesc = org.redkale.asm.Type.getDescriptor(ObjectEncoder.class);
        final String objectDesc = org.redkale.asm.Type.getDescriptor(Object.class);
        final String valtypeDesc = org.redkale.asm.Type.getDescriptor(clazz);
        // ------------------------------------------------------------------------------
        ClassWriter cw = new ClassWriter(COMPUTE_FRAMES);
        FieldVisitor fv;
        MethodVisitor mv;
        cw.visit(
                V11,
                ACC_PUBLIC + ACC_FINAL + ACC_SUPER,
                newDynName,
                "L" + supDynName + "<" + valtypeDesc + ">;",
                supDynName,
                null);

        for (AccessibleObject element : elements) {
            final String fieldName = factory.readConvertFieldName(clazz, element);
            fv = cw.visitField(ACC_PROTECTED + ACC_FINAL, fieldName + "FieldBytes", "[B", null, null);
            fv.visitEnd();
            fv = cw.visitField(ACC_PROTECTED + ACC_FINAL, fieldName + "FieldChars", "[C", null, null);
            fv.visitEnd();
            final Class fieldType = readGetSetFieldType(element);
            if (fieldType != String.class && !fieldType.isPrimitive()) {
                fv = cw.visitField(ACC_PROTECTED, fieldName + "Encoder", encodeableDesc, null, null);
                fv.visitEnd();
            }
        }
        { // 构造函数
            mv = (cw.visitMethod(
                    ACC_PUBLIC, "<init>", "(" + jsonfactoryDesc + typeDesc + objEncoderDesc + ")V", null, null));
            // mv.setDebug(true);
            Label label0 = new Label();
            mv.visitLabel(label0);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitVarInsn(ALOAD, 2);
            mv.visitVarInsn(ALOAD, 3);
            mv.visitMethodInsn(
                    INVOKESPECIAL,
                    supDynName,
                    "<init>",
                    "(" + jsonfactoryDesc + typeDesc + objEncoderDesc + ")V",
                    false);

            for (AccessibleObject element : elements) {
                final String fieldName = factory.readConvertFieldName(clazz, element);
                // xxxFieldBytes
                mv.visitVarInsn(ALOAD, 0);
                mv.visitLdcInsn("\"" + fieldName + "\":");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "getBytes", "()[B", false);
                mv.visitFieldInsn(PUTFIELD, newDynName, fieldName + "FieldBytes", "[B");
                // xxxFieldChars
                mv.visitVarInsn(ALOAD, 0);
                mv.visitLdcInsn("\"" + fieldName + "\":");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "toCharArray", "()[C", false);
                mv.visitFieldInsn(PUTFIELD, newDynName, fieldName + "FieldChars", "[C");
            }
            mv.visitInsn(RETURN);
            Label label2 = new Label();
            mv.visitLabel(label2);
            mv.visitLocalVariable("this", "L" + newDynName + ";", null, label0, label2, 0);
            mv.visitLocalVariable("factory", jsonfactoryDesc, null, label0, label2, 1);
            mv.visitLocalVariable("type", typeDesc, null, label0, label2, 2);
            mv.visitLocalVariable("objectEncoderSelf", objEncoderDesc, null, label0, label2, 3);
            mv.visitMaxs(1 + elements.size(), 1 + elements.size());
            mv.visitEnd();
        }

        { // convertTo 方法
            mv = (cw.visitMethod(ACC_PUBLIC, "convertTo", "(" + jsonwriterDesc + valtypeDesc + ")V", null, null));
            Label commaLabel = null;
            Label label0 = new Label();
            mv.visitLabel(label0);
            // mv.setDebug(true);
            { // if (value == null) { out.writeObjectNull(null);  return; }
                mv.visitVarInsn(ALOAD, 2);
                Label valif = new Label();
                mv.visitJumpInsn(IFNONNULL, valif);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitInsn(ACONST_NULL);
                mv.visitMethodInsn(INVOKEVIRTUAL, writerName, "writeObjectNull", "(Ljava/lang/Class;)V", false);
                mv.visitInsn(RETURN);
                mv.visitLabel(valif);
                mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            }
            { // if (!out.isExtFuncEmpty()) { objectEncoder.convertTo(out, value);  return; }
                mv.visitVarInsn(ALOAD, 1);
                mv.visitMethodInsn(INVOKEVIRTUAL, writerName, "isExtFuncEmpty", "()Z", false);
                Label extif = new Label();
                mv.visitJumpInsn(IFNE, extif);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, newDynName, "objectEncoderSelf", objEncoderDesc);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitVarInsn(ALOAD, 2);
                mv.visitMethodInsn(
                        INVOKEVIRTUAL,
                        objEncoderName,
                        "convertTo",
                        "(" + org.redkale.asm.Type.getDescriptor(Writer.class) + "Ljava/lang/Object;)V",
                        false);
                mv.visitInsn(RETURN);
                mv.visitLabel(extif);
                mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            }

            int elementIndex = -1;
            {
                { // out.writeTo('{');
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitIntInsn(BIPUSH, '{');
                    mv.visitMethodInsn(INVOKEVIRTUAL, writerName, "writeTo", "(B)V", false);
                }
                // boolean comma = false;
                if (elements.size() > 1) {
                    mv.visitInsn(ICONST_0);
                    mv.visitVarInsn(ISTORE, 3);
                    commaLabel = new Label();
                    mv.visitLabel(commaLabel);
                }
                // comma = out.writeFieldIntValue(ageFieldBytes, ageFieldChars, comma, value.getAge());
                for (AccessibleObject element : elements) {
                    elementIndex++;
                    final String fieldName = factory.readConvertFieldName(clazz, element);
                    final Class fieldType = readGetSetFieldType(element);
                    mv.visitVarInsn(ALOAD, 1); // JsonWriter
                    mv.visitVarInsn(ALOAD, 0); // this.xxxFieldBytes  第一个参数
                    mv.visitFieldInsn(GETFIELD, newDynName, fieldName + "FieldBytes", "[B");
                    mv.visitVarInsn(ALOAD, 0); // this.xxxFieldChars  第二个参数
                    mv.visitFieldInsn(GETFIELD, newDynName, fieldName + "FieldChars", "[C");
                    if (commaLabel != null) {
                        mv.visitVarInsn(ILOAD, 3); // comma 第三个参数
                    } else {
                        mv.visitInsn(ICONST_0); // comma=false 第三个参数
                    }
                    if (mixedNames.containsKey(fieldName)) { // Encodeable  第四个参数
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitFieldInsn(GETFIELD, newDynName, fieldName + "Encoder", encodeableDesc);
                    }
                    mv.visitVarInsn(ALOAD, 2); // value.getXXX()  第四/五个参数
                    if (element instanceof Field) {
                        mv.visitFieldInsn(
                                GETFIELD,
                                valtypeName,
                                ((Field) element).getName(),
                                org.redkale.asm.Type.getDescriptor(fieldType));
                    } else {
                        mv.visitMethodInsn(
                                ((Method) element).getDeclaringClass().isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL,
                                valtypeName,
                                ((Method) element).getName(),
                                "()" + org.redkale.asm.Type.getDescriptor(fieldType),
                                false);
                    }
                    if (fieldType == boolean.class || fieldType == Boolean.class) {
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL,
                                writerName,
                                "writeFieldBooleanValue",
                                "([B[CZ" + org.redkale.asm.Type.getDescriptor(fieldType) + ")Z",
                                false);
                    } else if (fieldType == byte.class || fieldType == Byte.class) {
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL,
                                writerName,
                                "writeFieldByteValue",
                                "([B[CZ" + org.redkale.asm.Type.getDescriptor(fieldType) + ")Z",
                                false);
                    } else if (fieldType == short.class || fieldType == Short.class) {
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL,
                                writerName,
                                "writeFieldShortValue",
                                "([B[CZ" + org.redkale.asm.Type.getDescriptor(fieldType) + ")Z",
                                false);
                    } else if (fieldType == char.class || fieldType == Character.class) {
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL,
                                writerName,
                                "writeFieldCharValue",
                                "([B[CZ" + org.redkale.asm.Type.getDescriptor(fieldType) + ")Z",
                                false);
                    } else if (fieldType == int.class || fieldType == Integer.class) {
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL,
                                writerName,
                                "writeFieldIntValue",
                                "([B[CZ" + org.redkale.asm.Type.getDescriptor(fieldType) + ")Z",
                                false);
                    } else if (fieldType == float.class || fieldType == Float.class) {
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL,
                                writerName,
                                "writeFieldFloatValue",
                                "([B[CZ" + org.redkale.asm.Type.getDescriptor(fieldType) + ")Z",
                                false);
                    } else if (fieldType == long.class || fieldType == Long.class) {
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL,
                                writerName,
                                "writeFieldLongValue",
                                "([B[CZ" + org.redkale.asm.Type.getDescriptor(fieldType) + ")Z",
                                false);
                    } else if (fieldType == double.class || fieldType == Double.class) {
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL,
                                writerName,
                                "writeFieldDoubleValue",
                                "([B[CZ" + org.redkale.asm.Type.getDescriptor(fieldType) + ")Z",
                                false);
                    } else if (fieldType == String.class) {
                        String writeFieldName = "writeFieldStringValue";
                        if (isConvertStandardString(factory, element)) {
                            writeFieldName = "writeFieldStandardStringValue";
                        }
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL,
                                writerName,
                                writeFieldName,
                                "([B[CZ" + org.redkale.asm.Type.getDescriptor(fieldType) + ")Z",
                                false);
                    } else {
                        // writeFieldObjectValue(fieldBytes, fieldChars, comma, encodeable, value)
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL,
                                writerName,
                                "writeFieldObjectValue",
                                "([B[CZ" + encodeableDesc + objectDesc + ")Z",
                                false);
                    }
                    if (commaLabel != null && elementIndex + 1 < elements.size()) {
                        mv.visitVarInsn(ISTORE, 3); // comma = out.writeFieldXXXValue()
                    } else {
                        mv.visitInsn(POP);
                    }
                }
                { // out.writeTo('}');
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitIntInsn(BIPUSH, '}');
                    mv.visitMethodInsn(INVOKEVIRTUAL, writerName, "writeTo", "(B)V", false);
                }
            }
            mv.visitInsn(RETURN);
            Label label2 = new Label();
            mv.visitLabel(label2);
            mv.visitLocalVariable("this", "L" + newDynName + ";", null, label0, label2, 0);
            mv.visitLocalVariable("out", "Lorg/redkale/convert/json/JsonWriter;", null, label0, label2, 1);
            mv.visitLocalVariable("value", "Lorg/redkale/test/convert/json/Message;", null, label0, label2, 2);
            if (commaLabel != null) {
                mv.visitLocalVariable("comma", "Z", null, commaLabel, label2, 3);
            }
            mv.visitMaxs(6, 4);
            mv.visitEnd();
        }
        { // convertTo 虚拟方法
            mv = (cw.visitMethod(
                    ACC_PUBLIC + ACC_BRIDGE + ACC_SYNTHETIC,
                    "convertTo",
                    "(" + jsonwriterDesc + "Ljava/lang/Object;)V",
                    null,
                    null));
            // mv.setDebug(true);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitVarInsn(ALOAD, 2);
            mv.visitTypeInsn(CHECKCAST, valtypeName);
            mv.visitMethodInsn(
                    INVOKEVIRTUAL, newDynName, "convertTo", "(" + jsonwriterDesc + valtypeDesc + ")V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(3, 3);
            mv.visitEnd();
        }
        cw.visitEnd();
        // ------------------------------------------------------------------------------
        byte[] bytes = cw.toByteArray();
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

    protected static boolean isConvertStandardString(JsonFactory factory, AccessibleObject element) {
        if (element instanceof Field) {
            return ((Field) element).getAnnotation(ConvertStandardString.class) != null
                    || ((Field) element).getAnnotation(ConvertSmallString.class) != null;
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
        if (standard == null) {
            if (method.getAnnotation(ConvertSmallString.class) != null) {
                return true;
            }
            try {
                Field f = method.getDeclaringClass().getDeclaredField(factory.readGetSetFieldName(method));
                if (f != null && f.getAnnotation(ConvertSmallString.class) != null) {
                    return true;
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
