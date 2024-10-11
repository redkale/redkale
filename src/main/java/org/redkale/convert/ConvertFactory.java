/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import java.io.File;
import java.lang.reflect.*;
import java.math.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.*;
import org.redkale.convert.ext.*;
import org.redkale.convert.json.JsonConvert;
import org.redkale.convert.pb.ProtobufConvert;
import org.redkale.convert.spi.ConvertProvider;
import org.redkale.util.*;

/**
 * 序列化模块的工厂类，用于注册自定义的序列化类型，获取Convert
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> Reader输入的子类
 * @param <W> Writer输出的子类
 */
@SuppressWarnings("unchecked")
public abstract class ConvertFactory<R extends Reader, W extends Writer> {

    private final ConvertFactory parent;

    protected Convert<R, W> convert;

    /**
     * 配置属性集合
     * @see org.redkale.convert.Convert#FEATURE_NULLABLE
     * @see org.redkale.convert.Convert#FEATURE_TINY
     */
    protected int features;

    private final Encodeable<W, ?> anyEncoder = new AnyEncoder(this);

    // -----------------------------------------------------------------------------------
    private final ConcurrentHashMap<Class, Creator> creators = new ConcurrentHashMap();

    private final ConcurrentHashMap<Type, Map<String, SimpledCoder<R, W, ?>>> fieldCoders = new ConcurrentHashMap();

    private final ConcurrentHashMap<Type, Decodeable<R, ?>> decoders = new ConcurrentHashMap();

    private final ConcurrentHashMap<Type, Encodeable<W, ?>> encoders = new ConcurrentHashMap();

    private final ConcurrentHashMap<AccessibleObject, ConvertColumnEntry> columnEntrys = new ConcurrentHashMap();

    private final ConcurrentHashMap<Class, BiFunction> fieldFuncs = new ConcurrentHashMap();

    private final ConcurrentHashMap<Type, Type> genericListTypes = new ConcurrentHashMap<>();

    private final Set<Class> skipIgnores = new HashSet();

    final Set<String> ignoreMapColumns = new HashSet();

    final ReentrantLock ignoreMapColumnLock = new ReentrantLock();

    // key:需要屏蔽的字段；value：排除的字段名
    private final ConcurrentHashMap<Class, Set<String>> ignoreAlls = new ConcurrentHashMap();

    private boolean skipAllIgnore = false;

    private Consumer<BiFunction> fieldFuncConsumer;

    protected ConvertFactory(ConvertFactory<R, W> parent, int features) {
        this.features = features;
        this.parent = parent;
        if (parent == null) {
            // ---------------------------------------------------------

            this.register(boolean.class, BoolSimpledCoder.instance);
            this.register(Boolean.class, BoolSimpledCoder.instance);

            this.register(byte.class, ByteSimpledCoder.instance);
            this.register(Byte.class, ByteSimpledCoder.instance);

            this.register(short.class, ShortSimpledCoder.instance);
            this.register(Short.class, ShortSimpledCoder.instance);

            this.register(char.class, CharSimpledCoder.instance);
            this.register(Character.class, CharSimpledCoder.instance);

            this.register(int.class, IntSimpledCoder.instance);
            this.register(Integer.class, IntSimpledCoder.instance);

            this.register(long.class, LongSimpledCoder.instance);
            this.register(Long.class, LongSimpledCoder.instance);

            this.register(float.class, FloatSimpledCoder.instance);
            this.register(Float.class, FloatSimpledCoder.instance);

            this.register(double.class, DoubleSimpledCoder.instance);
            this.register(Double.class, DoubleSimpledCoder.instance);

            this.register(Number.class, NumberSimpledCoder.instance);
            this.register(String.class, StringSimpledCoder.instance);
            this.register(StringWrapper.class, StringWrapperSimpledCoder.instance);
            this.register(CharSequence.class, CharSequenceSimpledCoder.instance);
            this.register(StringBuilder.class, CharSequenceSimpledCoder.StringBuilderSimpledCoder.instance);
            this.register(java.util.Date.class, DateSimpledCoder.instance);
            this.register(java.time.Instant.class, InstantSimpledCoder.instance);
            this.register(java.time.LocalDate.class, LocalDateSimpledCoder.instance);
            this.register(java.time.LocalTime.class, LocalTimeSimpledCoder.instance);
            this.register(java.time.LocalDateTime.class, LocalDateTimeSimpledCoder.instance);
            this.register(java.time.Duration.class, DurationSimpledCoder.instance);
            this.register(AtomicBoolean.class, AtomicBooleanSimpledCoder.instance);
            this.register(AtomicInteger.class, AtomicIntegerSimpledCoder.instance);
            this.register(AtomicLong.class, AtomicLongSimpledCoder.instance);
            this.register(BigInteger.class, BigIntegerSimpledCoder.instance);
            this.register(BigDecimal.class, BigDecimalSimpledCoder.instance);
            this.register(InetAddress.class, InetAddressSimpledCoder.instance);
            this.register(InetSocketAddress.class, InetAddressSimpledCoder.InetSocketAddressSimpledCoder.instance);
            this.register(LongAdder.class, LongAdderSimpledCoder.instance);
            this.register(Uint128.class, Uint128SimpledCoder.instance);
            this.register(Class.class, TypeSimpledCoder.instance);
            this.register(Pattern.class, PatternSimpledCoder.instance);
            this.register(File.class, FileSimpledCoder.instance);
            this.register(Throwable.class, ThrowableSimpledCoder.instance);
            this.register(CompletionHandler.class, CompletionHandlerSimpledCoder.instance);
            this.register(URL.class, URLSimpledCoder.instance);
            this.register(URI.class, URISimpledCoder.instance);
            // ---------------------------------------------------------
            this.register(ByteBuffer.class, ByteBufferSimpledCoder.instance);
            this.register(boolean[].class, BoolArraySimpledCoder.instance);
            this.register(byte[].class, ByteArraySimpledCoder.instance);
            this.register(short[].class, ShortArraySimpledCoder.instance);
            this.register(char[].class, CharArraySimpledCoder.instance);
            this.register(int[].class, IntArraySimpledCoder.instance);
            this.register(IntStream.class, IntArraySimpledCoder.IntStreamSimpledCoder.instance);
            this.register(long[].class, LongArraySimpledCoder.instance);
            this.register(LongStream.class, LongArraySimpledCoder.LongStreamSimpledCoder.instance);
            this.register(float[].class, FloatArraySimpledCoder.instance);
            this.register(double[].class, DoubleArraySimpledCoder.instance);
            this.register(DoubleStream.class, DoubleArraySimpledCoder.DoubleStreamSimpledCoder.instance);
            this.register(String[].class, StringArraySimpledCoder.instance);
            // ---------------------------------------------------------
            this.register(AnyValue.class, Creator.create(AnyValueWriter.class));
            this.register(
                    HttpCookie.class, (Object... params) -> new HttpCookie((String) params[0], (String) params[1]));
            try {
                Class sqldateClass =
                        Thread.currentThread().getContextClassLoader().loadClass("java.sql.Date");
                Invoker<Object, Object> sqldateInvoker = Invoker.create(sqldateClass, "valueOf", String.class);
                this.register(sqldateClass, new SimpledCoder<R, W, Object>() {

                    @Override
                    public void convertTo(W out, Object value) {
                        out.writeStandardString(value == null ? null : value.toString());
                    }

                    @Override
                    public Object convertFrom(R in) {
                        String t = in.readStandardString();
                        return t == null ? null : sqldateInvoker.invoke(null, t);
                    }
                });
                Class sqltimeClass =
                        Thread.currentThread().getContextClassLoader().loadClass("java.sql.Time");
                Invoker<Object, Object> sqltimeInvoker = Invoker.create(sqltimeClass, "valueOf", String.class);
                this.register(sqltimeClass, new SimpledCoder<R, W, Object>() {

                    @Override
                    public void convertTo(W out, Object value) {
                        out.writeStandardString(value == null ? null : value.toString());
                    }

                    @Override
                    public Object convertFrom(R in) {
                        String t = in.readStandardString();
                        return t == null ? null : sqltimeInvoker.invoke(null, t);
                    }
                });
                Class timestampClass =
                        Thread.currentThread().getContextClassLoader().loadClass("java.sql.Timestamp");
                Invoker<Object, Object> timestampInvoker = Invoker.create(timestampClass, "valueOf", String.class);
                this.register(timestampClass, new SimpledCoder<R, W, Object>() {

                    @Override
                    public void convertTo(W out, Object value) {
                        out.writeStandardString(value == null ? null : value.toString());
                    }

                    @Override
                    public Object convertFrom(R in) {
                        String t = in.readStandardString();
                        return t == null ? null : timestampInvoker.invoke(null, t);
                    }
                });
            } catch (Throwable t) {
                // do nothing
            }
        }
    }

