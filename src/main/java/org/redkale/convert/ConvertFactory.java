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
import java.util.regex.Pattern;
import java.util.stream.*;
import org.redkale.convert.bson.BsonConvert;
import org.redkale.convert.ext.*;
import org.redkale.convert.json.JsonConvert;
import org.redkale.util.*;

/**
 * 序列化模块的工厂类，用于注册自定义的序列化类型，获取Convert
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <R> Reader输入的子类
 * @param <W> Writer输出的子类
 */
@SuppressWarnings("unchecked")
public abstract class ConvertFactory<R extends Reader, W extends Writer> {

    private static final AtomicBoolean loaderInited = new AtomicBoolean();

    private static Convert defProtobufConvert;

    private final ConvertFactory parent;

    protected Convert<R, W> convert;

    protected boolean tiny; //String类型值为""，Boolean类型值为false时是否需要输出， 默认为true

    private final Encodeable<W, ?> anyEncoder = new AnyEncoder(this);

    //-----------------------------------------------------------------------------------
    private final ConcurrentHashMap<Class, Creator> creators = new ConcurrentHashMap();

    private final ConcurrentHashMap<String, Class> entitys = new ConcurrentHashMap();

    private final ConcurrentHashMap<Type, Map<String, SimpledCoder<R, W, ?>>> fieldCoders = new ConcurrentHashMap();

    private final ConcurrentHashMap<Type, Decodeable<R, ?>> decoders = new ConcurrentHashMap();

    private final ConcurrentHashMap<Type, Encodeable<W, ?>> encoders = new ConcurrentHashMap();

    private final ConcurrentHashMap<AccessibleObject, ConvertColumnEntry> columnEntrys = new ConcurrentHashMap();

    private final Set<Class> skipIgnores = new HashSet();

    //key:需要屏蔽的字段；value：排除的字段名
    private final ConcurrentHashMap<Class, Set<String>> ignoreAlls = new ConcurrentHashMap();

    private boolean skipAllIgnore = false;

    protected ConvertFactory(ConvertFactory<R, W> parent, boolean tiny) {
        this.tiny = tiny;
        this.parent = parent;
        if (parent == null) {
            //---------------------------------------------------------

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
            this.register(AtomicInteger.class, AtomicIntegerSimpledCoder.instance);
            this.register(AtomicLong.class, AtomicLongSimpledCoder.instance);
            this.register(BigInteger.class, BigIntegerSimpledCoder.instance);
            this.register(BigDecimal.class, BigDecimalSimpledCoder.instance);
            this.register(InetAddress.class, InetAddressSimpledCoder.instance);
            this.register(LongAdder.class, LongAdderSimpledCoder.instance);
            this.register(DLong.class, DLongSimpledCoder.instance);
            this.register(Class.class, TypeSimpledCoder.instance);
            this.register(InetSocketAddress.class, InetAddressSimpledCoder.InetSocketAddressSimpledCoder.instance);
            this.register(Pattern.class, PatternSimpledCoder.instance);
            this.register(File.class, FileSimpledCoder.instance);
            this.register(Throwable.class, ThrowableSimpledCoder.instance);
            this.register(CompletionHandler.class, CompletionHandlerSimpledCoder.instance);
            this.register(URL.class, URLSimpledCoder.instance);
            this.register(URI.class, URISimpledCoder.instance);
            //---------------------------------------------------------
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
            //---------------------------------------------------------
            this.register(AnyValue.class, Creator.create(AnyValue.DefaultAnyValue.class));
            this.register(HttpCookie.class, new Creator<HttpCookie>() {
                @Override
                @ConstructorParameters({"name", "value"})
                public HttpCookie create(Object... params) {
                    return new HttpCookie((String) params[0], (String) params[1]);
                }

            });
            try {
                Class sqldateClass = Thread.currentThread().getContextClassLoader().loadClass("java.sql.Date");
                Invoker<Object, Object> sqldateInvoker = Invoker.create(sqldateClass, "valueOf", String.class);
                this.register(sqldateClass, new SimpledCoder<R, W, Object>() {

                    @Override
                    public void convertTo(W out, Object value) {
                        out.writeSmallString(value == null ? null : value.toString());
                    }

                    @Override
                    public Object convertFrom(R in) {
                        String t = in.readSmallString();
                        return t == null ? null : sqldateInvoker.invoke(null, t);
                    }

                });
                Class sqltimeClass = Thread.currentThread().getContextClassLoader().loadClass("java.sql.Time");
                Invoker<Object, Object> sqltimeInvoker = Invoker.create(sqltimeClass, "valueOf", String.class);
                this.register(sqltimeClass, new SimpledCoder<R, W, Object>() {

                    @Override
                    public void convertTo(W out, Object value) {
                        out.writeSmallString(value == null ? null : value.toString());
                    }

                    @Override
                    public Object convertFrom(R in) {
                        String t = in.readSmallString();
                        return t == null ? null : sqltimeInvoker.invoke(null, t);
                    }

                });
                Class timestampClass = Thread.currentThread().getContextClassLoader().loadClass("java.sql.Timestamp");
                Invoker<Object, Object> timestampInvoker = Invoker.create(timestampClass, "valueOf", String.class);
                this.register(timestampClass, new SimpledCoder<R, W, Object>() {

                    @Override
                    public void convertTo(W out, Object value) {
                        out.writeSmallString(value == null ? null : value.toString());
                    }

                    @Override
                    public Object convertFrom(R in) {
                        String t = in.readSmallString();
                        return t == null ? null : timestampInvoker.invoke(null, t);
                    }

                });
            } catch (Throwable t) {
            }
        }
    }

