/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.convert.pb;

import static java.lang.classfile.ClassFile.*;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassSignature;
import java.lang.classfile.Label;
import java.lang.classfile.attribute.*;
import java.lang.constant.*;
import java.lang.reflect.*;
import java.lang.reflect.Type;
import java.util.*;
import org.redkale.bytecode.ByteCodes;
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

    protected final ObjectEncoder<ProtobufWriter, T> objectEncoderSelf;

    // 动态字段: protected SimpledCoder xxxSimpledCoder;

    // 动态字段: protected EnMember xxxEnMember;

    protected ProtobufDynEncoder(ProtobufFactory factory, Type type, ProtobufObjectEncoder objectEncoderSelf) {
        super((Class) type);
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
        final String typeDesc = ByteCodes.descriptor(Type.class);
        final String pbfactoryDesc = ByteCodes.descriptor(ProtobufFactory.class);
        final String pbwriterDesc = ByteCodes.descriptor(ProtobufWriter.class);
        final String simpledCoderDesc = ByteCodes.descriptor(SimpledCoder.class);
        final String enMemberDesc = ByteCodes.descriptor(EnMember.class);
        final String pbencoderDesc = ByteCodes.descriptor(ProtobufObjectEncoder.class);
        final String objectDesc = ByteCodes.descriptor(Object.class);
        final String valtypeDesc = ByteCodes.descriptor(clazz);
        // ------------------------------------------------------------------------------

        byte[] classBytes = ClassFile.of().build(ByteCodes.classDesc(newDynName), cw -> {
            cw.withVersion(JAVA_11_VERSION, 0)
                    .withFlags(ACC_PUBLIC + ACC_FINAL + ACC_SUPER)
                    .withSuperclass(ByteCodes.classDesc(supDynName));

            cw.with(SignatureAttribute.of(ClassSignature.parseFrom("L" + supDynName + "<" + valtypeDesc + ">;")));
            if (!simpledCoders.isEmpty()) {
                for (String key : simpledCoders.keySet()) {
                    cw.withField(key + "SimpledCoder", ClassDesc.ofDescriptor(simpledCoderDesc), ACC_PROTECTED);
                }
            }
            if (!otherMembers.isEmpty()) {
                for (String key : otherMembers.keySet()) {
                    cw.withField(key + "EnMember", ClassDesc.ofDescriptor(enMemberDesc), ACC_PROTECTED);
                }
            }
            { // 构造函数
                cw.withMethodBody(
                        "<init>",
                        MethodTypeDesc.ofDescriptor("(" + pbfactoryDesc + typeDesc + pbencoderDesc + ")V"),
                        ACC_PUBLIC,
                        mv -> {
                            Label label0 = mv.newLabel();
                            mv.labelBinding(label0);
                            mv.aload(0);
                            mv.aload(1);
                            mv.aload(2);
                            mv.aload(3);
                            mv.invokespecial(
                                    ByteCodes.classDesc(supDynName),
                                    "<init>",
                                    MethodTypeDesc.ofDescriptor("(" + pbfactoryDesc + typeDesc + pbencoderDesc + ")V"),
                                    false);
                            mv.return_();
                            Label label2 = mv.newLabel();
                            mv.labelBinding(label2);
                            mv.localVariable(0, "this", ClassDesc.ofDescriptor("L" + newDynName + ";"), label0, label2);
                            mv.localVariable(1, "factory", ClassDesc.ofDescriptor(pbfactoryDesc), label0, label2);
                            mv.localVariable(2, "type", ClassDesc.ofDescriptor(typeDesc), label0, label2);
                            mv.localVariable(3, "objectEncoder", ClassDesc.ofDescriptor(pbencoderDesc), label0, label2);
                        });
            }
            { // convertTo 方法
                cw.withMethodBody(
                        "convertTo",
                        MethodTypeDesc.ofDescriptor("(" + pbwriterDesc + enMemberDesc + valtypeDesc + ")V"),
                        ACC_PUBLIC,
                        mv -> {
                            Label label0 = mv.newLabel();
                            mv.labelBinding(label0);
                            // if (value == null) return;
                            mv.aload(3); // value
                            Label ifLabel = mv.newLabel();
                            mv.ifnonnull(ifLabel);
                            mv.return_();
                            mv.labelBinding(ifLabel);
                            mv.lineNumber(33);

                            // ProtobufWriter out = acceptWriter(out0, member, value);
                            mv.aload(0); // this
                            mv.aload(1); // out0
                            mv.aload(2); // member
                            mv.aload(3); // value
                            mv.invokevirtual(
                                    ByteCodes.classDesc(newDynName),
                                    "acceptWriter",
                                    MethodTypeDesc.ofDescriptor(
                                            "(" + pbwriterDesc + enMemberDesc + objectDesc + ")" + pbwriterDesc));
                            mv.astore(4);
                            Label sublabel = mv.newLabel();
                            mv.labelBinding(sublabel);

                            mv.aload(4);
                            mv.aload(3);
                            mv.invokevirtual(
                                    ByteCodes.classDesc(pbwriterName),
                                    "writeObjectB",
                                    MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;)V"));

                            for (EnMember member : selfObjEncoder.getMembers()) {
                                final String fieldName = member.getFieldName();
                                final Type fieldType = member.getAttribute().genericType();
                                final Class fieldClass = member.getAttribute().type();
                                if (ProtobufFactory.isSimpleType(fieldClass)) {
                                    mv.aload(4); // out
                                    mv.loadConstant(member.getTag()); // tag
                                    mv.aload(3); // value
                                    String realDesc;
                                    if (member.getMethod() != null) {
                                        String mname = member.getMethod().getName();
                                        realDesc = ByteCodes.descriptor(
                                                member.getMethod().getReturnType());
                                        String mdesc = ByteCodes.methodDescriptor(member.getMethod());
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(valtypeName),
                                                mname,
                                                MethodTypeDesc.ofDescriptor(mdesc));
                                    } else { // field
                                        Field field = member.getField();
                                        String fname = field.getName();
                                        realDesc = ByteCodes.descriptor(field.getType());
                                        mv.getfield(
                                                ByteCodes.classDesc(valtypeName),
                                                fname,
                                                ClassDesc.ofDescriptor(realDesc));
                                    }
                                    String fieldDesc = ByteCodes.descriptor(fieldClass);
                                    if (!Objects.equals(realDesc, fieldDesc)) { // 父类方法参数类型时泛型
                                        mv.checkcast(ByteCodes.classDesc(
                                                fieldClass.getName().replace('.', '/')));
                                    }
                                    mv.invokevirtual(
                                            ByteCodes.classDesc(pbwriterName),
                                            "writeFieldValue",
                                            MethodTypeDesc.ofDescriptor("(I" + fieldDesc + ")V"));
                                } else if (fieldClass.isEnum()) {
                                    mv.aload(4); // out
                                    mv.loadConstant(member.getTag()); // tag
                                    mv.aload(3); // value
                                    String realDesc;
                                    if (member.getMethod() != null) {
                                        String mname = member.getMethod().getName();
                                        realDesc = ByteCodes.descriptor(
                                                member.getMethod().getReturnType());
                                        String mdesc = ByteCodes.methodDescriptor(member.getMethod());
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(valtypeName),
                                                mname,
                                                MethodTypeDesc.ofDescriptor(mdesc));
                                    } else { // field
                                        Field field = member.getField();
                                        String fname = field.getName();
                                        realDesc = ByteCodes.descriptor(field.getType());
                                        mv.getfield(
                                                ByteCodes.classDesc(valtypeName),
                                                fname,
                                                ClassDesc.ofDescriptor(realDesc));
                                    }
                                    if (!Objects.equals(realDesc, "Ljava/lang/Enum;")) {
                                        mv.checkcast(ByteCodes.classDesc("java/lang/Enum"));
                                    }
                                    mv.invokevirtual(
                                            ByteCodes.classDesc(pbwriterName),
                                            "writeFieldValue",
                                            MethodTypeDesc.ofDescriptor("(ILjava/lang/Enum;)V"));
                                } else if (factory.supportSimpleCollectionType(fieldType)) {
                                    mv.aload(4); // out
                                    mv.loadConstant(member.getTag()); // tag
                                    mv.aload(3); // value
                                    if (member.getMethod() != null) {
                                        String mname = member.getMethod().getName();
                                        String mdesc = ByteCodes.methodDescriptor(member.getMethod());
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(valtypeName),
                                                mname,
                                                MethodTypeDesc.ofDescriptor(mdesc));
                                    } else { // field
                                        Field field = member.getField();
                                        String fname = field.getName();
                                        String fdesc = ByteCodes.descriptor(field.getType());
                                        mv.getfield(
                                                ByteCodes.classDesc(valtypeName), fname, ClassDesc.ofDescriptor(fdesc));
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
                                    mv.invokevirtual(
                                            ByteCodes.classDesc(pbwriterName),
                                            wmethodName,
                                            MethodTypeDesc.ofDescriptor("(ILjava/util/Collection;)V"));
                                } else if (simpledCoders.containsKey(fieldName)) {
                                    mv.aload(4); // out
                                    mv.loadConstant(member.getTag()); // tag
                                    mv.aload(0); // this
                                    mv.getfield(
                                            ByteCodes.classDesc(newDynName),
                                            fieldName + "SimpledCoder",
                                            ClassDesc.ofDescriptor(simpledCoderDesc));
                                    mv.aload(3); // value
                                    if (member.getMethod() != null) {
                                        String mname = member.getMethod().getName();
                                        String mdesc = ByteCodes.methodDescriptor(member.getMethod());
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(valtypeName),
                                                mname,
                                                MethodTypeDesc.ofDescriptor(mdesc));
                                    } else { // field
                                        Field field = member.getField();
                                        String fname = field.getName();
                                        String fdesc = ByteCodes.descriptor(field.getType());
                                        mv.getfield(
                                                ByteCodes.classDesc(valtypeName), fname, ClassDesc.ofDescriptor(fdesc));
                                    }
                                    mv.invokevirtual(
                                            ByteCodes.classDesc(pbwriterName),
                                            "writeFieldValue",
                                            MethodTypeDesc.ofDescriptor("(I" + simpledCoderDesc + objectDesc + ")V"));
                                } else {
                                    mv.aload(4); // out
                                    mv.aload(0); // this
                                    mv.getfield(
                                            ByteCodes.classDesc(newDynName),
                                            fieldName + "EnMember",
                                            ClassDesc.ofDescriptor(enMemberDesc));
                                    mv.aload(3); // value
                                    mv.invokevirtual(
                                            ByteCodes.classDesc(pbwriterName),
                                            "writeFieldValue",
                                            MethodTypeDesc.ofDescriptor("(" + enMemberDesc + objectDesc + ")V"));
                                }
                            }
                            // out.writeObjectE(value);
                            mv.aload(4); // out
                            mv.aload(3); // value
                            mv.invokevirtual(
                                    ByteCodes.classDesc(pbwriterName),
                                    "writeObjectE",
                                    MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;)V"));
                            // offerWriter(out0, out);
                            mv.aload(0); // this
                            mv.aload(1); // out0
                            mv.aload(4); // out
                            mv.invokevirtual(
                                    ByteCodes.classDesc(newDynName),
                                    "offerWriter",
                                    MethodTypeDesc.ofDescriptor("(" + pbwriterDesc + pbwriterDesc + ")V"));

                            mv.return_();
                            Label label2 = mv.newLabel();
                            mv.labelBinding(label2);
                            mv.localVariable(0, "this", ClassDesc.ofDescriptor("L" + newDynName + ";"), label0, label2);
                            mv.localVariable(1, "out", ClassDesc.ofDescriptor(pbwriterDesc), label0, label2);
                            mv.localVariable(2, "parentMember", ClassDesc.ofDescriptor(enMemberDesc), label0, label2);
                            mv.localVariable(3, "value", ClassDesc.ofDescriptor(valtypeDesc), label0, label2);
                            mv.localVariable(4, "subout", ClassDesc.ofDescriptor(pbwriterDesc), sublabel, label2);
                        });
            }
            { // convertTo 虚拟方法
                cw.withMethodBody(
                        "convertTo",
                        MethodTypeDesc.ofDescriptor("(" + pbwriterDesc + enMemberDesc + "Ljava/lang/Object;)V"),
                        ACC_PUBLIC + ACC_BRIDGE + ACC_SYNTHETIC,
                        mv -> {
                            mv.aload(0);
                            mv.aload(1);
                            mv.aload(2);
                            mv.aload(3);
                            mv.checkcast(ByteCodes.classDesc(valtypeName));
                            mv.invokevirtual(
                                    ByteCodes.classDesc(newDynName),
                                    "convertTo",
                                    MethodTypeDesc.ofDescriptor(
                                            "(" + pbwriterDesc + enMemberDesc + valtypeDesc + ")V"));
                            mv.return_();
                        });
            }
        });

        byte[] bytes = classBytes;
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