    public ConvertFactory parent() {
        return this.parent;
    }

    public static Convert findConvert(ConvertType type) {
        Objects.requireNonNull(type);
        if (type == ConvertType.JSON || type.contains(ConvertType.JSON)) {
            return JsonConvert.root();
        }
        if (type == ConvertType.PROTOBUF || type.contains(ConvertType.PROTOBUF)) {
            return ProtobufConvert.root();
        }

        Iterator<ConvertProvider> it = ServiceLoader.load(ConvertProvider.class).iterator();
        RedkaleClassLoader.putServiceLoader(ConvertProvider.class);
        while (it.hasNext()) {
            ConvertProvider cl = it.next();
            RedkaleClassLoader.putReflectionPublicConstructors(
                    cl.getClass(), cl.getClass().getName());
            if (type.contains(cl.type())) {
                return cl.convert();
            }
        }
        return null;
    }

    protected static int getSystemPropertyInt(String key, String parentkey, boolean defvalue, int feature) {
        return Boolean.parseBoolean(System.getProperty(key, System.getProperty(parentkey, String.valueOf(defvalue))))
                ? feature
                : 0;
    }

    public abstract ConvertType getConvertType();

    public abstract boolean isReversible(); // 是否可逆的

    public abstract boolean isFieldSort(); // 当ConvertColumn.index相同时是否按字段名称排序

    public abstract ConvertFactory createChild();

    public abstract ConvertFactory createChild(int features);

    protected abstract ConvertFactory rootFactory();

    protected SimpledCoder createEnumSimpledCoder(Class enumClass) {
        return new EnumSimpledCoder(this, enumClass);
    }

    protected Type formatObjectType(Type type) {
        if (type instanceof Class) {
            Class<?> clazz = (Class) type;
            ConvertImpl ci = clazz.getAnnotation(ConvertImpl.class);
            if (ci != null && ci.value() != Object.class) {
                if (!clazz.isAssignableFrom(ci.value())) {
                    throw new ConvertException("@" + ConvertImpl.class.getSimpleName() + ".value(" + ci.value()
                            + ") must be " + clazz + "'s subclass");
                }
                if (!Modifier.isAbstract(clazz.getModifiers()) && !Modifier.isInterface(clazz.getModifiers())) {
                    throw new ConvertException("@" + ConvertImpl.class.getSimpleName()
                            + " must at interface or abstract class, but " + clazz + " not");
                }
                Class impl = ci.value();
                if (Modifier.isAbstract(impl.getModifiers()) || Modifier.isInterface(impl.getModifiers())) {
                    throw new ConvertException("@" + ConvertImpl.class.getSimpleName() + " at class " + impl
                            + " cannot be interface or abstract class");
                }
                return impl;
            }
        }
        return type;
    }

    protected boolean isCollectionType(final Type type) {
        if (type == null) {
            return false;
        }
        if (type instanceof Class) {
            return Collection.class.isAssignableFrom((Class) type);
        } else if (!(type instanceof ParameterizedType)) {
            return false;
        }
        ParameterizedType ptype = (ParameterizedType) type;
        if (!(ptype.getRawType() instanceof Class)) {
            return false;
        }
        Type[] ptargs = ptype.getActualTypeArguments();
        if (ptargs == null || ptargs.length != 1) {
            return false;
        }
        Class ownerType = (Class) ptype.getRawType();
        return Collection.class.isAssignableFrom(ownerType);
    }

    // 返回null表示type不是Collection类型
    protected Type getCollectionComponentType(final Type type) {
        if (!isCollectionType(type)) {
            return null;
        }
        if (type instanceof Class) {
            return Object.class;
        } else if (!(type instanceof ParameterizedType)) {
            return null;
        }
        ParameterizedType ptype = ((ParameterizedType) type);
        return ptype.getActualTypeArguments()[0];
    }

    protected boolean isStreamType(final Type type) {
        if (type == null) {
            return false;
        }
        if (type instanceof Class) {
            return Stream.class.isAssignableFrom((Class) type);
        }
        ParameterizedType ptype = (ParameterizedType) type;
        if (!(ptype.getRawType() instanceof Class)) {
            return false;
        }
        Type[] ptargs = ptype.getActualTypeArguments();
        if (ptargs == null || ptargs.length != 1) {
            return false;
        }
        Class ownerType = (Class) ptype.getRawType();
        return Stream.class.isAssignableFrom(ownerType);
    }

    // 返回null表示type不是Stream类型
    protected Type getStreamionComponentType(final Type type) {
        if (!isStreamType(type)) {
            return null;
        }
        if (type instanceof Class) {
            return Object.class;
        }
        ParameterizedType ptype = ((ParameterizedType) type);
        return ptype.getActualTypeArguments()[0];
    }

