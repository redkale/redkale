/*
 *
 */
package org.redkale.locked.spi;

import static org.redkale.asm.Opcodes.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import org.redkale.asm.AnnotationVisitor;
import org.redkale.asm.ClassWriter;
import org.redkale.asm.Label;
import org.redkale.asm.MethodVisitor;
import org.redkale.asm.Type;
import org.redkale.bytecode.ByteCodes;
import org.redkale.bytecode.CodeMethodBean;
import org.redkale.bytecode.CodeMethodBoost;
import org.redkale.bytecode.CodeNewMethod;
import org.redkale.inject.ResourceFactory;
import org.redkale.locked.Locked;
import org.redkale.service.LoadMode;
import org.redkale.util.RedkaleClassLoader;
import org.redkale.util.RedkaleException;

/** @author zhangjx */
public class LockedCodeMethodBoost extends CodeMethodBoost {

    private static final List<Class<? extends Annotation>> FILTER_ANN = List.of(Locked.class, DynForLocked.class);

    public LockedCodeMethodBoost(boolean remote, Class serviceType) {
        super(remote, serviceType);
    }

    @Override
    public List<Class<? extends Annotation>> filterMethodAnnotations(Method method) {
        return FILTER_ANN;
    }

    @Override
    public CodeNewMethod doMethod(
            RedkaleClassLoader classLoader,
            ClassWriter cw,
            Class serviceImplClass,
            String newDynName,
            String fieldPrefix,
            List filterAnns,
            Method method,
            final CodeNewMethod newMethod) {
        Locked locked = method.getAnnotation(Locked.class);
        if (locked == null) {
            return newMethod;
        }
        if (!LoadMode.matches(remote, locked.mode())) {
            return newMethod;
        }
        if (method.getAnnotation(DynForLocked.class) != null) {
            return newMethod;
        }
        if (Modifier.isFinal(method.getModifiers()) || Modifier.isStatic(method.getModifiers())) {
            throw new RedkaleException(
                    "@" + Locked.class.getSimpleName() + " can not on final or static method, but on " + method);
        }
        if (!Modifier.isProtected(method.getModifiers()) && !Modifier.isPublic(method.getModifiers())) {
            throw new RedkaleException(
                    "@" + Locked.class.getSimpleName() + " must on protected or public method, but on " + method);
        }

        final String rsMethodName = method.getName() + "_afterLocked";
        final String dynFieldName = fieldPrefix + "_" + method.getName() + LockedAction.class.getSimpleName()
                + fieldIndex.incrementAndGet();
        { // 定义一个新方法调用 this.rsMethodName
            final CodeMethodBean methodBean = getMethodBean(method);
            final String lockDynDesc = Type.getDescriptor(DynForLocked.class);
            final MethodVisitor mv = createMethodVisitor(cw, method, newMethod, methodBean);
            // mv.setDebug(true);
            Label l0 = new Label();
            mv.visitLabel(l0);
            AnnotationVisitor av = mv.visitAnnotation(lockDynDesc, true);
            av.visit("dynField", dynFieldName);
            ByteCodes.visitAnnotation(av, DynForLocked.class, locked);
            visitRawAnnotation(method, newMethod, mv, Locked.class, filterAnns);
            mv.visitVarInsn(ALOAD, 0);
            List<Integer> insns = visitVarInsnParamTypes(mv, method, 0);
            mv.visitMethodInsn(INVOKESPECIAL, newDynName, rsMethodName, Type.getMethodDescriptor(method), false);
            visitInsnReturn(mv, method, l0, insns, methodBean);
            mv.visitMaxs(20, 20);
            mv.visitEnd();
        }
        return new CodeNewMethod(rsMethodName, ACC_PRIVATE);
    }

    @Override
    public void doAfterMethods(RedkaleClassLoader classLoader, ClassWriter cw, String newDynName, String fieldPrefix) {
        // do nothing
    }

    @Override
    public void doInstance(RedkaleClassLoader classLoader, ResourceFactory resourceFactory, Object service) {
        // do nothing
    }
}
