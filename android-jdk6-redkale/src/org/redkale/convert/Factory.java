/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;
import java.lang.reflect.*;
import java.math.BigInteger;
import java.net.*;
import java.nio.channels.*;
import static org.redkale.convert.ext.InetAddressSimpledCoder.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import org.redkale.convert.ext.*;
import org.redkale.util.*;
import org.redkale.util.Creator.Creators;

/**
 *
 * @see http://www.redkale.org
 * @author zhangjx
 * @param <R>
 * @param <W>
 */
@SuppressWarnings("unchecked")
public abstract class Factory<R extends Reader, W extends Writer> {

    private final Factory parent;

    protected Convert<R, W> convert;

    protected boolean tiny;

    private final Encodeable<W, ?> anyEncoder = new AnyEncoder(this);

    //-----------------------------------------------------------------------------------
    private final ConcurrentHashMap<Class, Creator> creators = new ConcurrentHashMap();

    private final Map<String, Class> entitys = new ConcurrentHashMap();

    private final ConcurrentHashMap<Type, Decodeable<R, ?>> decoders = new ConcurrentHashMap();

    private final ConcurrentHashMap<Type, Encodeable<W, ?>> encoders = new ConcurrentHashMap();

    private final HashMap<AccessibleObject, ConvertColumnEntry> columnEntrys = new HashMap();

    private final Set<Class> skipIgnores = new HashSet();

    private boolean skipAllIgnore = false;

