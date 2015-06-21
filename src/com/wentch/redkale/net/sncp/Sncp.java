/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.sncp;

import com.wentch.redkale.convert.bson.*;
import com.wentch.redkale.net.*;
import com.wentch.redkale.net.sncp.SncpClient.SncpAction;
import com.wentch.redkale.service.*;
import com.wentch.redkale.util.*;
import jdk.internal.org.objectweb.asm.*;
import static jdk.internal.org.objectweb.asm.Opcodes.*;

/**
 *
 * @author zhangjx
 */
public abstract class Sncp {

    private static final byte[] hashes = new byte[255];

    static {
        //0-9:48-57  A-Z:65-90 a-z:97-122  $:36  _:95
        byte index = 0;
        hashes['_'] = index++;
        hashes['$'] = index++;
        for (int i = '0'; i <= '9'; i++) {
            hashes[i] = index++;
        }
        for (int i = 'A'; i <= 'Z'; i++) {
            hashes[i] = index++;
        }
        for (int i = 'a'; i <= 'z'; i++) {
            hashes[i] = index++;
        }
    }

    private Sncp() {
    }

    public static long hash(final Class clazz) {
        if (clazz == null) return Long.MIN_VALUE;
        long rs = hash(clazz.getSimpleName());
        return (rs < Integer.MAX_VALUE) ? rs | 0xF00000000L : rs;
    }

    public static DLong hash(final java.lang.reflect.Method method) {
        if (method == null) return new DLong(-1L, -1L);
        long rs1 = hash(method.getName());
        if (rs1 < Integer.MAX_VALUE) {
            rs1 |= (method.getParameterCount() + 0L) << 32;
        }
        rs1 = (rs1 < Integer.MAX_VALUE) ? rs1 | 0xF00000000L : rs1;
        long rs2 = hash(wrapName(method), true);
        if (rs2 < Integer.MAX_VALUE) {
            rs2 |= (method.getParameterCount() + 0L) << 32;
        }
        rs2 = (rs2 < Integer.MAX_VALUE) ? rs2 | 0xF00000000L : rs2;
        return new DLong(rs1, rs2);
    }

    private static String wrapName(final java.lang.reflect.Method method) {
        final Class[] params = method.getParameterTypes();
        if (params.length == 0) return method.getName() + "00";
        int c = 0;
        for (Class clzz : params) {
            c += clzz.getSimpleName().charAt(0);
        }
        return method.getName() + Integer.toString(params.length, 36) + Integer.toString(0xff & c, 36);
    }

    public static long hash(final String name) {
        return hash(name, false);
    }

    public static long hash(final String name, boolean reverse) {
        if (name == null) return Long.MIN_VALUE;
        if (name.isEmpty()) return 0;
        char[] chars = Utility.charArray(name);
        long rs = 0L;
        if (reverse) {
            int start = Math.max(chars.length - 10, 0);
            for (int i = chars.length - 1; i >= start; i--) {
                rs = (rs << 6) | hashes[0xff & chars[i]];
            }
        } else {
            int end = Math.min(chars.length, 11);
            for (int i = 0; i < end; i++) {
                rs = (rs << 6) | hashes[0xff & chars[i]];
            }
        }
        return Math.abs(rs);
    }