    public ConvertFactory parent() {
        return this.parent;
    }

    public static Convert findConvert(ConvertType type) {
        if (type == null) return null;
        if (type == ConvertType.JSON) return JsonConvert.root();
        if (type == ConvertType.BSON) return BsonConvert.root();
        if (loaderInited.get()) {
            if (type == ConvertType.PROTOBUF) return defProtobufConvert;
        }
        synchronized (loaderInited) {
            if (!loaderInited.get()) {
                Iterator<ConvertProvider> it = ServiceLoader.load(ConvertProvider.class).iterator();
                RedkaleClassLoader.putServiceLoader(ConvertProvider.class);
                while (it.hasNext()) {
                    ConvertProvider cl = it.next();
                    RedkaleClassLoader.putReflectionPublicConstructors(cl.getClass(), cl.getClass().getName());
                    if (cl.type() == ConvertType.PROTOBUF) defProtobufConvert = cl.convert();
                }
                loaderInited.set(true);
            }
        }
        return type == ConvertType.PROTOBUF ? defProtobufConvert : null;
    }

    protected static boolean getSystemPropertyBoolean(String key, String parentkey, boolean defvalue) {
        return Boolean.parseBoolean(System.getProperty(key, System.getProperty(parentkey, String.valueOf(defvalue))));
    }

    public abstract ConvertType getConvertType();

    public abstract boolean isReversible(); //是否可逆的

    public abstract boolean isFieldSort(); //当ConvertColumn.index相同时是否按字段名称排序

    public abstract ConvertFactory createChild();

    public abstract ConvertFactory createChild(boolean tiny);

    protected SimpledCoder createEnumSimpledCoder(Class enumClass) {
        return new EnumSimpledCoder(enumClass);
    }

