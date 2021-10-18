/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.asm;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import static org.redkale.asm.Opcodes.*;

/**
 * MethodVisitor 的调试类
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class MethodDebugVisitor {

    private final MethodVisitor visitor;

    private boolean debug = false;

    public MethodDebugVisitor setDebug(boolean d) {
        debug = d;
        return this;
    }

    public void debugLine() {
        if (!debug) return;
        System.out.println();
        System.out.println();
        System.out.println();
    }

    private final Map<Label, Integer> labels = new LinkedHashMap<>();

    private static final String[] opcodes = new String[200]; //0 -18

    static {
        try {
            for (java.lang.reflect.Field field : Opcodes.class.getFields()) {
                String name = field.getName();
                if (name.startsWith("ASM")) continue;
                if (name.startsWith("V1_")) continue;
                if (name.startsWith("ACC_")) continue;
                if (name.startsWith("T_")) continue;
                if (name.startsWith("H_")) continue;
                if (name.startsWith("F_")) continue;
                if (field.getType() != int.class) continue;
                opcodes[(int) (Integer) field.get(null)] = name;
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex); //不可能会发生
        }
    }

    /**
     *
     * @param visitor MethodVisitor
     */
    public MethodDebugVisitor(MethodVisitor visitor) {
        //super(Opcodes.ASM5, visitor);
        this.visitor = visitor;
    }

    public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
        visitor.visitTryCatchBlock(start, end, handler, type);
        if (debug) System.out.println("mv.visitTryCatchBlock(label0, label1, label2, \"" + type + "\");");
    }

    public AnnotationVisitor visitParameterAnnotation(int i, String string, boolean bln) {
        AnnotationVisitor av = visitor.visitParameterAnnotation(i, string, bln);
        if (debug) System.out.println("mv.visitParameterAnnotation(" + i + ", \"" + string + "\", " + bln + ");");
        return av;
    }

    public AnnotationVisitor visitAnnotation(String desc, boolean flag) {
        AnnotationVisitor av = visitor.visitAnnotation(desc, flag);
        if (debug) System.out.println("mv.visitAnnotation(\"" + desc + "\", " + flag + ");");
        return av;
    }

    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
        AnnotationVisitor av = visitor.visitTypeAnnotation(typeRef, typePath, desc, visible);
        if (debug) System.out.println("mv.visitTypeAnnotation(" + typeRef + ", " + typePath + ", \"" + desc + "\", " + visible + ");");
        return av;
    }

    public void visitParameter(String name, int access) {
        visitor.visitParameter(name, access);
        if (debug) System.out.println("mv.visitParameter(" + name + ", " + access + ");");
    }

    public void visitVarInsn(int opcode, int var) {
        visitor.visitVarInsn(opcode, var);
        if (debug) System.out.println("mv.visitVarInsn(" + opcodes[opcode] + ", " + var + ");");
    }

    public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
        visitor.visitFrame(type, nLocal, local, nStack, stack);
        if (debug) {
            String typestr = "" + type;
            if (type == -1) {
                typestr = "Opcodes.F_NEW";
            } else if (type == 1) {
                typestr = "Opcodes.F_APPEND";
            } else if (type == 2) {
                typestr = "Opcodes.F_CHOP";
            } else if (type == 3) {
                typestr = "Opcodes.F_SAME";
            } else if (type == 4) {
                typestr = "Opcodes.F_SAME1";
            }
            System.out.println("mv.visitFrame(" + typestr + ", " + nLocal + ", " + Arrays.toString(local) + ", " + nStack + ", " + Arrays.toString(stack) + ");");
        }
    }

    public void visitJumpInsn(int opcode, Label var) {   //调用此方法的 ClassWriter 必须由 COMPUTE_FRAMES 构建
        visitor.visitJumpInsn(opcode, var);
        if (debug) {
            Integer index = labels.get(var);
            if (index == null) {
                index = labels.size();
                labels.put(var, index);
                System.out.println("Label l" + index + " = new Label();");
            }
            System.out.println("mv.visitJumpInsn(" + opcodes[opcode] + ", l" + index + ");");
        }
    }

    public void visitCode() {
        visitor.visitCode();
        if (debug) System.out.println("mv.visitCode();");
    }

    public void visitLabel(Label var) {
        visitor.visitLabel(var);
        if (debug) {
            Integer index = labels.get(var);
            if (index == null) {
                index = labels.size();
                labels.put(var, index);
                System.out.println("Label l" + index + " = new Label();");
            }
            System.out.println("mv.visitLabel(l" + index + ");");
        }
    }

    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
        visitor.visitMethodInsn(opcode, owner, name, desc, itf);
        if (debug) System.out.println("mv.visitMethodInsn(" + opcodes[opcode] + ", \"" + owner + "\", \"" + name + "\", \"" + desc + "\", " + itf + ");");
    }

    public void visitFieldInsn(int opcode, String owner, String name, String desc) {
        visitor.visitFieldInsn(opcode, owner, name, desc);
        if (debug) System.out.println("mv.visitFieldInsn(" + opcodes[opcode] + ", \"" + owner + "\", \"" + name + "\", \"" + desc + "\");");
    }

    public void visitTypeInsn(int opcode, String type) {
        visitor.visitTypeInsn(opcode, type);
        if (debug) System.out.println("mv.visitTypeInsn(" + opcodes[opcode] + ", \"" + type + "\");");
    }

    public void visitInsn(int opcode) {
        visitor.visitInsn(opcode);
        if (debug) System.out.println("mv.visitInsn(" + opcodes[opcode] + ");");
    }

    public void visitIntInsn(int opcode, int value) {
        visitor.visitIntInsn(opcode, value);
        if (debug) System.out.println("mv.visitIntInsn(" + opcodes[opcode] + ", " + value + ");");
    }

    public void visitIincInsn(int opcode, int value) {
        visitor.visitIincInsn(opcode, value);
        if (debug) System.out.println("mv.visitIincInsn(" + opcode + ", " + value + ");");
    }

    public void visitLdcInsn(Object o) {
        visitor.visitLdcInsn(o);
        if (debug) {
            if (o instanceof CharSequence) {
                System.out.println("mv.visitLdcInsn(\"" + o + "\");");
            } else if (o instanceof org.redkale.asm.Type) {
                System.out.println("mv.visitLdcInsn(Type.getType(\"" + o + "\"));");
            } else {
                System.out.println("mv.visitLdcInsn(" + o + ");");
            }
        }
    }

    public void visitMaxs(int maxStack, int maxLocals) {
        visitor.visitMaxs(maxStack, maxLocals);
        if (debug) System.out.println("mv.visitMaxs(" + maxStack + ", " + maxLocals + ");");
    }

    public void visitEnd() {
        visitor.visitEnd();
        if (debug) System.out.println("mv.visitEnd();\r\n\r\n\r\n");
    }

    public static void pushInt(MethodDebugVisitor mv, int num) {
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

    public static void pushInt(MethodVisitor mv, int num) {
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

    public static void visitAnnotation(final AnnotationVisitor av, final Annotation ann) {
        try {
            for (Method anm : ann.annotationType().getMethods()) {
                final String mname = anm.getName();
                if ("equals".equals(mname) || "hashCode".equals(mname) || "toString".equals(mname) || "annotationType".equals(mname)) continue;
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
            e.printStackTrace();
        }
    }
}
