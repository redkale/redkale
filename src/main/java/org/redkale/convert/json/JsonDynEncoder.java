/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.json;

import java.lang.reflect.*;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import org.redkale.asm.*;
import static org.redkale.asm.ClassWriter.COMPUTE_FRAMES;
import static org.redkale.asm.Opcodes.*;
import org.redkale.convert.*;
import org.redkale.convert.ext.*;
import org.redkale.util.*;

/**
 * 简单对象的JSON序列化操作类
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.3.0
 * @param <T> 序列化的数据类型
 */
@SuppressWarnings("unchecked")
public abstract class JsonDynEncoder<T> implements Encodeable<JsonWriter, T> {

    protected final Class typeClass;

    protected final ObjectEncoder<JsonWriter, T> objectEncoder;

    protected JsonDynEncoder(final JsonFactory factory, Type type) {
        this.typeClass = (Class) type;
        factory.register(type, this);
        this.objectEncoder = factory.createObjectEncoder(type);
    }

    @Override
    public boolean specifyable() {
        return false;
    }

    private static boolean checkMemberType(
            final JsonFactory factory, final Class declaringClass, Type type, Class clazz) {
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
        if (type == BigInteger.class) {
            return true;
        }
        if (type == BigDecimal.class) {
            return true;
        }
        if (type == LocalDate.class) {
            return true;
        }
        if (type == LocalTime.class) {
            return true;
        }
        if (type == LocalDateTime.class) {
            return true;
        }
        if (type == java.util.Date.class) {
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
                        || t == BigInteger.class
                        || t == BigDecimal.class
                        || t == LocalDate.class
                        || t == LocalTime.class
                        || t == LocalDateTime.class
                        || t == java.util.Date.class
                        || ((t instanceof Class) && ((Class) t).isEnum())) {
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    // 字段全部是primitive或String类型，且没有泛型的类才能动态生成JsonDynEncoder， 不支持的返回null
    public static JsonDynEncoder createDyncEncoder(final JsonFactory factory, final Type type) {
        if (!(type instanceof Class)) {
            return null;
        }
        // 发现有自定义的基础数据类型Encoder就不动态生成JsonDynEncoder了
        if (factory.loadEncoder(boolean.class) != BoolSimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(byte.class) != ByteSimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(short.class) != ShortSimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(char.class) != CharSimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(int.class) != IntSimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(float.class) != FloatSimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(long.class) != LongSimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(double.class) != DoubleSimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(String.class) != StringSimpledCoder.instance) {
            return null;
        }
        // array
        if (factory.loadEncoder(boolean[].class) != BoolArraySimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(byte[].class) != ByteArraySimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(short[].class) != ShortArraySimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(char[].class) != CharArraySimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(int[].class) != IntArraySimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(float[].class) != FloatArraySimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(long[].class) != LongArraySimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(double[].class) != DoubleArraySimpledCoder.instance) {
            return null;
        }
        if (factory.loadEncoder(String[].class) != StringArraySimpledCoder.instance) {
            return null;
        }

        final Class clazz = (Class) type;
        List<AccessibleObject> members = null;
        Set<String> names = new HashSet<>();
        try {
            ConvertColumnEntry ref;
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
                if (factory.findFieldCoder(clazz, field.getName()) != null) {
                    return null;
                }
                if (!(checkMemberType(factory, clazz, field.getGenericType(), field.getType()))) {
                    return null;
                }
                String name = convertFieldName(factory, clazz, field);
                if (names.contains(name)) {
                    continue;
                }
                names.add(name);
                if (members == null) {
                    members = new ArrayList<>();
                }
                members.add(field);
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
                if (method.getName().length() < 3) {
                    continue;
                }
                if (method.getName().equals("getClass")) {
                    continue;
                }
                if (!(method.getName().startsWith("is") && method.getName().length() > 2)
                        && !(method.getName().startsWith("get")
                                && method.getName().length() > 3)) {
                    continue;
                }
                if (factory.isConvertDisabled(method)) {
                    continue;
                }
                if (method.getParameterTypes().length != 0) {
                    continue;
                }
                if (method.getReturnType() == void.class) {
                    continue;
                }
                ref = factory.findRef(clazz, method);
                if (ref != null && ref.ignore()) {
                    continue;
                }
                if (ref != null && ref.fieldFunc() != null) {
                    return null;
                }
                if (!(checkMemberType(factory, clazz, method.getGenericReturnType(), method.getReturnType()))) {
                    return null;
                }
                String name = convertFieldName(factory, clazz, method);
                if (names.contains(name)) {
                    continue;
                }
                if (factory.findFieldCoder(clazz, name) != null) {
                    return null;
                }
                names.add(name);
                if (members == null) {
                    members = new ArrayList<>();
                }
                members.add(method);
            }
            if (members == null) {
                return null;
            }
            Collections.sort(members, (o1, o2) -> {
                ConvertColumnEntry ref1 = factory.findRef(clazz, o1);
                ConvertColumnEntry ref2 = factory.findRef(clazz, o2);
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
            return generateDyncEncoder(factory, clazz, members);
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    protected static String convertFieldName(final JsonFactory factory, Class clazz, AccessibleObject element) {
        ConvertColumnEntry ref = factory.findRef(clazz, element);
        String name = ref == null || ref.name().isEmpty() ? readGetSetFieldName(element) : ref.name();
        return name;
    }

    protected static ConvertSmallString readConvertSmallString(AccessibleObject element) {
        if (element instanceof Field) {
            return ((Field) element).getAnnotation(ConvertSmallString.class);
        }
        Method method = (Method) element;
        ConvertSmallString small = method.getAnnotation(ConvertSmallString.class);
        if (small == null) {
            try {
                Field f = method.getDeclaringClass().getDeclaredField(readGetSetFieldName(method));
                if (f != null) {
                    small = f.getAnnotation(ConvertSmallString.class);
                }
            } catch (Exception e) {
                // do nothing
            }
        }
        return small;
    }

    protected static Class readGetSetFieldType(AccessibleObject element) {
        if (element instanceof Field) {
            return ((Field) element).getType();
        }
        return ((Method) element).getReturnType();
    }

    protected static String readGetSetFieldName(AccessibleObject element) {
        if (element instanceof Field) {
            return ((Field) element).getName();
        }
        Method method = (Method) element;
        String fname = method.getName();
        if (!(fname.startsWith("is") && fname.length() > 2)
                && !(fname.startsWith("get") && fname.length() > 3)
                && !(fname.startsWith("set") && fname.length() > 3)) {
            return fname;
        }
        fname = fname.substring(fname.startsWith("is") ? 2 : 3);
        if (fname.length() > 1 && !(fname.charAt(1) >= 'A' && fname.charAt(1) <= 'Z')) {
            fname = Character.toLowerCase(fname.charAt(0)) + fname.substring(1);
        } else if (fname.length() == 1) {
            fname = "" + Character.toLowerCase(fname.charAt(0));
        }
        return fname;
    }

    protected static JsonDynEncoder generateDyncEncoder(
            final JsonFactory factory, final Class clazz, final List<AccessibleObject> members) {
        final ObjectEncoder selfObjEncoder = factory.createObjectEncoder(clazz);
        selfObjEncoder.init(factory);
        if (selfObjEncoder.getMembers().length != members.size()) {
            return null; // 存在ignore等定制配置
        }
        final String supDynName = JsonDynEncoder.class.getName().replace('.', '/');
        final String valtypeName = clazz.getName().replace('.', '/');
        final String writerName = JsonWriter.class.getName().replace('.', '/');
        final String encodeableName = Encodeable.class.getName().replace('.', '/');
        final String objEncoderName = ObjectEncoder.class.getName().replace('.', '/');
        final String typeDesc = org.redkale.asm.Type.getDescriptor(Type.class);
        final String jsonfactoryDesc = org.redkale.asm.Type.getDescriptor(JsonFactory.class);
        final String jsonwriterDesc = org.redkale.asm.Type.getDescriptor(JsonWriter.class);
        final String writerDesc = org.redkale.asm.Type.getDescriptor(Writer.class);
        final String encodeableDesc = org.redkale.asm.Type.getDescriptor(Encodeable.class);
        final String objEncoderDesc = org.redkale.asm.Type.getDescriptor(ObjectEncoder.class);
        final String valtypeDesc = org.redkale.asm.Type.getDescriptor(clazz);

        Map<String, AccessibleObject> mixedNames0 = null;
        StringBuilder memberb = new StringBuilder();
        for (AccessibleObject element : members) {
            ConvertColumnEntry ref1 = factory.findRef(clazz, element);
            final String fieldname = ref1 == null || ref1.name().isEmpty() ? readGetSetFieldName(element) : ref1.name();
            memberb.append(fieldname).append(',');
            final Class fieldType = readGetSetFieldType(element);
            if (fieldType != String.class && !fieldType.isPrimitive()) {
                if (mixedNames0 == null) {
                    mixedNames0 = new HashMap<>();
                }
                mixedNames0.put(fieldname, element);
            }
        }
        final Map<String, AccessibleObject> mixedNames = mixedNames0;
        final ClassLoader loader = Thread.currentThread().getContextClassLoader();
        final String newDynName = "org/redkaledyn/json/_Dyn" + JsonDynEncoder.class.getSimpleName() + "__"
                + clazz.getName().replace('.', '_').replace('$', '_') + "_" + factory.getFeatures() + "_"
                + Utility.md5Hex(memberb.toString()); // tiny必须要加上, 同一个类会有多个字段定制Convert
        try {
            Class clz = RedkaleClassLoader.findDynClass(newDynName.replace('/', '.'));
            Class newClazz = clz == null ? loader.loadClass(newDynName.replace('/', '.')) : clz;
            JsonDynEncoder resultEncoder =
                    (JsonDynEncoder) newClazz.getDeclaredConstructor(JsonFactory.class, Type.class)
                            .newInstance(factory, clazz);
            Field selfField = newClazz.getDeclaredField("objectEncoderSelf");
            selfField.setAccessible(true);
            selfField.set(resultEncoder, selfObjEncoder);
            if (mixedNames != null) {
                for (Map.Entry<String, AccessibleObject> en : mixedNames.entrySet()) {
                    Field f = newClazz.getDeclaredField(en.getKey() + "Encoder");
                    f.setAccessible(true);
                    f.set(
                            resultEncoder,
                            factory.loadEncoder(
                                    en.getValue() instanceof Field
                                            ? ((Field) en.getValue()).getGenericType()
                                            : ((Method) en.getValue()).getGenericReturnType()));
                }
            }
            return resultEncoder;
        } catch (Throwable ex) {
            // do nothing
        }
        // ------------------------------------------------------------------------------
        ClassWriter cw = new ClassWriter(COMPUTE_FRAMES);
        FieldVisitor fv;
        MethodVisitor mv;
        AnnotationVisitor av0;

        cw.visit(
                V11,
                ACC_PUBLIC + ACC_FINAL + ACC_SUPER,
                newDynName,
                "L" + supDynName + "<" + valtypeDesc + ">;",
                supDynName,
                null);

        fv = cw.visitField(ACC_PROTECTED, "objectEncoderSelf", objEncoderDesc, null, null);
        fv.visitEnd();
        final int membersSize = members.size();
        boolean onlyTwoIntFieldObjectFlag = false; // 只包含两个int字段
        boolean onlyOneLatin1FieldObjectFlag = false; // 只包含一个Latin1 String字段
        boolean onlyShotIntLongLatin1MoreFieldObjectFlag = true;
        int intFieldCount = 0;
        for (AccessibleObject element : members) {
            ConvertColumnEntry ref1 = factory.findRef(clazz, element);
            final String fieldname = ref1 == null || ref1.name().isEmpty() ? readGetSetFieldName(element) : ref1.name();
            fv = cw.visitField(ACC_PROTECTED + ACC_FINAL, fieldname + "FieldBytes", "[B", null, null);
            fv.visitEnd();
            fv = cw.visitField(ACC_PROTECTED + ACC_FINAL, fieldname + "CommaFieldBytes", "[B", null, null);
            fv.visitEnd();
            fv = cw.visitField(ACC_PROTECTED + ACC_FINAL, fieldname + "FirstFieldBytes", "[B", null, null);
            fv.visitEnd();
            fv = cw.visitField(ACC_PROTECTED + ACC_FINAL, fieldname + "FieldChars", "[C", null, null);
            fv.visitEnd();
            fv = cw.visitField(ACC_PROTECTED + ACC_FINAL, fieldname + "CommaFieldChars", "[C", null, null);
            fv.visitEnd();
            fv = cw.visitField(ACC_PROTECTED + ACC_FINAL, fieldname + "FirstFieldChars", "[C", null, null);
            fv.visitEnd();
            final Class fieldType = readGetSetFieldType(element);
            if (fieldType != String.class && !fieldType.isPrimitive()) {
                fv = cw.visitField(ACC_PROTECTED, fieldname + "Encoder", encodeableDesc, null, null);
                fv.visitEnd();
            }
            if (fieldType == int.class) {
                intFieldCount++;
            }
            if (fieldType == String.class && membersSize == 1 && readConvertSmallString(element) != null) {
                onlyOneLatin1FieldObjectFlag = true;
            } else if (fieldType != short.class
                    && fieldType != int.class
                    && fieldType != long.class
                    && !(fieldType == String.class && readConvertSmallString(element) != null)) {
                onlyShotIntLongLatin1MoreFieldObjectFlag = false;
            }
        }
        if (intFieldCount == 2 && intFieldCount == membersSize) {
            onlyTwoIntFieldObjectFlag = true;
        }
        if (onlyShotIntLongLatin1MoreFieldObjectFlag && membersSize < 2) {
            onlyShotIntLongLatin1MoreFieldObjectFlag = false; // 字段个数必须大于1
        }
        { // 构造函数
            mv = (cw.visitMethod(ACC_PUBLIC, "<init>", "(" + jsonfactoryDesc + typeDesc + ")V", null, null));
            // mv.setDebug(true);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitVarInsn(ALOAD, 2);
            mv.visitMethodInsn(INVOKESPECIAL, supDynName, "<init>", "(" + jsonfactoryDesc + typeDesc + ")V", false);

            for (AccessibleObject element : members) {
                ConvertColumnEntry ref1 = factory.findRef(clazz, element);
                final String fieldname =
                        ref1 == null || ref1.name().isEmpty() ? readGetSetFieldName(element) : ref1.name();
                // xxxFieldBytes
                mv.visitVarInsn(ALOAD, 0);
                mv.visitLdcInsn("\"" + fieldname + "\":");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "getBytes", "()[B", false);
                mv.visitFieldInsn(PUTFIELD, newDynName, fieldname + "FieldBytes", "[B");
                // xxxCommaFieldBytes
                mv.visitVarInsn(ALOAD, 0);
                mv.visitLdcInsn(",\"" + fieldname + "\":");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "getBytes", "()[B", false);
                mv.visitFieldInsn(PUTFIELD, newDynName, fieldname + "CommaFieldBytes", "[B");
                // xxxFirstFieldBytes
                mv.visitVarInsn(ALOAD, 0);
                mv.visitLdcInsn("{\"" + fieldname + "\":");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "getBytes", "()[B", false);
                mv.visitFieldInsn(PUTFIELD, newDynName, fieldname + "FirstFieldBytes", "[B");
                // xxxFieldChars
                mv.visitVarInsn(ALOAD, 0);
                mv.visitLdcInsn("\"" + fieldname + "\":");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "toCharArray", "()[C", false);
                mv.visitFieldInsn(PUTFIELD, newDynName, fieldname + "FieldChars", "[C");
                // xxxCommaFieldChars
                mv.visitVarInsn(ALOAD, 0);
                mv.visitLdcInsn(",\"" + fieldname + "\":");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "toCharArray", "()[C", false);
                mv.visitFieldInsn(PUTFIELD, newDynName, fieldname + "CommaFieldChars", "[C");
                // xxxFirstFieldChars
                mv.visitVarInsn(ALOAD, 0);
                mv.visitLdcInsn("{\"" + fieldname + "\":");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "toCharArray", "()[C", false);
                mv.visitFieldInsn(PUTFIELD, newDynName, fieldname + "FirstFieldChars", "[C");
            }
            mv.visitInsn(RETURN);
            mv.visitMaxs(1 + members.size(), 1 + members.size());
            mv.visitEnd();
        }

