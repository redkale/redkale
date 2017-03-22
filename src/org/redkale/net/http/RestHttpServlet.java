/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.io.*;
import java.util.concurrent.ConcurrentHashMap;
import jdk.internal.org.objectweb.asm.*;
import static jdk.internal.org.objectweb.asm.Opcodes.*;
import org.redkale.util.*;

/**
 *
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <T> 当前用户对象类型
 */
public abstract class RestHttpServlet<T> extends HttpBaseServlet {

    protected abstract T currentUser(HttpRequest req) throws IOException;

    protected void finishJson(final HttpResponse response, RestOutput output) throws IOException {
        if (output == null) {
            response.finishJson(output);
            return;
        }
        if (output.getContentType() != null) response.setContentType(output.getContentType());
        response.addHeader(output.getHeaders());
        response.addCookie(output.getCookies());

        if (output.getResult() instanceof File) {
            response.finish((File) output.getResult());
        } else if (output.getResult() instanceof String) {
            response.finish((String) output.getResult());
        } else {
            response.finishJson(output.getResult());
        }
    }

    /**
     * 创建AsyncHandler实例，将非字符串对象以JSON格式输出，字符串以文本输出 <br>
     *
     * 传入的AsyncHandler子类必须是public，且保证其子类可被继承且completed、failed可被重载且包含空参数的构造函数。
     *
     * @param <H>          AsyncHandler泛型
     * @param response     HttpResponse
     * @param handlerClass Class
     *
     * @return AsyncHandler
     */
    protected final <H extends AsyncHandler> H createAsyncHandler(HttpResponse response, final Class<H> handlerClass) {
        if (handlerClass == null || handlerClass == AsyncHandler.class) return (H) response.createAsyncHandler();
        Creator<H> creator = creators.get(handlerClass);
        if (creator == null) {
            creator = createCreator(handlerClass);
            creators.put(handlerClass, creator);
        }
        return (H) creator.create(response.createAsyncHandler());
    }

    private static final ConcurrentHashMap<Class, Creator> creators = new ConcurrentHashMap<>();

    private static <H extends AsyncHandler> Creator<H> createCreator(Class<H> handlerClass) {
        //------------------------------------------------------------- 
        final boolean handlerinterface = handlerClass.isInterface();
        final String handlerClassName = handlerClass.getName().replace('.', '/');
        final String handlerName = AsyncHandler.class.getName().replace('.', '/');
        final String handlerDesc = Type.getDescriptor(AsyncHandler.class);
        final String newDynName = handlerClass.getName().replace('.', '/') + "_Dync" + AsyncHandler.class.getSimpleName() + "_" + (System.currentTimeMillis() % 10000);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        FieldVisitor fv;
        AsmMethodVisitor mv;
        AnnotationVisitor av0;
        cw.visit(V1_8, ACC_PUBLIC + ACC_SUPER, newDynName, null, handlerinterface ? "java/lang/Object" : handlerClassName, handlerinterface ? new String[]{handlerClassName} : new String[]{handlerName});

        { //handler 属性
            fv = cw.visitField(ACC_PRIVATE, "handler", handlerDesc, null, null);
            fv.visitEnd();
        }
        {//构造方法
            mv = new AsmMethodVisitor(cw.visitMethod(ACC_PUBLIC, "<init>", "(" + handlerDesc + ")V", null, null));
            //mv.setDebug(true);
            {
                av0 = mv.visitAnnotation("Ljava/beans/ConstructorProperties;", true);
                {
                    AnnotationVisitor av1 = av0.visitArray("value");
                    av1.visit(null, "handler");
                    av1.visitEnd();
                }
                av0.visitEnd();
            }
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, handlerinterface ? "java/lang/Object" : handlerClassName, "<init>", "()V", false);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitFieldInsn(PUTFIELD, newDynName, "handler", handlerDesc);
            mv.visitInsn(RETURN);
            mv.visitMaxs(2, 2);
            mv.visitEnd();
        }

        for (java.lang.reflect.Method method : handlerClass.getMethods()) { //
            if ("completed".equals(method.getName()) && method.getParameterCount() == 2) {
                mv = new AsmMethodVisitor(cw.visitMethod(ACC_PUBLIC, "completed", Type.getMethodDescriptor(method), null, null));
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, newDynName, "handler", handlerDesc);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitVarInsn(ALOAD, 2);
                mv.visitMethodInsn(INVOKEINTERFACE, handlerName, "completed", "(Ljava/lang/Object;Ljava/lang/Object;)V", true);
                mv.visitInsn(RETURN);
                mv.visitMaxs(3, 3);
                mv.visitEnd();
            } else if ("failed".equals(method.getName()) && method.getParameterCount() == 2) {
                mv = new AsmMethodVisitor(cw.visitMethod(ACC_PUBLIC, "failed", Type.getMethodDescriptor(method), null, null));
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, newDynName, "handler", handlerDesc);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitVarInsn(ALOAD, 2);
                mv.visitMethodInsn(INVOKEINTERFACE, handlerName, "failed", "(Ljava/lang/Throwable;Ljava/lang/Object;)V", true);
                mv.visitInsn(RETURN);
                mv.visitMaxs(3, 3);
                mv.visitEnd();
            } else if (handlerinterface || java.lang.reflect.Modifier.isAbstract(method.getModifiers())) {
                mv = new AsmMethodVisitor(cw.visitMethod(ACC_PUBLIC, method.getName(), Type.getMethodDescriptor(method), null, null));
                Class returnType = method.getReturnType();
                if (returnType == void.class) {
                    mv.visitInsn(RETURN);
                    mv.visitMaxs(0, 1);
                } else if (returnType.isPrimitive()) {
                    mv.visitInsn(ICONST_0);
                    if (returnType == long.class) {
                        mv.visitInsn(LRETURN);
                        mv.visitMaxs(2, 1);
                    } else if (returnType == float.class) {
                        mv.visitInsn(FRETURN);
                        mv.visitMaxs(2, 1);
                    } else if (returnType == double.class) {
                        mv.visitInsn(DRETURN);
                        mv.visitMaxs(2, 1);
                    } else {
                        mv.visitInsn(IRETURN);
                        mv.visitMaxs(1, 1);
                    }
                } else {
                    mv.visitInsn(ACONST_NULL);
                    mv.visitInsn(ARETURN);
                    mv.visitMaxs(1, 1);
                }
                mv.visitEnd();
            }
        }
        cw.visitEnd();
        byte[] bytes = cw.toByteArray();
        Class<AsyncHandler> newHandlerClazz = (Class<AsyncHandler>) new ClassLoader(handlerClass.getClassLoader()) {
            public final Class<?> loadClass(String name, byte[] b) {
                return defineClass(name, b, 0, b.length);
            }
        }.loadClass(newDynName.replace('/', '.'), bytes);
        return (Creator<H>) Creator.create(newHandlerClazz);
    }
}
