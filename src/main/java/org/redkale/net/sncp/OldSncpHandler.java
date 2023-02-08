/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.sncp;

import java.nio.channels.CompletionHandler;
import java.util.concurrent.CompletableFuture;
import java.util.logging.*;
import org.redkale.annotation.ConstructorParameters;
import org.redkale.asm.*;
import static org.redkale.asm.Opcodes.*;
import org.redkale.convert.bson.*;
import org.redkale.net.sncp.OldSncpDynServlet.SncpServletAction;
import org.redkale.util.*;

/**
 * 异步回调函数  <br>
 *
 * public class _DyncSncpAsyncHandler extends XXXAsyncHandler implements SncpAsyncHandler
 *
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <V> 结果对象的泛型
 * @param <A> 附件对象的泛型
 */
public interface OldSncpHandler<V, A> extends CompletionHandler<V, A> {

    public Object[] sncp_getParams();

    public void sncp_setParams(Object... params);

    public void sncp_setFuture(CompletableFuture future);

    public CompletableFuture sncp_getFuture();

    static class Factory {

        /**
         * <blockquote><pre>

 考虑点：
      1、CompletionHandler子类是接口，且还有其他多个方法
      2、CompletionHandler子类是类， 需要继承，且必须有空参数构造函数
      3、CompletionHandler子类无论是接口还是类，都可能存在其他泛型

  public class XXXAsyncHandler_DynSncpAsyncHandler extends XXXAsyncHandler implements OldSncpHandler {

      private OldSncpHandler sncphandler;

      private CompletableFuture sncpfuture;

      &#64;ConstructorParameters({"sncphandler"})
      public XXXAsyncHandler_DynSncpAsyncHandler(OldSncpHandler sncphandler) {
          super();
          this.sncphandler = sncphandler;
      }

      &#64;Override
         *      public void completed(Object result, Object attachment) {
         *          sncphandler.completed(result, attachment);
         *      }
         *
         *      &#64;Override
         *      public void failed(Throwable exc, Object attachment) {
         *          sncphandler.failed(exc, attachment);
         *      }
         *
         *      &#64;Override
         *      public Object[] sncp_getParams() {
         *          return sncphandler.sncp_getParams();
         *      }
         *
         *      &#64;Override
         *      public void sncp_setParams(Object... params) {
         *          sncphandler.sncp_setParams(params);
         *      }
         *
         *      &#64;Override
         *      public void sncp_setFuture(CompletableFuture future) {
         *          this.sncpfuture = future;
         *      }
         *
         *      &#64;Override
         *      public CompletableFuture sncp_getFuture() {
         *          return this.sncpfuture;
         *      }
         *  }
         *
         * </pre></blockquote>
         *
         * @param handlerClass CompletionHandler类型或子类
         *
         * @return Creator
         */
        public static Creator<OldSncpHandler> createCreator(Class<? extends CompletionHandler> handlerClass) {
            //------------------------------------------------------------- 
            final boolean handlerinterface = handlerClass.isInterface();
            final String handlerClassName = handlerClass.getName().replace('.', '/');
            final String sncpHandlerName = OldSncpHandler.class.getName().replace('.', '/');
            final String cpDesc = Type.getDescriptor(ConstructorParameters.class);
            final String sncpHandlerDesc = Type.getDescriptor(OldSncpHandler.class);
            final String sncpFutureDesc = Type.getDescriptor(CompletableFuture.class);
            final String newDynName = "org/redkaledyn/sncp/handler/_Dyn" + OldSncpHandler.class.getSimpleName()
                + "__" + handlerClass.getName().replace('.', '/').replace('$', '_');
            try {
                Class clz = RedkaleClassLoader.findDynClass(newDynName.replace('/', '.'));
                Class newHandlerClazz = clz == null ? Thread.currentThread().getContextClassLoader().loadClass(newDynName.replace('/', '.')) : clz;
                return Creator.create(newHandlerClazz);
            } catch (Throwable ex) {
            }
            // ------------------------------------------------------------------------------
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            FieldVisitor fv;
            MethodDebugVisitor mv;
            AnnotationVisitor av0;
            cw.visit(V11, ACC_PUBLIC + ACC_FINAL + ACC_SUPER, newDynName, null, handlerinterface ? "java/lang/Object" : handlerClassName, handlerinterface ? new String[]{handlerClassName, sncpHandlerName} : new String[]{sncpHandlerName});

            { //handler 属性
                fv = cw.visitField(ACC_PRIVATE, "sncphandler", sncpHandlerDesc, null, null);
                fv.visitEnd();
            }
            { //future 属性
                fv = cw.visitField(ACC_PRIVATE, "sncpfuture", sncpFutureDesc, null, null);
                fv.visitEnd();
            }
            {//构造方法
                mv = new MethodDebugVisitor(cw.visitMethod(ACC_PUBLIC, "<init>", "(" + sncpHandlerDesc + ")V", null, null));
                //mv.setDebug(true);
                {
                    av0 = mv.visitAnnotation(cpDesc, true);
                    {
                        AnnotationVisitor av1 = av0.visitArray("value");
                        av1.visit(null, "sncphandler");
                        av1.visitEnd();
                    }
                    av0.visitEnd();
                }
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, handlerinterface ? "java/lang/Object" : handlerClassName, "<init>", "()V", false);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitFieldInsn(PUTFIELD, newDynName, "sncphandler", sncpHandlerDesc);
                mv.visitInsn(RETURN);
                mv.visitMaxs(2, 2);
                mv.visitEnd();
            }

