/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.lang.reflect.*;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;
import java.util.concurrent.*;
import java.util.function.*;
import org.redkale.annotation.ConstructorParameters;
import org.redkale.asm.*;
import static org.redkale.asm.Opcodes.*;
import org.redkale.asm.Type;

/**
 * 实现一个类的构造方法。 代替低效的反射实现方式。 不支持数组类。 常见的无参数的构造函数类都可以自动生成Creator， 对应自定义的类可以提供一个静态构建Creator方法。 例如:
 *
 * <blockquote>
 *
 * <pre>
 * public class Record {
 *
 *    private final int id;
 *
 *    private String name;
 *
 *    Record(int id, String name) {
 *        this.id = id;
 *        this.name = name;
 *    }
 *
 *    private static Creator createCreator() {
 *        return new Creator&lt;Record&gt;() {
 *            &#64;Override
 *            &#64;ConstructorParameters({"id", "name"})
 *            public Record create(Object... params) {
 *                if(params[0] == null) params[0] = 0;
 *                return new Record((Integer) params[0], (String) params[1]);
 *            }
 *         };
 *    }
 * }
 * </pre>
 *
 * </blockquote>
 *
 * 或者:
 *
 * <blockquote>
 *
 * <pre>
 * public class Record {
 *
 *    private final int id;
 *
 *    private String name;
 *
 *    &#64;ConstructorParameters({"id", "name"})
 *    public Record(int id, String name) {
 *        this.id = id;
 *        this.name = name;
 *    }
 * }
 * </pre>
 *
 * </blockquote>
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <T> 构建对象的数据类型
 */
public interface Creator<T> {

    /**
     * 创建对象
     *
     * @param params 构造函数的参数
     * @return 构建的对象
     */
    public T create(Object... params);

    /**
     * 参数类型数组
     *
     * @since 2.8.0
     * @return 参数类型数组
     */
    default Class[] paramTypes() {
        return new Class[0];
    }

    /**
     * 创建指定类型对象数组的IntFunction
     *
     * @param <T> 泛型
     * @param type 类型
     * @return IntFunction
     */
    public static <T> IntFunction<T[]> funcArray(final Class<T> type) {
        return Inners.CreatorInner.arrayCacheMap.computeIfAbsent(type, Inners.CreatorInner::createArrayFunction);
    }

    public static IntFunction<String[]> funcStringArray() {
        return Inners.CreatorInner.stringFuncArray;
    }

    public static <T> Creator<T> load(Class<T> clazz) {
        return Inners.CreatorInner.creatorCacheMap.computeIfAbsent(clazz, v -> create(clazz));
    }

    public static <T> Creator<T> load(Class<T> clazz, int paramCount) {
        if (paramCount < 0) {
            return Inners.CreatorInner.creatorCacheMap.computeIfAbsent(clazz, v -> create(clazz));
        } else {
            return Inners.CreatorInner.creatorCacheMap2.computeIfAbsent(
                    clazz.getName() + "-" + paramCount, v -> create(clazz, paramCount));
        }
    }

    public static <T> Creator<T> register(Class<T> clazz, final Supplier<T> supplier) {
        Creator<T> creator = (Object... params) -> supplier.get();
        Inners.CreatorInner.creatorCacheMap.put(clazz, creator);
        return creator;
    }

    public static <T> Creator<T> register(final LambdaSupplier<T> supplier) {
        Creator<T> creator = (Object... params) -> supplier.get();
        Inners.CreatorInner.creatorCacheMap.put(LambdaSupplier.readClass(supplier), creator);
        return creator;
    }

    /**
     * 创建指定大小的对象数组
     *
     * @param <T> 泛型
     * @param type 类型
     * @param size 数组大小
     * @return 数组
     */
    public static <T> T[] newArray(final Class<T> type, final int size) {
        if (type == int.class) {
            return (T[]) (Object) new int[size];
        }
        if (type == byte.class) {
            return (T[]) (Object) new byte[size];
        }
        if (type == long.class) {
            return (T[]) (Object) new long[size];
        }
        if (type == String.class) {
            return (T[]) new String[size];
        }
        if (type == Object.class) {
            return (T[]) new Object[size];
        }
        if (type == boolean.class) {
            return (T[]) (Object) new boolean[size];
        }
        if (type == short.class) {
            return (T[]) (Object) new short[size];
        }
        if (type == char.class) {
            return (T[]) (Object) new char[size];
        }
        if (type == float.class) {
            return (T[]) (Object) new float[size];
        }
        if (type == double.class) {
            return (T[]) (Object) new double[size];
        }
        return funcArray(type).apply(size);
    }