    protected <E> Encodeable<W, E> createDyncEncoder(Type type) {
        return null;
    }

    protected ObjectDecoder createObjectDecoder(Type type) {
        return new ObjectDecoder(type);
    }

    protected <E> Decodeable<R, E> createMultiImplDecoder(Class[] types) {
        return null;
    }

    protected <E> ObjectEncoder<W, E> createObjectEncoder(Type type) {
        return new ObjectEncoder(type);
    }

    protected <E> Decodeable<R, E> createMapDecoder(Type type) {
        return new MapDecoder(this, type);
    }

    protected <E> Encodeable<W, E> createMapEncoder(Type type) {
        return new MapEncoder(this, type);
    }

    protected <E> Decodeable<R, E> createArrayDecoder(Type type) {
        return new ArrayDecoder(this, type);
    }

    protected <E> Encodeable<W, E> createArrayEncoder(Type type) {
        return new ArrayEncoder(this, type);
    }

    protected <E> Decodeable<R, E> createCollectionDecoder(Type type) {
        return new CollectionDecoder(this, type);
    }

    protected <E> Encodeable<W, E> createCollectionEncoder(Type type) {
        return new CollectionEncoder(this, type);
    }

    protected <E> Decodeable<R, E> createStreamDecoder(Type type) {
        return new StreamDecoder(this, type);
    }

    protected <E> Encodeable<W, E> createStreamEncoder(Type type) {
        return new StreamEncoder(this, type);
    }

    public Convert getConvert() {
        return convert;
    }

    public final int getFeatures() {
        return this.features;
    }

    protected <F extends ConvertFactory<R, W>> F withFeatures(int features) {
        if (features > -1) {
            this.features = features;
        }
        return (F) this;
    }

    protected <F extends ConvertFactory<R, W>> F addFeature(int feature) {
        if (feature > 0) {
            if (features > -1) {
                this.features |= feature;
            } else {
                this.features = feature;
            }
        }
        return (F) this;
    }

    protected <F extends ConvertFactory<R, W>> F removeFeature(int feature) {
        if (feature > 0) {
            if (features > -1) {
                this.features = this.features & ~feature;
            }
        }
        return (F) this;
    }

    protected <F extends ConvertFactory<R, W>> F withTinyFeature(boolean tiny) {
        return tiny ? addFeature(Convert.FEATURE_TINY) : removeFeature(Convert.FEATURE_NULLABLE);
    }

    protected <F extends ConvertFactory<R, W>> F withNullableFeature(boolean nullable) {
        return nullable ? addFeature(Convert.FEATURE_NULLABLE) : removeFeature(Convert.FEATURE_NULLABLE);
    }

    protected Type createGenericListType(Type componentType) {
        ConcurrentHashMap<Type, Type> map = rootFactory().genericListTypes;
        return map.computeIfAbsent(componentType, t -> TypeToken.createParameterizedType(null, List.class, t));
    }

    public static boolean checkTinyFeature(int features) {
        return (features & Convert.FEATURE_TINY) > 0;
    }

    public static boolean checkNullableFeature(int features) {
        return (features & Convert.FEATURE_NULLABLE) > 0;
    }

    public boolean isConvertDisabled(AccessibleObject element) {
        ConvertDisabled[] ccs = element.getAnnotationsByType(ConvertDisabled.class);
        if (ccs.length == 0 && element instanceof Method) {
            final Method method = (Method) element;
            String fieldName = readGetSetFieldName(method);
            if (fieldName != null) {
                try {
                    ccs = method.getDeclaringClass()
                            .getDeclaredField(fieldName)
                            .getAnnotationsByType(ConvertDisabled.class);
                } catch (Exception e) { // 说明没有该字段，忽略异常
                }
            }
        }
        final ConvertType ct = this.getConvertType();
        for (ConvertDisabled ref : ccs) {
            if (ref.type().contains(ct)) {
                return true;
            }
        }
        return false;
    }

    public ConvertColumnEntry findRef(Class clazz, AccessibleObject element) {
        if (element == null) {
            return null;
        }
        ConvertColumnEntry en = this.columnEntrys.get(element);
        Set<String> onlyColumns = ignoreAlls.get(clazz);
        if (en != null && onlyColumns == null) {
            return en;
        }
        final ConvertType ct = this.getConvertType();
        String fieldName = null;
        // 解析 ColumnHandler
        BiFunction colHandler = null;
        ConvertType colConvertType = ConvertType.ALL;
        ConvertColumnHandler[] chs = element.getAnnotationsByType(ConvertColumnHandler.class);
        if (chs.length == 0 && element instanceof Method) {
            final Method method = (Method) element;
            fieldName = readGetSetFieldName(method);
            if (fieldName != null) {
                Class mclz = method.getDeclaringClass();
                do {
                    try {
                        chs = mclz.getDeclaredField(fieldName).getAnnotationsByType(ConvertColumnHandler.class);
                        break;
                    } catch (Exception e) { // 说明没有该字段，忽略异常
                    }
                } while (mclz != Object.class && (mclz = mclz.getSuperclass()) != Object.class);
            }
        }
        for (ConvertColumnHandler h : chs) {
            if (h.type().contains(ct)) {
                Class<? extends ColumnHandler> handlerClass = h.value();
                if (handlerClass != ColumnHandler.class) {
                    colConvertType = h.type();
                    colHandler = findFieldFunc(handlerClass);
                    if (colHandler == null) {
                        try {
                            colHandler = handlerClass.getConstructor().newInstance();
                        } catch (Exception e) {
                            throw new ConvertException(e);
                        }
                        Consumer<BiFunction> consumer = findFieldFuncConsumer();
                        if (consumer != null) {
                            consumer.accept(colHandler);
                        }
                        register((Class) handlerClass, colHandler);
                    }
                    break;
                }
            }
        }
        // 解析ConvertColumn
        ConvertColumn[] ccs = element.getAnnotationsByType(ConvertColumn.class);
        if (ccs.length == 0 && element instanceof Method) {
            final Method method = (Method) element;
            fieldName = readGetSetFieldName(method);
            if (fieldName != null) {
                Class mclz = method.getDeclaringClass();
                do {
                    try {
                        ccs = mclz.getDeclaredField(fieldName).getAnnotationsByType(ConvertColumn.class);
                        break;
                    } catch (Exception e) { // 说明没有该字段，忽略异常
                    }
                } while (mclz != Object.class && (mclz = mclz.getSuperclass()) != Object.class);
            }
        }
        if (onlyColumns != null && fieldName == null) {
            if (element instanceof Method) {
                fieldName = readGetSetFieldName((Method) element);
            } else if (element instanceof Field) {
                fieldName = ((Field) element).getName();
            }
        }
        if (ccs.length == 0 && onlyColumns != null && fieldName != null) {
            if (!onlyColumns.contains(fieldName)) {
                return new ConvertColumnEntry(fieldName, true, colConvertType, colHandler);
            }
        }
        for (ConvertColumn ref : ccs) {
            if (ref.type().contains(ct)) {
                String realName = ref.name().isEmpty() ? fieldName : ref.name();
                if (onlyColumns != null && fieldName != null) {
                    if (!onlyColumns.contains(realName)) {
                        return new ConvertColumnEntry(realName, true, colConvertType, colHandler);
                    }
                }
                ConvertColumnEntry entry = new ConvertColumnEntry(ref, colHandler);
                if (skipAllIgnore) {
                    entry.setIgnore(false);
                    return entry;
                }
                if (skipIgnores.isEmpty()) {
                    if (onlyColumns != null && realName != null && onlyColumns.contains(realName)) {
                        entry.setIgnore(false);
                    }
                    return entry;
                }
                if (skipIgnores.contains(((Member) element).getDeclaringClass())) {
                    entry.setIgnore(false);
                }
                return entry;
            }
        }
        if (colHandler != null) {
            return new ConvertColumnEntry(fieldName, false, colConvertType, colHandler);
        }
        return null;
    }

