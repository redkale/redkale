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
import org.redkale.util.RedkaleClassLoader;
import org.redkale.util.Utility;

/**
 * 生产动态字节码的方法扩展器， 可以进行方法加强动作
 *
 * <p>详情见: https://redkale.org
 *
 * @param <T> 泛型
 * @author zhangjx
 * @since 2.8.0
 */
public abstract class AsmMethodBoost<T> {

    protected final AtomicInteger fieldIndex = new AtomicInteger();

    protected final boolean remote;

    protected final Class serviceType;

    protected AsmMethodBoost(boolean remote, Class serviceType) {
        this.remote = remote;
        this.serviceType = serviceType;
    }

    public static AsmMethodBoost create(boolean remote, Collection<AsmMethodBoost> list) {
        return new AsmMethodBoosts(remote, list);
    }

    public static AsmMethodBoost create(boolean remote, AsmMethodBoost... items) {
        return new AsmMethodBoosts(remote, items);
    }

    /**
     * 返回一个类所有方法的字节信息， key为: method.getName+':'+Type.getMethodDescriptor(method)
     *
     * @param clazz Class
     * @return Map
     */
    public static Map<String, AsmMethodBean> getMethodBeans(Class clazz) {
        Map<String, AsmMethodBean> rs = MethodParamClassVisitor.getMethodParamNames(new HashMap<>(), clazz);
        // 返回的List中参数列表可能会比方法参数量多，因为方法内的临时变量也会存入list中， 所以需要list的元素集合比方法的参数多
        rs.values().forEach(AsmMethodBean::removeEmptyNames);
        return rs;
    }

    public static String getMethodBeanKey(Method method) {
        return method.getName() + ":" + Type.getMethodDescriptor(method);
    }

    /**
     * 获取需屏蔽的方法上的注解
     *
     * @param method 方法
     * @return 需要屏蔽的注解
     */
    public abstract List<Class<? extends Annotation>> filterMethodAnnotations(Method method);

    /**
     * 对方法进行动态加强处理
     *
     * @param classLoader ClassLoader
     * @param cw 动态字节码Writer
     * @param serviceImplClass 原始实现类
     * @param newDynName 动态新类名
     * @param fieldPrefix 动态字段的前缀
     * @param filterAnns 需要过滤的注解
     * @param method 操作的方法
     * @param newMethod 新的方法名, 可能为null
     * @return 下一个新的方法名，不做任何处理应返回参数newMethodName
     */
    public abstract AsmNewMethod doMethod(
            RedkaleClassLoader classLoader,
            ClassWriter cw,
            Class serviceImplClass,
            String newDynName,
            String fieldPrefix,
            List<Class<? extends Annotation>> filterAnns,
            Method method,
            @Nullable AsmNewMethod newMethod);

    /**
     * 处理所有动态方法后调用
     *
     * @param classLoader ClassLoader
     * @param cw 动态字节码Writer
     * @param newDynName 动态新类名
     * @param fieldPrefix 动态字段的前缀
     */
    public void doAfterMethods(RedkaleClassLoader classLoader, ClassWriter cw, String newDynName, String fieldPrefix) {}

    /**
     * 处理所有动态方法后调用
     *
     * @param classLoader ClassLoader
     * @param cw 动态字节码Writer
     * @param mv 构造函数MethodVisitor
     * @param newDynName 动态新类名
     * @param fieldPrefix 动态字段的前缀
     * @param remote 是否远程模式
     */
    public void doConstructorMethod(
            RedkaleClassLoader classLoader,
            ClassWriter cw,
            MethodVisitor mv,
            String newDynName,
            String fieldPrefix,
            boolean remote) {}
    /**
     * 实例对象进行操作，通常用于给动态的字段赋值
     *
     * @param classLoader ClassLoader
     * @param resourceFactory ResourceFactory
     * @param service 实例对象
     */
    public abstract void doInstance(RedkaleClassLoader classLoader, ResourceFactory resourceFactory, T service);

    protected AsmMethodBean getMethodBean(Method method) {
        Map<String, AsmMethodBean> methodBeans = AsmMethodBoost.getMethodBeans(serviceType);
        return AsmMethodBean.get(methodBeans, method);
    }