            for (java.lang.reflect.Method method : handlerClass.getMethods()) { //
                if ("completed".equals(method.getName()) && method.getParameterCount() == 2) {
                    mv = new MethodDebugVisitor(cw.visitMethod(ACC_PUBLIC, "completed", Type.getMethodDescriptor(method), null, null));
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, newDynName, "sncphandler", sncpHandlerDesc);
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitMethodInsn(INVOKEINTERFACE, sncpHandlerName, "completed", "(Ljava/lang/Object;Ljava/lang/Object;)V", true);
                    mv.visitInsn(RETURN);
                    mv.visitMaxs(3, 3);
                    mv.visitEnd();
                } else if ("failed".equals(method.getName()) && method.getParameterCount() == 2) {
                    mv = new MethodDebugVisitor(cw.visitMethod(ACC_PUBLIC, "failed", Type.getMethodDescriptor(method), null, null));
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, newDynName, "sncphandler", sncpHandlerDesc);
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitMethodInsn(INVOKEINTERFACE, sncpHandlerName, "failed", "(Ljava/lang/Throwable;Ljava/lang/Object;)V", true);
                    mv.visitInsn(RETURN);
                    mv.visitMaxs(3, 3);
                    mv.visitEnd();
                } else if (handlerinterface || java.lang.reflect.Modifier.isAbstract(method.getModifiers())) {
                    mv = new MethodDebugVisitor(cw.visitMethod(ACC_PUBLIC, method.getName(), Type.getMethodDescriptor(method), null, null));
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
            { // sncp_getParams
                mv = new MethodDebugVisitor(cw.visitMethod(ACC_PUBLIC, "sncp_getParams", "()[Ljava/lang/Object;", null, null));
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, newDynName, "sncphandler", sncpHandlerDesc);
                mv.visitMethodInsn(INVOKEINTERFACE, sncpHandlerName, "sncp_getParams", "()[Ljava/lang/Object;", true);
                mv.visitInsn(ARETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            {  // sncp_setParams
                mv = new MethodDebugVisitor(cw.visitMethod(ACC_PUBLIC + ACC_VARARGS, "sncp_setParams", "([Ljava/lang/Object;)V", null, null));
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, newDynName, "sncphandler", sncpHandlerDesc);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitMethodInsn(INVOKEINTERFACE, sncpHandlerName, "sncp_setParams", "([Ljava/lang/Object;)V", true);
                mv.visitInsn(RETURN);
                mv.visitMaxs(2, 2);
                mv.visitEnd();
            }
            {  // sncp_setFuture
                mv = new MethodDebugVisitor(cw.visitMethod(ACC_PUBLIC, "sncp_setFuture", "(" + sncpFutureDesc + ")V", null, null));
                mv.visitVarInsn(ALOAD, 0);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitFieldInsn(PUTFIELD, newDynName, "sncpfuture", sncpFutureDesc);
                mv.visitInsn(RETURN);
                mv.visitMaxs(2, 2);
                mv.visitEnd();
            }
            { // sncp_getFuture
                mv = new MethodDebugVisitor(cw.visitMethod(ACC_PUBLIC, "sncp_getFuture", "()" + sncpFutureDesc, null, null));
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, newDynName, "sncpfuture", sncpFutureDesc);
                mv.visitInsn(ARETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            cw.visitEnd();
            byte[] bytes = cw.toByteArray();
            Class<OldSncpHandler> newClazz = (Class<OldSncpHandler>) new ClassLoader(handlerClass.getClassLoader()) {
                public final Class<?> loadClass(String name, byte[] b) {
                    return defineClass(name, b, 0, b.length);
                }
            }.loadClass(newDynName.replace('/', '.'), bytes);
            RedkaleClassLoader.putDynClass(newDynName.replace('/', '.'), bytes, newClazz);
            return Creator.create(newClazz);
        }

    }

    public static class DefaultSncpAsyncHandler<V, A> implements OldSncpHandler<V, A> {

        //为了在回调函数中调用_callParameter方法
        protected Object[] params;

        protected SncpServletAction action;

        protected BsonReader in;

        protected BsonWriter out;

        protected SncpRequest request;

        protected SncpResponse response;

        protected CompletableFuture future;

        protected Logger logger;

        public DefaultSncpAsyncHandler(Logger logger, SncpServletAction action, BsonReader in, BsonWriter out, SncpRequest request, SncpResponse response) {
            this.logger = logger;
            this.action = action;
            this.in = in;
            this.out = out;
            this.request = request;
            this.response = response;
        }

        @Override
        public void completed(Object result, Object attachment) {
            try {
                action._callParameter(out, sncp_getParams());
                action.convert.convertTo(out, Object.class, result);
                response.finish(0, out);
            } catch (Exception e) {
                failed(e, attachment);
            } finally {
                action.convert.offerBsonReader(in);
                action.convert.offerBsonWriter(out);
            }
        }

        @Override
        public void failed(Throwable exc, Object attachment) {
            response.getContext().getLogger().log(Level.INFO, "Sncp execute error(" + request + ")", exc);
            response.finish(SncpResponse.RETCODE_THROWEXCEPTION, null);
        }

        @Override
        public Object[] sncp_getParams() {
            return params;
        }

        @Override
        public void sncp_setParams(Object... params) {
            this.params = params;
        }

        @Override
        public void sncp_setFuture(CompletableFuture future) {
            this.future = future;
        }

        @Override
        public CompletableFuture sncp_getFuture() {
            return this.future;
        }

    }
}
