/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.json;

import java.lang.reflect.*;
import java.util.*;
import org.redkale.asm.ClassWriter;
import static org.redkale.asm.ClassWriter.COMPUTE_FRAMES;
import org.redkale.asm.FieldVisitor;
import org.redkale.asm.Label;
import org.redkale.asm.MethodVisitor;
import org.redkale.asm.Opcodes;
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
public abstract class JsonDynEncoder<T> extends ObjectEncoder<JsonWriter, T> {

    protected final Class typeClass;

    protected final ObjectEncoder<JsonWriter, T> objectEncoderSelf;

    protected JsonDynEncoder(JsonFactory factory, Type type, ObjectEncoder objectEncoderSelf) {
        super(type);
        this.typeClass = (Class) type;
        this.factory = factory;
        this.objectEncoderSelf = objectEncoderSelf;
        this.members = objectEncoderSelf.getMembers();
        this.inited = true;
        factory.register(type, this);
    }

    protected static JsonDynEncoder generateDyncEncoder(
            final JsonFactory factory, final Class clazz, final List<AccessibleObject> elements) {
        final ObjectEncoder selfObjEncoder = factory.createObjectEncoder(clazz);
        selfObjEncoder.init(factory); // 必须执行，初始化EnMember内部信息
        if (selfObjEncoder.getMembers().length != elements.size()) {
            return null; // 存在ignore等定制配置
        }

        Map<String, AccessibleObject> mixedNames0 = null;
        StringBuilder elementb = new StringBuilder();
        for (AccessibleObject element : elements) {
            final String fieldName = factory.readConvertFieldName(clazz, element);
            elementb.append(fieldName).append(',');
            final Class fieldType = readGetSetFieldType(element);
            if (fieldType != String.class && !fieldType.isPrimitive()) {
                if (mixedNames0 == null) {
                    mixedNames0 = new HashMap<>();
                }
                mixedNames0.put(fieldName, element);
            }
        }
        final Map<String, AccessibleObject> mixedNames = mixedNames0;
        final ClassLoader loader = Thread.currentThread().getContextClassLoader();
        final String newDynName = "org/redkaledyn/json/_Dyn" + JsonDynEncoder.class.getSimpleName() + "__"
                + clazz.getName().replace('.', '_').replace('$', '_') + "_" + factory.getFeatures() + "_"
                + Utility.md5Hex(elementb.toString()); // tiny必须要加上, 同一个类会有多个字段定制Convert
        try {
            Class clz = RedkaleClassLoader.findDynClass(newDynName.replace('/', '.'));
            Class newClazz = clz == null ? loader.loadClass(newDynName.replace('/', '.')) : clz;
            JsonDynEncoder resultEncoder =
                    (JsonDynEncoder) newClazz.getConstructor(JsonFactory.class, Type.class, ObjectEncoder.class)
                            .newInstance(factory, clazz, selfObjEncoder);
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
        // ------------------------------------------------------------------------------
        ClassWriter cw = new ClassWriter(COMPUTE_FRAMES);
        FieldVisitor fv;
        MethodVisitor mv;
        cw.visit(
                V11,
                ACC_PUBLIC + ACC_FINAL + ACC_SUPER,
                newDynName,
                "L" + supDynName + "<" + valtypeDesc + ">;",
                supDynName,
                null);

        final int membersSize = elements.size();
        boolean onlyTwoIntFieldObjectFlag = false; // 只包含两个int字段
        boolean onlyOneLatin1FieldObjectFlag = false; // 只包含一个Latin1 String字段
        boolean onlyShotIntLongLatin1MoreFieldObjectFlag = true;
        int intFieldCount = 0;
        for (AccessibleObject element : elements) {
            final String fieldName = factory.readConvertFieldName(clazz, element);
            fv = cw.visitField(ACC_PROTECTED + ACC_FINAL, fieldName + "FieldBytes", "[B", null, null);
            fv.visitEnd();
            fv = cw.visitField(ACC_PROTECTED + ACC_FINAL, fieldName + "CommaFieldBytes", "[B", null, null);
            fv.visitEnd();
            fv = cw.visitField(ACC_PROTECTED + ACC_FINAL, fieldName + "FirstFieldBytes", "[B", null, null);
            fv.visitEnd();
            fv = cw.visitField(ACC_PROTECTED + ACC_FINAL, fieldName + "FieldChars", "[C", null, null);
            fv.visitEnd();
            fv = cw.visitField(ACC_PROTECTED + ACC_FINAL, fieldName + "CommaFieldChars", "[C", null, null);
            fv.visitEnd();
            fv = cw.visitField(ACC_PROTECTED + ACC_FINAL, fieldName + "FirstFieldChars", "[C", null, null);
            fv.visitEnd();
            final Class fieldType = readGetSetFieldType(element);
            if (fieldType != String.class && !fieldType.isPrimitive()) {
                fv = cw.visitField(ACC_PROTECTED, fieldName + "Encoder", encodeableDesc, null, null);
                fv.visitEnd();
            }
            if (fieldType == int.class) {
                intFieldCount++;
            }
            if (fieldType == String.class && membersSize == 1 && readConvertSmallString(factory, element) != null) {
                onlyOneLatin1FieldObjectFlag = true;
            } else if (fieldType != short.class
                    && fieldType != int.class
                    && fieldType != long.class
                    && !(fieldType == String.class && readConvertSmallString(factory, element) != null)) {
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
            mv = (cw.visitMethod(
                    ACC_PUBLIC, "<init>", "(" + jsonfactoryDesc + typeDesc + objEncoderDesc + ")V", null, null));
            // mv.setDebug(true);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitVarInsn(ALOAD, 2);
            mv.visitVarInsn(ALOAD, 3);
            mv.visitMethodInsn(
                    INVOKESPECIAL,
                    supDynName,
                    "<init>",
                    "(" + jsonfactoryDesc + typeDesc + objEncoderDesc + ")V",
                    false);

            for (AccessibleObject element : elements) {
                final String fieldName = factory.readConvertFieldName(clazz, element);
                // xxxFieldBytes
                mv.visitVarInsn(ALOAD, 0);
                mv.visitLdcInsn("\"" + fieldName + "\":");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "getBytes", "()[B", false);
                mv.visitFieldInsn(PUTFIELD, newDynName, fieldName + "FieldBytes", "[B");
                // xxxCommaFieldBytes
                mv.visitVarInsn(ALOAD, 0);
                mv.visitLdcInsn(",\"" + fieldName + "\":");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "getBytes", "()[B", false);
                mv.visitFieldInsn(PUTFIELD, newDynName, fieldName + "CommaFieldBytes", "[B");
                // xxxFirstFieldBytes
                mv.visitVarInsn(ALOAD, 0);
                mv.visitLdcInsn("{\"" + fieldName + "\":");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "getBytes", "()[B", false);
                mv.visitFieldInsn(PUTFIELD, newDynName, fieldName + "FirstFieldBytes", "[B");
                // xxxFieldChars
                mv.visitVarInsn(ALOAD, 0);
                mv.visitLdcInsn("\"" + fieldName + "\":");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "toCharArray", "()[C", false);
                mv.visitFieldInsn(PUTFIELD, newDynName, fieldName + "FieldChars", "[C");
                // xxxCommaFieldChars
                mv.visitVarInsn(ALOAD, 0);
                mv.visitLdcInsn(",\"" + fieldName + "\":");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "toCharArray", "()[C", false);
                mv.visitFieldInsn(PUTFIELD, newDynName, fieldName + "CommaFieldChars", "[C");
                // xxxFirstFieldChars
                mv.visitVarInsn(ALOAD, 0);
                mv.visitLdcInsn("{\"" + fieldName + "\":");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "toCharArray", "()[C", false);
                mv.visitFieldInsn(PUTFIELD, newDynName, fieldName + "FirstFieldChars", "[C");
            }
            mv.visitInsn(RETURN);
            mv.visitMaxs(1 + elements.size(), 1 + elements.size());
            mv.visitEnd();
        }

        { // convertTo 方法
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
            final Class firstType = readGetSetFieldType(elements.get(0));
            final boolean mustHadComma = firstType.isPrimitive()
                    && (firstType != boolean.class || !tiny || nullable); // byte/short/char/int/float/long/double

            if (onlyOneLatin1FieldObjectFlag) {
                // out.writeObjectByOnlyOneLatin1FieldValue(messageFirstFieldBytes, value.getMessage());elementIndex++;
                elementIndex++;
                AccessibleObject element = elements.get(elementIndex);
                final String fieldName = factory.readConvertFieldName(clazz, element);
                final Class fieldType = readGetSetFieldType(element);

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
                            org.redkale.asm.Type.getDescriptor(fieldType));
                } else {
                    mv.visitMethodInsn(
                            INVOKEVIRTUAL,
                            valtypeName,
                            ((Method) element).getName(),
                            "()" + org.redkale.asm.Type.getDescriptor(fieldType),
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
                AccessibleObject element1 = elements.get(elementIndex);
                final String fieldName1 = factory.readConvertFieldName(clazz, element1);
                final Class fieldType1 = readGetSetFieldType(element1);

                elementIndex++;
                AccessibleObject element2 = elements.get(elementIndex);
                final String fieldName2 = factory.readConvertFieldName(clazz, element2);
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
                for (AccessibleObject element : elements) {
                    elementIndex++;
                    final String fieldName = factory.readConvertFieldName(clazz, element);
                    final Class fieldType = readGetSetFieldType(element);

                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(
                            GETFIELD,
                            newDynName,
                            fieldName + (elementIndex == 0 ? "FirstFieldBytes" : "CommaFieldBytes"),
                            "[B");
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(
                            GETFIELD,
                            newDynName,
                            fieldName + (elementIndex == 0 ? "FirstFieldChars" : "CommaFieldChars"),
                            "[C");

                    mv.visitVarInsn(ALOAD, 2); // String message = value.getMessage(); 加载 value
                    if (element instanceof Field) {
                        mv.visitFieldInsn(
                                GETFIELD,
                                valtypeName,
                                ((Field) element).getName(),
                                org.redkale.asm.Type.getDescriptor(fieldType));
                    } else {
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL,
                                valtypeName,
                                ((Method) element).getName(),
                                "()" + org.redkale.asm.Type.getDescriptor(fieldType),
                                false);
                    }
                    if (fieldType == short.class) {
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL,
                                writerName,
                                elementIndex + 1 == membersSize ? "writeLastFieldShortValue" : "writeFieldShortValue",
                                "([B[CS)V",
                                false);
                    } else if (fieldType == int.class) {
                        mv.visitMethodInsn(
                                INVOKEVIRTUAL,
                                writerName,
                                elementIndex + 1 == membersSize ? "writeLastFieldIntValue" : "writeFieldIntValue",
                                "([B[CI)V",
                                false);
                    } else if (fieldType == long.class) {
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

                    if (fieldType == long.class || fieldType == double.class) {
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
                for (AccessibleObject element : elements) {
                    elementIndex++;
                    final String fieldName = factory.readConvertFieldName(clazz, element);
                    final Class fieldType = readGetSetFieldType(element);
                    int storeid = ASTORE;
                    int loadid = ALOAD;
                    { // String message = value.getMessage();
                        mv.visitVarInsn(ALOAD, 2); // 加载 value
                        if (element instanceof Field) {
                            mv.visitFieldInsn(
                                    GETFIELD,
                                    valtypeName,
                                    ((Field) element).getName(),
                                    org.redkale.asm.Type.getDescriptor(fieldType));
                        } else {
                            mv.visitMethodInsn(
                                    INVOKEVIRTUAL,
                                    valtypeName,
                                    ((Method) element).getName(),
                                    "()" + org.redkale.asm.Type.getDescriptor(fieldType),
                                    false);
                        }
                        if (fieldType == boolean.class) {
                            storeid = ISTORE;
                            loadid = ILOAD;
                            mv.visitVarInsn(storeid, maxLocals);
                        } else if (fieldType == byte.class) {
                            storeid = ISTORE;
                            loadid = ILOAD;
                            mv.visitVarInsn(storeid, maxLocals);
                        } else if (fieldType == short.class) {
                            storeid = ISTORE;
                            loadid = ILOAD;
                            mv.visitVarInsn(storeid, maxLocals);
                        } else if (fieldType == char.class) {
                            storeid = ISTORE;
                            loadid = ILOAD;
                            mv.visitVarInsn(storeid, maxLocals);
                        } else if (fieldType == int.class) {
                            storeid = ISTORE;
                            loadid = ILOAD;
                            mv.visitVarInsn(storeid, maxLocals);
                        } else if (fieldType == float.class) {
                            storeid = FSTORE;
                            loadid = FLOAD;
                            mv.visitVarInsn(storeid, maxLocals);
                        } else if (fieldType == long.class) {
                            storeid = LSTORE;
                            loadid = LLOAD;
                            mv.visitVarInsn(storeid, maxLocals);
                        } else if (fieldType == double.class) {
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
                    if (!fieldType.isPrimitive() && !nullable) { // if (message != null) { start
                        mv.visitVarInsn(loadid, maxLocals);
                        msgnotemptyif = new Label();
                        mv.visitJumpInsn(IFNULL, msgnotemptyif);
                        if (tiny && fieldType == String.class) {
                            mv.visitVarInsn(loadid, maxLocals);
                            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "isEmpty", "()Z", false);
                            mv.visitJumpInsn(IFNE, msgnotemptyif);
                        }
                    } else if (fieldType == boolean.class && tiny) {
                        mv.visitVarInsn(loadid, maxLocals);
                        msgnotemptyif = new Label();
                        mv.visitJumpInsn(IFEQ, msgnotemptyif);
                    }
                    if (mustHadComma) { // 第一个字段必然会写入
                        if (elementIndex == 0) { // 第一个
                            // out.writeTo(messageFieldBytes);
                            mv.visitVarInsn(ALOAD, 1);
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitFieldInsn(GETFIELD, newDynName, fieldName + "FieldBytes", "[B");
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitFieldInsn(GETFIELD, newDynName, fieldName + "FieldChars", "[C");
                            mv.visitMethodInsn(INVOKEVIRTUAL, writerName, "writeField", "([B[C)V", false);
                        } else {
                            // out.writeTo(messageCommaFieldBytes);
                            mv.visitVarInsn(ALOAD, 1);
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitFieldInsn(GETFIELD, newDynName, fieldName + "CommaFieldBytes", "[B");
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitFieldInsn(GETFIELD, newDynName, fieldName + "CommaFieldChars", "[C");
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
                        mv.visitFieldInsn(GETFIELD, newDynName, fieldName + "CommaFieldBytes", "[B");
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitFieldInsn(GETFIELD, newDynName, fieldName + "CommaFieldChars", "[C");
                        mv.visitMethodInsn(INVOKEVIRTUAL, writerName, "writeField", "([B[C)V", false);

                        Label commaelse = new Label();
                        mv.visitJumpInsn(GOTO, commaelse);
                        mv.visitLabel(commaif);
                        if (fieldType == boolean.class) {
                            mv.visitFrame(Opcodes.F_APPEND, 1, new Object[] {Opcodes.INTEGER}, 0, null);
                        } else if (fieldType == byte.class) {
                            mv.visitFrame(Opcodes.F_APPEND, 1, new Object[] {Opcodes.INTEGER}, 0, null);
                        } else if (fieldType == short.class) {
                            mv.visitFrame(Opcodes.F_APPEND, 1, new Object[] {Opcodes.INTEGER}, 0, null);
                        } else if (fieldType == char.class) {
                            mv.visitFrame(Opcodes.F_APPEND, 1, new Object[] {Opcodes.INTEGER}, 0, null);
                        } else if (fieldType == int.class) {
                            mv.visitFrame(Opcodes.F_APPEND, 1, new Object[] {Opcodes.INTEGER}, 0, null);
                        } else if (fieldType == float.class) {
                            mv.visitFrame(Opcodes.F_APPEND, 1, new Object[] {Opcodes.FLOAT}, 0, null);
                        } else if (fieldType == long.class) {
                            mv.visitFrame(Opcodes.F_APPEND, 1, new Object[] {Opcodes.LONG}, 0, null);
                        } else if (fieldType == double.class) {
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
                        mv.visitFieldInsn(GETFIELD, newDynName, fieldName + "FieldBytes", "[B");
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitFieldInsn(GETFIELD, newDynName, fieldName + "FieldChars", "[C");
                        mv.visitMethodInsn(INVOKEVIRTUAL, writerName, "writeField", "([B[C)V", false);
                        // comma = true;
                        mv.visitInsn(ICONST_1);
                        mv.visitVarInsn(ISTORE, 3);
                        mv.visitLabel(commaelse);
                        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null); // if (comma) } end
                    }
                    // out.writeString(message);
                    if (fieldType == boolean.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitVarInsn(loadid, maxLocals);
                        mv.visitMethodInsn(INVOKEVIRTUAL, writerName, "writeBoolean", "(Z)V", false);
                    } else if (fieldType == byte.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitVarInsn(loadid, maxLocals);
                        mv.visitMethodInsn(INVOKEVIRTUAL, writerName, "writeByte", "(B)V", false);
                    } else if (fieldType == short.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitVarInsn(loadid, maxLocals);
                        mv.visitMethodInsn(INVOKEVIRTUAL, writerName, "writeShort", "(S)V", false);
                    } else if (fieldType == char.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitVarInsn(loadid, maxLocals);
                        mv.visitMethodInsn(INVOKEVIRTUAL, writerName, "writeChar", "(C)V", false);
                    } else if (fieldType == int.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitVarInsn(loadid, maxLocals);
                        mv.visitMethodInsn(INVOKEVIRTUAL, writerName, "writeInt", "(I)V", false);
                    } else if (fieldType == float.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitVarInsn(loadid, maxLocals);
                        mv.visitMethodInsn(INVOKEVIRTUAL, writerName, "writeFloat", "(F)V", false);
                    } else if (fieldType == long.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitVarInsn(loadid, maxLocals);
                        mv.visitMethodInsn(INVOKEVIRTUAL, writerName, "writeLong", "(J)V", false);
                    } else if (fieldType == double.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitVarInsn(loadid, maxLocals);
                        mv.visitMethodInsn(INVOKEVIRTUAL, writerName, "writeDouble", "(D)V", false);
                    } else if (fieldType == String.class) {
                        if (readConvertSmallString(factory, element) == null) {
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
                        mv.visitFieldInsn(GETFIELD, newDynName, fieldName + "Encoder", encodeableDesc);
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitVarInsn(loadid, maxLocals);
                        mv.visitMethodInsn(
                                INVOKEINTERFACE,
                                encodeableName,
                                "convertTo",
                                "(" + writerDesc + "Ljava/lang/Object;)V",
                                true);
                    }
                    if (!fieldType.isPrimitive() && !nullable) { // if (message != null) } end
                        mv.visitLabel(msgnotemptyif);
                        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                    } else if (fieldType == boolean.class && tiny) {
                        mv.visitLabel(msgnotemptyif);
                        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                    }
                    if (fieldType == long.class || fieldType == double.class) {
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
        { // convertTo 虚拟方法
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
                    (JsonDynEncoder) newClazz.getConstructor(JsonFactory.class, Type.class, ObjectEncoder.class)
                            .newInstance(factory, clazz, selfObjEncoder);
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
                if (!factory.isSimpleMemberType(clazz, field.getGenericType(), field.getType())) {
                    return null;
                }
                String name = factory.readConvertFieldName(clazz, field);
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
                if (!factory.isSimpleMemberType(clazz, method.getGenericReturnType(), method.getReturnType())) {
                    return null;
                }
                String name = factory.readConvertFieldName(clazz, method);
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
            factory.sortFieldIndex(clazz, members);
            return generateDyncEncoder(factory, clazz, members);
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    protected static ConvertSmallString readConvertSmallString(JsonFactory factory, AccessibleObject element) {
        if (element instanceof Field) {
            return ((Field) element).getAnnotation(ConvertSmallString.class);
        }
        Method method = (Method) element;
        ConvertSmallString small = method.getAnnotation(ConvertSmallString.class);
        if (small == null) {
            try {
                Field f = method.getDeclaringClass().getDeclaredField(factory.readGetSetFieldName(method));
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

    @Override
    public abstract void convertTo(JsonWriter out, T value);

    @Override
    public boolean specifyable() {
        return false;
    }

    @Override
    public Type getType() {
        return typeClass;
    }
}
