/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.locks.*;
import org.redkale.annotation.*;
import org.redkale.annotation.ConstructorParameters;
import org.redkale.convert.ext.StringSimpledCoder;
import org.redkale.util.*;

/**
 * 自定义对象的序列化操作类
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <W> Writer输出的子类
 * @param <T> 序列化的数据类型
 */
@SuppressWarnings("unchecked")
public class ObjectEncoder<W extends Writer, T> implements Encodeable<W, T> {

    protected final Type type;

    protected final Class typeClass;

    protected EnMember[] members;

    @Nullable
    protected ConvertFactory factory;

    protected volatile boolean inited = false;

    private final ReentrantLock lock = new ReentrantLock();

    private final Condition condition = lock.newCondition();

    protected ObjectEncoder(Type type) {
        this.type = type;
        if (type instanceof ParameterizedType) {
            final ParameterizedType pt = (ParameterizedType) type;
            this.typeClass = (Class) pt.getRawType();
        } else if (type instanceof TypeVariable) {
            TypeVariable tv = (TypeVariable) type;
            Type[] ts = tv.getBounds();
            if (ts.length == 1 && ts[0] instanceof Class) {
                this.typeClass = (Class) ts[0];
            } else {
                throw new ConvertException("[" + type + "] is no a class or ParameterizedType");
            }
        } else {
            this.typeClass = (Class) type;
        }
        this.members = new EnMember[0];
    }

