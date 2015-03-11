/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.http;

import com.wentch.redkale.net.Response;
import com.wentch.redkale.net.Request;
import com.wentch.redkale.net.Context;
import com.wentch.redkale.util.AnyValue;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import static jdk.internal.org.objectweb.asm.Opcodes.*;

/**
 *
 * @author zhangjx
 */ 
public abstract class BasedHttpServlet extends HttpServlet {

    private Map.Entry<String, Entry>[] actions;

    @Override
    public final void execute(HttpRequest request, HttpResponse response) throws IOException {
        for (Map.Entry<String, Entry> en : actions) {
            if (request.getRequestURI().startsWith(en.getKey())) {
                Entry entry = en.getValue();
                if (entry.ignore || authenticate(request, response)) entry.servlet.execute(request, response);
                return;
            }
        }
        throw new IOException(this.getClass().getName() + " not found method for URI(" + request.getRequestURI() + ")");
    }

    @Override
    public void init(Context context, AnyValue config) {
        String path = ((HttpContext) context).getContextPath();
        WebServlet ws = this.getClass().getAnnotation(WebServlet.class);
        if (ws != null && !ws.fillurl()) path = "";
        HashMap<String, Entry> map = load();
        this.actions = new Map.Entry[map.size()];
        int i = -1;
        for (Map.Entry<String, Entry> en : map.entrySet()) {
            actions[++i] = new AbstractMap.SimpleEntry<>(path + en.getKey(), en.getValue());
        }
    }

    public abstract boolean authenticate(HttpRequest request, HttpResponse response) throws IOException;

    private HashMap<String, Entry> load() {
        final boolean typeIgnore = this.getClass().getAnnotation(AuthIgnore.class) != null;
        WebServlet module = this.getClass().getAnnotation(WebServlet.class);
        final int serviceid = module == null ? 0 : module.moduleid();
        final HashMap<String, Entry> map = new HashMap<>();
        Set<String> nameset = new HashSet<>();
        for (final Method method : this.getClass().getMethods()) {
            //-----------------------------------------------
            String methodname = method.getName();
            if ("service".equals(methodname) || "execute".equals(methodname) || "authenticate".equals(methodname)) continue;
            //-----------------------------------------------
            Class[] paramTypes = method.getParameterTypes();
            if (paramTypes.length != 2 || paramTypes[0] != HttpRequest.class
                    || paramTypes[1] != HttpResponse.class) continue;
            //-----------------------------------------------
            Class[] exps = method.getExceptionTypes();
            if (exps.length > 0 && (exps.length != 1 || exps[0] != IOException.class)) continue;
            //-----------------------------------------------
            String name = methodname;
            int actionid = 0;
            WebAction action = method.getAnnotation(WebAction.class);
            if (action != null) {
                actionid = action.actionid();
                name = action.url().trim();
            }
            if (nameset.contains(name)) throw new RuntimeException(this.getClass().getSimpleName() + " has two same " + WebAction.class.getSimpleName() + "(" + name + ")");
            for (String n : nameset) {
                if (n.contains(name) || name.contains(n)) {
                    throw new RuntimeException(this.getClass().getSimpleName() + " has two overlap " + WebAction.class.getSimpleName() + "(" + name + ", " + n + ")");
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
        }

        public boolean isNeedCheck() {
            return this.moduleid != 0 || this.actionid != 0;
        }

        public final boolean ignore;

        public final int moduleid;

        public final int actionid;

        public final String name;

        public final Method method;

        public final HttpServlet servlet;
    }
}
