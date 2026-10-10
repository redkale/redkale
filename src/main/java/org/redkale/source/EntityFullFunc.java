/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.source;

import static java.lang.classfile.ClassFile.*;

import java.io.Serializable;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassSignature;
import java.lang.classfile.Label;
import java.lang.classfile.MethodSignature;
import java.lang.classfile.attribute.*;
import java.lang.constant.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.Objects;
import org.redkale.annotation.ClassDepends;
import org.redkale.bytecode.ByteCodes;
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
        final String entityDesc = ByteCodes.descriptor(entityType);
        final String creatorDesc = ByteCodes.descriptor(Creator.class);
        final String creatorName = Creator.class.getName().replace('.', '/');
        final String attrDesc = ByteCodes.descriptor(Attribute.class);
        final String attrName = Attribute.class.getName().replace('.', '/');
        final String rowDesc = ByteCodes.descriptor(DataResultSetRow.class);
        final String rowName = DataResultSetRow.class.getName().replace('.', '/');
        final String objectDesc = ByteCodes.descriptor(Object.class);
        final String serisDesc = ByteCodes.descriptor(Serializable[].class);

        RedkaleClassLoader classLoader = RedkaleClassLoader.currentClassLoader();
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

        byte[] classBytes = ClassFile.of().build(ByteCodes.classDesc(newDynName), cw -> {
            cw.withVersion(JAVA_11_VERSION, 0)
                    .withFlags(ACC_PUBLIC + ACC_FINAL + ACC_SUPER)
                    .withSuperclass(ByteCodes.classDesc(supDynName));

            cw.with(SignatureAttribute.of(ClassSignature.parseFrom("L" + supDynName + "<" + entityDesc + ">;")));

            { // 构造方法
                cw.withMethod(
                        "<init>",
                        MethodTypeDesc.ofDescriptor("(Ljava/lang/Class;" + creatorDesc + "[" + attrDesc + ")V"),
                        ACC_PUBLIC,
                        mb -> {
                            mb.with(SignatureAttribute.of(MethodSignature.parseFrom(
                                    "(Ljava/lang/Class<" + entityDesc + ">;L" + creatorName + "<" + entityDesc + ">;[L"
                                            + attrName + "<" + entityDesc + "Ljava/io/Serializable;>;)V")));

                            mb.withCode(mv -> {
                                Label label0 = mv.newLabel();
                                mv.labelBinding(label0);
                                mv.aload(0);
                                mv.aload(1);
                                mv.aload(2);
                                mv.aload(3);
                                mv.invokespecial(
                                        ByteCodes.classDesc(supDynName),
                                        "<init>",
                                        MethodTypeDesc.ofDescriptor(
                                                "(Ljava/lang/Class;" + creatorDesc + "[" + attrDesc + ")V"),
                                        false);
                                mv.return_();
                                Label label2 = mv.newLabel();
                                mv.labelBinding(label2);
                                mv.localVariable(
                                        0, "this", ClassDesc.ofDescriptor("L" + newDynName + ";"), label0, label2);
                                mv.localVariable(
                                        1, "type", ClassDesc.ofDescriptor("Ljava/lang/Class;"), label0, label2);
                                mv.localVariable(2, "creator", ClassDesc.ofDescriptor(creatorDesc), label0, label2);
                                mv.localVariable(3, "attrs", ClassDesc.ofDescriptor("[" + attrDesc), label0, label2);
                            });
                        });
            }
            { // getObject(DataResultSetRow row)
                cw.withMethodBody(
                        "getObject", MethodTypeDesc.ofDescriptor("(" + rowDesc + ")" + entityDesc), ACC_PUBLIC, mv -> {
                            Label label0 = mv.newLabel();
                            mv.labelBinding(label0);
                            mv.aload(1);
                            mv.invokeinterface(
                                    ByteCodes.classDesc(rowName), "wasNull", MethodTypeDesc.ofDescriptor("()Z"));
                            Label ifLabel = mv.newLabel();
                            mv.ifeq(ifLabel);
                            mv.aconst_null();
                            mv.areturn();
                            mv.labelBinding(ifLabel);

                            // creator.create()
                            mv.aload(0);
                            mv.getfield(
                                    ByteCodes.classDesc(supDynName), "creator", ClassDesc.ofDescriptor(creatorDesc));
                            mv.iconst_0();
                            mv.anewarray(ByteCodes.classDesc("java/lang/Object"));
                            mv.invokeinterface(
                                    ByteCodes.classDesc(creatorName),
                                    "create",
                                    MethodTypeDesc.ofDescriptor("([Ljava/lang/Object;)Ljava/lang/Object;"));
                            mv.checkcast(ByteCodes.classDesc(entityName));
                            mv.astore(2);
                            Label label1 = mv.newLabel();
                            mv.labelBinding(label1);

                            for (int i = 0; i < attrs.length; i++) {
                                final int colIndex = i + 1;
                                final Attribute<T, Serializable> attr = attrs[i];
                                java.lang.reflect.Method setter = null;
                                java.lang.reflect.Field field = null;
                                try {
                                    setter = entityType.getMethod(
                                            "set" + Utility.firstCharUpperCase(attr.field()), attr.type());
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
                                        String desc = ByteCodes.methodDescriptor(setter);
                                        mv.aload(2); // obj
                                        mv.aload(1); // row
                                        mv.loadConstant(colIndex);
                                        mv.iconst_0();
                                        mv.invokeinterface(
                                                ByteCodes.classDesc(rowName),
                                                "getBoolean",
                                                MethodTypeDesc.ofDescriptor("(IZ)Z"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(entityName),
                                                setter.getName(),
                                                MethodTypeDesc.ofDescriptor(desc));
                                        continue;
                                    } else if (field != null) {
                                        mv.aload(2); // obj
                                        mv.aload(1); // row
                                        mv.loadConstant(colIndex);
                                        mv.iconst_0();
                                        mv.invokeinterface(
                                                ByteCodes.classDesc(rowName),
                                                "getBoolean",
                                                MethodTypeDesc.ofDescriptor("(IZ)Z"));
                                        mv.putfield(
                                                ByteCodes.classDesc(entityName),
                                                field.getName(),
                                                ClassDesc.ofDescriptor("Z"));
                                        continue;
                                    }
                                } else if (attr.type() == short.class) {
                                    if (setter != null) {
                                        String desc = ByteCodes.methodDescriptor(setter);
                                        mv.aload(2); // obj
                                        mv.aload(1); // row
                                        mv.loadConstant(colIndex);
                                        mv.iconst_0();
                                        mv.invokeinterface(
                                                ByteCodes.classDesc(rowName),
                                                "getShort",
                                                MethodTypeDesc.ofDescriptor("(IS)S"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(entityName),
                                                setter.getName(),
                                                MethodTypeDesc.ofDescriptor(desc));
                                        continue;
                                    } else if (field != null) {
                                        mv.aload(2); // obj
                                        mv.aload(1); // row
                                        mv.loadConstant(colIndex);
                                        mv.iconst_0();
                                        mv.invokeinterface(
                                                ByteCodes.classDesc(rowName),
                                                "getShort",
                                                MethodTypeDesc.ofDescriptor("(IS)S"));
                                        mv.putfield(
                                                ByteCodes.classDesc(entityName),
                                                field.getName(),
                                                ClassDesc.ofDescriptor("S"));
                                        continue;
                                    }
                                } else if (attr.type() == int.class) {
                                    if (setter != null) {
                                        String desc = ByteCodes.methodDescriptor(setter);
                                        mv.aload(2); // obj
                                        mv.aload(1); // row
                                        mv.loadConstant(colIndex);
                                        mv.iconst_0();
                                        mv.invokeinterface(
                                                ByteCodes.classDesc(rowName),
                                                "getInteger",
                                                MethodTypeDesc.ofDescriptor("(II)I"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(entityName),
                                                setter.getName(),
                                                MethodTypeDesc.ofDescriptor(desc));
                                        continue;
                                    } else if (field != null) {
                                        mv.aload(2); // obj
                                        mv.aload(1); // row
                                        mv.loadConstant(colIndex);
                                        mv.iconst_0();
                                        mv.invokeinterface(
                                                ByteCodes.classDesc(rowName),
                                                "getInteger",
                                                MethodTypeDesc.ofDescriptor("(II)I"));
                                        mv.putfield(
                                                ByteCodes.classDesc(entityName),
                                                field.getName(),
                                                ClassDesc.ofDescriptor("I"));
                                        continue;
                                    }
                                } else if (attr.type() == float.class) {
                                    if (setter != null) {
                                        String desc = ByteCodes.methodDescriptor(setter);
                                        mv.aload(2); // obj
                                        mv.aload(1); // row
                                        mv.loadConstant(colIndex);
                                        mv.fconst_0();
                                        mv.invokeinterface(
                                                ByteCodes.classDesc(rowName),
                                                "getFloat",
                                                MethodTypeDesc.ofDescriptor("(IF)F"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(entityName),
                                                setter.getName(),
                                                MethodTypeDesc.ofDescriptor(desc));
                                        continue;
                                    } else if (field != null) {
                                        mv.aload(2); // obj
                                        mv.aload(1); // row
                                        mv.loadConstant(colIndex);
                                        mv.fconst_0();
                                        mv.invokeinterface(
                                                ByteCodes.classDesc(rowName),
                                                "getFloat",
                                                MethodTypeDesc.ofDescriptor("(IF)F"));
                                        mv.putfield(
                                                ByteCodes.classDesc(entityName),
                                                field.getName(),
                                                ClassDesc.ofDescriptor("F"));
                                        continue;
                                    }
                                } else if (attr.type() == long.class) {
                                    if (setter != null) {
                                        String desc = ByteCodes.methodDescriptor(setter);
                                        mv.aload(2); // obj
                                        mv.aload(1); // row
                                        mv.loadConstant(colIndex);
                                        mv.lconst_0();
                                        mv.invokeinterface(
                                                ByteCodes.classDesc(rowName),
                                                "getLong",
                                                MethodTypeDesc.ofDescriptor("(IJ)J"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(entityName),
                                                setter.getName(),
                                                MethodTypeDesc.ofDescriptor(desc));
                                        continue;
                                    } else if (field != null) {
                                        mv.aload(2); // obj
                                        mv.aload(1); // row
                                        mv.loadConstant(colIndex);
                                        mv.lconst_0();
                                        mv.invokeinterface(
                                                ByteCodes.classDesc(rowName),
                                                "getLong",
                                                MethodTypeDesc.ofDescriptor("(IJ)J"));
                                        mv.putfield(
                                                ByteCodes.classDesc(entityName),
                                                field.getName(),
                                                ClassDesc.ofDescriptor("J"));
                                        continue;
                                    }
                                } else if (attr.type() == double.class) {
                                    if (setter != null) {
                                        String desc = ByteCodes.methodDescriptor(setter);
                                        mv.aload(2); // obj
                                        mv.aload(1); // row
                                        mv.loadConstant(colIndex);
                                        mv.dconst_0();
                                        mv.invokeinterface(
                                                ByteCodes.classDesc(rowName),
                                                "getDouble",
                                                MethodTypeDesc.ofDescriptor("(ID)D"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(entityName),
                                                setter.getName(),
                                                MethodTypeDesc.ofDescriptor(desc));
                                        continue;
                                    } else if (field != null) {
                                        mv.aload(2); // obj
                                        mv.aload(1); // row
                                        mv.loadConstant(colIndex);
                                        mv.dconst_0();
                                        mv.invokeinterface(
                                                ByteCodes.classDesc(rowName),
                                                "getDouble",
                                                MethodTypeDesc.ofDescriptor("(ID)D"));
                                        mv.putfield(
                                                ByteCodes.classDesc(entityName),
                                                field.getName(),
                                                ClassDesc.ofDescriptor("D"));
                                        continue;
                                    }
                                } else if (attr.type() == Boolean.class) {
                                    if (setter != null) {
                                        String desc = ByteCodes.methodDescriptor(setter);
                                        mv.aload(2); // obj
                                        mv.aload(1); // row
                                        mv.loadConstant(colIndex);
                                        mv.invokeinterface(
                                                ByteCodes.classDesc(rowName),
                                                "getBoolean",
                                                MethodTypeDesc.ofDescriptor("(I)Ljava/lang/Boolean;"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(entityName),
                                                setter.getName(),
                                                MethodTypeDesc.ofDescriptor(desc));
                                        continue;
                                    } else if (field != null) {
                                        mv.aload(2); // obj
                                        mv.aload(1); // row
                                        mv.loadConstant(colIndex);
                                        mv.invokeinterface(
                                                ByteCodes.classDesc(rowName),
                                                "getBoolean",
                                                MethodTypeDesc.ofDescriptor("(I)Ljava/lang/Boolean;"));
                                        mv.putfield(
                                                ByteCodes.classDesc(entityName),
                                                field.getName(),
                                                ClassDesc.ofDescriptor("Ljava/lang/Boolean;"));
                                        continue;
                                    }
                                } else if (attr.type() == Short.class) {
                                    if (setter != null) {
                                        String desc = ByteCodes.methodDescriptor(setter);
                                        mv.aload(2); // obj
                                        mv.aload(1); // row
                                        mv.loadConstant(colIndex);
                                        mv.invokeinterface(
                                                ByteCodes.classDesc(rowName),
                                                "getShort",
                                                MethodTypeDesc.ofDescriptor("(I)Ljava/lang/Short;"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(entityName),
                                                setter.getName(),
                                                MethodTypeDesc.ofDescriptor(desc));
                                        continue;
                                    } else if (field != null) {
                                        mv.aload(2); // obj
                                        mv.aload(1); // row
                                        mv.loadConstant(colIndex);
                                        mv.invokeinterface(
                                                ByteCodes.classDesc(rowName),
                                                "getShort",
                                                MethodTypeDesc.ofDescriptor("(I)Ljava/lang/Short;"));
                                        mv.putfield(
                                                ByteCodes.classDesc(entityName),
                                                field.getName(),
                                                ClassDesc.ofDescriptor("Ljava/lang/Short;"));
                                        continue;
                                    }
                                } else if (attr.type() == Integer.class) {
                                    if (setter != null) {
                                        String desc = ByteCodes.methodDescriptor(setter);
                                        mv.aload(2); // obj
                                        mv.aload(1); // row
                                        mv.loadConstant(colIndex);
                                        mv.invokeinterface(
                                                ByteCodes.classDesc(rowName),
                                                "getInteger",
                                                MethodTypeDesc.ofDescriptor("(I)Ljava/lang/Integer;"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(entityName),
                                                setter.getName(),
                                                MethodTypeDesc.ofDescriptor(desc));
                                        continue;
                                    } else if (field != null) {
                                        mv.aload(2); // obj
                                        mv.aload(1); // row
                                        mv.loadConstant(colIndex);
                                        mv.invokeinterface(
                                                ByteCodes.classDesc(rowName),
                                                "getInteger",
                                                MethodTypeDesc.ofDescriptor("(I)Ljava/lang/Integer;"));
                                        mv.putfield(
                                                ByteCodes.classDesc(entityName),
                                                field.getName(),
                                                ClassDesc.ofDescriptor("Ljava/lang/Integer;"));
                                        continue;
                                    }
                                } else if (attr.type() == Float.class) {
                                    if (setter != null) {
                                        String desc = ByteCodes.methodDescriptor(setter);
                                        mv.aload(2); // obj
                                        mv.aload(1); // row
                                        mv.loadConstant(colIndex);
                                        mv.invokeinterface(
                                                ByteCodes.classDesc(rowName),
                                                "getFloat",
                                                MethodTypeDesc.ofDescriptor("(I)Ljava/lang/Float;"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(entityName),
                                                setter.getName(),
                                                MethodTypeDesc.ofDescriptor(desc));
                                        continue;
                                    } else if (field != null) {
                                        mv.aload(2); // obj
                                        mv.aload(1); // row
                                        mv.loadConstant(colIndex);
                                        mv.invokeinterface(
                                                ByteCodes.classDesc(rowName),
                                                "getFloat",
                                                MethodTypeDesc.ofDescriptor("(I)Ljava/lang/Float;"));
                                        mv.putfield(
                                                ByteCodes.classDesc(entityName),
                                                field.getName(),
                                                ClassDesc.ofDescriptor("Ljava/lang/Float;"));
                                        continue;
                                    }
                                } else if (attr.type() == Long.class) {
                                    if (setter != null) {
                                        String desc = ByteCodes.methodDescriptor(setter);
                                        mv.aload(2); // obj
                                        mv.aload(1); // row
                                        mv.loadConstant(colIndex);
                                        mv.invokeinterface(
                                                ByteCodes.classDesc(rowName),
                                                "getLong",
                                                MethodTypeDesc.ofDescriptor("(I)Ljava/lang/Long;"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(entityName),
                                                setter.getName(),
                                                MethodTypeDesc.ofDescriptor(desc));
                                        continue;
                                    } else if (field != null) {
                                        mv.aload(2); // obj
                                        mv.aload(1); // row
                                        mv.loadConstant(colIndex);
                                        mv.invokeinterface(
                                                ByteCodes.classDesc(rowName),
                                                "getLong",
                                                MethodTypeDesc.ofDescriptor("(I)Ljava/lang/Long;"));
                                        mv.putfield(
                                                ByteCodes.classDesc(entityName),
                                                field.getName(),
                                                ClassDesc.ofDescriptor("Ljava/lang/Long;"));
                                        continue;
                                    }
                                } else if (attr.type() == Double.class) {
                                    if (setter != null) {
                                        String desc = ByteCodes.methodDescriptor(setter);
                                        mv.aload(2); // obj
                                        mv.aload(1); // row
                                        mv.loadConstant(colIndex);
                                        mv.invokeinterface(
                                                ByteCodes.classDesc(rowName),
                                                "getDouble",
                                                MethodTypeDesc.ofDescriptor("(I)Ljava/lang/Double;"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(entityName),
                                                setter.getName(),
                                                MethodTypeDesc.ofDescriptor(desc));
                                        continue;
                                    } else if (field != null) {
                                        mv.aload(2); // obj
                                        mv.aload(1); // row
                                        mv.loadConstant(colIndex);
                                        mv.invokeinterface(
                                                ByteCodes.classDesc(rowName),
                                                "getDouble",
                                                MethodTypeDesc.ofDescriptor("(I)Ljava/lang/Double;"));
                                        mv.putfield(
                                                ByteCodes.classDesc(entityName),
                                                field.getName(),
                                                ClassDesc.ofDescriptor("Ljava/lang/Double;"));
                                        continue;
                                    }
                                } else if (attr.type() == String.class) {
                                    if (setter != null) {
                                        String desc = ByteCodes.methodDescriptor(setter);
                                        mv.aload(2); // obj
                                        mv.aload(1); // row
                                        mv.loadConstant(colIndex);
                                        mv.invokeinterface(
                                                ByteCodes.classDesc(rowName),
                                                "getString",
                                                MethodTypeDesc.ofDescriptor("(I)Ljava/lang/String;"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(entityName),
                                                setter.getName(),
                                                MethodTypeDesc.ofDescriptor(desc));
                                        continue;
                                    } else if (field != null) {
                                        mv.aload(2); // obj
                                        mv.aload(1); // row
                                        mv.loadConstant(colIndex);
                                        mv.invokeinterface(
                                                ByteCodes.classDesc(rowName),
                                                "getString",
                                                MethodTypeDesc.ofDescriptor("(I)Ljava/lang/String;"));
                                        mv.putfield(
                                                ByteCodes.classDesc(entityName),
                                                field.getName(),
                                                ClassDesc.ofDescriptor("Ljava/lang/String;"));
                                        continue;
                                    }
                                } else if (attr.type() == byte[].class) {
                                    if (setter != null) {
                                        String desc = ByteCodes.methodDescriptor(setter);
                                        mv.aload(2); // obj
                                        mv.aload(1); // row
                                        mv.loadConstant(colIndex);
                                        mv.invokeinterface(
                                                ByteCodes.classDesc(rowName),
                                                "getBytes",
                                                MethodTypeDesc.ofDescriptor("(I)[B"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(entityName),
                                                setter.getName(),
                                                MethodTypeDesc.ofDescriptor(desc));
                                        continue;
                                    } else if (field != null) {
                                        mv.aload(2); // obj
                                        mv.aload(1); // row
                                        mv.loadConstant(colIndex);
                                        mv.invokeinterface(
                                                ByteCodes.classDesc(rowName),
                                                "getBytes",
                                                MethodTypeDesc.ofDescriptor("(I)[B"));
                                        mv.putfield(
                                                ByteCodes.classDesc(entityName),
                                                field.getName(),
                                                ClassDesc.ofDescriptor("[B"));
                                        continue;
                                    }
                                } else if (attr.type() == BigDecimal.class) {
                                    if (setter != null) {
                                        String desc = ByteCodes.methodDescriptor(setter);
                                        mv.aload(2); // obj
                                        mv.aload(1); // row
                                        mv.loadConstant(colIndex);
                                        mv.invokeinterface(
                                                ByteCodes.classDesc(rowName),
                                                "getBigDecimal",
                                                MethodTypeDesc.ofDescriptor("(I)Ljava/math/BigDecimal;"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(entityName),
                                                setter.getName(),
                                                MethodTypeDesc.ofDescriptor(desc));
                                        continue;
                                    } else if (field != null) {
                                        mv.aload(2); // obj
                                        mv.aload(1); // row
                                        mv.loadConstant(colIndex);
                                        mv.invokeinterface(
                                                ByteCodes.classDesc(rowName),
                                                "getBigDecimal",
                                                MethodTypeDesc.ofDescriptor("(I)Ljava/math/BigDecimal;"));
                                        mv.putfield(
                                                ByteCodes.classDesc(entityName),
                                                field.getName(),
                                                ClassDesc.ofDescriptor("Ljava/math/BigDecimal;"));
                                        continue;
                                    }
                                }
                                mv.aload(0);
                                mv.loadConstant(colIndex - 1);
                                mv.aload(1); // row
                                mv.aload(2); // obj
                                mv.invokevirtual(
                                        ByteCodes.classDesc(supDynName),
                                        "setFieldValue",
                                        MethodTypeDesc.ofDescriptor("(I" + rowDesc + objectDesc + ")V"));
                            }

                            mv.aload(2); // obj
                            mv.areturn();
                            Label label2 = mv.newLabel();
                            mv.labelBinding(label2);
                            mv.localVariable(0, "this", ClassDesc.ofDescriptor("L" + newDynName + ";"), label0, label2);
                            mv.localVariable(1, "row", ClassDesc.ofDescriptor(rowDesc), label0, label2);
                            mv.localVariable(2, "obj", ClassDesc.ofDescriptor(entityDesc), label1, label2);
                        });
            }
            { // 虚拟 getObject(DataResultSetRow row)
                cw.withMethodBody(
                        "getObject",
                        MethodTypeDesc.ofDescriptor("(" + rowDesc + ")" + objectDesc),
                        ACC_PUBLIC | ACC_BRIDGE | ACC_SYNTHETIC,
                        mv -> {
                            mv.aload(0);
                            mv.aload(1);
                            mv.invokevirtual(
                                    ByteCodes.classDesc(newDynName),
                                    "getObject",
                                    MethodTypeDesc.ofDescriptor("(" + rowDesc + ")" + entityDesc));
                            mv.areturn();
                        });
            }

            { // getObject(Serializable... values)
                cw.withMethodBody(
                        "getObject",
                        MethodTypeDesc.ofDescriptor("(" + serisDesc + ")" + entityDesc),
                        ACC_PUBLIC | ACC_VARARGS,
                        mv -> {
                            Label label0 = mv.newLabel();
                            mv.labelBinding(label0);
                            // creator.create()
                            mv.aload(0);
                            mv.getfield(
                                    ByteCodes.classDesc(supDynName), "creator", ClassDesc.ofDescriptor(creatorDesc));
                            mv.iconst_0();
                            mv.anewarray(ByteCodes.classDesc("java/lang/Object"));
                            mv.invokeinterface(
                                    ByteCodes.classDesc(creatorName),
                                    "create",
                                    MethodTypeDesc.ofDescriptor("([Ljava/lang/Object;)" + objectDesc));
                            mv.checkcast(ByteCodes.classDesc(entityName));
                            mv.astore(2);
                            Label label1 = mv.newLabel();
                            mv.labelBinding(label1);

                            for (int i = 0; i < attrs.length; i++) {
                                final int attrIndex = i;
                                final Attribute<T, Serializable> attr = attrs[i];
                                java.lang.reflect.Method setter = null;
                                java.lang.reflect.Field field = null;
                                try {
                                    setter = entityType.getMethod(
                                            "set" + Utility.firstCharUpperCase(attr.field()), attr.type());
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
                                    throw new SourceException(
                                            "Not found '" + attr.field() + "' setter method or public field ");
                                }
                                if (attr.type() == boolean.class) {
                                    if (setter != null) {
                                        String desc = ByteCodes.methodDescriptor(setter);
                                        mv.aload(2); // obj
                                        mv.aload(1); // values
                                        mv.loadConstant(attrIndex);
                                        mv.aaload();
                                        mv.checkcast(ByteCodes.classDesc("java/lang/Boolean"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc("java/lang/Boolean"),
                                                "booleanValue",
                                                MethodTypeDesc.ofDescriptor("()Z"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(entityName),
                                                setter.getName(),
                                                MethodTypeDesc.ofDescriptor(desc));
                                    } else if (field != null) {
                                        mv.aload(2); // obj
                                        mv.aload(1); // values
                                        mv.loadConstant(attrIndex);
                                        mv.aaload();
                                        mv.checkcast(ByteCodes.classDesc("java/lang/Boolean"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc("java/lang/Boolean"),
                                                "booleanValue",
                                                MethodTypeDesc.ofDescriptor("()Z"));
                                        mv.putfield(
                                                ByteCodes.classDesc(entityName),
                                                field.getName(),
                                                ClassDesc.ofDescriptor("Z"));
                                    }
                                } else if (attr.type() == short.class) {
                                    if (setter != null) {
                                        String desc = ByteCodes.methodDescriptor(setter);
                                        mv.aload(2); // obj
                                        mv.aload(1); // values
                                        mv.loadConstant(attrIndex);
                                        mv.aaload();
                                        mv.checkcast(ByteCodes.classDesc("java/lang/Short"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc("java/lang/Short"),
                                                "shortValue",
                                                MethodTypeDesc.ofDescriptor("()S"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(entityName),
                                                setter.getName(),
                                                MethodTypeDesc.ofDescriptor(desc));
                                    } else if (field != null) {
                                        mv.aload(2); // obj
                                        mv.aload(1); // values
                                        mv.loadConstant(attrIndex);
                                        mv.aaload();
                                        mv.checkcast(ByteCodes.classDesc("java/lang/Short"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc("java/lang/Short"),
                                                "shortValue",
                                                MethodTypeDesc.ofDescriptor("()S"));
                                        mv.putfield(
                                                ByteCodes.classDesc(entityName),
                                                field.getName(),
                                                ClassDesc.ofDescriptor("S"));
                                    }
                                } else if (attr.type() == int.class) {
                                    if (setter != null) {
                                        String desc = ByteCodes.methodDescriptor(setter);
                                        mv.aload(2); // obj
                                        mv.aload(1); // values
                                        mv.loadConstant(attrIndex);
                                        mv.aaload();
                                        mv.checkcast(ByteCodes.classDesc("java/lang/Integer"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc("java/lang/Integer"),
                                                "intValue",
                                                MethodTypeDesc.ofDescriptor("()I"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(entityName),
                                                setter.getName(),
                                                MethodTypeDesc.ofDescriptor(desc));
                                    } else if (field != null) {
                                        mv.aload(2); // obj
                                        mv.aload(1); // values
                                        mv.loadConstant(attrIndex);
                                        mv.aaload();
                                        mv.checkcast(ByteCodes.classDesc("java/lang/Integer"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc("java/lang/Integer"),
                                                "intValue",
                                                MethodTypeDesc.ofDescriptor("()I"));
                                        mv.putfield(
                                                ByteCodes.classDesc(entityName),
                                                field.getName(),
                                                ClassDesc.ofDescriptor("I"));
                                    }
                                } else if (attr.type() == float.class) {
                                    if (setter != null) {
                                        String desc = ByteCodes.methodDescriptor(setter);
                                        mv.aload(2); // obj
                                        mv.aload(1); // values
                                        mv.loadConstant(attrIndex);
                                        mv.aaload();
                                        mv.checkcast(ByteCodes.classDesc("java/lang/Float"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc("java/lang/Float"),
                                                "floatValue",
                                                MethodTypeDesc.ofDescriptor("()F"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(entityName),
                                                setter.getName(),
                                                MethodTypeDesc.ofDescriptor(desc));
                                    } else if (field != null) {
                                        mv.aload(2); // obj
                                        mv.aload(1); // values
                                        mv.loadConstant(attrIndex);
                                        mv.aaload();
                                        mv.checkcast(ByteCodes.classDesc("java/lang/Float"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc("java/lang/Float"),
                                                "floatValue",
                                                MethodTypeDesc.ofDescriptor("()F"));
                                        mv.putfield(
                                                ByteCodes.classDesc(entityName),
                                                field.getName(),
                                                ClassDesc.ofDescriptor("F"));
                                    }
                                } else if (attr.type() == long.class) {
                                    if (setter != null) {
                                        String desc = ByteCodes.methodDescriptor(setter);
                                        mv.aload(2); // obj
                                        mv.aload(1); // values
                                        mv.loadConstant(attrIndex);
                                        mv.aaload();
                                        mv.checkcast(ByteCodes.classDesc("java/lang/Long"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc("java/lang/Long"),
                                                "longValue",
                                                MethodTypeDesc.ofDescriptor("()J"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(entityName),
                                                setter.getName(),
                                                MethodTypeDesc.ofDescriptor(desc));
                                    } else if (field != null) {
                                        mv.aload(2); // obj
                                        mv.aload(1); // values
                                        mv.loadConstant(attrIndex);
                                        mv.aaload();
                                        mv.checkcast(ByteCodes.classDesc("java/lang/Long"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc("java/lang/Long"),
                                                "longValue",
                                                MethodTypeDesc.ofDescriptor("()J"));
                                        mv.putfield(
                                                ByteCodes.classDesc(entityName),
                                                field.getName(),
                                                ClassDesc.ofDescriptor("J"));
                                    }
                                } else if (attr.type() == double.class) {
                                    if (setter != null) {
                                        String desc = ByteCodes.methodDescriptor(setter);
                                        mv.aload(2); // obj
                                        mv.aload(1); // values
                                        mv.loadConstant(attrIndex);
                                        mv.aaload();
                                        mv.checkcast(ByteCodes.classDesc("java/lang/Double"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc("java/lang/Double"),
                                                "doubleValue",
                                                MethodTypeDesc.ofDescriptor("()D"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(entityName),
                                                setter.getName(),
                                                MethodTypeDesc.ofDescriptor(desc));
                                    } else if (field != null) {
                                        mv.aload(2); // obj
                                        mv.aload(1); // values
                                        mv.loadConstant(attrIndex);
                                        mv.aaload();
                                        mv.checkcast(ByteCodes.classDesc("java/lang/Double"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc("java/lang/Double"),
                                                "doubleValue",
                                                MethodTypeDesc.ofDescriptor("()D"));
                                        mv.putfield(
                                                ByteCodes.classDesc(entityName),
                                                field.getName(),
                                                ClassDesc.ofDescriptor("D"));
                                    }
                                } else if (attr.type() == Boolean.class) {
                                    if (setter != null) {
                                        String desc = ByteCodes.methodDescriptor(setter);
                                        mv.aload(2); // obj
                                        mv.aload(1); // values
                                        mv.loadConstant(attrIndex);
                                        mv.aaload();
                                        mv.checkcast(ByteCodes.classDesc("java/lang/Boolean"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(entityName),
                                                setter.getName(),
                                                MethodTypeDesc.ofDescriptor(desc));
                                    } else if (field != null) {
                                        mv.aload(2); // obj
                                        mv.aload(1); // values
                                        mv.loadConstant(attrIndex);
                                        mv.aaload();
                                        mv.checkcast(ByteCodes.classDesc("java/lang/Boolean"));
                                        mv.putfield(
                                                ByteCodes.classDesc(entityName),
                                                field.getName(),
                                                ClassDesc.ofDescriptor("Ljava/lang/Boolean;"));
                                    }
                                } else if (attr.type() == Short.class) {
                                    if (setter != null) {
                                        String desc = ByteCodes.methodDescriptor(setter);
                                        mv.aload(2); // obj
                                        mv.aload(1); // values
                                        mv.loadConstant(attrIndex);
                                        mv.aaload();
                                        mv.checkcast(ByteCodes.classDesc("java/lang/Short"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(entityName),
                                                setter.getName(),
                                                MethodTypeDesc.ofDescriptor(desc));
                                    } else if (field != null) {
                                        mv.aload(2); // obj
                                        mv.aload(1); // values
                                        mv.loadConstant(attrIndex);
                                        mv.aaload();
                                        mv.checkcast(ByteCodes.classDesc("java/lang/Short"));
                                        mv.putfield(
                                                ByteCodes.classDesc(entityName),
                                                field.getName(),
                                                ClassDesc.ofDescriptor("Ljava/lang/Short;"));
                                    }
                                } else if (attr.type() == Integer.class) {
                                    if (setter != null) {
                                        String desc = ByteCodes.methodDescriptor(setter);
                                        mv.aload(2); // obj
                                        mv.aload(1); // values
                                        mv.loadConstant(attrIndex);
                                        mv.aaload();
                                        mv.checkcast(ByteCodes.classDesc("java/lang/Integer"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(entityName),
                                                setter.getName(),
                                                MethodTypeDesc.ofDescriptor(desc));
                                    } else if (field != null) {
                                        mv.aload(2); // obj
                                        mv.aload(1); // values
                                        mv.loadConstant(attrIndex);
                                        mv.aaload();
                                        mv.checkcast(ByteCodes.classDesc("java/lang/Integer"));
                                        mv.putfield(
                                                ByteCodes.classDesc(entityName),
                                                field.getName(),
                                                ClassDesc.ofDescriptor("Ljava/lang/Integer;"));
                                    }
                                } else if (attr.type() == Float.class) {
                                    if (setter != null) {
                                        String desc = ByteCodes.methodDescriptor(setter);
                                        mv.aload(2); // obj
                                        mv.aload(1); // values
                                        mv.loadConstant(attrIndex);
                                        mv.aaload();
                                        mv.checkcast(ByteCodes.classDesc("java/lang/Float"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(entityName),
                                                setter.getName(),
                                                MethodTypeDesc.ofDescriptor(desc));
                                    } else if (field != null) {
                                        mv.aload(2); // obj
                                        mv.aload(1); // values
                                        mv.loadConstant(attrIndex);
                                        mv.aaload();
                                        mv.checkcast(ByteCodes.classDesc("java/lang/Float"));
                                        mv.putfield(
                                                ByteCodes.classDesc(entityName),
                                                field.getName(),
                                                ClassDesc.ofDescriptor("Ljava/lang/Float;"));
                                    }
                                } else if (attr.type() == Long.class) {
                                    if (setter != null) {
                                        String desc = ByteCodes.methodDescriptor(setter);
                                        mv.aload(2); // obj
                                        mv.aload(1); // values
                                        mv.loadConstant(attrIndex);
                                        mv.aaload();
                                        mv.checkcast(ByteCodes.classDesc("java/lang/Long"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(entityName),
                                                setter.getName(),
                                                MethodTypeDesc.ofDescriptor(desc));
                                    } else if (field != null) {
                                        mv.aload(2); // obj
                                        mv.aload(1); // values
                                        mv.loadConstant(attrIndex);
                                        mv.aaload();
                                        mv.checkcast(ByteCodes.classDesc("java/lang/Long"));
                                        mv.putfield(
                                                ByteCodes.classDesc(entityName),
                                                field.getName(),
                                                ClassDesc.ofDescriptor("Ljava/lang/Long;"));
                                    }
                                } else if (attr.type() == Double.class) {
                                    if (setter != null) {
                                        String desc = ByteCodes.methodDescriptor(setter);
                                        mv.aload(2); // obj
                                        mv.aload(1); // values
                                        mv.loadConstant(attrIndex);
                                        mv.aaload();
                                        mv.checkcast(ByteCodes.classDesc("java/lang/Double"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(entityName),
                                                setter.getName(),
                                                MethodTypeDesc.ofDescriptor(desc));
                                    } else if (field != null) {
                                        mv.aload(2); // obj
                                        mv.aload(1); // values
                                        mv.loadConstant(attrIndex);
                                        mv.aaload();
                                        mv.checkcast(ByteCodes.classDesc("java/lang/Double"));
                                        mv.putfield(
                                                ByteCodes.classDesc(entityName),
                                                field.getName(),
                                                ClassDesc.ofDescriptor("Ljava/lang/Double;"));
                                    }
                                } else if (attr.type() == String.class) {
                                    if (setter != null) {
                                        String desc = ByteCodes.methodDescriptor(setter);
                                        mv.aload(2); // obj
                                        mv.aload(1); // values
                                        mv.loadConstant(attrIndex);
                                        mv.aaload();
                                        mv.checkcast(ByteCodes.classDesc("java/lang/String"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(entityName),
                                                setter.getName(),
                                                MethodTypeDesc.ofDescriptor(desc));
                                    } else if (field != null) {
                                        mv.aload(2); // obj
                                        mv.aload(1); // values
                                        mv.loadConstant(attrIndex);
                                        mv.aaload();
                                        mv.checkcast(ByteCodes.classDesc("java/lang/String"));
                                        mv.putfield(
                                                ByteCodes.classDesc(entityName),
                                                field.getName(),
                                                ClassDesc.ofDescriptor("Ljava/lang/String;"));
                                    }
                                } else if (attr.type() == byte[].class) {
                                    if (setter != null) {
                                        String desc = ByteCodes.methodDescriptor(setter);
                                        mv.aload(2); // obj
                                        mv.aload(1); // values
                                        mv.loadConstant(attrIndex);
                                        mv.aaload();
                                        mv.checkcast(ByteCodes.classDesc("[B"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(entityName),
                                                setter.getName(),
                                                MethodTypeDesc.ofDescriptor(desc));
                                    } else if (field != null) {
                                        mv.aload(2); // obj
                                        mv.aload(1); // values
                                        mv.loadConstant(attrIndex);
                                        mv.aaload();
                                        mv.checkcast(ByteCodes.classDesc("[B"));
                                        mv.putfield(
                                                ByteCodes.classDesc(entityName),
                                                field.getName(),
                                                ClassDesc.ofDescriptor("[B"));
                                    }
                                } else if (attr.type() == BigDecimal.class) {
                                    if (setter != null) {
                                        String desc = ByteCodes.methodDescriptor(setter);
                                        mv.aload(2); // obj
                                        mv.aload(1); // values
                                        mv.loadConstant(attrIndex);
                                        mv.aaload();
                                        mv.checkcast(ByteCodes.classDesc("java/math/BigDecimal"));
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(entityName),
                                                setter.getName(),
                                                MethodTypeDesc.ofDescriptor(desc));
                                    } else if (field != null) {
                                        mv.aload(2); // obj
                                        mv.aload(1); // values
                                        mv.loadConstant(attrIndex);
                                        mv.aaload();
                                        mv.checkcast(ByteCodes.classDesc("java/math/BigDecimal"));
                                        mv.putfield(
                                                ByteCodes.classDesc(entityName),
                                                field.getName(),
                                                ClassDesc.ofDescriptor("Ljava/math/BigDecimal;"));
                                    }
                                } else {
                                    if (setter != null) {
                                        String desc = ByteCodes.methodDescriptor(setter);
                                        mv.aload(2); // obj
                                        mv.aload(1); // values
                                        mv.loadConstant(attrIndex);
                                        mv.aaload();
                                        ByteCodes.visitCheckCast(mv, setter.getParameterTypes()[0]);
                                        mv.invokevirtual(
                                                ByteCodes.classDesc(entityName),
                                                setter.getName(),
                                                MethodTypeDesc.ofDescriptor(desc));
                                    } else if (field != null) {
                                        String desc = ByteCodes.descriptor(field.getType());
                                        mv.aload(2); // obj
                                        mv.aload(1); // values
                                        mv.loadConstant(attrIndex);
                                        mv.aaload();
                                        ByteCodes.visitCheckCast(mv, field.getType());
                                        mv.putfield(
                                                ByteCodes.classDesc(entityName),
                                                field.getName(),
                                                ClassDesc.ofDescriptor(desc));
                                    }
                                }
                            }
                            mv.aload(2); // obj
                            mv.areturn();
                            Label label2 = mv.newLabel();
                            mv.labelBinding(label2);
                            mv.localVariable(0, "this", ClassDesc.ofDescriptor("L" + newDynName + ";"), label0, label2);
                            mv.localVariable(1, "values", ClassDesc.ofDescriptor(serisDesc), label0, label2);
                            mv.localVariable(2, "obj", ClassDesc.ofDescriptor(entityDesc), label1, label2);
                        });
            }
            { // 虚拟 getObject(Serializable... values)
                int access = ACC_PUBLIC | ACC_BRIDGE | ACC_VARARGS | ACC_SYNTHETIC;
                cw.withMethodBody(
                        "getObject", MethodTypeDesc.ofDescriptor("(" + serisDesc + ")" + objectDesc), access, mv -> {
                            mv.aload(0);
                            mv.aload(1);
                            mv.invokevirtual(
                                    ByteCodes.classDesc(newDynName),
                                    "getObject",
                                    MethodTypeDesc.ofDescriptor("(" + serisDesc + ")" + entityDesc));
                            mv.areturn();
                        });
            }
        });

        byte[] bytes = classBytes;
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
