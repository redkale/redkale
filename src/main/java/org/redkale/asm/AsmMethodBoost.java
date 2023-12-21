/*
 *
 */
package org.redkale.asm;

import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.redkale.annotation.Nullable;
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
import static org.redkale.asm.Opcodes.IRETURN;
import static org.redkale.asm.Opcodes.LLOAD;
import static org.redkale.asm.Opcodes.LRETURN;
import static org.redkale.asm.Opcodes.RETURN;
import org.redkale.inject.ResourceFactory;
import org.redkale.util.Utility;

/**
 * 生产动态字节码的方法扩展器， 可以进行方法加强动作
 *
 * @param <T> 泛型
 *
 * @since 2.8.0
 */
public abstract class AsmMethodBoost<T> {

    protected final AtomicInteger fieldIndex = new AtomicInteger();

    protected final Class serviceType;

    protected AsmMethodBoost(Class serviceType) {
        this.serviceType = serviceType;
    }

    public static AsmMethodBoost create(Collection<AsmMethodBoost> list) {
        return new AsmMethodBoosts(list);
    }

    public static AsmMethodBoost create(AsmMethodBoost... items) {
        return new AsmMethodBoosts(items);
    }

    /**
     *
     * 返回一个类所有方法的字节信息， key为: method.getName+':'+Type.getMethodDescriptor(method)
     *
     * @param clazz Class
     *
     * @return Map
     */
    public static Map<String, AsmMethodBean> getMethodBeans(Class clazz) {
        Map<String, AsmMethodBean> rs = MethodParamClassVisitor.getMethodParamNames(new HashMap<>(), clazz);
        //返回的List中参数列表可能会比方法参数量多，因为方法内的临时变量也会存入list中， 所以需要list的元素集合比方法的参数多
        rs.values().forEach(AsmMethodBean::removeEmptyNames);
        return rs;
    }

    /**
     * 获取需屏蔽的方法上的注解
     *
     * @param method 方法
     *
     * @return 需要屏蔽的注解
     */
    public abstract List<Class<? extends Annotation>> filterMethodAnnotations(Method method);

    /**
     * 对方法进行动态加强处理
     *
     * @param cw            动态字节码Writer
     * @param newDynName    动态新类名
     * @param fieldPrefix   动态字段的前缀
     * @param filterAnns    需要过滤的注解
     * @param method        操作的方法
     * @param newMethodName 新的方法名, 可能为null
     *
     * @return 下一个新的方法名，不做任何处理应返回参数newMethodName
     */
    public abstract String doMethod(ClassWriter cw, String newDynName, String fieldPrefix,
        List<Class<? extends Annotation>> filterAnns, Method method, @Nullable String newMethodName);

    /** 处理所有动态方法后调用
     *
     * @param cw          动态字节码Writer
     * @param newDynName  动态新类名
     * @param fieldPrefix 动态字段的前缀
     */
    public void doAfterMethods(ClassWriter cw, String newDynName, String fieldPrefix) {
    }

    /**
     * 实例对象进行操作，通常用于给动态的字段赋值
     *
     * @param resourceFactory ResourceFactory
     * @param service         实例对象
     */
    public abstract void doInstance(ResourceFactory resourceFactory, T service);

    protected AsmMethodBean getMethodBean(Method method) {
        Map<String, AsmMethodBean> methodBeans = AsmMethodBoost.getMethodBeans(serviceType);
        return AsmMethodBean.get(methodBeans, method);
    }

    protected MethodVisitor createMethodVisitor(ClassWriter cw, Method method, String newMethodName, AsmMethodBean methodBean) {
        return new MethodDebugVisitor(cw.visitMethod(getAcc(method, newMethodName),
            getNowMethodName(method, newMethodName), Type.getMethodDescriptor(method),
            getMethodSignature(method, methodBean), getMethodExceptions(method, methodBean)));
    }

    protected int getAcc(Method method, String newMethodName) {
        if (newMethodName != null) {
            return ACC_PRIVATE;
        }
        return Modifier.isProtected(method.getModifiers()) ? ACC_PROTECTED : ACC_PUBLIC;
    }

    protected String getNowMethodName(Method method, String newMethodName) {
        return newMethodName == null ? method.getName() : newMethodName;
    }

    protected String getMethodSignature(Method method, AsmMethodBean methodBean) {
        return methodBean != null ? methodBean.getSignature() : null;
    }

    protected String[] getMethodExceptions(Method method, AsmMethodBean methodBean) {
        if (methodBean == null) {
            String[] exceptions = null;
            Class<?>[] expTypes = method.getExceptionTypes();
            if (expTypes.length > 0) {
                exceptions = new String[expTypes.length];
                for (int i = 0; i < expTypes.length; i++) {
                    exceptions[i] = expTypes[i].getName().replace('.', '/');
                }
            }
            return exceptions;
        } else {
            return methodBean.getExceptions();
        }
    }