    protected Factory(Factory<R, W> parent, boolean tiny) {
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
            this.register(CharSequence.class, CharSequenceSimpledCoder.instance);
            this.register(java.util.Date.class, DateSimpledCoder.instance);
            this.register(BigInteger.class, BigIntegerSimpledCoder.instance);
            this.register(InetAddress.class, InetAddressSimpledCoder.instance);
            this.register(DLong.class, DLongSimpledCoder.instance);
            this.register(Class.class, TypeSimpledCoder.instance);
            this.register(InetSocketAddress.class, InetSocketAddressSimpledCoder.instance);
            this.register(Pattern.class, PatternSimpledCoder.instance);
            this.register(CompletionHandler.class, CompletionHandlerSimpledCoder.instance);
            //---------------------------------------------------------
            this.register(boolean[].class, BoolArraySimpledCoder.instance);
            this.register(byte[].class, ByteArraySimpledCoder.instance);
            this.register(short[].class, ShortArraySimpledCoder.instance);
            this.register(char[].class, CharArraySimpledCoder.instance);
            this.register(int[].class, IntArraySimpledCoder.instance);
            this.register(long[].class, LongArraySimpledCoder.instance);
            this.register(float[].class, FloatArraySimpledCoder.instance);
            this.register(double[].class, DoubleArraySimpledCoder.instance);
            this.register(String[].class, StringArraySimpledCoder.instance);
            //---------------------------------------------------------
        }
    }

    public Factory parent() {
        return this.parent;
    }

    public abstract ConvertType getConvertType();

    public abstract boolean isReversible();

    public abstract Factory createChild();

    public abstract Factory createChild(boolean tiny);

    public Convert getConvert() {
        return convert;
    }

    public void setTiny(boolean tiny) {
        this.tiny = tiny;
    }

    public ConvertColumnEntry findRef(AccessibleObject field) {
        if (field == null) return null;
        ConvertColumnEntry en = this.columnEntrys.get(field);
        if (en != null) return en;
        final ConvertType ct = this.getConvertType();
        final ConvertColumns ccs = field.getAnnotation(ConvertColumns.class);
        final ConvertColumn cc = field.getAnnotation(ConvertColumn.class);
        if (ccs == null && cc == null) return null;
        final ConvertColumn[] cca = (ccs == null) ? new ConvertColumn[]{cc} : ccs.value();
        for (ConvertColumn ref : cca) {
            if (ref.type().contains(ct)) {
                ConvertColumnEntry entry = new ConvertColumnEntry(ref);
                if (skipAllIgnore) {
                    entry.setIgnore(false);
                    return entry;
                }
                if (skipIgnores.isEmpty()) return entry;
                if (skipIgnores.contains(((Member) field).getDeclaringClass())) entry.setIgnore(false);
                return entry;
            }
        }
        return null;
    }

    final String getEntity(Class clazz) {
        ConvertEntity ce = (ConvertEntity) clazz.getAnnotation(ConvertEntity.class);
        if (ce != null && findEntity(ce.value()) == null) entitys.put(ce.value(), clazz);
        return ce == null ? clazz.getName() : ce.value();
    }

    private Class findEntity(String name) {
        Class clazz = entitys.get(name);
        return parent == null ? clazz : parent.findEntity(name);
    }

    final Class getEntity(String name) {
        Class clazz = findEntity(name);
        try {
            return clazz == null ? Class.forName(name) : clazz;
        } catch (Exception ex) {
            throw new ConvertException("convert entity is " + name, ex);
        }
    }

    /**
     * 使所有类的所有被声明为ConvertColumn.ignore = true 的字段或方法变为ConvertColumn.ignore = false
     * <p>
     * @param skipIgnore
     */
    public final void registerSkipAllIgnore(final boolean skipIgnore) {
        this.skipAllIgnore = skipIgnore;
    }

    /**
     * 使该类所有被声明为ConvertColumn.ignore = true 的字段或方法变为ConvertColumn.ignore = false
     * <p>
     * @param type
     */
    public final void registerSkipIgnore(final Class type) {
        skipIgnores.add(type);
    }

    public final void register(final Class type, boolean ignore, String... columns) {
        for (String column : columns) {
            register(type, column, new ConvertColumnEntry(column, ignore));
        }
    }

    public final boolean register(final Class type, String column, ConvertColumnEntry entry) {
        if (type == null || column == null || entry == null) return false;
        try {
            final Field field = type.getDeclaredField(column);
            String get = "get";
            if (field.getType() == boolean.class || field.getType() == Boolean.class) get = "is";
            char[] cols = column.toCharArray();
            cols[0] = Character.toUpperCase(cols[0]);
            String col2 = new String(cols);
            try {
                register(type.getMethod(get + col2), entry);
            } catch (Exception ex) {
            }
            try {
                register(type.getMethod("set" + col2, field.getType()), entry);
            } catch (Exception ex) {
            }
            return register(field, entry);
        } catch (Exception e) {
            return false;
        }
    }

    public final <E> boolean register(final AccessibleObject field, final ConvertColumnEntry entry) {
        if (field == null || entry == null) return false;
        this.columnEntrys.put(field, entry);
        return true;
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
            result = Creators.create(type);
            creators.put(type, result);
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

    public final <E> void register(final Type clazz, final Decodeable<R, E> decoder) {
        decoders.put(clazz, decoder);
    }

    public final <E> void register(final Type clazz, final Encodeable<W, E> printer) {
        encoders.put(clazz, printer);
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
        if (type instanceof GenericArrayType) return new ArrayDecoder(this, type);
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
        decoder = findDecoder(clazz);
        if (decoder != null) return decoder;
        return createDecoder(type, clazz);
    }

    public final <E> Decodeable<R, E> createDecoder(final Type type) {
        Class clazz;
        if (type instanceof ParameterizedType) {
            final ParameterizedType pts = (ParameterizedType) type;
            clazz = (Class) (pts).getRawType();
        } else if (type instanceof Class) {
            clazz = (Class) type;
        } else {
            throw new ConvertException("not support the type (" + type + ")");
        }
        return createDecoder(type, clazz);
    }

    private <E> Decodeable<R, E> createDecoder(final Type type, final Class clazz) {
        Decodeable<R, E> decoder = null;
        ObjectDecoder od = null;
        if (clazz.isEnum()) {
            decoder = new EnumSimpledCoder(clazz);
        } else if (clazz.isArray()) {
            decoder = new ArrayDecoder(this, type);
        } else if (Collection.class.isAssignableFrom(clazz)) {
            decoder = new CollectionDecoder(this, type);
        } else if (Map.class.isAssignableFrom(clazz)) {
            decoder = new MapDecoder(this, type);
        } else if (clazz == Object.class) {
            od = new ObjectDecoder(type);
            decoder = od;
        } else if (!clazz.getName().startsWith("java.")) {
            SimpledCoder simpleCoder = null;
            for (final Method method : clazz.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers())) continue;
                if (method.getParameterTypes().length != 0) continue;
                if (method.getReturnType() != SimpledCoder.class) continue;
                try {
                    method.setAccessible(true);
                    simpleCoder = (SimpledCoder) method.invoke(null);
                    break;
                } catch (Exception e) {
                }
            }
            if (simpleCoder == null) {
                od = new ObjectDecoder(type);
                decoder = od;
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
        if (type instanceof GenericArrayType) return new ArrayEncoder(this, type);
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
        encoder = findEncoder(clazz);
        if (encoder != null) return encoder;
        return createEncoder(type, clazz);
    }

    public final <E> Encodeable<W, E> createEncoder(final Type type) {
        Class clazz;
        if (type instanceof ParameterizedType) {
            final ParameterizedType pts = (ParameterizedType) type;
            clazz = (Class) (pts).getRawType();
        } else if (type instanceof Class) {
            clazz = (Class) type;
        } else {
            throw new ConvertException("not support the type (" + type + ")");
        }
        return createEncoder(type, clazz);
    }

    private <E> Encodeable<W, E> createEncoder(final Type type, final Class clazz) {
        Encodeable<W, E> encoder = null;
        ObjectEncoder oe = null;
        if (clazz.isEnum()) {
            encoder = new EnumSimpledCoder(clazz);
        } else if (clazz.isArray()) {
            encoder = new ArrayEncoder(this, type);
        } else if (Collection.class.isAssignableFrom(clazz)) {
            encoder = new CollectionEncoder(this, type);
        } else if (Map.class.isAssignableFrom(clazz)) {
            encoder = new MapEncoder(this, type);
        } else if (clazz == Object.class) {
            return (Encodeable<W, E>) this.anyEncoder;
        } else if (!clazz.getName().startsWith("java.")) {
            SimpledCoder simpleCoder = null;
            for (final Method method : clazz.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers())) continue;
                if (method.getParameterTypes().length != 0) continue;
                if (method.getReturnType() != SimpledCoder.class) continue;
                try {
                    method.setAccessible(true);
                    simpleCoder = (SimpledCoder) method.invoke(null);
                    break;
                } catch (Exception e) {
                }
            }
            if (simpleCoder == null) {
                oe = new ObjectEncoder(type);
                encoder = oe;
            } else {
                encoder = simpleCoder;
            }
        }
        if (encoder == null) throw new ConvertException("not support the type (" + type + ")");
        register(type, encoder);
        if (oe != null) oe.init(this);
        return encoder;

    }

}
