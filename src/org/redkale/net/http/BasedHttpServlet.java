/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import org.redkale.net.Response;
import org.redkale.net.Request;
import org.redkale.net.Context;
import org.redkale.util.AnyValue;
import java.io.IOException;
import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.reflect.Method;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;
import jdk.internal.org.objectweb.asm.*;
import static jdk.internal.org.objectweb.asm.Opcodes.*;

/**
 *
 * <p>
 * 详情见: http://www.redkale.org
 *
 * @author zhangjx
 */
public abstract class BasedHttpServlet extends HttpServlet {

    /**
     * 配合 BasedHttpServlet 使用。
     * 当标记为 &#64;AuthIgnore 的方法不会再调用之前调用authenticate 方法。
     *
     * <p>
     * 详情见: http://www.redkale.org
     *
     * @author zhangjx
     */
    @Inherited
    @Documented
    @Target({METHOD, TYPE})
    @Retention(RUNTIME)
    protected @interface AuthIgnore {

    }

    /**
     * 配合 BasedHttpServlet 使用。
     * 用于对&#64;WebServlet对应的url进行细分。 其 url
     *
     * <p>
     * 详情见: http://www.redkale.org
     *
     * @author zhangjx
     */
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    protected @interface WebAction {

        int actionid() default 0;

        String url();
    }

    /**
     * 配合 BasedHttpServlet 使用。
     * 当标记为 &#64;HttpCacheable 的方法使用response.finish的参数将被缓存一定时间(默认值timeout=15秒)。
     * 通常情况下 &#64;HttpCacheable 需要与 &#64;AuthIgnore 一起使用，因为没有标记&#64;AuthIgnore的方法一般输出的结果与当前用户信息有关。
     *
     * <p>
     * 详情见: http://www.redkale.org
     *
     * @author zhangjx
     */
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    protected @interface HttpCacheable {

        /**
         * 超时的秒数
         *
         * @return 超时秒数
         */
        int timeout() default 15;
    }

    private Map.Entry<String, Entry>[] actions;

    public boolean preExecute(HttpRequest request, HttpResponse response) throws IOException {
        return true;
    }

    @Override
    public final void execute(HttpRequest request, HttpResponse response) throws IOException {
        if (!preExecute(request, response)) return;
        for (Map.Entry<String, Entry> en : actions) {
            if (request.getRequestURI().startsWith(en.getKey())) {
                Entry entry = en.getValue();
                if (entry.ignore || authenticate(entry.moduleid, entry.actionid, request, response)) {
                    if (entry.cachetimeout > 0) {//有缓存设置
                        CacheEntry ce = entry.cache.get(request.getRequestURI());
                        if (ce != null && ce.time + entry.cachetimeout > System.currentTimeMillis()) { //缓存有效
                            response.setStatus(ce.status);
                            response.setContentType(ce.contentType);
                            response.finish(ce.getBuffers());
                            return;
                        }
                        response.setBufferHandler(entry.cacheInterceptor);
                    }
                    entry.servlet.execute(request, response);
                }
                return;
            }
        }
        throw new IOException(this.getClass().getName() + " not found method for URI(" + request.getRequestURI() + ")");
    }

    @Override
    public void init(Context context, AnyValue config) {
        String path = ((HttpContext) context).getContextPath();
        WebServlet ws = this.getClass().getAnnotation(WebServlet.class);
        if (ws != null && !ws.repair()) path = "";
        HashMap<String, Entry> map = load();
        this.actions = new Map.Entry[map.size()];
        int i = -1;
        for (Map.Entry<String, Entry> en : map.entrySet()) {
            actions[++i] = new AbstractMap.SimpleEntry<>(path + en.getKey(), en.getValue());
        }
    }

    public abstract boolean authenticate(int module, int actionid, HttpRequest request, HttpResponse response) throws IOException;

    private HashMap<String, Entry> load() {
        final boolean typeIgnore = this.getClass().getAnnotation(AuthIgnore.class) != null;
        WebServlet module = this.getClass().getAnnotation(WebServlet.class);
        final int serviceid = module == null ? 0 : module.moduleid();
        final HashMap<String, Entry> map = new HashMap<>();
        Set<String> nameset = new HashSet<>();
        for (final Method method : this.getClass().getMethods()) {
            //-----------------------------------------------
            String methodname = method.getName();
            if ("service".equals(methodname) || "preExecute".equals(methodname) || "execute".equals(methodname) || "authenticate".equals(methodname)) continue;
            //-----------------------------------------------
            Class[] paramTypes = method.getParameterTypes();
            if (paramTypes.length != 2 || paramTypes[0] != HttpRequest.class
                    || paramTypes[1] != HttpResponse.class) continue;
            //-----------------------------------------------
            Class[] exps = method.getExceptionTypes();
            if (exps.length > 0 && (exps.length != 1 || exps[0] != IOException.class)) continue;
            //-----------------------------------------------

            final WebAction action = method.getAnnotation(WebAction.class);
            if (action == null) continue;
            final int actionid = action.actionid();
            final String name = action.url().trim();

            if (nameset.contains(name)) throw new RuntimeException(this.getClass().getSimpleName() + " has two same " + WebAction.class.getSimpleName() + "(" + name + ")");
            for (String n : nameset) {
                if (n.contains(name) || name.contains(n)) {
                    throw new RuntimeException(this.getClass().getSimpleName() + " has two sub-contains " + WebAction.class.getSimpleName() + "(" + name + ", " + n + ")");
                }
            }
            nameset.add(name);
            map.put(name, new Entry(typeIgnore, serviceid, actionid, name, method, createHttpServlet(method)));
        }
        return map;
    }

