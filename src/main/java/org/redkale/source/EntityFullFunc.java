/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.source;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;
import org.redkale.annotation.ClassDepends;
import org.redkale.asm.Asms;
import org.redkale.asm.ClassWriter;
import org.redkale.asm.Label;
import org.redkale.asm.MethodVisitor;
import org.redkale.asm.Opcodes;
import static org.redkale.asm.Opcodes.*;
import org.redkale.asm.Type;
import org.redkale.util.Attribute;
import org.redkale.util.Creator;
import org.redkale.util.RedkaleClassLoader;
import org.redkale.util.RedkaleException;
import org.redkale.util.Utility;

/**
 * 可以是实体类，也可以是查询结果的JavaBean类
 *
 * @author zhangjx
 * @param <T> T
 * @since 2.8.0
 */
public abstract class EntityFullFunc<T> {

    protected final Class<T> type;

    protected final Creator<T> creator;

    protected final Attribute<T, Serializable>[] attrs;

    @ClassDepends
    protected EntityFullFunc(Class<T> type, Creator<T> creator, Attribute<T, Serializable>[] attrs) {
        this.type = Objects.requireNonNull(type);
        this.creator = Objects.requireNonNull(creator);
        this.attrs = Objects.requireNonNull(attrs);
    }

    public abstract T getObject(DataResultSetRow row);

    public abstract T getObject(Serializable... values);

    @ClassDepends
    protected void setFieldValue(int attrIndex, DataResultSetRow row, T obj) {
        Attribute<T, Serializable> attr = attrs[attrIndex];
        if (attr != null) {
            attr.set(obj, row.getObject(attr, attrIndex + 1, null));
        }
    }

    public Class<T> getType() {
        return type;
    }

    public Creator<T> getCreator() {
        return creator;
    }

    public Attribute<T, Serializable>[] getAttrs() {
        return attrs;
    }

