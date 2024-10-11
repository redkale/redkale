/*
 *
 */
package org.redkale.cached.spi;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.redkale.asm.AnnotationVisitor;
import org.redkale.asm.AsmMethodBean;
import org.redkale.asm.AsmMethodBoost;
import org.redkale.asm.AsmNewMethod;
import org.redkale.asm.Asms;
import org.redkale.asm.ClassWriter;
import org.redkale.asm.FieldVisitor;
import org.redkale.asm.Handle;
import org.redkale.asm.Label;
import org.redkale.asm.MethodVisitor;
import org.redkale.asm.Opcodes;
import static org.redkale.asm.Opcodes.*;
import org.redkale.asm.Type;
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
 *
 * @since 2.8.0
 */
public class CachedAsmMethodBoost extends AsmMethodBoost {

    static final java.lang.reflect.Type FUTURE_VOID = new TypeToken<CompletableFuture<Void>>() {}.getType();

    private static final List<Class<? extends Annotation>> FILTER_ANN = List.of(Cached.class, DynForCached.class);

    private final Logger logger = Logger.getLogger(getClass().getSimpleName());

    private Map<String, CachedAction> actionMap;

    public CachedAsmMethodBoost(boolean remote, Class serviceType) {
        super(remote, serviceType);
    }

    @Override
    public List<Class<? extends Annotation>> filterMethodAnnotations(Method method) {
        return FILTER_ANN;
    }