    protected Type formatObjectType(Type type) {
        if (type instanceof Class) {
            Class<?> clazz = (Class) type;
            ConvertImpl ci = clazz.getAnnotation(ConvertImpl.class);
            if (ci != null && ci.value() != Object.class) {
                if (!clazz.isAssignableFrom(ci.value())) {
                    throw new ConvertException("@" + ConvertImpl.class.getSimpleName() + ".value(" + ci.value() + ") must be " + clazz + "'s subclass");
                }
                if (!Modifier.isAbstract(clazz.getModifiers()) && !Modifier.isInterface(clazz.getModifiers())) {
                    throw new ConvertException("@" + ConvertImpl.class.getSimpleName() + " must at interface or abstract class, but " + clazz + " not");
                }
                Class impl = ci.value();
                if (Modifier.isAbstract(impl.getModifiers()) || Modifier.isInterface(impl.getModifiers())) {
                    throw new ConvertException("@" + ConvertImpl.class.getSimpleName() + " at class " + impl + " cannot be interface or abstract class");
                }
                return impl;
            }
        }
        return type;
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

    public ConvertFactory tiny(boolean tiny) {
        this.tiny = tiny;
        return this;
    }

    public boolean isConvertDisabled(AccessibleObject element) {
        ConvertDisabled[] ccs = element.getAnnotationsByType(ConvertDisabled.class);
        if (ccs.length == 0 && element instanceof Method) {
            final Method method = (Method) element;
            String fieldName = readGetSetFieldName(method);
            if (fieldName != null) {
                try {
                    ccs = method.getDeclaringClass().getDeclaredField(fieldName).getAnnotationsByType(ConvertDisabled.class);
                } catch (Exception e) { //说明没有该字段，忽略异常
                }
            }
        }
        final ConvertType ct = this.getConvertType();
        for (ConvertDisabled ref : ccs) {
            if (ref.type().contains(ct)) return true;
        }
        return false;
    }

    public ConvertColumnEntry findRef(Class clazz, AccessibleObject element) {
        if (element == null) return null;
        ConvertColumnEntry en = this.columnEntrys.get(element);
        Set<String> onlyColumns = ignoreAlls.get(clazz);
        if (en != null && onlyColumns == null) return en;
        final ConvertType ct = this.getConvertType();
        ConvertColumn[] ccs = element.getAnnotationsByType(ConvertColumn.class);
        String fieldName = null;
        if (ccs.length == 0 && element instanceof Method) {
            final Method method = (Method) element;
            fieldName = readGetSetFieldName(method);
            if (fieldName != null) {
                Class mclz = method.getDeclaringClass();
                do {
                    try {
                        ccs = mclz.getDeclaredField(fieldName).getAnnotationsByType(ConvertColumn.class);
                        break;
                    } catch (Exception e) { //说明没有该字段，忽略异常
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
            if (!onlyColumns.contains(fieldName)) return new ConvertColumnEntry(fieldName, true);
        }
        for (ConvertColumn ref : ccs) {
            if (ref.type().contains(ct)) {
                String realName = ref.name().isEmpty() ? fieldName : ref.name();
                if (onlyColumns != null && fieldName != null) {
                    if (!onlyColumns.contains(realName)) return new ConvertColumnEntry(realName, true);
                }
                ConvertColumnEntry entry = new ConvertColumnEntry(ref);
                if (skipAllIgnore) {
                    entry.setIgnore(false);
                    return entry;
                }
                if (skipIgnores.isEmpty()) {
                    if (onlyColumns != null && realName != null && onlyColumns.contains(realName)) entry.setIgnore(false);
                    return entry;
                }
                if (skipIgnores.contains(((Member) element).getDeclaringClass())) entry.setIgnore(false);
                return entry;
            }
        }
        return null;
    }

    static Field readGetSetField(Method method) {
        String name = readGetSetFieldName(method);
        if (name == null) return null;
        try {
            return method.getDeclaringClass().getDeclaredField(name);
        } catch (Exception e) {
            return null;
        }
    }

    static String readGetSetFieldName(Method method) {
        if (method == null) return null;
        String fname = method.getName();
        if (!(fname.startsWith("is") && fname.length() > 2)
            && !(fname.startsWith("get") && fname.length() > 3)
            && !(fname.startsWith("set") && fname.length() > 3)) return fname; //record类会直接用field名作为method名
        fname = fname.substring(fname.startsWith("is") ? 2 : 3);
        if (fname.length() > 1 && !(fname.charAt(1) >= 'A' && fname.charAt(1) <= 'Z')) {
            fname = Character.toLowerCase(fname.charAt(0)) + fname.substring(1);
        } else if (fname.length() == 1) {
            fname = "" + Character.toLowerCase(fname.charAt(0));
        }
        return fname;
    }

    final String getEntityAlias(Class clazz) {
        if (clazz == String.class) return "A";
        if (clazz == int.class) return "I";
        if (clazz == Integer.class) return "i";
        if (clazz == long.class) return "J";
        if (clazz == Long.class) return "j";
        if (clazz == byte.class) return "B";
        if (clazz == Byte.class) return "b";
        if (clazz == boolean.class) return "Z";
        if (clazz == Boolean.class) return "z";
        if (clazz == short.class) return "S";
        if (clazz == Short.class) return "s";
        if (clazz == char.class) return "C";
        if (clazz == Character.class) return "c";
        if (clazz == float.class) return "F";
        if (clazz == Float.class) return "f";
        if (clazz == double.class) return "D";
        if (clazz == Double.class) return "d";

        if (clazz == String[].class) return "[A";
        if (clazz == int[].class) return "[I";
        if (clazz == long[].class) return "[J";
        if (clazz == byte[].class) return "[B";
        if (clazz == boolean[].class) return "[Z";
        if (clazz == short[].class) return "[S";
        if (clazz == char[].class) return "[C";
        if (clazz == float[].class) return "[F";
        if (clazz == double[].class) return "[D";

        ConvertEntity ce = (ConvertEntity) clazz.getAnnotation(ConvertEntity.class);
        if (ce != null && findEntityAlias(ce.value()) == null) entitys.put(ce.value(), clazz);
        return ce == null ? clazz.getName() : ce.value();
    }

    final Class getEntityAlias(String name) {
        if ("A".equals(name)) return String.class;
        if ("I".equals(name)) return int.class;
        if ("i".equals(name)) return Integer.class;
        if ("J".equals(name)) return long.class;
        if ("j".equals(name)) return Long.class;
        if ("B".equals(name)) return byte.class;
        if ("b".equals(name)) return Byte.class;
        if ("Z".equals(name)) return boolean.class;
        if ("z".equals(name)) return Boolean.class;
        if ("S".equals(name)) return short.class;
        if ("s".equals(name)) return Short.class;
        if ("C".equals(name)) return char.class;
        if ("c".equals(name)) return Character.class;
        if ("F".equals(name)) return float.class;
        if ("f".equals(name)) return Float.class;
        if ("D".equals(name)) return double.class;
        if ("d".equals(name)) return Double.class;

        if ("[A".equals(name)) return String[].class;
        if ("[I".equals(name)) return int[].class;
        if ("[J".equals(name)) return long[].class;
        if ("[B".equals(name)) return byte[].class;
        if ("[Z".equals(name)) return boolean[].class;
        if ("[S".equals(name)) return short[].class;
        if ("[C".equals(name)) return char[].class;
        if ("[F".equals(name)) return float[].class;
        if ("[D".equals(name)) return double[].class;

        Class clazz = findEntityAlias(name);
        try {
            return clazz == null ? Thread.currentThread().getContextClassLoader().loadClass(name) : clazz;
        } catch (Exception ex) {
            throw new ConvertException("convert entity is " + name, ex);
        }
    }

    ConvertFactory columnFactory(Class type, ConvertCoder[] coders, boolean encode) {
        if (coders == null || coders.length < 1) return this;
        final ConvertType ct = this.getConvertType();
        List<Encodeable> encoderList = null;
        List<Decodeable> decoderList = null;
        Class readerOrWriterClass = (Class) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[encode ? 1 : 0];
        for (ConvertCoder ann : coders) {
            if (!ann.type().contains(ct)) continue;
            SimpledCoder coder = null;
            Class<? extends SimpledCoder> clazz1 = ann.coder();
            if (clazz1 != SimpledCoder.class) {
                try {
                    boolean skip = false;
                    RedkaleClassLoader.putReflectionPublicMethods(clazz1.getName());
                    for (Method method : clazz1.getMethods()) {
                        if (method.isBridge()) continue;
                        if (encode) {
                            if ("convertTo".equals(method.getName()) && method.getParameterCount() == 2 && Writer.class.isAssignableFrom(method.getParameterTypes()[0])) {
                                skip = !method.getParameterTypes()[0].isAssignableFrom(readerOrWriterClass);
                                break;
                            }
                        } else {
                            if ("convertFrom".equals(method.getName()) && method.getParameterCount() == 1 && Reader.class.isAssignableFrom(method.getParameterTypes()[0])) {
                                skip = !method.getParameterTypes()[0].isAssignableFrom(readerOrWriterClass);
                                break;
                            }
                        }
                    }
                    if (skip) continue;
                    Field instanceField = clazz1.getField("instance");
                    if (Modifier.isStatic(instanceField.getModifiers()) && instanceField.getType() == clazz1) {
                        RedkaleClassLoader.putReflectionField(clazz1.getName(), instanceField);
                        coder = (SimpledCoder) instanceField.get(null);
                    }
                } catch (Throwable t) {
                }
                if (coder == null) {
                    try {
                        coder = (SimpledCoder) clazz1.getConstructor().newInstance();
                        RedkaleClassLoader.putReflectionPublicConstructors(clazz1, clazz1.getName());
                    } catch (Throwable t) {
                        t.printStackTrace();
                        continue;
                    }
                }
                if (encode) {
                    if (encoderList == null) encoderList = new ArrayList<>();
                    encoderList.add(coder);
                } else {
                    if (decoderList == null) decoderList = new ArrayList<>();
                    decoderList.add(coder);
                }
            }
            if (coder == null) {
                Class colType = type;
                if (ann.column() != Object.class) colType = ann.column();
                if (encode) {
                    Class<? extends Encodeable> clazz2 = ann.encoder();
                    if (clazz2 != Encodeable.class) {
                        try {
                            boolean skip = false;
                            RedkaleClassLoader.putReflectionPublicMethods(clazz2.getName());
                            for (Method method : clazz2.getMethods()) {
                                if (method.isBridge()) continue;
                                if ("convertTo".equals(method.getName()) && method.getParameterCount() == 2 && Writer.class.isAssignableFrom(method.getParameterTypes()[0])) {
                                    skip = !method.getParameterTypes()[0].isAssignableFrom(readerOrWriterClass);
                                    break;
                                }
                            }
                            if (skip) continue;
                            Encodeable encoder = null;
                            Constructor constructor = clazz2.getConstructors()[0];
                            Parameter[] params = constructor.getParameters();
                            Class[] paramTypes = new Class[params.length];
                            for (int i = 0; i < paramTypes.length; i++) {
                                paramTypes[i] = params[i].getType();
                            }
                            if (params.length == 0) {
                                encoder = (Encodeable) constructor.newInstance();
                            } else if (params.length == 1) {
                                if (paramTypes[0] != Type.class) throw new RuntimeException(clazz2 + " not found public empty-parameter Constructor");
                                encoder = (Encodeable) constructor.newInstance(colType);
                            } else if (params.length == 2) {
                                if (paramTypes[0] == ConvertFactory.class && paramTypes[1] == Type.class) {
                                    encoder = (Encodeable) constructor.newInstance(this, colType);
                                } else if (paramTypes[0] == Type.class && paramTypes[1] == ConvertFactory.class) {
                                    encoder = (Encodeable) constructor.newInstance(colType, this);
                                } else {
                                    throw new RuntimeException(clazz2 + " not found public empty-parameter Constructor");
                                }
                            } else {
                                throw new RuntimeException(clazz2 + " not found public empty-parameter Constructor");
                            }
                            RedkaleClassLoader.putReflectionPublicConstructors(clazz2, clazz2.getName());
                            if (encoderList == null) encoderList = new ArrayList<>();
                            encoderList.add(encoder);
                        } catch (Throwable t) {
                            t.printStackTrace();
                            continue;
                        }
                    }
                } else {
                    Class<? extends Decodeable> clazz2 = ann.decoder();
                    if (clazz2 != Decodeable.class) {
                        try {
                            boolean skip = false;
                            RedkaleClassLoader.putReflectionPublicMethods(clazz2.getName());
                            for (Method method : clazz2.getMethods()) {
                                if (method.isBridge()) continue;
                                if ("convertFrom".equals(method.getName()) && method.getParameterCount() == 1 && Reader.class.isAssignableFrom(method.getParameterTypes()[0])) {
                                    skip = !method.getParameterTypes()[0].isAssignableFrom(readerOrWriterClass);
                                    break;
                                }
                            }
                            if (skip) continue;
                            Decodeable decoder = null;
                            Constructor constructor = clazz2.getConstructors()[0];
                            Parameter[] params = constructor.getParameters();
                            Class[] paramTypes = new Class[params.length];
                            for (int i = 0; i < paramTypes.length; i++) {
                                paramTypes[i] = params[i].getType();
                            }
                            if (params.length == 0) {
                                decoder = (Decodeable) constructor.newInstance();
                            } else if (params.length == 1) {
                                if (paramTypes[0] != Type.class) throw new RuntimeException(clazz2 + " not found public empty-parameter Constructor");
                                decoder = (Decodeable) constructor.newInstance(colType);
                            } else if (params.length == 2) {
                                if (paramTypes[0] == ConvertFactory.class && paramTypes[1] == Type.class) {
                                    decoder = (Decodeable) constructor.newInstance(this, colType);
                                } else if (paramTypes[0] == Type.class && paramTypes[1] == ConvertFactory.class) {
                                    decoder = (Decodeable) constructor.newInstance(colType, this);
                                } else {
                                    throw new RuntimeException(clazz2 + " not found public empty-parameter Constructor");
                                }
                            } else {
                                throw new RuntimeException(clazz2 + " not found public empty-parameter Constructor");
                            }
                            RedkaleClassLoader.putReflectionPublicConstructors(clazz2, clazz2.getName());
                            if (decoderList == null) decoderList = new ArrayList<>();
                            decoderList.add(decoder);
                        } catch (Throwable t) {
                            t.printStackTrace();
                            continue;
                        }
                    }
                }
            }
        }
        if (encoderList == null && decoderList == null) return this;
        ConvertFactory child = createChild();
        if (encode) {
            for (Encodeable item : encoderList) {
                child.register(item.getType(), item);
                if (item instanceof ObjectEncoder) {
                    ((ObjectEncoder) item).init(child);
                }
            }
        } else {
            for (Decodeable item : decoderList) {
                child.register(item.getType(), item);
                if (item instanceof ObjectDecoder) {
                    ((ObjectDecoder) item).init(child);
                }
            }
        }
        return child;
    }

    private Class findEntityAlias(String name) {
        Class clazz = entitys.get(name);
        return parent == null ? clazz : parent.findEntityAlias(name);
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
     *
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
     * @param type           指定的类
     * @param excludeColumns 需要排除的字段名
     */
    public final void registerIgnoreAll(final Class type, String... excludeColumns) {
        Set<String> set = ignoreAlls.get(type);
        if (set == null) {
            ignoreAlls.put(type, new HashSet<>(Arrays.asList(excludeColumns)));
        } else {
            set.addAll(Arrays.asList(excludeColumns));
        }
    }

    public final void registerIgnoreAll(final Class type, Collection<String> excludeColumns) {
        Set<String> set = ignoreAlls.get(type);
        if (set == null) {
            ignoreAlls.put(type, new HashSet<>(excludeColumns));
        } else {
            set.addAll(new ArrayList(excludeColumns));
        }
    }

    public final void register(final Class type, boolean ignore, String... columns) {
        for (String column : columns) {
            register(type, column, new ConvertColumnEntry(column, ignore));
        }
    }

    public final void register(final Class type, boolean ignore, Collection<String> columns) {
        for (String column : columns) {
            register(type, column, new ConvertColumnEntry(column, ignore));
        }
    }

    public final boolean register(final Class type, String column, String alias) {
        return register(type, column, new ConvertColumnEntry(alias));
    }

    public final boolean register(final Class type, String column, ConvertColumnEntry entry) {
        if (type == null || column == null || entry == null) return false;
        Field field = null;
        try {
            field = type.getDeclaredField(column);
        } catch (Exception e) {
        }
        String get = "get";
        if (field != null && (field.getType() == boolean.class || field.getType() == Boolean.class)) get = "is";
        char[] cols = column.toCharArray();
        cols[0] = Character.toUpperCase(cols[0]);
        final String bigColumn = new String(cols);
        try {
            register(type.getMethod(get + bigColumn), entry);
        } catch (NoSuchMethodException mex) {
            if (get.length() >= 3) { //get
                try {
                    register(type.getMethod("is" + bigColumn), entry);
                } catch (Exception ex) {
                }
            }
        } catch (Exception ex) {
        }
        try {
            register(type.getMethod("set" + bigColumn, field.getType()), entry);
        } catch (Exception ex) {
        }
        return field == null ? true : register(field, entry);
    }

    public final <E> boolean register(final AccessibleObject field, final ConvertColumnEntry entry) {
        if (field == null || entry == null) return false;
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
        if (creator != null) return creator;
        return this.parent == null ? null : this.parent.findCreator(type);
    }

    public final <T> Creator<T> loadCreator(Class<T> type) {
        Creator result = findCreator(type);
        if (result == null) {
            result = Creator.create(type);
            if (result != null) creators.put(type, result);
        }
        return result;
    }

    //----------------------------------------------------------------------
    public final <E> Encodeable<W, E> getAnyEncoder() {
        return (Encodeable<W, E>) anyEncoder;
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

    //coder = null表示删除该字段的指定SimpledCoder
    public final <E> void register(final Class clazz, final String field, final SimpledCoder<R, W, E> coder) {
        if (field == null || clazz == null) return;
        try {
            clazz.getDeclaredField(field);
        } catch (Exception e) {
            throw new RuntimeException(clazz + " not found field(" + field + ")");
        }
        if (coder == null) {
            Map map = this.fieldCoders.get(clazz);
            if (map != null) map.remove(field);
        } else {
            this.fieldCoders.computeIfAbsent(clazz, c -> new ConcurrentHashMap<>()).put(field, coder);
        }
    }

    public final <E> SimpledCoder<R, W, E> findFieldCoder(final Type clazz, final String field) {
        if (field == null) return null;
        Map<String, SimpledCoder<R, W, ?>> map = this.fieldCoders.get(clazz);
        if (map == null) return parent == null ? null : parent.findFieldCoder(clazz, field);
        return (SimpledCoder) map.get(field);
    }

    public final <E> Decodeable<R, E> findDecoder(final Type type) {
        Decodeable<R, E> rs = (Decodeable<R, E>) decoders.get(type);
        if (rs != null) return rs;
        return this.parent == null ? null : this.parent.findDecoder(type);
    }

    public final <E> Encodeable<W, E> findEncoder(final Type type) {
        Encodeable<W, E> rs = (Encodeable<W, E>) encoders.get(type);
        if (rs != null) return rs;
        return this.parent == null ? null : this.parent.findEncoder(type);
    }

    public final <E> Decodeable<R, E> loadDecoder(final Type type) {
        Decodeable<R, E> decoder = findDecoder(type);
        if (decoder != null) return decoder;
        if (type instanceof GenericArrayType) return createArrayDecoder(type);
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
            if (cz == null) throw new ConvertException("not support the type (" + type + ")");
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
            if (cz == null) throw new ConvertException("not support the type (" + type + ")");
        } else if (type instanceof Class) {
            clazz = (Class) type;
        } else {
            throw new ConvertException("not support the type (" + type + ")");
        }
        //此处不能再findDecoder，否则type与class不一致, 如: RetResult 和 RetResult<Integer>
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
                        if (!Modifier.isStatic(method.getModifiers())) continue;
                        Class[] paramTypes = method.getParameterTypes();
                        if (paramTypes.length != 1 && paramTypes.length != 2) continue;
                        if (paramTypes[0] != ConvertFactory.class && paramTypes[0] != this.getClass()) continue;
                        if (paramTypes.length == 2 && paramTypes[1] != Class.class && paramTypes[1] != Type.class) continue;
                        if (!Decodeable.class.isAssignableFrom(method.getReturnType())) continue;
                        if (Modifier.isPrivate(method.getModifiers()) && subclazz != clazz) continue; //声明private的只能被自身类使用
                        try {
                            method.setAccessible(true);
                            simpleCoder = (Decodeable) (paramTypes.length == 2 ? (paramTypes[1] == Type.class ? method.invoke(null, this, type) : method.invoke(null, this, clazz)) : method.invoke(null, this));
                            RedkaleClassLoader.putReflectionDeclaredMethods(subclazz.getName());
                            RedkaleClassLoader.putReflectionMethod(subclazz.getName(), method);
                            break;
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    if (simpleCoder != null) break;
                }
            }
            if (simpleCoder == null) {
                if (type instanceof Class) {
                    Class<?> typeclz = (Class) type;
                    ConvertImpl ci = typeclz.getAnnotation(ConvertImpl.class);
                    if (ci != null && ci.types().length > 0) {
                        for (Class sub : ci.types()) {
                            if (!typeclz.isAssignableFrom(sub)) {
                                throw new ConvertException("@" + ConvertImpl.class.getSimpleName() + ".types(" + sub + ") must be " + typeclz + "'s subclass");
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
        if (decoder == null) throw new ConvertException("not support the type (" + type + ")");
        register(type, decoder);
        if (od != null) od.init(this);
        return decoder;
    }

    public final <E> Encodeable<W, E> loadEncoder(final Type type) {
        Encodeable<W, E> encoder = findEncoder(type);
        if (encoder != null) return encoder;
        if (type instanceof GenericArrayType) return createArrayEncoder(type);
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
            if (!(t instanceof Class)) t = Object.class;
            clazz = (Class) t;
        } else if (type instanceof Class) {
            clazz = (Class) type;
        } else {
            throw new ConvertException("not support the type (" + type + ")");
        }
        //此处不能再findEncoder，否则type与class不一致, 如: RetResult 和 RetResult<Integer> 
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
        } else if (Optional.class == clazz) {
            encoder = new OptionalCoder(this, type);
        } else if (clazz == Object.class) {
            return (Encodeable<W, E>) this.anyEncoder;
        } else if (!clazz.getName().startsWith("java.") || java.net.HttpCookie.class == clazz
            || java.util.Map.Entry.class == clazz || java.util.AbstractMap.SimpleEntry.class == clazz) {
            Encodeable simpleCoder = null;
            if (!skipCustomMethod) {
                for (Class subclazz : getSuperClasses(clazz)) {
                    for (final Method method : subclazz.getDeclaredMethods()) {
                        if (!Modifier.isStatic(method.getModifiers())) continue;
                        Class[] paramTypes = method.getParameterTypes();
                        if (paramTypes.length != 1 && paramTypes.length != 2) continue;
                        if (paramTypes[0] != ConvertFactory.class && paramTypes[0] != this.getClass()) continue;
                        if (paramTypes.length == 2 && paramTypes[1] != Class.class && paramTypes[1] != Type.class) continue;
                        if (!Encodeable.class.isAssignableFrom(method.getReturnType())) continue;
                        if (Modifier.isPrivate(method.getModifiers()) && subclazz != clazz) continue; //声明private的只能被自身类使用
                        try {
                            method.setAccessible(true);
                            simpleCoder = (Encodeable) (paramTypes.length == 2 ? (paramTypes[1] == Type.class ? method.invoke(null, this, type) : method.invoke(null, this, clazz)) : method.invoke(null, this));
                            RedkaleClassLoader.putReflectionDeclaredMethods(subclazz.getName());
                            RedkaleClassLoader.putReflectionMethod(subclazz.getName(), method);
                            break;
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    if (simpleCoder != null) break;
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
        if (encoder == null) throw new ConvertException("not support the type (" + type + ")");
        register(type, encoder);
        if (oe != null) oe.init(this);
        return encoder;

    }

    private Set<Class> getSuperClasses(final Class clazz) {
        Set<Class> set = new LinkedHashSet<>();
        set.add(clazz);
        Class recursClass = clazz;
        while ((recursClass = recursClass.getSuperclass()) != null) {
            if (recursClass == Object.class) break;
            set.addAll(getSuperClasses(recursClass));
        }
        for (Class sub : clazz.getInterfaces()) {
            set.addAll(getSuperClasses(sub));
        }
        return set;
    }

}
