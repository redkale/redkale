/*
 * Copyright (c) 2016-2116 Redkale
 */
package org.redkale.bytecode;

import static java.lang.classfile.ClassFile.*;

import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.lang.constant.*;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.redkale.annotation.Nullable;
import org.redkale.inject.ResourceFactory;
import org.redkale.util.RedkaleClassLoader;

/** Dynamic method extensions backed by the JDK Classfile API. */
public abstract class CodeMethodBoost<T> {
    protected final AtomicInteger fieldIndex = new AtomicInteger();
    protected final boolean remote;
    protected final Class serviceType;

    protected CodeMethodBoost(boolean remote, Class serviceType) {
        this.remote = remote;
        this.serviceType = serviceType;
    }

    public static CodeMethodBoost create(boolean remote, Collection<CodeMethodBoost> list) {
        return new CodeMethodBoosts(remote, list);
    }

    public static CodeMethodBoost create(boolean remote, CodeMethodBoost... items) {
        return new CodeMethodBoosts(remote, items);
    }

    public static Map<String, CodeMethodBean> getMethodBeans(Class clazz) {
        Map<String, CodeMethodBean> result = new HashMap<>();
        for (Class current = clazz; current != null && current != Object.class; current = current.getSuperclass()) {
            String name = current.getName();
            byte[] bytes = RedkaleClassLoader.getDynClassBytes(name);
            if (bytes == null) {
                try (InputStream in =
                        current.getResourceAsStream(name.substring(name.lastIndexOf('.') + 1) + ".class")) {
                    if (in == null) continue;
                    bytes = in.readAllBytes();
                } catch (Exception e) {
                    throw new org.redkale.util.RedkaleException(e);
                }
            }
            ClassModel model = ClassFile.of().parse(bytes);
            for (MethodModel method : model.methods()) {
                if (method.flags().has(java.lang.reflect.AccessFlag.STATIC)) continue;
                String methodName = method.methodName().stringValue();
                String desc = method.methodType().stringValue();
                String key = methodName + ":" + desc;
                if (result.containsKey(key)) continue;
                String signature = method.findAttribute(Attributes.signature())
                        .map(a -> a.signature().stringValue())
                        .orElse(null);
                String[] exceptions = method.findAttribute(Attributes.exceptions())
                        .map(a -> a.exceptions().stream()
                                .map(e -> e.asInternalName())
                                .toArray(String[]::new))
                        .orElse(null);
                CodeMethodBean bean =
                        new CodeMethodBean(method.flags().flagsMask(), methodName, desc, signature, exceptions);
                List<MethodParameterInfo> parameters = method.findAttribute(Attributes.methodParameters())
                        .map(MethodParametersAttribute::parameters)
                        .orElse(List.of());
                List<LocalVariableInfo> locals = method.code()
                        .flatMap(c -> c.findAttribute(Attributes.localVariableTable()))
                        .map(LocalVariableTableAttribute::localVariables)
                        .orElse(List.of());
                List<LocalVariableTypeInfo> genericLocals = method.code()
                        .flatMap(c -> c.findAttribute(Attributes.localVariableTypeTable()))
                        .map(LocalVariableTypeTableAttribute::localVariableTypes)
                        .orElse(List.of());
                MethodTypeDesc methodType = method.methodTypeSymbol();
                int slot = 1;
                for (int i = 0; i < methodType.parameterCount(); i++) {
                    String paramName = i < parameters.size()
                            ? parameters.get(i).name().map(n -> n.stringValue()).orElse(null)
                            : null;
                    String paramSignature = null;
                    for (LocalVariableInfo local : locals) {
                        if (local.slot() == slot && local.startPc() == 0) {
                            paramName = local.name().stringValue();
                            break;
                        }
                    }
                    for (LocalVariableTypeInfo local : genericLocals) {
                        if (local.slot() == slot && local.startPc() == 0) {
                            paramSignature = local.signature().stringValue();
                            break;
                        }
                    }
                    ClassDesc paramType = methodType.parameterType(i);
                    bean.getParams()
                            .add(new CodeMethodParam(
                                    paramName == null ? "arg" + i : paramName,
                                    paramType.descriptorString(),
                                    paramSignature));
                    slot += TypeKind.from(paramType).slotSize();
                }
                result.put(key, bean);
            }
        }
        return result;
    }