    public void init(final ConvertFactory factory) {
        this.factory = factory;
        try {
            if (type == Object.class) {
                return;
            }
            // if (!(type instanceof Class)) throw new ConvertException("[" + type + "] is no a class");
            final Class clazz = this.typeClass;
            final Set<EnMember> list = new LinkedHashSet();
            final boolean reversible = factory.isReversible();
            Creator creator = null;
            try {
                creator = factory.loadCreator(this.typeClass);
            } catch (RuntimeException e) {
                if (reversible && !Modifier.isAbstract(this.typeClass.getModifiers())) {
                    throw e;
                }
            }
            final String[] cps = creator == null ? null : ObjectEncoder.findConstructorProperties(creator);
            try {
                ConvertColumnEntry ref;
                ConvertFactory colFactory;
                RedkaleClassLoader.putReflectionPublicFields(clazz.getName());
                for (final Field field : clazz.getFields()) {
                    if (Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    if (factory.isConvertDisabled(field)) {
                        continue;
                    }
                    ref = factory.findRef(clazz, field);
                    if (ref != null && ref.ignore()) {
                        continue;
                    }
                    ConvertSmallString small = field.getAnnotation(ConvertSmallString.class);
                    colFactory = factory.columnFactory(
                            field.getGenericType(), field.getAnnotationsByType(ConvertCoder.class), true);
                    Encodeable<W, ?> fieldCoder;
                    if (small != null && field.getType() == String.class) {
                        fieldCoder = StringSimpledCoder.StandardStringSimpledCoder.instance;
                    } else {
                        fieldCoder = colFactory.findFieldCoder(clazz, field.getName());
                    }
                    if (fieldCoder == null) {
                        Type t = TypeToken.createClassType(
                                TypeToken.getGenericType(field.getGenericType(), this.type), this.type);
                        fieldCoder = colFactory.loadEncoder(t);
                    }
                    EnMember member = new EnMember(
                            createAttribute(colFactory, type, clazz, field, null, null),
                            fieldCoder,
                            field,
                            null,
                            ref == null ? null : ref.fieldFunc());
                    if (ref != null) {
                        member.index = ref.getIndex();
                    }
                    list.add(member);
                }

                RedkaleClassLoader.putReflectionPublicMethods(clazz.getName());
                for (final Method method : clazz.getMethods()) {
                    if (Modifier.isStatic(method.getModifiers())) {
                        continue;
                    }
                    if (Modifier.isAbstract(method.getModifiers())) {
                        continue;
                    }
                    if (method.isSynthetic()) {
                        continue;
                    }
                    if (method.getName().equals("getClass")) {
                        continue;
                    }
                    if (method.getReturnType() == void.class) {
                        continue;
                    }
                    if (method.getParameterCount() != 0) {
                        continue;
                    }
                    if (!(method.getName().startsWith("is") && method.getName().length() > 2)
                            && !(method.getName().startsWith("get")
                                    && method.getName().length() > 3)
                            && !Utility.isRecordGetter(clazz, method)) {
                        continue;
                    }
                    if (factory.isConvertDisabled(method)) {
                        continue;
                    }
                    String convertName = ConvertFactory.readGetSetFieldName(method);
                    if (reversible && (cps == null || !contains(cps, convertName))) {
                        boolean is = method.getName().startsWith("is");
                        try {
                            clazz.getMethod(
                                    method.getName().replaceFirst(is ? "is" : "get", "set"), method.getReturnType());
                        } catch (Exception e) {
                            continue;
                        }
                    }
                    ref = factory.findRef(clazz, method);
                    if (ref != null && ref.ignore()) {
                        continue;
                    }
                    ConvertSmallString small = method.getAnnotation(ConvertSmallString.class);
                    if (small == null) {
                        try {
                            Field f = clazz.getDeclaredField(convertName);
                            if (f != null) {
                                small = f.getAnnotation(ConvertSmallString.class);
                            }
                        } catch (Exception e) {
                            // do nothing
                        }
                    }
                    Field maybeField = ConvertFactory.readGetSetField(method);
                    colFactory = factory.columnFactory(
                            method.getGenericReturnType(), method.getAnnotationsByType(ConvertCoder.class), true);
                    if (maybeField != null && colFactory == factory) {
                        colFactory = factory.columnFactory(
                                maybeField.getGenericType(), maybeField.getAnnotationsByType(ConvertCoder.class), true);
                    }
                    Encodeable<W, ?> fieldCoder;
                    if (small != null && method.getReturnType() == String.class) {
                        fieldCoder = StringSimpledCoder.StandardStringSimpledCoder.instance;
                    } else {
                        String fieldName = ConvertFactory.readGetSetFieldName(method);
                        fieldCoder = colFactory.findFieldCoder(clazz, fieldName);
                    }
                    if (fieldCoder == null) {
                        Type t = TypeToken.createClassType(
                                TypeToken.getGenericType(method.getGenericReturnType(), this.type), this.type);
                        fieldCoder = colFactory.loadEncoder(t);
                    }
                    EnMember member = new EnMember(
                            createAttribute(colFactory, type, clazz, null, method, null),
                            fieldCoder,
                            maybeField,
                            method,
                            ref == null ? null : ref.fieldFunc());
                    if (Utility.contains(list, m -> m.attribute.field().equals(member.attribute.field()))) {
                        continue;
                    }
                    if (ref != null) {
                        member.index = ref.getIndex();
                    }
                    list.add(member);
                }

                List<EnMember> sorts = new ArrayList<>(list);
                if (cps != null) {
                    Set<EnMember> dissorts = new LinkedHashSet<>(list);
                    for (final String constructorField :
                            cps) { // reversible模式下需要确保DeMember与EnMember的个数和顺序保持一致，不然postition会不一致导致反序列化对应的字段顺序不同
                        if (Utility.contains(dissorts, m -> m.attribute.field().equals(constructorField))) {
                            continue;
                        }
                        // 不存在setter方法
                        try {
                            Field f = clazz.getDeclaredField(constructorField);
                            ConvertColumnEntry ref2 = factory.findRef(clazz, f);
                            // Type t = TypeToken.createClassType(f.getGenericType(), this.type);
                            try {
                                EnMember member = new EnMember(
                                        createAttribute(factory, type, clazz, f, null, null),
                                        null,
                                        f,
                                        null,
                                        ref2 == null ? null : ref2.fieldFunc());
                                if (ref2 != null) {
                                    member.index = ref2.getIndex();
                                }
                                dissorts.add(member); // 虚构
                            } catch (RuntimeException e) {
                                // do nothing
                            }
                        } catch (NoSuchFieldException nsfe) { // 不存在field， 可能存在getter方法
                            char[] fs = constructorField.toCharArray();
                            fs[0] = Character.toUpperCase(fs[0]);
                            String mn = new String(fs);
                            Method getter;
                            try {
                                getter = clazz.getMethod("get" + mn);
                            } catch (NoSuchMethodException ex) {
                                getter = clazz.getMethod("is" + mn);
                            }
                            ConvertColumnEntry ref2 = factory.findRef(clazz, getter);
                            // Type t =
                            // TypeToken.createClassType(TypeToken.getGenericType(getter.getGenericParameterTypes()[0],
                            // this.type), this.type);
                            try {
                                EnMember member = new EnMember(
                                        createAttribute(factory, type, clazz, null, getter, null),
                                        null,
                                        null,
                                        null,
                                        ref2 == null ? null : ref2.fieldFunc());
                                if (ref2 != null) {
                                    member.index = ref2.getIndex();
                                }
                                dissorts.add(member); // 虚构
                            } catch (RuntimeException e) {
                                // do nothing
                            }
                        }
                    }
                    if (dissorts.size() != list.size()) {
                        sorts = new ArrayList<>(dissorts);
                    }
                }
                Collections.sort(sorts, (a, b) -> a.compareTo(factory.isFieldSort(), b));
                Set<Integer> pos = new HashSet<>();
                for (EnMember member : sorts) {
                    if (member.index > 0) {
                        pos.add(member.index);
                    }
                }
                int pidx = 0;
                for (EnMember member : sorts) {
                    if (member.index > 0) {
                        member.position = member.index;
                    } else {
                        while (pos.contains(++pidx)) {
                            // do nothing
                        }
                        member.position = pidx;
                    }
                    initForEachEnMember(factory, member);
                }
                EnMember[] enMembers = list.toArray(new EnMember[list.size()]);
                Arrays.sort(enMembers, (a, b) -> a.compareTo(factory.isFieldSort(), b));
                this.initFieldMember(enMembers);
                afterInitEnMember(factory);
            } catch (Exception ex) {
                throw new ConvertException("ObjectEncoder init type=" + this.type + " error", ex);
            }
        } finally {
            inited = true;
            lock.lock();
            try {
                condition.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

    protected void initFieldMember(EnMember[] enMembers) {
        this.members = enMembers;
    }

    protected void checkInited() {
        if (!this.inited) {
            lock.lock();
            try {
                condition.await();
            } catch (Exception e) {
                // do nothing
            } finally {
                lock.unlock();
            }
        }
    }

    @Override
    public void convertTo(W out, T value) {
        this.checkInited();
        if (value == null) {
            out.writeObjectNull(null);
            return;
        }
        if (value.getClass() != this.typeClass && !this.type.equals(out.specificObjectType())) {
            final Class clz = value.getClass();
            if (out.needWriteClassName()) {
                out.writeClassName(factory.getEntityAlias(clz));
            }
            factory.loadEncoder(clz).convertTo(out, value);
            return;
        }
        out.writeObjectB(value);
        int maxPosition = 0;
        for (EnMember member : members) {
            maxPosition = member.getPosition();
            out.writeObjectField(member, value);
        }
        if (out.objExtFunc != null) {
            ConvertField[] extFields = out.objExtFunc.apply(value);
            if (extFields != null) {
                Encodeable<W, ?> anyEncoder = factory.getAnyEncoder();
                for (ConvertField en : extFields) {
                    if (en != null) {
                        maxPosition++;
                        out.writeObjectField(
                                en.getName(),
                                en.getType(),
                                Math.max(en.getPosition(), maxPosition),
                                anyEncoder,
                                en.getValue());
                    }
                }
            }
        }
        out.writeObjectE(value);
    }

    // ---------------------------------- 可定制方法 ----------------------------------
    protected void initForEachEnMember(ConvertFactory factory, EnMember member) {
        // do nothing
    }

    protected void afterInitEnMember(ConvertFactory factory) {
        // do nothing
    }

    // ---------------------------------------------------------------------------------
    protected void setTag(EnMember member, int tag) {
        member.tag = tag;
    }

    protected void setTagSize(EnMember member, int tagSize) {
        member.tagSize = tagSize;
    }

    protected void setIndex(EnMember member, int index) {
        member.index = index;
    }

    protected void setPosition(EnMember member, int position) {
        member.position = position;
    }

    @Override
    public Type getType() {
        return this.type;
    }

    public Class getTypeClass() {
        return this.typeClass;
    }

    public EnMember[] getMembers() {
        return members;
    }

    @Override
    public String toString() {
        return "ObjectEncoder{" + "type=" + type + ", members=" + Arrays.toString(members) + '}';
    }

    static boolean contains(String[] values, String value) {
        for (String str : values) {
            if (str.equals(value)) {
                return true;
            }
        }
        return false;
    }

    static String[] findConstructorProperties(Creator creator) {
        if (creator == null) {
            return null;
        }
        try {
            Method method = creator.getClass().getMethod("create", Object[].class);
            ConstructorParameters cps = method.getAnnotation(ConstructorParameters.class);
            String[] vals = null;
            if (cps != null) {
                vals = cps.value();
            } else {
                org.redkale.util.ConstructorParameters cps2 =
                        method.getAnnotation(org.redkale.util.ConstructorParameters.class);
                if (cps2 != null) {
                    vals = cps2.value();
                }
            }
            return vals;
        } catch (Exception e) {
            return null;
        }
    }

    static Attribute createAttribute(
            final ConvertFactory factory,
            final Type realType,
            Class clazz,
            final Field field,
            final Method getter,
            final Method setter) {
        String fieldalias;
        if (field != null) { // public field
            ConvertColumnEntry ref = factory.findRef(clazz, field);
            fieldalias = ref == null || ref.name().isEmpty() ? field.getName() : ref.name();
        } else if (getter != null) {
            ConvertColumnEntry ref = factory.findRef(clazz, getter);
            String mfieldname = ConvertFactory.readGetSetFieldName(getter);
            if (ref == null) {
                try {
                    ref = factory.findRef(clazz, clazz.getDeclaredField(mfieldname));
                } catch (Exception e) {
                    // do nothing
                }
            }
            fieldalias = ref == null || ref.name().isEmpty() ? mfieldname : ref.name();
        } else { // setter != null
            ConvertColumnEntry ref = factory.findRef(clazz, setter);
            String mfieldname = ConvertFactory.readGetSetFieldName(setter);
            if (ref == null) {
                try {
                    ref = factory.findRef(clazz, clazz.getDeclaredField(mfieldname));
                } catch (Exception e) {
                    // do nothing
                }
            }
            fieldalias = ref == null || ref.name().isEmpty() ? mfieldname : ref.name();
        }
        Type subclass = realType;
        if ((realType instanceof Class) && field != null && getter == null && setter == null) {
            // 修复父类含public field，subclass不传父类会导致java.lang.NoSuchFieldError的bug
            subclass = field.getDeclaringClass();
        }
        return Attribute.create(subclass, clazz, fieldalias, null, field, getter, setter, null);
    }
}