    static <T> EntityFullFunc<T> create(Class<T> entityType, Creator<T> creator, Attribute<T, Serializable>[] attrs) {
        final String supDynName = EntityFullFunc.class.getName().replace('.', '/');
        final String entityName = entityType.getName().replace('.', '/');
        final String entityDesc = Type.getDescriptor(entityType);
        final String creatorDesc = Type.getDescriptor(Creator.class);
        final String creatorName = Creator.class.getName().replace('.', '/');
        final String attrDesc = Type.getDescriptor(Attribute.class);
        final String attrName = Attribute.class.getName().replace('.', '/');
        final String rowDesc = Type.getDescriptor(DataResultSetRow.class);
        final String rowName = DataResultSetRow.class.getName().replace('.', '/');
        final String objectDesc = Type.getDescriptor(Object.class);
        final String serisDesc = Type.getDescriptor(Serializable[].class);

        RedkaleClassLoader classLoader = RedkaleClassLoader.getRedkaleClassLoader();
        final String newDynName = "org/redkaledyn/source/_Dyn" + EntityFullFunc.class.getSimpleName() + "__"
                + entityType.getName().replace('.', '_').replace('$', '_');
        try {
            return (EntityFullFunc) classLoader
                    .loadClass(newDynName.replace('/', '.'))
                    .getConstructor(Class.class, Creator.class, Attribute[].class)
                    .newInstance(entityType, creator, attrs);
        } catch (Throwable ex) {
            // do nothing
        }

        // -------------------------------------------------------------
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        MethodVisitor mv;
        cw.visit(
                V11,
                ACC_PUBLIC + ACC_FINAL + ACC_SUPER,
                newDynName,
                "L" + supDynName + "<" + entityDesc + ">;",
                supDynName,
                null);

        { // 构造方法
            mv = cw.visitMethod(
                    ACC_PUBLIC,
                    "<init>",
                    "(Ljava/lang/Class;" + creatorDesc + "[" + attrDesc + ")V",
                    "(Ljava/lang/Class<" + entityDesc + ">;L" + creatorName + "<" + entityDesc + ">;[L" + attrName + "<"
                            + entityDesc + "Ljava/io/Serializable;>;)V",
                    null);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitVarInsn(ALOAD, 2);
            mv.visitVarInsn(ALOAD, 3);
            mv.visitMethodInsn(
                    INVOKESPECIAL,
                    supDynName,
                    "<init>",
                    "(Ljava/lang/Class;" + creatorDesc + "[" + attrDesc + ")V",
                    false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(4, 4);
            mv.visitEnd();
        }
        { // getObject(DataResultSetRow row)
            mv = cw.visitMethod(ACC_PUBLIC, "getObject", "(" + rowDesc + ")" + entityDesc, null, null);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitMethodInsn(INVOKEINTERFACE, rowName, "wasNull", "()Z", true);
            Label ifLabel = new Label();
            mv.visitJumpInsn(IFEQ, ifLabel);
            mv.visitInsn(ACONST_NULL);
            mv.visitInsn(ARETURN);
            mv.visitLabel(ifLabel);
            mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            // creator.create()
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, supDynName, "creator", creatorDesc);
            mv.visitInsn(ICONST_0);
            mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
            mv.visitMethodInsn(INVOKEINTERFACE, creatorName, "create", "([Ljava/lang/Object;)Ljava/lang/Object;", true);
            mv.visitTypeInsn(CHECKCAST, entityName);
            mv.visitVarInsn(ASTORE, 2);

            for (int i = 0; i < attrs.length; i++) {
                final int colIndex = i + 1;
                final Attribute<T, Serializable> attr = attrs[i];
                java.lang.reflect.Method setter = null;
                java.lang.reflect.Field field = null;
                try {
                    setter = entityType.getMethod("set" + Utility.firstCharUpperCase(attr.field()), attr.type());
                } catch (Exception e) {
                    try {
                        field = entityType.getField(attr.field());
                    } catch (Exception e2) {
                        try {
                            setter = entityType.getMethod(attr.field(), attr.type());
                        } catch (Exception e3) {
                            // do nothing
                        }
                    }
                }
                if (attr.type() == boolean.class) {
                    if (setter != null) {
                        String desc = Type.getMethodDescriptor(setter);
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // row
                        Asms.visitInsn(mv, colIndex);
                        mv.visitInsn(ICONST_0);
                        mv.visitMethodInsn(INVOKEINTERFACE, rowName, "getBoolean", "(IZ)Z", true);
                        mv.visitMethodInsn(INVOKEVIRTUAL, entityName, setter.getName(), desc, false);
                        continue;
                    } else if (field != null) {
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // row
                        Asms.visitInsn(mv, colIndex);
                        mv.visitInsn(ICONST_0);
                        mv.visitMethodInsn(INVOKEINTERFACE, rowName, "getBoolean", "(IZ)Z", true);
                        mv.visitFieldInsn(PUTFIELD, entityName, field.getName(), "Z");
                        continue;
                    }
                } else if (attr.type() == short.class) {
                    if (setter != null) {
                        String desc = Type.getMethodDescriptor(setter);
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // row
                        Asms.visitInsn(mv, colIndex);
                        mv.visitInsn(ICONST_0);
                        mv.visitMethodInsn(INVOKEINTERFACE, rowName, "getShort", "(IS)S", true);
                        mv.visitMethodInsn(INVOKEVIRTUAL, entityName, setter.getName(), desc, false);
                        continue;
                    } else if (field != null) {
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // row
                        Asms.visitInsn(mv, colIndex);
                        mv.visitInsn(ICONST_0);
                        mv.visitMethodInsn(INVOKEINTERFACE, rowName, "getShort", "(IS)S", true);
                        mv.visitFieldInsn(PUTFIELD, entityName, field.getName(), "S");
                        continue;
                    }
                } else if (attr.type() == int.class) {
                    if (setter != null) {
                        String desc = Type.getMethodDescriptor(setter);
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // row
                        Asms.visitInsn(mv, colIndex);
                        mv.visitInsn(ICONST_0);
                        mv.visitMethodInsn(INVOKEINTERFACE, rowName, "getInteger", "(II)I", true);
                        mv.visitMethodInsn(INVOKEVIRTUAL, entityName, setter.getName(), desc, false);
                        continue;
                    } else if (field != null) {
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // row
                        Asms.visitInsn(mv, colIndex);
                        mv.visitInsn(ICONST_0);
                        mv.visitMethodInsn(INVOKEINTERFACE, rowName, "getInteger", "(II)I", true);
                        mv.visitFieldInsn(PUTFIELD, entityName, field.getName(), "I");
                        continue;
                    }
                } else if (attr.type() == float.class) {
                    if (setter != null) {
                        String desc = Type.getMethodDescriptor(setter);
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // row
                        Asms.visitInsn(mv, colIndex);
                        mv.visitInsn(FCONST_0);
                        mv.visitMethodInsn(INVOKEINTERFACE, rowName, "getFloat", "(IF)F", true);
                        mv.visitMethodInsn(INVOKEVIRTUAL, entityName, setter.getName(), desc, false);
                        continue;
                    } else if (field != null) {
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // row
                        Asms.visitInsn(mv, colIndex);
                        mv.visitInsn(FCONST_0);
                        mv.visitMethodInsn(INVOKEINTERFACE, rowName, "getFloat", "(IF)F", true);
                        mv.visitFieldInsn(PUTFIELD, entityName, field.getName(), "F");
                        continue;
                    }
                } else if (attr.type() == long.class) {
                    if (setter != null) {
                        String desc = Type.getMethodDescriptor(setter);
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // row
                        Asms.visitInsn(mv, colIndex);
                        mv.visitInsn(LCONST_0);
                        mv.visitMethodInsn(INVOKEINTERFACE, rowName, "getLong", "(IJ)J", true);
                        mv.visitMethodInsn(INVOKEVIRTUAL, entityName, setter.getName(), desc, false);
                        continue;
                    } else if (field != null) {
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // row
                        Asms.visitInsn(mv, colIndex);
                        mv.visitInsn(LCONST_0);
                        mv.visitMethodInsn(INVOKEINTERFACE, rowName, "getLong", "(IJ)J", true);
                        mv.visitFieldInsn(PUTFIELD, entityName, field.getName(), "J");
                        continue;
                    }
                } else if (attr.type() == double.class) {
                    if (setter != null) {
                        String desc = Type.getMethodDescriptor(setter);
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // row
                        Asms.visitInsn(mv, colIndex);
                        mv.visitInsn(DCONST_0);
                        mv.visitMethodInsn(INVOKEINTERFACE, rowName, "getDouble", "(ID)D", true);
                        mv.visitMethodInsn(INVOKEVIRTUAL, entityName, setter.getName(), desc, false);
                        continue;
                    } else if (field != null) {
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // row
                        Asms.visitInsn(mv, colIndex);
                        mv.visitInsn(DCONST_0);
                        mv.visitMethodInsn(INVOKEINTERFACE, rowName, "getDouble", "(ID)D", true);
                        mv.visitFieldInsn(PUTFIELD, entityName, field.getName(), "D");
                        continue;
                    }
                } else if (attr.type() == Boolean.class) {
                    if (setter != null) {
                        String desc = Type.getMethodDescriptor(setter);
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // row
                        Asms.visitInsn(mv, colIndex);
                        mv.visitMethodInsn(INVOKEINTERFACE, rowName, "getBoolean", "(I)Ljava/lang/Boolean;", true);
                        mv.visitMethodInsn(INVOKEVIRTUAL, entityName, setter.getName(), desc, false);
                        continue;
                    } else if (field != null) {
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // row
                        Asms.visitInsn(mv, colIndex);
                        mv.visitMethodInsn(INVOKEINTERFACE, rowName, "getBoolean", "(I)Ljava/lang/Boolean;", true);
                        mv.visitFieldInsn(PUTFIELD, entityName, field.getName(), "Ljava/lang/Boolean;");
                        continue;
                    }
                } else if (attr.type() == Short.class) {
                    if (setter != null) {
                        String desc = Type.getMethodDescriptor(setter);
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // row
                        Asms.visitInsn(mv, colIndex);
                        mv.visitMethodInsn(INVOKEINTERFACE, rowName, "getShort", "(I)Ljava/lang/Short;", true);
                        mv.visitMethodInsn(INVOKEVIRTUAL, entityName, setter.getName(), desc, false);
                        continue;
                    } else if (field != null) {
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // row
                        Asms.visitInsn(mv, colIndex);
                        mv.visitMethodInsn(INVOKEINTERFACE, rowName, "getShort", "(I)Ljava/lang/Short;", true);
                        mv.visitFieldInsn(PUTFIELD, entityName, field.getName(), "Ljava/lang/Short;");
                        continue;
                    }
                } else if (attr.type() == Integer.class) {
                    if (setter != null) {
                        String desc = Type.getMethodDescriptor(setter);
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // row
                        Asms.visitInsn(mv, colIndex);
                        mv.visitMethodInsn(INVOKEINTERFACE, rowName, "getInteger", "(I)Ljava/lang/Integer;", true);
                        mv.visitMethodInsn(INVOKEVIRTUAL, entityName, setter.getName(), desc, false);
                        continue;
                    } else if (field != null) {
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // row
                        Asms.visitInsn(mv, colIndex);
                        mv.visitMethodInsn(INVOKEINTERFACE, rowName, "getInteger", "(I)Ljava/lang/Integer;", true);
                        mv.visitFieldInsn(PUTFIELD, entityName, field.getName(), "Ljava/lang/Integer;");
                        continue;
                    }
                } else if (attr.type() == Float.class) {
                    if (setter != null) {
                        String desc = Type.getMethodDescriptor(setter);
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // row
                        Asms.visitInsn(mv, colIndex);
                        mv.visitMethodInsn(INVOKEINTERFACE, rowName, "getFloat", "(I)Ljava/lang/Float;", true);
                        mv.visitMethodInsn(INVOKEVIRTUAL, entityName, setter.getName(), desc, false);
                        continue;
                    } else if (field != null) {
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // row
                        Asms.visitInsn(mv, colIndex);
                        mv.visitMethodInsn(INVOKEINTERFACE, rowName, "getFloat", "(I)Ljava/lang/Float;", true);
                        mv.visitFieldInsn(PUTFIELD, entityName, field.getName(), "Ljava/lang/Float;");
                        continue;
                    }
                } else if (attr.type() == Long.class) {
                    if (setter != null) {
                        String desc = Type.getMethodDescriptor(setter);
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // row
                        Asms.visitInsn(mv, colIndex);
                        mv.visitMethodInsn(INVOKEINTERFACE, rowName, "getLong", "(I)Ljava/lang/Long;", true);
                        mv.visitMethodInsn(INVOKEVIRTUAL, entityName, setter.getName(), desc, false);
                        continue;
                    } else if (field != null) {
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // row
                        Asms.visitInsn(mv, colIndex);
                        mv.visitMethodInsn(INVOKEINTERFACE, rowName, "getLong", "(I)Ljava/lang/Long;", true);
                        mv.visitFieldInsn(PUTFIELD, entityName, field.getName(), "Ljava/lang/Long;");
                        continue;
                    }
                } else if (attr.type() == Double.class) {
                    if (setter != null) {
                        String desc = Type.getMethodDescriptor(setter);
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // row
                        Asms.visitInsn(mv, colIndex);
                        mv.visitMethodInsn(INVOKEINTERFACE, rowName, "getDouble", "(I)Ljava/lang/Double;", true);
                        mv.visitMethodInsn(INVOKEVIRTUAL, entityName, setter.getName(), desc, false);
                        continue;
                    } else if (field != null) {
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // row
                        Asms.visitInsn(mv, colIndex);
                        mv.visitMethodInsn(INVOKEINTERFACE, rowName, "getDouble", "(I)Ljava/lang/Double;", true);
                        mv.visitFieldInsn(PUTFIELD, entityName, field.getName(), "Ljava/lang/Double;");
                        continue;
                    }
                } else if (attr.type() == String.class) {
                    if (setter != null) {
                        String desc = Type.getMethodDescriptor(setter);
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // row
                        Asms.visitInsn(mv, colIndex);
                        mv.visitMethodInsn(INVOKEINTERFACE, rowName, "getString", "(I)Ljava/lang/String;", true);
                        mv.visitMethodInsn(INVOKEVIRTUAL, entityName, setter.getName(), desc, false);
                        continue;
                    } else if (field != null) {
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // row
                        Asms.visitInsn(mv, colIndex);
                        mv.visitMethodInsn(INVOKEINTERFACE, rowName, "getString", "(I)Ljava/lang/String;", true);
                        mv.visitFieldInsn(PUTFIELD, entityName, field.getName(), "Ljava/lang/String;");
                        continue;
                    }
                } else if (attr.type() == byte[].class) {
                    if (setter != null) {
                        String desc = Type.getMethodDescriptor(setter);
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // row
                        Asms.visitInsn(mv, colIndex);
                        mv.visitMethodInsn(INVOKEINTERFACE, rowName, "getBytes", "(I)[B", true);
                        mv.visitMethodInsn(INVOKEVIRTUAL, entityName, setter.getName(), desc, false);
                        continue;
                    } else if (field != null) {
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // row
                        Asms.visitInsn(mv, colIndex);
                        mv.visitMethodInsn(INVOKEINTERFACE, rowName, "getBytes", "(I)[B", true);
                        mv.visitFieldInsn(PUTFIELD, entityName, field.getName(), "[B");
                        continue;
                    }
                } else if (attr.type() == BigDecimal.class) {
                    if (setter != null) {
                        String desc = Type.getMethodDescriptor(setter);
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // row
                        Asms.visitInsn(mv, colIndex);
                        mv.visitMethodInsn(
                                INVOKEINTERFACE, rowName, "getBigDecimal", "(I)Ljava/math/BigDecimal;", true);
                        mv.visitMethodInsn(INVOKEVIRTUAL, entityName, setter.getName(), desc, false);
                        continue;
                    } else if (field != null) {
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // row
                        Asms.visitInsn(mv, colIndex);
                        mv.visitMethodInsn(
                                INVOKEINTERFACE, rowName, "getBigDecimal", "(I)Ljava/math/BigDecimal;", true);
                        mv.visitFieldInsn(PUTFIELD, entityName, field.getName(), "Ljava/math/BigDecimal;");
                        continue;
                    }
                }
                mv.visitVarInsn(ALOAD, 0);
                Asms.visitInsn(mv, colIndex - 1);
                mv.visitVarInsn(ALOAD, 1); // row
                mv.visitVarInsn(ALOAD, 2); // obj
                mv.visitMethodInsn(
                        INVOKEVIRTUAL, supDynName, "setFieldValue", "(I" + rowDesc + objectDesc + ")V", false);
            }

            mv.visitVarInsn(ALOAD, 2); // obj
            mv.visitInsn(ARETURN);
            mv.visitMaxs(5, 3);
            mv.visitEnd();
        }
        { // 虚拟 getObject(DataResultSetRow row)
            mv = cw.visitMethod(
                    ACC_PUBLIC | ACC_BRIDGE | ACC_SYNTHETIC, "getObject", "(" + rowDesc + ")" + objectDesc, null, null);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, "getObject", "(" + rowDesc + ")" + entityDesc, false);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(2, 2);
            mv.visitEnd();
        }