    /*
     * public final class DynRemoteTestService extends TestService{
     *
     *  @Resource
     *  private BsonConvert convert;
     * 
     *  @Resource(name="xxxx")
     *  private Transport transport;
     *
     *  public SncpClient client;
     *
     *  @Override
     *  public boolean testChange(TestBean bean) {
     *      return client.remote(convert, transport, 0, bean);
     *  }
     *
     *  @Override
     *  public TestBean findTestBean(long id) {
     *      return client.remote(convert, transport, 1, id);
     *  }
     *
     *  @Override
     *  public void runTestBean(long id, TestBean bean) {
     *      client.remote(convert, transport, 2, id, bean);
     *  }
     */
    /**
     *
     * @param <T>
     * @param serviceName
     * @param remote
     * @param serviceClass
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <T extends Service> T createRemoteService(final String serviceName, final Class<T> serviceClass, final String remote) {
        if (serviceClass == null) return null;
        if (!Service.class.isAssignableFrom(serviceClass)) return null;
        int mod = serviceClass.getModifiers();
        if (!java.lang.reflect.Modifier.isPublic(mod)) return null;
        if (java.lang.reflect.Modifier.isAbstract(mod)) return null;
        final String supDynName = serviceClass.getName().replace('.', '/');
        final String clientName = SncpClient.class.getName().replace('.', '/');
        final String clientDesc = Type.getDescriptor(SncpClient.class);
        final String convertDesc = Type.getDescriptor(BsonConvert.class);
        final String transportDesc = Type.getDescriptor(Transport.class);
        final String anyValueDesc = Type.getDescriptor(AnyValue.class);
        ClassLoader loader = Sncp.class.getClassLoader();
        String newDynName = supDynName.substring(0, supDynName.lastIndexOf('/') + 1) + "DynRemote" + serviceClass.getSimpleName();
        try {
            return (T) Class.forName(newDynName.replace('/', '.')).newInstance();
        } catch (Exception ex) {
        }
        final SncpClient client = new SncpClient(serviceName, serviceClass);
        //------------------------------------------------------------------------------
        ClassWriter cw = new ClassWriter(0);
        FieldVisitor fv;
        DebugMethodVisitor mv;
        AnnotationVisitor av0;

        cw.visit(V1_8, ACC_PUBLIC + ACC_FINAL + ACC_SUPER, newDynName, null, supDynName, null);
        {
            av0 = cw.visitAnnotation(Type.getDescriptor(RemoteOn.class), true);
            av0.visitEnd();
        }
        {
            fv = cw.visitField(ACC_PRIVATE, "convert", convertDesc, null, null);
            av0 = fv.visitAnnotation("Ljavax/annotation/Resource;", true);
            av0.visitEnd();
            fv.visitEnd();
        }
        {
            fv = cw.visitField(ACC_PRIVATE, "transport", transportDesc, null, null);
            av0 = fv.visitAnnotation("Ljavax/annotation/Resource;", true);
            av0.visit("name", remote == null ? "" : remote);
            av0.visitEnd();
            fv.visitEnd();
        }
        {
            fv = cw.visitField(ACC_PUBLIC, "client", clientDesc, null, null);
            fv.visitEnd();
        }
        { //构造函数
            mv = new DebugMethodVisitor(cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null));
            //mv.setDebug(true);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, supDynName, "<init>", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        { //init
            mv = new DebugMethodVisitor(cw.visitMethod(ACC_PUBLIC, "init", "(" + anyValueDesc + ")V", null, null));
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 2);
            mv.visitEnd();
        }
        { //destroy
            mv = new DebugMethodVisitor(cw.visitMethod(ACC_PUBLIC, "destroy", "(" + anyValueDesc + ")V", null, null));
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 2);
            mv.visitEnd();
        }
        int i = -1;
        for (final SncpAction entry : client.actions) {
            final int index = ++i;
            final java.lang.reflect.Method method = entry.method;
            {
                mv = new DebugMethodVisitor(cw.visitMethod(ACC_PUBLIC, method.getName(), Type.getMethodDescriptor(method), null, null));
                //mv.setDebug(true);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, newDynName, "client", clientDesc);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, newDynName, "convert", convertDesc);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, newDynName, "transport", transportDesc);
                if (index <= 5) {
                    mv.visitInsn(ICONST_0 + index);
                } else {
                    mv.visitIntInsn(BIPUSH, index);
                }

                {  //传参数
                    int paramlen = entry.paramTypes.length;
                    if (paramlen <= 5) {
                        mv.visitInsn(ICONST_0 + paramlen);
                    } else {
                        mv.visitIntInsn(BIPUSH, paramlen);
                    }
                    mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
                    java.lang.reflect.Type[] paramtypes = entry.paramTypes;
                    int insn = 0;
                    for (int j = 0; j < paramtypes.length; j++) {
                        final java.lang.reflect.Type pt = paramtypes[j];
                        mv.visitInsn(DUP);
                        insn++;
                        if (j <= 5) {
                            mv.visitInsn(ICONST_0 + j);
                        } else {
                            mv.visitIntInsn(BIPUSH, j);
                        }
                        if (pt instanceof Class && ((Class) pt).isPrimitive()) {
                            if (pt == long.class) {
                                mv.visitVarInsn(LLOAD, insn++);
                            } else if (pt == float.class) {
                                mv.visitVarInsn(FLOAD, insn++);
                            } else if (pt == double.class) {
                                mv.visitVarInsn(DLOAD, insn++);
                            } else {
                                mv.visitVarInsn(ILOAD, insn);
                            }
                            Class bigclaz = java.lang.reflect.Array.get(java.lang.reflect.Array.newInstance((Class) pt, 1), 0).getClass();
                            mv.visitMethodInsn(INVOKESTATIC, bigclaz.getName().replace('.', '/'), "valueOf", "(" + Type.getDescriptor((Class) pt) + ")" + Type.getDescriptor(bigclaz), false);
                        } else {
                            mv.visitVarInsn(ALOAD, insn);
                        }
                        //mv.visitVarInsn(ALOAD, 1);
                        mv.visitInsn(AASTORE);
                    }
                }

                mv.visitMethodInsn(INVOKEVIRTUAL, clientName, "remote", "(" + convertDesc + transportDesc + "I[Ljava/lang/Object;)Ljava/lang/Object;", false);
                //mv.visitMethodInsn(INVOKEVIRTUAL, convertName, "convertFrom", convertFromDesc, false);
                if (method.getGenericReturnType() == void.class) {
                    mv.visitInsn(POP);
                    mv.visitInsn(RETURN);
                } else {
                    Class returnclz = method.getReturnType();
                    Class bigPrimitiveClass = returnclz.isPrimitive() ? java.lang.reflect.Array.get(java.lang.reflect.Array.newInstance(returnclz, 1), 0).getClass() : returnclz;
                    mv.visitTypeInsn(CHECKCAST, (returnclz.isPrimitive() ? bigPrimitiveClass : returnclz).getName().replace('.', '/'));
                    if (returnclz.isPrimitive()) {
                        String bigPrimitiveName = bigPrimitiveClass.getName().replace('.', '/');
                        try {
                            java.lang.reflect.Method pm = bigPrimitiveClass.getMethod(returnclz.getSimpleName() + "Value");
                            mv.visitMethodInsn(INVOKEVIRTUAL, bigPrimitiveName, pm.getName(), Type.getMethodDescriptor(pm), false);
                        } catch (Exception ex) {
                            throw new RuntimeException(ex); //不可能会发生
                        }
                        if (returnclz == long.class) {
                            mv.visitInsn(LRETURN);
                        } else if (returnclz == float.class) {
                            mv.visitInsn(FRETURN);
                        } else if (returnclz == double.class) {
                            mv.visitInsn(DRETURN);
                        } else {
                            mv.visitInsn(IRETURN);
                        }
                    } else {
                        mv.visitInsn(ARETURN);
                    }
                }
                mv.visitMaxs(20, 20);
                mv.visitEnd();
            }
        }
        cw.visitEnd();
        byte[] bytes = cw.toByteArray();
        Class<?> newClazz = new ClassLoader(loader) {
            public final Class<?> loadClass(String name, byte[] b) {
                return defineClass(name, b, 0, b.length);
            }
        }.loadClass(newDynName.replace('/', '.'), bytes);
        try {
            T rs = (T) newClazz.newInstance();
            newClazz.getField("client").set(rs, client);
            return rs;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

    }
}
