/*
 *
 */
package org.redkale.cache.spi;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.redkale.asm.AnnotationVisitor;
import org.redkale.asm.AsmMethodBean;
import org.redkale.asm.AsmMethodBoost;
import org.redkale.asm.Asms;
import org.redkale.asm.ClassWriter;
import org.redkale.asm.FieldVisitor;
import org.redkale.asm.Label;
import org.redkale.asm.MethodVisitor;
import static org.redkale.asm.Opcodes.*;
import org.redkale.asm.Type;
import org.redkale.cache.Cached;
import org.redkale.inject.ResourceFactory;
import org.redkale.util.RedkaleException;
import org.redkale.util.TypeToken;

/**
 *
 * @author zhangjx
 */
public class CacheAsmMethodBoost extends AsmMethodBoost {

    private static final java.lang.reflect.Type FUTURE_VOID = new TypeToken<CompletableFuture<Void>>() {
    }.getType();

    private static final List<Class<? extends Annotation>> FILTER_ANN = List.of(Cached.class, DynForCache.class);

    public CacheAsmMethodBoost(Class serviceType) {
        super(serviceType);
    }

    @Override
    public List<Class<? extends Annotation>> filterMethodAnnotations(Method method) {
        return FILTER_ANN;
    }

    @Override
    public String doMethod(ClassWriter cw, String newDynName, String fieldPrefix, List filterAnns, Method method, final String newMethodName) {
        Cached cached = method.getAnnotation(Cached.class);
        if (cached == null) {
            return newMethodName;
        }
        if (method.getAnnotation(DynForCache.class) != null) {
            return newMethodName;
        }
        if (Modifier.isFinal(method.getModifiers()) || Modifier.isStatic(method.getModifiers())) {
            throw new RedkaleException("@" + Cached.class.getSimpleName() + " cannot on final or static method, but on " + method);
        }
        if (!Modifier.isProtected(method.getModifiers()) && !Modifier.isPublic(method.getModifiers())) {
            throw new RedkaleException("@" + Cached.class.getSimpleName() + " must on protected or public method, but on " + method);
        }
        if (method.getReturnType() == void.class || FUTURE_VOID.equals(method.getGenericReturnType())) {
            throw new RedkaleException("@" + Cached.class.getSimpleName() + " cannot on void method, but on " + method);
        }

        final String rsMethodName = method.getName() + "_afterCache";
        final String dynFieldName = fieldPrefix + "_" + method.getName() + "CacheAction" + fieldIndex.incrementAndGet();
        { //定义一个新方法调用 this.rsMethodName
            final AsmMethodBean methodBean = getMethodBean(method);
            final String cacheDynDesc = Type.getDescriptor(DynForCache.class);
            final MethodVisitor mv = createMethodVisitor(cw, method, newMethodName, methodBean);
            //mv.setDebug(true);
            Label l0 = new Label();
            mv.visitLabel(l0);
            AnnotationVisitor av = mv.visitAnnotation(cacheDynDesc, true);
            av.visit("dynField", dynFieldName);
            Asms.visitAnnotation(av, cached);
            visitRawAnnotation(method, newMethodName, mv, Cached.class, filterAnns);
            mv.visitVarInsn(ALOAD, 0);
            //传参数
            List<Integer> insns = visitVarInsnParamTypes(mv, method);
            mv.visitMethodInsn(INVOKESPECIAL, newDynName, rsMethodName, Type.getMethodDescriptor(method), false);
            visitInsnReturn(mv, method, l0, insns, methodBean);
            mv.visitMaxs(20, 20);
            mv.visitEnd();
        }
        { //定义字段
            FieldVisitor fv = cw.visitField(ACC_PRIVATE, dynFieldName, Type.getDescriptor(CacheAction.class), null, null);
            fv.visitEnd();
        }
        return rsMethodName;
    }

    @Override
    public void doAfterMethods(ClassWriter cw, String newDynName, String fieldPrefix) {
        //do nothing
    }

    @Override
    public void doInstance(ResourceFactory resourceFactory, Object service) {
        //do nothing
    }

}