        {
            mv = (cw.visitMethod(ACC_PUBLIC, "convertTo", "(" + jsonwriterDesc + valtypeDesc + ")V", null, null));
            // mv.setDebug(true);
            { // if (value == null) { out.writeObjectNull(null);  return; }
                mv.visitVarInsn(ALOAD, 2);
                Label valif = new Label();
                mv.visitJumpInsn(IFNONNULL, valif);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitInsn(ACONST_NULL);
                mv.visitMethodInsn(INVOKEVIRTUAL, writerName, "writeObjectNull", "(Ljava/lang/Class;)V", false);
                mv.visitInsn(RETURN);
                mv.visitLabel(valif);
                mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            }
            { // if (!out.isExtFuncEmpty()) { objectEncoder.convertTo(out, value);  return; }
                mv.visitVarInsn(ALOAD, 1);
                mv.visitMethodInsn(INVOKEVIRTUAL, writerName, "isExtFuncEmpty", "()Z", false);
                Label extif = new Label();
                mv.visitJumpInsn(IFNE, extif);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, newDynName, "objectEncoderSelf", objEncoderDesc);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitVarInsn(ALOAD, 2);
                mv.visitMethodInsn(
                        INVOKEVIRTUAL,
                        objEncoderName,
                        "convertTo",
                        "(" + org.redkale.asm.Type.getDescriptor(Writer.class) + "Ljava/lang/Object;)V",
                        false);
                mv.visitInsn(RETURN);
                mv.visitLabel(extif);
                mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            }