        { // getObject(Serializable... values)
            mv = cw.visitMethod(ACC_PUBLIC | ACC_VARARGS, "getObject", "(" + serisDesc + ")" + entityDesc, null, null);
            // creator.create()
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, supDynName, "creator", creatorDesc);
            mv.visitInsn(ICONST_0);
            mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
            mv.visitMethodInsn(INVOKEINTERFACE, creatorName, "create", "([Ljava/lang/Object;)" + objectDesc, true);
            mv.visitTypeInsn(CHECKCAST, entityName);
            mv.visitVarInsn(ASTORE, 2);

            for (int i = 0; i < attrs.length; i++) {
                final int attrIndex = i;
                final Attribute<T, Serializable> attr = attrs[i];
                java.lang.reflect.Method setter = null;
                java.lang.reflect.Field field = null;
                try {
                    setter = entityType.getMethod("set" + Utility.firstCharUpperCase(attr.field()), attr.type());
                } catch (Exception e) {
                    try {
                        field = entityType.getField(attr.field());
                    } catch (Exception e2) {
                        try {
                            setter = entityType.getMethod(attr.field(), attr.type());
                        } catch (Exception e3) {
                            // do nothing
                        }
                    }
                }
                if (setter == null && field == null) {
                    throw new SourceException("Not found '" + attr.field() + "' setter method or public field ");
                }
                if (attr.type() == boolean.class) {
                    if (setter != null) {
                        String desc = Type.getMethodDescriptor(setter);
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // values
                        Asms.visitInsn(mv, attrIndex);
                        mv.visitInsn(AALOAD);
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
                        mv.visitMethodInsn(INVOKEVIRTUAL, entityName, setter.getName(), desc, false);
                    } else if (field != null) {
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // values
                        Asms.visitInsn(mv, attrIndex);
                        mv.visitInsn(AALOAD);
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
                        mv.visitFieldInsn(PUTFIELD, entityName, field.getName(), "Z");
                    }
                } else if (attr.type() == short.class) {
                    if (setter != null) {
                        String desc = Type.getMethodDescriptor(setter);
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // values
                        Asms.visitInsn(mv, attrIndex);
                        mv.visitInsn(AALOAD);
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Short");
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S", false);
                        mv.visitMethodInsn(INVOKEVIRTUAL, entityName, setter.getName(), desc, false);
                    } else if (field != null) {
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // values
                        Asms.visitInsn(mv, attrIndex);
                        mv.visitInsn(AALOAD);
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Short");
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S", false);
                        mv.visitFieldInsn(PUTFIELD, entityName, field.getName(), "S");
                    }
                } else if (attr.type() == int.class) {
                    if (setter != null) {
                        String desc = Type.getMethodDescriptor(setter);
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // values
                        Asms.visitInsn(mv, attrIndex);
                        mv.visitInsn(AALOAD);
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                        mv.visitMethodInsn(INVOKEVIRTUAL, entityName, setter.getName(), desc, false);
                    } else if (field != null) {
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // values
                        Asms.visitInsn(mv, attrIndex);
                        mv.visitInsn(AALOAD);
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                        mv.visitFieldInsn(PUTFIELD, entityName, field.getName(), "I");
                    }
                } else if (attr.type() == float.class) {
                    if (setter != null) {
                        String desc = Type.getMethodDescriptor(setter);
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // values
                        Asms.visitInsn(mv, attrIndex);
                        mv.visitInsn(AALOAD);
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Float");
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false);
                        mv.visitMethodInsn(INVOKEVIRTUAL, entityName, setter.getName(), desc, false);
                    } else if (field != null) {
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // values
                        Asms.visitInsn(mv, attrIndex);
                        mv.visitInsn(AALOAD);
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Float");
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false);
                        mv.visitFieldInsn(PUTFIELD, entityName, field.getName(), "F");
                    }
                } else if (attr.type() == long.class) {
                    if (setter != null) {
                        String desc = Type.getMethodDescriptor(setter);
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // values
                        Asms.visitInsn(mv, attrIndex);
                        mv.visitInsn(AALOAD);
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Long");
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
                        mv.visitMethodInsn(INVOKEVIRTUAL, entityName, setter.getName(), desc, false);
                    } else if (field != null) {
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // values
                        Asms.visitInsn(mv, attrIndex);
                        mv.visitInsn(AALOAD);
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Long");
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
                        mv.visitFieldInsn(PUTFIELD, entityName, field.getName(), "J");
                    }
                } else if (attr.type() == double.class) {
                    if (setter != null) {
                        String desc = Type.getMethodDescriptor(setter);
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // values
                        Asms.visitInsn(mv, attrIndex);
                        mv.visitInsn(AALOAD);
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Double");
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
                        mv.visitMethodInsn(INVOKEVIRTUAL, entityName, setter.getName(), desc, false);
                    } else if (field != null) {
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // values
                        Asms.visitInsn(mv, attrIndex);
                        mv.visitInsn(AALOAD);
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Double");
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
                        mv.visitFieldInsn(PUTFIELD, entityName, field.getName(), "D");
                    }
                } else if (attr.type() == Boolean.class) {
                    if (setter != null) {
                        String desc = Type.getMethodDescriptor(setter);
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // values
                        Asms.visitInsn(mv, attrIndex);
                        mv.visitInsn(AALOAD);
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
                        mv.visitMethodInsn(INVOKEVIRTUAL, entityName, setter.getName(), desc, false);
                    } else if (field != null) {
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // values
                        Asms.visitInsn(mv, attrIndex);
                        mv.visitInsn(AALOAD);
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
                        mv.visitFieldInsn(PUTFIELD, entityName, field.getName(), "Ljava/lang/Boolean;");
                    }
                } else if (attr.type() == Short.class) {
                    if (setter != null) {
                        String desc = Type.getMethodDescriptor(setter);
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // values
                        Asms.visitInsn(mv, attrIndex);
                        mv.visitInsn(AALOAD);
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Short");
                        mv.visitMethodInsn(INVOKEVIRTUAL, entityName, setter.getName(), desc, false);
                    } else if (field != null) {
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // values
                        Asms.visitInsn(mv, attrIndex);
                        mv.visitInsn(AALOAD);
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Short");
                        mv.visitFieldInsn(PUTFIELD, entityName, field.getName(), "Ljava/lang/Short;");
                    }
                } else if (attr.type() == Integer.class) {
                    if (setter != null) {
                        String desc = Type.getMethodDescriptor(setter);
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // values
                        Asms.visitInsn(mv, attrIndex);
                        mv.visitInsn(AALOAD);
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                        mv.visitMethodInsn(INVOKEVIRTUAL, entityName, setter.getName(), desc, false);
                    } else if (field != null) {
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // values
                        Asms.visitInsn(mv, attrIndex);
                        mv.visitInsn(AALOAD);
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                        mv.visitFieldInsn(PUTFIELD, entityName, field.getName(), "Ljava/lang/Integer;");
                    }
                } else if (attr.type() == Float.class) {
                    if (setter != null) {
                        String desc = Type.getMethodDescriptor(setter);
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // values
                        Asms.visitInsn(mv, attrIndex);
                        mv.visitInsn(AALOAD);
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Float");
                        mv.visitMethodInsn(INVOKEVIRTUAL, entityName, setter.getName(), desc, false);
                    } else if (field != null) {
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // values
                        Asms.visitInsn(mv, attrIndex);
                        mv.visitInsn(AALOAD);
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Float");
                        mv.visitFieldInsn(PUTFIELD, entityName, field.getName(), "Ljava/lang/Float;");
                    }
                } else if (attr.type() == Long.class) {
                    if (setter != null) {
                        String desc = Type.getMethodDescriptor(setter);
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // values
                        Asms.visitInsn(mv, attrIndex);
                        mv.visitInsn(AALOAD);
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Long");
                        mv.visitMethodInsn(INVOKEVIRTUAL, entityName, setter.getName(), desc, false);
                    } else if (field != null) {
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // values
                        Asms.visitInsn(mv, attrIndex);
                        mv.visitInsn(AALOAD);
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Long");
                        mv.visitFieldInsn(PUTFIELD, entityName, field.getName(), "Ljava/lang/Long;");
                    }
                } else if (attr.type() == Double.class) {
                    if (setter != null) {
                        String desc = Type.getMethodDescriptor(setter);
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // values
                        Asms.visitInsn(mv, attrIndex);
                        mv.visitInsn(AALOAD);
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Double");
                        mv.visitMethodInsn(INVOKEVIRTUAL, entityName, setter.getName(), desc, false);
                    } else if (field != null) {
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // values
                        Asms.visitInsn(mv, attrIndex);
                        mv.visitInsn(AALOAD);
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Double");
                        mv.visitFieldInsn(PUTFIELD, entityName, field.getName(), "Ljava/lang/Double;");
                    }
                } else if (attr.type() == String.class) {
                    if (setter != null) {
                        String desc = Type.getMethodDescriptor(setter);
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // values
                        Asms.visitInsn(mv, attrIndex);
                        mv.visitInsn(AALOAD);
                        mv.visitTypeInsn(CHECKCAST, "java/lang/String");
                        mv.visitMethodInsn(INVOKEVIRTUAL, entityName, setter.getName(), desc, false);
                    } else if (field != null) {
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // values
                        Asms.visitInsn(mv, attrIndex);
                        mv.visitInsn(AALOAD);
                        mv.visitTypeInsn(CHECKCAST, "java/lang/String");
                        mv.visitFieldInsn(PUTFIELD, entityName, field.getName(), "Ljava/lang/String;");
                    }
                } else if (attr.type() == byte[].class) {
                    if (setter != null) {
                        String desc = Type.getMethodDescriptor(setter);
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // values
                        Asms.visitInsn(mv, attrIndex);
                        mv.visitInsn(AALOAD);
                        mv.visitTypeInsn(CHECKCAST, "[B");
                        mv.visitMethodInsn(INVOKEVIRTUAL, entityName, setter.getName(), desc, false);
                    } else if (field != null) {
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // values
                        Asms.visitInsn(mv, attrIndex);
                        mv.visitInsn(AALOAD);
                        mv.visitTypeInsn(CHECKCAST, "[B");
                        mv.visitFieldInsn(PUTFIELD, entityName, field.getName(), "[B");
                    }
                } else if (attr.type() == BigDecimal.class) {
                    if (setter != null) {
                        String desc = Type.getMethodDescriptor(setter);
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // values
                        Asms.visitInsn(mv, attrIndex);
                        mv.visitInsn(AALOAD);
                        mv.visitTypeInsn(CHECKCAST, "java/math/BigDecimal");
                        mv.visitMethodInsn(INVOKEVIRTUAL, entityName, setter.getName(), desc, false);
                    } else if (field != null) {
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // values
                        Asms.visitInsn(mv, attrIndex);
                        mv.visitInsn(AALOAD);
                        mv.visitTypeInsn(CHECKCAST, "java/math/BigDecimal");
                        mv.visitFieldInsn(PUTFIELD, entityName, field.getName(), "Ljava/math/BigDecimal;");
                    }
                } else {
                    if (setter != null) {
                        String desc = Type.getMethodDescriptor(setter);
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // values
                        Asms.visitInsn(mv, attrIndex);
                        mv.visitInsn(AALOAD);
                        Asms.visitCheckCast(mv, setter.getParameterTypes()[0]);
                        mv.visitMethodInsn(INVOKEVIRTUAL, entityName, setter.getName(), desc, false);
                    } else if (field != null) {
                        String desc = Type.getDescriptor(field.getType());
                        mv.visitVarInsn(ALOAD, 2); // obj
                        mv.visitVarInsn(ALOAD, 1); // values
                        Asms.visitInsn(mv, attrIndex);
                        mv.visitInsn(AALOAD);
                        Asms.visitCheckCast(mv, field.getType());
                        mv.visitFieldInsn(PUTFIELD, entityName, field.getName(), desc);
                    }
                }
            }
            mv.visitVarInsn(ALOAD, 2); // obj
            mv.visitInsn(ARETURN);
            mv.visitMaxs(3, 3);
            mv.visitEnd();
        }
        { // 虚拟 getObject(Serializable... values)
            int access = ACC_PUBLIC | ACC_BRIDGE | ACC_VARARGS | ACC_SYNTHETIC;
            mv = cw.visitMethod(access, "getObject", "(" + serisDesc + ")" + objectDesc, null, null);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, "getObject", "(" + serisDesc + ")" + entityDesc, false);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(2, 2);
            mv.visitEnd();
        }
        cw.visitEnd();

        byte[] bytes = cw.toByteArray();
        Class<EntityFullFunc> newClazz = classLoader.loadClass(newDynName.replace('/', '.'), bytes);
        RedkaleClassLoader.putReflectionDeclaredConstructors(newClazz, newDynName.replace('/', '.'));
        try {
            return newClazz.getConstructor(Class.class, Creator.class, Attribute[].class)
                    .newInstance(entityType, creator, attrs);
        } catch (Exception ex) {
            throw new RedkaleException(ex);
        }
    }
}
