/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.sncp;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.net.InetSocketAddress;
import java.security.*;
import java.util.*;
import java.util.function.Consumer;
import javax.annotation.Resource;
import static jdk.internal.org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import jdk.internal.org.objectweb.asm.*;
import static jdk.internal.org.objectweb.asm.Opcodes.*;
import jdk.internal.org.objectweb.asm.Type;
import org.redkale.convert.bson.BsonConvert;
import org.redkale.net.Transport;
import org.redkale.net.sncp.SncpClient.SncpAction;
import org.redkale.service.*;
import org.redkale.util.*;

/**
 * Service Node Communicate Protocol
 * 生成Service的本地模式或远程模式Service-Class的工具类
 * 
 * 
 * 详情见: http://redkale.org
 *
 * @author zhangjx
 */
public abstract class Sncp {

    static final String FIELDPREFIX = "_redkale";

    static final String LOCALPREFIX = "_DynLocal";

    static final String REMOTEPREFIX = "_DynRemote";

    private static final MessageDigest md5;

    static {  //64进制
        MessageDigest d = null;
        try {
            d = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException ex) {
            ex.printStackTrace();
        }
        md5 = d;
    }

    private Sncp() {
    }

    public static long nodeid(InetSocketAddress ip) {
        byte[] bytes = ip.getAddress().getAddress();
        return ((0L + ip.getPort()) << 32) | ((0xffffffff & bytes[0]) << 24) | ((0xffffff & bytes[1]) << 16) | ((0xffff & bytes[2]) << 8) | (0xff & bytes[3]);
    }

    public static DLong hash(final java.lang.reflect.Method method) {
        if (method == null) return DLong.ZERO;
        StringBuilder sb = new StringBuilder(); //不能使用method.toString() 因为包含declaringClass信息导致接口与实现类的方法hash不一致
        sb.append(method.getReturnType().getName()).append(' ');
        sb.append(method.getName());
        sb.append('(');
        boolean first = true;
        for (Class pt : method.getParameterTypes()) {
            if (!first) sb.append(',');
            sb.append(pt.getName());
            first = false;
        }
        sb.append(')');
        return hash(sb.toString());
    }

    /**
     * 对类名或者name字符串进行hash。
     *
     * @param name String
     * @return hash值
     */
    public static DLong hash(final String name) {
        if (name == null || name.isEmpty()) return DLong.ZERO;
        byte[] bytes = name.trim().getBytes();
        synchronized (md5) {
            bytes = md5.digest(bytes);
        }
        return DLong.create(bytes);
    }

    public static boolean isRemote(Service service) {
        SncpDyn dyn = service.getClass().getAnnotation(SncpDyn.class);
        return dyn != null && dyn.remote();
    }

    public static SncpClient getSncpClient(Service service) {
        if (service == null) return null;
        try {
            Field ts = service.getClass().getDeclaredField(FIELDPREFIX + "_client");
            ts.setAccessible(true);
            return (SncpClient) ts.get(service);
        } catch (Exception e) {
            throw new RuntimeException(service + " not found " + FIELDPREFIX + "_client");
        }
    }

    public static Transport getSameGroupTransport(Service service) {
        if (service == null) return null;
        try {
            Field ts = service.getClass().getDeclaredField(FIELDPREFIX + "_sameGroupTransport");
            ts.setAccessible(true);
            return (Transport) ts.get(service);
        } catch (Exception e) {
            throw new RuntimeException(service + " not found " + FIELDPREFIX + "_sameGroupTransport");
        }
    }

    public static Transport[] getDiffGroupTransports(Service service) {
        if (service == null) return null;
        try {
            Field ts = service.getClass().getDeclaredField(FIELDPREFIX + "_diffGroupTransports");
            ts.setAccessible(true);
            return (Transport[]) ts.get(service);
        } catch (Exception e) {
            throw new RuntimeException(service + " not found " + FIELDPREFIX + "_diffGroupTransports");
        }
    }

