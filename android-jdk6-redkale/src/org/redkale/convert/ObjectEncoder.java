/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import org.redkale.util.Attribute;
import java.lang.reflect.*;
import java.util.*;
import org.redkale.util.*;
import org.redkale.util.Attribute.Attributes;

/**
 *
 * @see http://www.redkale.org
 * @author zhangjx
 * @param <W>
 * @param <T>
 */
@SuppressWarnings("unchecked")
public final class ObjectEncoder<W extends Writer, T> implements Encodeable<W, T> {

    static final Type[] TYPEZERO = new Type[0];

    protected final Type type;

    protected final Class typeClass;

    protected EnMember[] members;

    protected Factory factory;

    private boolean inited = false;

    private final Object lock = new Object();

    protected ObjectEncoder(Type type) {
        this.type = type;
        if (type instanceof ParameterizedType) {
            final ParameterizedType pt = (ParameterizedType) type;
            this.typeClass = (Class) pt.getRawType();
        } else {
            this.typeClass = (Class) type;
        }
        this.members = new EnMember[0];
    }

    public void init(final Factory factory) {
        this.factory = factory;
        try {
            if (type == Object.class) return;
            //if (!(type instanceof Class)) throw new ConvertException("[" + type + "] is no a class");
            final Class clazz = this.typeClass;
            final Set<EnMember> list = new HashSet();
            final String[] cps = ObjectEncoder.findConstructorProperties(factory.loadCreator(this.typeClass));
            try {
                ConvertColumnEntry ref;
                for (final Field field : clazz.getFields()) {
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    ref = factory.findRef(field);
                    if (ref != null && ref.ignore()) continue;
                    Type t = createClassType(field.getGenericType(), this.type);
                    list.add(new EnMember(createAttribute(factory, clazz, field, null, null), factory.loadEncoder(t)));
                }
                final boolean reversible = factory.isReversible();
                for (final Method method : clazz.getMethods()) {
                    if (Modifier.isStatic(method.getModifiers())) continue;
                    if (Modifier.isAbstract(method.getModifiers())) continue;
                    if (method.isSynthetic()) continue;
                    if (method.getName().length() < 3) continue;
                    if (method.getName().equals("getClass")) continue;
                    if (!method.getName().startsWith("is") && !method.getName().startsWith("get")) continue;
                    if (method.getParameterTypes().length != 0) continue;
                    if (method.getReturnType() == void.class) continue;
                    if (reversible && (cps == null || !contains(cps, readGetSetFieldName(method)))) {
                        boolean is = method.getName().startsWith("is");
                        try {
                            clazz.getMethod(method.getName().replaceFirst(is ? "is" : "get", "set"), method.getReturnType());
                        } catch (Exception e) {
                            continue;
                        }
                    }
                    ref = factory.findRef(method);
                    if (ref != null && ref.ignore()) continue;
                    Type t = createClassType(method.getGenericReturnType(), this.type);
                    list.add(new EnMember(createAttribute(factory, clazz, null, method, null), factory.loadEncoder(t)));
                }
                this.members = list.toArray(new EnMember[list.size()]);
                Arrays.sort(this.members);

            } catch (Exception ex) {
                throw new ConvertException(ex);
            }
        } finally {
            inited = true;
            synchronized (lock) {
                lock.notifyAll();
            }
        }
    }

