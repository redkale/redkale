/*
 * Copyright (c) 2016-2116 Redkale
 */
package org.redkale.bytecode;

import static java.lang.constant.ConstantDescs.*;

import java.lang.annotation.Annotation;
import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.lang.constant.*;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.*;
import org.redkale.util.RedkaleException;
import org.redkale.util.TypeToken;

/** Classfile utilities shared by dynamic code generators. */
public final class ByteCodes {
    private ByteCodes() {}

    public static String descriptor(Class<?> type) {
        return type.descriptorString();
    }

    public static String internalName(Class<?> type) {
        return type.getName().replace('.', '/');
    }

    public static String methodDescriptor(Method method) {
        return MethodType.methodType(method.getReturnType(), method.getParameterTypes())
                .descriptorString();
    }

    public static ClassDesc classDesc(String name) {
        return name.startsWith("[") ? ClassDesc.ofDescriptor(name) : ClassDesc.ofInternalName(name.replace('.', '/'));
    }

    public static ConstantDesc constantType(String descriptor) {
        return descriptor.startsWith("(")
                ? MethodTypeDesc.ofDescriptor(descriptor)
                : ClassDesc.ofDescriptor(descriptor);
    }

    public static ClassDesc constantType(Class<?> type) {
        return ClassDesc.ofDescriptor(type.descriptorString());
    }

    public static AnnotationValue annotationValue(Object value) {
        if (value instanceof Class<?> type) return AnnotationValue.ofClass(constantType(type));
        if (value instanceof Enum<?> item)
            return AnnotationValue.ofEnum(constantType(item.getDeclaringClass()), item.name());
        if (value instanceof Annotation annotation)
            return AnnotationValue.ofAnnotation(annotation(annotation.annotationType(), annotation));
        if (value.getClass().isArray()) {
            List<AnnotationValue> values = new ArrayList<>();
            for (int i = 0; i < Array.getLength(value); i++) values.add(annotationValue(Array.get(value, i)));
            return AnnotationValue.ofArray(values);
        }
        return AnnotationValue.of(value);
    }

    public static java.lang.classfile.Annotation annotation(Class<? extends Annotation> type, Annotation value) {
        List<AnnotationElement> elements = new ArrayList<>();
        try {
            for (Method method : value.annotationType().getDeclaredMethods()) {
                try {
                    type.getDeclaredMethod(method.getName());
                } catch (NoSuchMethodException e) {
                    continue;
                }
                elements.add(AnnotationElement.of(method.getName(), annotationValue(method.invoke(value))));
            }
        } catch (ReflectiveOperationException e) {
            throw new RedkaleException(e);
        }
        return java.lang.classfile.Annotation.of(constantType(type), elements);
    }

    public static void parameterAnnotations(MethodBuilder builder, Method method) {
        List<List<java.lang.classfile.Annotation>> params = new ArrayList<>();
        for (Annotation[] annotations : method.getParameterAnnotations()) {
            List<java.lang.classfile.Annotation> values = new ArrayList<>();
            for (Annotation annotation : annotations) values.add(annotation(annotation.annotationType(), annotation));
            params.add(values);
        }
        if (params.stream().anyMatch(v -> !v.isEmpty()))
            builder.with(RuntimeVisibleParameterAnnotationsAttribute.of(params));
    }

    public static void visitCheckCast(CodeBuilder code, Class<?> type) {
        if (type.isPrimitive()) {
            ClassDesc wrapper = constantType(TypeToken.primitiveToWrapper(type));
            code.checkcast(wrapper)
                    .invokevirtual(wrapper, type.getSimpleName() + "Value", MethodTypeDesc.of(constantType(type)));
        } else code.checkcast(constantType(type));
    }

    public static void visitPrimitiveValueOf(CodeBuilder code, Class<?> type) {
        if (type.isPrimitive()) {
            ClassDesc wrapper = constantType(TypeToken.primitiveToWrapper(type));
            code.invokestatic(wrapper, "valueOf", MethodTypeDesc.of(wrapper, constantType(type)));
        }
    }

    public static void visitFieldInsn(CodeBuilder code, Class<?> type) {
        if (type.isPrimitive()) code.getstatic(constantType(TypeToken.primitiveToWrapper(type)), "TYPE", CD_Class);
        else code.loadConstant(constantType(type));
    }

    public static void visitPrimitiveVirtual(CodeBuilder code, Class<?> type) {
        if (type.isPrimitive())
            code.invokevirtual(
                    constantType(TypeToken.primitiveToWrapper(type)),
                    type.getSimpleName() + "Value",
                    MethodTypeDesc.of(constantType(type)));
    }
}