    /**
     * <blockquote><pre>
     * public class TestService implements Service{
     * 
     *      public String findSomeThing(){
     *          return "hello";
     *      }
     * 
     *      &#64;MultiRun(selfrun = false)
     *      public void createSomeThing(TestBean bean){
     *          //do something
     *      }
     * 
     *      &#64;MultiRun
     *      public String updateSomeThing(String id){
     *          return "hello" + id;
     *      }
     * }
     * </pre></blockquote>
     * 
     * <blockquote><pre>
     * &#64;Resource(name = "")
     * &#64;SncpDyn(remote = false)
     * &#64;ResourceType({TestService.class})
     * public final class _DynLocalTestService extends TestService{
     * 
     *      &#64;Resource
     *      private BsonConvert _redkale_convert;
     * 
     *      private Transport _redkale_sameGroupTransport;
     * 
     *      private Transport[] _redkale_diffGroupTransports;
     * 
     *      private SncpClient _redkale_client;
     * 
     *      private String _redkale_selfstring;
     * 
     *      &#64;Override
     *      public String toString() {
     *          return _redkale_selfstring == null ? super.toString() : _redkale_selfstring;
     *      }
     * 
     *      &#64;Override
     *      public void createSomeThing(TestBean bean){
     *          this._redkale_createSomeThing(false, true, true, bean);
     *      }
     * 
     *      &#64;SncpDyn(remote = false, index = 0)
     *      public void _redkale_createSomeThing(boolean selfrunnable, boolean samerunnable, boolean diffrunnable, TestBean bean){
     *          if(selfrunnable) super.createSomeThing(bean);
     *          if (_redkale_client== null) return;
     *          if (samerunnable) _redkale_client.remoteSameGroup(_redkale_convert, _sameGroupTransport, 0, true, false, false, bean);
     *          if (diffrunnable) _redkale_client.remoteDiffGroup(_redkale_convert, _diffGroupTransports, 0, true, true, false, bean);
     *      }
     * 
     *      &#64;Override
     *      public String updateSomeThing(String id){
     *          return this._redkale_updateSomeThing(true, true, true, id);
     *      }
     * 
     *      &#64;SncpDyn(remote = false, index = 1)
     *      public String _redkale_updateSomeThing(boolean selfrunnable, boolean samerunnable, boolean diffrunnable, String id){
     *          String rs = super.updateSomeThing(id);
     *          if (_redkale_client== null) return;
     *          if (samerunnable) _redkale_client.remoteSameGroup(_redkale_convert, _sameGroupTransport, 1, true, false, false, id);
     *          if (diffrunnable) _redkale_client.remoteDiffGroup(_redkale_convert, _diffGroupTransports, 1, true, true, false, id);
     *          return rs;
     *      }
     * }
     * </pre></blockquote>
     * 
     * 创建Service的本地模式Class
     *
     * @param <T>          Service子类
     * @param name         资源名
     * @param serviceClass Service类
     * @return Service实例
     */
    @SuppressWarnings("unchecked")
    protected static <T extends Service> Class<? extends T> createLocalServiceClass(final String name, final Class<T> serviceClass) {
        if (serviceClass == null) return null;
        if (!Service.class.isAssignableFrom(serviceClass)) return serviceClass;
        int mod = serviceClass.getModifiers();
        if (!java.lang.reflect.Modifier.isPublic(mod)) return serviceClass;
        if (java.lang.reflect.Modifier.isAbstract(mod)) return serviceClass;
        final List<Method> methods = SncpClient.parseMethod(serviceClass);
        final String supDynName = serviceClass.getName().replace('.', '/');
        final String clientName = SncpClient.class.getName().replace('.', '/');
        final String clientDesc = Type.getDescriptor(SncpClient.class);
        final String convertDesc = Type.getDescriptor(BsonConvert.class);
        final String sncpDynDesc = Type.getDescriptor(SncpDyn.class);
        final String transportDesc = Type.getDescriptor(Transport.class);
        final String transportsDesc = Type.getDescriptor(Transport[].class);
        ClassLoader loader = Sncp.class.getClassLoader();
        String newDynName = supDynName.substring(0, supDynName.lastIndexOf('/') + 1) + LOCALPREFIX + serviceClass.getSimpleName();
        if (!name.isEmpty()) {
            boolean normal = true;
            for (char ch : name.toCharArray()) {
                if (!((ch >= '0' && ch <= '9') || ch == '_' || (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z'))) normal = false;
            }
            newDynName += "_" + (normal ? name : hash(name));
        }
        try {
            return (Class<T>) Class.forName(newDynName.replace('/', '.'));
        } catch (Exception ex) {
        }
        //------------------------------------------------------------------------------
        ClassWriter cw = new ClassWriter(COMPUTE_FRAMES);
        FieldVisitor fv;
        AsmMethodVisitor mv;
        AnnotationVisitor av0;

        cw.visit(V1_8, ACC_PUBLIC + ACC_FINAL + ACC_SUPER, newDynName, null, supDynName, null);
        {
            av0 = cw.visitAnnotation("Ljavax/annotation/Resource;", true);
            av0.visit("name", name);
            av0.visitEnd();
        }
        {
            av0 = cw.visitAnnotation(sncpDynDesc, true);
            av0.visit("remote", Boolean.FALSE);
            av0.visitEnd();
        }
        { //给新类加上 原有的Annotation
            for (Annotation ann : serviceClass.getAnnotations()) {
                if (ann instanceof Resource || ann instanceof SncpDyn || ann instanceof ResourceType) continue;
                visitAnnotation(cw.visitAnnotation(Type.getDescriptor(ann.annotationType()), true), ann);
            }
        }
        {
            av0 = cw.visitAnnotation(Type.getDescriptor(ResourceType.class), true);
            {
                AnnotationVisitor av1 = av0.visitArray("value");
                ResourceType rty = serviceClass.getAnnotation(ResourceType.class);
                if (rty == null) {
                    av1.visit(null, Type.getType(Type.getDescriptor(serviceClass)));
                } else {
                    for (Class cl : rty.value()) {
                        av1.visit(null, Type.getType(Type.getDescriptor(cl)));
                    }
                }
                av1.visitEnd();
            }
            av0.visitEnd();
        }

        {
            fv = cw.visitField(ACC_PRIVATE, FIELDPREFIX + "_convert", convertDesc, null, null);
            av0 = fv.visitAnnotation("Ljavax/annotation/Resource;", true);
            av0.visitEnd();
            fv.visitEnd();
        }
        {
            fv = cw.visitField(ACC_PRIVATE, FIELDPREFIX + "_sameGroupTransport", transportDesc, null, null);
            fv.visitEnd();
        }
        {
            fv = cw.visitField(ACC_PRIVATE, FIELDPREFIX + "_diffGroupTransports", transportsDesc, null, null);
            fv.visitEnd();
        }
        {
            fv = cw.visitField(ACC_PRIVATE, FIELDPREFIX + "_client", clientDesc, null, null);
            fv.visitEnd();
        }

        {
            fv = cw.visitField(ACC_PRIVATE, FIELDPREFIX + "_selfstring", "Ljava/lang/String;", null, null);
            fv.visitEnd();
        }
        { //构造函数
            mv = new AsmMethodVisitor(cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null));
            //mv.setDebug(true);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, supDynName, "<init>", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        { // toString()
            mv = new AsmMethodVisitor(cw.visitMethod(ACC_PUBLIC, "toString", "()Ljava/lang/String;", null, null));
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, newDynName, FIELDPREFIX + "_selfstring", "Ljava/lang/String;");
            Label l1 = new Label();
            mv.visitJumpInsn(IFNONNULL, l1);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "toString", "()Ljava/lang/String;", false);
            Label l2 = new Label();
            mv.visitJumpInsn(GOTO, l2);
            mv.visitLabel(l1);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, newDynName, FIELDPREFIX + "_selfstring", "Ljava/lang/String;");
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
                mv = new AsmMethodVisitor(cw.visitMethod(ACC_PUBLIC + (method.isVarArgs() ? ACC_VARARGS : 0), method.getName(), methodDesc, null, null));
                //mv.setDebug(true);
                { //给参数加上 Annotation
                    final Annotation[][] anns = method.getParameterAnnotations();
                    for (int k = 0; k < anns.length; k++) {
                        for (Annotation ann : anns[k]) {
                            if (ann instanceof SncpDyn || ann instanceof MultiRun) continue; //必须过滤掉 MultiRun、SncpDyn，否则生成远程模式Service时会出错
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
                mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, FIELDPREFIX + "_" + method.getName(), "(ZZZ" + methodDesc.substring(1), false);
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
            {  // _方法   _方法比无_方法多了三个参数
                mv = new AsmMethodVisitor(cw.visitMethod(ACC_PUBLIC + (method.isVarArgs() ? ACC_VARARGS : 0), FIELDPREFIX + "_" + method.getName(), "(ZZZ" + methodDesc.substring(1), null, null));
                //mv.setDebug(true);  
                { //给参数加上 Annotation
                    final Annotation[][] anns = method.getParameterAnnotations();
                    for (int k = 0; k < anns.length; k++) {
                        for (Annotation ann : anns[k]) {
                            if (ann instanceof SncpDyn || ann instanceof MultiRun) continue; //必须过滤掉 MultiRun、SncpDyn，否则生成远程模式Service时会出错
                            visitAnnotation(mv.visitParameterAnnotation(k, Type.getDescriptor(ann.annotationType()), true), ann);
                        }
                    }
                }
                av0 = mv.visitAnnotation(sncpDynDesc, true);
                av0.visit("remote", Boolean.FALSE);
                av0.visit("index", index);
                av0.visitEnd();
                //---------------------------- 调用selfrun ---------------------------------
                Label selfLabel = new Label();
                if (returnType == void.class) {  // if
                    mv.visitVarInsn(ILOAD, 1);
                    mv.visitJumpInsn(IFEQ, selfLabel);
                }
                mv.visitVarInsn(ALOAD, 0);
                int varindex = 3; //空3给selfrunnable、samerunnable、diffrunnable
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
                    mv.visitLabel(selfLabel);
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

                //---------------------------if (_redkale_client== null)  return ----------------------------------
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, newDynName, FIELDPREFIX + "_client", clientDesc);
                Label clientLabel = new Label();
                mv.visitJumpInsn(IFNONNULL, clientLabel);
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
                mv.visitLabel(clientLabel);
                //---------------------------- 调用samerun ---------------------------------
                mv.visitVarInsn(ILOAD, 2); //读取 samerunnable
                Label sameLabel = new Label();
                mv.visitJumpInsn(IFEQ, sameLabel);  //判断 samerunnable

                mv.visitVarInsn(ALOAD, 0);//调用 _client
                mv.visitFieldInsn(GETFIELD, newDynName, FIELDPREFIX + "_client", clientDesc);
                mv.visitVarInsn(ALOAD, 0);  //传递 _convert
                mv.visitFieldInsn(GETFIELD, newDynName, FIELDPREFIX + "_convert", convertDesc);
                mv.visitVarInsn(ALOAD, 0);  //传递 _sameGroupTransport
                mv.visitFieldInsn(GETFIELD, newDynName, FIELDPREFIX + "_sameGroupTransport", transportDesc);

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
                mv.visitInsn(ICONST_1);   //第一个参数  selfrunnable
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                mv.visitInsn(AASTORE);

                mv.visitInsn(DUP);
                mv.visitInsn(ICONST_1);
                mv.visitInsn(ICONST_0);   //第一个参数  samerunnable
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                mv.visitInsn(AASTORE);

                mv.visitInsn(DUP);
                mv.visitInsn(ICONST_2);
                mv.visitInsn(ICONST_0);   //第二个参数  diffrunnable
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
                mv.visitMethodInsn(INVOKEVIRTUAL, clientName, mrun.async() ? "asyncRemoteSameGroup" : "remoteSameGroup", "(" + convertDesc + transportDesc + "I[Ljava/lang/Object;)V", false);
                mv.visitLabel(sameLabel);
                //---------------------------- 调用diffrun ---------------------------------
                mv.visitVarInsn(ILOAD, 3); //读取 diffrunnable
                Label diffLabel = new Label();
                mv.visitJumpInsn(IFEQ, diffLabel);  //判断 diffrunnable

                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, newDynName, FIELDPREFIX + "_client", clientDesc);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, newDynName, FIELDPREFIX + "_convert", convertDesc);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, newDynName, FIELDPREFIX + "_diffGroupTransports", transportsDesc);

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
                mv.visitInsn(ICONST_1);   //第一个参数  samerunnable
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                mv.visitInsn(AASTORE);

