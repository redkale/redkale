/*
 *
 */
package org.redkale.cached.spi;

import static java.lang.classfile.ClassFile.*;

import java.lang.annotation.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.*;
import java.lang.constant.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.redkale.bytecode.ByteCodes;
import org.redkale.bytecode.CodeMethodBean;
import org.redkale.bytecode.CodeMethodBoost;
import org.redkale.bytecode.CodeNewMethod;
import org.redkale.cached.Cached;
import org.redkale.inject.ResourceFactory;
import org.redkale.service.LoadMode;
import org.redkale.util.RedkaleClassLoader;
import org.redkale.util.RedkaleException;
import org.redkale.util.ThrowSupplier;
import org.redkale.util.TypeToken;

/**
 * 动态字节码的方法扩展器
 *
 * @author zhangjx
 * @since 2.8.0
 */
public class CachedCodeMethodBoost extends CodeMethodBoost<Object> {

    static final java.lang.reflect.Type FUTURE_VOID = new TypeToken<CompletableFuture<Void>>() {}.getType();

    private static final List<Class<? extends Annotation>> FILTER_ANN = List.of(Cached.class, DynForCached.class);

    private final Logger logger = Logger.getLogger(getClass().getSimpleName());

    private Map<String, CachedAction> actionMap;

    public CachedCodeMethodBoost(boolean remote, Class serviceType) {
        super(remote, serviceType);
    }

    @Override
    public List<Class<? extends Annotation>> filterMethodAnnotations(Method method) {
        return FILTER_ANN;
    }

