/*
 *
 */
package org.redkale.util;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.*;
import static org.redkale.asm.ClassWriter.COMPUTE_FRAMES;
import org.redkale.asm.*;
import static org.redkale.asm.Opcodes.ACC_BRIDGE;
import static org.redkale.asm.Opcodes.ACC_FINAL;
import static org.redkale.asm.Opcodes.ACC_PRIVATE;
import static org.redkale.asm.Opcodes.ACC_PUBLIC;
import static org.redkale.asm.Opcodes.ACC_STATIC;
import static org.redkale.asm.Opcodes.ACC_SUPER;
import static org.redkale.asm.Opcodes.ACC_SYNTHETIC;
import static org.redkale.asm.Opcodes.ACONST_NULL;
import static org.redkale.asm.Opcodes.ALOAD;
import static org.redkale.asm.Opcodes.ARETURN;
import static org.redkale.asm.Opcodes.ASTORE;
import static org.redkale.asm.Opcodes.CHECKCAST;
import static org.redkale.asm.Opcodes.GETFIELD;
import static org.redkale.asm.Opcodes.GOTO;
import static org.redkale.asm.Opcodes.IFEQ;
import static org.redkale.asm.Opcodes.IFLE;
import static org.redkale.asm.Opcodes.IFNONNULL;
import static org.redkale.asm.Opcodes.IFNULL;
import static org.redkale.asm.Opcodes.INSTANCEOF;
import static org.redkale.asm.Opcodes.INVOKEINTERFACE;
import static org.redkale.asm.Opcodes.INVOKESPECIAL;
import static org.redkale.asm.Opcodes.INVOKESTATIC;
import static org.redkale.asm.Opcodes.INVOKEVIRTUAL;
import static org.redkale.asm.Opcodes.POP;
import static org.redkale.asm.Opcodes.PUTFIELD;
import static org.redkale.asm.Opcodes.RETURN;
import static org.redkale.asm.Opcodes.V11;
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
 *
 * @since 2.8.0
 */
public interface Copier<S, D> extends BiFunction<S, D, D> {

    /**
     * 是否跳过值为null的字段
     */
    public static final int OPTION_SKIP_NULL_VALUE = 1 << 1; //2

    /**
     * 是否跳过值为空字符串的字段
     */
    public static final int OPTION_SKIP_RMPTY_STRING = 1 << 2; //4

    /**
     * 同名字段类型强制转换
     */
    public static final int OPTION_ALLOW_TYPE_CAST = 1 << 3; //8

    /**
     * 将源对象字段复制到目标对象
     *
     * @param dest 目标对象
     * @param src  源对象
     *
     * @return 目标对象
     */
    @Override
    public D apply(S src, D dest);

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
    public static <S, D> D copy(final S src, final D dest) {
        return copy(src, dest, 0);
    }

