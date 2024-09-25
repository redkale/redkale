/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.convert.pb;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import org.redkale.asm.Asms;
import org.redkale.asm.ClassWriter;
import static org.redkale.asm.ClassWriter.COMPUTE_FRAMES;
import org.redkale.asm.FieldVisitor;
import org.redkale.asm.Label;
import org.redkale.asm.MethodVisitor;
import org.redkale.asm.Opcodes;
import static org.redkale.asm.Opcodes.*;
import org.redkale.convert.*;
import org.redkale.convert.ext.*;
import org.redkale.util.AnyValue;
import org.redkale.util.RedkaleClassLoader;
import org.redkale.util.RedkaleException;
import org.redkale.util.Utility;

/**
 * 简单对象的PROTOBUF序列化操作类
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 * @param <T> 序列化的数据类型
 */
public abstract class ProtobufDynEncoder<T> extends ProtobufObjectEncoder<T> {

    protected final Class typeClass;

    protected final ObjectEncoder<ProtobufWriter, T> objectEncoderSelf;

    // 动态字段: protected SimpledCoder xxxSimpledCoder;

    // 动态字段: protected EnMember xxxEnMember;

    protected ProtobufDynEncoder(ProtobufFactory factory, Type type, ObjectEncoder objectEncoderSelf) {
        super((Class) type);
        this.typeClass = (Class) type;
        this.factory = factory;
        this.objectEncoderSelf = objectEncoderSelf;
        this.members = objectEncoderSelf.getMembers();
        this.inited = true;
        factory.register(type, this);
    }

    @Override
    public abstract void convertTo(ProtobufWriter out, T value);