    public static String getMethodBeanKey(Method method) {
        return method.getName() + ":" + ByteCodes.methodDescriptor(method);
    }

    public abstract List<Class<? extends Annotation>> filterMethodAnnotations(Method method);

    public abstract CodeNewMethod doMethod(
            RedkaleClassLoader classLoader,
            ClassBuilder cw,
            Class serviceImplClass,
            String newDynName,
            String fieldPrefix,
            List<Class<? extends Annotation>> filterAnns,
            Method method,
            @Nullable CodeNewMethod newMethod);

    public void doAfterMethods(
            RedkaleClassLoader classLoader,
            ClassBuilder cw,
            String newDynName,
            String fieldPrefix,
            List<java.lang.classfile.Annotation> cwAnnotations) {}

    public void doConstructorMethod(
            RedkaleClassLoader classLoader,
            ClassBuilder cw,
            CodeBuilder mv,
            String newDynName,
            String fieldPrefix,
            boolean remote) {}

    public abstract void doInstance(RedkaleClassLoader classLoader, ResourceFactory resourceFactory, T service);

    protected CodeMethodBean getMethodBean(Method method) {
        return CodeMethodBean.get(getMethodBeans(serviceType), method);
    }

    protected void createMethod(
            ClassBuilder cw,
            Method method,
            CodeNewMethod newMethod,
            CodeMethodBean methodBean,
            Consumer<MethodBuilder> builder) {
        cw.withMethod(
                getNowMethodName(method, newMethod),
                MethodTypeDesc.ofDescriptor(ByteCodes.methodDescriptor(method)),
                getAcc(method, newMethod),
                mb -> {
                    String signature = getMethodSignature(method, methodBean);
                    if (signature != null) mb.with(SignatureAttribute.of(MethodSignature.parseFrom(signature)));
                    String[] exceptions = getMethodExceptions(method, methodBean);
                    if (exceptions != null)
                        mb.with(ExceptionsAttribute.ofSymbols(Arrays.stream(exceptions)
                                .map(ByteCodes::classDesc)
                                .toList()));
                    builder.accept(mb);
                });
    }

    protected final int getAcc(Method method, CodeNewMethod newMethod) {
        return newMethod != null
                ? ACC_PRIVATE
                : Modifier.isProtected(method.getModifiers()) ? ACC_PROTECTED : ACC_PUBLIC;
    }

    protected final String getNowMethodName(Method method, CodeNewMethod newMethod) {
        return newMethod == null ? method.getName() : newMethod.getMethodName();
    }

    protected String getMethodSignature(Method method, CodeMethodBean methodBean) {
        return methodBean == null ? null : methodBean.getSignature();
    }

    protected String[] getMethodExceptions(Method method, CodeMethodBean methodBean) {
        return methodBean == null
                ? Arrays.stream(method.getExceptionTypes())
                        .map(ByteCodes::internalName)
                        .toArray(String[]::new)
                : methodBean.getExceptions();
    }

    protected void visitRawAnnotation(
            Method method,
            CodeNewMethod newMethod,
            MethodBuilder mb,
            Class skipAnnType,
            List skipAnns,
            List<java.lang.classfile.Annotation> annotations) {
        if (newMethod == null) {
            for (Annotation ann : method.getAnnotations()) {
                if (ann.annotationType() != skipAnnType
                        && (skipAnns == null || !skipAnns.contains(ann.annotationType())))
                    annotations.add(ByteCodes.annotation(ann.annotationType(), ann));
            }
            ByteCodes.parameterAnnotations(mb, method);
        }
    }