    protected void visitRawAnnotation(Method method, String newMethodName, MethodVisitor mv, Class selfAnnType, List filterAnns) {
        if (newMethodName == null) {
            //给方法加上原有的Annotation
            final Annotation[] anns = method.getAnnotations();
            for (Annotation ann : anns) {
                if (ann.annotationType() != selfAnnType
                    && (filterAnns == null || !filterAnns.contains(ann.annotationType()))) {
                    Asms.visitAnnotation(mv.visitAnnotation(Type.getDescriptor(ann.annotationType()), true), ann);
                }
            }
            //给参数加上原有的Annotation
            final Annotation[][] annss = method.getParameterAnnotations();
            for (int k = 0; k < annss.length; k++) {
                for (Annotation ann : annss[k]) {
                    Asms.visitAnnotation(mv.visitParameterAnnotation(k, Type.getDescriptor(ann.annotationType()), true), ann);
                }
            }
        }
    }

    protected List<Integer> visitVarInsnParamTypes(MethodVisitor mv, Method method) {
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
        return insns;
    }

    protected void visitInsnReturn(MethodVisitor mv, Method method, Label l0, List<Integer> insns, AsmMethodBean methodBean) {
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
        Class[] paramTypes = method.getParameterTypes();
        if (methodBean != null && paramTypes.length > 0) {
            Label l2 = new Label();
            mv.visitLabel(l2);
            //mv.visitLocalVariable("this", thisClassDesc, null, l0, l2, 0);
            List<String> fieldNames = methodBean.getFieldNames();
            for (int i = 0; i < paramTypes.length; i++) {
                mv.visitLocalVariable(fieldNames.get(i), Type.getDescriptor(paramTypes[i]), null, l0, l2, insns.get(i));
            }
        }
    }

    /**
     * 生产动态字节码的方法扩展器， 可以进行方法加强动作
     *
     * @param <T> 泛型
     *
     * @since 2.8.0
     */
    static class AsmMethodBoosts<T> extends AsmMethodBoost<T> {

        private final AsmMethodBoost[] items;

        public AsmMethodBoosts(Collection<AsmMethodBoost> list) {
            super(null);
            this.items = list.toArray(new AsmMethodBoost[list.size()]);
        }

        public AsmMethodBoosts(AsmMethodBoost... items) {
            super(null);
            this.items = items;
        }

        @Override
        public List<Class<? extends Annotation>> filterMethodAnnotations(Method method) {
            List<Class<? extends Annotation>> list = null;
            for (AsmMethodBoost item : items) {
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
        public String doMethod(ClassWriter cw, String newDynName, String fieldPrefix,
            List<Class<? extends Annotation>> filterAnns, Method method, String newMethodName) {
            String newName = newMethodName;
            for (AsmMethodBoost item : items) {
                if (item != null) {
                    newName = item.doMethod(cw, newDynName, fieldPrefix, filterAnns, method, newName);
                }
            }
            return newName;
        }

        @Override
        public void doAfterMethods(ClassWriter cw, String newDynName, String fieldPrefix) {
            for (AsmMethodBoost item : items) {
                if (item != null) {
                    item.doAfterMethods(cw, newDynName, fieldPrefix);
                }
            }
        }

        @Override
        public void doInstance(ResourceFactory resourceFactory, T service) {
            for (AsmMethodBoost item : items) {
                if (item != null) {
                    item.doInstance(resourceFactory, service);
                }
            }
        }

    }

    static class MethodParamClassVisitor extends ClassVisitor {

        private final Map<String, AsmMethodBean> fieldMap;

        public MethodParamClassVisitor(int api, final Map<String, AsmMethodBean> fieldmap) {
            super(api);
            this.fieldMap = fieldmap;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            if (java.lang.reflect.Modifier.isStatic(access)) {
                return null;
            }
            String key = name + ":" + desc;
            if (fieldMap.containsKey(key)) {
                return null;
            }
            AsmMethodBean bean = new AsmMethodBean(access, name, desc, signature, exceptions);
            List<String> fieldNames = bean.getFieldNames();
            fieldMap.put(key, bean);
            return new MethodVisitor(Opcodes.ASM6) {
                @Override
                public void visitLocalVariable(String name, String description, String signature, Label start, Label end, int index) {
                    if (index < 1) {
                        return;
                    }
                    int size = fieldNames.size();
                    //index并不会按顺序执行的
                    if (index > size) {
                        for (int i = size; i < index; i++) {
                            fieldNames.add(" ");
                        }
                        fieldNames.set(index - 1, name);
                    }
                    fieldNames.set(index - 1, name);
                }
            };
        }

        //返回的List中参数列表可能会比方法参数量多，因为方法内的临时变量也会存入list中， 所以需要list的元素集合比方法的参数多
        static Map<String, AsmMethodBean> getMethodParamNames(Map<String, AsmMethodBean> map, Class clazz) {
            String n = clazz.getName();
            InputStream in = clazz.getResourceAsStream(n.substring(n.lastIndexOf('.') + 1) + ".class");
            if (in == null) {
                return map;
            }
            try {
                new ClassReader(Utility.readBytesThenClose(in)).accept(new MethodParamClassVisitor(Opcodes.ASM6, map), 0);
            } catch (Exception e) { //无需理会
            }
            Class superClass = clazz.getSuperclass();
            if (superClass == Object.class) {
                return map;
            }
            return getMethodParamNames(map, superClass);
        }
    }
}
