/*
 *
 */
package org.redkale.asm;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import static org.redkale.asm.Opcodes.BIPUSH;
import static org.redkale.asm.Opcodes.CHECKCAST;
import static org.redkale.asm.Opcodes.GETSTATIC;
import static org.redkale.asm.Opcodes.ICONST_0;
import static org.redkale.asm.Opcodes.INVOKESTATIC;
import static org.redkale.asm.Opcodes.INVOKEVIRTUAL;
import static org.redkale.asm.Opcodes.SIPUSH;
import org.redkale.util.RedkaleException;

/**
 * ASM简单的工具方法 <br>
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
public final class Asms {

    private Asms() {
    }

    public static void visitAnnotation(final AnnotationVisitor av, final Annotation ann) {
        try {
            for (Method anm : ann.annotationType().getMethods()) {
                final String mname = anm.getName();
                if ("equals".equals(mname) || "hashCode".equals(mname) || "toString".equals(mname) || "annotationType".equals(mname)) {
                    continue;
                }
                final Object r = anm.invoke(ann);
                if (r instanceof String[]) {
                    AnnotationVisitor av1 = av.visitArray(mname);
                    for (String item : (String[]) r) {
                        av1.visit(null, item);
                    }
                    av1.visitEnd();
                } else if (r instanceof Class[]) {
                    AnnotationVisitor av1 = av.visitArray(mname);
                    for (Class item : (Class[]) r) {
                        av1.visit(null, Type.getType(item));
                    }
                    av1.visitEnd();
                } else if (r instanceof Enum[]) {
                    AnnotationVisitor av1 = av.visitArray(mname);
                    for (Enum item : (Enum[]) r) {
                        av1.visitEnum(null, Type.getDescriptor(item.getClass()), ((Enum) item).name());
                    }
                    av1.visitEnd();
                } else if (r instanceof Annotation[]) {
                    AnnotationVisitor av1 = av.visitArray(mname);
                    for (Annotation item : (Annotation[]) r) {
                        visitAnnotation(av1.visitAnnotation(null, Type.getDescriptor(((Annotation) item).annotationType())), item);
                    }
                    av1.visitEnd();
                } else if (r instanceof Class) {
                    av.visit(mname, Type.getType((Class) r));
                } else if (r instanceof Enum) {
                    av.visitEnum(mname, Type.getDescriptor(r.getClass()), ((Enum) r).name());
                } else if (r instanceof Annotation) {
                    visitAnnotation(av.visitAnnotation(null, Type.getDescriptor(((Annotation) r).annotationType())), (Annotation) r);
                } else {
                    av.visit(mname, r);
                }
            }
            av.visitEnd();
        } catch (Exception e) {
            throw new RedkaleException(e);
        }
    }

    public static void visitInsn(MethodVisitor mv, int num) {
        if (num < 6) {
            mv.visitInsn(ICONST_0 + num);
        } else if (num <= Byte.MAX_VALUE) {
            mv.visitIntInsn(BIPUSH, num);
        } else if (num <= Short.MAX_VALUE) {
            mv.visitIntInsn(SIPUSH, num);
        } else {
            mv.visitLdcInsn(num);
        }
    }

    public static void visitFieldInsn(MethodVisitor mv, Class clazz) {
        if (clazz == boolean.class) {
            mv.visitFieldInsn(GETSTATIC, "java/lang/Boolean", "TYPE", "Ljava/lang/Class;");
        } else if (clazz == byte.class) {
            mv.visitFieldInsn(GETSTATIC, "java/lang/Byte", "TYPE", "Ljava/lang/Class;");
        } else if (clazz == char.class) {
            mv.visitFieldInsn(GETSTATIC, "java/lang/Character", "TYPE", "Ljava/lang/Class;");
        } else if (clazz == short.class) {
            mv.visitFieldInsn(GETSTATIC, "java/lang/Short", "TYPE", "Ljava/lang/Class;");
        } else if (clazz == int.class) {
            mv.visitFieldInsn(GETSTATIC, "java/lang/Integer", "TYPE", "Ljava/lang/Class;");
        } else if (clazz == float.class) {
            mv.visitFieldInsn(GETSTATIC, "java/lang/Float", "TYPE", "Ljava/lang/Class;");
        } else if (clazz == long.class) {
            mv.visitFieldInsn(GETSTATIC, "java/lang/Long", "TYPE", "Ljava/lang/Class;");
        } else if (clazz == double.class) {
            mv.visitFieldInsn(GETSTATIC, "java/lang/Double", "TYPE", "Ljava/lang/Class;");
        } else {
            mv.visitLdcInsn(Type.getType(Type.getDescriptor(clazz)));
        }
    }

    public static void visitPrimitiveValueOf(MethodVisitor mv, Class clazz) {
        if (clazz == boolean.class) {
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
        } else if (clazz == byte.class) {
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
        } else if (clazz == short.class) {
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false);
        } else if (clazz == char.class) {
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false);
        } else if (clazz == int.class) {
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
        } else if (clazz == float.class) {
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
        } else if (clazz == long.class) {
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
        } else if (clazz == double.class) {
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
        }
    }

    public static void visitCheckCast(MethodVisitor mv, Class clazz) {
        if (clazz == boolean.class) {
            mv.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
        } else if (clazz == byte.class) {
            mv.visitTypeInsn(CHECKCAST, "java/lang/Byte");
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B", false);
        } else if (clazz == short.class) {
            mv.visitTypeInsn(CHECKCAST, "java/lang/Short");
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S", false);
        } else if (clazz == char.class) {
            mv.visitTypeInsn(CHECKCAST, "java/lang/Character");
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C", false);
        } else if (clazz == int.class) {
            mv.visitTypeInsn(CHECKCAST, "java/lang/Integer");
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
        } else if (clazz == float.class) {
            mv.visitTypeInsn(CHECKCAST, "java/lang/Float");
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false);
        } else if (clazz == long.class) {
            mv.visitTypeInsn(CHECKCAST, "java/lang/Long");
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
        } else if (clazz == double.class) {
            mv.visitTypeInsn(CHECKCAST, "java/lang/Double");
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
        } else {
            mv.visitTypeInsn(CHECKCAST, clazz.getName().replace('.', '/'));
        }
    }

}
