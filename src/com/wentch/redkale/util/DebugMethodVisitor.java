/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.util;

import jdk.internal.org.objectweb.asm.*;

/**
 *
 * @author zhangjx
 */
public class DebugMethodVisitor {

    private final MethodVisitor visitor;

    private boolean debug = false;

    public void setDebug(boolean d) {
        debug = d;
    }

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
                opcodes[(int) field.get(null)] = name;
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex); //不可能会发生
        }
    }

    public DebugMethodVisitor(MethodVisitor visitor) {
        //super(Opcodes.ASM5, visitor);
        this.visitor = visitor;
    }

    public void visitParameter(String name, int access) {
        visitor.visitParameter(name, access);
        if (debug) System.out.println("mv.visitParameter(" + name + ", " + access + ");");
    }

    public void visitVarInsn(int opcode, int var) {
        visitor.visitVarInsn(opcode, var);
        if (debug) System.out.println("mv.visitVarInsn(" + opcodes[opcode] + ", " + var + ");");
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