    protected MethodVisitor createMethodVisitor(
            ClassWriter cw, Method method, AsmNewMethod newMethod, AsmMethodBean methodBean) {
        return new MethodDebugVisitor(cw.visitMethod(
                getAcc(method, newMethod),
                getNowMethodName(method, newMethod),
                Type.getMethodDescriptor(method),
                getMethodSignature(method, methodBean),
                getMethodExceptions(method, methodBean)));
    }

    protected final int getAcc(Method method, AsmNewMethod newMethod) {
        if (newMethod != null) {
            return ACC_PRIVATE;
        }
        return Modifier.isProtected(method.getModifiers()) ? ACC_PROTECTED : ACC_PUBLIC;
    }

    protected String getNowMethodName(Method method, AsmNewMethod newMethod) {
        return newMethod == null ? method.getName() : newMethod.getMethodName();
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

    protected void visitRawAnnotation(
            Method method, AsmNewMethod newMethod, MethodVisitor mv, Class skipAnnType, List skipAnns) {
        if (newMethod == null) {
            // 给方法加上原有的Annotation
            final Annotation[] anns = method.getAnnotations();
            for (Annotation ann : anns) {
                if (ann.annotationType() != skipAnnType
                        && (skipAnns == null || !skipAnns.contains(ann.annotationType()))) {
                    Asms.visitAnnotation(
                            mv.visitAnnotation(Type.getDescriptor(ann.annotationType()), true),
                            ann.annotationType(),
                            ann);
                }
            }
            // 给参数加上原有的Annotation
            final Annotation[][] annss = method.getParameterAnnotations();
            for (int k = 0; k < annss.length; k++) {
                for (Annotation ann : annss[k]) {
                    Asms.visitAnnotation(
                            mv.visitParameterAnnotation(k, Type.getDescriptor(ann.annotationType()), true),
                            ann.annotationType(),
                            ann);
                }
            }
        }
    }

    protected List<Integer> visitVarInsnParamTypes(MethodVisitor mv, Method method, int insn) {
        // 传参数
        Class[] paramTypes = method.getParameterTypes();
        List<Integer> insns = new ArrayList<>();
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

    protected void visitParamTypesLocalVariable(
            MethodVisitor mv, Method method, Label l0, Label l2, List<Integer> insns, AsmMethodBean methodBean) {
        Class[] paramTypes = method.getParameterTypes();
        if (methodBean != null && paramTypes.length > 0) {
            mv.visitLabel(l2);
            List<AsmMethodParam> params = methodBean.getParams();
            for (int i = 0; i < paramTypes.length; i++) {
                AsmMethodParam param = params.get(i);
                mv.visitLocalVariable(
                        param.getName(),
                        param.description(paramTypes[i]),
                        param.signature(paramTypes[i]),
                        l0,
                        l2,
                        insns.get(i));
            }
        }
    }

    protected void visitInsnReturn(
            MethodVisitor mv, Method method, Label l0, List<Integer> insns, AsmMethodBean methodBean) {
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
        visitParamTypesLocalVariable(mv, method, l0, new Label(), insns, methodBean);
    }

    /**
     * 生产动态字节码的方法扩展器， 可以进行方法加强动作
     *
     * @param <T> 泛型
     * @since 2.8.0
     */
    static class AsmMethodBoosts<T> extends AsmMethodBoost<T> {

        private final AsmMethodBoost[] items;

        public AsmMethodBoosts(boolean remote, Collection<AsmMethodBoost> list) {
            super(remote, null);
            this.items = list.toArray(new AsmMethodBoost[list.size()]);
        }

        public AsmMethodBoosts(boolean remote, AsmMethodBoost... items) {
            super(remote, null);
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
        public AsmNewMethod doMethod(
                RedkaleClassLoader classLoader,
                ClassWriter cw,
                Class serviceImplClass,
                String newDynName,
                String fieldPrefix,
                List<Class<? extends Annotation>> filterAnns,
                Method method,
                AsmNewMethod newMethod) {
            AsmNewMethod newResult = newMethod;
            for (AsmMethodBoost item : items) {
                if (item != null) {
                    newResult = item.doMethod(
                            classLoader, cw, serviceImplClass, newDynName, fieldPrefix, filterAnns, method, newResult);
                }
            }
            return newResult;
        }

        @Override
        public void doAfterMethods(
                RedkaleClassLoader classLoader, ClassWriter cw, String newDynName, String fieldPrefix) {
            for (AsmMethodBoost item : items) {
                if (item != null) {
                    item.doAfterMethods(classLoader, cw, newDynName, fieldPrefix);
                }
            }
        }

        @Override
        public void doConstructorMethod(
                RedkaleClassLoader classLoader,
                ClassWriter cw,
                MethodVisitor mv,
                String newDynName,
                String fieldPrefix,
                boolean remote) {
            for (AsmMethodBoost item : items) {
                if (item != null) {
                    item.doConstructorMethod(classLoader, cw, mv, newDynName, fieldPrefix, remote);
                }
            }
        }

        @Override
        public void doInstance(RedkaleClassLoader classLoader, ResourceFactory resourceFactory, T service) {
            for (AsmMethodBoost item : items) {
                if (item != null) {
                    item.doInstance(classLoader, resourceFactory, service);
                }
            }
        }
    }

    static class MethodParamClassVisitor extends ClassVisitor {

        private Class serviceType;

        private final Map<String, AsmMethodBean> methodBeanMap;

        public MethodParamClassVisitor(int api, Class serviceType, final Map<String, AsmMethodBean> methodBeanMap) {
            super(api);
            this.serviceType = serviceType;
            this.methodBeanMap = methodBeanMap;
        }

        @Override
        public MethodVisitor visitMethod(
                int methodAccess,
                String methodName,
                String methodDesc,
                String methodSignature,
                String[] methodExceptions) {
            super.visitMethod(api, methodName, methodDesc, methodSignature, methodExceptions);
            if (java.lang.reflect.Modifier.isStatic(methodAccess)) {
                return null;
            }
            String key = methodName + ":" + methodDesc;
            if (methodBeanMap.containsKey(key)) {
                return null;
            }
            AsmMethodBean bean =
                    new AsmMethodBean(methodAccess, methodName, methodDesc, methodSignature, methodExceptions);
            List<AsmMethodParam> paramList = bean.getParams();
            methodBeanMap.put(key, bean);
            return new MethodVisitor(Opcodes.ASM6) {
                @Override
                public void visitParameter(String paramName, int paramAccess) {
                    paramList.add(new AsmMethodParam(paramName));
                }

                @Override
                public void visitLocalVariable(
                        String varName, String varDesc, String varSignature, Label start, Label end, int varIndex) {
                    if (varIndex < 1) {
                        return;
                    }
                    int size = paramList.size();
                    // index并不会按顺序执行
                    if (varIndex > size) {
                        for (int i = size; i < varIndex; i++) {
                            paramList.add(new AsmMethodParam(" ", varDesc, varSignature));
                        }
                        paramList.set(varIndex - 1, new AsmMethodParam(varName, varDesc, varSignature));
                    }
                    paramList.set(varIndex - 1, new AsmMethodParam(varName, varDesc, varSignature));
                }
            };
        }

        // 返回的List中参数列表可能会比方法参数量多，因为方法内的临时变量也会存入list中， 所以需要list的元素集合比方法的参数多
        static Map<String, AsmMethodBean> getMethodParamNames(Map<String, AsmMethodBean> map, Class clazz) {
            String n = clazz.getName();
            byte[] bs = RedkaleClassLoader.getDynClassBytes(n);
            if (bs == null) {
                InputStream in = clazz.getResourceAsStream(n.substring(n.lastIndexOf('.') + 1) + ".class");
                if (in == null) {
                    return map;
                }
                try {
                    bs = Utility.readBytesThenClose(in);
                } catch (Exception e) {
                    // do nothing
                }
            }
            try {
                new ClassReader(bs).accept(new MethodParamClassVisitor(Opcodes.ASM6, clazz, map), 0);
            } catch (Exception e) {
                e.printStackTrace();
                // do nothing
            }
            Class superClass = clazz.getSuperclass();
            if (superClass == null || superClass == Object.class) { // 接口的getSuperclass为null
                return map;
            }
            return getMethodParamNames(map, superClass);
        }
    }
}