                mv.visitInsn(DUP);
                mv.visitInsn(ICONST_1);
                mv.visitInsn(ICONST_1);   //第二个参数  diffrunnable
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                mv.visitInsn(AASTORE);

                mv.visitInsn(DUP);
                mv.visitInsn(ICONST_2);
                mv.visitInsn(ICONST_0);   //第二个参数  diffrunnable
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
                mv.visitMethodInsn(INVOKEVIRTUAL, clientName, mrun.async() ? "asyncRemoteDiffGroup" : "remoteDiffGroup", "(" + convertDesc + transportsDesc + "I[Ljava/lang/Object;)V", false);
                mv.visitLabel(diffLabel);

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
     * @param <T>                 Service泛型
     * @param name                资源名
     * @param executor            线程池
     * @param resourceFactory     资源容器
     * @param serviceClass        Service类
     * @param clientAddress       本地IP地址
     * @param sameGroupTransport  同组的通信组件
     * @param diffGroupTransports 异组的通信组件列表
     * @return Service的本地模式实例
     */
    @SuppressWarnings("unchecked")
    public static <T extends Service> T createLocalService(final String name, final Consumer<Runnable> executor, final ResourceFactory resourceFactory,
                                                           final Class<T> serviceClass, final InetSocketAddress clientAddress, final Transport sameGroupTransport, final Collection<Transport> diffGroupTransports) {
        try {
            final Class newClazz = createLocalServiceClass(name, serviceClass);
            T rs = (T) newClazz.newInstance();
            //--------------------------------------            
            Service remoteService = null;
            Transport remoteTransport = null;
            {
                Class loop = newClazz;
                do {
                    for (Field field : loop.getDeclaredFields()) {
                        int mod = field.getModifiers();
                        if (Modifier.isFinal(mod) || Modifier.isStatic(mod)) continue;
                        if (field.getAnnotation(DynRemote.class) == null) continue;
                        if (!field.getType().isAssignableFrom(newClazz)) continue;
                        field.setAccessible(true);
                        if (remoteTransport == null) {
                            List<Transport> list = new ArrayList<>();
                            if (sameGroupTransport != null) list.add(sameGroupTransport);
                            if (diffGroupTransports != null) list.addAll(diffGroupTransports);
                            if (!list.isEmpty()) {
                                Transport tmp = new Transport(list);
                                synchronized (resourceFactory) {
                                    Transport old = resourceFactory.find(tmp.getName(), Transport.class);
                                    if (old != null) {
                                        remoteTransport = old;
                                    } else {
                                        remoteTransport = tmp;
                                        resourceFactory.register(tmp.getName(), tmp);
                                    }
                                }
                            }
                        }
                        if (remoteService == null && remoteTransport != null) {
                            remoteService = createRemoteService(name, executor, serviceClass, clientAddress, remoteTransport);
                        }
                        if (remoteService != null) field.set(rs, remoteService);
                    }
                } while ((loop = loop.getSuperclass()) != Object.class);
            }
            SncpClient client = null;
            {
                try {
                    Field e = newClazz.getDeclaredField(FIELDPREFIX + "_client");
                    e.setAccessible(true);
                    client = new SncpClient(name, serviceClass, executor, false, newClazz, clientAddress);
                    e.set(rs, client);
                } catch (NoSuchFieldException ne) {
                }
            }
            {
                StringBuilder sb = new StringBuilder();
                sb.append(newClazz.getName()).append("{name = '").append(name).append("'");
                if (client != null) {
                    sb.append(", serviceid = ").append(client.getServiceid());
                    sb.append(", action.size = ").append(client.getActionCount());
                    List<String> groups = new ArrayList<>();
                    if (sameGroupTransport != null) groups.add(sameGroupTransport.getName());
                    if (diffGroupTransports != null) {
                        for (Transport t : diffGroupTransports) {
                            groups.add(t.getName());
                        }
                    }
                    sb.append(", address = ").append(clientAddress).append(", groups = ").append(groups);
                    sb.append(", sameaddrs = ").append(sameGroupTransport == null ? null : Arrays.asList(sameGroupTransport.getRemoteAddresses()));

                    List<InetSocketAddress> addrs = new ArrayList<>();
                    if (diffGroupTransports != null) {
                        for (Transport t : diffGroupTransports) {
                            addrs.addAll(Arrays.asList(t.getRemoteAddresses()));
                        }
                    }
                    sb.append(", diffaddrs = ").append(addrs);
                } else {
                    sb.append(", ").append(MultiRun.class.getSimpleName().toLowerCase()).append(" = false");
                }
                sb.append("}");
                Field s = newClazz.getDeclaredField(FIELDPREFIX + "_selfstring");
                s.setAccessible(true);
                s.set(rs, sb.toString());
            }
            if (client == null) return rs;
            {
                Field c = newClazz.getDeclaredField(FIELDPREFIX + "_sameGroupTransport");
                c.setAccessible(true);
                c.set(rs, sameGroupTransport);
            }
            if (diffGroupTransports != null) {
                Field t = newClazz.getDeclaredField(FIELDPREFIX + "_diffGroupTransports");
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
     * <blockquote><pre>
     * &#64;Resource(name = "")
     * &#64;SncpDyn(remote = true)
     * &#64;ResourceType({TestService.class})
     * public final class _DynRemoteTestService extends TestService{
     * 
     *      &#64;Resource
     *      private BsonConvert _redkale_convert;
     * 
     *      private Transport _redkale_transport;
     * 
     *      private SncpClient _redkale_client;
     * 
     *      private String _redkale_selfstring;
     * 
     *      &#64;Override
     *      public String toString() {
     *          return _redkale_selfstring == null ? super.toString() : _redkale_selfstring;
     *      }
     * 
     *      &#64;SncpDyn(remote = false, index = 0)
     *      public void _redkale_createSomeThing(boolean selfrunnable, boolean samerunnable, boolean diffrunnable, TestBean bean){
     *          _redkale_client.remote(_redkale_convert, _redkale_transport, 0, selfrunnable, samerunnable, diffrunnable, bean);
     *      }
     * 
     *      &#64;SncpDyn(remote = false, index = 1)
     *      public String _redkale_updateSomeThing(boolean selfrunnable, boolean samerunnable, boolean diffrunnable, String id){
     *          return _redkale_client.remote(_redkale_convert, _redkale_transport, 1, selfrunnable, samerunnable, diffrunnable, id);
     *      }
     * 
     *      &#64;Override
     *      public void createSomeThing(TestBean bean){
     *          _redkale_client.remote(_redkale_convert, _redkale_transport, 2, bean);
     *      }
     * 
     *      &#64;Override
     *      public String findSomeThing(){
     *          return _redkale_client.remote(_redkale_convert, _redkale_transport, 3);
     *      }
     * 
     *      &#64;Override
     *      public String updateSomeThing(String id){
     *          return  _redkale_client.remote(_redkale_convert, _redkale_transport, 4, id);
     *      }
     * }
     * </pre></blockquote>
     * 
     * 创建远程模式的Service实例
     *
     * @param <T>           Service泛型
     * @param name          资源名
     * @param executor      线程池
     * @param serviceClass  Service类
     * @param clientAddress 本地IP地址
     * @param transport     通信组件
     *
     * @return Service的远程模式实例
     */
    @SuppressWarnings("unchecked")
    public static <T extends Service> T createRemoteService(final String name, final Consumer<Runnable> executor, final Class<T> serviceClass,
                                                            final InetSocketAddress clientAddress, final Transport transport) {
        if (serviceClass == null) return null;
        if (!Service.class.isAssignableFrom(serviceClass)) return null;
        int mod = serviceClass.getModifiers();
        boolean realed = !(java.lang.reflect.Modifier.isAbstract(mod) || serviceClass.isInterface());
        if (!java.lang.reflect.Modifier.isPublic(mod)) return null;
        final String supDynName = serviceClass.getName().replace('.', '/');
        final String clientName = SncpClient.class.getName().replace('.', '/');
        final String clientDesc = Type.getDescriptor(SncpClient.class);
        final String sncpDynDesc = Type.getDescriptor(SncpDyn.class);
        final String convertDesc = Type.getDescriptor(BsonConvert.class);
        final String transportDesc = Type.getDescriptor(Transport.class);
        final String anyValueDesc = Type.getDescriptor(AnyValue.class);
        ClassLoader loader = Sncp.class.getClassLoader();
        String newDynName = supDynName.substring(0, supDynName.lastIndexOf('/') + 1) + REMOTEPREFIX + serviceClass.getSimpleName();
        final SncpClient client = new SncpClient(name, serviceClass, executor, true, realed ? createLocalServiceClass(name, serviceClass) : serviceClass, clientAddress);
        try {
            Class newClazz = Class.forName(newDynName.replace('/', '.'));
            T rs = (T) newClazz.newInstance();
            Field c = newClazz.getDeclaredField(FIELDPREFIX + "_client");
            c.setAccessible(true);
            c.set(rs, client);
            Field t = newClazz.getDeclaredField(FIELDPREFIX + "_transport");
            t.setAccessible(true);
            t.set(rs, transport);
            {
                StringBuilder sb = new StringBuilder();
                sb.append(newClazz.getName()).append("{name = '").append(name);
                sb.append("', serviceid = ").append(client.getServiceid());
                sb.append(", action.size = ").append(client.getActionCount());
                sb.append(", address = ").append(clientAddress).append(", groups = ").append(transport == null ? null : transport.getName());
                sb.append(", remoteaddrs = ").append(transport == null ? null : Arrays.asList(transport.getRemoteAddresses()));
                sb.append("}");
                Field s = newClazz.getDeclaredField(FIELDPREFIX + "_selfstring");
                s.setAccessible(true);
                s.set(rs, sb.toString());
            }
            return rs;
        } catch (Exception ex) {
        }
        //------------------------------------------------------------------------------
        ClassWriter cw = new ClassWriter(COMPUTE_FRAMES);
        FieldVisitor fv;
        AsmMethodVisitor mv;
        AnnotationVisitor av0;

        cw.visit(V1_8, ACC_PUBLIC + ACC_FINAL + ACC_SUPER, newDynName, null, serviceClass.isInterface() ? "java/lang/Object" : supDynName, serviceClass.isInterface() ? new String[]{supDynName} : null);
        {
            av0 = cw.visitAnnotation("Ljavax/annotation/Resource;", true);
            av0.visit("name", name);
            av0.visitEnd();
        }
        {
            av0 = cw.visitAnnotation(Type.getDescriptor(ResourceType.class), true);
            {
                AnnotationVisitor av1 = av0.visitArray("value");
                ResourceType rty = serviceClass.getAnnotation(ResourceType.class);
                if (rty == null) {
                    av1.visit(null, Type.getType(Type.getDescriptor(serviceClass)));
                } else {
                    for (Class cl : rty.value()) {
                        av1.visit(null, Type.getType(Type.getDescriptor(cl)));
                    }
                }
                av1.visitEnd();
            }
            av0.visitEnd();
        }
        {
            av0 = cw.visitAnnotation(sncpDynDesc, true);
            av0.visit("remote", Boolean.TRUE);
            av0.visitEnd();
        }
        { //给新类加上 原有的Annotation
            for (Annotation ann : serviceClass.getAnnotations()) {
                if (ann instanceof Resource || ann instanceof SncpDyn || ann instanceof ResourceType) continue;
                visitAnnotation(cw.visitAnnotation(Type.getDescriptor(ann.annotationType()), true), ann);
            }
        }
        {
            fv = cw.visitField(ACC_PRIVATE, FIELDPREFIX + "_convert", convertDesc, null, null);
            av0 = fv.visitAnnotation("Ljavax/annotation/Resource;", true);
            av0.visitEnd();
            fv.visitEnd();
        }
        {
            fv = cw.visitField(ACC_PRIVATE, FIELDPREFIX + "_transport", transportDesc, null, null);
            fv.visitEnd();
        }
        {
            fv = cw.visitField(ACC_PRIVATE, FIELDPREFIX + "_client", clientDesc, null, null);
            fv.visitEnd();
        }
        {
            fv = cw.visitField(ACC_PRIVATE, FIELDPREFIX + "_selfstring", "Ljava/lang/String;", null, null);
            fv.visitEnd();
        }
        { //构造函数
            mv = new AsmMethodVisitor(cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null));
            //mv.setDebug(true);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, serviceClass.isInterface() ? "java/lang/Object" : supDynName, "<init>", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        { //init
            mv = new AsmMethodVisitor(cw.visitMethod(ACC_PUBLIC, "init", "(" + anyValueDesc + ")V", null, null));
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 2);
            mv.visitEnd();
        }
        { //destroy
            mv = new AsmMethodVisitor(cw.visitMethod(ACC_PUBLIC, "destroy", "(" + anyValueDesc + ")V", null, null));
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 2);
            mv.visitEnd();
        }
        { // toString()
            mv = new AsmMethodVisitor(cw.visitMethod(ACC_PUBLIC, "toString", "()Ljava/lang/String;", null, null));
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, newDynName, FIELDPREFIX + "_selfstring", "Ljava/lang/String;");
            Label l1 = new Label();
            mv.visitJumpInsn(IFNONNULL, l1);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "toString", "()Ljava/lang/String;", false);
            Label l2 = new Label();
            mv.visitJumpInsn(GOTO, l2);
            mv.visitLabel(l1);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, newDynName, FIELDPREFIX + "_selfstring", "Ljava/lang/String;");
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
                mv = new AsmMethodVisitor(cw.visitMethod(ACC_PUBLIC, method.getName(), Type.getMethodDescriptor(method), null, null));
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
                mv.visitFieldInsn(GETFIELD, newDynName, FIELDPREFIX + "_client", clientDesc);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, newDynName, FIELDPREFIX + "_convert", convertDesc);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, newDynName, FIELDPREFIX + "_transport", transportDesc);
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
            Field c = newClazz.getDeclaredField(FIELDPREFIX + "_client");
            c.setAccessible(true);
            c.set(rs, client);
            Field t = newClazz.getDeclaredField(FIELDPREFIX + "_transport");
            t.setAccessible(true);
            t.set(rs, transport);
            {
                StringBuilder sb = new StringBuilder();
                sb.append(newClazz.getName()).append("{name = '").append(name);
                sb.append("', serviceid = ").append(client.getServiceid());
                sb.append(", action.size = ").append(client.getActionCount());
                sb.append(", address = ").append(clientAddress).append(", groups = ").append(transport == null ? null : transport.getName());
                sb.append(", remotes = ").append(transport == null ? null : Arrays.asList(transport.getRemoteAddresses()));
                sb.append("}");
                Field s = newClazz.getDeclaredField(FIELDPREFIX + "_selfstring");
                s.setAccessible(true);
                s.set(rs, sb.toString());
            }
            return rs;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

    }
}