    @Override
    public AsmNewMethod doMethod(
            final RedkaleClassLoader classLoader,
            final ClassWriter cw,
            final Class serviceImplClass,
            final String newDynName,
            final String fieldPrefix,
            final List filterAnns,
            final Method method,
            final AsmNewMethod newMethod) {
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
        final AsmMethodBean methodBean = getMethodBean(method);
        { // 定义一个新方法调用 this.rsMethodName
            final String cacheDynDesc = Type.getDescriptor(DynForCached.class);
            final MethodVisitor mv = createMethodVisitor(cw, method, newMethod, methodBean);
            // mv.setDebug(true);
            AnnotationVisitor av = mv.visitAnnotation(cacheDynDesc, true);
            av.visit("dynField", dynFieldName);
            Asms.visitAnnotation(av, DynForCached.class, cached);
            visitRawAnnotation(method, newMethod, mv, Cached.class, filterAnns);

            Label l0 = new Label();
            mv.visitLabel(l0);
            mv.visitVarInsn(ALOAD, 0);
            List<Integer> insns = visitVarInsnParamTypes(mv, method, 0);
            String dynDesc = methodBean.getDesc();
            dynDesc = "(L" + newDynName + ";" + dynDesc.substring(1, dynDesc.lastIndexOf(')') + 1)
                    + Type.getDescriptor(ThrowSupplier.class);
            mv.visitInvokeDynamicInsn("get", dynDesc, Asms.createLambdaMetaHandle(), new Object[] {
                org.redkale.asm.Type.getType("()Ljava/lang/Object;"),
                new Handle(Opcodes.H_INVOKESPECIAL, newDynName, "lambda$" + actionIndex, methodBean.getDesc(), false),
                org.redkale.asm.Type.getType("()" + Type.getDescriptor(method.getReturnType()))
            });
            mv.visitVarInsn(ASTORE, 1 + method.getParameterCount());
            Label l1 = new Label();
            mv.visitLabel(l1);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, newDynName, dynFieldName, Type.getDescriptor(CachedAction.class));

            mv.visitVarInsn(ALOAD, 1 + method.getParameterCount());
            Asms.visitInsn(mv, method.getParameterCount());
            mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
            int insn = 0;
            Class[] paramtypes = method.getParameterTypes();
            for (int j = 0; j < paramtypes.length; j++) {
                final Class pt = paramtypes[j];
                mv.visitInsn(DUP);
                insn++;
                Asms.visitInsn(mv, j);
                if (pt.isPrimitive()) {
                    if (pt == long.class) {
                        mv.visitVarInsn(LLOAD, insn++);
                    } else if (pt == float.class) {
                        mv.visitVarInsn(FLOAD, insn++);
                    } else if (pt == double.class) {
                        mv.visitVarInsn(DLOAD, insn++);
                    } else {
                        mv.visitVarInsn(ILOAD, insn);
                    }
                    Class bigclaz = TypeToken.primitiveToWrapper(pt);
                    mv.visitMethodInsn(
                            INVOKESTATIC,
                            bigclaz.getName().replace('.', '/'),
                            "valueOf",
                            "(" + Type.getDescriptor((Class) pt) + ")" + Type.getDescriptor(bigclaz),
                            false);
                } else {
                    mv.visitVarInsn(ALOAD, insn);
                }
                mv.visitInsn(AASTORE);
            }
            String throwFuncDesc = Type.getDescriptor(ThrowSupplier.class);
            mv.visitMethodInsn(
                    INVOKEVIRTUAL,
                    CachedAction.class.getName().replace('.', '/'),
                    "get",
                    "(" + throwFuncDesc + "[Ljava/lang/Object;)Ljava/lang/Object;",
                    false);
            mv.visitTypeInsn(CHECKCAST, method.getReturnType().getName().replace('.', '/'));
            mv.visitInsn(ARETURN);
            Label l2 = new Label();
            mv.visitLabel(l2);
            mv.visitLocalVariable("this", "L" + newDynName + ";", null, l0, l2, 0);
            visitParamTypesLocalVariable(mv, method, l0, l2, insns, methodBean);
            mv.visitLocalVariable("_redkale_supplier", Type.getDescriptor(ThrowSupplier.class), null, l1, l2, ++insn);

            mv.visitMaxs(20, 20);
            mv.visitEnd();
            CachedAction action = new CachedAction(
                    new CachedEntry(cached), method, serviceType, methodBean.paramNameArray(method), dynFieldName);
            actions.put(dynFieldName, action);
        }
        { // ThrowSupplier
            final MethodVisitor mv = cw.visitMethod(
                    ACC_PRIVATE + ACC_SYNTHETIC, "lambda$" + actionIndex, methodBean.getDesc(), null, new String[] {
                        "java/lang/Throwable"
                    });
            // mv.setDebug(true);
            Label l0 = new Label();
            mv.visitLabel(l0);
            mv.visitVarInsn(ALOAD, 0);
            visitVarInsnParamTypes(mv, method, 0);
            mv.visitMethodInsn(INVOKESPECIAL, newDynName, rsMethodName, methodBean.getDesc(), false);
            mv.visitInsn(ARETURN);
            Label l1 = new Label();
            mv.visitLabel(l1);
            mv.visitLocalVariable("this", "L" + newDynName + ";", null, l0, l1, 0);
            mv.visitMaxs(5, 5);
            mv.visitEnd();
        }
        { // 定义字段
            FieldVisitor fv =
                    cw.visitField(ACC_PRIVATE, dynFieldName, Type.getDescriptor(CachedAction.class), null, null);
            fv.visitEnd();
        }
        if (actions.size() == 1) {
            cw.visitInnerClass(
                    "java/lang/invoke/MethodHandles$Lookup",
                    "java/lang/invoke/MethodHandles",
                    "Lookup",
                    ACC_PUBLIC + ACC_FINAL + ACC_STATIC);
        }
        return new AsmNewMethod(rsMethodName, ACC_PRIVATE);
    }

    @Override
    public void doInstance(RedkaleClassLoader classLoader, ResourceFactory resourceFactory, Object service) {
        Class clazz = service.getClass();
        if (actionMap == null) { // 为null表示没有调用过doMethod， 动态类在编译是已经生成好了
            actionMap = new LinkedHashMap<>();
            Map<String, AsmMethodBean> methodBeans = AsmMethodBoost.getMethodBeans(clazz);
            for (final Method method : clazz.getDeclaredMethods()) {
                DynForCached cached = method.getAnnotation(DynForCached.class);
                if (cached != null) {
                    String dynFieldName = cached.dynField();
                    AsmMethodBean methodBean = AsmMethodBean.get(methodBeans, method);
                    CachedAction action = new CachedAction(
                            new CachedEntry(cached),
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