    @Override
    public final void convertTo(W out, T value) {
        if (value == null) {
            out.wirteClassName(null);
            out.writeNull();
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
        if (value != null && value.getClass() != this.typeClass) {
            final Class clz = value.getClass();
            out.wirteClassName(factory.getEntity(clz));
            factory.loadEncoder(clz).convertTo(out, value);
            return;
        }
        out.writeObjectB(members.length, value);
        boolean comma = false;
        for (EnMember member : members) {
            comma = member.write(out, comma, value);
        }
        out.writeObjectE(value);
    }

    @Override
    public final Type getType() {
        return this.type;
    }

    @Override
    public String toString() {
        return "ObjectEncoder{" + "type=" + type + ", members=" + Arrays.toString(members) + '}';
    }

    static Type createClassType(final Type type, final Type declaringType) {
        if (TypeToken.isClassType(type)) return type;
        //存在通配符则declaringType 必须是 ParameterizedType
        if (!(declaringType instanceof ParameterizedType)) return Object.class;
        if (type instanceof ParameterizedType) {  // e.g. Map<String, String>
            final ParameterizedType pt = (ParameterizedType) type;
            final Type[] paramTypes = pt.getActualTypeArguments();
            for (int i = 0; i < paramTypes.length; i++) {
                paramTypes[i] = createClassType(paramTypes[i], declaringType);
            }
            return TypeToken.createParameterizedType(pt.getOwnerType(), pt.getRawType(), paramTypes);
        }

        final ParameterizedType declaringPType = (ParameterizedType) declaringType;
        final Type[] virTypes = ((Class) declaringPType.getRawType()).getTypeParameters();
        final Type[] desTypes = declaringPType.getActualTypeArguments();
        if (type instanceof WildcardType) {   // e.g. <? extends Serializable>
            final WildcardType wt = (WildcardType) type;
            for (Type f : wt.getUpperBounds()) {
                for (int i = 0; i < virTypes.length; i++) {
                    if (virTypes[i].equals(f)) return desTypes.length <= i ? Object.class : desTypes[i];
                }
            }
        } else if (type instanceof TypeVariable) { // e.g.  <? extends E>
            for (int i = 0; i < virTypes.length; i++) {
                if (virTypes[i].equals(type)) return desTypes.length <= i ? Object.class : desTypes[i];
            }
        }
        return type;
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

    static String readGetSetFieldName(Method method) {
        if (method == null) return null;
        String fname = method.getName();
        if (!fname.startsWith("is") && !fname.startsWith("get") && !fname.startsWith("set")) return fname;
        fname = fname.substring(fname.startsWith("is") ? 2 : 3);
        if (fname.length() > 1 && !(fname.charAt(1) >= 'A' && fname.charAt(1) <= 'Z')) {
            fname = Character.toLowerCase(fname.charAt(0)) + fname.substring(1);
        } else if (fname.length() == 1) {
            fname = "" + Character.toLowerCase(fname.charAt(0));
        }
        return fname;
    }

    static String[] findConstructorProperties(Creator creator) {
        try {
            Creator.ConstructorParameters cps = creator.getClass().getMethod("create", Object[].class).getAnnotation(Creator.ConstructorParameters.class);
            return cps == null ? null : cps.value();
        } catch (Exception e) {
            return null;
        }
    }

    static Attribute createAttribute(final Factory factory, Class clazz, final Field field, final Method getter, final Method setter) {
        String fieldalias = null;
        if (field != null) { // public field
            ConvertColumnEntry ref = factory.findRef(field);
            fieldalias = ref == null || ref.name().isEmpty() ? field.getName() : ref.name();
        } else if (getter != null) {
            ConvertColumnEntry ref = factory.findRef(getter);
            String mfieldname = readGetSetFieldName(getter);
            if (ref == null) {
                try {
                    ref = factory.findRef(clazz.getDeclaredField(mfieldname));
                } catch (Exception e) {
                }
            }
            fieldalias = ref == null || ref.name().isEmpty() ? mfieldname : ref.name();
        } else { // setter != null
            ConvertColumnEntry ref = factory.findRef(setter);
            String mfieldname = readGetSetFieldName(setter);
            if (ref == null) {
                try {
                    ref = factory.findRef(clazz.getDeclaredField(mfieldname));
                } catch (Exception e) {
                }
            }
            fieldalias = ref == null || ref.name().isEmpty() ? mfieldname : ref.name();
        }
        return Attributes.create(clazz, fieldalias, field, getter, setter);
    }

}
