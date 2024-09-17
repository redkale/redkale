/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import static org.redkale.asm.ClassWriter.COMPUTE_FRAMES;
import static org.redkale.asm.Opcodes.*;

/**
 * 获取泛型的Type类
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <T> 泛型
 */
@SuppressWarnings("unchecked")
public abstract class TypeToken<T> {

    private static final ReentrantLock syncLock = new ReentrantLock();

    private final Type type;

    public TypeToken() {
        type = ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
    }

    /**
     * 具体泛型类型
     *
     * @return 泛型
     */
    public final Type getType() {
        return type;
    }

    /**
     * 判断Type是否能确定最终的class， 是则返回true，存在通配符或者不确定类型则返回false。 例如: Map&#60; String, String &#62; 返回 ture; Map&#60; ? extends
     * Serializable, String &#62; 返回false;
     *
     * @param type Type对象
     * @return 是否可反解析
     */
    public static final boolean isClassType(final Type type) {
        if (type instanceof Class) {
            return true;
        }
        if (type instanceof WildcardType) {
            return false;
        }
        if (type instanceof TypeVariable) {
            return false;
        }
        if (type instanceof GenericArrayType) {
            return isClassType(((GenericArrayType) type).getGenericComponentType());
        }
        if (!(type instanceof ParameterizedType)) {
            return false; // 只能是null了
        }
        final ParameterizedType ptype = (ParameterizedType) type;
        if (ptype.getOwnerType() != null && !isClassType(ptype.getOwnerType())) {
            return false;
        }
        if (!isClassType(ptype.getRawType())) {
            return false;
        }
        for (Type t : ptype.getActualTypeArguments()) {
            if (!isClassType(t)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断泛型知否包含了不确定的数据类型
     *
     * @param type 类型
     * @return 是否含不确定类型
     */
    public static final boolean containsUnknownType(final Type type) {
        if (type == null) {
            return false;
        }
        if (type instanceof Class) {
            return false;
        }
        if (type instanceof WildcardType) {
            return true;
        }
        if (type instanceof TypeVariable) {
            return true;
        }
        if (type instanceof GenericArrayType) {
            return containsUnknownType(((GenericArrayType) type).getGenericComponentType());
        }
        if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            if (containsUnknownType(pt.getRawType())) {
                return true;
            }
            if (containsUnknownType(pt.getOwnerType())) {
                return true;
            }
            Type[] ts = pt.getActualTypeArguments();
            if (ts != null) {
                for (Type t : ts) {
                    if (containsUnknownType(t)) {
                        return true;
                    }
                }
            }
            return false;
        }
        return true;
    }

    /**
     * 获取type的主类
     *
     * @param type 类型
     * @return 主类
     */
    public static final Class typeToClass(final Type type) {
        if (type instanceof Class) {
            return (Class) type;
        }
        if (type instanceof WildcardType) {
            return null;
        }
        if (type instanceof TypeVariable) {
            return null;
        }
        if (type instanceof GenericArrayType) {
            return Creator.newArray(typeToClass(((GenericArrayType) type).getGenericComponentType()), 0)
                    .getClass();
        }
        if (!(type instanceof ParameterizedType)) {
            return null; // 只能是null了
        }
        Type owner = ((ParameterizedType) type).getOwnerType();
        Type raw = ((ParameterizedType) type).getRawType();
        // A$B<C> owner=A  raw=A$B, 所以内部类情况下使用owner是错误的
        return typeToClass(raw != null ? raw : owner);
    }

    /**
     * 获取type的主类，如果是不确定类型，则返回defClass
     *
     * @param type 类型
     * @param defClass 默认类
     * @return 确定的类型
     */
    public static final Class typeToClassOrElse(final Type type, final Class defClass) {
        Class clazz = typeToClass(type);
        return clazz == null ? defClass : clazz;
    }

    /**
     * 将泛型中不确定的类型转成确定性类型
     *
     * @param types 泛型集合
     * @param declaringClass 宿主类型
     * @return 确定性类型集合
     */
    public static Type[] getGenericType(final Type[] types, final Type declaringClass) {
        Type[] newTypes = new Type[types.length];
        for (int i = 0; i < newTypes.length; i++) {
            newTypes[i] = getGenericType(types[i], declaringClass);
        }
        return newTypes;
    }

    /**
     * 获取primitive类对应的box类型
     *
     * @param clazz primitive类
     * @return 基础类型box类型
     */
    public static Class primitiveToWrapper(Class clazz) {
        if (clazz == boolean.class) {
            return Boolean.class;
        } else if (clazz == byte.class) {
            return Byte.class;
        } else if (clazz == char.class) {
            return Character.class;
        } else if (clazz == short.class) {
            return Short.class;
        } else if (clazz == int.class) {
            return Integer.class;
        } else if (clazz == float.class) {
            return Float.class;
        } else if (clazz == long.class) {
            return Long.class;
        } else if (clazz == double.class) {
            return Double.class;
        } else if (clazz == void.class) {
            return Void.class;
        } else {
            return clazz;
        }
    }
    //
    //    public static void main(String[] args) throws Throwable {
    //        Method tt0 = C.class.getMethod("getValue");
    //        System.out.println("tt0.type=" + tt0.getReturnType() + "======" + tt0.getGenericReturnType() + "======" +
    // getGenericType(tt0.getGenericReturnType(), C.class));
    //
    //        Method tt3 = C.class.getMethod("getValue3");
    //        System.out.println("tt3.type=" + tt3.getReturnType() + "======" + tt3.getGenericReturnType() + "======" +
    // getGenericType(tt3.getGenericReturnType(), C.class));
    //
    //        Method ttr = AGameService.class.getMethod("getResult");
    //        System.out.println("ttr.type=" + ttr.getReturnType() + "======" + ttr.getGenericReturnType() + "======" +
    // getGenericType(ttr.getGenericReturnType(), AGameService.class));
    //        System.out.println("ttr.应该是: List<AGameTable>, 结果是: " + getGenericType(ttr.getGenericReturnType(),
    // AGameService.class));
    //    }
    //
    //    public static class GamePlayer {
    //    }
    //
    //    public static class GameTable<P extends GamePlayer> {
    //    }
    //
    //    public static class GameService<GT extends GameTable, P extends GamePlayer> {
    //
    //    }
    //
    //    public static class AbstractGamePlayer extends GamePlayer {
    //    }
    //
    //    public static class AbstractGameTable<P extends AbstractGamePlayer> extends GameTable<P> {
    //    }
    //
    //    public static class AbstractGameService<GT extends AbstractGameTable<P>, P extends AbstractGamePlayer> extends
    // GameService<GT, P> {
    //
    //        public List<GT> getResult() {
    //            return null;
    //        }
    //    }
    //
    //    public static class IGamePlayer extends AbstractGamePlayer {
    //    }
    //
    //    public static class IGameTable<P extends IGamePlayer> extends AbstractGameTable<P> {
    //    }
    //
    //    public static class IGameService<GT extends IGameTable<P>, P extends IGamePlayer> extends
    // AbstractGameService<GT, P> {
    //
    //    }
    //
    //    public static class AGamePlayer extends IGamePlayer {
    //    }
    //
    //    public static class AGameTable extends IGameTable<AGamePlayer> {
    //    }
    //
    //    public static class AGameService extends IGameService<AGameTable, AGamePlayer> {
    //
    //    }
    //
    //    public static class A<V, T> {
    //
    //        public List<T> getValue() {
    //            return null;
    //        }
    //
    //        public T getValue3() {
    //            return null;
    //        }
    //    }
    //
    //    public static class B<V, W> extends A<W, V> {
    //    }
    //
    //    public static class C extends B<String, Integer> {
    //    }

    /**
     * 获取TypeVariable对应的实际Type, 如果type不是TypeVariable 直接返回type。
     *
     * <pre>
     *  public abstract class Key {
     *  }
     *  public abstract class Val {
     *  }
     *  public abstract class AService &lt;K extends Key, V extends Val&gt; {
     *       public abstract V findValue(K key);
     *       public abstract Sheet&lt;V&gt; queryValue(K key);
     *  }
     *  public class Key2 extends Key {
     *  }
     *  public class Val2 extends Val {
     *  }
     *  public class Service2 extends Service &lt;Key2, Val2&gt; {
     *       public Val2 findValue(Key2 key){
     *          return new Val2();
     *       }
     *       public Sheet&lt;Val2&gt; queryValue(Key2 key){
     *          return new Sheet();
     *       }
     *  }
     * </pre>
     *
     * @param paramType 泛型
     * @param declaringClass 泛型依附类
     * @return Type
     */
    public static Type getGenericType(final Type paramType, final Type declaringClass) {
        if (paramType == null || declaringClass == null) {
            return paramType;
        }
        if (paramType instanceof TypeVariable) {
            Type superType = null;
            Class declaringClass0 = null;
            if (declaringClass instanceof Class) {
                declaringClass0 = (Class) declaringClass;
                superType = declaringClass0.getGenericSuperclass();
                if (superType instanceof ParameterizedType) {
                    Map<Type, Type> map = new HashMap<>();
                    parseType(map, declaringClass0);
                    Type rstype = getType(map, paramType);
                    if (isClassType(rstype)) {
                        return rstype;
                    }
                }
                while (superType instanceof Class && superType != Object.class)
                    superType = ((Class) superType).getGenericSuperclass();
            } else if (declaringClass instanceof ParameterizedType) {
                superType = declaringClass;
                Type rawType = ((ParameterizedType) declaringClass).getRawType();
                if (rawType instanceof Class) {
                    declaringClass0 = (Class) rawType;
                }
            }
            if (declaringClass0 != null && superType instanceof ParameterizedType) {
                ParameterizedType superPT = (ParameterizedType) superType;
                Type[] atas = superPT.getActualTypeArguments();
                Class ss = declaringClass0;
                TypeVariable[] asts = ss.getTypeParameters();
                while (atas.length != asts.length && ss != Object.class) {
                    ss = ss.getSuperclass();
                    asts = ss.getTypeParameters();
                }

                if (atas.length == asts.length) {
                    for (int i = 0; i < asts.length; i++) {
                        Type currt = asts[i];
                        if (asts[i] != paramType && superPT.getRawType() instanceof Class) {
                            if (asts[i] instanceof TypeVariable) {

                                Class raw = (Class) superPT.getRawType();
                                do {
                                    Type rawsuper = raw.getGenericSuperclass();
                                    if (!(rawsuper instanceof ParameterizedType)) {
                                        break;
                                    }
                                    ParameterizedType rpt = (ParameterizedType) rawsuper;
                                    Type supraw = rpt.getRawType();
                                    if (!(supraw instanceof Class)) {
                                        break;
                                    }
                                    Type[] tps = ((Class) supraw).getTypeParameters();
                                    if (rpt.getActualTypeArguments().length == tps.length) {
                                        for (int k = 0; k < rpt.getActualTypeArguments().length; k++) {
                                            if (rpt.getActualTypeArguments()[k] == currt) {
                                                currt = tps[k];
                                                break;
                                            }
                                        }
                                    }
                                    Type rtrt = rpt.getRawType();
                                    if (!(rtrt instanceof Class)) {
                                        break;
                                    }
                                    raw = (Class) rtrt;
                                } while (raw != Object.class);
                            }
                        }
                        if (currt == paramType) {
                            if (atas[i] instanceof Class
                                    && ((TypeVariable) paramType).getBounds().length == 1
                                    && ((TypeVariable) paramType).getBounds()[0] instanceof Class
                                    && ((Class) ((TypeVariable) paramType).getBounds()[0])
                                            .isAssignableFrom((Class) atas[i])) {
                                return atas[i];
                            }
                            if (atas[i] instanceof Class
                                    && ((TypeVariable) paramType).getBounds().length == 1
                                    && ((TypeVariable) paramType).getBounds()[0] instanceof ParameterizedType) {
                                ParameterizedType pt = (ParameterizedType) ((TypeVariable) paramType).getBounds()[0];
                                if (pt.getRawType() instanceof Class
                                        && ((Class) pt.getRawType()).isAssignableFrom((Class) atas[i])) {
                                    return atas[i];
                                }
                            }
                            if (atas[i] instanceof ParameterizedType
                                    && ((TypeVariable) paramType).getBounds().length == 1
                                    && ((TypeVariable) paramType).getBounds()[0] == Object.class) {
                                return atas[i];
                            }
                        }
                    }
                    ParameterizedType cycType = superPT;
                    if (cycType.getRawType() instanceof Class) {
                        TypeVariable[] argTypes = ((Class) cycType.getRawType()).getTypeParameters();
                        if (argTypes.length == asts.length) {
                            for (int i = 0; i < argTypes.length; i++) {
                                if (argTypes[i] == paramType) {
                                    if (atas[i] instanceof TypeVariable
                                            && ((TypeVariable) atas[i]).getBounds().length == 1
                                            && ((TypeVariable) atas[i]).getBounds()[0] instanceof Class) {
                                        return ((Class) ((TypeVariable) atas[i]).getBounds()[0]);
                                    }
                                }
                            }
                        }
                    }
                }
                Type moreType = ((ParameterizedType) superType).getRawType();
                if (moreType != Object.class) {
                    return getGenericType(paramType, moreType);
                }
            }
            TypeVariable tv = (TypeVariable) paramType;
            if (tv.getBounds().length == 1) {
                return tv.getBounds()[0];
            }
        } else if (paramType instanceof GenericArrayType) {
            final Type rst = getGenericType(((GenericArrayType) paramType).getGenericComponentType(), declaringClass);
            return (GenericArrayType) () -> rst;
        }
        if (paramType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) paramType;
            return createParameterizedType(
                    getGenericType(pt.getOwnerType(), declaringClass),
                    getGenericType(pt.getRawType(), declaringClass),
                    getGenericType(pt.getActualTypeArguments(), declaringClass));
        }
        return paramType;
    }

    private static Type getType(Map<Type, Type> map, Type type) {
        Type one = map.get(type);
        if (one == null) {
            return getParameterizedType(map, type);
        }
        if (one instanceof ParameterizedType && !isClassType(one)) {
            return getParameterizedType(map, one);
        }
        return getType(map, one);
    }

    private static Type getParameterizedType(Map<Type, Type> map, Type type) {
        if (type instanceof ParameterizedType && !isClassType(type)) {
            ParameterizedType pt = (ParameterizedType) type;
            Type owner = getType(map, pt.getOwnerType());
            if (owner == null || isClassType(owner)) {
                Type raw = getType(map, pt.getRawType());
                if (raw == null || isClassType(raw)) {
                    Type[] os = pt.getActualTypeArguments();
                    Type[] ns = new Type[os.length];
                    boolean allClass = true;
                    for (int i = 0; i < ns.length; i++) {
                        ns[i] = getType(map, os[i]);
                        if (!isClassType(ns[i])) {
                            allClass = false;
                            break;
                        }
                    }
                    if (allClass) {
                        return createParameterizedType(owner, raw, ns);
                    }
                }
            }
        }
        return type;
    }

    private static Map<Type, Type> parseType(Map<Type, Type> map, Class clzz) {
        if (clzz == Object.class) {
            return map;
        }
        Type superType = clzz.getGenericSuperclass();
        if (!(superType instanceof ParameterizedType)) {
            return map;
        }
        ParameterizedType pt = (ParameterizedType) superType;
        Type[] ptt = pt.getActualTypeArguments();
        Type superRaw = pt.getRawType();
        if (!(superRaw instanceof Class)) {
            return map;
        }
        Class superClazz = (Class) superRaw;
        TypeVariable[] scs = superClazz.getTypeParameters();
        if (scs.length != ptt.length) {
            return map;
        }
        for (int i = 0; i < scs.length; i++) {
            if (scs[i] == ptt[i]) {
                continue;
            }
            map.put(scs[i], ptt[i]);
        }
        return parseType(map, clzz.getSuperclass());
    }

    /**
     * 动态创建类型为ParameterizedType或Class的Type
     *
     * @param type 当前泛型
     * @param declaringType0 子类
     * @return Type
     */
    public static Type createClassType(final Type type, final Type declaringType0) {
        if (isClassType(type)) {
            return type;
        }
        if (type instanceof ParameterizedType) { // e.g. Map<String, String>
            final ParameterizedType pt = (ParameterizedType) type;
            final Type[] paramTypes = pt.getActualTypeArguments();
            for (int i = 0; i < paramTypes.length; i++) {
                paramTypes[i] = createClassType(paramTypes[i], declaringType0);
            }
            return createParameterizedType(pt.getOwnerType(), pt.getRawType(), paramTypes);
        }
        Type declaringType = declaringType0;
        if (declaringType instanceof Class) {
            do {
                declaringType = ((Class) declaringType).getGenericSuperclass();
                if (declaringType == Object.class) {
                    return Object.class;
                }
            } while (declaringType instanceof Class);
        }
        // 存在通配符则declaringType 必须是 ParameterizedType
        if (!(declaringType instanceof ParameterizedType)) {
            return Object.class;
        }
        final ParameterizedType declaringPType = (ParameterizedType) declaringType;
        final Type[] virTypes = ((Class) declaringPType.getRawType()).getTypeParameters();
        final Type[] desTypes = declaringPType.getActualTypeArguments();
        if (type instanceof WildcardType) { // e.g. <? extends Serializable>
            final WildcardType wt = (WildcardType) type;
            for (Type f : wt.getUpperBounds()) {
                for (int i = 0; i < virTypes.length; i++) {
                    if (virTypes[i].equals(f)) {
                        return desTypes.length <= i ? Object.class : desTypes[i];
                    }
                }
            }
        } else if (type instanceof TypeVariable) { // e.g.  <? extends E>
            for (int i = 0; i < virTypes.length; i++) {
                if (virTypes[i].equals(type)) {
                    return desTypes.length <= i ? Object.class : desTypes[i];
                }
            }
        }
        return type;
    }

    /**
     * 动态创建 ParameterizedType <br>
     * 例如: List&lt;String&gt;: createParameterizedType(null, List.class, String.class)
     *
     * @param ownerType0 ParameterizedType 的 ownerType, 一般为null
     * @param rawType0 ParameterizedType 的 rawType
     * @param actualTypeArguments0 ParameterizedType 的 actualTypeArguments
     * @return Type
     */
    public static Type createParameterizedType(
            final Type ownerType0, final Type rawType0, final Type... actualTypeArguments0) {
        if (ownerType0 == null && rawType0 instanceof Class) {
            int count = 0;
            for (Type t : actualTypeArguments0) {
                if (isClassType(t)) {
                    count++;
                }
            }
            if (count == actualTypeArguments0.length) {
                syncLock.lock();
                try {
                    return createParameterizedType0((Class) rawType0, actualTypeArguments0);
                } finally {
                    syncLock.unlock();
                }
            }
        }
        return new ParameterizedType() {
            private final Class<?> rawType = (Class<?>) rawType0;

            private final Type ownerType = ownerType0;

            private final Type[] actualTypeArguments = actualTypeArguments0;

            @Override
            public Type[] getActualTypeArguments() {
                return actualTypeArguments.clone();
            }

            @Override
            public Type getRawType() {
                return rawType;
            }

            @Override
            public Type getOwnerType() {
                return ownerType;
            }

            @Override
            public int hashCode() {
                return Arrays.hashCode(actualTypeArguments) ^ Objects.hashCode(rawType) ^ Objects.hashCode(ownerType);
            }

            @Override
            public boolean equals(Object o) {
                if (!(o instanceof ParameterizedType)) {
                    return false;
                }
                final ParameterizedType that = (ParameterizedType) o;
                if (this == that) {
                    return true;
                }
                return Objects.equals(ownerType, that.getOwnerType())
                        && Objects.equals(rawType, that.getRawType())
                        && Arrays.equals(actualTypeArguments, that.getActualTypeArguments());
            }

            @Override
            public String toString() {
                StringBuilder sb = new StringBuilder();
                if (ownerType != null) {
                    sb.append((ownerType instanceof Class) ? (((Class) ownerType).getName()) : ownerType.toString())
                            .append(".");
                }
                sb.append(rawType.getName());

                if (actualTypeArguments != null && actualTypeArguments.length > 0) {
                    sb.append("<");
                    boolean first = true;
                    for (Type t : actualTypeArguments) {
                        if (!first) {
                            sb.append(", ");
                        }
                        sb.append(t);
                        first = false;
                    }
                    sb.append(">");
                }
                return sb.toString();
            }
        };
    }

    // 注意:  RetResult<Map<String, Long>[]> 这种泛型带[]的尚未实现支持
    private static Type createParameterizedType0(final Class rawType, final Type... actualTypeArguments) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        StringBuilder tmpps = new StringBuilder(getClassTypeDescriptor(rawType));
        for (Type cz : actualTypeArguments) {
            tmpps.append(" ").append(getClassTypeDescriptor(cz));
        }
        StringBuilder nsb = new StringBuilder();
        for (char ch : tmpps.toString().toCharArray()) {
            if (ch >= '0' && ch <= '9') {
                nsb.append(ch);
            } else if (ch >= 'a' && ch <= 'z') {
                nsb.append(ch);
            } else if (ch >= 'A' && ch <= 'Z') {
                nsb.append(ch);
            } else { // 特殊字符统一使用_
                nsb.append('_');
            }
        }
        nsb.append("_GenericType");
        final String newDynName =
                "org/redkaledyn/typetoken/_Dyn" + TypeToken.class.getSimpleName() + "_" + nsb.toString();
        try {
            return loader.loadClass(newDynName.replace('/', '.'))
                    .getField("field")
                    .getGenericType();
        } catch (Throwable ex) {
            // do nothing
        }
        // ------------------------------------------------------------------------------
        org.redkale.asm.ClassWriter cw = new org.redkale.asm.ClassWriter(COMPUTE_FRAMES);
        org.redkale.asm.FieldVisitor fv;
        org.redkale.asm.MethodVisitor mv;
        cw.visit(V11, ACC_PUBLIC + ACC_FINAL + ACC_SUPER, newDynName, null, "java/lang/Object", null);
        String rawTypeDesc = org.redkale.asm.Type.getDescriptor(rawType);
        StringBuilder sb = new StringBuilder();
        sb.append(rawTypeDesc.substring(0, rawTypeDesc.length() - 1)).append('<');
        for (Type c : actualTypeArguments) {
            sb.append(getClassTypeDescriptor(c));
        }
        sb.append(">;");
        {
            fv = cw.visitField(ACC_PUBLIC, "field", rawTypeDesc, sb.toString(), null);
            fv.visitEnd();
        }
        { // 构造方法
            mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        cw.visitEnd();
        byte[] bytes = cw.toByteArray();
        Class<?> newClazz = new ClassLoader(loader) {
            public final Class<?> loadClass(String name, byte[] b) {
                return defineClass(name, b, 0, b.length);
            }
        }.loadClass(newDynName.replace('/', '.'), bytes);
        RedkaleClassLoader.putDynClass(newDynName.replace('/', '.'), bytes, newClazz);
        RedkaleClassLoader.putReflectionPublicFields(newDynName.replace('/', '.'));
        try {
            return newClazz.getField("field").getGenericType();
        } catch (Exception ex) {
            throw new RedkaleException(ex);
        }
    }

    private static CharSequence getClassTypeDescriptor(Type type) {
        if (!isClassType(type)) {
            throw new IllegalArgumentException(type + " not a class type");
        }
        if (type instanceof Class) {
            return org.redkale.asm.Type.getDescriptor((Class) type);
        }
        if (type instanceof GenericArrayType) {
            return getClassTypeDescriptor(((GenericArrayType) type).getGenericComponentType()) + "[]";
        }
        final ParameterizedType pt = (ParameterizedType) type;
        CharSequence rawTypeDesc = getClassTypeDescriptor(pt.getRawType());
        StringBuilder sb = new StringBuilder();
        sb.append(rawTypeDesc.subSequence(0, rawTypeDesc.length() - 1)).append('<');
        for (Type c : pt.getActualTypeArguments()) {
            sb.append(getClassTypeDescriptor(c));
        }
        sb.append(">;");
        return sb;
    }
}
