/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.nio.channels.CompletionHandler;
import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;
import org.redkale.annotation.ConstructorParameters;
import org.redkale.asm.*;
import static org.redkale.asm.Opcodes.*;
import org.redkale.net.*;
import org.redkale.net.Context.ContextConfig;
import org.redkale.util.*;

/**
 * HTTP服务的上下文对象
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class HttpContext extends Context {

    protected final SecureRandom random = new SecureRandom();

    protected final ConcurrentHashMap<Class, Creator> asyncHandlerCreators = new ConcurrentHashMap<>();

    protected final String remoteAddrHeader;

    // 用逗号隔开的多个header
    protected final String[] remoteAddrHeaders;

    protected final String localHeader;

    protected final String localParameter;

    protected final HttpRpcAuthenticator rpcAuthenticator;

    protected final AnyValue rpcAuthenticatorConfig;

    // 延迟解析header
    protected final boolean lazyHeader;

    // pipeline模式下是否相同header
    // deprecated
    final boolean sameHeader;

    // 不带通配符的mapping url的缓存对象
    private final UriPathNode uriPathNode = new UriPathNode();

    public HttpContext(HttpContextConfig config) {
        super(config);
        this.lazyHeader = config.lazyHeader;
        this.sameHeader = config.sameHeader;
        this.remoteAddrHeader = config.remoteAddrHeader;
        this.remoteAddrHeaders = config.remoteAddrHeaders;
        this.localHeader = config.localHeader;
        this.localParameter = config.localParameter;
        this.rpcAuthenticator = config.rpcAuthenticator;
        this.rpcAuthenticatorConfig = config.rpcAuthenticatorConfig;
        random.setSeed(Math.abs(System.nanoTime()));
    }

    UriPathNode getUriPathNode() {
        return uriPathNode;
    }

    void addUriPath(final String path, HttpServlet servlet) {
        this.uriPathNode.put(path, servlet);
    }

    HttpServlet removeUriPath(final String path) {
        return this.uriPathNode.remove(path);
    }

    @Override
    protected void updateReadIOThread(AsyncConnection conn, AsyncIOThread ioReadThread) {
        super.updateReadIOThread(conn, ioReadThread);
    }

    @Override
    protected void updateWriteIOThread(AsyncConnection conn, AsyncIOThread ioWriteThread) {
        super.updateWriteIOThread(conn, ioWriteThread);
    }

    protected String createSessionid() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return new String(Utility.binToHex(bytes));
    }

    @SuppressWarnings("unchecked")
    protected <H extends CompletionHandler> Creator<H> loadAsyncHandlerCreator(Class<H> handlerClass) {
        return asyncHandlerCreators.computeIfAbsent(handlerClass, c -> createAsyncHandlerCreator(c));
    }

    @SuppressWarnings("unchecked")
    private static <H extends CompletionHandler> Creator<H> createAsyncHandlerCreator(Class<H> handlerClass) {
        // 生成规则与SncpAsyncHandler.Factory 很类似
        // -------------------------------------------------------------
        final boolean handlerinterface = handlerClass.isInterface();
        final String cpDesc = Type.getDescriptor(ConstructorParameters.class);
        final String handlerClassName = handlerClass.getName().replace('.', '/');
        final String handlerName = CompletionHandler.class.getName().replace('.', '/');
        final String handlerDesc = Type.getDescriptor(CompletionHandler.class);
        final String newDynName = "org/redkaledyn/http/handler/_DynHttpAsyncHandler__"
                + handlerClass.getName().replace('.', '/').replace('$', '_');
        RedkaleClassLoader classLoader = RedkaleClassLoader.currentClassLoader();
        try {
            return (Creator<H>) Creator.create(classLoader.loadClass(newDynName.replace('/', '.')));
        } catch (Throwable ex) {
            // do nothing
        }
        // ------------------------------------------------------------------------------
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        FieldVisitor fv;
        MethodDebugVisitor mv;
        AnnotationVisitor av0;
        cw.visit(
                V11,
                ACC_PUBLIC + ACC_FINAL + ACC_SUPER,
                newDynName,
                null,
                handlerinterface ? "java/lang/Object" : handlerClassName,
                handlerinterface ? new String[] {handlerClassName} : new String[] {handlerName});

        { // handler 属性
            fv = cw.visitField(ACC_PRIVATE, "handler", handlerDesc, null, null);
            fv.visitEnd();
        }
        { // 构造方法
            mv = new MethodDebugVisitor(cw.visitMethod(ACC_PUBLIC, "<init>", "(" + handlerDesc + ")V", null, null));
            // mv.setDebug(true);
            {
                av0 = mv.visitAnnotation(cpDesc, true);
                {
                    AnnotationVisitor av1 = av0.visitArray("value");
                    av1.visit(null, "handler");
                    av1.visitEnd();
                }
                av0.visitEnd();
            }
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(
                    INVOKESPECIAL, handlerinterface ? "java/lang/Object" : handlerClassName, "<init>", "()V", false);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitFieldInsn(PUTFIELD, newDynName, "handler", handlerDesc);
            mv.visitInsn(RETURN);
            mv.visitMaxs(2, 2);
            mv.visitEnd();
        }

        for (java.lang.reflect.Method method : handlerClass.getMethods()) { //
            if ("completed".equals(method.getName()) && method.getParameterCount() == 2) {
                mv = new MethodDebugVisitor(
                        cw.visitMethod(ACC_PUBLIC, "completed", Type.getMethodDescriptor(method), null, null));
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, newDynName, "handler", handlerDesc);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitVarInsn(ALOAD, 2);
                mv.visitMethodInsn(
                        INVOKEINTERFACE, handlerName, "completed", "(Ljava/lang/Object;Ljava/lang/Object;)V", true);
                mv.visitInsn(RETURN);
                mv.visitMaxs(3, 3);
                mv.visitEnd();
            } else if ("failed".equals(method.getName()) && method.getParameterCount() == 2) {
                mv = new MethodDebugVisitor(
                        cw.visitMethod(ACC_PUBLIC, "failed", Type.getMethodDescriptor(method), null, null));
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, newDynName, "handler", handlerDesc);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitVarInsn(ALOAD, 2);
                mv.visitMethodInsn(
                        INVOKEINTERFACE, handlerName, "failed", "(Ljava/lang/Throwable;Ljava/lang/Object;)V", true);
                mv.visitInsn(RETURN);
                mv.visitMaxs(3, 3);
                mv.visitEnd();
            } else if (handlerinterface || java.lang.reflect.Modifier.isAbstract(method.getModifiers())) {
                mv = new MethodDebugVisitor(
                        cw.visitMethod(ACC_PUBLIC, method.getName(), Type.getMethodDescriptor(method), null, null));
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
        Class<CompletionHandler> newClazz = classLoader.loadClass(newDynName.replace('/', '.'), bytes);
        return (Creator<H>) Creator.create(newClazz);
    }

    protected static class UriPathNode extends ByteTreeNode<HttpServlet> {

        protected UriPathNode() {
            super();
        }

        @Override
        protected ByteTreeNode<HttpServlet> put(String key, HttpServlet servlet) {
            return super.put(key, servlet);
        }

        @Override
        protected HttpServlet remove(String key) {
            return super.remove(key);
        }
    }

    public static class HttpContextConfig extends ContextConfig {

        // 是否延迟解析http-header
        public boolean lazyHeader;

        public boolean sameHeader;

        public String remoteAddrHeader;

        // 用逗号隔开的多个header
        public String[] remoteAddrHeaders;

        public String localHeader;

        public String localParameter;

        public HttpRpcAuthenticator rpcAuthenticator;

        public AnyValue rpcAuthenticatorConfig;
    }
}