    /**
     * 将源对象字段复制到目标对象
     *
     * @param <D>     目标类泛型
     * @param <S>     源类泛型
     * @param dest    目标对象
     * @param src     源对象
     * @param options 可配项
     *
     * @return 目标对象
     */
    public static <S, D> D copy(final S src, final D dest, final int options) {
        if (src == null || dest == null) {
            return null;
        }
        return load((Class<S>) src.getClass(), (Class<D>) dest.getClass(), options).apply(src, dest);
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
    public static <S, D> D copy(final S src, final Class<D> destClass) {
        return copy(src, destClass, 0);
    }

    /**
     * 将源对象字段复制到目标对象
     *
     * @param <D>       目标类泛型
     * @param <S>       源类泛型
     * @param destClass 目标类名
     * @param src       源对象
     * @param options   可配项
     *
     * @return 目标对象
     */
    public static <S, D> D copy(final S src, final Class<D> destClass, final int options) {
        if (src == null) {
            return null;
        }
        Creator<D> creator = Creator.load(destClass);
        return load((Class<S>) src.getClass(), destClass, options).apply(src, creator.create());
    }

    /**
     * 将源对象字段复制到目标对象
     *
     * @param <S>     源类泛型
     * @param src     源对象
     * @param options 可配项
     *
     * @return 目标对象
     */
    public static <S> Map copyToMap(final S src, final int options) {
        if (src == null) {
            return null;
        }
        HashMap dest = new HashMap();
        return load((Class<S>) src.getClass(), HashMap.class, options).apply(src, dest);
    }

    /**
     * 创建源类到目标类的复制器并缓存
     *
     * @param <D>       目标类泛型
     * @param <S>       源类泛型
     * @param destClass 目标类名
     * @param srcClass  源类名
     * @param options   可配项
     *
     * @return 复制器
     */
    public static <S, D> Function<S, D> func(final Class<S> srcClass, final Class<D> destClass) {
        Copier<S, D> copier = load(srcClass, destClass);
        Creator<D> creator = Creator.load(destClass);
        return src -> copier.apply(src, creator.create());
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
    public static <S, D> Copier<S, D> load(final Class<S> srcClass, final Class<D> destClass) {
        return load(srcClass, destClass, 0);
    }

    /**
     * 创建源类到目标类的复制器并缓存
     *
     * @param <D>       目标类泛型
     * @param <S>       源类泛型
     * @param destClass 目标类名
     * @param srcClass  源类名
     * @param options   可配项
     *
     * @return 复制器
     */
    public static <S, D> Function<S, D> func(final Class<S> srcClass, final Class<D> destClass, final int options) {
        Copier<S, D> copier = load(srcClass, destClass, options);
        Creator<D> creator = Creator.load(destClass);
        return src -> copier.apply(src, creator.create());
    }

    /**
     * 创建源类到目标类的复制器并缓存
     *
     * @param <D>       目标类泛型
     * @param <S>       源类泛型
     * @param destClass 目标类名
     * @param srcClass  源类名
     * @param options   可配项
     *
     * @return 复制器
     */
    public static <S, D> Copier<S, D> load(final Class<S> srcClass, final Class<D> destClass, final int options) {
        if (destClass == srcClass) {
            return CopierInner.copierOneCaches
                .computeIfAbsent(options, t -> new ConcurrentHashMap<>())
                .computeIfAbsent(srcClass, v -> create(srcClass, destClass, options));
        } else {
            return CopierInner.copierTwoCaches
                .computeIfAbsent(options, t -> new ConcurrentHashMap<>())
                .computeIfAbsent(srcClass, t -> new ConcurrentHashMap<>())
                .computeIfAbsent(destClass, v -> create(srcClass, destClass, options));
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
    public static <S, D> Copier<S, D> create(final Class<S> srcClass, final Class<D> destClass) {
        return create(srcClass, destClass, (BiPredicate) null, (Map<String, String>) null);
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
    public static <S, D> Copier<S, D> create(final Class<S> srcClass, final Class<D> destClass, final Map<String, String> names) {
        return create(srcClass, destClass, (BiPredicate) null, names);
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
    public static <S, D> Copier<S, D> create(final Class<S> srcClass, final Class<D> destClass, final Predicate<String> srcColumnPredicate) {
        return create(srcClass, destClass, (sc, m) -> srcColumnPredicate.test(m), (Map<String, String>) null);
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
    public static <S, D> Copier<S, D> create(final Class<S> srcClass, final Class<D> destClass,
        final Predicate<String> srcColumnPredicate, final Map<String, String> names) {
        return create(srcClass, destClass, (sc, m) -> srcColumnPredicate.test(m), names);
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
    public static <S, D> Copier<S, D> create(final Class<S> srcClass, final Class<D> destClass,
        final BiPredicate<java.lang.reflect.AccessibleObject, String> srcColumnPredicate) {
        return create(srcClass, destClass, srcColumnPredicate, (Map<String, String>) null);
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
    public static <S, D> Copier<S, D> create(final Class<S> srcClass, final Class<D> destClass,
        final BiPredicate<java.lang.reflect.AccessibleObject, String> srcColumnPredicate, final Map<String, String> names) {
        return create(srcClass, destClass, 0, srcColumnPredicate, names);
    }

    /**
     * 创建源类到目标类的复制器
     *
     * @param <D>       目标类泛型
     * @param <S>       源类泛型
     * @param destClass 目标类名
     * @param srcClass  源类名
     * @param options   可配项
     *
     * @return 复制器
     */
    @SuppressWarnings("unchecked")
    public static <S, D> Copier<S, D> create(final Class<S> srcClass, final Class<D> destClass, final int options) {
        return create(srcClass, destClass, options, (BiPredicate) null, (Map<String, String>) null);
    }

    /**
     * 创建源类到目标类的复制器
     *
     * @param <D>                目标类泛型
     * @param <S>                源类泛型
     * @param destClass          目标类名
     * @param srcClass           源类名
     * @param options            可配项
     * @param srcColumnPredicate 需复制的字段名判断期
     * @param nameAlias          源字段名与目标字段名的映射关系
     *
     * @return 复制器
     */
    @SuppressWarnings("unchecked")
    public static <S, D> Copier<S, D> create(final Class<S> srcClass, final Class<D> destClass, final int options,
        final BiPredicate<java.lang.reflect.AccessibleObject, String> srcColumnPredicate, final Map<String, String> nameAlias) {
        final boolean skipNullValue = (options & OPTION_SKIP_NULL_VALUE) > 0 || ConcurrentHashMap.class.isAssignableFrom(destClass);
        final boolean skipEmptyString = (options & OPTION_SKIP_RMPTY_STRING) > 0;
        final boolean allowTypeCast = (options & OPTION_ALLOW_TYPE_CAST) > 0;
        final Predicate<Object> valPredicate = v -> !(skipNullValue && v == null)
            && !(skipEmptyString && v instanceof CharSequence && ((CharSequence) v).length() == 0);

        if (Map.class.isAssignableFrom(destClass) && Map.class.isAssignableFrom(srcClass)) {
            final Map names0 = nameAlias;
            if (srcColumnPredicate != null) {
                if (nameAlias != null) {
                    return (S src, D dest) -> {
                        if (src == null) {
                            return null;
                        }
                        Map d = (Map) dest;
                        ((Map) src).forEach((k, v) -> {
                            if (srcColumnPredicate.test(null, k.toString()) && valPredicate.test(v)) {
                                d.put(names0.getOrDefault(k, k), v);
                            }
                        });
                        return dest;
                    };
                } else {
                    return (S src, D dest) -> {
                        if (src == null) {
                            return null;
                        }
                        Map d = (Map) dest;
                        ((Map) src).forEach((k, v) -> {
                            if (srcColumnPredicate.test(null, k.toString()) && valPredicate.test(v)) {
                                d.put(k, v);
                            }
                        });
                        return dest;
                    };
                }
            } else if (nameAlias != null) {
                return (S src, D dest) -> {
                    if (src == null) {
                        return null;
                    }
                    Map d = (Map) dest;
                    ((Map) src).forEach((k, v) -> {
                        if (valPredicate.test(v)) {
                            d.put(names0.getOrDefault(k, k), v);
                        }
                    });
                    return dest;
                };
            }
            return new Copier<S, D>() {
                @Override
                public D apply(S src, D dest) {
                    if (src == null) {
                        return null;
                    }
                    if (options == 0) {
                        ((Map) dest).putAll((Map) src);
                    } else {
                        Map d = (Map) dest;
                        ((Map) src).forEach((k, v) -> {
                            if (valPredicate.test(v)) {
                                d.put(k, v);
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
        final String supDynName = Copier.class.getName().replace('.', '/');
        final String destClassName = destClass.getName().replace('.', '/');
        final String srcClassName = srcClass.getName().replace('.', '/');
        final String destDesc = Type.getDescriptor(destClass);
        final String srcDesc = Type.getDescriptor(srcClass);
        final ClassLoader loader = Thread.currentThread().getContextClassLoader();
        final String utilClassName = Utility.class.getName().replace('.', '/');
        final String newDynName = "org/redkaledyn/copier/_Dyn" + Copier.class.getSimpleName() + "_" + options
            + "__" + srcClass.getName().replace('.', '_').replace('$', '_')
            + "__" + destClass.getName().replace('.', '_').replace('$', '_');
        try {
            Class clz = RedkaleClassLoader.findDynClass(newDynName.replace('/', '.'));
            return (Copier) (clz == null ? loader.loadClass(newDynName.replace('/', '.')) : clz).getDeclaredConstructor().newInstance();
        } catch (Throwable ex) {
        }
        final Predicate<Class<?>> throwPredicate = e -> !RuntimeException.class.isAssignableFrom(e);
        // ------------------------------------------------------------------------------
        ClassWriter cw = new ClassWriter(COMPUTE_FRAMES);
        FieldVisitor fv;
        MethodVisitor mv;
        AnnotationVisitor av0;

        cw.visit(V11, ACC_PUBLIC + ACC_FINAL + ACC_SUPER, newDynName, "Ljava/lang/Object;L" + supDynName + "<" + srcDesc + destDesc + ">;", "java/lang/Object", new String[]{supDynName});

        { // 构造函数
            mv = (cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null));
            //mv.setDebug(true);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        if (srcIsMap) { //Map -> JavaBean
            {
                mv = (cw.visitMethod(ACC_PUBLIC, "apply", "(" + srcDesc + destDesc + ")" + destDesc, null, null));
                //mv.setDebug(true);
                {
                    //if(src == null) return null;            
                    mv.visitVarInsn(ALOAD, 1);
                    Label ifLabel = new Label();
                    mv.visitJumpInsn(IFNONNULL, ifLabel);
                    mv.visitInsn(ACONST_NULL);
                    mv.visitInsn(ARETURN);
                    mv.visitLabel(ifLabel);
                    mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                }

                mv.visitVarInsn(ALOAD, 1);
                mv.visitVarInsn(ALOAD, 2);
                mv.visitInvokeDynamicInsn("accept",
                    "(" + destDesc + ")Ljava/util/function/BiConsumer;",
                    new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false),
                    new Object[]{Type.getType("(Ljava/lang/Object;Ljava/lang/Object;)V"), new Handle(Opcodes.H_INVOKESTATIC, newDynName, "lambda$0", "(" + destDesc + "Ljava/lang/Object;Ljava/lang/Object;)V", false), Type.getType("(Ljava/lang/Object;Ljava/lang/Object;)V")});
                mv.visitMethodInsn(srcClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL, srcClassName, "forEach", "(Ljava/util/function/BiConsumer;)V", srcClass.isInterface());
                mv.visitVarInsn(ALOAD, 2);
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
                    final String dfname = nameAlias == null ? sfname : nameAlias.getOrDefault(sfname, sfname);
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
                    final String dfname = nameAlias == null ? sfname : nameAlias.getOrDefault(sfname, sfname);
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
                    final boolean charstr = CharSequence.class.isAssignableFrom(fieldClass);

                    mv.visitLdcInsn(en.getKey());
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
                    Label ifeq = index == elements.size() ? goLabel : new Label();
                    mv.visitJumpInsn(IFEQ, ifeq);
                    if (skipNullValue || primitive) {
                        mv.visitVarInsn(ALOAD, 2);
                        mv.visitJumpInsn(IFNULL, ifeq);
                    } else if (skipEmptyString && charstr) {
                        mv.visitVarInsn(ALOAD, 2);
                        mv.visitJumpInsn(IFNULL, ifeq);
                        mv.visitVarInsn(ALOAD, 2);
                        mv.visitTypeInsn(INSTANCEOF, "java/lang/CharSequence");
                        mv.visitJumpInsn(IFEQ, ifeq);
                        mv.visitVarInsn(ALOAD, 2);
                        mv.visitTypeInsn(CHECKCAST, "java/lang/CharSequence");
                        mv.visitMethodInsn(INVOKEINTERFACE, "java/lang/CharSequence", "length", "()I", true);
                        mv.visitJumpInsn(IFLE, ifeq);
                    }

                    mv.visitVarInsn(ALOAD, 0);
                    Asms.visitFieldInsn(mv, fieldClass);

                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitMethodInsn(INVOKESTATIC, utilClassName, "convertValue", "(Ljava/lang/reflect/Type;Ljava/lang/Object;)Ljava/lang/Object;", false);
                    Asms.visitCheckCast(mv, fieldClass);

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
        } else { //JavaBean -> Map/JavaBean
            mv = (cw.visitMethod(ACC_PUBLIC, "apply", "(" + srcDesc + destDesc + ")" + destDesc, null, null));
            //mv.setDebug(true);
            {
                //if(src == null) return null;            
                mv.visitVarInsn(ALOAD, 1);
                Label ifLabel = new Label();
                mv.visitJumpInsn(IFNONNULL, ifLabel);
                mv.visitInsn(ACONST_NULL);
                mv.visitInsn(ARETURN);
                mv.visitLabel(ifLabel);
                mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            }
            
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

                final String dfname = nameAlias == null ? sfname : nameAlias.getOrDefault(sfname, sfname);
                final Class srcFieldType = field.getType();
                final boolean charstr = CharSequence.class.isAssignableFrom(srcFieldType);
                if (destIsMap) { //JavaBean -> Map
                    String td = Type.getDescriptor(srcFieldType);
                    if ((!skipNullValue && !(skipEmptyString && charstr)) || srcFieldType.isPrimitive()) {
                        mv.visitVarInsn(ALOAD, 2);
                        mv.visitLdcInsn(dfname);
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitFieldInsn(GETFIELD, srcClassName, sfname, td);
                        Asms.visitPrimitiveValueOf(mv, srcFieldType);
                        mv.visitMethodInsn(destClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL, destClassName, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", destClass.isInterface());
                        mv.visitInsn(POP);
                    } else { // skipNullValue OR (skipEmptyString && charstr)
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitFieldInsn(GETFIELD, srcClassName, sfname, td);
                        mv.visitVarInsn(ASTORE, 3);
                        mv.visitVarInsn(ALOAD, 3);
                        Label ifLabel = new Label();
                        mv.visitJumpInsn(IFNULL, ifLabel);
                        if (skipEmptyString && charstr) {
                            mv.visitVarInsn(ALOAD, 3);
                            mv.visitTypeInsn(CHECKCAST, "java/lang/CharSequence");
                            mv.visitMethodInsn(INVOKEINTERFACE, "java/lang/CharSequence", "length", "()I", true);
                            mv.visitJumpInsn(IFLE, ifLabel);
                        }
                        mv.visitVarInsn(ALOAD, 2);
                        mv.visitLdcInsn(dfname);
                        mv.visitVarInsn(ALOAD, 3);
                        mv.visitMethodInsn(destClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL, destClassName, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", destClass.isInterface());
                        mv.visitInsn(POP);
                        mv.visitLabel(ifLabel);
                        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                    }
                } else { //JavaBean -> JavaBean
                    boolean needTypeCast = false;
                    java.lang.reflect.Method setter = null;
                    java.lang.reflect.Field setField = null;
                    try {
                        setField = destClass.getField(dfname);
                        if (!field.getType().equals(setField.getType())) {
                            if (allowTypeCast) {
                                needTypeCast = true;
                            } else {
                                continue;
                            }
                        }
                    } catch (Exception e) {
                        String setterMethodName = "set" + Utility.firstCharUpperCase(dfname);
                        try {
                            setter = destClass.getMethod(setterMethodName, field.getType());
                            if (Utility.contains(setter.getExceptionTypes(), throwPredicate)) {
                                continue;  //setter方法带有非RuntimeException异常
                            }
                        } catch (Exception e2) {
                            if (allowTypeCast) {
                                try {
                                    for (java.lang.reflect.Method m : destClass.getMethods()) {
                                        if (Modifier.isStatic(m.getModifiers())) {
                                            continue;
                                        }
                                        if (Utility.contains(m.getExceptionTypes(), throwPredicate)) {
                                            continue;  //setter方法带有非RuntimeException异常
                                        }
                                        if (m.getParameterTypes().length != 1) {
                                            continue;
                                        }
                                        if (m.getName().equals(setterMethodName)) {
                                            setter = m;
                                            needTypeCast = true;
                                            break;
                                        }
                                    }
                                    if (setter == null) {
                                        continue;
                                    }
                                } catch (Exception e3) {
                                    continue;
                                }
                            } else {
                                continue;
                            }
                        }
                    }
                    String srcFieldDesc = Type.getDescriptor(srcFieldType);
                    final Class destFieldType = setter == null ? setField.getType() : setter.getParameterTypes()[0];
                    boolean localSkipNull = skipNullValue || (!srcFieldType.isPrimitive() && destFieldType.isPrimitive());
                    if ((!localSkipNull && !(skipEmptyString && charstr))
                        || (srcFieldType.isPrimitive() && !allowTypeCast)
                        || (srcFieldType.isPrimitive() && destFieldType.isPrimitive())) {
                        if (needTypeCast) {
                            mv.visitVarInsn(ALOAD, 2);
                            Asms.visitFieldInsn(mv, destFieldType);
                            mv.visitVarInsn(ALOAD, 1);
                            mv.visitFieldInsn(GETFIELD, srcClassName, sfname, srcFieldDesc);
                            Asms.visitPrimitiveValueOf(mv, srcFieldType);
                            mv.visitMethodInsn(INVOKESTATIC, utilClassName, "convertValue", "(Ljava/lang/reflect/Type;Ljava/lang/Object;)Ljava/lang/Object;", false);
                            Asms.visitCheckCast(mv, destFieldType);
                            if (setter == null) { //src: field, dest:field
                                mv.visitFieldInsn(PUTFIELD, destClassName, dfname, Type.getDescriptor(destFieldType));
                            } else { //src: field, dest:method
                                mv.visitMethodInsn(destClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL, destClassName, setter.getName(), Type.getMethodDescriptor(setter), destClass.isInterface());
                            }
                        } else {
                            mv.visitVarInsn(ALOAD, 2);
                            mv.visitVarInsn(ALOAD, 1);
                            mv.visitFieldInsn(GETFIELD, srcClassName, sfname, srcFieldDesc);
                            if (setter == null) {  //src: field, dest:field
                                mv.visitFieldInsn(PUTFIELD, destClassName, dfname, Type.getDescriptor(destFieldType));
                            } else { //src: field, dest:method
                                mv.visitMethodInsn(destClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL, destClassName, setter.getName(), Type.getMethodDescriptor(setter), destClass.isInterface());
                            }
                        }
                    } else { // skipNullValue OR (skipEmptyString && charstr)
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitFieldInsn(GETFIELD, srcClassName, sfname, srcFieldDesc);
                        mv.visitVarInsn(ASTORE, 3);
                        mv.visitVarInsn(ALOAD, 3);
                        Label ifLabel = new Label();
                        mv.visitJumpInsn(IFNULL, ifLabel);
                        if (skipEmptyString && charstr) {
                            mv.visitVarInsn(ALOAD, 3);
                            mv.visitTypeInsn(CHECKCAST, "java/lang/CharSequence");
                            mv.visitMethodInsn(INVOKEINTERFACE, "java/lang/CharSequence", "length", "()I", true);
                            mv.visitJumpInsn(IFLE, ifLabel);
                        }
                        if (needTypeCast) {
                            mv.visitVarInsn(ALOAD, 2);
                            Asms.visitFieldInsn(mv, destFieldType);
                            mv.visitVarInsn(ALOAD, 3);
                            Asms.visitPrimitiveValueOf(mv, srcFieldType);
                            mv.visitMethodInsn(INVOKESTATIC, utilClassName, "convertValue", "(Ljava/lang/reflect/Type;Ljava/lang/Object;)Ljava/lang/Object;", false);
                            Asms.visitCheckCast(mv, destFieldType);
                            if (setter == null) { //src: field, dest:field
                                mv.visitFieldInsn(PUTFIELD, destClassName, dfname, Type.getDescriptor(destFieldType));
                            } else { //src: field, dest:method
                                mv.visitMethodInsn(destClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL, destClassName, setter.getName(), Type.getMethodDescriptor(setter), destClass.isInterface());
                            }
                        } else {
                            mv.visitVarInsn(ALOAD, 2);
                            mv.visitVarInsn(ALOAD, 3);
                            mv.visitTypeInsn(CHECKCAST, srcFieldType.getName().replace('.', '/'));
                            if (setter == null) {  //src: field, dest:field
                                mv.visitFieldInsn(PUTFIELD, destClassName, dfname, srcFieldDesc);
                            } else { //src: field, dest:method
                                mv.visitMethodInsn(destClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL, destClassName, setter.getName(), Type.getMethodDescriptor(setter), destClass.isInterface());
                            }
                        }
                        mv.visitLabel(ifLabel);
                        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
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

                final String dfname = nameAlias == null ? sfname : nameAlias.getOrDefault(sfname, sfname);
                final Class srcFieldType = getter.getReturnType();
                final boolean charstr = CharSequence.class.isAssignableFrom(srcFieldType);
                if (destIsMap) {  //srcClass是JavaBean
                    if ((!skipNullValue && !(skipEmptyString && charstr)) || srcFieldType.isPrimitive()) {
                        mv.visitVarInsn(ALOAD, 2);
                        mv.visitLdcInsn(dfname);
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitMethodInsn(srcClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL, srcClassName, getter.getName(), Type.getMethodDescriptor(getter), srcClass.isInterface());
                        Asms.visitPrimitiveValueOf(mv, srcFieldType);
                        mv.visitMethodInsn(destClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL, destClassName, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", destClass.isInterface());
                        mv.visitInsn(POP);
                    } else {  // skipNullValue OR (skipEmptyString && charstr)
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitMethodInsn(srcClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL, srcClassName, getter.getName(), Type.getMethodDescriptor(getter), srcClass.isInterface());
                        mv.visitVarInsn(ASTORE, 3);
                        mv.visitVarInsn(ALOAD, 3);
                        Label ifLabel = new Label();
                        mv.visitJumpInsn(IFNULL, ifLabel);
                        if (skipEmptyString && charstr) {
                            mv.visitVarInsn(ALOAD, 3);
                            mv.visitTypeInsn(CHECKCAST, "java/lang/CharSequence");
                            mv.visitMethodInsn(INVOKEINTERFACE, "java/lang/CharSequence", "length", "()I", true);
                            mv.visitJumpInsn(IFLE, ifLabel);
                        }
                        mv.visitVarInsn(ALOAD, 2);
                        mv.visitLdcInsn(dfname);
                        mv.visitVarInsn(ALOAD, 3);
                        mv.visitMethodInsn(destClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL, destClassName, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", destClass.isInterface());
                        mv.visitInsn(POP);
                        mv.visitLabel(ifLabel);
                        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                    }
                } else { //srcClass、destClass是JavaBean
                    boolean needTypeCast = false;
                    java.lang.reflect.Method setter = null;
                    java.lang.reflect.Field setField = null;
                    String setterMethodName = "set" + Utility.firstCharUpperCase(dfname);
                    try {
                        setter = destClass.getMethod(setterMethodName, getter.getReturnType());
                        if (Utility.contains(setter.getExceptionTypes(), throwPredicate)) {
                            continue;  //setter方法带有非RuntimeException异常
                        }
                    } catch (Exception e) {
                        if (allowTypeCast) {
                            try {
                                for (java.lang.reflect.Method m : destClass.getMethods()) {
                                    if (Modifier.isStatic(m.getModifiers())) {
                                        continue;
                                    }
                                    if (Utility.contains(m.getExceptionTypes(), throwPredicate)) {
                                        continue;  //setter方法带有非RuntimeException异常
                                    }
                                    if (m.getParameterTypes().length != 1) {
                                        continue;
                                    }
                                    if (m.getName().equals(setterMethodName)) {
                                        setter = m;
                                        needTypeCast = true;
                                        break;
                                    }
                                }
                            } catch (Exception e2) {
                            }
                        }
                        if (setter == null) {
                            try {
                                setField = destClass.getField(dfname);
                                if (!getter.getReturnType().equals(setField.getType())) {
                                    if (allowTypeCast) {
                                        needTypeCast = true;
                                    } else {
                                        continue;
                                    }
                                }
                            } catch (Exception e3) {
                                continue;
                            }
                        }
                    }
                    final Class destFieldType = setter == null ? setField.getType() : setter.getParameterTypes()[0];
                    boolean localSkipNull = skipNullValue || (!srcFieldType.isPrimitive() && destFieldType.isPrimitive());
                    if ((!localSkipNull && !(skipEmptyString && charstr))
                        || (srcFieldType.isPrimitive() && !allowTypeCast)
                        || (srcFieldType.isPrimitive() && destFieldType.isPrimitive())) {
                        if (needTypeCast) {
                            mv.visitVarInsn(ALOAD, 2);
                            Asms.visitFieldInsn(mv, destFieldType);
                            mv.visitVarInsn(ALOAD, 1);
                            mv.visitMethodInsn(srcClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL, srcClassName, getter.getName(), Type.getMethodDescriptor(getter), srcClass.isInterface());
                            Asms.visitPrimitiveValueOf(mv, srcFieldType);
                            mv.visitMethodInsn(INVOKESTATIC, utilClassName, "convertValue", "(Ljava/lang/reflect/Type;Ljava/lang/Object;)Ljava/lang/Object;", false);
                            Asms.visitCheckCast(mv, destFieldType);
                            if (setter == null) { //src: method, dest: field
                                mv.visitFieldInsn(PUTFIELD, destClassName, dfname, Type.getDescriptor(destFieldType));
                            } else {  //src: method, dest: method
                                mv.visitMethodInsn(destClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL, destClassName, setter.getName(), Type.getMethodDescriptor(setter), destClass.isInterface());
                            }
                        } else {
                            mv.visitVarInsn(ALOAD, 2);
                            mv.visitVarInsn(ALOAD, 1);
                            mv.visitMethodInsn(srcClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL, srcClassName, getter.getName(), Type.getMethodDescriptor(getter), srcClass.isInterface());
                            if (setter == null) { //src: method, dest: field
                                mv.visitFieldInsn(PUTFIELD, destClassName, dfname, Type.getDescriptor(destFieldType));
                            } else {  //src: method, dest: method
                                mv.visitMethodInsn(destClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL, destClassName, setter.getName(), Type.getMethodDescriptor(setter), destClass.isInterface());
                            }
                        }
                    } else {  // skipNullValue OR (skipEmptyString && charstr)
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitMethodInsn(srcClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL, srcClassName, getter.getName(), Type.getMethodDescriptor(getter), srcClass.isInterface());
                        mv.visitVarInsn(ASTORE, 3);
                        mv.visitVarInsn(ALOAD, 3);
                        Label ifLabel = new Label();
                        mv.visitJumpInsn(IFNULL, ifLabel);
                        if (skipEmptyString && charstr) {
                            mv.visitVarInsn(ALOAD, 3);
                            mv.visitTypeInsn(CHECKCAST, "java/lang/CharSequence");
                            mv.visitMethodInsn(INVOKEINTERFACE, "java/lang/CharSequence", "length", "()I", true);
                            mv.visitJumpInsn(IFLE, ifLabel);
                        }
                        if (needTypeCast) {
                            mv.visitVarInsn(ALOAD, 2);
                            Asms.visitFieldInsn(mv, destFieldType);
                            mv.visitVarInsn(ALOAD, 3);
                            Asms.visitPrimitiveValueOf(mv, srcFieldType);
                            mv.visitMethodInsn(INVOKESTATIC, utilClassName, "convertValue", "(Ljava/lang/reflect/Type;Ljava/lang/Object;)Ljava/lang/Object;", false);
                            Asms.visitCheckCast(mv, destFieldType);
                            if (setter == null) { //src: method, dest: field
                                mv.visitFieldInsn(PUTFIELD, destClassName, dfname, Type.getDescriptor(destFieldType));
                            } else { //src: method, dest: method
                                mv.visitMethodInsn(destClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL, destClassName, setter.getName(), Type.getMethodDescriptor(setter), destClass.isInterface());
                            }
                        } else {
                            mv.visitVarInsn(ALOAD, 2);
                            mv.visitVarInsn(ALOAD, 3);
                            mv.visitTypeInsn(CHECKCAST, srcFieldType.getName().replace('.', '/'));
                            if (setter == null) { //src: method, dest: field
                                mv.visitFieldInsn(PUTFIELD, destClassName, dfname, Type.getDescriptor(getter.getReturnType()));
                            } else { //src: method, dest: method
                                mv.visitMethodInsn(destClass.isInterface() ? INVOKEINTERFACE : INVOKEVIRTUAL, destClassName, setter.getName(), Type.getMethodDescriptor(setter), destClass.isInterface());
                            }
                        }
                        mv.visitLabel(ifLabel);
                        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                    }
                }
            }
            mv.visitVarInsn(ALOAD, 2);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(3, 3);
            mv.visitEnd();
        }
        {
            mv = (cw.visitMethod(ACC_PUBLIC + ACC_BRIDGE + ACC_SYNTHETIC, "apply", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", null, null));
            //mv.setDebug(true);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, srcClassName);
            mv.visitVarInsn(ALOAD, 2);
            mv.visitTypeInsn(CHECKCAST, destClassName);
            mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, "apply", "(" + srcDesc + destDesc + ")" + destDesc, false);
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
            return (Copier) newClazz.getDeclaredConstructor().newInstance();
        } catch (Exception ex) {
            throw new RedkaleException(ex);
        }
    }

    static class CopierInner {

        static final ConcurrentHashMap<Integer, ConcurrentHashMap<Class, Copier>> copierOneCaches = new ConcurrentHashMap();

        static final ConcurrentHashMap<Integer, ConcurrentHashMap<Class, ConcurrentHashMap<Class, Copier>>> copierTwoCaches = new ConcurrentHashMap();

    }

}
