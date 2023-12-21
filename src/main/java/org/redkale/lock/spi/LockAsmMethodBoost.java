/*
 *
 */
package org.redkale.lock.spi;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import org.redkale.asm.AnnotationVisitor;
import org.redkale.asm.AsmMethodBean;
import org.redkale.asm.AsmMethodBoost;
import org.redkale.asm.Asms;
import org.redkale.asm.ClassWriter;
import org.redkale.asm.Label;
import org.redkale.asm.MethodVisitor;
import static org.redkale.asm.Opcodes.*;
import org.redkale.asm.Type;
import org.redkale.inject.ResourceFactory;
import org.redkale.lock.Locked;
import org.redkale.util.RedkaleException;

/**
 *
 * @author zhangjx
 */
public class LockAsmMethodBoost extends AsmMethodBoost {

    private static final List<Class<? extends Annotation>> FILTER_ANN = List.of(Locked.class, DynForLock.class);

    public LockAsmMethodBoost(Class serviceType) {
        super(serviceType);
    }

    @Override
    public List<Class<? extends Annotation>> filterMethodAnnotations(Method method) {
        return FILTER_ANN;
    }

    @Override
    public String doMethod(ClassWriter cw, String newDynName, String fieldPrefix, List filterAnns, Method method, final String newMethodName) {
        Locked locked = method.getAnnotation(Locked.class);
        if (locked == null) {
            return newMethodName;
        }
        if (method.getAnnotation(DynForLock.class) != null) {
            return newMethodName;
        }
        if (Modifier.isFinal(method.getModifiers()) || Modifier.isStatic(method.getModifiers())) {
            throw new RedkaleException("@" + Locked.class.getSimpleName() + " can not on final or static method, but on " + method);
        }
        if (!Modifier.isProtected(method.getModifiers()) && !Modifier.isPublic(method.getModifiers())) {
            throw new RedkaleException("@" + Locked.class.getSimpleName() + " must on protected or public method, but on " + method);
        }

        final String rsMethodName = method.getName() + "_afterLock";
        final String dynFieldName = fieldPrefix + "_" + method.getName() + "LockAction" + fieldIndex.incrementAndGet();
        {  //定义一个新方法调用 this.rsMethodName
            final AsmMethodBean methodBean = getMethodBean(method);
            final String lockDynDesc = Type.getDescriptor(DynForLock.class);
            final MethodVisitor mv = createMethodVisitor(cw, method, newMethodName, methodBean);
            //mv.setDebug(true);
            Label l0 = new Label();
            mv.visitLabel(l0);
            AnnotationVisitor av = mv.visitAnnotation(lockDynDesc, true);
            av.visit("dynField", dynFieldName);
            Asms.visitAnnotation(av, locked);
            visitRawAnnotation(method, newMethodName, mv, Locked.class, filterAnns);
            mv.visitVarInsn(ALOAD, 0);
            List<Integer> insns = visitVarInsnParamTypes(mv, method, 0);
            mv.visitMethodInsn(INVOKESPECIAL, newDynName, rsMethodName, Type.getMethodDescriptor(method), false);
            visitInsnReturn(mv, method, l0, insns, methodBean);
            mv.visitMaxs(20, 20);
            mv.visitEnd();
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
