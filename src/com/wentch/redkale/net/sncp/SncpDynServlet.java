/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.sncp;

import com.wentch.redkale.convert.bson.*;
import static com.wentch.redkale.net.sncp.SncpRequest.DEFAULT_HEADER;
import com.wentch.redkale.service.*;
import com.wentch.redkale.util.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.*;
import java.util.*;
import java.util.function.*;
import java.util.logging.*;
import javax.annotation.*;
import jdk.internal.org.objectweb.asm.*;
import static jdk.internal.org.objectweb.asm.Opcodes.*;
import jdk.internal.org.objectweb.asm.Type;

/**
 *
 * @author zhangjx
 */
public final class SncpDynServlet extends SncpServlet {

    private final Logger logger = Logger.getLogger(SncpDynServlet.class.getSimpleName());

    private final boolean finest = logger.isLoggable(Level.FINEST);

    private final Class<? extends Service> type;

    private final String serviceName;

    private final long nameid;

    private final long serviceid;

    private final HashMap<DLong, SncpServletAction> actions = new HashMap<>();

    private Supplier<ByteBuffer> bufferSupplier;

    public SncpDynServlet(final BsonConvert convert, final String serviceName, final Service service, final AnyValue conf) {
        this.conf = conf;
        this.serviceName = serviceName;
        this.type = (Class<? extends Service>) service.getClass().getSuperclass();
        this.nameid = Sncp.hash(serviceName);
        this.serviceid = Sncp.hash(type);
        Set<DLong> actionids = new HashSet<>();
        for (java.lang.reflect.Method method : service.getClass().getMethods()) {
            if (method.isSynthetic()) continue;
            if (Modifier.isStatic(method.getModifiers())) continue;
            if (Modifier.isFinal(method.getModifiers())) continue;
            if (method.getName().equals("getClass") || method.getName().equals("toString")) continue;
            if (method.getName().equals("equals") || method.getName().equals("hashCode")) continue;
            if (method.getName().equals("notify") || method.getName().equals("notifyAll") || method.getName().equals("wait")) continue;
            if (method.getName().equals("init") || method.getName().equals("destroy") || method.getName().equals("name")) continue;
            final DLong actionid = Sncp.hash(method);
            SncpServletAction action = SncpServletAction.create(service, actionid, method);
            action.convert = convert;
            if (actionids.contains(actionid)) {
                throw new RuntimeException(type.getName() + " have action(Method=" + method + ", actionid=" + actionid + ") same to (" + actions.get(actionid).method + ")");
            }
            actions.put(actionid, action);
            actionids.add(actionid);
        }
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "(type=" + type.getName() + ", serviceid=" + serviceid + ", actions.size=" + actions.size() + ", name=" + serviceName + ")";
    }

    @Override
    public long getNameid() {
        return nameid;
    }

    @Override
    public long getServiceid() {
        return serviceid;
    }

    @Override
    public void execute(SncpRequest request, SncpResponse response) throws IOException {
        final boolean tcp = request.isTCP();
        if (bufferSupplier == null) {
            if (tcp) {
                bufferSupplier = request.getContext().getBufferSupplier();
            } else { //UDP 需要分包
                final Supplier<ByteBuffer> supplier = request.getContext().getBufferSupplier();
                bufferSupplier = () -> supplier.get().put(DEFAULT_HEADER);
            }
        }
        SncpServletAction action = actions.get(request.getActionid());
        //if (finest) logger.log(Level.FINEST, "sncpdyn.execute: " + request + ", " + (action == null ? "null" : action.method));
        if (action == null) {
            response.finish(SncpResponse.RETCODE_ILLACTIONID, null);  //无效actionid
        } else {
            BsonWriter out = action.convert.pollBsonWriter(bufferSupplier);
            if (tcp) out.writeTo(DEFAULT_HEADER);
            BsonReader in = action.convert.pollBsonReader();
            try {
                in.setBytes(request.getBody());
                action.action(in, out);
                response.finish(0, out);
            } catch (Throwable t) {
                response.getContext().getLogger().log(Level.INFO, "sncp execute error(" + request + ")", t);
                response.finish(SncpResponse.RETCODE_THROWEXCEPTION, null);
            } finally {
                action.convert.offerBsonReader(in);
                action.convert.offerBsonWriter(out);
            }
        }
    }

    public static abstract class SncpServletAction {

        public Method method;

        @Resource
        protected BsonConvert convert;