    @Override
    public CodeNewMethod doMethod(
            final RedkaleClassLoader classLoader,
            final ClassBuilder cw,
            final Class serviceImplClass,
            final String newDynName,
            final String fieldPrefix,
            final List filterAnns,
            final Method method,
            final CodeNewMethod newMethod) {
        Map<String, CachedAction> actions = this.actionMap;
        if (actions == null) {
            actions = new LinkedHashMap<>();
            this.actionMap = actions;
        }
        Cached cached = method.getAnnotation(Cached.class);
        if (cached == null) {
            return newMethod;
        }
        if (method.getAnnotation(DynForCached.class) != null) {
            return newMethod;
        }
        if (!LoadMode.matches(remote, cached.mode())) {
            return newMethod;
        }
        if (Modifier.isFinal(method.getModifiers()) || Modifier.isStatic(method.getModifiers())) {
            throw new RedkaleException(
                    "@" + Cached.class.getSimpleName() + " cannot on final or static method, but on " + method);
        }
        if (!Modifier.isProtected(method.getModifiers()) && !Modifier.isPublic(method.getModifiers())) {
            throw new RedkaleException(
                    "@" + Cached.class.getSimpleName() + " must on protected or public method, but on " + method);
        }
        if (method.getReturnType() == void.class || FUTURE_VOID.equals(method.getGenericReturnType())) {
            throw new RedkaleException("@" + Cached.class.getSimpleName() + " cannot on void method, but on " + method);
        }
        final int actionIndex = fieldIndex.incrementAndGet();
        final String rsMethodName = method.getName() + "_afterCached";
        final String dynFieldName =
                fieldPrefix + "_" + method.getName() + CachedAction.class.getSimpleName() + actionIndex;
        final CodeMethodBean methodBean = getMethodBean(method);
        final ClassDesc dynDesc = ByteCodes.classDesc(newDynName);
        final ClassDesc actionDesc = ByteCodes.constantType(CachedAction.class);
        final ClassDesc supplierDesc = ByteCodes.constantType(ThrowSupplier.class);
        final MethodTypeDesc methodDesc = MethodTypeDesc.ofDescriptor(ByteCodes.methodDescriptor(method));
        createMethod(cw, method, newMethod, methodBean, mb -> {
            List<java.lang.classfile.Annotation> annotations = new ArrayList<>();
            java.lang.classfile.Annotation cachedAnn = ByteCodes.annotation(DynForCached.class, cached);
            List<AnnotationElement> elements = new ArrayList<>(cachedAnn.elements());
            elements.add(AnnotationElement.ofString("dynField", dynFieldName));
            annotations.add(java.lang.classfile.Annotation.of(ByteCodes.constantType(DynForCached.class), elements));
            visitRawAnnotation(method, newMethod, mb, Cached.class, filterAnns, annotations);
            mb.with(RuntimeVisibleAnnotationsAttribute.of(annotations));
            mb.withCode(code -> {
                Label start = code.newLabel();
                code.labelBinding(start).aload(0);
                List<Integer> slots = visitVarInsnParamTypes(code, method, 0);
                MethodTypeDesc factoryType =
                        methodDesc.changeReturnType(supplierDesc).insertParameterTypes(0, dynDesc);
                DirectMethodHandleDesc bootstrap = MethodHandleDesc.ofMethod(
                        DirectMethodHandleDesc.Kind.STATIC,
                        ClassDesc.of("java.lang.invoke.LambdaMetafactory"),
                        "metafactory",
                        MethodTypeDesc.ofDescriptor(
                                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;"));
                code.invokedynamic(DynamicCallSiteDesc.of(
                        bootstrap,
                        "get",
                        factoryType,
                        MethodTypeDesc.of(ConstantDescs.CD_Object),
                        MethodHandleDesc.ofMethod(
                                DirectMethodHandleDesc.Kind.SPECIAL, dynDesc, "lambda$" + actionIndex, methodDesc),
                        MethodTypeDesc.of(methodDesc.returnType())));
                int supplierSlot = code.allocateLocal(TypeKind.REFERENCE);
                code.astore(supplierSlot)
                        .aload(0)
                        .getfield(dynDesc, dynFieldName, actionDesc)
                        .aload(supplierSlot)
                        .loadConstant(method.getParameterCount())
                        .anewarray(ConstantDescs.CD_Object);
                for (int i = 0; i < method.getParameterCount(); i++) {
                    Class<?> type = method.getParameterTypes()[i];
                    code.dup().loadConstant(i).loadLocal(TypeKind.from(type), slots.get(i));
                    ByteCodes.visitPrimitiveValueOf(code, type);
                    code.aastore();
                }
                code.invokevirtual(
                        actionDesc,
                        "get",
                        MethodTypeDesc.of(ConstantDescs.CD_Object, supplierDesc, ConstantDescs.CD_Object.arrayType()));
                ByteCodes.visitCheckCast(code, method.getReturnType());
                code.return_(TypeKind.from(method.getReturnType()));
                visitParamTypesLocalVariable(code, method, start, code.newLabel(), slots, methodBean);
            });
        });
        CachedAction action = new CachedAction(
                new CachedEntry(cached, method), method, serviceType, methodBean.paramNameArray(method), dynFieldName);
        actions.put(dynFieldName, action);
        cw.withMethod("lambda$" + actionIndex, methodDesc, ACC_PRIVATE | ACC_SYNTHETIC, mb -> {
            mb.with(ExceptionsAttribute.ofSymbols(ConstantDescs.CD_Throwable));
            mb.withCode(code -> {
                code.aload(0);
                visitVarInsnParamTypes(code, method, 0);
                code.invokespecial(dynDesc, rsMethodName, methodDesc).return_(TypeKind.from(method.getReturnType()));
            });
        });
        cw.withField(dynFieldName, actionDesc, ACC_PRIVATE);
        return new CodeNewMethod(rsMethodName, ACC_PRIVATE);
    }

    @Override
    public void doInstance(RedkaleClassLoader classLoader, ResourceFactory resourceFactory, Object service) {
        Class clazz = service.getClass();
        if (actionMap == null) { // 为null表示没有调用过doMethod， 动态类在编译是已经生成好了
            actionMap = new LinkedHashMap<>();
            Map<String, CodeMethodBean> methodBeans = CodeMethodBoost.getMethodBeans(clazz);
            for (final Method method : clazz.getDeclaredMethods()) {
                DynForCached cached = method.getAnnotation(DynForCached.class);
                if (cached != null) {
                    String dynFieldName = cached.dynField();
                    CodeMethodBean methodBean = CodeMethodBean.get(methodBeans, method);
                    CachedAction action = new CachedAction(
                            new CachedEntry(cached, method),
                            method,
                            serviceType,
                            methodBean.paramNameArray(method),
                            dynFieldName);
                    actionMap.put(dynFieldName, action);
                }
            }
        }

        actionMap.forEach((field, action) -> {
            try {
                resourceFactory.inject(action);
                final String tkey = action.init(resourceFactory, service);
                if (tkey.indexOf('@') < 0
                        && tkey.indexOf('{') < 0
                        && action.getMethod().getParameterCount() > 0) {
                    // 一般有参数的方法，Cached.key应该是动态的
                    logger.log(
                            Level.WARNING,
                            action.getMethod() + " has parameters but @" + Cached.class.getSimpleName()
                                    + ".key not contains parameter");
                }
                Field c = clazz.getDeclaredField(field);
                c.setAccessible(true);
                c.set(service, action);
                RedkaleClassLoader.putReflectionField(clazz.getName(), c);
            } catch (Exception e) {
                throw new RedkaleException("field (" + field + ") in " + clazz.getName() + " set error", e);
            }
        });
        // do nothing
    }
}