    /**
     * 根据Supplier生产Creator
     *
     * @param <T> 构建类的数据类型
     * @param supplier Supplier
     * @return Creator对象
     */
    public static <T> Creator<T> create(final Supplier<T> supplier) {
        Objects.requireNonNull(supplier);
        return (Object... params) -> supplier.get();
    }

    /**
     * 根据Function生产Creator
     *
     * @param <T> 构建类的数据类型
     * @param func Function
     * @return Creator对象
     */
    public static <T> Creator create(final Function<Object[], T> func) {
        Objects.requireNonNull(func);
        return (Object... params) -> func.apply(params);
    }

    /**
     * 根据指定的class采用ASM技术生产Creator。
     *
     * @param <T> 构建类的数据类型
     * @param clazz 构建类
     * @return Creator对象
     */
    @SuppressWarnings("unchecked")
    public static <T> Creator<T> create(Class<T> clazz) {
        return create(clazz, -1);
    }

    /**
     * 根据指定的class采用ASM技术生产Creator。
     *
     * @param <T> 构建类的数据类型
     * @param clazz 构建类
     * @param paramCount 参数个数
     * @return Creator对象
     * @since 2.8.0
     */
    @SuppressWarnings("unchecked")
    public static <T> Creator<T> create(Class<T> clazz, int paramCount) {
        if (List.class.isAssignableFrom(clazz)
                && (clazz.isAssignableFrom(ArrayList.class)
                        || clazz.getName().startsWith("java.util.Collections")
                        || clazz.getName().startsWith("java.util.ImmutableCollections")
                        || clazz.getName().startsWith("java.util.Arrays"))) {
            clazz = (Class<T>) ArrayList.class;
        } else if (Map.class.isAssignableFrom(clazz)
                && (clazz.isAssignableFrom(HashMap.class)
                        || clazz.getName().startsWith("java.util.Collections")
                        || clazz.getName().startsWith("java.util.ImmutableCollections"))) {
            clazz = (Class<T>) HashMap.class;
        } else if (Set.class.isAssignableFrom(clazz)
                && (clazz.isAssignableFrom(HashSet.class)
                        || clazz.getName().startsWith("java.util.Collections")
                        || clazz.getName().startsWith("java.util.ImmutableCollections"))) {
            clazz = (Class<T>) HashSet.class;
        } else if (Map.class.isAssignableFrom(clazz) && clazz.isAssignableFrom(ConcurrentHashMap.class)) {
            clazz = (Class<T>) ConcurrentHashMap.class;
        } else if (Deque.class.isAssignableFrom(clazz)
                && (clazz.isAssignableFrom(ArrayDeque.class)
                        || clazz.getName().startsWith("java.util.Collections")
                        || clazz.getName().startsWith("java.util.ImmutableCollections"))) {
            clazz = (Class<T>) ArrayDeque.class;
        } else if (Collection.class.isAssignableFrom(clazz) && clazz.isAssignableFrom(ArrayList.class)) {
            clazz = (Class<T>) ArrayList.class;
        } else if (Map.Entry.class.isAssignableFrom(clazz)
                && (Modifier.isInterface(clazz.getModifiers())
                        || Modifier.isAbstract(clazz.getModifiers())
                        || !Modifier.isPublic(clazz.getModifiers()))) {
            clazz = (Class<T>) AbstractMap.SimpleEntry.class;
        } else if (Iterable.class == clazz) {
            clazz = (Class<T>) ArrayList.class;
        } else if (CompletionStage.class.isAssignableFrom(clazz) && clazz.isAssignableFrom(CompletableFuture.class)) {
            clazz = (Class<T>) CompletableFuture.class;
        } else if (Future.class.isAssignableFrom(clazz) && clazz.isAssignableFrom(CompletableFuture.class)) {
            clazz = (Class<T>) CompletableFuture.class;
        }
        if (paramCount < 0) {
            Creator creator = Inners.CreatorInner.creatorCacheMap.get(clazz);
            if (creator != null) {
                return creator;
            }
        }
        if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
            throw new RedkaleException("[" + clazz + "] is a interface or abstract class, cannot create it's Creator.");
        }
        for (final Method method : clazz.getDeclaredMethods()) { // 查找类中是否存在提供创建Creator实例的静态方法
            if (!Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (method.getParameterTypes().length != 0) {
                continue;
            }
            if (method.getReturnType() != Creator.class) {
                continue;
            }
            try {
                method.setAccessible(true);
                Creator<T> c = (Creator) method.invoke(null);
                if (c != null) {
                    RedkaleClassLoader.putReflectionDeclaredMethods(clazz.getName());
                    RedkaleClassLoader.putReflectionMethod(clazz.getName(), method);
                    return c;
                }
            } catch (Exception e) {
                throw new RedkaleException(e);
            }
        }
        final String supDynName = Creator.class.getName().replace('.', '/');
        final String interName = clazz.getName().replace('.', '/');
        final String interDesc = Type.getDescriptor(clazz);
        RedkaleClassLoader loader =
                RedkaleClassLoader.getRedkaleClassLoader();
        final String newDynName = "org/redkaledyn/creator/_Dyn" + Creator.class.getSimpleName() + "__"
                + clazz.getName().replace('.', '_').replace('$', '_') + (paramCount < 0 ? "" : ("_" + paramCount));
        try {
            Class clz = RedkaleClassLoader.findDynClass(newDynName.replace('/', '.'));
            return (Creator) (clz == null ? loader.loadClass(newDynName.replace('/', '.')) : clz)
                    .getDeclaredConstructor()
                    .newInstance();
        } catch (Throwable ex) {
            // do nothing
        }

        Constructor<T> constructor0 = null;
        SimpleEntry<String, Class>[] constructorParameters0 = null; // 构造函数的参数

        if (constructor0 == null) { // 1、查找public的空参数构造函数
            for (Constructor c : clazz.getConstructors()) {
                int cc = c.getParameterCount();
                if (cc == 0 && (paramCount < 0 || cc == paramCount)) {
                    constructor0 = c;
                    constructorParameters0 = new SimpleEntry[0];
                    break;
                }
            }
        }
        if (constructor0 == null) { // 2、查找public带ConstructorParameters注解的构造函数
            for (Constructor c : clazz.getConstructors()) {
                ConstructorParameters cp = (ConstructorParameters) c.getAnnotation(ConstructorParameters.class);
                if (cp == null) {
                    continue;
                }
                int cc = c.getParameterCount();
                SimpleEntry<String, Class>[] fields = Inners.CreatorInner.getConstructorField(clazz, cc, cp.value());
                if (fields != null && (paramCount < 0 || cc == paramCount)) {
                    constructor0 = c;
                    constructorParameters0 = fields;
                    break;
                }
            }
        }
        if (constructor0 == null) { // 3、查找public且不带ConstructorParameters注解的构造函数
            List<Constructor> cs = new ArrayList<>();
            for (Constructor c : clazz.getConstructors()) {
                if (c.getAnnotation(ConstructorParameters.class) != null) {
                    continue;
                }
                if (c.getParameterCount() < 1) {
                    continue;
                }
                cs.add(c);
            }
            // 优先参数最多的构造函数
            cs.sort((o1, o2) -> o2.getParameterCount() - o1.getParameterCount());
            for (Constructor c : cs) {
                int cc = c.getParameterCount();
                SimpleEntry<String, Class>[] fields =
                        Inners.CreatorInner.getConstructorField(clazz, cc, Type.getConstructorDescriptor(c));
                if (fields != null && (paramCount < 0 || cc == paramCount)) {
                    constructor0 = c;
                    constructorParameters0 = fields;
                    break;
                }
            }
            if (constructor0 == null && paramCount == 1) {
                for (Constructor c : cs) {
                    int cc = c.getParameterCount();
                    if (cc == 1 && c.getParameterTypes()[0] == Object[].class) {
                        constructor0 = c;
                        constructorParameters0 = new SimpleEntry[1];
                        break;
                    }
                }
            }
        }
        if (constructor0 == null) { // 4、查找非private带ConstructorParameters的构造函数
            for (Constructor c : clazz.getDeclaredConstructors()) {
                if (Modifier.isPublic(c.getModifiers()) || Modifier.isPrivate(c.getModifiers())) {
                    continue;
                }
                ConstructorParameters cp = (ConstructorParameters) c.getAnnotation(ConstructorParameters.class);
                if (cp == null) {
                    continue;
                }
                int cc = c.getParameterCount();
                SimpleEntry<String, Class>[] fields = Inners.CreatorInner.getConstructorField(clazz, cc, cp.value());
                if (fields != null && (paramCount < 0 || cc == paramCount)) {
                    constructor0 = c;
                    constructorParameters0 = fields;
                    break;
                }
            }
        }
        if (constructor0 == null) { // 5、查找非private且不带ConstructorParameters的构造函数
            List<Constructor> cs = new ArrayList<>();
            for (Constructor c : clazz.getDeclaredConstructors()) {
                if (Modifier.isPublic(c.getModifiers()) || Modifier.isPrivate(c.getModifiers())) {
                    continue;
                }
                if (c.getAnnotation(ConstructorParameters.class) != null) {
                    continue;
                }
                if (c.getParameterCount() < 1) {
                    continue;
                }
                cs.add(c);
            }
            // 优先参数最多的构造函数
            cs.sort((o1, o2) -> o2.getParameterCount() - o1.getParameterCount());
            for (Constructor c : cs) {
                int cc = c.getParameterCount();
                SimpleEntry<String, Class>[] fields =
                        Inners.CreatorInner.getConstructorField(clazz, cc, Type.getConstructorDescriptor(c));
                if (fields != null && (paramCount < 0 || cc == paramCount)) {
                    constructor0 = c;
                    constructorParameters0 = fields;
                    break;
                }
            }
        }
        final Constructor<T> constructor = constructor0;
        final SimpleEntry<String, Class>[] constructorParameters = constructorParameters0;
        if (constructor == null || constructorParameters == null) {
            throw new RedkaleException(
                    "[" + clazz + "] have no public or ConstructorParameters-Annotation constructor.");
        }

        // -------------------------------------------------------------
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        FieldVisitor fv;
        MethodVisitor mv;
        AnnotationVisitor av0;
        cw.visit(
                V11,
                ACC_PUBLIC + ACC_FINAL + ACC_SUPER,
                newDynName,
                "Ljava/lang/Object;L" + supDynName + "<" + interDesc + ">;",
                "java/lang/Object",
                new String[] {supDynName});

        { // Creator自身的构造方法
            mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        { // paramTypes 方法
            mv = cw.visitMethod(ACC_PUBLIC, "paramTypes", "()[Ljava/lang/Class;", null, null);
            int paramLen = constructorParameters.length;
            Asms.visitInsn(mv, paramLen);
            mv.visitTypeInsn(ANEWARRAY, "java/lang/Class");
            for (int i = 0; i < constructorParameters.length; i++) {
                mv.visitInsn(DUP);
                Asms.visitInsn(mv, i);
                if (constructorParameters[i] == null) {
                    mv.visitLdcInsn(Type.getType("[Ljava/lang/Object;"));
                } else {
                    Asms.visitFieldInsn(mv, constructorParameters[i].getValue());
                }
                mv.visitInsn(AASTORE);
            }
            mv.visitInsn(ARETURN);
            mv.visitMaxs(4, 1);
            mv.visitEnd();
        }
        { // create 方法
            mv = cw.visitMethod(
                    ACC_PUBLIC + ACC_VARARGS, "create", "([Ljava/lang/Object;)L" + interName + ";", null, null);
            if (constructorParameters.length > 0 && constructorParameters[0] != null) {
                av0 = mv.visitAnnotation(Type.getDescriptor(ConstructorParameters.class), true);
                AnnotationVisitor av1 = av0.visitArray("value");
                for (SimpleEntry<String, Class> n : constructorParameters) {
                    av1.visit(null, n.getKey());
                }
                av1.visitEnd();
                av0.visitEnd();
            }
            // 有Primitive数据类型且值为null的参数需要赋默认值
            for (int i = 0; i < constructorParameters.length; i++) {
                if (constructorParameters[i] == null) {
                    continue;
                }
                final Class pt = constructorParameters[i].getValue();
                if (!pt.isPrimitive()) {
                    continue;
                }
                mv.visitVarInsn(ALOAD, 1);
                Asms.visitInsn(mv, i);
                mv.visitInsn(AALOAD);
                Label lab = new Label();
                mv.visitJumpInsn(IFNONNULL, lab);
                mv.visitVarInsn(ALOAD, 1);
                Asms.visitInsn(mv, i);
                if (pt == int.class) {
                    mv.visitInsn(ICONST_0);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                } else if (pt == long.class) {
                    mv.visitInsn(LCONST_0);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
                } else if (pt == boolean.class) {
                    mv.visitFieldInsn(GETSTATIC, "java/lang/Boolean", "FALSE", "Ljava/lang/Boolean;");
                } else if (pt == short.class) {
                    mv.visitInsn(ICONST_0);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false);
                } else if (pt == float.class) {
                    mv.visitInsn(FCONST_0);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
                } else if (pt == byte.class) {
                    mv.visitInsn(ICONST_0);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
                } else if (pt == double.class) {
                    mv.visitInsn(DCONST_0);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
                } else if (pt == char.class) {
                    mv.visitInsn(ICONST_0);
                    mv.visitMethodInsn(
                            INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false);
                }
                mv.visitInsn(AASTORE);
                mv.visitLabel(lab);
            }
            mv.visitTypeInsn(NEW, interName);
            mv.visitInsn(DUP);
            // ---------------------------------------
            for (int i = 0; i < constructorParameters.length; i++) {
                if (constructorParameters[i] == null) {
                    mv.visitVarInsn(ALOAD, 1);
                    break;
                }
                mv.visitVarInsn(ALOAD, 1);
                Asms.visitInsn(mv, i);
                mv.visitInsn(AALOAD);
                final Class ct = constructorParameters[i].getValue();
                if (ct.isPrimitive()) {
                    final Class bigct = TypeToken.primitiveToWrapper(ct);
                    mv.visitTypeInsn(CHECKCAST, bigct.getName().replace('.', '/'));
                    try {
                        Method pm = bigct.getMethod(ct.getSimpleName() + "Value");
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL,
                                bigct.getName().replace('.', '/'),
                                pm.getName(),
                                Type.getMethodDescriptor(pm),
                                false);
                    } catch (Exception ex) {
                        throw new RedkaleException(ex); // 不可能会发生
                    }
                } else {
                    mv.visitTypeInsn(CHECKCAST, ct.getName().replace('.', '/'));
                }
            }
            // ---------------------------------------
            mv.visitMethodInsn(INVOKESPECIAL, interName, "<init>", Type.getConstructorDescriptor(constructor), false);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(3, 2);
            mv.visitEnd();
        }
        { // 虚拟 create 方法
            mv = cw.visitMethod(
                    ACC_PUBLIC + ACC_BRIDGE + ACC_VARARGS + ACC_SYNTHETIC,
                    "create",
                    "([Ljava/lang/Object;)Ljava/lang/Object;",
                    null,
                    null);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, "create", "([Ljava/lang/Object;)" + interDesc, false);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(2, 2);
            mv.visitEnd();
        }
        cw.visitEnd();

        byte[] bytes = cw.toByteArray();
        try {
            Class newClazz = loader.loadClass(newDynName.replace('/', '.'), bytes);
            RedkaleClassLoader.putDynClass(newDynName.replace('/', '.'), bytes, newClazz);
            RedkaleClassLoader.putReflectionDeclaredConstructors(newClazz, newDynName.replace('/', '.'));
            return (Creator) newClazz.getDeclaredConstructor().newInstance();
        } catch (Exception ex) {
            throw new RedkaleException(ex);
        }
    }
}
