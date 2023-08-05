package org.redkale.util;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.*;
import static org.redkale.asm.ClassWriter.COMPUTE_FRAMES;
import org.redkale.asm.*;
import static org.redkale.asm.Opcodes.*;
import org.redkale.asm.Type;

/**
 * JavaBean类对象的拷贝，相同的字段名会被拷贝 <br>
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <D> 目标对象的数据类型
 * @param <S> 源对象的数据类型
 */
public interface Reproduce<D, S> extends BiFunction<D, S, D> {

    /**
     * 将源对象字段复制到目标对象
     *
     * @param dest 目标对象
     * @param src  源对象
     *
     * @return 目标对象
     */
    @Override
    public D apply(D dest, S src);

    /**
     * 将源对象字段复制到目标对象
     *
     * @param <D>  目标类泛型
     * @param <S>  源类泛型
     * @param dest 目标对象
     * @param src  源对象
     *
     * @return 目标对象
     */
    public static <D, S> D copy(final D dest, final S src) {
        if (src == null || dest == null) {
            return null;
        }
        Class<D> destClass = (Class<D>) dest.getClass();
        Creator<D> creator = Creator.load(destClass);
        return load(destClass, (Class<S>) src.getClass()).apply(creator.create(), src);
    }

    /**
     * 将源对象字段复制到目标对象
     *
     * @param <D>       目标类泛型
     * @param <S>       源类泛型
     * @param destClass 目标类名
     * @param src       源对象
     *
     * @return 目标对象
     */
    public static <D, S> D copy(final Class<D> destClass, final S src) {
        if (src == null) {
            return null;
        }
        Creator<D> creator = Creator.load(destClass);
        return load(destClass, (Class<S>) src.getClass()).apply(creator.create(), src);
    }

    /**
     * 创建源类到目标类的复制器并缓存
     *
     * @param <D>       目标类泛型
     * @param <S>       源类泛型
     * @param destClass 目标类名
     * @param srcClass  源类名
     *
     * @return 复制器
     */
    public static <D, S> Reproduce<D, S> load(final Class<D> destClass, final Class<S> srcClass) {
        if (destClass == srcClass) {
            return ReproduceInner.reproduceOneCaches
                .computeIfAbsent(destClass, v -> create(destClass, srcClass));
        } else {
            return ReproduceInner.reproduceTwoCaches
                .computeIfAbsent(destClass, t -> new ConcurrentHashMap<>())
                .computeIfAbsent(srcClass, v -> create(destClass, srcClass));
        }
    }

    /**
     * 创建源类到目标类的复制器
     *
     * @param <D>       目标类泛型
     * @param <S>       源类泛型
     * @param destClass 目标类名
     * @param srcClass  源类名
     *
     * @return 复制器
     */
    public static <D, S> Reproduce<D, S> create(final Class<D> destClass, final Class<S> srcClass) {
        return create(destClass, srcClass, (BiPredicate) null, (Map<String, String>) null);
    }

    /**
     * 创建源类到目标类的复制器
     *
     * @param <D>       目标类泛型
     * @param <S>       源类泛型
     * @param destClass 目标类名
     * @param srcClass  源类名
     * @param names     源字段名与目标字段名的映射关系
     *
     * @return 复制器
     */
    public static <D, S> Reproduce<D, S> create(final Class<D> destClass, final Class<S> srcClass, final Map<String, String> names) {
        return create(destClass, srcClass, (BiPredicate) null, names);
    }

    /**
     * 创建源类到目标类的复制器
     *
     * @param <D>                目标类泛型
     * @param <S>                源类泛型
     * @param destClass          目标类名
     * @param srcClass           源类名
     * @param srcColumnPredicate 需复制的字段名判断期
     *
     * @return 复制器
     */
    @SuppressWarnings("unchecked")
    public static <D, S> Reproduce<D, S> create(final Class<D> destClass, final Class<S> srcClass, final Predicate<String> srcColumnPredicate) {
        return create(destClass, srcClass, (sc, m) -> srcColumnPredicate.test(m), (Map<String, String>) null);
    }

