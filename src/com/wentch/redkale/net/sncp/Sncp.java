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
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.function.*;
import javax.annotation.*;
import jdk.internal.org.objectweb.asm.*;
import static jdk.internal.org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static jdk.internal.org.objectweb.asm.Opcodes.*;
import jdk.internal.org.objectweb.asm.Type;

/**
 * 生成Service的本地模式或远程模式Service-Class的工具类
 *
 * @author zhangjx
 */
public abstract class Sncp {

    //当前SNCP Server的IP地址+端口 类型: SocketAddress、InetSocketAddress、String
    public static final String RESNAME_SNCP_ADDR = "SNCP_ADDR";

    //当前Service所属的组  类型: Set<String>、String[]
    public static final String RESNAME_SNCP_GROUPS = "SNCP_GROUPS";

    private static final java.lang.reflect.Type GROUPS_TYPE1 = new TypeToken<Set<String>>() {
    }.getType();

    private static final java.lang.reflect.Type GROUPS_TYPE2 = new TypeToken<String[]>() {
    }.getType();

    static final String LOCALPREFIX = "_DynLocal";

    static final String REMOTEPREFIX = "_DynRemote";

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

    public static long nodeid(InetSocketAddress ip) {
        byte[] bytes = ip.getAddress().getAddress();
        return ((0L + ip.getPort()) << 32) | ((0xffffffff & bytes[0]) << 24) | ((0xffffff & bytes[1]) << 16) | ((0xffff & bytes[2]) << 8) | (0xff & bytes[3]);
    }

    public static long hash(final Class clazz) {
        if (clazz == null) return Long.MIN_VALUE;
        long rs = hash(clazz.getSimpleName());
        return (rs < Integer.MAX_VALUE) ? rs | 0xF00000000L : rs;
    }

    public static long hashClass(final String clazzName) {
        if (clazzName == null || clazzName.isEmpty()) return Long.MIN_VALUE;
        long rs = hash(clazzName.substring(clazzName.lastIndexOf('.') + 1));
        return (rs < Integer.MAX_VALUE) ? rs | 0xF00000000L : rs;
    }

