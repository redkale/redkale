/*
 *
 */
package org.redkale.cache.spi;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.redkale.asm.AnnotationVisitor;
import org.redkale.asm.AsmMethodBean;
import org.redkale.asm.AsmMethodBoost;
import org.redkale.asm.Asms;
import org.redkale.asm.ClassWriter;
import org.redkale.asm.Label;
import org.redkale.asm.MethodDebugVisitor;
import static org.redkale.asm.Opcodes.ACC_PRIVATE;
import static org.redkale.asm.Opcodes.ACC_PROTECTED;
import static org.redkale.asm.Opcodes.ACC_PUBLIC;
import static org.redkale.asm.Opcodes.ALOAD;
import static org.redkale.asm.Opcodes.ARETURN;
import static org.redkale.asm.Opcodes.DLOAD;
import static org.redkale.asm.Opcodes.DRETURN;
import static org.redkale.asm.Opcodes.FLOAD;
import static org.redkale.asm.Opcodes.FRETURN;
import static org.redkale.asm.Opcodes.ILOAD;
import static org.redkale.asm.Opcodes.INVOKESPECIAL;
import static org.redkale.asm.Opcodes.IRETURN;
import static org.redkale.asm.Opcodes.LLOAD;
import static org.redkale.asm.Opcodes.LRETURN;
import static org.redkale.asm.Opcodes.RETURN;
import org.redkale.asm.Type;
import org.redkale.cache.Cached;
import org.redkale.util.RedkaleException;

/**
 *
 * @author zhangjx
 */
public class CacheAsmMethodBoost implements AsmMethodBoost {

    protected final Class serviceType;

    public CacheAsmMethodBoost(Class serviceType) {
        this.serviceType = serviceType;
    }

    @Override
    public String doMethod(ClassWriter cw, String newDynName, String fieldPrefix, Method method, final String newMethodName) {
        Cached cached = method.getAnnotation(Cached.class);
        if (cached == null) {
            return newMethodName;
        }
        if (Modifier.isFinal(method.getModifiers()) || Modifier.isStatic(method.getModifiers())) {
            throw new RedkaleException("@" + Cached.class.getSimpleName() + " can not on final or static method, but on " + method);
        }
        if (!Modifier.isProtected(method.getModifiers()) && !Modifier.isPublic(method.getModifiers())) {
            throw new RedkaleException("@" + Cached.class.getSimpleName() + " must on protected or public method, but on " + method);
        }
        int acc;
        String nowMethodName;
        if (newMethodName == null) {
            nowMethodName = method.getName();
            acc = Modifier.isProtected(method.getModifiers()) ? ACC_PROTECTED : ACC_PUBLIC;
        } else {
            acc = ACC_PRIVATE;
            nowMethodName = newMethodName;
        }

        final String rsMethodName = method.getName() + "_cache";
        {
            Map<String, AsmMethodBean> methodBeans = AsmMethodBoost.getMethodBean(serviceType);
            String desc = Type.getMethodDescriptor(method);
            AsmMethodBean methodBean = methodBeans.get(method.getName() + ":" + desc);

            final String cacheDynDesc = Type.getDescriptor(CacheDyn.class);
            MethodDebugVisitor mv;
            AnnotationVisitor av;
            String signature = null;
            String[] exceptions = null;
            if (methodBean == null) {
                Class<?>[] expTypes = method.getExceptionTypes();
                if (expTypes.length > 0) {
                    exceptions = new String[expTypes.length];
                    for (int i = 0; i < expTypes.length; i++) {
                        exceptions[i] = expTypes[i].getName().replace('.', '/');
                    }
                }
            } else {
                signature = methodBean.getSignature();
                exceptions = methodBean.getExceptions();
            }
            //需要定义一个新方法调用 this.rsMethodName
            mv = new MethodDebugVisitor(cw.visitMethod(acc, nowMethodName, Type.getMethodDescriptor(method), signature, exceptions));
            //mv.setDebug(true);
            Label l0 = new Label();
            mv.visitLabel(l0);
            {
                av = mv.visitAnnotation(cacheDynDesc, true);
                av.visitEnd();
                final Annotation[] anns = method.getAnnotations();
                for (Annotation ann : anns) {
                    if (ann.annotationType() != Cached.class) {
                        Asms.visitAnnotation(mv.visitAnnotation(Type.getDescriptor(ann.annotationType()), true), ann);
                    }
                }
            }
            { //给参数加上原有的Annotation
                final Annotation[][] anns = method.getParameterAnnotations();
                for (int k = 0; k < anns.length; k++) {
                    for (Annotation ann : anns[k]) {
                        Asms.visitAnnotation(mv.visitParameterAnnotation(k, Type.getDescriptor(ann.annotationType()), true), ann);
                    }
                }
            }
            mv.visitVarInsn(ALOAD, 0);
            //传参数
            Class[] paramTypes = method.getParameterTypes();
            List<Integer> insns = new ArrayList<>();
            int insn = 0;
            for (Class pt : paramTypes) {
                insn++;
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
                } else {
                    mv.visitVarInsn(ALOAD, insn);
                }
                insns.add(insn);
            }
            mv.visitMethodInsn(INVOKESPECIAL, newDynName, rsMethodName, Type.getMethodDescriptor(method), false);
            if (method.getGenericReturnType() == void.class) {
                mv.visitInsn(RETURN);
            } else {
                Class returnclz = method.getReturnType();
                if (returnclz.isPrimitive()) {
                    if (returnclz == long.class) {
                        mv.visitInsn(LRETURN);
                    } else if (returnclz == float.class) {
                        mv.visitInsn(FRETURN);
                    } else if (returnclz == double.class) {
                        mv.visitInsn(DRETURN);
                    } else {
                        mv.visitInsn(IRETURN);
                    }
                } else {
                    mv.visitInsn(ARETURN);
                }
            }
            if (methodBean != null && paramTypes.length > 0) {
                Label l2 = new Label();
                mv.visitLabel(l2);
                //mv.visitLocalVariable("this", thisClassDesc, null, l0, l2, 0);
                List<String> fieldNames = methodBean.getFieldNames();
                for (int i = 0; i < paramTypes.length; i++) {
                    mv.visitLocalVariable(fieldNames.get(i), Type.getDescriptor(paramTypes[i]), null, l0, l2, insns.get(i));
                }
            }
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
    public void doInstance(Object service) {
        //do nothing
    }

}
