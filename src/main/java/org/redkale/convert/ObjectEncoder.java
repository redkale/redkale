/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import java.lang.reflect.*;
import java.util.*;
import org.redkale.convert.ext.StringSimpledCoder;
import org.redkale.util.*;

/**
 * 自定义对象的序列化操作类
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <W> Writer输出的子类
 * @param <T> 序列化的数据类型
 */
@SuppressWarnings("unchecked")
public class ObjectEncoder<W extends Writer, T> implements Encodeable<W, T> {

    static final Type[] TYPEZERO = new Type[0];

    protected final Type type;

    protected final Class typeClass;

    protected EnMember[] members;

    protected ConvertFactory factory;

    protected volatile boolean inited = false;

    protected final Object lock = new Object();

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
            if (type == Object.class) return;
            //if (!(type instanceof Class)) throw new ConvertException("[" + type + "] is no a class");
            final Class clazz = this.typeClass;
            final Set<EnMember> list = new LinkedHashSet();
            final boolean reversible = factory.isReversible();
            Creator creator = null;
            try {
                creator = factory.loadCreator(this.typeClass);
            } catch (RuntimeException e) {
                if (reversible && !Modifier.isAbstract(this.typeClass.getModifiers())) throw e;
            }
            final String[] cps = creator == null ? null : ObjectEncoder.findConstructorProperties(creator);
            try {
                ConvertColumnEntry ref;

                RedkaleClassLoader.putReflectionPublicFields(clazz.getName());
                for (final Field field : clazz.getFields()) {
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    if (factory.isConvertDisabled(field)) continue;
                    ref = factory.findRef(clazz, field);
                    if (ref != null && ref.ignore()) continue;
                    ConvertSmallString small = field.getAnnotation(ConvertSmallString.class);
                    Encodeable<W, ?> fieldCoder;
                    if (small != null && field.getType() == String.class) {
                        fieldCoder = StringSimpledCoder.SmallStringSimpledCoder.instance;
                    } else {
                        fieldCoder = factory.findFieldCoder(clazz, field.getName());
                    }
                    if (fieldCoder == null) {
                        Type t = TypeToken.createClassType(TypeToken.getGenericType(field.getGenericType(), this.type), this.type);
                        fieldCoder = factory.loadEncoder(t);
                    }
                    EnMember member = new EnMember(createAttribute(factory, type, clazz, field, null, null), fieldCoder, field, null);
                    if (ref != null) member.index = ref.getIndex();
                    list.add(member);
                }

                RedkaleClassLoader.putReflectionPublicMethods(clazz.getName());
                for (final Method method : clazz.getMethods()) {
                    if (Modifier.isStatic(method.getModifiers())) continue;
                    if (Modifier.isAbstract(method.getModifiers())) continue;
                    if (method.isSynthetic()) continue;
                    if (method.getName().equals("getClass")) continue;
                    if (method.getReturnType() == void.class) continue;
                    if (method.getParameterCount() != 0) continue;
                    if (!method.getName().startsWith("is") && !method.getName().startsWith("get") && !Utility.isRecordGetter(clazz, method)) continue;
                    if (factory.isConvertDisabled(method)) continue;
                    String convertname = ConvertFactory.readGetSetFieldName(method);
                    if (reversible && (cps == null || !contains(cps, convertname))) {
                        boolean is = method.getName().startsWith("is");
                        try {
                            clazz.getMethod(method.getName().replaceFirst(is ? "is" : "get", "set"), method.getReturnType());
                        } catch (Exception e) {
                            continue;
                        }
                    }
                    ref = factory.findRef(clazz, method);
                    if (ref != null && ref.ignore()) continue;
                    ConvertSmallString small = method.getAnnotation(ConvertSmallString.class);
                    if (small == null) {
                        try {
                            Field f = clazz.getDeclaredField(convertname);
                            if (f != null) small = f.getAnnotation(ConvertSmallString.class);
                        } catch (Exception e) {
                        }
                    }
                    Encodeable<W, ?> fieldCoder;
                    if (small != null && method.getReturnType() == String.class) {
                        fieldCoder = StringSimpledCoder.SmallStringSimpledCoder.instance;
                    } else {
                        fieldCoder = factory.findFieldCoder(clazz, ConvertFactory.readGetSetFieldName(method));
                    }
                    if (fieldCoder == null) {
                        Type t = TypeToken.createClassType(TypeToken.getGenericType(method.getGenericReturnType(), this.type), this.type);
                        fieldCoder = factory.loadEncoder(t);
                    }
                    EnMember member = new EnMember(createAttribute(factory, type, clazz, null, method, null), fieldCoder, ConvertFactory.readGetSetField(method), method);
                    if (ref != null) member.index = ref.getIndex();
                    list.add(member);
                }
                List<EnMember> sorts = new ArrayList<>(list);
                if (cps != null) {
                    Set<EnMember> dissorts = new LinkedHashSet<>(list);
                    for (final String constructorField : cps) {  //reversible模式下需要确保DeMember与EnMember的个数和顺序保持一致，不然postition会不一致导致反序列化对应的字段顺序不同
                        boolean flag = false;
                        for (EnMember m : dissorts) {
                            if (m.attribute.field().equals(constructorField)) {
                                flag = true;
                                break;
                            }
                        }
                        if (flag) continue;
                        //不存在setter方法
                        try {
                            Field f = clazz.getDeclaredField(constructorField);
                            Type t = TypeToken.createClassType(f.getGenericType(), this.type);
                            try {
                                dissorts.add(new EnMember(createAttribute(factory, type, clazz, f, null, null), null, f, null)); //虚构
                            } catch (RuntimeException e) {
                            }
                        } catch (NoSuchFieldException nsfe) { //不存在field， 可能存在getter方法
                            char[] fs = constructorField.toCharArray();
                            fs[0] = Character.toUpperCase(fs[0]);
                            String mn = new String(fs);
                            Method getter;
                            try {
                                getter = clazz.getMethod("get" + mn);
                            } catch (NoSuchMethodException ex) {
                                getter = clazz.getMethod("is" + mn);
                            }
                            Type t = TypeToken.createClassType(TypeToken.getGenericType(getter.getGenericParameterTypes()[0], this.type), this.type);
                            try {
                                dissorts.add(new EnMember(createAttribute(factory, type, clazz, null, getter, null), null, null, null)); //虚构
                            } catch (RuntimeException e) {
                            }
                        }
                    }
                    if (dissorts.size() != list.size()) sorts = new ArrayList<>(dissorts);
                }
                Collections.sort(sorts, (a, b) -> a.compareTo(factory.isFieldSort(), b));
                Set<Integer> pos = new HashSet<>();
                for (EnMember member : sorts) {
                    if (member.index > 0) pos.add(member.index);
                }
                int pidx = 0;
                for (EnMember member : sorts) {
                    if (member.index > 0) {
                        member.position = member.index;
                    } else {
                        while (pos.contains(++pidx));
                        member.position = pidx;
                    }
                    initForEachEnMember(factory, member);
                }

                this.members = list.toArray(new EnMember[list.size()]);
                Arrays.sort(this.members, (a, b) -> a.compareTo(factory.isFieldSort(), b));

            } catch (Exception ex) {
                throw new ConvertException("ObjectEncoder init type=" + this.type + " error", ex);
            }
        } finally {
            inited = true;
            synchronized (lock) {
                lock.notifyAll();
            }
        }
    }

    protected void initForEachEnMember(ConvertFactory factory, EnMember member) {
    }

    protected void setTag(EnMember member, int tag) {
        member.tag = tag;
    }

    @Override
    public void convertTo(W out, T value) {
        if (value == null) {
            out.writeObjectNull(null);
            return;
        }
        if (!this.inited) {
            synchronized (lock) {
                try {
                    lock.wait();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        if (value.getClass() != this.typeClass && !this.type.equals(out.specify())) {
            final Class clz = value.getClass();
            if (out.needWriteClassName()) out.writeClassName(factory.getEntityAlias(clz));
            factory.loadEncoder(clz).convertTo(out, value);
            return;
        }
        W objout = objectWriter(out, value);
        if (objout.writeObjectB(value) < 0) {
            int maxPosition = 0;
            for (EnMember member : members) {
                maxPosition = member.getPosition();
                objout.writeObjectField(member, value);
            }
            if (objout.objExtFunc != null) {
                ConvertField[] extFields = objout.objExtFunc.apply(value);
                if (extFields != null) {
                    Encodeable<W, ?> anyEncoder = factory.getAnyEncoder();
                    for (ConvertField en : extFields) {
                        if (en == null) continue;
                        maxPosition++;
                        objout.writeObjectField(en.getName(), en.getType(), Math.max(en.getPosition(), maxPosition), anyEncoder, en.getValue());
                    }
                }
            }
        }
        objout.writeObjectE(value);
    }

    protected W objectWriter(W out, T value) {
        return out;
    }

    @Override
    public Type getType() {
        return this.type;
    }

    public Class getTypeClass() {
        return this.typeClass;
    }

    public EnMember[] getMembers() {
        return Arrays.copyOf(members, members.length);
    }

    @Override
    public String toString() {
        return "ObjectEncoder{" + "type=" + type + ", members=" + Arrays.toString(members) + '}';
    }

//
//    static Type makeGenericType(final Type type, final Type[] virGenericTypes, final Type[] realGenericTypes) {
//        if (type instanceof Class) {  //e.g. String
//            return type;
//        } else if (type instanceof ParameterizedType) {  //e.g. Map<String, String>
//            final ParameterizedType pt = (ParameterizedType) type;
//            Type[] paramTypes = pt.getActualTypeArguments();
//            final Type[] newTypes = new Type[paramTypes.length];
//            int count = 0;
//            for (int i = 0; i < newTypes.length; i++) {
//                newTypes[i] = makeGenericType(paramTypes[i], virGenericTypes, realGenericTypes);
//                if (paramTypes[i] == newTypes[i]) count++;
//            }
//            if (count == paramTypes.length) return pt;
//            return new ParameterizedType() {
//
//                @Override
//                public Type[] getActualTypeArguments() {
//                    return newTypes;
//                }
//
//                @Override
//                public Type getRawType() {
//                    return pt.getRawType();
//                }
//
//                @Override
//                public Type getOwnerType() {
//                    return pt.getOwnerType();
//                }
//
//            };
//        }
//        if (realGenericTypes == null) return type;
//        if (type instanceof WildcardType) {   // e.g. <? extends Serializable>
//            final WildcardType wt = (WildcardType) type;
//            for (Type f : wt.getUpperBounds()) {
//                for (int i = 0; i < virGenericTypes.length; i++) {
//                    if (virGenericTypes[i] == f) return realGenericTypes.length == 0 ? Object.class : realGenericTypes[i];
//                }
//            }
//        } else if (type instanceof TypeVariable) { // e.g.  <? extends E>
//            for (int i = 0; i < virGenericTypes.length; i++) {
//                if (virGenericTypes[i] == type) return i >= realGenericTypes.length ? Object.class : realGenericTypes[i];
//            }
//        }
//        return type;
//    }
    static boolean contains(String[] values, String value) {
        for (String str : values) {
            if (str.equals(value)) return true;
        }
        return false;
    }

    static String[] findConstructorProperties(Creator creator) {
        if (creator == null) return null;
        try {
            ConstructorParameters cps = creator.getClass().getMethod("create", Object[].class).getAnnotation(ConstructorParameters.class);
            return cps == null ? null : cps.value();
        } catch (Exception e) {
            return null;
        }
    }

    static Attribute createAttribute(final ConvertFactory factory, final Type realType, Class clazz, final Field field, final Method getter, final Method setter) {
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
                }
            }
            fieldalias = ref == null || ref.name().isEmpty() ? mfieldname : ref.name();
        }

        return Attribute.create(realType, clazz, fieldalias, null, field, getter, setter, null);
    }

}
