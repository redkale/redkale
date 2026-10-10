/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import static java.lang.classfile.ClassFile.*;
import static java.lang.constant.ConstantDescs.*;

import java.lang.classfile.*;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.attribute.SignatureAttribute;
import java.lang.constant.*;
import java.lang.invoke.MethodType;
import java.lang.reflect.*;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;
import java.util.concurrent.*;
import java.util.function.*;
import org.redkale.annotation.ConstructorParameters;

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
     * 根据指定的class采用Classfile API技术生产Creator。
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
     * 根据指定的class采用Classfile API技术生产Creator。
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

        final String interDesc = clazz.descriptorString();
        RedkaleClassLoader classLoader = RedkaleClassLoader.currentClassLoader();
        final String newDynName = "org/redkaledyn/creator/_Dyn" + Creator.class.getSimpleName() + "__"
                + clazz.getName().replace('.', '_').replace('$', '_') + (paramCount < 0 ? "" : ("_" + paramCount));
        try {
            return (Creator) classLoader
                    .loadClass(newDynName.replace('/', '.'))
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
                SimpleEntry<String, Class>[] fields = Inners.CreatorInner.getConstructorField(
                        clazz,
                        cc,
                        MethodType.methodType(void.class, c.getParameterTypes()).descriptorString());
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
                SimpleEntry<String, Class>[] fields = Inners.CreatorInner.getConstructorField(
                        clazz,
                        cc,
                        MethodType.methodType(void.class, c.getParameterTypes()).descriptorString());
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
        final ClassDesc targetDesc = ClassDesc.ofDescriptor(interDesc);
        final ClassDesc dynDesc = ClassDesc.ofInternalName(newDynName);
        final MethodTypeDesc createDesc = MethodTypeDesc.of(targetDesc, CD_Object.arrayType());
        final MethodTypeDesc constructorDesc =
                MethodTypeDesc.ofDescriptor(MethodType.methodType(void.class, constructor.getParameterTypes())
                        .descriptorString());
        byte[] bytes = ClassFile.of().build(dynDesc, cb -> {
            cb.withVersion(JAVA_11_VERSION, 0)
                    .withFlags(ACC_PUBLIC | ACC_FINAL | ACC_SUPER)
                    .withSuperclass(CD_Object)
                    .withInterfaceSymbols(ClassDesc.ofInternalName(supDynName));
            cb.with(SignatureAttribute.of(
                    ClassSignature.parseFrom("Ljava/lang/Object;L" + supDynName + "<" + interDesc + ">;")));
            cb.withMethodBody(
                    "<init>",
                    MethodTypeDesc.of(CD_void),
                    ACC_PUBLIC,
                    code -> code.aload(0)
                            .invokespecial(CD_Object, "<init>", MethodTypeDesc.of(CD_void))
                            .return_());
            cb.withMethodBody("paramTypes", MethodTypeDesc.of(CD_Class.arrayType()), ACC_PUBLIC, code -> {
                code.loadConstant(constructorParameters.length).anewarray(CD_Class);
                for (int i = 0; i < constructorParameters.length; i++) {
                    code.dup().loadConstant(i);
                    if (constructorParameters[i] == null) {
                        code.ldc(CD_Object.arrayType());
                    } else {
                        Class<?> type = constructorParameters[i].getValue();
                        if (type.isPrimitive()) {
                            code.getstatic(
                                    ClassDesc.ofDescriptor(
                                            TypeToken.primitiveToWrapper(type).descriptorString()),
                                    "TYPE",
                                    CD_Class);
                        } else {
                            code.ldc(ClassDesc.ofDescriptor(type.descriptorString()));
                        }
                    }
                    code.aastore();
                }
                code.areturn();
            });
            cb.withMethod("create", createDesc, ACC_PUBLIC | ACC_VARARGS, mb -> {
                if (constructorParameters.length > 0 && constructorParameters[0] != null) {
                    AnnotationValue[] names = Arrays.stream(constructorParameters)
                            .map(n -> AnnotationValue.ofString(n.getKey()))
                            .toArray(AnnotationValue[]::new);
                    mb.with(RuntimeVisibleAnnotationsAttribute.of(Annotation.of(
                            ClassDesc.of(ConstructorParameters.class.getName()),
                            AnnotationElement.ofArray("value", names))));
                }
                mb.withCode(code -> {
                    // 基本类型参数为 null 时，填入对应的默认值。
                    for (int i = 0; i < constructorParameters.length; i++) {
                        if (constructorParameters[i] == null) {
                            continue;
                        }
                        Class<?> type = constructorParameters[i].getValue();
                        if (!type.isPrimitive()) {
                            continue;
                        }
                        ClassDesc primitiveDesc = ClassDesc.ofDescriptor(type.descriptorString());
                        ClassDesc wrapperDesc = ClassDesc.ofDescriptor(
                                TypeToken.primitiveToWrapper(type).descriptorString());
                        Label present = code.newLabel();
                        code.aload(1).loadConstant(i).aaload().ifnonnull(present);
                        code.aload(1).loadConstant(i);
                        if (type == boolean.class) {
                            code.getstatic(wrapperDesc, "FALSE", wrapperDesc);
                        } else {
                            if (type == long.class) {
                                code.lconst_0();
                            } else if (type == float.class) {
                                code.fconst_0();
                            } else if (type == double.class) {
                                code.dconst_0();
                            } else {
                                code.iconst_0();
                            }
                            code.invokestatic(wrapperDesc, "valueOf", MethodTypeDesc.of(wrapperDesc, primitiveDesc));
                        }
                        code.aastore().labelBinding(present);
                    }
                    code.new_(targetDesc).dup();
                    for (int i = 0; i < constructorParameters.length; i++) {
                        if (constructorParameters[i] == null) {
                            code.aload(1);
                            break;
                        }
                        code.aload(1).loadConstant(i).aaload();
                        Class<?> type = constructorParameters[i].getValue();
                        ClassDesc typeDesc = ClassDesc.ofDescriptor(type.descriptorString());
                        if (type.isPrimitive()) {
                            ClassDesc wrapperDesc = ClassDesc.ofDescriptor(
                                    TypeToken.primitiveToWrapper(type).descriptorString());
                            code.checkcast(wrapperDesc)
                                    .invokevirtual(
                                            wrapperDesc, type.getSimpleName() + "Value", MethodTypeDesc.of(typeDesc));
                        } else {
                            code.checkcast(typeDesc);
                        }
                    }
                    code.invokespecial(targetDesc, "<init>", constructorDesc).areturn();
                });
            });
            MethodTypeDesc bridgeDesc = MethodTypeDesc.of(CD_Object, CD_Object.arrayType());
            if (!createDesc.equals(bridgeDesc)) {
                cb.withMethodBody(
                        "create",
                        bridgeDesc,
                        ACC_PUBLIC | ACC_BRIDGE | ACC_VARARGS | ACC_SYNTHETIC,
                        code -> code.aload(0)
                                .aload(1)
                                .invokevirtual(dynDesc, "create", createDesc)
                                .areturn());
            }
        });
        try {
            Class newClazz = classLoader.loadClass(newDynName.replace('/', '.'), bytes);
            RedkaleClassLoader.putReflectionDeclaredConstructors(newClazz, newDynName.replace('/', '.'));
            return (Creator) newClazz.getDeclaredConstructor().newInstance();
        } catch (Exception ex) {
            throw new RedkaleException(ex);
        }
    }
}