    private HttpServlet createHttpServlet(final Method method) {
        //------------------------------------------------------------------------------
        final String supDynName = HttpServlet.class.getName().replace('.', '/');
        final String interName = this.getClass().getName().replace('.', '/');
        final String interDesc = jdk.internal.org.objectweb.asm.Type.getDescriptor(this.getClass());
        final String requestSupDesc = jdk.internal.org.objectweb.asm.Type.getDescriptor(Request.class);
        final String responseSupDesc = jdk.internal.org.objectweb.asm.Type.getDescriptor(Response.class);
        final String requestDesc = jdk.internal.org.objectweb.asm.Type.getDescriptor(HttpRequest.class);
        final String responseDesc = jdk.internal.org.objectweb.asm.Type.getDescriptor(HttpResponse.class);
        String newDynName = interName + "_Dyn_" + method.getName();
        int i = 0;
        for (;;) {
            try {
                Class.forName(newDynName.replace('/', '.'));
                newDynName += "_" + (++i);
            } catch (Exception ex) {
                break;
            }
        }
        //------------------------------------------------------------------------------
        ClassWriter cw = new ClassWriter(0);
        FieldVisitor fv;
        MethodVisitor mv;
        AnnotationVisitor av0;
        final String factfield = "factServlet";
        cw.visit(V1_8, ACC_PUBLIC + ACC_FINAL + ACC_SUPER, newDynName, null, supDynName, null);
        {
            fv = cw.visitField(ACC_PUBLIC, factfield, interDesc, null, null);
            fv.visitEnd();
        }
        { //构造函数
            mv = (cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null));
            //mv.setDebug(true);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, supDynName, "<init>", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        {
            mv = (cw.visitMethod(ACC_PUBLIC, "execute", "(" + requestDesc + responseDesc + ")V", null, new String[]{"java/io/IOException"}));
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, newDynName, factfield, interDesc);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitVarInsn(ALOAD, 2);
            mv.visitMethodInsn(INVOKEVIRTUAL, interName, method.getName(), "(" + requestDesc + responseDesc + ")V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(3, 3);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(ACC_PUBLIC + ACC_BRIDGE + ACC_SYNTHETIC, "execute", "(" + requestSupDesc + responseSupDesc + ")V", null, new String[]{"java/io/IOException"});
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, HttpRequest.class.getName().replace('.', '/'));
            mv.visitVarInsn(ALOAD, 2);
            mv.visitTypeInsn(CHECKCAST, HttpResponse.class.getName().replace('.', '/'));
            mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, "execute", "(" + requestDesc + responseDesc + ")V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(3, 3);
            mv.visitEnd();
        }
        cw.visitEnd();
        //------------------------------------------------------------------------------
        byte[] bytes = cw.toByteArray();
        Class<?> newClazz = new ClassLoader(this.getClass().getClassLoader()) {
            public final Class<?> loadClass(String name, byte[] b) {
                return defineClass(name, b, 0, b.length);
            }
        }.loadClass(newDynName.replace('/', '.'), bytes);
        try {
            HttpServlet instance = (HttpServlet) newClazz.newInstance();
            instance.getClass().getField(factfield).set(instance, this);
            return instance;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static final class Entry {

        public Entry(boolean typeIgnore, int moduleid, int actionid, String name, Method method, HttpServlet servlet) {
            this.moduleid = moduleid;
            this.actionid = actionid;
            this.name = name;
            this.method = method;
            this.servlet = servlet;
            this.ignore = typeIgnore || method.getAnnotation(AuthIgnore.class) != null;
            HttpCacheable hc = method.getAnnotation(HttpCacheable.class);
            this.cachetimeout = hc == null ? 0 : hc.timeout() * 1000;
            this.cache = cachetimeout > 0 ? new ConcurrentHashMap() : null;
            this.cacheInterceptor = cachetimeout > 0 ? (HttpResponse response, ByteBuffer[] buffers) -> {
                int status = response.getStatus();
                if (status != 200) return null;
                CacheEntry ce = new CacheEntry(response.getStatus(), response.getContentType(), buffers);
                cache.put(response.getRequest().getRequestURI(), ce);
                return ce.getBuffers();
            } : null;
        }

        public boolean isNeedCheck() {
            return this.moduleid != 0 || this.actionid != 0;
        }

        public final HttpResponse.BufferHandler cacheInterceptor;

        public final ConcurrentHashMap<String, CacheEntry> cache;

        public final int cachetimeout;

        public final boolean ignore;

        public final int moduleid;

        public final int actionid;

        public final String name;

        public final Method method;

        public final HttpServlet servlet;
    }

    private static final class CacheEntry {

        public final long time = System.currentTimeMillis();

        private final ByteBuffer[] buffers;

        private final int status;

        private final String contentType;

        public CacheEntry(int status, String contentType, ByteBuffer[] bufs) {
            this.status = status;
            this.contentType = contentType;
            final ByteBuffer[] newBuffers = new ByteBuffer[bufs.length];
            for (int i = 0; i < newBuffers.length; i++) {
                newBuffers[i] = bufs[i].duplicate().asReadOnlyBuffer();
            }
            this.buffers = newBuffers;
        }

        public ByteBuffer[] getBuffers() {
            final ByteBuffer[] newBuffers = new ByteBuffer[buffers.length];
            for (int i = 0; i < newBuffers.length; i++) {
                newBuffers[i] = buffers[i].duplicate();
            }
            return newBuffers;
        }
    }
}