    protected static ProtobufDynEncoder generateDyncEncoder(final ProtobufFactory factory, final Class clazz) {
        final ObjectEncoder selfObjEncoder = factory.createObjectEncoder(clazz);
        selfObjEncoder.init(factory); // 必须执行，初始化EnMember内部信息

        final Map<String, SimpledCoder> simpledCoders = new HashMap<>();
        final Map<String, EnMember> otherMembers = new HashMap<>();
        StringBuilder elementb = new StringBuilder();
        for (EnMember member : selfObjEncoder.getMembers()) {
            final String fieldName = member.getAttribute().field();
            final Class fieldClass = member.getAttribute().type();
            final Type fieldType = member.getAttribute().genericType();
            elementb.append(fieldName).append(',');
            if (!ProtobufWriter.isSimpleType(fieldClass)
                    && !fieldClass.isEnum()
                    && !ProtobufWriter.supportSimpleCollectionType(fieldType)) {
                if ((member.getEncoder() instanceof SimpledCoder)) {
                    simpledCoders.put(fieldName, (SimpledCoder) member.getEncoder());
                } else {
                    otherMembers.put(fieldName, member);
                }
            }
        }

        final ClassLoader loader = Thread.currentThread().getContextClassLoader();
        final String newDynName = "org/redkaledyn/pb/_Dyn" + ProtobufDynEncoder.class.getSimpleName() + "__"
                + clazz.getName().replace('.', '_').replace('$', '_') + "_" + factory.getFeatures() + "_"
                + Utility.md5Hex(elementb.toString()); // tiny必须要加上, 同一个类会有多个字段定制Convert
        try {
            Class clz = RedkaleClassLoader.findDynClass(newDynName.replace('/', '.'));
            Class newClazz = clz == null ? loader.loadClass(newDynName.replace('/', '.')) : clz;
            ProtobufDynEncoder resultEncoder =
                    (ProtobufDynEncoder) newClazz.getConstructor(ProtobufFactory.class, Type.class, ObjectEncoder.class)
                            .newInstance(factory, clazz, selfObjEncoder);
            if (!simpledCoders.isEmpty()) {
                for (Map.Entry<String, SimpledCoder> en : simpledCoders.entrySet()) {
                    Field f = newClazz.getDeclaredField(en.getKey() + "SimpledCoder");
                    f.setAccessible(true);
                    f.set(resultEncoder, en.getValue());
                }
            }
            if (!otherMembers.isEmpty()) {
                for (Map.Entry<String, EnMember> en : otherMembers.entrySet()) {
                    Field f = newClazz.getDeclaredField(en.getKey() + "EnMember");
                    f.setAccessible(true);
                    f.set(resultEncoder, en.getValue());
                }
            }
            return resultEncoder;
        } catch (Throwable ex) {
            // do nothing
        }

        final String supDynName = ProtobufDynEncoder.class.getName().replace('.', '/');
        final String valtypeName = clazz.getName().replace('.', '/');
        final String pbwriterName = ProtobufWriter.class.getName().replace('.', '/');
        final String typeDesc = org.redkale.asm.Type.getDescriptor(Type.class);
        final String pbfactoryDesc = org.redkale.asm.Type.getDescriptor(ProtobufFactory.class);
        final String pbwriterDesc = org.redkale.asm.Type.getDescriptor(ProtobufWriter.class);
        final String simpledCoderDesc = org.redkale.asm.Type.getDescriptor(SimpledCoder.class);
        final String enMemberDesc = org.redkale.asm.Type.getDescriptor(EnMember.class);
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
        if (!simpledCoders.isEmpty()) {
            for (String key : simpledCoders.keySet()) {
                fv = cw.visitField(ACC_PROTECTED, key + "SimpledCoder", simpledCoderDesc, null, null);
                fv.visitEnd();
            }
        }
        if (!otherMembers.isEmpty()) {
            for (String key : otherMembers.keySet()) {
                fv = cw.visitField(ACC_PROTECTED, key + "EnMember", enMemberDesc, null, null);
                fv.visitEnd();
            }
        }
        { // 构造函数
            mv = (cw.visitMethod(
                    ACC_PUBLIC, "<init>", "(" + pbfactoryDesc + typeDesc + objEncoderDesc + ")V", null, null));
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitVarInsn(ALOAD, 2);
            mv.visitVarInsn(ALOAD, 3);
            mv.visitMethodInsn(
                    INVOKESPECIAL, supDynName, "<init>", "(" + pbfactoryDesc + typeDesc + objEncoderDesc + ")V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(4, 4);
            mv.visitEnd();
        }
        { // convertTo 方法
            mv = (cw.visitMethod(ACC_PUBLIC, "convertTo", "(" + pbwriterDesc + valtypeDesc + ")V", null, null));
            // if (value == null) return;
            mv.visitVarInsn(ALOAD, 2); // value
            Label ifLabel = new Label();
            mv.visitJumpInsn(IFNONNULL, ifLabel);
            mv.visitInsn(RETURN);
            mv.visitLabel(ifLabel);
            mv.visitLineNumber(33, ifLabel);
            mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            // ProtobufWriter out = objectWriter(out0, value);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitVarInsn(ALOAD, 2);
            mv.visitMethodInsn(
                    INVOKEVIRTUAL,
                    newDynName,
                    "objectWriter",
                    "(" + pbwriterDesc + objectDesc + ")" + pbwriterDesc,
                    false);
            mv.visitVarInsn(ASTORE, 3);

            mv.visitVarInsn(ALOAD, 3);
            mv.visitVarInsn(ALOAD, 2);
            mv.visitMethodInsn(INVOKEVIRTUAL, pbwriterName, "writeObjectB", "(Ljava/lang/Object;)I", false);
            mv.visitInsn(POP);

            for (EnMember member : selfObjEncoder.getMembers()) {
                final String fieldName = member.getAttribute().field();
                final Type fieldType = member.getAttribute().genericType();
                final Class fieldClass = member.getAttribute().type();
                if (ProtobufWriter.isSimpleType(fieldClass)) {
                    mv.visitVarInsn(ALOAD, 3); // out
                    Asms.visitInsn(mv, member.getTag()); // tag
                    mv.visitVarInsn(ALOAD, 2); // value
                    if (member.getMethod() != null) {
                        String mname = member.getMethod().getName();
                        String mdesc = org.redkale.asm.Type.getMethodDescriptor(member.getMethod());
                        mv.visitMethodInsn(INVOKEVIRTUAL, valtypeName, mname, mdesc, false);
                    } else { // field
                        Field field = member.getField();
                        String fname = field.getName();
                        String fdesc = org.redkale.asm.Type.getDescriptor(field.getType());
                        mv.visitFieldInsn(GETFIELD, valtypeName, fname, fdesc);
                    }
                    String fieldDesc = org.redkale.asm.Type.getDescriptor(fieldClass);
                    mv.visitMethodInsn(INVOKEVIRTUAL, pbwriterName, "writeFieldValue", "(I" + fieldDesc + ")V", false);
                } else if (fieldClass.isEnum()) {
                    mv.visitVarInsn(ALOAD, 3); // out
                    Asms.visitInsn(mv, member.getTag()); // tag
                    mv.visitVarInsn(ALOAD, 2); // value
                    if (member.getMethod() != null) {
                        String mname = member.getMethod().getName();
                        String mdesc = org.redkale.asm.Type.getMethodDescriptor(member.getMethod());
                        mv.visitMethodInsn(INVOKEVIRTUAL, valtypeName, mname, mdesc, false);
                    } else { // field
                        Field field = member.getField();
                        String fname = field.getName();
                        String fdesc = org.redkale.asm.Type.getDescriptor(field.getType());
                        mv.visitFieldInsn(GETFIELD, valtypeName, fname, fdesc);
                    }
                    mv.visitMethodInsn(INVOKEVIRTUAL, pbwriterName, "writeFieldValue", "(ILjava/lang/Enum;)V", false);
                } else if (ProtobufWriter.supportSimpleCollectionType(fieldType)) {
                    mv.visitVarInsn(ALOAD, 3); // out
                    Asms.visitInsn(mv, member.getTag()); // tag
                    mv.visitVarInsn(ALOAD, 2); // value
                    if (member.getMethod() != null) {
                        String mname = member.getMethod().getName();
                        String mdesc = org.redkale.asm.Type.getMethodDescriptor(member.getMethod());
                        mv.visitMethodInsn(INVOKEVIRTUAL, valtypeName, mname, mdesc, false);
                    } else { // field
                        Field field = member.getField();
                        String fname = field.getName();
                        String fdesc = org.redkale.asm.Type.getDescriptor(field.getType());
                        mv.visitFieldInsn(GETFIELD, valtypeName, fname, fdesc);
                    }
                    Class componentType = ProtobufWriter.getSimpleCollectionComponentType(fieldType);
                    String wmethodName = null;
                    if (componentType == Boolean.class) {
                        wmethodName = "writeFieldBoolsValue";
                    } else if (componentType == Byte.class) {
                        wmethodName = "writeFieldBytesValue";
                    } else if (componentType == Character.class) {
                        wmethodName = "writeFieldCharsValue";
                    } else if (componentType == Short.class) {
                        wmethodName = "writeFieldShortsValue";
                    } else if (componentType == Integer.class) {
                        wmethodName = "writeFieldIntsValue";
                    } else if (componentType == Float.class) {
                        wmethodName = "writeFieldFloatsValue";
                    } else if (componentType == Long.class) {
                        wmethodName = "writeFieldLongsValue";
                    } else if (componentType == Double.class) {
                        wmethodName = "writeFieldDoublesValue";
                    } else if (componentType == String.class) {
                        wmethodName = "writeFieldStringValue";
                    }
                    mv.visitMethodInsn(INVOKEVIRTUAL, pbwriterName, wmethodName, "(ILjava/util/Collection;)V", false);
                } else if (simpledCoders.containsKey(fieldName)) {
                    mv.visitVarInsn(ALOAD, 3); // out
                    Asms.visitInsn(mv, member.getTag()); // tag
                    mv.visitVarInsn(ALOAD, 0); // this
                    mv.visitFieldInsn(GETFIELD, newDynName, fieldName + "SimpledCoder", simpledCoderDesc);
                    mv.visitVarInsn(ALOAD, 2); // value
                    if (member.getMethod() != null) {
                        String mname = member.getMethod().getName();
                        String mdesc = org.redkale.asm.Type.getMethodDescriptor(member.getMethod());
                        mv.visitMethodInsn(INVOKEVIRTUAL, valtypeName, mname, mdesc, false);
                    } else { // field
                        Field field = member.getField();
                        String fname = field.getName();
                        String fdesc = org.redkale.asm.Type.getDescriptor(field.getType());
                        mv.visitFieldInsn(GETFIELD, valtypeName, fname, fdesc);
                    }
                    mv.visitMethodInsn(
                            INVOKEVIRTUAL,
                            pbwriterName,
                            "writeFieldValue",
                            "(I" + simpledCoderDesc + objectDesc + ")V",
                            false);
                } else {
                    mv.visitVarInsn(ALOAD, 3); // out
                    mv.visitVarInsn(ALOAD, 0); // this
                    mv.visitFieldInsn(GETFIELD, newDynName, fieldName + "EnMember", enMemberDesc);
                    mv.visitVarInsn(ALOAD, 2); // value
                    mv.visitMethodInsn(
                            INVOKEVIRTUAL,
                            pbwriterName,
                            "writeObjectField",
                            "(" + enMemberDesc + objectDesc + ")V",
                            false);
                }
            }
            // out.writeObjectE(value);
            mv.visitVarInsn(ALOAD, 3); // out
            mv.visitVarInsn(ALOAD, 2); // value
            mv.visitMethodInsn(INVOKEVIRTUAL, pbwriterName, "writeObjectE", "(Ljava/lang/Object;)V", false);
            // offerWriter(out0, out);
            mv.visitVarInsn(ALOAD, 0); // this
            mv.visitVarInsn(ALOAD, 1); // out0
            mv.visitVarInsn(ALOAD, 3); // out
            mv.visitMethodInsn(
                    INVOKEVIRTUAL, newDynName, "offerWriter", "(" + pbwriterDesc + pbwriterDesc + ")V", false);

            mv.visitInsn(RETURN);
            mv.visitMaxs(4, 3);
            mv.visitEnd();
        }
        { // convertTo 虚拟方法
            mv = (cw.visitMethod(
                    ACC_PUBLIC + ACC_BRIDGE + ACC_SYNTHETIC,
                    "convertTo",
                    "(" + pbwriterDesc + "Ljava/lang/Object;)V",
                    null,
                    null));
            // mv.setDebug(true);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitVarInsn(ALOAD, 2);
            mv.visitTypeInsn(CHECKCAST, valtypeName);
            mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, "convertTo", "(" + pbwriterDesc + valtypeDesc + ")V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(3, 3);
            mv.visitEnd();
        }
        cw.visitEnd();

        byte[] bytes = cw.toByteArray();
        Class<ProtobufDynEncoder> newClazz = (Class<ProtobufDynEncoder>)
                new ClassLoader(loader) {
                    public final Class<?> loadClass(String name, byte[] b) {
                        return defineClass(name, b, 0, b.length);
                    }
                }.loadClass(newDynName.replace('/', '.'), bytes);
        RedkaleClassLoader.putDynClass(newDynName.replace('/', '.'), bytes, newClazz);
        RedkaleClassLoader.putReflectionDeclaredConstructors(newClazz, newDynName.replace('/', '.'));
        try {
            ProtobufDynEncoder resultEncoder =
                    (ProtobufDynEncoder) newClazz.getConstructor(ProtobufFactory.class, Type.class, ObjectEncoder.class)
                            .newInstance(factory, clazz, selfObjEncoder);
            if (!simpledCoders.isEmpty()) {
                for (Map.Entry<String, SimpledCoder> en : simpledCoders.entrySet()) {
                    Field f = newClazz.getDeclaredField(en.getKey() + "SimpledCoder");
                    f.setAccessible(true);
                    f.set(resultEncoder, en.getValue());
                    RedkaleClassLoader.putReflectionField(newClazz.getName(), f);
                }
            }
            if (!otherMembers.isEmpty()) {
                for (Map.Entry<String, EnMember> en : otherMembers.entrySet()) {
                    Field f = newClazz.getDeclaredField(en.getKey() + "EnMember");
                    f.setAccessible(true);
                    f.set(resultEncoder, en.getValue());
                    RedkaleClassLoader.putReflectionField(newClazz.getName(), f);
                }
            }
            return resultEncoder;
        } catch (Exception ex) {
            throw new RedkaleException(ex);
        }
    }

    // 字段全部是primitive或String类型，且没有泛型的类才能动态生成ProtobufDynEncoder， 不支持的返回null
    public static ProtobufDynEncoder createDyncEncoder(final ProtobufFactory factory, final Type type) {
        if (!(type instanceof Class)) {
            return null;
        }
        if (AnyValue.class.isAssignableFrom((Class) type)) {
            return null;
        }

        // 发现有自定义的基础数据类型Encoder就不动态生成ProtobufDynEncoder了
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
        return generateDyncEncoder(factory, (Class) type);
    }

    @Override
    public Type getType() {
        return typeClass;
    }
}