    public static DLong hash(final java.lang.reflect.Method method) {
        if (method == null) return new DLong(-1L, -1L);
        String n = method.getName();
        if (n.length() > 11) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < n.length(); i += 2) {
                sb.append(n.charAt(i));
            }
            sb.append(n.length());
            n = sb.toString();
        }
        long rs1 = hash(n);
        if (rs1 < Integer.MAX_VALUE) rs1 |= (method.getParameterCount() + 0L) << 32;
        rs1 = (rs1 < Integer.MAX_VALUE) ? rs1 | 0xF00000000L : rs1;

        long rs2 = hash(wrapName(method), true);
        if (rs2 < Integer.MAX_VALUE) rs2 |= (method.getParameterCount() + 0L) << 32;
        rs2 = (rs2 < Integer.MAX_VALUE) ? rs2 | 0xF00000000L : rs2;
        return new DLong(rs1, rs2);
    }

    private static String wrapName(final java.lang.reflect.Method method) {
        final Class[] params = method.getParameterTypes();
        if (params.length == 0) return method.getName() + "00";
        StringBuilder sb = new StringBuilder();
        for (Class clzz : params) {
            String s = clzz.getSimpleName();
            sb.append(s.substring(0, s.length() > 1 ? 2 : 1)).append(s.substring(s.length() - 1));
        }
        String n = method.getName();
        if (n.length() > 11) {
            StringBuilder zsb = new StringBuilder();
            for (int i = 0; i < n.length(); i += 2) {
                zsb.append(n.charAt(i));
            }
            zsb.append(n.length());
            n = zsb.toString();
        }
        return n + sb + Integer.toString(params.length, 36);
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

    public static boolean isRemote(Service service) {
        return service.getClass().getName().startsWith(REMOTEPREFIX);
    }

    /**
     * public class TestService implements Service{
     *
     * public String queryNode(){
     * return "hello";
     * }
     *
     * @MultiRun
     * public String updateSomeThing(String id){
     * return "hello" + id;
     * }
     *
     * @MultiRun(selfrun = false)
     * public void createSomeThing(TestBean bean){
     * "xxxxx" + bean;
     * }
     * }
     *
     * public final class _DynLocalTestService extends TestService{
     *
     * @Resource
     * private BsonConvert _convert;
     *
     * private Transport[] _sameGroupTransports;
     *
     * private Transport[] _diffGroupTransports;
     *
     * private SncpClient _client;
     *
     * private String _selfstring;
     *
     * @Override
     * public final String name() {
     * return "";
     * }
     *
     * @Override
     * public String toString() {
     * return _selfstring == null ? super.toString() : _selfstring;
     * }
     *
     * @Override
     * public String updateSomeThing(String id){
     * return _updateSomeThing(true, true, true, id);
     * }
     *
     * public String _updateSomeThing(boolean canselfrun, boolean cansamerun, boolean candiffrun, String id){
     * String rs = super.updateSomeThing(id);
     * if (_client== null) return;
     * _client.remote(_convert, _sameGroupTransports, cansamerun, 0, true, false, false, id);
     * _client.remote(_convert, _diffGroupTransports, candiffrun, 0, true, true, false, id);
     * return rs;
     * }
     *
     * @Override
     * public void createSomeThing(TestBean bean){
     * _createSomeThing(false, true, true, bean);
     * }
     *
     * public void _createSomeThing(boolean canselfrun, boolean cansamerun, boolean candiffrun, TestBean bean){
     * if(canselfrun) super.createSomeThing(bean);
     * if (_client== null) return;
     * _client.remote(_convert, _sameGroupTransports, cansamerun, 1, true, false, false, bean);
     * _client.remote(_convert, _diffGroupTransports, candiffrun, 1, true, true, false, bean);
     * }
     * }
     *
     * 创建Service的本地模式Class
     * @param <T>
     * @param name
     * @param serviceClass
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <T extends Service> Class<? extends T> createLocalServiceClass(final String name, final Class<T> serviceClass) {
        if (serviceClass == null) return null;
        if (!Service.class.isAssignableFrom(serviceClass)) return serviceClass;
        int mod = serviceClass.getModifiers();
        if (!java.lang.reflect.Modifier.isPublic(mod)) return serviceClass;
        if (java.lang.reflect.Modifier.isAbstract(mod)) return serviceClass;
        final List<Method> methods = SncpClient.parseMethod(serviceClass, false);
        boolean hasMultiRun0 = false;
        for (Method method : methods) {
            if (method.getAnnotation(MultiRun.class) != null) {
                hasMultiRun0 = true;
                break;
            }
        }
        final boolean hasMultiRun = hasMultiRun0;
        final String supDynName = serviceClass.getName().replace('.', '/');
        final String clientName = SncpClient.class.getName().replace('.', '/');
        final String clientDesc = Type.getDescriptor(SncpClient.class);
        final String convertDesc = Type.getDescriptor(BsonConvert.class);
        final String sncpDynDesc = Type.getDescriptor(SncpDyn.class);
        final String transportsDesc = Type.getDescriptor(Transport[].class);
        ClassLoader loader = Sncp.class.getClassLoader();
        String newDynName = supDynName.substring(0, supDynName.lastIndexOf('/') + 1) + LOCALPREFIX + serviceClass.getSimpleName();
        try {
            return (Class<T>) Class.forName(newDynName.replace('/', '.'));
        } catch (Exception ex) {
        }
        //------------------------------------------------------------------------------
        ClassWriter cw = new ClassWriter(COMPUTE_FRAMES);
        FieldVisitor fv;
        DebugMethodVisitor mv;
        AnnotationVisitor av0;

        cw.visit(V1_8, ACC_PUBLIC + ACC_FINAL + ACC_SUPER, newDynName, null, supDynName, null);
        {
            av0 = cw.visitAnnotation(sncpDynDesc, true);
            av0.visitEnd();
        }
        if (hasMultiRun) {
            {
                fv = cw.visitField(ACC_PRIVATE, "_convert", convertDesc, null, null);
                av0 = fv.visitAnnotation("Ljavax/annotation/Resource;", true);
                av0.visitEnd();
                fv.visitEnd();
            }
            {
                fv = cw.visitField(ACC_PRIVATE, "_sameGroupTransports", transportsDesc, null, null);
                fv.visitEnd();
            }
            {
                fv = cw.visitField(ACC_PRIVATE, "_diffGroupTransports", transportsDesc, null, null);
                fv.visitEnd();
            }
            {
                fv = cw.visitField(ACC_PRIVATE, "_client", clientDesc, null, null);
                fv.visitEnd();
            }
        }
        {
            fv = cw.visitField(ACC_PRIVATE, "_selfstring", "Ljava/lang/String;", null, null);
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
        { // name()
            mv = new DebugMethodVisitor(cw.visitMethod(ACC_PUBLIC + ACC_FINAL, "name", "()Ljava/lang/String;", null, null));
            mv.visitLdcInsn(name);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        { // toString()
            mv = new DebugMethodVisitor(cw.visitMethod(ACC_PUBLIC, "toString", "()Ljava/lang/String;", null, null));
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, newDynName, "_selfstring", "Ljava/lang/String;");
            Label l1 = new Label();
            mv.visitJumpInsn(IFNONNULL, l1);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "toString", "()Ljava/lang/String;", false);
            Label l2 = new Label();
            mv.visitJumpInsn(GOTO, l2);
            mv.visitLabel(l1);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, newDynName, "_selfstring", "Ljava/lang/String;");
            mv.visitLabel(l2);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        int i = - 1;
        for (final Method method : methods) {
            final MultiRun mrun = method.getAnnotation(MultiRun.class);
            if (mrun == null) continue;
            final Class returnType = method.getReturnType();
            final String methodDesc = Type.getMethodDescriptor(method);
            final Class[] paramtypes = method.getParameterTypes();
            final int index = ++i;
            {   //原始方法
                mv = new DebugMethodVisitor(cw.visitMethod(ACC_PUBLIC + (method.isVarArgs() ? ACC_VARARGS : 0), method.getName(), methodDesc, null, null));
                //mv.setDebug(true);
                { //给参数加上 Annotation
                    final Annotation[][] anns = method.getParameterAnnotations();
                    for (int k = 0; k < anns.length; k++) {
                        for (Annotation ann : anns[k]) {
                            visitAnnotation(mv.visitParameterAnnotation(k, Type.getDescriptor(ann.annotationType()), true), ann);
                        }
                    }
                }
                mv.visitVarInsn(ALOAD, 0);
                mv.visitInsn(mrun.selfrun() ? ICONST_1 : ICONST_0);
                mv.visitInsn(mrun.samerun() ? ICONST_1 : ICONST_0);
                mv.visitInsn(mrun.diffrun() ? ICONST_1 : ICONST_0);
                int varindex = 0;
                for (Class pt : paramtypes) {
                    if (pt.isPrimitive()) {
                        if (pt == long.class) {
                            mv.visitVarInsn(LLOAD, ++varindex);
                            ++varindex;
                        } else if (pt == double.class) {
                            mv.visitVarInsn(DLOAD, ++varindex);
                            ++varindex;
                        } else if (pt == float.class) {
                            mv.visitVarInsn(FLOAD, ++varindex);
                        } else {
                            mv.visitVarInsn(ILOAD, ++varindex);
                        }
                    } else {
                        mv.visitVarInsn(ALOAD, ++varindex);
                    }
                }
                mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, "_" + method.getName(), "(ZZZ" + methodDesc.substring(1), false);
                if (returnType == void.class) {
                    mv.visitInsn(RETURN);
                } else if (returnType.isPrimitive()) {
                    if (returnType == long.class) {
                        mv.visitInsn(LRETURN);
                    } else if (returnType == float.class) {
                        mv.visitInsn(FRETURN);
                    } else if (returnType == double.class) {
                        mv.visitInsn(DRETURN);
                    } else {
                        mv.visitInsn(IRETURN);
                    }
                } else {
                    mv.visitInsn(ARETURN);
                }
                mv.visitMaxs(varindex + 3, varindex + 1);
                mv.visitEnd();
            }
            {  // _方法
                mv = new DebugMethodVisitor(cw.visitMethod(ACC_PUBLIC + (method.isVarArgs() ? ACC_VARARGS : 0), "_" + method.getName(), "(ZZZ" + methodDesc.substring(1), null, null));
                //mv.setDebug(true);  
                { //给参数加上 Annotation
                    final Annotation[][] anns = method.getParameterAnnotations();
                    for (int k = 0; k < anns.length; k++) {
                        for (Annotation ann : anns[k]) {
                            visitAnnotation(mv.visitParameterAnnotation(k, Type.getDescriptor(ann.annotationType()), true), ann);
                        }
                    }
                }
                av0 = mv.visitAnnotation(sncpDynDesc, true);
                av0.visit("index", index);
                av0.visitEnd();
                Label l1 = new Label();
                if (returnType == void.class) {  // if
                    mv.visitVarInsn(ILOAD, 1);
                    mv.visitJumpInsn(IFEQ, l1);
                }
                mv.visitVarInsn(ALOAD, 0);
                int varindex = 3;
                for (Class pt : paramtypes) {
                    if (pt.isPrimitive()) {
                        if (pt == long.class) {
                            mv.visitVarInsn(LLOAD, ++varindex);
                            ++varindex;
                        } else if (pt == double.class) {
                            mv.visitVarInsn(DLOAD, ++varindex);
                            ++varindex;
                        } else if (pt == float.class) {
                            mv.visitVarInsn(FLOAD, ++varindex);
                        } else {
                            mv.visitVarInsn(ILOAD, ++varindex);
                        }
                    } else {
                        mv.visitVarInsn(ALOAD, ++varindex);
                    }
                }
                mv.visitMethodInsn(INVOKESPECIAL, supDynName, method.getName(), methodDesc, false);
                if (returnType == void.class) {  // end if
                    mv.visitLabel(l1);
                }
                if (returnType == void.class) {
                } else if (returnType.isPrimitive()) {
                    if (returnType == long.class) {
                        mv.visitVarInsn(LSTORE, ++varindex);
                        ++varindex; //多加1
                    } else if (returnType == float.class) {
                        mv.visitVarInsn(FSTORE, ++varindex);
                    } else if (returnType == double.class) {
                        mv.visitVarInsn(DSTORE, ++varindex);
                        ++varindex; //多加1
                    } else {
                        mv.visitVarInsn(ISTORE, ++varindex);
                    }
                } else {
                    mv.visitVarInsn(ASTORE, ++varindex);
                }
                final int rsindex = varindex;  //

                //---------------------------if (_client== null)  return ----------------------------------
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, newDynName, "_client", clientDesc);
                Label ifrt = new Label();
                mv.visitJumpInsn(IFNONNULL, ifrt);
                if (returnType == void.class) {
                    mv.visitInsn(RETURN);
                } else if (returnType.isPrimitive()) {
                    if (returnType == long.class) {
                        mv.visitVarInsn(LLOAD, rsindex);
                        mv.visitInsn(LRETURN);
                    } else if (returnType == float.class) {
                        mv.visitVarInsn(FLOAD, rsindex);
                        mv.visitInsn(FRETURN);
                    } else if (returnType == double.class) {
                        mv.visitVarInsn(DLOAD, rsindex);
                        mv.visitInsn(DRETURN);
                    } else {
                        mv.visitVarInsn(ILOAD, rsindex);
                        mv.visitInsn(IRETURN);
                    }
                } else {
                    mv.visitVarInsn(ALOAD, rsindex);
                    mv.visitInsn(ARETURN);
                }
                mv.visitLabel(ifrt);
                //-------------------------------------------------------------
                mv.visitVarInsn(ALOAD, 0);//调用 _client
                mv.visitFieldInsn(GETFIELD, newDynName, "_client", clientDesc);
                mv.visitVarInsn(ALOAD, 0);  //传递 _convert
                mv.visitFieldInsn(GETFIELD, newDynName, "_convert", convertDesc);
                mv.visitVarInsn(ALOAD, 0);  //传递 _sameGroupTransports
                mv.visitFieldInsn(GETFIELD, newDynName, "_sameGroupTransports", transportsDesc);

                mv.visitVarInsn(ILOAD, 2);   //传递 cansamerun

                if (index <= 5) {  //第几个 SncpAction 
                    mv.visitInsn(ICONST_0 + index);
                } else {
                    mv.visitIntInsn(BIPUSH, index);
                }
                if (paramtypes.length + 3 <= 5) {  //参数总数量
                    mv.visitInsn(ICONST_0 + paramtypes.length + 3);
                } else {
                    mv.visitIntInsn(BIPUSH, paramtypes.length + 3);
                }

                mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");

                mv.visitInsn(DUP);
                mv.visitInsn(ICONST_0);
                mv.visitInsn(ICONST_1);   //第一个参数  canselfrun
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                mv.visitInsn(AASTORE);

                mv.visitInsn(DUP);
                mv.visitInsn(ICONST_1);
                mv.visitInsn(ICONST_0);   //第一个参数  cansamerun
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                mv.visitInsn(AASTORE);

                mv.visitInsn(DUP);
                mv.visitInsn(ICONST_2);
                mv.visitInsn(ICONST_0);   //第二个参数  candiffrun
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                mv.visitInsn(AASTORE);

                int insn = 3;
                for (int j = 0; j < paramtypes.length; j++) {
                    final Class pt = paramtypes[j];
                    mv.visitInsn(DUP);
                    insn++;
                    if (j <= 2) {
                        mv.visitInsn(ICONST_0 + j + 3);
                    } else {
                        mv.visitIntInsn(BIPUSH, j + 3);
                    }
                    if (pt.isPrimitive()) {
                        if (pt == long.class) {
                            mv.visitVarInsn(LLOAD, insn++);
                        } else if (pt == float.class) {
                            mv.visitVarInsn(FLOAD, insn++);
                        } else if (pt == double.class) {
                            mv.visitVarInsn(DLOAD, insn++);
                        } else {
                            mv.visitVarInsn(ILOAD, insn);
                        }
                        Class bigclaz = java.lang.reflect.Array.get(java.lang.reflect.Array.newInstance(pt, 1), 0).getClass();
                        mv.visitMethodInsn(INVOKESTATIC, bigclaz.getName().replace('.', '/'), "valueOf", "(" + Type.getDescriptor(pt) + ")" + Type.getDescriptor(bigclaz), false);
                    } else {
                        mv.visitVarInsn(ALOAD, insn);
                    }
                    mv.visitInsn(AASTORE);
                }
                mv.visitMethodInsn(INVOKEVIRTUAL, clientName, mrun.async() ? "asyncRemote" : "remote", "(" + convertDesc + transportsDesc + "ZI[Ljava/lang/Object;)V", false);

                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, newDynName, "_client", clientDesc);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, newDynName, "_convert", convertDesc);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, newDynName, "_diffGroupTransports", transportsDesc);

                mv.visitVarInsn(ILOAD, 3);   //传递 candiffrun
                if (index <= 5) {  //第几个 SncpAction 
                    mv.visitInsn(ICONST_0 + index);
                } else {
                    mv.visitIntInsn(BIPUSH, index);
                }
                if (paramtypes.length + 3 <= 5) {  //参数总数量
                    mv.visitInsn(ICONST_0 + paramtypes.length + 3);
                } else {
                    mv.visitIntInsn(BIPUSH, paramtypes.length + 3);
                }

                mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");

                mv.visitInsn(DUP);
                mv.visitInsn(ICONST_0);
                mv.visitInsn(ICONST_1);   //第一个参数  cansamerun
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                mv.visitInsn(AASTORE);

                mv.visitInsn(DUP);
                mv.visitInsn(ICONST_1);
                mv.visitInsn(ICONST_1);   //第二个参数  candiffrun
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                mv.visitInsn(AASTORE);

                mv.visitInsn(DUP);
                mv.visitInsn(ICONST_2);
                mv.visitInsn(ICONST_0);   //第二个参数  candiffrun
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                mv.visitInsn(AASTORE);

                insn = 3;
                for (int j = 0; j < paramtypes.length; j++) {
                    final Class pt = paramtypes[j];
                    mv.visitInsn(DUP);
                    insn++;
                    if (j <= 2) {
                        mv.visitInsn(ICONST_0 + j + 3);
                    } else {
                        mv.visitIntInsn(BIPUSH, j + 3);
                    }
                    if (pt.isPrimitive()) {
                        if (pt == long.class) {
                            mv.visitVarInsn(LLOAD, insn++);
                        } else if (pt == float.class) {
                            mv.visitVarInsn(FLOAD, insn++);
                        } else if (pt == double.class) {
                            mv.visitVarInsn(DLOAD, insn++);
                        } else {
                            mv.visitVarInsn(ILOAD, insn);
                        }
                        Class bigclaz = java.lang.reflect.Array.get(java.lang.reflect.Array.newInstance(pt, 1), 0).getClass();
                        mv.visitMethodInsn(INVOKESTATIC, bigclaz.getName().replace('.', '/'), "valueOf", "(" + Type.getDescriptor(pt) + ")" + Type.getDescriptor(bigclaz), false);
                    } else {
                        mv.visitVarInsn(ALOAD, insn);
                    }
                    mv.visitInsn(AASTORE);
                }
                mv.visitMethodInsn(INVOKEVIRTUAL, clientName, mrun.async() ? "asyncRemote" : "remote", "(" + convertDesc + transportsDesc + "ZI[Ljava/lang/Object;)V", false);

                if (returnType == void.class) {
                    mv.visitInsn(RETURN);
                } else if (returnType.isPrimitive()) {
                    if (returnType == long.class) {
                        mv.visitVarInsn(LLOAD, rsindex);
                        mv.visitInsn(LRETURN);
                    } else if (returnType == float.class) {
                        mv.visitVarInsn(FLOAD, rsindex);
                        mv.visitInsn(FRETURN);
                    } else if (returnType == double.class) {
                        mv.visitVarInsn(DLOAD, rsindex);
                        mv.visitInsn(DRETURN);
                    } else {
                        mv.visitVarInsn(ILOAD, rsindex);
                        mv.visitInsn(IRETURN);
                    }
                } else {
                    mv.visitVarInsn(ALOAD, rsindex);
                    mv.visitInsn(ARETURN);
                }

                mv.visitMaxs(Math.max(varindex, 10), varindex + 4);
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
        return (Class<T>) newClazz;
    }

    private static void visitAnnotation(final AnnotationVisitor av, final Annotation ann) {
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

    /**
     *
     * 创建本地模式Service实例
     *
     * @param <T>
     * @param name
     * @param executor
     * @param serviceClass
     * @param clientAddress
     * @param groups
     * @param sameGroupTransports
     * @param diffGroupTransports
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <T extends Service> T createLocalService(
            final String name,
            final Consumer<Runnable> executor,
            final Class<T> serviceClass,
            final InetSocketAddress clientAddress, HashSet<String> groups, Collection<Transport> sameGroupTransports, Collection<Transport> diffGroupTransports
    ) {
        try {
            final Class newClazz = createLocalServiceClass(name, serviceClass);
            T rs = (T) newClazz.newInstance();
            //--------------------------------------            
            if (sameGroupTransports == null) sameGroupTransports = new ArrayList<>();
            if (diffGroupTransports == null) diffGroupTransports = new ArrayList<>();
            Transport remoteTransport = null;
            {
                Class loop = newClazz;
                String[] groupArray = null;
                do {
                    for (Field field : loop.getDeclaredFields()) {
                        int mod = field.getModifiers();
                        if (Modifier.isFinal(mod) || Modifier.isStatic(mod)) continue;
                        if (field.getAnnotation(SncpRemote.class) != null) {
                            field.setAccessible(true);
                            if (remoteTransport == null) {
                                List<Transport> list = new ArrayList<>();
                                list.addAll(sameGroupTransports);
                                list.addAll(diffGroupTransports);
                                if (!list.isEmpty()) remoteTransport = new Transport(list.get(0), clientAddress, list);
                            }
                            if (field.getType().isAssignableFrom(newClazz) && remoteTransport != null) {
                                field.set(rs, createRemoteService(name, executor, serviceClass, clientAddress, groups, remoteTransport));
                            }
                            continue;
                        }
                        Resource res = field.getAnnotation(Resource.class);
                        if (res == null) continue;
                        field.setAccessible(true);
                        if (res.name().equals(RESNAME_SNCP_GROUPS)) {
                            if (groups == null) groups = new LinkedHashSet<>();
                            if (groupArray == null) groupArray = groups.toArray(new String[groups.size()]);
                            if (field.getGenericType().equals(GROUPS_TYPE1)) {
                                field.set(rs, groups);
                            } else if (field.getGenericType().equals(GROUPS_TYPE2)) {
                                field.set(rs, groupArray);
                            }
                        } else if (res.name().endsWith(RESNAME_SNCP_ADDR)) {
                            if (field.getType() == String.class) {
                                field.set(rs, clientAddress == null ? null : (clientAddress.getHostString() + ":" + clientAddress.getPort()));
                            } else {
                                field.set(rs, clientAddress);
                            }
                        }
                    }
                } while ((loop = loop.getSuperclass()) != Object.class);
            }
            SncpClient client = null;
            {
                try {
                    Field e = newClazz.getDeclaredField("_client");
                    e.setAccessible(true);
                    client = new SncpClient(name, executor, hash(serviceClass), false, newClazz, true, clientAddress, groups);
                    e.set(rs, client);
                } catch (NoSuchFieldException ne) {
                }
            }
            {
                StringBuilder sb = new StringBuilder();
                sb.append(newClazz.getName()).append("{name = ").append(name);
                if (client != null) {
                    sb.append(", nameid = ").append(client.getNameid()).append(", serviceid = ").append(client.getServiceid());
                    sb.append(", action.size = ").append(client.getActionCount());

                    sb.append(", address = ").append(clientAddress).append(", groups = ").append(groups);
                    List<InetSocketAddress> addrs = new ArrayList<>();
                    for (Transport t : sameGroupTransports) {
                        addrs.addAll(Arrays.asList(t.getRemoteAddress()));
                    }
                    sb.append(", samegroups = ").append(addrs);

                    addrs.clear();
                    for (Transport t : diffGroupTransports) {
                        addrs.addAll(Arrays.asList(t.getRemoteAddress()));
                    }
                    sb.append(", diffgroups = ").append(addrs);
                } else {
                    sb.append(", ").append(MultiRun.class.getSimpleName().toLowerCase()).append(" = false");
                }
                sb.append("}");
                Field s = newClazz.getDeclaredField("_selfstring");
                s.setAccessible(true);
                s.set(rs, sb.toString());
            }
            if (client == null) return rs;
            {
                Field c = newClazz.getDeclaredField("_sameGroupTransports");
                c.setAccessible(true);
                c.set(rs, sameGroupTransports.toArray(new Transport[sameGroupTransports.size()]));
            }
            {
                Field t = newClazz.getDeclaredField("_diffGroupTransports");
                t.setAccessible(true);
                t.set(rs, diffGroupTransports.toArray(new Transport[diffGroupTransports.size()]));
            }
            return rs;
        } catch (RuntimeException rex) {
            throw rex;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

    }

    /**
     * public final class _DynRemoteTestService extends TestService{
     *
     * @Resource
     * private BsonConvert _convert;
     *
     * private Transport _transport;
     *
     * private SncpClient _client;
     *
     * private String _selfstring;
     *
     * @Override
     * public final String name() {
     * return "";
     * }
     *
     * @Override
     * public String toString() {
     * return _selfstring == null ? super.toString() : _selfstring;
     * }
     *
     * @Override
     * public boolean testChange(TestBean bean) {
     * return _client.remote(_convert, _transport, 0, bean);
     * }
     *
     * @Override
     * public TestBean findTestBean(long id) {
     * return _client.remote(_convert, _transport, 1, id);
     * }
     *
     * @Override
     * public void runTestBean(long id, TestBean bean) {
     * _client.remote(_convert, _transport, 2, id, bean);
     * }
     * }
     *
     * 创建远程模式的Service实例
     * <p>
     * @param <T>
     * @param name
     * @param executor
     * @param serviceClass
     * @param clientAddress
     * @param groups
     * @param transport
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <T extends Service> T createRemoteService(
            final String name,
            final Consumer<Runnable> executor,
            final Class<T> serviceClass,
            final InetSocketAddress clientAddress, HashSet<String> groups, final Transport transport
    ) {
        if (serviceClass == null) return null;
        if (!Service.class.isAssignableFrom(serviceClass)) return null;
        int mod = serviceClass.getModifiers();
        if (!java.lang.reflect.Modifier.isPublic(mod)) return null;
        if (java.lang.reflect.Modifier.isAbstract(mod)) return null;
        final String supDynName = serviceClass.getName().replace('.', '/');
        final String clientName = SncpClient.class.getName().replace('.', '/');
        final String clientDesc = Type.getDescriptor(SncpClient.class);
        final String sncpDynDesc = Type.getDescriptor(SncpDyn.class);
        final String convertDesc = Type.getDescriptor(BsonConvert.class);
        final String transportDesc = Type.getDescriptor(Transport.class);
        final String anyValueDesc = Type.getDescriptor(AnyValue.class);
        ClassLoader loader = Sncp.class.getClassLoader();
        String newDynName = supDynName.substring(0, supDynName.lastIndexOf('/') + 1) + REMOTEPREFIX + serviceClass.getSimpleName();
        final SncpClient client = new SncpClient(name, executor, hash(serviceClass), true, createLocalServiceClass(name, serviceClass), false, clientAddress, groups);
        try {
            Class newClazz = Class.forName(newDynName.replace('/', '.'));
            T rs = (T) newClazz.newInstance();
            Field c = newClazz.getDeclaredField("_client");
            c.setAccessible(true);
            c.set(rs, client);
            Field t = newClazz.getDeclaredField("_transport");
            t.setAccessible(true);
            t.set(rs, transport);
            {
                StringBuilder sb = new StringBuilder();
                sb.append(newClazz.getName()).append("{name = ").append(name);
                sb.append(", nameid = ").append(client.getNameid()).append(", serviceid = ").append(client.getServiceid());
                sb.append(", action.size = ").append(client.getActionCount());
                sb.append(", address = ").append(clientAddress).append(", groups = ").append(groups);
                sb.append(", remotes = ").append(transport == null ? null : Arrays.asList(transport.getRemoteAddress()));
                sb.append("}");
                Field s = newClazz.getDeclaredField("_selfstring");
                s.setAccessible(true);
                s.set(rs, sb.toString());
            }
            return rs;
        } catch (Exception ex) {
        }
        //------------------------------------------------------------------------------
        ClassWriter cw = new ClassWriter(COMPUTE_FRAMES);
        FieldVisitor fv;
        DebugMethodVisitor mv;
        AnnotationVisitor av0;

        cw.visit(V1_8, ACC_PUBLIC + ACC_FINAL + ACC_SUPER, newDynName, null, supDynName, null);
        {
            av0 = cw.visitAnnotation(sncpDynDesc, true);
            av0.visitEnd();
        }
        {
            fv = cw.visitField(ACC_PRIVATE, "_convert", convertDesc, null, null);
            av0 = fv.visitAnnotation("Ljavax/annotation/Resource;", true);
            av0.visitEnd();
            fv.visitEnd();
        }
        {
            fv = cw.visitField(ACC_PRIVATE, "_transport", transportDesc, null, null);
            fv.visitEnd();
        }
        {
            fv = cw.visitField(ACC_PRIVATE, "_client", clientDesc, null, null);
            fv.visitEnd();
        }
        {
            fv = cw.visitField(ACC_PRIVATE, "_selfstring", "Ljava/lang/String;", null, null);
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
        { // name()
            mv = new DebugMethodVisitor(cw.visitMethod(ACC_PUBLIC + ACC_FINAL, "name", "()Ljava/lang/String;", null, null));
            mv.visitLdcInsn(name);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        { // toString()
            mv = new DebugMethodVisitor(cw.visitMethod(ACC_PUBLIC, "toString", "()Ljava/lang/String;", null, null));
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, newDynName, "_selfstring", "Ljava/lang/String;");
            Label l1 = new Label();
            mv.visitJumpInsn(IFNONNULL, l1);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "toString", "()Ljava/lang/String;", false);
            Label l2 = new Label();
            mv.visitJumpInsn(GOTO, l2);
            mv.visitLabel(l1);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, newDynName, "_selfstring", "Ljava/lang/String;");
            mv.visitLabel(l2);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(1, 1);
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
                mv.visitFieldInsn(GETFIELD, newDynName, "_client", clientDesc);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, newDynName, "_convert", convertDesc);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, newDynName, "_transport", transportDesc);
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
            Field c = newClazz.getDeclaredField("_client");
            c.setAccessible(true);
            c.set(rs, client);
            Field t = newClazz.getDeclaredField("_transport");
            t.setAccessible(true);
            t.set(rs, transport);
            {
                StringBuilder sb = new StringBuilder();
                sb.append(newClazz.getName()).append("{name = ").append(name);
                sb.append(", nameid = ").append(client.getNameid()).append(", serviceid = ").append(client.getServiceid());
                sb.append(", action.size = ").append(client.getActionCount());
                sb.append(", address = ").append(clientAddress).append(", groups = ").append(groups);
                sb.append(", remotes = ").append(transport == null ? null : Arrays.asList(transport.getRemoteAddress()));
                sb.append("}");
                Field s = newClazz.getDeclaredField("_selfstring");
                s.setAccessible(true);
                s.set(rs, sb.toString());
            }
            return rs;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

    }
}
