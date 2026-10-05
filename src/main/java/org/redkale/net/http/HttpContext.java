/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import static java.lang.classfile.ClassFile.*;
import static java.lang.constant.ConstantDescs.*;

import java.lang.classfile.*;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.constant.*;
import java.lang.invoke.MethodType;
import java.nio.channels.CompletionHandler;
import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;
import org.redkale.annotation.ConstructorParameters;
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
        final ClassDesc cpDesc = ClassDesc.ofDescriptor(ConstructorParameters.class.descriptorString());
        final ClassDesc handlerClassDesc = ClassDesc.ofDescriptor(handlerClass.descriptorString());
        final ClassDesc handlerDesc = ClassDesc.ofDescriptor(CompletionHandler.class.descriptorString());
        final String newDynName = "org/redkaledyn/http/handler/_DynHttpAsyncHandler__"
                + handlerClass.getName().replace('.', '/').replace('$', '_');
        RedkaleClassLoader classLoader = RedkaleClassLoader.currentClassLoader();
        try {
            return (Creator<H>) Creator.create(classLoader.loadClass(newDynName.replace('/', '.')));
        } catch (Throwable ex) {
            // do nothing
        }
        // ------------------------------------------------------------------------------
        final ClassDesc dynDesc = ClassDesc.ofInternalName(newDynName);
        final ClassDesc superDesc = handlerinterface ? CD_Object : handlerClassDesc;
        byte[] bytes = ClassFile.of().build(dynDesc, cb -> {
            cb.withVersion(JAVA_11_VERSION, 0)
                    .withFlags(ACC_PUBLIC | ACC_FINAL | ACC_SUPER)
                    .withSuperclass(superDesc)
                    .withInterfaceSymbols(handlerinterface ? handlerClassDesc : handlerDesc);
            cb.withField("handler", handlerDesc, ACC_PRIVATE);
            cb.withMethod("<init>", MethodTypeDesc.of(CD_void, handlerDesc), ACC_PUBLIC, mb -> {
                mb.with(RuntimeVisibleAnnotationsAttribute.of(Annotation.of(
                        cpDesc, AnnotationElement.ofArray("value", AnnotationValue.ofString("handler")))));
                mb.withCode(code -> {
                    code.aload(0)
                            .invokespecial(superDesc, "<init>", MethodTypeDesc.of(CD_void))
                            .aload(0)
                            .aload(1)
                            .putfield(dynDesc, "handler", handlerDesc)
                            .return_();
                    code.localVariable(0, "this", dynDesc, code.startLabel(), code.endLabel());
                    code.localVariable(1, "handler", handlerDesc, code.startLabel(), code.endLabel());
                });
            });
            for (java.lang.reflect.Method method : handlerClass.getMethods()) {
                final MethodTypeDesc methodDesc = MethodTypeDesc.ofDescriptor(MethodType.methodType(
                                method.getReturnType(), method.getParameterTypes())
                        .descriptorString());
                if (("completed".equals(method.getName()) || "failed".equals(method.getName()))
                        && method.getParameterCount() == 2) {
                    cb.withMethodBody(method.getName(), methodDesc, ACC_PUBLIC, code -> code.aload(0)
                            .getfield(dynDesc, "handler", handlerDesc)
                            .aload(1)
                            .aload(2)
                            .invokeinterface(
                                    handlerDesc,
                                    method.getName(),
                                    MethodTypeDesc.of(
                                            CD_void,
                                            "completed".equals(method.getName()) ? CD_Object : CD_Throwable,
                                            CD_Object))
                            .return_());
                } else if (handlerinterface || java.lang.reflect.Modifier.isAbstract(method.getModifiers())) {
                    cb.withMethodBody(method.getName(), methodDesc, ACC_PUBLIC, code -> {
                        Class returnType = method.getReturnType();
                        if (returnType == void.class) {
                            code.return_();
                        } else if (returnType == long.class) {
                            code.lconst_0().lreturn();
                        } else if (returnType == float.class) {
                            code.fconst_0().freturn();
                        } else if (returnType == double.class) {
                            code.dconst_0().dreturn();
                        } else if (returnType.isPrimitive()) {
                            code.iconst_0().ireturn();
                        } else {
                            code.aconst_null().areturn();
                        }
                    });
                }
            }
        });
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