    static Field readGetSetField(Method method) {
        String name = readGetSetFieldName(method);
        if (name == null) {
            return null;
        }
        try {
            return method.getDeclaringClass().getDeclaredField(name);
        } catch (Exception e) {
            return null;
        }
    }

    static String readGetSetFieldName(Method method) {
        if (method == null) {
            return null;
        }
        String fname = method.getName();
        if (!(fname.startsWith("is") && fname.length() > 2)
                && !(fname.startsWith("get") && fname.length() > 3)
                && !(fname.startsWith("set") && fname.length() > 3)) {
            return fname; // record类会直接用field名作为method名
        }
        fname = fname.substring(fname.startsWith("is") ? 2 : 3);
        if (fname.length() > 1 && !(fname.charAt(1) >= 'A' && fname.charAt(1) <= 'Z')) {
            fname = Character.toLowerCase(fname.charAt(0)) + fname.substring(1);
        } else if (fname.length() == 1) {
            fname = "" + Character.toLowerCase(fname.charAt(0));
        }
        return fname;
    }

    public String readConvertFieldName(Class clazz, AccessibleObject element) {
        ConvertColumnEntry ref = findRef(clazz, element);
        String name = ref == null || ref.name().isEmpty() ? readGetSetFieldName(element) : ref.name();
        return name;
    }

    public String readGetSetFieldName(AccessibleObject element) {
        if (element instanceof Field) {
            return ((Field) element).getName();
        }
        return readGetSetFieldName((Method) element);
    }

    public void sortFieldIndex(final Class clazz, List<AccessibleObject> members) {
        Collections.sort(members, (o1, o2) -> {
            ConvertColumnEntry ref1 = findRef(clazz, o1);
            ConvertColumnEntry ref2 = findRef(clazz, o2);
            if ((ref1 != null && ref1.getIndex() > 0) || (ref2 != null && ref2.getIndex() > 0)) {
                int idx1 = ref1 == null ? Integer.MAX_VALUE / 2 : ref1.getIndex();
                int idx2 = ref2 == null ? Integer.MAX_VALUE / 2 : ref2.getIndex();
                if (idx1 != idx2) {
                    return idx1 - idx2;
                }
            }
            String n1 = ref1 == null || ref1.name().isEmpty() ? readGetSetFieldName(o1) : ref1.name();
            String n2 = ref2 == null || ref2.name().isEmpty() ? readGetSetFieldName(o2) : ref2.name();
            if (n1 == null && n2 == null) {
                return 0;
            }
            if (n1 == null) {
                return -1;
            }
            if (n2 == null) {
                return 1;
            }
            return n1.compareTo(n2);
        });
    }

