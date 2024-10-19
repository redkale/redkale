/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.convert.pb;

import java.lang.reflect.*;
import java.lang.reflect.Type;
import java.util.*;
import org.redkale.asm.*;
import static org.redkale.asm.ClassWriter.COMPUTE_FRAMES;
import static org.redkale.asm.Opcodes.*;
import org.redkale.convert.*;
import org.redkale.util.*;

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

    protected ProtobufDynEncoder(ProtobufFactory factory, Type type, ProtobufObjectEncoder objectEncoderSelf) {
        super((Class) type);
        this.typeClass = (Class) type;
        this.factory = factory;
        this.objectEncoderSelf = objectEncoderSelf;
        this.members = objectEncoderSelf.getMembers();
        this.inited = true;
        factory.register(type, this);
    }

    @Override
    public abstract void convertTo(ProtobufWriter out, EnMember member, T value);

    protected static ProtobufDynEncoder generateDyncEncoder(final ProtobufFactory factory, final Class clazz) {
        final ObjectEncoder selfObjEncoder = factory.createObjectEncoder(clazz);
        factory.register(clazz, selfObjEncoder);
        selfObjEncoder.init(factory); // 必须执行，初始化EnMember内部信息

        final Map<String, SimpledCoder> simpledCoders = new HashMap<>();
        final Map<String, EnMember> otherMembers = new HashMap<>();
        StringBuilder elementb = new StringBuilder();
        for (EnMember member : selfObjEncoder.getMembers()) {
            final String fieldName = member.getFieldName();
            final Class fieldClass = member.getAttribute().type();
            final Type fieldType = member.getAttribute().genericType();
            elementb.append(fieldName).append(',');
            if (!ProtobufFactory.isSimpleType(fieldClass)
                    && !fieldClass.isEnum()
                    && !factory.supportSimpleCollectionType(fieldType)) {
                if ((member.getEncoder() instanceof SimpledCoder)) {
                    simpledCoders.put(fieldName, (SimpledCoder) member.getEncoder());
                } else {
                    otherMembers.put(fieldName, member);
                }
            }
        }

        RedkaleClassLoader classLoader = RedkaleClassLoader.currentClassLoader();
        final String newDynName = "org/redkaledyn/convert/pb/_Dyn" + ProtobufDynEncoder.class.getSimpleName() + "__"
                + clazz.getName().replace('.', '_').replace('$', '_') + "_" + factory.getFeatures() + "_"
                + Utility.md5Hex(elementb.toString()); // tiny必须要加上, 同一个类会有多个字段定制Convert
        try {
            Class newClazz = classLoader.loadClass(newDynName.replace('/', '.'));
            ProtobufDynEncoder resultEncoder = (ProtobufDynEncoder)
                    newClazz.getConstructor(ProtobufFactory.class, Type.class, ProtobufObjectEncoder.class)
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
        final String pbencoderDesc = org.redkale.asm.Type.getDescriptor(ProtobufObjectEncoder.class);
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
                    ACC_PUBLIC, "<init>", "(" + pbfactoryDesc + typeDesc + pbencoderDesc + ")V", null, null));
            Label label0 = new Label();
            mv.visitLabel(label0);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitVarInsn(ALOAD, 2);
            mv.visitVarInsn(ALOAD, 3);
            mv.visitMethodInsn(
                    INVOKESPECIAL, supDynName, "<init>", "(" + pbfactoryDesc + typeDesc + pbencoderDesc + ")V", false);
            mv.visitInsn(RETURN);
            Label label2 = new Label();
            mv.visitLabel(label2);
            mv.visitLocalVariable("this", "L" + newDynName + ";", null, label0, label2, 0);
            mv.visitLocalVariable("factory", pbfactoryDesc, null, label0, label2, 1);
            mv.visitLocalVariable("type", typeDesc, null, label0, label2, 2);
            mv.visitLocalVariable("objectEncoder", pbencoderDesc, null, label0, label2, 3);
            mv.visitMaxs(4, 4);
            mv.visitEnd();
        }
        { // convertTo 方法
            mv = (cw.visitMethod(
                    ACC_PUBLIC, "convertTo", "(" + pbwriterDesc + enMemberDesc + valtypeDesc + ")V", null, null));
            Label label0 = new Label();
            mv.visitLabel(label0);
            // if (value == null) return;
            mv.visitVarInsn(ALOAD, 3); // value
            Label ifLabel = new Label();
            mv.visitJumpInsn(IFNONNULL, ifLabel);
            mv.visitInsn(RETURN);
            mv.visitLabel(ifLabel);
            mv.visitLineNumber(33, ifLabel);
            mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);

            // ProtobufWriter out = acceptWriter(out0, member, value);
            mv.visitVarInsn(ALOAD, 0); // this
            mv.visitVarInsn(ALOAD, 1); // out0
            mv.visitVarInsn(ALOAD, 2); // member
            mv.visitVarInsn(ALOAD, 3); // value
            mv.visitMethodInsn(
                    INVOKEVIRTUAL,
                    newDynName,
                    "acceptWriter",
                    "(" + pbwriterDesc + enMemberDesc + objectDesc + ")" + pbwriterDesc,
                    false);
            mv.visitVarInsn(ASTORE, 4);
            Label sublabel = new Label();
            mv.visitLabel(sublabel);

            mv.visitVarInsn(ALOAD, 4);
            mv.visitVarInsn(ALOAD, 3);
            mv.visitMethodInsn(INVOKEVIRTUAL, pbwriterName, "writeObjectB", "(Ljava/lang/Object;)V", false);

            for (EnMember member : selfObjEncoder.getMembers()) {
                final String fieldName = member.getFieldName();
                final Type fieldType = member.getAttribute().genericType();
                final Class fieldClass = member.getAttribute().type();
                if (ProtobufFactory.isSimpleType(fieldClass)) {
                    mv.visitVarInsn(ALOAD, 4); // out
                    Asms.visitInsn(mv, member.getTag()); // tag
                    mv.visitVarInsn(ALOAD, 3); // value
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
                    mv.visitVarInsn(ALOAD, 4); // out
                    Asms.visitInsn(mv, member.getTag()); // tag
                    mv.visitVarInsn(ALOAD, 3); // value
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
                } else if (factory.supportSimpleCollectionType(fieldType)) {
                    mv.visitVarInsn(ALOAD, 4); // out
                    Asms.visitInsn(mv, member.getTag()); // tag
                    mv.visitVarInsn(ALOAD, 3); // value
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
                    Class componentType = factory.getSimpleCollectionComponentType(fieldType);
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
                        wmethodName = "writeFieldStringsValue";
                    }
                    mv.visitMethodInsn(INVOKEVIRTUAL, pbwriterName, wmethodName, "(ILjava/util/Collection;)V", false);
                } else if (simpledCoders.containsKey(fieldName)) {
                    mv.visitVarInsn(ALOAD, 4); // out
                    Asms.visitInsn(mv, member.getTag()); // tag
                    mv.visitVarInsn(ALOAD, 0); // this
                    mv.visitFieldInsn(GETFIELD, newDynName, fieldName + "SimpledCoder", simpledCoderDesc);
                    mv.visitVarInsn(ALOAD, 3); // value
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
                    mv.visitVarInsn(ALOAD, 4); // out
                    mv.visitVarInsn(ALOAD, 0); // this
                    mv.visitFieldInsn(GETFIELD, newDynName, fieldName + "EnMember", enMemberDesc);
                    mv.visitVarInsn(ALOAD, 3); // value
                    mv.visitMethodInsn(
                            INVOKEVIRTUAL,
                            pbwriterName,
                            "writeFieldValue",
                            "(" + enMemberDesc + objectDesc + ")V",
                            false);
                }
            }
            // out.writeObjectE(value);
            mv.visitVarInsn(ALOAD, 4); // out
            mv.visitVarInsn(ALOAD, 3); // value
            mv.visitMethodInsn(INVOKEVIRTUAL, pbwriterName, "writeObjectE", "(Ljava/lang/Object;)V", false);
            // offerWriter(out0, out);
            mv.visitVarInsn(ALOAD, 0); // this
            mv.visitVarInsn(ALOAD, 1); // out0
            mv.visitVarInsn(ALOAD, 4); // out
            mv.visitMethodInsn(
                    INVOKEVIRTUAL, newDynName, "offerWriter", "(" + pbwriterDesc + pbwriterDesc + ")V", false);

            mv.visitInsn(RETURN);
            Label label2 = new Label();
            mv.visitLabel(label2);
            mv.visitLocalVariable("this", "L" + newDynName + ";", null, label0, label2, 0);
            mv.visitLocalVariable("out", pbwriterDesc, null, label0, label2, 1);
            mv.visitLocalVariable("parentMember", enMemberDesc, null, label0, label2, 2);
            mv.visitLocalVariable("value", valtypeDesc, null, label0, label2, 3);
            mv.visitLocalVariable("subout", pbwriterDesc, null, sublabel, label2, 4);
            mv.visitMaxs(4, 3);
            mv.visitEnd();
        }
        { // convertTo 虚拟方法
            mv = (cw.visitMethod(
                    ACC_PUBLIC + ACC_BRIDGE + ACC_SYNTHETIC,
                    "convertTo",
                    "(" + pbwriterDesc + enMemberDesc + "Ljava/lang/Object;)V",
                    null,
                    null));
            // mv.setDebug(true);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitVarInsn(ALOAD, 2);
            mv.visitVarInsn(ALOAD, 3);
            mv.visitTypeInsn(CHECKCAST, valtypeName);
            mv.visitMethodInsn(
                    INVOKEVIRTUAL,
                    newDynName,
                    "convertTo",
                    "(" + pbwriterDesc + enMemberDesc + valtypeDesc + ")V",
                    false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(3, 3);
            mv.visitEnd();
        }
        cw.visitEnd();

        byte[] bytes = cw.toByteArray();
        Class<ProtobufDynEncoder> newClazz = classLoader.loadClass(newDynName.replace('/', '.'), bytes);
        RedkaleClassLoader.putReflectionDeclaredConstructors(newClazz, newDynName.replace('/', '.'));
        try {
            ProtobufDynEncoder resultEncoder = (ProtobufDynEncoder)
                    newClazz.getConstructor(ProtobufFactory.class, Type.class, ProtobufObjectEncoder.class)
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
        return generateDyncEncoder(factory, (Class) type);
    }

    @Override
    public Type getType() {
        return typeClass;
    }
}