    protected List<Integer> visitVarInsnParamTypes(CodeBuilder code, Method method, int previousSlot) {
        List<Integer> slots = new ArrayList<>();
        int slot = previousSlot + 1;
        for (Class<?> type : method.getParameterTypes()) {
            TypeKind kind = TypeKind.from(type);
            slots.add(slot);
            code.loadLocal(kind, slot);
            slot += kind.slotSize();
        }
        return slots;
    }

    protected void visitParamTypesLocalVariable(
            CodeBuilder code, Method method, Label start, Label end, List<Integer> slots, CodeMethodBean methodBean) {
        code.labelBinding(end);
        if (methodBean == null) return;
        for (int i = 0; i < method.getParameterCount(); i++) {
            CodeMethodParam param = methodBean.getParams().get(i);
            code.localVariable(
                    slots.get(i), param.getName(), ByteCodes.constantType(method.getParameterTypes()[i]), start, end);
            String signature = param.signature(method.getParameterTypes()[i]);
            if (signature != null)
                code.localVariableType(slots.get(i), param.getName(), Signature.parseFrom(signature), start, end);
        }
    }

    protected void visitInsnReturn(
            CodeBuilder code, Method method, Label start, List<Integer> slots, CodeMethodBean bean) {
        code.return_(TypeKind.from(method.getReturnType()));
        visitParamTypesLocalVariable(code, method, start, code.newLabel(), slots, bean);
    }

    static class CodeMethodBoosts<T> extends CodeMethodBoost<T> {

        private final CodeMethodBoost[] items;

        public CodeMethodBoosts(boolean remote, Collection<CodeMethodBoost> list) {
            super(remote, null);
            this.items = list.toArray(new CodeMethodBoost[list.size()]);
        }

        public CodeMethodBoosts(boolean remote, CodeMethodBoost... items) {
            super(remote, null);
            this.items = items;
        }

        @Override
        public List<Class<? extends Annotation>> filterMethodAnnotations(Method method) {
            List<Class<? extends Annotation>> list = null;
            for (CodeMethodBoost item : items) {
                if (item != null) {
                    List<Class<? extends Annotation>> sub = item.filterMethodAnnotations(method);
                    if (sub != null) {
                        if (list == null) {
                            list = new ArrayList<>();
                        }
                        list.addAll(sub);
                    }
                }
            }
            return list;
        }

        @Override
        public CodeNewMethod doMethod(
                RedkaleClassLoader classLoader,
                ClassBuilder cw,
                Class serviceImplClass,
                String newDynName,
                String fieldPrefix,
                List<Class<? extends Annotation>> filterAnns,
                Method method,
                CodeNewMethod newMethod) {
            CodeNewMethod newResult = newMethod;
            for (CodeMethodBoost item : items) {
                if (item != null) {
                    newResult = item.doMethod(
                            classLoader, cw, serviceImplClass, newDynName, fieldPrefix, filterAnns, method, newResult);
                }
            }
            return newResult;
        }

        @Override
        public void doAfterMethods(
                RedkaleClassLoader classLoader,
                ClassBuilder cw,
                String newDynName,
                String fieldPrefix,
                List<java.lang.classfile.Annotation> cwAnnotations) {
            for (CodeMethodBoost item : items) {
                if (item != null) {
                    item.doAfterMethods(classLoader, cw, newDynName, fieldPrefix, cwAnnotations);
                }
            }
        }

        @Override
        public void doConstructorMethod(
                RedkaleClassLoader classLoader,
                ClassBuilder cw,
                CodeBuilder mv,
                String newDynName,
                String fieldPrefix,
                boolean remote) {
            for (CodeMethodBoost item : items) {
                if (item != null) {
                    item.doConstructorMethod(classLoader, cw, mv, newDynName, fieldPrefix, remote);
                }
            }
        }

        @Override
        public void doInstance(RedkaleClassLoader classLoader, ResourceFactory resourceFactory, T service) {
            for (CodeMethodBoost item : items) {
                if (item != null) {
                    item.doInstance(classLoader, resourceFactory, service);
                }
            }
        }
    }
}