    /**
     * 创建源类到目标类的复制器
     *
     * @param <D>                目标类泛型
     * @param <S>                源类泛型
     * @param destClass          目标类名
     * @param srcClass           源类名
     * @param srcColumnPredicate 需复制的字段名判断期
     * @param names              源字段名与目标字段名的映射关系
     *
     * @return 复制器
     */
    @SuppressWarnings("unchecked")
    public static <D, S> Reproduce<D, S> create(final Class<D> destClass, final Class<S> srcClass, final Predicate<String> srcColumnPredicate, final Map<String, String> names) {
        return create(destClass, srcClass, (sc, m) -> srcColumnPredicate.test(m), names);
    }

    /**
     * 创建源类到目标类的复制器
     *
     * @param <D>                目标类泛型
     * @param <S>                源类泛型
     * @param destClass          目标类名
     * @param srcClass           源类名
     * @param srcColumnPredicate 需复制的字段名判断期
     *
     * @return 复制器
     */
    @SuppressWarnings("unchecked")
    public static <D, S> Reproduce<D, S> create(final Class<D> destClass, final Class<S> srcClass, final BiPredicate<java.lang.reflect.AccessibleObject, String> srcColumnPredicate) {
        return create(destClass, srcClass, srcColumnPredicate, (Map<String, String>) null);
    }

