/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.sncp;

import static org.redkale.net.sncp.SncpRequest.DEFAULT_HEADER;
import java.io.*;
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.nio.*;
import java.util.*;
import java.util.function.*;
import java.util.logging.*;
import javax.annotation.*;
import jdk.internal.org.objectweb.asm.*;
import static jdk.internal.org.objectweb.asm.Opcodes.*;
import jdk.internal.org.objectweb.asm.Type;
import org.redkale.convert.bson.*;
import org.redkale.service.*;
import org.redkale.util.*;
import org.redkale.service.DynCall;

/**
 *
 * <p>
 * 详情见: http://redkale.org
 *
 * @author zhangjx
 */
public final class SncpDynServlet extends SncpServlet {

    private static volatile int maxClassNameLength = 0;

    private static volatile int maxNameLength = 0;

    private static final Logger logger = Logger.getLogger(SncpDynServlet.class.getSimpleName());

    private final boolean finest = logger.isLoggable(Level.FINEST);

    private final Class<? extends Service> type;

    private final String serviceName;

    private final DLong serviceid;

    private final HashMap<DLong, SncpServletAction> actions = new HashMap<>();

    private Supplier<ByteBuffer> bufferSupplier;

    public SncpDynServlet(final BsonConvert convert, final String serviceName, final Class<? extends Service> type, final Service service) {
        this.serviceName = serviceName;
        this.type = type;
        this.serviceid = Sncp.hash(type.getName() + ':' + serviceName);
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
        maxNameLength = Math.max(maxNameLength, serviceName.length() + 1);
        maxClassNameLength = Math.max(maxClassNameLength, type.getName().length());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getSimpleName()).append("(type=").append(type.getName());
        int len = maxClassNameLength - type.getName().length();
        for (int i = 0; i < len; i++) {
            sb.append(' ');
        }
        sb.append(", serviceid=").append(serviceid).append(", name='").append(serviceName).append("'");
        for (int i = 0; i < maxNameLength - serviceName.length(); i++) {
            sb.append(' ');
        }
        sb.append(", actions.size=").append(actions.size() > 9 ? "" : " ").append(actions.size()).append(")");
        return sb.toString();
    }

    @Override
    public DLong getServiceid() {
        return serviceid;
    }

    @Override
    public int compareTo(SncpServlet o0) {
        if (!(o0 instanceof SncpDynServlet)) return 1;
        SncpDynServlet o = (SncpDynServlet) o0;
        int rs = this.type.getName().compareTo(o.type.getName());
        if (rs == 0) rs = this.serviceName.compareTo(o.serviceName);
        return rs;
    }

    @Override
    public void execute(SncpRequest request, SncpResponse response) throws IOException {
        if (bufferSupplier == null) {
            bufferSupplier = request.getContext().getBufferSupplier();
        }
        SncpServletAction action = actions.get(request.getActionid());
        //if (finest) logger.log(Level.FINEST, "sncpdyn.execute: " + request + ", " + (action == null ? "null" : action.method));
        if (action == null) {
            response.finish(SncpResponse.RETCODE_ILLACTIONID, null);  //无效actionid
        } else {
            BsonWriter out = action.convert.pollBsonWriter(bufferSupplier);
            out.writeTo(DEFAULT_HEADER);
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

        protected org.redkale.util.Attribute[] paramAttrs; // 为null表示无DynCall处理，index=0固定为null, 其他为参数标记的DynCall回调方法

        protected java.lang.reflect.Type[] paramTypes;  //index=0表示返回参数的type， void的返回参数类型为null

        public abstract void action(final BsonReader in, final BsonWriter out) throws Throwable;

        public final void callParameter(final BsonWriter out, final Object... params) {
            if (paramAttrs != null) {
                for (int i = 1; i < paramAttrs.length; i++) {
                    org.redkale.util.Attribute attr = paramAttrs[i];
                    if (attr == null) continue;
                    out.writeByte((byte) i);
                    convert.convertTo(out, attr.type(), attr.get(params[i - 1]));
                }
            }
            out.writeByte((byte) 0);
        }

        /**
         * <blockquote><pre>
         *  public class TestService implements Service {
         *      public boolean change(TestBean bean, String name, int id) {
         * <p>
         *      }
         *  }
         * <p>
         *  public class DynActionTestService_change extends SncpServletAction {
         * <p>
         *      public TestService service;
         * <p>
         *      &#64;Override
         *      public void action(final BsonReader in, final BsonWriter out) throws Throwable {
         *          TestBean arg1 = convert.convertFrom(paramTypes[1], in);
         *          String arg2 = convert.convertFrom(paramTypes[2], in);
         *          int arg3 = convert.convertFrom(paramTypes[3], in);
         *          Object rs = service.change(arg1, arg2, arg3);
         *          callParameter(out, arg1, arg2, arg3);
         *          convert.convertTo(out, paramTypes[0], rs);
         *      }
         *  }
         * </pre></blockquote>
         *
         * @param service  Service
         * @param actionid 操作ID
         * @param method   方法
         *
         * @return SncpServletAction
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
            AsmMethodVisitor mv;

            cw.visit(V1_8, ACC_PUBLIC + ACC_FINAL + ACC_SUPER, newDynName, null, supDynName, null);

            {
                {
                    fv = cw.visitField(ACC_PUBLIC, "service", serviceDesc, null, null);
                    fv.visitEnd();
                }
                fv.visitEnd();
            }
            {  // constructor方法
                mv = new AsmMethodVisitor(cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null));
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, supDynName, "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
            String convertFromDesc = "(Ljava/lang/reflect/Type;" + convertReaderDesc + ")Ljava/lang/Object;";
            try {
                convertFromDesc = Type.getMethodDescriptor(BsonConvert.class.getMethod("convertFrom", java.lang.reflect.Type.class, BsonReader.class));
            } catch (Exception ex) {
                throw new RuntimeException(ex); //不可能会发生
            }
            { // action方法
                mv = new AsmMethodVisitor(cw.visitMethod(ACC_PUBLIC, "action", "(" + convertReaderDesc + convertWriterDesc + ")V", null, new String[]{"java/lang/Throwable"}));
                //mv.setDebug(true);
                int iconst = ICONST_1;
                int intconst = 1;
                int store = 3; //action的参数个数+1
                final Class[] paramClasses = method.getParameterTypes();
                int[][] codes = new int[paramClasses.length][2];
                for (int i = 0; i < paramClasses.length; i++) { //参数
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, newDynName, "convert", Type.getDescriptor(BsonConvert.class));
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, newDynName, "paramTypes", "[Ljava/lang/reflect/Type;");
                    if (iconst > ICONST_5) {
                        mv.visitIntInsn(BIPUSH, intconst);
                    } else {
                        mv.visitInsn(iconst);  //
                    }
                    mv.visitInsn(AALOAD);
                    mv.visitVarInsn(ALOAD, 1);

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

                final Class returnClass = method.getReturnType();
                if (returnClass != void.class) {
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
                }
                //------------------------- callParameter 方法 --------------------------------
                mv.visitVarInsn(ALOAD, 0);
                mv.visitVarInsn(ALOAD, 2);
                if (paramClasses.length <= 5) {  //参数总数量
                    mv.visitInsn(ICONST_0 + paramClasses.length);
                } else {
                    mv.visitIntInsn(BIPUSH, paramClasses.length);
                }
                mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
                int insn = 2;
                for (int j = 0; j < paramClasses.length; j++) {
                    final Class pt = paramClasses[j];
                    mv.visitInsn(DUP);
                    insn++;
                    if (j <= 5) {
                        mv.visitInsn(ICONST_0 + j);
                    } else {
                        mv.visitIntInsn(BIPUSH, j);
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
                mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, "callParameter", "(" + convertWriterDesc + "[Ljava/lang/Object;)V", false);

                //-------------------------直接返回  或者  调用convertTo方法 --------------------------------
                int maxStack = codes.length > 0 ? codes[codes.length - 1][1] : 1;
                if (returnClass == void.class) { //返回
                    mv.visitInsn(RETURN);
                    maxStack = 8;
                } else {
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
                mv.visitMaxs(maxStack + 10, store + 10);
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

                org.redkale.util.Attribute[] atts = new org.redkale.util.Attribute[ptypes.length + 1];
                Annotation[][] anns = method.getParameterAnnotations();
                boolean hasattr = false;
                for (int i = 0; i < anns.length; i++) {
                    if (anns[i].length > 0) {
                        for (Annotation ann : anns[i]) {
                            if (ann.annotationType() == DynCall.class) {
                                try {
                                    atts[i + 1] = ((DynCall) ann).value().newInstance();
                                    hasattr = true;
                                } catch (Exception e) {
                                    logger.log(Level.SEVERE, DynCall.class.getSimpleName() + ".attribute cannot a newInstance for" + method, e);
                                }
                                break;
                            }
                        }
                    }
                }
                if (hasattr) instance.paramAttrs = atts;
                newClazz.getField("service").set(instance, service);
                return instance;
            } catch (Exception ex) {
                throw new RuntimeException(ex); //不可能会发生
            }
        }
    }

}