        protected java.lang.reflect.Type[] paramTypes;  //index=0表示返回参数的type， void的返回参数类型为null

        public abstract void action(final BsonReader in, final BsonWriter out) throws Throwable;

        /*
         *
         * public class TestService implements Service {
         *      public boolean change(TestBean bean, String name, int id) {
         *
         *      }
         * }
         *
         * public class DynActionTestService_change extends SncpServletAction {
         *
         *      public TestService service;
         *
         *      @Override
         *      public void action(final BsonReader in, final BsonWriter out) throws Throwable {
         *          TestBean arg1 = convert.convertFrom(in, paramTypes[1]);
         *          String arg2 = convert.convertFrom(in, paramTypes[2]);
         *          int arg3 = convert.convertFrom(in, paramTypes[3]);
         *          Object rs = service.change(arg1, arg2, arg3);
         *          convert.convertTo(out, paramTypes[0], rs);
         *      }
         * }
         */
        /**
         *
         * @param service
         * @param actionid
         * @param method
         * @return
         */
        @SuppressWarnings("unchecked")
        public static SncpServletAction create(final Service service, final DLong actionid, final Method method) {
            final Class serviceClass = service.getClass();
            final String supDynName = SncpServletAction.class.getName().replace('.', '/');
            final String serviceName = serviceClass.getName().replace('.', '/');
            final String convertName = BsonConvert.class.getName().replace('.', '/');
            final String convertReaderDesc = Type.getDescriptor(BsonReader.class);
            final String convertWriterDesc = Type.getDescriptor(BsonWriter.class);
            final String serviceDesc = Type.getDescriptor(serviceClass);
            String newDynName = serviceName.substring(0, serviceName.lastIndexOf('/') + 1)
                    + "DynAction" + serviceClass.getSimpleName() + "_" + method.getName() + "_" + actionid;
            while (true) {
                try {
                    Class.forName(newDynName.replace('/', '.'));
                    newDynName += "_";
                } catch (Exception ex) {
                    break;
                }
            }
            //-------------------------------------------------------------
            ClassWriter cw = new ClassWriter(0);
            FieldVisitor fv;
            DebugMethodVisitor mv;

            cw.visit(V1_8, ACC_PUBLIC + ACC_FINAL + ACC_SUPER, newDynName, null, supDynName, null);

            {
                {
                    fv = cw.visitField(ACC_PUBLIC, "service", serviceDesc, null, null);
                    fv.visitEnd();
                }
                fv.visitEnd();
            }
            {  // constructor方法
                mv = new DebugMethodVisitor(cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null));
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, supDynName, "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            String convertFromDesc = "(" + convertReaderDesc + "Ljava/lang/reflect/Type;)Ljava/lang/Object;";
            try {
                convertFromDesc = Type.getMethodDescriptor(BsonConvert.class.getMethod("convertFrom", BsonReader.class, java.lang.reflect.Type.class));
            } catch (Exception ex) {
                throw new RuntimeException(ex); //不可能会发生
            }
            { // action方法
                mv = new DebugMethodVisitor(cw.visitMethod(ACC_PUBLIC, "action", "(" + convertReaderDesc + convertWriterDesc + ")V", null, new String[]{"java/lang/Throwable"}));
                //mv.setDebug(true);
                int iconst = ICONST_1;
                int intconst = 1;
                int store = 3; //action的参数个数+1
                final Class[] paramClasses = method.getParameterTypes();
                int[][] codes = new int[paramClasses.length][2];
                for (int i = 0; i < paramClasses.length; i++) { //参数
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, newDynName, "convert", Type.getDescriptor(BsonConvert.class));
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, newDynName, "paramTypes", "[Ljava/lang/reflect/Type;");
                    if (iconst > ICONST_5) {
                        mv.visitIntInsn(BIPUSH, intconst);
                    } else {
                        mv.visitInsn(iconst);  //
                    }
                    mv.visitInsn(AALOAD);

                    mv.visitMethodInsn(INVOKEVIRTUAL, convertName, "convertFrom", convertFromDesc, false);
                    int load = ALOAD;
                    int v = 0;
                    if (paramClasses[i].isPrimitive()) {
                        int storecode = ISTORE;
                        load = ILOAD;
                        if (paramClasses[i] == long.class) {
                            storecode = LSTORE;
                            load = LLOAD;
                            v = 1;
                        } else if (paramClasses[i] == float.class) {
                            storecode = FSTORE;
                            load = FLOAD;
                            v = 1;
                        } else if (paramClasses[i] == double.class) {
                            storecode = DSTORE;
                            load = DLOAD;
                            v = 1;
                        }
                        Class bigPrimitiveClass = Array.get(Array.newInstance(paramClasses[i], 1), 0).getClass();
                        String bigPrimitiveName = bigPrimitiveClass.getName().replace('.', '/');
                        try {
                            Method pm = bigPrimitiveClass.getMethod(paramClasses[i].getSimpleName() + "Value");
                            mv.visitTypeInsn(CHECKCAST, bigPrimitiveName);
                            mv.visitMethodInsn(INVOKEVIRTUAL, bigPrimitiveName, pm.getName(), Type.getMethodDescriptor(pm), false);
                        } catch (Exception ex) {
                            throw new RuntimeException(ex); //不可能会发生
                        }
                        mv.visitVarInsn(storecode, store);
                    } else {
                        mv.visitTypeInsn(CHECKCAST, paramClasses[i].getName().replace('.', '/'));
                        mv.visitVarInsn(ASTORE, store);  //
                    }
                    codes[i] = new int[]{load, store};
                    store += v;
                    iconst++;
                    intconst++;
                    store++;
                }
                {  //调用service
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, newDynName, "service", serviceDesc);
                    for (int[] j : codes) {
                        mv.visitVarInsn(j[0], j[1]);
                    }
                    mv.visitMethodInsn(INVOKEVIRTUAL, serviceName, method.getName(), Type.getMethodDescriptor(method), false);
                }