    /**
     * 创建源类到目标类的复制器
     *
     * @param <D>                目标类泛型
     * @param <S>                源类泛型
     * @param destClass          目标类名
     * @param srcClass           源类名
     * @param srcColumnPredicate 需复制的字段名判断期
     * @param names              源字段名与目标字段名的映射关系
     *
     * @return 复制器
     */
    @SuppressWarnings("unchecked")
    public static <D, S> Reproduce<D, S> create(final Class<D> destClass, final Class<S> srcClass, final BiPredicate<java.lang.reflect.AccessibleObject, String> srcColumnPredicate, final Map<String, String> names) {
        final boolean allowMapNull = !ConcurrentHashMap.class.isAssignableFrom(destClass);
        if (Map.class.isAssignableFrom(destClass) && Map.class.isAssignableFrom(srcClass)) {
            final Map names0 = names;
            if (srcColumnPredicate != null) {
                if (names != null) {
                    return (D dest, S src) -> {
                        Map d = (Map) dest;
                        ((Map) src).forEach((k, v) -> {
                            if (srcColumnPredicate.test(null, k.toString()) && (allowMapNull || v != null)) {
                                d.put(names0.getOrDefault(k, k), v);
                            }
                        });
                        return dest;
                    };
                } else {
                    return (D dest, S src) -> {
                        Map d = (Map) dest;
                        ((Map) src).forEach((k, v) -> {
                            if (srcColumnPredicate.test(null, k.toString()) && (allowMapNull || v != null)) {
                                d.put(k, v);
                            }
                        });
                        return dest;
                    };
                }
            } else if (names != null) {
                return (D dest, S src) -> {
                    Map d = (Map) dest;
                    ((Map) src).forEach((k, v) -> {
                        if (allowMapNull || v != null) {
                            d.put(names0.getOrDefault(k, k), v);
                        }
                    });
                    return dest;
                };
            }
            return new Reproduce<D, S>() {
                @Override
                public D apply(D dest, S src) {
                    if (allowMapNull) {
                        ((Map) dest).putAll((Map) src);
                    } else {
                        Map d = (Map) dest;
                        ((Map) src).forEach((k, v) -> {
                            if (v != null) {
                                d.put(names0.getOrDefault(k, k), v);
                            }
                        });
                    }
                    return dest;
                }
            };
        }
        // ------------------------------------------------------------------------------
        final boolean destIsMap = Map.class.isAssignableFrom(destClass);
        final boolean srcIsMap = Map.class.isAssignableFrom(srcClass);
        final String supDynName = Reproduce.class.getName().replace('.', '/');
        final String destClassName = destClass.getName().replace('.', '/');
        final String srcClassName = srcClass.getName().replace('.', '/');
        final String destDesc = Type.getDescriptor(destClass);
        final String srcDesc = Type.getDescriptor(srcClass);
        final ClassLoader loader = Thread.currentThread().getContextClassLoader();
        final String utilClassName = Utility.class.getName().replace('.', '/');
        final String newDynName = "org/redkaledyn/reproduce/_Dyn" + Reproduce.class.getSimpleName()
            + "__" + destClass.getName().replace('.', '_').replace('$', '_')
            + "__" + srcClass.getName().replace('.', '_').replace('$', '_');
        try {
            Class clz = RedkaleClassLoader.findDynClass(newDynName.replace('/', '.'));
            return (Reproduce) (clz == null ? loader.loadClass(newDynName.replace('/', '.')) : clz).getDeclaredConstructor().newInstance();
        } catch (Throwable ex) {
        }
        final Predicate<Class<?>> throwPredicate = e -> !RuntimeException.class.isAssignableFrom(e);
        // ------------------------------------------------------------------------------
        ClassWriter cw = new ClassWriter(COMPUTE_FRAMES);
        FieldVisitor fv;
        MethodVisitor mv;
        AnnotationVisitor av0;

        cw.visit(V11, ACC_PUBLIC + ACC_FINAL + ACC_SUPER, newDynName, "Ljava/lang/Object;L" + supDynName + "<" + destDesc + srcDesc + ">;", "java/lang/Object", new String[]{supDynName});

        { // 构造函数
            mv = (cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null));
            //mv.setDebug(true);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        if (srcIsMap) { //destClass不是Map
            {
                mv = (cw.visitMethod(ACC_PUBLIC, "apply", "(" + destDesc + srcDesc + ")" + destDesc, null, null));
                //mv.setDebug(true);
                mv.visitVarInsn(ALOAD, 2);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitInvokeDynamicInsn("accept",
                    "(" + destDesc + ")Ljava/util/function/BiConsumer;",
                    new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false),
                    new Object[]{Type.getType("(Ljava/lang/Object;Ljava/lang/Object;)V"), new Handle(Opcodes.H_INVOKESTATIC, newDynName, "lambda$0", "(" + destDesc + "Ljava/lang/Object;Ljava/lang/Object;)V", false), Type.getType("(Ljava/lang/Object;Ljava/lang/Object;)V")});
                mv.visitMethodInsn(srcClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL, srcClassName, "forEach", "(Ljava/util/function/BiConsumer;)V", srcClass.isInterface());
                mv.visitVarInsn(ALOAD, 1);
                mv.visitInsn(ARETURN);
                mv.visitMaxs(2, 3);
                mv.visitEnd();
            }
            {
                final Map<String, AccessibleObject> elements = new LinkedHashMap<>();
                for (java.lang.reflect.Field field : destClass.getFields()) {
                    if (Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    if (Modifier.isFinal(field.getModifiers())) {
                        continue;
                    }
                    if (!Modifier.isPublic(field.getModifiers())) {
                        continue;
                    }
                    final String sfname = field.getName();
                    if (srcColumnPredicate != null && !srcColumnPredicate.test(field, sfname)) {
                        continue;
                    }
                    final String dfname = names == null ? sfname : names.getOrDefault(sfname, sfname);
                    elements.put(dfname, field);
                }

                for (java.lang.reflect.Method setter : destClass.getMethods()) {
                    if (Modifier.isStatic(setter.getModifiers())) {
                        continue;
                    }
                    if (setter.getParameterTypes().length != 1) {
                        continue;
                    }
                    if (Utility.contains(setter.getExceptionTypes(), throwPredicate)) {
                        continue;  //setter方法带有非RuntimeException异常
                    }
                    if (!setter.getName().startsWith("set")) {
                        continue;
                    }
                    String sfname = Utility.readFieldName(setter.getName());
                    if (sfname.isEmpty()) {
                        continue;
                    }
                    if (srcColumnPredicate != null && !srcColumnPredicate.test(setter, sfname)) {
                        continue;
                    }
                    final String dfname = names == null ? sfname : names.getOrDefault(sfname, sfname);
                    elements.put(dfname, setter);
                }

                mv = cw.visitMethod(ACC_PRIVATE + ACC_STATIC + ACC_SYNTHETIC, "lambda$0", "(" + destDesc + "Ljava/lang/Object;Ljava/lang/Object;)V", null, null);
                Label goLabel = new Label();
                int i = 0;
                for (Map.Entry<String, AccessibleObject> en : elements.entrySet()) {
                    final int index = ++i;
                    final java.lang.reflect.Type fieldType = en.getValue() instanceof Field
                        ? ((Field) en.getValue()).getGenericType()
                        : ((Method) en.getValue()).getGenericParameterTypes()[0];
                    final Class fieldClass = en.getValue() instanceof Field
                        ? ((Field) en.getValue()).getType()
                        : ((Method) en.getValue()).getParameterTypes()[0];
                    final boolean primitive = fieldClass.isPrimitive();

                    mv.visitLdcInsn(en.getKey());
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
                    Label ifeq = index == elements.size() ? goLabel : new Label();
                    mv.visitJumpInsn(IFEQ, ifeq);
                    if (primitive) {
                        mv.visitVarInsn(ALOAD, 2);
                        mv.visitJumpInsn(IFNULL, ifeq);
                    }
                    mv.visitVarInsn(ALOAD, 0);

                    if (fieldClass == boolean.class) {
                        mv.visitFieldInsn(GETSTATIC, "java/lang/Boolean", "TYPE", "Ljava/lang/Class;");
                    } else if (fieldClass == byte.class) {
                        mv.visitFieldInsn(GETSTATIC, "java/lang/Byte", "TYPE", "Ljava/lang/Class;");
                    } else if (fieldClass == char.class) {
                        mv.visitFieldInsn(GETSTATIC, "java/lang/Character", "TYPE", "Ljava/lang/Class;");
                    } else if (fieldClass == short.class) {
                        mv.visitFieldInsn(GETSTATIC, "java/lang/Short", "TYPE", "Ljava/lang/Class;");
                    } else if (fieldClass == int.class) {
                        mv.visitFieldInsn(GETSTATIC, "java/lang/Integer", "TYPE", "Ljava/lang/Class;");
                    } else if (fieldClass == float.class) {
                        mv.visitFieldInsn(GETSTATIC, "java/lang/Float", "TYPE", "Ljava/lang/Class;");
                    } else if (fieldClass == long.class) {
                        mv.visitFieldInsn(GETSTATIC, "java/lang/Long", "TYPE", "Ljava/lang/Class;");
                    } else if (fieldClass == double.class) {
                        mv.visitFieldInsn(GETSTATIC, "java/lang/Double", "TYPE", "Ljava/lang/Class;");
                    } else {
                        mv.visitLdcInsn(Type.getType(Type.getDescriptor(fieldClass)));
                    }

                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitMethodInsn(INVOKESTATIC, utilClassName, "convertValue", "(Ljava/lang/reflect/Type;Ljava/lang/Object;)Ljava/lang/Object;", false);
                    if (fieldClass == boolean.class) {
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
                    } else if (fieldClass == byte.class) {
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Byte");
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B", false);
                    } else if (fieldClass == short.class) {
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Short");
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S", false);
                    } else if (fieldClass == char.class) {
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Character");
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C", false);
                    } else if (fieldClass == int.class) {
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                    } else if (fieldClass == float.class) {
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Float");
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false);
                    } else if (fieldClass == long.class) {
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Long");
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
                    } else if (fieldClass == double.class) {
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Double");
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
                    } else {
                        mv.visitTypeInsn(CHECKCAST, fieldClass.getName().replace('.', '/'));
                    }

                    if (en.getValue() instanceof Field) {
                        mv.visitFieldInsn(PUTFIELD, destClassName, en.getKey(), Type.getDescriptor(fieldClass));
                    } else {
                        Method setter = (Method) en.getValue();
                        mv.visitMethodInsn(destClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL, destClassName, setter.getName(), Type.getMethodDescriptor(setter), destClass.isInterface());
                    }
                    if (index == elements.size()) {
                        mv.visitLabel(goLabel);
                    } else {
                        mv.visitJumpInsn(GOTO, goLabel);
                        mv.visitLabel(ifeq);
                    }
                    mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                }

                mv.visitInsn(RETURN);
                mv.visitMaxs(3, 3);
                mv.visitEnd();
            }
        } else {
            mv = (cw.visitMethod(ACC_PUBLIC, "apply", "(" + destDesc + srcDesc + ")" + destDesc, null, null));
            //mv.setDebug(true);

            for (java.lang.reflect.Field field : srcClass.getFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (Modifier.isFinal(field.getModifiers())) {
                    continue;
                }
                if (!Modifier.isPublic(field.getModifiers())) {
                    continue;
                }
                final String sfname = field.getName();
                if (srcColumnPredicate != null && !srcColumnPredicate.test(field, sfname)) {
                    continue;
                }

                final String dfname = names == null ? sfname : names.getOrDefault(sfname, sfname);
                if (destIsMap) {
                    Class st = field.getType();
                    String td = Type.getDescriptor(st);
                    if (allowMapNull || st.isPrimitive()) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitLdcInsn(dfname);
                        mv.visitVarInsn(ALOAD, 2);
                        mv.visitFieldInsn(GETFIELD, srcClassName, sfname, td);
                        if (st == boolean.class) {
                            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                        } else if (st == byte.class) {
                            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
                        } else if (st == short.class) {
                            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false);
                        } else if (st == char.class) {
                            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false);
                        } else if (st == int.class) {
                            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                        } else if (st == float.class) {
                            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
                        } else if (st == long.class) {
                            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
                        } else if (st == double.class) {
                            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
                        }
                        mv.visitMethodInsn(destClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL, destClassName, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", destClass.isInterface());
                        mv.visitInsn(POP);
                    } else {
                        mv.visitVarInsn(ALOAD, 2);
                        mv.visitFieldInsn(GETFIELD, srcClassName, sfname, td);
                        mv.visitVarInsn(ASTORE, 3);
                        mv.visitVarInsn(ALOAD, 3);
                        Label ifLabel = new Label();
                        mv.visitJumpInsn(IFNULL, ifLabel);
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitLdcInsn(dfname);
                        mv.visitVarInsn(ALOAD, 3);
                        mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
                        mv.visitInsn(POP);
                        mv.visitLabel(ifLabel);
                        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                    }
                } else {
                    java.lang.reflect.Method setter = null;
                    try {
                        if (!field.getType().equals(destClass.getField(dfname).getType())) {
                            continue;
                        }
                    } catch (Exception e) {
                        try {
                            char[] cs = dfname.toCharArray();
                            cs[0] = Character.toUpperCase(cs[0]);
                            String dfname2 = new String(cs);
                            setter = destClass.getMethod("set" + dfname2, field.getType());
                        } catch (Exception e2) {
                            continue;
                        }
                    }
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitVarInsn(ALOAD, 2);
                    String td = Type.getDescriptor(field.getType());
                    mv.visitFieldInsn(GETFIELD, srcClassName, sfname, td);
                    if (setter == null) {
                        mv.visitFieldInsn(PUTFIELD, destClassName, dfname, td);
                    } else {
                        mv.visitMethodInsn(destClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL, destClassName, setter.getName(), Type.getMethodDescriptor(setter), destClass.isInterface());
                    }
                }
            }

            for (java.lang.reflect.Method getter : srcClass.getMethods()) {
                if (Modifier.isStatic(getter.getModifiers())) {
                    continue;
                }
                if (getter.getParameterTypes().length > 0) {
                    continue;
                }
                if ("getClass".equals(getter.getName())) {
                    continue;
                }
                if (Utility.contains(getter.getExceptionTypes(), throwPredicate)) {
                    continue;  //setter方法带有非RuntimeException异常
                }
                if (!getter.getName().startsWith("get") && !getter.getName().startsWith("is")) {
                    continue;
                }
                final String sfname = Utility.readFieldName(getter.getName());
                if (sfname.isEmpty()) {
                    continue;
                }
                if (srcColumnPredicate != null && !srcColumnPredicate.test(getter, sfname)) {
                    continue;
                }

                final String dfname = names == null ? sfname : names.getOrDefault(sfname, sfname);
                if (destIsMap) {
                    Class st = getter.getReturnType();
                    if (allowMapNull || st.isPrimitive()) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitLdcInsn(dfname);
                        mv.visitVarInsn(ALOAD, 2);
                        mv.visitMethodInsn(srcClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL, srcClassName, getter.getName(), Type.getMethodDescriptor(getter), srcClass.isInterface());
                        if (st == boolean.class) {
                            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                        } else if (st == byte.class) {
                            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
                        } else if (st == short.class) {
                            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false);
                        } else if (st == char.class) {
                            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false);
                        } else if (st == int.class) {
                            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                        } else if (st == float.class) {
                            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
                        } else if (st == long.class) {
                            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
                        } else if (st == double.class) {
                            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
                        }
                        mv.visitMethodInsn(destClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL, destClassName, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", destClass.isInterface());
                        mv.visitInsn(POP);
                    } else {
                        mv.visitVarInsn(ALOAD, 2);
                         mv.visitMethodInsn(srcClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL, srcClassName, getter.getName(), Type.getMethodDescriptor(getter), srcClass.isInterface());
                        mv.visitVarInsn(ASTORE, 3);
                        mv.visitVarInsn(ALOAD, 3);
                        Label ifLabel = new Label();
                        mv.visitJumpInsn(IFNULL, ifLabel);
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitLdcInsn(dfname);
                        mv.visitVarInsn(ALOAD, 3);
                        mv.visitMethodInsn(destClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL, destClassName, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", destClass.isInterface());
                        mv.visitInsn(POP);
                        mv.visitLabel(ifLabel);
                        mv.visitLineNumber(47, ifLabel);
                        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                    }
                } else {
                    java.lang.reflect.Method setter = null;
                    java.lang.reflect.Field srcField = null;
                    char[] cs = dfname.toCharArray();
                    cs[0] = Character.toUpperCase(cs[0]);
                    String dfname2 = new String(cs);
                    try {
                        setter = destClass.getMethod("set" + dfname2, getter.getReturnType());
                        if (Utility.contains(setter.getExceptionTypes(), throwPredicate)) {
                            continue;  //setter方法带有非RuntimeException异常
                        }
                    } catch (Exception e) {
                        try {
                            srcField = destClass.getField(dfname);
                            if (!getter.getReturnType().equals(srcField.getType())) {
                                continue;
                            }
                        } catch (Exception e2) {
                            continue;
                        }
                    }
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitMethodInsn(srcClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL, srcClassName, getter.getName(), Type.getMethodDescriptor(getter), srcClass.isInterface());
                    if (srcField == null) {
                        mv.visitMethodInsn(destClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL, destClassName, setter.getName(), Type.getMethodDescriptor(setter), destClass.isInterface());
                    } else {
                        mv.visitFieldInsn(PUTFIELD, destClassName, dfname, Type.getDescriptor(getter.getReturnType()));
                    }
                }
            }
            mv.visitVarInsn(ALOAD, 1);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(3, 3);
            mv.visitEnd();
        }
        {
            mv = (cw.visitMethod(ACC_PUBLIC + ACC_BRIDGE + ACC_SYNTHETIC, "apply", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", null, null));
            //mv.setDebug(true);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, destClassName);
            mv.visitVarInsn(ALOAD, 2);
            mv.visitTypeInsn(CHECKCAST, srcClassName);
            mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, "apply", "(" + destDesc + srcDesc + ")" + destDesc, false);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(3, 3);
            mv.visitEnd();
        }
        cw.visitEnd();
        // ------------------------------------------------------------------------------
        byte[] bytes = cw.toByteArray();
        Class<?> newClazz = new ClassLoader(loader) {
            public final Class<?> loadClass(String name, byte[] b) {
                return defineClass(name, b, 0, b.length);
            }
        }.loadClass(newDynName.replace('/', '.'), bytes);
        RedkaleClassLoader.putDynClass(newDynName.replace('/', '.'), bytes, newClazz);
        RedkaleClassLoader.putReflectionDeclaredConstructors(newClazz, newDynName.replace('/', '.'));
        try {
            return (Reproduce) newClazz.getDeclaredConstructor().newInstance();
        } catch (Exception ex) {
            throw new RedkaleException(ex);
        }
    }

    static class ReproduceInner {

        static final ConcurrentHashMap<Class, Reproduce> reproduceOneCaches = new ConcurrentHashMap();

        static final ConcurrentHashMap<Class, ConcurrentHashMap<Class, Reproduce>> reproduceTwoCaches = new ConcurrentHashMap();

    }

}