    public boolean isSimpleMemberType(Class declaringClass, Type type, Class clazz) {
        if (type == String.class) {
            return true;
        }
        if (clazz.isPrimitive()) {
            return true;
        }
        if (clazz.isEnum()) {
            return true;
        }
        if (type == Boolean.class) {
            return true;
        }
        if (type == Byte.class) {
            return true;
        }
        if (type == Short.class) {
            return true;
        }
        if (type == Character.class) {
            return true;
        }
        if (type == Integer.class) {
            return true;
        }
        if (type == Float.class) {
            return true;
        }
        if (type == Long.class) {
            return true;
        }
        if (type == Double.class) {
            return true;
        }
        if (type == boolean[].class) {
            return true;
        }
        if (type == byte[].class) {
            return true;
        }
        if (type == short[].class) {
            return true;
        }
        if (type == char[].class) {
            return true;
        }
        if (type == int[].class) {
            return true;
        }
        if (type == float[].class) {
            return true;
        }
        if (type == long[].class) {
            return true;
        }
        if (type == double[].class) {
            return true;
        }
        if (type == Boolean[].class) {
            return true;
        }
        if (type == Byte[].class) {
            return true;
        }
        if (type == Short[].class) {
            return true;
        }
        if (type == Character[].class) {
            return true;
        }
        if (type == Integer[].class) {
            return true;
        }
        if (type == Float[].class) {
            return true;
        }
        if (type == Long[].class) {
            return true;
        }
        if (type == Double[].class) {
            return true;
        }
        if (type == String[].class) {
            return true;
        }
        if (rootFactory().findEncoder(type) != null) {
            return true;
        }
        if (declaringClass == clazz) {
            return false;
        }
        if (Collection.class.isAssignableFrom(clazz) && type instanceof ParameterizedType) {
            Type[] ts = ((ParameterizedType) type).getActualTypeArguments();
            if (ts.length == 1) {
                Type t = ts[0];
                if (t == Boolean.class
                        || t == Byte.class
                        || t == Short.class
                        || t == Character.class
                        || t == Integer.class
                        || t == Float.class
                        || t == Long.class
                        || t == Double.class
                        || t == String.class
                        || rootFactory().findEncoder(t) != null
                        || ((t instanceof Class) && ((Class) t).isEnum())) {
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    ConvertFactory columnFactory(Type type, ConvertCoder[] coders, boolean encode) {
        if (Utility.isEmpty(coders)) {
            return this;
        }
        final ConvertType ct = this.getConvertType();
        List<Encodeable> encoderList = null;
        List<Decodeable> decoderList = null;
        Class readerOrWriterClass = (Class)
                ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[encode ? 1 : 0];
        for (ConvertCoder ann : coders) {
            if (!ann.type().contains(ct)) {
                continue;
            }
            Type colType = type;
            if (ann.column() != Object.class) {
                colType = ann.column();
            }
            if (encode) {
                Class<? extends Encodeable> enClazz = ann.encoder();
                if (enClazz != Encodeable.class) {
                    try {
                        boolean skip = false;
                        RedkaleClassLoader.putReflectionPublicMethods(enClazz.getName());
                        for (Method method : enClazz.getMethods()) {
                            if (method.isBridge()) {
                                continue;
                            }
                            if ("convertTo".equals(method.getName())
                                    && method.getParameterCount() == 2
                                    && Writer.class.isAssignableFrom(method.getParameterTypes()[0])) {
                                skip = !method.getParameterTypes()[0].isAssignableFrom(readerOrWriterClass);
                                break;
                            }
                        }
                        if (skip) {
                            continue;
                        }
                        Encodeable encoder = null;
                        try {
                            Field instanceField = enClazz.getField("instance");
                            if (instanceField.getType() == enClazz && Modifier.isStatic(instanceField.getModifiers())) {
                                RedkaleClassLoader.putReflectionField(enClazz.getName(), instanceField);
                                encoder = (Encodeable) instanceField.get(null);
                            }
                        } catch (Exception e) {
                            // do nothing
                        }
                        if (encoder == null) {
                            Creator<? extends Encodeable> creator = Creator.create(enClazz);
                            Class[] paramTypes = creator.paramTypes();
                            if (paramTypes.length == 0) {
                                encoder = creator.create();
                            } else if (paramTypes.length == 1) {
                                if (Class.class.isAssignableFrom(paramTypes[0])) {
                                    encoder = creator.create((Object) TypeToken.typeToClass(colType));
                                } else if (Type.class.isAssignableFrom(paramTypes[0])) {
                                    encoder = creator.create((Object) colType);
                                } else if (ConvertFactory.class.isAssignableFrom(paramTypes[0])) {
                                    encoder = creator.create(this);
                                } else {
                                    throw new ConvertException(
                                            enClazz + " not found public empty-parameter Constructor");
                                }
                            } else if (paramTypes.length == 2) {
                                if (ConvertFactory.class.isAssignableFrom(paramTypes[0])
                                        && Class.class.isAssignableFrom(paramTypes[1])) {
                                    encoder = creator.create(this, TypeToken.typeToClass(colType));
                                } else if (Class.class.isAssignableFrom(paramTypes[0])
                                        && ConvertFactory.class.isAssignableFrom(paramTypes[1])) {
                                    encoder = creator.create(TypeToken.typeToClass(colType), this);
                                } else if (ConvertFactory.class.isAssignableFrom(paramTypes[0])
                                        && Type.class.isAssignableFrom(paramTypes[1])) {
                                    encoder = creator.create(this, colType);
                                } else if (Type.class.isAssignableFrom(paramTypes[0])
                                        && ConvertFactory.class.isAssignableFrom(paramTypes[1])) {
                                    encoder = creator.create(colType, this);
                                } else {
                                    throw new ConvertException(
                                            enClazz + " not found public empty-parameter Constructor");
                                }
                            } else {
                                throw new ConvertException(enClazz + " not found public empty-parameter Constructor");
                            }
                            RedkaleClassLoader.putReflectionPublicConstructors(enClazz, enClazz.getName());
                        }
                        if (encoderList == null) {
                            encoderList = new ArrayList<>();
                        }
                        encoderList.add(encoder);
                    } catch (Throwable t) {
                        throw new ConvertException(t);
                    }
                }
            } else {
                Class<? extends Decodeable> deClazz = ann.decoder();
                if (deClazz != Decodeable.class) {
                    try {
                        boolean skip = false;
                        RedkaleClassLoader.putReflectionPublicMethods(deClazz.getName());
                        for (Method method : deClazz.getMethods()) {
                            if (method.isBridge()) {
                                continue;
                            }
                            if ("convertFrom".equals(method.getName())
                                    && method.getParameterCount() == 1
                                    && Reader.class.isAssignableFrom(method.getParameterTypes()[0])) {
                                skip = !method.getParameterTypes()[0].isAssignableFrom(readerOrWriterClass);
                                break;
                            }
                        }
                        if (skip) {
                            continue;
                        }
                        Decodeable decoder = null;
                        try {
                            Field instanceField = deClazz.getField("instance");
                            if (instanceField.getType() == deClazz && Modifier.isStatic(instanceField.getModifiers())) {
                                RedkaleClassLoader.putReflectionField(deClazz.getName(), instanceField);
                                decoder = (Decodeable) instanceField.get(null);
                            }
                        } catch (Exception e) {
                            // do nothing
                        }
                        if (decoder == null) {
                            Creator<? extends Decodeable> creator = Creator.create(deClazz);
                            Class[] paramTypes = creator.paramTypes();
                            if (paramTypes.length == 0) {
                                decoder = creator.create();
                            } else if (paramTypes.length == 1) {
                                if (Class.class.isAssignableFrom(paramTypes[0])) {
                                    decoder = creator.create((Object) TypeToken.typeToClass(colType));
                                } else if (Type.class.isAssignableFrom(paramTypes[0])) {
                                    decoder = creator.create((Object) colType);
                                } else if (ConvertFactory.class.isAssignableFrom(paramTypes[0])) {
                                    decoder = creator.create(this);
                                } else {
                                    throw new ConvertException(
                                            deClazz + " not found public empty-parameter Constructor");
                                }
                            } else if (paramTypes.length == 2) {
                                if (ConvertFactory.class.isAssignableFrom(paramTypes[0])
                                        && Class.class.isAssignableFrom(paramTypes[1])) {
                                    decoder = creator.create(this, TypeToken.typeToClass(colType));
                                } else if (Class.class.isAssignableFrom(paramTypes[0])
                                        && ConvertFactory.class.isAssignableFrom(paramTypes[1])) {
                                    decoder = creator.create(TypeToken.typeToClass(colType), this);
                                } else if (ConvertFactory.class.isAssignableFrom(paramTypes[0])
                                        && Type.class.isAssignableFrom(paramTypes[1])) {
                                    decoder = creator.create(this, colType);
                                } else if (Type.class.isAssignableFrom(paramTypes[0])
                                        && ConvertFactory.class.isAssignableFrom(paramTypes[1])) {
                                    decoder = creator.create(colType, this);
                                } else {
                                    throw new ConvertException(
                                            deClazz + " not found public empty-parameter Constructor");
                                }
                            } else {
                                throw new ConvertException(deClazz + " not found public empty-parameter Constructor");
                            }
                            RedkaleClassLoader.putReflectionPublicConstructors(deClazz, deClazz.getName());
                        }
                        if (decoderList == null) {
                            decoderList = new ArrayList<>();
                        }
                        decoderList.add(decoder);
                    } catch (Throwable t) {
                        throw new ConvertException(t);
                    }
                }
            }
        }
        if (encoderList == null && decoderList == null) {
            return this;
        }
        ConvertFactory child = createChild();
        if (encode && encoderList != null) {
            for (Encodeable item : encoderList) {
                child.register(item.getType(), item);
                if (item instanceof ObjectEncoder) {
                    ((ObjectEncoder) item).init(child);
                }
            }
        } else if (decoderList != null) {
            for (Decodeable item : decoderList) {
                child.register(item.getType(), item);
                if (item instanceof ObjectDecoder) {
                    ((ObjectDecoder) item).init(child);
                }
            }
        }
        return child;
    }

    private Consumer<BiFunction> findFieldFuncConsumer() {
        if (fieldFuncConsumer != null) {
            return fieldFuncConsumer;
        }
        return parent == null ? null : parent.findFieldFuncConsumer();
    }

    /**
     * 设置ColumnHandler初始化的处理函数
     *
     * @param consumer ColumnHandler处理函数
     */
    public final void registerFieldFuncConsumer(Consumer<BiFunction> consumer) {
        this.fieldFuncConsumer = consumer;
    }

    /**
     * 使所有类的所有被声明为ConvertColumn.ignore = true 的字段或方法变为ConvertColumn.ignore = false
     *
     * @param skipIgnore 是否忽略Ignore注解
     */
    public final void registerSkipAllIgnore(final boolean skipIgnore) {
        this.skipAllIgnore = skipIgnore;
    }

    /**
     * 使所有类的所有被声明为ConvertColumn.ignore = true 的字段或方法变为ConvertColumn.ignore = false
     *
     * @param skipIgnore 忽略ignore
     * @return 自身
     */
    public ConvertFactory<R, W> skipAllIgnore(final boolean skipIgnore) {
        this.skipAllIgnore = skipIgnore;
        return this;
    }

    /**
     * 使该类所有被声明为ConvertColumn.ignore = true 的字段或方法变为ConvertColumn.ignore = false
     *
     * @param type 指定的类
     */
    public final void registerSkipIgnore(final Class type) {
        skipIgnores.add(type);
    }

    /**
     * 屏蔽指定类所有字段，仅仅保留指定字段 <br>
     * <b>注意: 该配置优先级高于skipAllIgnore和ConvertColumnEntry配置</b>
     *
     * @param type 指定的类
     * @param excludeColumns 需要排除的字段名
     */
    public final void registerIgnoreAll(final Class type, String... excludeColumns) {
        ignoreAlls.computeIfAbsent(type, t -> new CopyOnWriteArraySet<>()).addAll(Arrays.asList(excludeColumns));
    }

    public final void registerIgnoreAll(final Class type, Collection<String> excludeColumns) {
        ignoreAlls.computeIfAbsent(type, t -> new CopyOnWriteArraySet<>()).addAll(excludeColumns);
    }

    public final void register(final Class type, boolean ignore, String... columns) {
        if (type == Map.class) {
            ignoreMapColumnLock.lock();
            try {
                if (ignore) {
                    ignoreMapColumns.addAll(List.of(columns));
                } else {
                    for (String column : columns) {
                        ignoreMapColumns.remove(column);
                    }
                }
            } finally {
                ignoreMapColumnLock.unlock();
            }
            return;
        }
        for (String column : columns) {
            register(type, column, new ConvertColumnEntry(column, ignore));
        }
    }

    public final void register(final Class type, boolean ignore, Collection<String> columns) {
        if (type == Map.class) {
            ignoreMapColumnLock.lock();
            try {
                if (ignore) {
                    ignoreMapColumns.addAll(columns);
                } else {
                    for (String column : columns) {
                        ignoreMapColumns.remove(column);
                    }
                }
            } finally {
                ignoreMapColumnLock.unlock();
            }
            return;
        }
        for (String column : columns) {
            register(type, column, new ConvertColumnEntry(column, ignore));
        }
    }

    public final boolean register(final Class type, String column, String alias) {
        return register(type, column, new ConvertColumnEntry(alias));
    }

    public final boolean register(final Class type, String column, ConvertColumnEntry entry) {
        if (type == null || column == null || entry == null) {
            return false;
        }
        Field field = null;
        try {
            field = type.getDeclaredField(column);
        } catch (Exception e) {
            // do nothing
        }
        String get = "get";
        if (field != null && (field.getType() == boolean.class || field.getType() == Boolean.class)) {
            get = "is";
        }
        char[] cols = column.toCharArray();
        cols[0] = Character.toUpperCase(cols[0]);
        final String bigColumn = new String(cols);
        try {
            register(type.getMethod(get + bigColumn), entry);
        } catch (NoSuchMethodException mex) {
            if (get.length() >= 3) { // get
                try {
                    register(type.getMethod("is" + bigColumn), entry);
                } catch (Exception ex) {
                    // do nothing
                }
            }
        } catch (Exception ex) {
            // do nothing
        }
        if (field != null) {
            try {
                register(type.getMethod("set" + bigColumn, field.getType()), entry);
            } catch (Exception ex) {
                // do nothing
            }
        }
        return field == null || register(field, entry);
    }

    public final boolean register(final AccessibleObject field, final ConvertColumnEntry entry) {
        if (field == null || entry == null) {
            return false;
        }
        this.columnEntrys.put(field, entry);
        return true;
    }

    public final void reloadCoder(final Type type) {
        this.register(type, this.createDecoder(type));
        this.register(type, this.createEncoder(type));
    }

    public final void reloadCoder(final Type type, final Class clazz) {
        this.register(type, this.createDecoder(type, clazz, false));
        this.register(type, this.createEncoder(type, clazz, false));
    }

    public final <E> void register(final Class<E> clazz, final Creator<? extends E> creator) {
        creators.put(clazz, creator);
    }

    public final <T> Creator<T> findCreator(Class<T> type) {
        Creator<T> creator = creators.get(type);
        if (creator != null) {
            return creator;
        }
        return this.parent == null ? null : this.parent.findCreator(type);
    }

    public final <T> Creator<T> loadCreator(Class<T> type) {
        Creator result = findCreator(type);
        if (result == null) {
            result = Creator.create(type);
            if (result != null) {
                creators.put(type, result);
            }
        }
        return result;
    }

    public final <C extends BiFunction> BiFunction findFieldFunc(Class<C> type) {
        BiFunction handler = fieldFuncs.get(type);
        if (handler != null) {
            return handler;
        }
        return this.parent == null ? null : this.parent.findFieldFunc(type);
    }

    // ----------------------------------------------------------------------
    public final <E> Encodeable<W, E> getAnyEncoder() {
        return (Encodeable<W, E>) anyEncoder;
    }

    public final <C extends BiFunction> void register(Class<C> clazz, C columnHandler) {
        fieldFuncs.put(Objects.requireNonNull(clazz), Objects.requireNonNull(columnHandler));
    }

    public final <E> void register(final Type clazz, final SimpledCoder<R, W, E> coder) {
        decoders.put(clazz, coder);
        encoders.put(clazz, coder);
    }

    public final <E> void register(final Type clazz, final Decodeable<R, E> decoder, final Encodeable<W, E> encoder) {
        decoders.put(clazz, decoder);
        encoders.put(clazz, encoder);
    }

    public final <E> void register(final Type clazz, final Decodeable<R, E> decoder) {
        decoders.put(clazz, decoder);
    }

    public final <E> void register(final Type clazz, final Encodeable<W, E> encoder) {
        encoders.put(clazz, encoder);
    }

    // coder = null表示删除该字段的指定SimpledCoder
    public final <E> void register(final Class clazz, final String field, final SimpledCoder<R, W, E> coder) {
        if (field == null || clazz == null) {
            return;
        }
        try {
            clazz.getDeclaredField(field);
        } catch (Exception e) {
            throw new ConvertException(clazz + " not found field(" + field + ")");
        }
        if (coder == null) {
            Map map = this.fieldCoders.get(clazz);
            if (map != null) {
                map.remove(field);
            }
        } else {
            this.fieldCoders
                    .computeIfAbsent(clazz, c -> new ConcurrentHashMap<>())
                    .put(field, coder);
        }
    }

    public final <E> SimpledCoder<R, W, E> findFieldCoder(final Type clazz, final String field) {
        if (field == null) {
            return null;
        }
        Map<String, SimpledCoder<R, W, ?>> map = this.fieldCoders.get(clazz);
        if (map == null) {
            return parent == null ? null : parent.findFieldCoder(clazz, field);
        }
        return (SimpledCoder) map.get(field);
    }

    public final <E> Decodeable<R, E> findDecoder(final Type type) {
        Decodeable<R, E> rs = (Decodeable<R, E>) decoders.get(type);
        if (rs != null) {
            return rs;
        }
        return this.parent == null ? null : this.parent.findDecoder(type);
    }

    public final <E> Encodeable<W, E> findEncoder(final Type type) {
        Encodeable<W, E> rs = (Encodeable<W, E>) encoders.get(type);
        if (rs != null) {
            return rs;
        }
        return this.parent == null ? null : this.parent.findEncoder(type);
    }

    public final <E> Decodeable<R, E> loadDecoder(final Type type) {
        Decodeable<R, E> decoder = findDecoder(type);
        if (decoder != null) {
            return decoder;
        }
        if (type instanceof GenericArrayType) {
            return createArrayDecoder(type);
        }
        Class clazz;
        if (type instanceof ParameterizedType) {
            final ParameterizedType pts = (ParameterizedType) type;
            clazz = (Class) (pts).getRawType();
        } else if (type instanceof TypeVariable) { // e.g.  <? extends E>
            final TypeVariable tv = (TypeVariable) type;
            Class cz = tv.getBounds().length == 0 ? Object.class : null;
            for (Type f : tv.getBounds()) {
                if (f instanceof Class) {
                    cz = (Class) f;
                    break;
                }
            }
            clazz = cz;
            if (cz == null) {
                throw new ConvertException("not support the type (" + type + ")");
            }
        } else if (type instanceof WildcardType) { // e.g. <? extends Serializable>
            final WildcardType wt = (WildcardType) type;
            Class cz = null;
            for (Type f : wt.getUpperBounds()) {
                if (f instanceof Class) {
                    cz = (Class) f;
                    break;
                }
            }
            clazz = cz;
            if (cz == null) {
                throw new ConvertException("not support the type (" + type + ")");
            }
        } else if (type instanceof Class) {
            clazz = (Class) type;
        } else {
            throw new ConvertException("not support the type (" + type + ")");
        }
        // 此处不能再findDecoder，否则type与class不一致, 如: RetResult 和 RetResult<Integer>
        return createDecoder(type, clazz, false);
    }

    public final <E> Decodeable<R, E> createDecoder(final Type type) {
        return createDecoder(type, false);
    }

    public final <E> Decodeable<R, E> createDecoder(final Type type, boolean skipCustomMethod) {
        Class clazz;
        if (type instanceof ParameterizedType) {
            final ParameterizedType pts = (ParameterizedType) type;
            clazz = (Class) (pts).getRawType();
        } else if (type instanceof Class) {
            clazz = (Class) type;
        } else {
            throw new ConvertException("not support the type (" + type + ")");
        }
        return createDecoder(type, clazz, skipCustomMethod);
    }

    private <E> Decodeable<R, E> createDecoder(final Type type, final Class clazz, boolean skipCustomMethod) {
        Decodeable<R, E> decoder = null;
        ObjectDecoder od = null;
        if (clazz.isEnum()) {
            decoder = createEnumSimpledCoder(clazz);
        } else if (clazz.isArray()) {
            decoder = createArrayDecoder(type);
        } else if (Collection.class.isAssignableFrom(clazz)) {
            decoder = createCollectionDecoder(type);
        } else if (Stream.class.isAssignableFrom(clazz)) {
            decoder = createStreamDecoder(type);
        } else if (Map.class.isAssignableFrom(clazz)) {
            decoder = createMapDecoder(type);
        } else if (CompletionHandler.class.isAssignableFrom(clazz)) {
            decoder = CompletionHandlerSimpledCoder.instance;
        } else if (Optional.class == clazz) {
            decoder = new OptionalCoder(this, type);
        } else if (clazz == Object.class) {
            od = createObjectDecoder(type);
            decoder = od;
        } else if (!clazz.getName().startsWith("java.")
                || java.net.HttpCookie.class == clazz
                || java.util.AbstractMap.SimpleEntry.class == clazz
                || clazz.getName().startsWith("java.awt.geom.Point2D")) {
            Decodeable simpleCoder = null;
            if (!skipCustomMethod) {
                for (Class subclazz : getSuperClasses(clazz)) {
                    for (final Method method : subclazz.getDeclaredMethods()) {
                        if (!Modifier.isStatic(method.getModifiers())) {
                            continue;
                        }
                        Class[] paramTypes = method.getParameterTypes();
                        if (paramTypes.length != 1 && paramTypes.length != 2) {
                            continue;
                        }
                        if (paramTypes[0] != ConvertFactory.class && paramTypes[0] != this.getClass()) {
                            continue;
                        }
                        if (paramTypes.length == 2 && paramTypes[1] != Class.class && paramTypes[1] != Type.class) {
                            continue;
                        }
                        if (!Decodeable.class.isAssignableFrom(method.getReturnType())) {
                            continue;
                        }
                        if (Modifier.isPrivate(method.getModifiers()) && subclazz != clazz) {
                            continue; // 声明private的只能被自身类使用
                        }
                        try {
                            method.setAccessible(true);
                            simpleCoder = (Decodeable)
                                    (paramTypes.length == 2
                                            ? (paramTypes[1] == Type.class
                                                    ? method.invoke(null, this, type)
                                                    : method.invoke(null, this, clazz))
                                            : method.invoke(null, this));
                            RedkaleClassLoader.putReflectionDeclaredMethods(subclazz.getName());
                            RedkaleClassLoader.putReflectionMethod(subclazz.getName(), method);
                            break;
                        } catch (Exception e) {
                            // do nothing
                        }
                    }
                    if (simpleCoder != null) {
                        break;
                    }
                }
            }
            if (simpleCoder == null) {
                if (type instanceof Class) {
                    Class<?> typeclz = (Class) type;
                    ConvertImpl ci = typeclz.getAnnotation(ConvertImpl.class);
                    if (ci != null && ci.types().length > 0) {
                        for (Class sub : ci.types()) {
                            if (!typeclz.isAssignableFrom(sub)) {
                                throw new ConvertException("@" + ConvertImpl.class.getSimpleName() + ".types(" + sub
                                        + ") must be " + typeclz + "'s subclass");
                            }
                        }
                        decoder = createMultiImplDecoder(ci.types());
                    }
                }
                if (decoder == null) {
                    Type impl = formatObjectType(type);
                    od = createObjectDecoder(impl);
                    decoder = od;
                }
            } else {
                decoder = simpleCoder;
            }
        }
        if (decoder == null) {
            throw new ConvertException("not support the type (" + type + ")");
        }
        register(type, decoder);
        if (od != null) {
            od.init(this);
        }
        return decoder;
    }

    public final <E> Encodeable<W, E> loadEncoder(final Type type) {
        Encodeable<W, E> encoder = findEncoder(type);
        if (encoder != null) {
            return encoder;
        }
        if (type instanceof GenericArrayType) {
            return createArrayEncoder(type);
        }
        Class clazz;
        if (type instanceof ParameterizedType) {
            final ParameterizedType pts = (ParameterizedType) type;
            clazz = (Class) (pts).getRawType();
        } else if (type instanceof TypeVariable) {
            TypeVariable tv = (TypeVariable) type;
            Type t = Object.class;
            if (tv.getBounds().length == 1) {
                t = tv.getBounds()[0];
            }
            if (!(t instanceof Class)) {
                t = Object.class;
            }
            clazz = (Class) t;
        } else if (type instanceof Class) {
            clazz = (Class) type;
        } else {
            throw new ConvertException("not support the type (" + type + ")");
        }
        // 此处不能再findEncoder，否则type与class不一致, 如: RetResult 和 RetResult<Integer>
        return createEncoder(type, clazz, false);
    }

    public final <E> Encodeable<W, E> createEncoder(final Type type) {
        return createEncoder(type, false);
    }

    public final <E> Encodeable<W, E> createEncoder(final Type type, boolean skipCustomMethod) {
        Class clazz;
        if (type instanceof ParameterizedType) {
            final ParameterizedType pts = (ParameterizedType) type;
            clazz = (Class) (pts).getRawType();
        } else if (type instanceof Class) {
            clazz = (Class) type;
        } else {
            throw new ConvertException("not support the type (" + type + ")");
        }
        return createEncoder(type, clazz, skipCustomMethod);
    }

    private <E> Encodeable<W, E> createEncoder(final Type type, final Class clazz, boolean skipCustomMethod) {
        Encodeable<W, E> encoder = null;
        ObjectEncoder oe = null;
        if (clazz.isEnum()) {
            encoder = createEnumSimpledCoder(clazz);
        } else if (clazz.isArray()) {
            encoder = createArrayEncoder(type);
        } else if (Collection.class.isAssignableFrom(clazz)) {
            encoder = createCollectionEncoder(type);
        } else if (Stream.class.isAssignableFrom(clazz)) {
            encoder = createStreamEncoder(type);
        } else if (Map.class.isAssignableFrom(clazz)) {
            encoder = createMapEncoder(type);
        } else if (CompletionHandler.class.isAssignableFrom(clazz)) {
            encoder = CompletionHandlerSimpledCoder.instance;
        } else if (Optional.class == clazz) {
            encoder = new OptionalCoder(this, type);
        } else if (clazz == Object.class) {
            return (Encodeable<W, E>) this.anyEncoder;
        } else if (!clazz.getName().startsWith("java.")
                || java.net.HttpCookie.class == clazz
                || java.util.Map.Entry.class == clazz
                || java.util.AbstractMap.SimpleEntry.class == clazz) {
            Encodeable simpleCoder = null;
            if (!skipCustomMethod) {
                for (Class subclazz : getSuperClasses(clazz)) {
                    for (final Method method : subclazz.getDeclaredMethods()) {
                        if (!Modifier.isStatic(method.getModifiers())) {
                            continue;
                        }
                        Class[] paramTypes = method.getParameterTypes();
                        if (paramTypes.length != 1 && paramTypes.length != 2) {
                            continue;
                        }
                        if (paramTypes[0] != ConvertFactory.class && paramTypes[0] != this.getClass()) {
                            continue;
                        }
                        if (paramTypes.length == 2 && paramTypes[1] != Class.class && paramTypes[1] != Type.class) {
                            continue;
                        }
                        if (!Encodeable.class.isAssignableFrom(method.getReturnType())) {
                            continue;
                        }
                        if (Modifier.isPrivate(method.getModifiers()) && subclazz != clazz) {
                            continue; // 声明private的只能被自身类使用
                        }
                        try {
                            method.setAccessible(true);
                            simpleCoder = (Encodeable)
                                    (paramTypes.length == 2
                                            ? (paramTypes[1] == Type.class
                                                    ? method.invoke(null, this, type)
                                                    : method.invoke(null, this, clazz))
                                            : method.invoke(null, this));
                            RedkaleClassLoader.putReflectionDeclaredMethods(subclazz.getName());
                            RedkaleClassLoader.putReflectionMethod(subclazz.getName(), method);
                            break;
                        } catch (Exception e) {
                            // do nothing
                        }
                    }
                    if (simpleCoder != null) {
                        break;
                    }
                }
            }
            if (simpleCoder == null) {
                Type impl = formatObjectType(type);
                encoder = createDyncEncoder(impl);
                if (encoder == null) {
                    oe = createObjectEncoder(impl);
                    encoder = oe;
                }
            } else {
                encoder = simpleCoder;
            }
        }
        if (encoder == null) {
            throw new ConvertException("not support the type (" + type + ")");
        }
        register(type, encoder);
        if (oe != null) {
            oe.init(this);
        }
        return encoder;
    }

    private Set<Class> getSuperClasses(final Class clazz) {
        Set<Class> set = new LinkedHashSet<>();
        set.add(clazz);
        Class recursClass = clazz;
        while ((recursClass = recursClass.getSuperclass()) != null) {
            if (recursClass == Object.class) {
                break;
            }
            set.addAll(getSuperClasses(recursClass));
        }
        for (Class sub : clazz.getInterfaces()) {
            set.addAll(getSuperClasses(sub));
        }
        return set;
    }
}
