package org.redkale.util;

import java.lang.reflect.Modifier;
import java.util.function.Predicate;
import static org.objectweb.asm.Opcodes.*;
import org.objectweb.asm.*;

public interface Reproduce<D, S> {

    public D copy(D dest, S src);

    public static abstract class Reproduces {

        public static <D, S> Reproduce<D, S> create(final Class<D> destClass, final Class<S> srcClass) {
            return create(destClass, srcClass, null);
        }

        @SuppressWarnings("unchecked")
        public static <D, S> Reproduce<D, S> create(final Class<D> destClass, final Class<S> srcClass, final Predicate<String> columnPredicate) {
            // ------------------------------------------------------------------------------
            final String supDynName = Reproduce.class.getName().replace('.', '/');
            final String destName = destClass.getName().replace('.', '/');
            final String srcName = srcClass.getName().replace('.', '/');
            final String destDesc = Type.getDescriptor(destClass);
            final String srcDesc = Type.getDescriptor(srcClass);
            String newDynName = supDynName + "Dyn_" + destClass.getSimpleName() + "_" + srcClass.getSimpleName();
            ClassLoader loader = Reproduce.class.getClassLoader();
            if (String.class.getClassLoader() != destClass.getClassLoader()) {
                loader = destClass.getClassLoader();
                newDynName = destName + "_Dyn" + Reproduce.class.getSimpleName() + "_" + srcClass.getSimpleName();
            }
            try {
                return (Reproduce) Class.forName(newDynName.replace('/', '.')).newInstance();
            } catch (Exception ex) {
            }
            // ------------------------------------------------------------------------------
            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            MethodVisitor mv;
            AnnotationVisitor av0;

            cw.visit(V1_7, ACC_PUBLIC + ACC_FINAL + ACC_SUPER, newDynName, "Ljava/lang/Object;L" + supDynName + "<" + destDesc + srcDesc + ">;", "java/lang/Object", new String[]{supDynName});

            { // 构造函数
                mv = (cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null));
                //mv.setDebug(true);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {
                mv = (cw.visitMethod(ACC_PUBLIC, "copy", "(" + destDesc + srcDesc + ")" + destDesc, null, null));
                //mv.setDebug(true);

                for (java.lang.reflect.Field field : srcClass.getFields()) {
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    if (Modifier.isFinal(field.getModifiers())) continue;
                    if (!Modifier.isPublic(field.getModifiers())) continue;
                    final String fname = field.getName();
                    try {
                        if (!field.getType().equals(destClass.getField(fname).getType())) continue;
                        if (!columnPredicate.test(fname)) continue;
                    } catch (Exception e) {
                        continue;
                    }
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitVarInsn(ALOAD, 2);
                    String td = Type.getDescriptor(field.getType());
                    mv.visitFieldInsn(GETFIELD, srcName, fname, td);
                    mv.visitFieldInsn(PUTFIELD, destName, fname, td);
                }

                for (java.lang.reflect.Method getter : srcClass.getMethods()) {
                    if (Modifier.isStatic(getter.getModifiers())) continue;
                     if (getter.getParameterTypes().length > 0) continue;
                    if ("getClass".equals(getter.getName())) continue;
                    if (!getter.getName().startsWith("get") && !getter.getName().startsWith("is")) continue;
                    java.lang.reflect.Method setter;
                    boolean is = getter.getName().startsWith("is");
                    try {
                        setter = destClass.getMethod(getter.getName().replaceFirst(is ? "is" : "get", "set"), getter.getReturnType());
                        if (columnPredicate != null) {
                            String col = setter.getName().substring(3);
                            if (col.length() < 2 || Character.isLowerCase(col.charAt(1))) {
                                char[] cs = col.toCharArray();
                                cs[0] = Character.toLowerCase(cs[0]);
                                col = new String(cs);
                            }
                            if (!columnPredicate.test(col)) continue;
                        }
                    } catch (Exception e) {
                        continue;
                    }
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitMethodInsn(INVOKEVIRTUAL, srcName, getter.getName(), Type.getMethodDescriptor(getter), false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, destName, setter.getName(), Type.getMethodDescriptor(setter), false);
                }
                mv.visitVarInsn(ALOAD, 1);
                mv.visitInsn(ARETURN);
                mv.visitMaxs(3, 3);
                mv.visitEnd();
            }
            {
                mv = (cw.visitMethod(ACC_PUBLIC + ACC_BRIDGE + ACC_SYNTHETIC, "copy", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", null, null));
                //mv.setDebug(true);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitTypeInsn(CHECKCAST, destName);
                mv.visitVarInsn(ALOAD, 2);
                mv.visitTypeInsn(CHECKCAST, srcName);
                mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, "copy", "(" + destDesc + srcDesc + ")" + destDesc, false);
                mv.visitInsn(ARETURN);
                mv.visitMaxs(3, 3);
                mv.visitEnd();
            }
            cw.visitEnd();
            // ------------------------------------------------------------------------------
            byte[] bytes = cw.toByteArray();
            Class<?> creatorClazz = new ClassLoader(loader) {
                public final Class<?> loadClass(String name, byte[] b) {
                    return defineClass(name, b, 0, b.length);
                }
            }.loadClass(newDynName.replace('/', '.'), bytes);
            try {
                return (Reproduce) creatorClazz.newInstance();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

}