            int maxLocals = 4;
            int elementIndex = -1;
            final boolean tiny = ConvertFactory.checkTinyFeature(factory.getFeatures());
            final boolean nullable = ConvertFactory.checkNullableFeature(factory.getFeatures());
            final Class firstType = readGetSetFieldType(members.get(0));
            final boolean mustHadComma = firstType.isPrimitive()
                    && (firstType != boolean.class || !tiny || nullable); // byte/short/char/int/float/long/double

            if (onlyOneLatin1FieldObjectFlag) {
                // out.writeObjectByOnlyOneLatin1FieldValue(messageFirstFieldBytes, value.getMessage());elementIndex++;
                elementIndex++;
                AccessibleObject element = members.get(elementIndex);
                ConvertColumnEntry ref1 = factory.findRef(clazz, element);
                final String fieldName =
                        ref1 == null || ref1.name().isEmpty() ? readGetSetFieldName(element) : ref1.name();
                final Class fieldtype = readGetSetFieldType(element);

                mv.visitVarInsn(ALOAD, 1);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, newDynName, fieldName + "FirstFieldBytes", "[B");
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, newDynName, fieldName + "FirstFieldChars", "[C");

                mv.visitVarInsn(ALOAD, 2); // String message = value.getMessage(); 加载 value
                if (element instanceof Field) {
                    mv.visitFieldInsn(
                            GETFIELD,
                            valtypeName,
                            ((Field) element).getName(),
                            org.redkale.asm.Type.getDescriptor(fieldtype));
                } else {
                    mv.visitMethodInsn(
                            INVOKEVIRTUAL,
                            valtypeName,
                            ((Method) element).getName(),
                            "()" + org.redkale.asm.Type.getDescriptor(fieldtype),
                            false);
                }
                mv.visitMethodInsn(
                        INVOKEVIRTUAL,
                        writerName,
                        "writeObjectByOnlyOneLatin1FieldValue",
                        "([B[CLjava/lang/String;)V",
                        false);
                maxLocals++;
            } else if (onlyTwoIntFieldObjectFlag) {
                elementIndex++;
                AccessibleObject element1 = members.get(elementIndex);
                ConvertColumnEntry ref1 = factory.findRef(clazz, element1);
                final String fieldName1 =
                        ref1 == null || ref1.name().isEmpty() ? readGetSetFieldName(element1) : ref1.name();
                final Class fieldType1 = readGetSetFieldType(element1);

                elementIndex++;
                AccessibleObject element2 = members.get(elementIndex);
                ConvertColumnEntry ref2 = factory.findRef(clazz, element2);
                final String fieldName2 =
                        ref2 == null || ref2.name().isEmpty() ? readGetSetFieldName(element2) : ref2.name();
                final Class fieldtype2 = readGetSetFieldType(element2);

                mv.visitVarInsn(ALOAD, 1);

                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, newDynName, fieldName1 + "FirstFieldBytes", "[B");
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, newDynName, fieldName1 + "FirstFieldChars", "[C");

                mv.visitVarInsn(ALOAD, 2); // String message = value.getMessage(); 加载 value
                if (element1 instanceof Field) {
                    mv.visitFieldInsn(
                            GETFIELD,
                            valtypeName,
                            ((Field) element1).getName(),
                            org.redkale.asm.Type.getDescriptor(fieldType1));
                } else {
                    mv.visitMethodInsn(
                            INVOKEVIRTUAL,
                            valtypeName,
                            ((Method) element1).getName(),
                            "()" + org.redkale.asm.Type.getDescriptor(fieldType1),
                            false);
                }
                maxLocals++;

                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, newDynName, fieldName2 + "CommaFieldBytes", "[B");
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, newDynName, fieldName2 + "CommaFieldChars", "[C");

                mv.visitVarInsn(ALOAD, 2); // String message = value.getMessage(); 加载 value
                if (element2 instanceof Field) {
                    mv.visitFieldInsn(
                            GETFIELD,
                            valtypeName,
                            ((Field) element2).getName(),
                            org.redkale.asm.Type.getDescriptor(fieldtype2));
                } else {
                    mv.visitMethodInsn(
                            INVOKEVIRTUAL,
                            valtypeName,
                            ((Method) element2).getName(),
                            "()" + org.redkale.asm.Type.getDescriptor(fieldtype2),
                            false);
                }
                maxLocals++;

                mv.visitMethodInsn(
                        INVOKEVIRTUAL, writerName, "writeObjectByOnlyTwoIntFieldValue", "([B[CI[B[CI)V", false);

            } else if (onlyShotIntLongLatin1MoreFieldObjectFlag && mustHadComma) {
                for (AccessibleObject element : members) {
                    elementIndex++;
                    ConvertColumnEntry ref1 = factory.findRef(clazz, element);
                    final String fieldname =
                            ref1 == null || ref1.name().isEmpty() ? readGetSetFieldName(element) : ref1.name();
                    final Class fieldtype = readGetSetFieldType(element);

                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(
                            GETFIELD,
                            newDynName,
                            fieldname + (elementIndex == 0 ? "FirstFieldBytes" : "CommaFieldBytes"),
                            "[B");
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(
                            GETFIELD,
                            newDynName,
                            fieldname + (elementIndex == 0 ? "FirstFieldChars" : "CommaFieldChars"),
                            "[C");

                    mv.visitVarInsn(ALOAD, 2); // String message = value.getMessage(); 加载 value
                    if (element instanceof Field) {
                        mv.visitFieldInsn(
                                GETFIELD,
                                valtypeName,
                                ((Field) element).getName(),
                                org.redkale.asm.Type.getDescriptor(fieldtype));
                    } else {
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL,
                                valtypeName,
                                ((Method) element).getName(),
                                "()" + org.redkale.asm.Type.getDescriptor(fieldtype),
                                false);
                    }
                    if (fieldtype == short.class) {
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL,
                                writerName,
                                elementIndex + 1 == membersSize ? "writeLastFieldShortValue" : "writeFieldShortValue",
                                "([B[CS)V",
                                false);
                    } else if (fieldtype == int.class) {
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL,
                                writerName,
                                elementIndex + 1 == membersSize ? "writeLastFieldIntValue" : "writeFieldIntValue",
                                "([B[CI)V",
                                false);
                    } else if (fieldtype == long.class) {
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL,
                                writerName,
                                elementIndex + 1 == membersSize ? "writeLastFieldLongValue" : "writeFieldLongValue",
                                "([B[CJ)V",
                                false);
                    } else {
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL,
                                writerName,
                                elementIndex + 1 == membersSize ? "writeLastFieldLatin1Value" : "writeFieldLatin1Value",
                                "([B[CLjava/lang/String;)V",
                                false);
                    }

                    if (fieldtype == long.class || fieldtype == double.class) {
                        maxLocals += 2;
                    } else {
                        maxLocals++;
                    }
                }
            } else {
                { // out.writeTo('{');
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitIntInsn(BIPUSH, '{');
                    mv.visitMethodInsn(INVOKEVIRTUAL, writerName, "writeTo", "(B)V", false);
                }

                if (!mustHadComma) { // boolean comma = false;
                    mv.visitInsn(ICONST_0);
                    mv.visitVarInsn(ISTORE, 3);
                }
                for (AccessibleObject element : members) {
                    elementIndex++;
                    ConvertColumnEntry ref1 = factory.findRef(clazz, element);
                    final String fieldname =
                            ref1 == null || ref1.name().isEmpty() ? readGetSetFieldName(element) : ref1.name();
                    final Class fieldtype = readGetSetFieldType(element);
                    int storeid = ASTORE;
                    int loadid = ALOAD;
                    { // String message = value.getMessage();
                        mv.visitVarInsn(ALOAD, 2); // 加载 value
                        if (element instanceof Field) {
                            mv.visitFieldInsn(
                                    GETFIELD,
                                    valtypeName,
                                    ((Field) element).getName(),
                                    org.redkale.asm.Type.getDescriptor(fieldtype));
                        } else {
                            mv.visitMethodInsn(
                                    INVOKEVIRTUAL,
                                    valtypeName,
                                    ((Method) element).getName(),
                                    "()" + org.redkale.asm.Type.getDescriptor(fieldtype),
                                    false);
                        }
                        if (fieldtype == boolean.class) {
                            storeid = ISTORE;
                            loadid = ILOAD;
                            mv.visitVarInsn(storeid, maxLocals);
                        } else if (fieldtype == byte.class) {
                            storeid = ISTORE;
                            loadid = ILOAD;
                            mv.visitVarInsn(storeid, maxLocals);
                        } else if (fieldtype == short.class) {
                            storeid = ISTORE;
                            loadid = ILOAD;
                            mv.visitVarInsn(storeid, maxLocals);
                        } else if (fieldtype == char.class) {
                            storeid = ISTORE;
                            loadid = ILOAD;
                            mv.visitVarInsn(storeid, maxLocals);
                        } else if (fieldtype == int.class) {
                            storeid = ISTORE;
                            loadid = ILOAD;
                            mv.visitVarInsn(storeid, maxLocals);
                        } else if (fieldtype == float.class) {
                            storeid = FSTORE;
                            loadid = FLOAD;
                            mv.visitVarInsn(storeid, maxLocals);
                        } else if (fieldtype == long.class) {
                            storeid = LSTORE;
                            loadid = LLOAD;
                            mv.visitVarInsn(storeid, maxLocals);
                        } else if (fieldtype == double.class) {
                            storeid = DSTORE;
                            loadid = DLOAD;
                            mv.visitVarInsn(storeid, maxLocals);
                        } else {
                            // storeid = ASTORE;
                            // loadid = ALOAD;
                            mv.visitVarInsn(storeid, maxLocals);
                        }
                    }
                    Label msgnotemptyif = null;
                    if (!fieldtype.isPrimitive() && !nullable) { // if (message != null) { start
                        mv.visitVarInsn(loadid, maxLocals);
                        msgnotemptyif = new Label();
                        mv.visitJumpInsn(IFNULL, msgnotemptyif);
                        if (tiny && fieldtype == String.class) {
                            mv.visitVarInsn(loadid, maxLocals);
                            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "isEmpty", "()Z", false);
                            mv.visitJumpInsn(IFNE, msgnotemptyif);
                        }
                    } else if (fieldtype == boolean.class && tiny) {
                        mv.visitVarInsn(loadid, maxLocals);
                        msgnotemptyif = new Label();
                        mv.visitJumpInsn(IFEQ, msgnotemptyif);
                    }
                    if (mustHadComma) { // 第一个字段必然会写入
                        if (elementIndex == 0) { // 第一个
                            // out.writeTo(messageFieldBytes);
                            mv.visitVarInsn(ALOAD, 1);
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitFieldInsn(GETFIELD, newDynName, fieldname + "FieldBytes", "[B");
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitFieldInsn(GETFIELD, newDynName, fieldname + "FieldChars", "[C");
                            mv.visitMethodInsn(INVOKEVIRTUAL, writerName, "writeField", "([B[C)V", false);
                        } else {
                            // out.writeTo(messageCommaFieldBytes);
                            mv.visitVarInsn(ALOAD, 1);
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitFieldInsn(GETFIELD, newDynName, fieldname + "CommaFieldBytes", "[B");
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitFieldInsn(GETFIELD, newDynName, fieldname + "CommaFieldChars", "[C");
                            mv.visitMethodInsn(INVOKEVIRTUAL, writerName, "writeField", "([B[C)V", false);
                        }
                    } else { // if(comma) {} else {}  代码块
                        // if (comma) { start
                        mv.visitVarInsn(ILOAD, 3);
                        Label commaif = new Label();
                        mv.visitJumpInsn(IFEQ, commaif);

                        // out.writeTo(messageCommaFieldBytes);
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitFieldInsn(GETFIELD, newDynName, fieldname + "CommaFieldBytes", "[B");
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitFieldInsn(GETFIELD, newDynName, fieldname + "CommaFieldChars", "[C");
                        mv.visitMethodInsn(INVOKEVIRTUAL, writerName, "writeField", "([B[C)V", false);

                        Label commaelse = new Label();
                        mv.visitJumpInsn(GOTO, commaelse);
                        mv.visitLabel(commaif);
                        if (fieldtype == boolean.class) {
                            mv.visitFrame(Opcodes.F_APPEND, 1, new Object[] {Opcodes.INTEGER}, 0, null);
                        } else if (fieldtype == byte.class) {
                            mv.visitFrame(Opcodes.F_APPEND, 1, new Object[] {Opcodes.INTEGER}, 0, null);
                        } else if (fieldtype == short.class) {
                            mv.visitFrame(Opcodes.F_APPEND, 1, new Object[] {Opcodes.INTEGER}, 0, null);
                        } else if (fieldtype == char.class) {
                            mv.visitFrame(Opcodes.F_APPEND, 1, new Object[] {Opcodes.INTEGER}, 0, null);
                        } else if (fieldtype == int.class) {
                            mv.visitFrame(Opcodes.F_APPEND, 1, new Object[] {Opcodes.INTEGER}, 0, null);
                        } else if (fieldtype == float.class) {
                            mv.visitFrame(Opcodes.F_APPEND, 1, new Object[] {Opcodes.FLOAT}, 0, null);
                        } else if (fieldtype == long.class) {
                            mv.visitFrame(Opcodes.F_APPEND, 1, new Object[] {Opcodes.LONG}, 0, null);
                        } else if (fieldtype == double.class) {
                            mv.visitFrame(Opcodes.F_APPEND, 1, new Object[] {Opcodes.DOUBLE}, 0, null);
                        } else {
                            mv.visitFrame(
                                    Opcodes.F_APPEND,
                                    2,
                                    new Object[] {Opcodes.INTEGER, "java/lang/String"},
                                    0,
                                    null); // } else {  comma
                        }
                        // out.writeTo(messageFieldBytes);
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitFieldInsn(GETFIELD, newDynName, fieldname + "FieldBytes", "[B");
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitFieldInsn(GETFIELD, newDynName, fieldname + "FieldChars", "[C");
                        mv.visitMethodInsn(INVOKEVIRTUAL, writerName, "writeField", "([B[C)V", false);
                        // comma = true;
                        mv.visitInsn(ICONST_1);
                        mv.visitVarInsn(ISTORE, 3);
                        mv.visitLabel(commaelse);
                        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null); // if (comma) } end
                    }
                    // out.writeString(message);
                    if (fieldtype == boolean.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitVarInsn(loadid, maxLocals);
                        mv.visitMethodInsn(INVOKEVIRTUAL, writerName, "writeBoolean", "(Z)V", false);
                    } else if (fieldtype == byte.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitVarInsn(loadid, maxLocals);
                        mv.visitMethodInsn(INVOKEVIRTUAL, writerName, "writeByte", "(B)V", false);
                    } else if (fieldtype == short.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitVarInsn(loadid, maxLocals);
                        mv.visitMethodInsn(INVOKEVIRTUAL, writerName, "writeShort", "(S)V", false);
                    } else if (fieldtype == char.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitVarInsn(loadid, maxLocals);
                        mv.visitMethodInsn(INVOKEVIRTUAL, writerName, "writeChar", "(C)V", false);
                    } else if (fieldtype == int.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitVarInsn(loadid, maxLocals);
                        mv.visitMethodInsn(INVOKEVIRTUAL, writerName, "writeInt", "(I)V", false);
                    } else if (fieldtype == float.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitVarInsn(loadid, maxLocals);
                        mv.visitMethodInsn(INVOKEVIRTUAL, writerName, "writeFloat", "(F)V", false);
                    } else if (fieldtype == long.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitVarInsn(loadid, maxLocals);
                        mv.visitMethodInsn(INVOKEVIRTUAL, writerName, "writeLong", "(J)V", false);
                    } else if (fieldtype == double.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitVarInsn(loadid, maxLocals);
                        mv.visitMethodInsn(INVOKEVIRTUAL, writerName, "writeDouble", "(D)V", false);
                    } else if (fieldtype == String.class) {
                        if (readConvertSmallString(element) == null) {
                            mv.visitVarInsn(ALOAD, 1);
                            mv.visitVarInsn(loadid, maxLocals);
                            mv.visitMethodInsn(
                                    INVOKEVIRTUAL, writerName, "writeString", "(Ljava/lang/String;)V", false);
                        } else {
                            mv.visitVarInsn(ALOAD, 1);
                            mv.visitInsn(ICONST_1);
                            mv.visitVarInsn(loadid, maxLocals);
                            mv.visitMethodInsn(
                                    INVOKEVIRTUAL, writerName, "writeLatin1To", "(ZLjava/lang/String;)V", false);
                        }
                    } else { // int[],Boolean[],String[]
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitFieldInsn(GETFIELD, newDynName, fieldname + "Encoder", encodeableDesc);
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitVarInsn(loadid, maxLocals);
                        mv.visitMethodInsn(
                                INVOKEINTERFACE,
                                encodeableName,
                                "convertTo",
                                "(" + writerDesc + "Ljava/lang/Object;)V",
                                true);
                    }
                    if (!fieldtype.isPrimitive() && !nullable) { // if (message != null) } end
                        mv.visitLabel(msgnotemptyif);
                        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                    } else if (fieldtype == boolean.class && tiny) {
                        mv.visitLabel(msgnotemptyif);
                        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                    }
                    if (fieldtype == long.class || fieldtype == double.class) {
                        maxLocals += 2;
                    } else {
                        maxLocals++;
                    }
                }
                { // out.writeTo('}');
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitIntInsn(BIPUSH, '}');
                    mv.visitMethodInsn(INVOKEVIRTUAL, writerName, "writeTo", "(B)V", false);
                }
            }
            mv.visitInsn(RETURN);
            mv.visitMaxs(maxLocals, maxLocals);
            mv.visitEnd();
        }
        {
            mv = (cw.visitMethod(
                    ACC_PUBLIC + ACC_BRIDGE + ACC_SYNTHETIC,
                    "convertTo",
                    "(" + jsonwriterDesc + "Ljava/lang/Object;)V",
                    null,
                    null));
            // mv.setDebug(true);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitVarInsn(ALOAD, 2);
            mv.visitTypeInsn(CHECKCAST, valtypeName);
            mv.visitMethodInsn(
                    INVOKEVIRTUAL, newDynName, "convertTo", "(" + jsonwriterDesc + valtypeDesc + ")V", false);
            mv.visitInsn(RETURN);
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
        RedkaleClassLoader.putReflectionDeclaredConstructors(
                newClazz, newDynName.replace('/', '.'), JsonFactory.class, Type.class);
        try {
            JsonDynEncoder resultEncoder =
                    (JsonDynEncoder) newClazz.getDeclaredConstructor(JsonFactory.class, Type.class)
                            .newInstance(factory, clazz);
            Field selfField = newClazz.getDeclaredField("objectEncoderSelf");
            selfField.setAccessible(true);
            selfField.set(resultEncoder, selfObjEncoder);
            RedkaleClassLoader.putReflectionField(newDynName.replace('/', '.'), selfField);
            if (mixedNames != null) {
                for (Map.Entry<String, AccessibleObject> en : mixedNames.entrySet()) {
                    Field f = newClazz.getDeclaredField(en.getKey() + "Encoder");
                    f.setAccessible(true);
                    f.set(
                            resultEncoder,
                            factory.loadEncoder(
                                    en.getValue() instanceof Field
                                            ? ((Field) en.getValue()).getGenericType()
                                            : ((Method) en.getValue()).getGenericReturnType()));
                    RedkaleClassLoader.putReflectionField(newDynName.replace('/', '.'), f);
                }
            }
            return resultEncoder;
        } catch (Exception ex) {
            throw new ConvertException(ex);
        }
    }

    @Override
    public abstract void convertTo(JsonWriter out, T value);

    @Override
    public Type getType() {
        return typeClass;
    }
}
