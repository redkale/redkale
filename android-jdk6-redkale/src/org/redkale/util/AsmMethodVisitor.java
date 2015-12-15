/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.util.*;
import org.objectweb.asm.*;

/**
 *
 * @see http://www.redkale.org
 * @author zhangjx
 */
public class AsmMethodVisitor {

    private final MethodVisitor visitor;

    private boolean debug = false;

    public AsmMethodVisitor setDebug(boolean d) {
        debug = d;
        return this;
    }

    private final Map<Label, Integer> labels = new LinkedHashMap();

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

    public AsmMethodVisitor(MethodVisitor visitor) {
        //super(Opcodes.ASM5, visitor);
        this.visitor = visitor;
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

    public void visitParameter(String name, int access) {
        visitor.visitParameter(name, access);
        if (debug) System.out.println("mv.visitParameter(" + name + ", " + access + ");");
    }

    public void visitVarInsn(int opcode, int var) {
        visitor.visitVarInsn(opcode, var);
        if (debug) System.out.println("mv.visitVarInsn(" + opcodes[opcode] + ", " + var + ");");
    }

    public void visitJumpInsn(int opcode, Label var) {   //调用次方法的 ClassWriter 必须由 COMPUTE_FRAMES 构建
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
        if (debug) System.out.println("mv.visitLdcInsn(" + o + ");");
    }

    public void visitMaxs(int maxStack, int maxLocals) {
        visitor.visitMaxs(maxStack, maxLocals);
        if (debug) System.out.println("mv.visitMaxs(" + maxStack + ", " + maxLocals + ");");
    }

    public void visitEnd() {
        visitor.visitEnd();
        if (debug) System.out.println("mv.visitEnd();\r\n\r\n\r\n");
    }
}