                int maxStack = codes.length > 0 ? codes[codes.length - 1][1] : 1;
                Class returnClass = method.getReturnType();
                if (method.getReturnType() == void.class) { //返回
                    mv.visitInsn(RETURN);
                    maxStack = 8;
                } else {
                    if (returnClass.isPrimitive()) {
                        Class bigClass = Array.get(Array.newInstance(returnClass, 1), 0).getClass();
                        try {
                            Method vo = bigClass.getMethod("valueOf", returnClass);
                            mv.visitMethodInsn(INVOKESTATIC, bigClass.getName().replace('.', '/'), vo.getName(), Type.getMethodDescriptor(vo), false);
                        } catch (Exception ex) {
                            throw new RuntimeException(ex); //不可能会发生
                        }
                    }
                    mv.visitVarInsn(ASTORE, store);  //11
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, newDynName, "convert", Type.getDescriptor(BsonConvert.class));
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, newDynName, "paramTypes", "[Ljava/lang/reflect/Type;");
                    mv.visitInsn(ICONST_0);
                    mv.visitInsn(AALOAD);
                    mv.visitVarInsn(ALOAD, store);
                    mv.visitMethodInsn(INVOKEVIRTUAL, convertName, "convertTo", "(" + convertWriterDesc + "Ljava/lang/reflect/Type;Ljava/lang/Object;)V", false);
                    mv.visitInsn(RETURN);
                    store++;
                    if (maxStack < 10) maxStack = 10;
                }
                mv.visitMaxs(maxStack, store);
                mv.visitEnd();
            }
            cw.visitEnd();

            byte[] bytes = cw.toByteArray();
            Class<?> newClazz = new ClassLoader(serviceClass.getClassLoader()) {
                public final Class<?> loadClass(String name, byte[] b) {
                    return defineClass(name, b, 0, b.length);
                }
            }.loadClass(newDynName.replace('/', '.'), bytes);
            try {
                SncpServletAction instance = (SncpServletAction) newClazz.newInstance();
                instance.method = method;
                java.lang.reflect.Type[] ptypes = method.getGenericParameterTypes();
                java.lang.reflect.Type[] types = new java.lang.reflect.Type[ptypes.length + 1];
                java.lang.reflect.Type rt = method.getGenericReturnType();
                if (rt instanceof TypeVariable) {
                    TypeVariable tv = (TypeVariable) rt;
                    if (tv.getBounds().length == 1) rt = tv.getBounds()[0];
                }
                types[0] = rt;
                System.arraycopy(ptypes, 0, types, 1, ptypes.length);
                instance.paramTypes = types;
                newClazz.getField("service").set(instance, service);
                return instance;
            } catch (Exception ex) {
                throw new RuntimeException(ex); //不可能会发生
            }
        }
    }

}
