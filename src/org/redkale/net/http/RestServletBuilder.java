/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import static jdk.internal.org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static jdk.internal.org.objectweb.asm.Opcodes.*;
import jdk.internal.org.objectweb.asm.Type;
import org.redkale.net.sncp.*;
import org.redkale.service.*;
import org.redkale.util.*;
import org.redkale.source.Flipper;

/**
 * 以find开头的方法且参数只有一个且参数类型为primitive class或String，则RestParam值默认为#
 * <p>
 * 详情见: http://redkale.org
 *
 * @author zhangjx
 */
public final class RestServletBuilder {

    private static final Set<String> EXCLUDERMETHODS = new HashSet<>();

    static {
        for (Method m : Object.class.getMethods()) {
            EXCLUDERMETHODS.add(m.getName());
        }
    }

    private RestServletBuilder() {
    }

    //待实现
    public static <T extends RestHttpServlet> T createRestServlet(final Class<T> baseServletClass, final String serviceName, final Class<? extends Service> serviceType) {
        if (baseServletClass == null || serviceType == null) return null;
        if (!RestHttpServlet.class.isAssignableFrom(baseServletClass)) return null;
        int mod = baseServletClass.getModifiers();
        if (!java.lang.reflect.Modifier.isPublic(mod)) return null;
        if (java.lang.reflect.Modifier.isAbstract(mod)) return null;
        final String supDynName = baseServletClass.getName().replace('.', '/');
        final String serviceDesc = Type.getDescriptor(serviceType);
        final String webServletDesc = Type.getDescriptor(WebServlet.class);
        final String httpRequestDesc = Type.getDescriptor(HttpRequest.class);
        final String httpResponseDesc = Type.getDescriptor(HttpResponse.class);
        final String authDesc = Type.getDescriptor(BasedHttpServlet.AuthIgnore.class);
        final String actionDesc = Type.getDescriptor(BasedHttpServlet.WebAction.class);
        final String serviceTypeString = serviceType.getName().replace('.', '/');
        final Class userType = getSuperUserType(baseServletClass);

        final RestService controller = serviceType.getAnnotation(RestService.class);
        if (controller != null && controller.ignore()) return null; //标记为ignore=true不创建Servlet
        ClassLoader loader = Sncp.class.getClassLoader();
        String newDynName = serviceTypeString.substring(0, serviceTypeString.lastIndexOf('/') + 1) + "_Dyn" + serviceType.getSimpleName().replaceAll("Service.*$", "") + "RestServlet";
        if (!serviceName.isEmpty()) {
            boolean normal = true;
            for (char ch : serviceName.toCharArray()) {//含特殊字符的使用hash值
                if (!((ch >= '0' && ch <= '9') || ch == '_' || (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z'))) normal = false;
            }
            newDynName += "_" + (normal ? serviceName : Sncp.hash(serviceName));
        }
        try {
            return ((Class<T>) Class.forName(newDynName.replace('/', '.'))).newInstance();
        } catch (Exception ex) {
        }
        Method currentUserMethod = null;
        try {
            currentUserMethod = baseServletClass.getDeclaredMethod("currentUser", HttpRequest.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        //------------------------------------------------------------------------------
        final String defmodulename = (controller != null && !controller.value().isEmpty()) ? controller.value() : serviceType.getSimpleName().replaceAll("Service.*$", "");
        for (char ch : defmodulename.toCharArray()) {
            if (!((ch >= '0' && ch <= '9') || ch == '$' || ch == '_' || (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z'))) { //不能含特殊字符
                throw new RuntimeException(serviceType.getName() + " has illeal " + RestService.class.getSimpleName() + ".value, only 0-9 a-z A-Z _ $");
            }
        }

        ClassWriter cw = new ClassWriter(COMPUTE_FRAMES);
        FieldVisitor fv;
        AsmMethodVisitor mv;
        AnnotationVisitor av0;

        cw.visit(V1_8, ACC_PUBLIC + ACC_FINAL + ACC_SUPER, newDynName, null, supDynName, null);
        { //注入 @WebServlet 注解
            av0 = cw.visitAnnotation(webServletDesc, true);
            {
                AnnotationVisitor av1 = av0.visitArray("value");
                av1.visit(null, "/" + defmodulename.toLowerCase() + "/*");
                av1.visitEnd();
            }
            av0.visit("moduleid", controller == null ? 0 : controller.module());
            av0.visit("repair", controller == null ? true : controller.repair());
            av0.visitEnd();
        }

        {  //注入 @Resource  private XXXService _service;
            fv = cw.visitField(ACC_PRIVATE, "_service", serviceDesc, null, null);
            av0 = fv.visitAnnotation("Ljavax/annotation/Resource;", true);
            av0.visitEnd();
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

        final List<MappingEntry> entrys = new ArrayList<>();
        for (final Method method : serviceType.getMethods()) {
            Class[] extypes = method.getExceptionTypes();
            if (extypes.length > 1) continue;
            if (extypes.length == 1 && extypes[0] != IOException.class) continue;
            if (EXCLUDERMETHODS.contains(method.getName())) continue;
            if ("init".equals(method.getName())) continue;
            if ("destroy".equals(method.getName())) continue;

            RestMapping[] mappings = method.getAnnotationsByType(RestMapping.class);
            boolean ignore = false;
            for (RestMapping mapping : mappings) {
                if (mapping.ignore()) {
                    ignore = true;
                    break;
                }
            }
            if (ignore) continue;
            if (mappings.length == 0) { //没有Mapping，设置一个默认值
                MappingEntry entry = new MappingEntry(null, defmodulename, method);
                if (entrys.contains(entry)) throw new RuntimeException(serviceType.getName() + " on " + method.getName() + " 's mapping(" + entry.name + ") is repeat");
                entrys.add(entry);
            } else {
                for (RestMapping mapping : mappings) {
                    MappingEntry entry = new MappingEntry(mapping, defmodulename, method);
                    if (entrys.contains(entry)) throw new RuntimeException(serviceType.getName() + " on " + method.getName() + " 's mapping(" + entry.name + ") is repeat");
                    entrys.add(entry);
                }
            }
        }
        if (entrys.isEmpty()) return null;

        for (final MappingEntry entry : entrys) {
            final Method method = entry.mappingMethod;
            final Class returnType = method.getReturnType();
            final String methodDesc = Type.getMethodDescriptor(method);
            final Parameter[] params = method.getParameters();

            mv = new AsmMethodVisitor(cw.visitMethod(ACC_PUBLIC, entry.name, "(" + httpRequestDesc + httpResponseDesc + ")V", null, new String[]{"java/io/IOException"}));
            //mv.setDebug(true);
            mv.debugLine();
            if (entry.authignore) { //设置 AuthIgnore
                av0 = mv.visitAnnotation(authDesc, true);
                av0.visitEnd();
            }
            final int maxStack = 3 + params.length;
            List<int[]> varInsns = new ArrayList<>();
            int maxLocals = 3;
            boolean hasVisitWebAction = false;
            final String jsvar = entry.jsvar.isEmpty() ? null : entry.jsvar;
            int argIndex = 0;
            for (final Parameter param : params) {
                final Class ptype = param.getType();
                RestParam annpara = param.getAnnotation(RestParam.class);
                String n = annpara == null || annpara.value().isEmpty() ? null : annpara.value();
                if (n == null) {
                    if (param.isNamePresent()) {
                        n = param.getName();
                    } else {
                        n = (++argIndex > 1) ? ("bean" + argIndex) : "bean";
                    }
                }
                if ((entry.name.startsWith("find") || entry.name.startsWith("delete")) && params.length == 1) {
                    if (ptype.isPrimitive() || ptype == String.class) n = "#";
                }
                if (!hasVisitWebAction) {
                    hasVisitWebAction = true;
                    //设置 WebAction
                    av0 = mv.visitAnnotation(actionDesc, true);
                    av0.visit("url", "/" + defmodulename.toLowerCase() + "/" + entry.name + ("#".equals(n) ? "/" : ""));
                    av0.visit("actionid", entry.actionid);

                    AnnotationVisitor av1 = av0.visitArray("methods");
                    for (String m : entry.methods) {
                        av1.visit(null, m);
                    }
                    av1.visitEnd();

                    av0.visitEnd();
                }
                final String pname = n;
                final boolean hd = annpara == null ? false : annpara.header();

                if ("#".equals(pname)) { //从request.getRequstURI 中去参数
                    if (ptype == boolean.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpRequest", "getRequstURILastPath", "()Ljava/lang/String;", false);
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "parseBoolean", "(Ljava/lang/String;)Z", false);
                        mv.visitVarInsn(ISTORE, maxLocals);
                        varInsns.add(new int[]{ILOAD, maxLocals});
                    } else if (ptype == byte.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpRequest", "getRequstURILastPath", "()Ljava/lang/String;", false);
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Byte", "parseByte", "(Ljava/lang/String;)B", false);
                        mv.visitVarInsn(ISTORE, maxLocals);
                        varInsns.add(new int[]{ILOAD, maxLocals});
                    } else if (ptype == short.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpRequest", "getRequstURILastPath", "()Ljava/lang/String;", false);
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Short", "parseShort", "(Ljava/lang/String;)S", false);
                        mv.visitVarInsn(ISTORE, maxLocals);
                        varInsns.add(new int[]{ILOAD, maxLocals});
                    } else if (ptype == char.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpRequest", "getRequstURILastPath", "()Ljava/lang/String;", false);
                        mv.visitInsn(ICONST_0);
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false);
                        mv.visitVarInsn(ISTORE, maxLocals);
                        varInsns.add(new int[]{ILOAD, maxLocals});
                    } else if (ptype == int.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpRequest", "getRequstURILastPath", "()Ljava/lang/String;", false);
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;)I", false);
                        mv.visitVarInsn(ISTORE, maxLocals);
                        varInsns.add(new int[]{ILOAD, maxLocals});
                    } else if (ptype == float.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpRequest", "getRequstURILastPath", "()Ljava/lang/String;", false);
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "parseFloat", "(Ljava/lang/String;)F", false);
                        mv.visitVarInsn(FSTORE, maxLocals);
                        varInsns.add(new int[]{FLOAD, maxLocals});
                    } else if (ptype == long.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpRequest", "getRequstURILastPath", "()Ljava/lang/String;", false);
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "parseLong", "(Ljava/lang/String;)J", false);
                        mv.visitVarInsn(LSTORE, maxLocals);
                        varInsns.add(new int[]{LLOAD, maxLocals});
                        maxLocals++;
                    } else if (ptype == double.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpRequest", "getRequstURILastPath", "()Ljava/lang/String;", false);
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "parseDouble", "(Ljava/lang/String;)D", false);
                        mv.visitVarInsn(DSTORE, maxLocals);
                        varInsns.add(new int[]{DLOAD, maxLocals});
                        maxLocals++;
                    } else if (ptype == String.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpRequest", "getRequstURILastPath", "()Ljava/lang/String;", false);
                        mv.visitVarInsn(ASTORE, maxLocals);
                    } else {
                        throw new RuntimeException(method + " only " + RestParam.class.getSimpleName() + "(#) to Type(primitive class or String)");
                    }
                } else if (ptype == boolean.class) {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitLdcInsn(pname);
                    mv.visitInsn(ICONST_0);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpRequest", hd ? "getBooleanHeader" : "getBooleanParameter", "(Ljava/lang/String;Z)Z", false);
                    mv.visitVarInsn(ISTORE, maxLocals);
                    varInsns.add(new int[]{ILOAD, maxLocals});
                } else if (ptype == byte.class) {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitLdcInsn(pname);
                    mv.visitLdcInsn("0");
                    mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpRequest", hd ? "getHeader" : "getParameter", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Byte", "parseByte", "(Ljava/lang/String;)B", false);
                    mv.visitVarInsn(ISTORE, maxLocals);
                    varInsns.add(new int[]{ILOAD, maxLocals});
                } else if (ptype == short.class) {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitLdcInsn(pname);
                    mv.visitInsn(ICONST_0);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpRequest", hd ? "getShortHeader" : "getShortParameter", "(Ljava/lang/String;S)S", false);
                    mv.visitVarInsn(ISTORE, maxLocals);
                    varInsns.add(new int[]{ILOAD, maxLocals});
                } else if (ptype == char.class) {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitLdcInsn(pname);
                    mv.visitLdcInsn("0");
                    mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpRequest", hd ? "getHeader" : "getParameter", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
                    mv.visitInsn(ICONST_0);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false);
                    mv.visitVarInsn(ISTORE, maxLocals);
                    varInsns.add(new int[]{ILOAD, maxLocals});
                } else if (ptype == int.class) {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitLdcInsn(pname);
                    mv.visitInsn(ICONST_0);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpRequest", hd ? "getIntHeader" : "getIntParameter", "(Ljava/lang/String;I)I", false);
                    mv.visitVarInsn(ISTORE, maxLocals);
                    varInsns.add(new int[]{ILOAD, maxLocals});
                } else if (ptype == float.class) {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitLdcInsn(pname);
                    mv.visitInsn(FCONST_0);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpRequest", hd ? "getFloatHeader" : "getFloatParameter", "(Ljava/lang/String;F)F", false);
                    mv.visitVarInsn(FSTORE, maxLocals);
                    varInsns.add(new int[]{FLOAD, maxLocals});
                } else if (ptype == long.class) {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitLdcInsn(pname);
                    mv.visitInsn(LCONST_0);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpRequest", hd ? "getLongHeader" : "getLongParameter", "(Ljava/lang/String;J)J", false);
                    mv.visitVarInsn(LSTORE, maxLocals);
                    varInsns.add(new int[]{LLOAD, maxLocals});
                    maxLocals++;
                } else if (ptype == double.class) {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitLdcInsn(pname);
                    mv.visitInsn(DCONST_0);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpRequest", hd ? "getDoubleHeader" : "getDoubleParameter", "(Ljava/lang/String;D)D", false);
                    mv.visitVarInsn(DSTORE, maxLocals);
                    varInsns.add(new int[]{DLOAD, maxLocals});
                    maxLocals++;
                } else if (ptype == String.class) {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitLdcInsn(pname);
                    mv.visitLdcInsn("");
                    mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpRequest", hd ? "getHeader" : "getParameter", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
                    mv.visitVarInsn(ASTORE, maxLocals);
                    varInsns.add(new int[]{ALOAD, maxLocals});
                } else if (ptype == Flipper.class) {
                    //mv.visitVarInsn(ALOAD, 0);
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpRequest", "getFlipper", "()Lorg/redkale/source/Flipper;", false);
                    //mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, "findFlipper", "(Lorg/redkale/net/http/HttpRequest;)Lorg/redkale/source/Flipper;", false);
                    mv.visitVarInsn(ASTORE, maxLocals);
                    varInsns.add(new int[]{ALOAD, maxLocals});
                } else if (ptype == userType) { //当前用户对象的类名
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, "currentUser", Type.getMethodDescriptor(currentUserMethod), false);
                    mv.visitVarInsn(ASTORE, maxLocals);
                    varInsns.add(new int[]{ALOAD, maxLocals});
                } else {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitLdcInsn(Type.getType(Type.getDescriptor(ptype)));
                    mv.visitLdcInsn(pname);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpRequest", hd ? "getJsonHeader" : "getJsonParameter", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Object;", false);
                    mv.visitTypeInsn(CHECKCAST, ptype.getName().replace('.', '/'));
                    mv.visitVarInsn(ASTORE, maxLocals);
                    varInsns.add(new int[]{ALOAD, maxLocals});
                }
                maxLocals++;
            } // end params for each

            if (!hasVisitWebAction) { //当无参数时则没有设置过 WebAction
                hasVisitWebAction = true;
                //设置 WebAction
                av0 = mv.visitAnnotation(actionDesc, true);
                av0.visit("url", "/" + defmodulename.toLowerCase() + "/" + entry.name);
                av0.visit("actionid", entry.actionid);
                av0.visitEnd();
            }

            mv.visitVarInsn(ALOAD, 0); //调用this
            mv.visitFieldInsn(GETFIELD, newDynName, "_service", serviceDesc);
            for (int[] ins : varInsns) {
                mv.visitVarInsn(ins[0], ins[1]);
            }
            mv.visitMethodInsn(INVOKEVIRTUAL, serviceTypeString, method.getName(), methodDesc, false);
            if (returnType == void.class) {
                //mv.visitVarInsn(ALOAD, 0);
                mv.visitVarInsn(ALOAD, 2);
                //mv.visitFieldInsn(GETSTATIC, "org/redkale/service/RetResult", "SUCCESS", "Lorg/redkale/service/RetResult;");
                mv.visitMethodInsn(INVOKESTATIC, "org/redkale/service/RetResult", "success", "()Lorg/redkale/service/RetResult;", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpResponse", "finishJson", "(Lorg/redkale/service/RetResult;)V", false);
                //mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, "sendRetResult", "(Lorg/redkale/net/http/HttpResponse;Lorg/redkale/service/RetResult;)V", false);
                mv.visitInsn(RETURN);
            } else if (returnType == boolean.class) {
                mv.visitVarInsn(ISTORE, maxLocals);
                if (jsvar == null) {
                    mv.visitVarInsn(ALOAD, 2); //response
                    mv.visitVarInsn(ILOAD, maxLocals);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", "(Z)Ljava/lang/String;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpResponse", "finish", "(Ljava/lang/String;)V", false);
                } else {
                    //mv.visitVarInsn(ALOAD, 0);
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitLdcInsn(jsvar);
                    mv.visitVarInsn(ILOAD, maxLocals);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpResponse", "finishJsResult", "(Ljava/lang/String;Ljava/lang/Object;)V", false);
                    //mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, "sendJsResult", "(Lorg/redkale/net/http/HttpResponse;Ljava/lang/String;Ljava/lang/Object;)V", false);
                }
                mv.visitInsn(RETURN);
                maxLocals++;
            } else if (returnType == byte.class) {
                mv.visitVarInsn(ISTORE, maxLocals);
                if (jsvar == null) {
                    mv.visitVarInsn(ALOAD, 2); //response
                    mv.visitVarInsn(ILOAD, maxLocals);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", "(I)Ljava/lang/String;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpResponse", "finish", "(Ljava/lang/String;)V", false);
                } else {
                    //mv.visitVarInsn(ALOAD, 0);
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitLdcInsn(jsvar);
                    mv.visitVarInsn(ILOAD, maxLocals);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpResponse", "finishJsResult", "(Ljava/lang/String;Ljava/lang/Object;)V", false);
                    //mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, "sendJsResult", "(Lorg/redkale/net/http/HttpResponse;Ljava/lang/String;Ljava/lang/Object;)V", false);
                }
                mv.visitInsn(RETURN);
                maxLocals++;
            } else if (returnType == short.class) {
                mv.visitVarInsn(ISTORE, maxLocals);
                if (jsvar == null) {
                    mv.visitVarInsn(ALOAD, 2); //response
                    mv.visitVarInsn(ILOAD, maxLocals);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", "(I)Ljava/lang/String;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpResponse", "finish", "(Ljava/lang/String;)V", false);
                } else {
                    //mv.visitVarInsn(ALOAD, 0);
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitLdcInsn(jsvar);
                    mv.visitVarInsn(ILOAD, maxLocals);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpResponse", "finishJsResult", "(Ljava/lang/String;Ljava/lang/Object;)V", false);
                    //mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, "sendJsResult", "(Lorg/redkale/net/http/HttpResponse;Ljava/lang/String;Ljava/lang/Object;)V", false);
                }
                mv.visitInsn(RETURN);
                maxLocals++;
            } else if (returnType == char.class) {
                mv.visitVarInsn(ISTORE, maxLocals);
                if (jsvar == null) {
                    mv.visitVarInsn(ALOAD, 2); //response
                    mv.visitVarInsn(ILOAD, maxLocals);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", "(C)Ljava/lang/String;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpResponse", "finish", "(Ljava/lang/String;)V", false);
                } else {
                    //mv.visitVarInsn(ALOAD, 0);
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitLdcInsn(jsvar);
                    mv.visitVarInsn(ILOAD, maxLocals);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpResponse", "finishJsResult", "(Ljava/lang/String;Ljava/lang/Object;)V", false);
                    //mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, "sendJsResult", "(Lorg/redkale/net/http/HttpResponse;Ljava/lang/String;Ljava/lang/Object;)V", false);
                }
                mv.visitInsn(RETURN);
                maxLocals++;
            } else if (returnType == int.class) {
                mv.visitVarInsn(ISTORE, maxLocals);
                if (jsvar == null) {
                    mv.visitVarInsn(ALOAD, 2); //response
                    mv.visitVarInsn(ILOAD, maxLocals);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", "(I)Ljava/lang/String;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpResponse", "finish", "(Ljava/lang/String;)V", false);
                } else {
                    //mv.visitVarInsn(ALOAD, 0);
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitLdcInsn(jsvar);
                    mv.visitVarInsn(ILOAD, maxLocals);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpResponse", "finishJsResult", "(Ljava/lang/String;Ljava/lang/Object;)V", false);
                    //mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, "sendJsResult", "(Lorg/redkale/net/http/HttpResponse;Ljava/lang/String;Ljava/lang/Object;)V", false);
                }
                mv.visitInsn(RETURN);
                maxLocals++;
            } else if (returnType == float.class) {
                mv.visitVarInsn(FSTORE, maxLocals);
                if (jsvar == null) {
                    mv.visitVarInsn(ALOAD, 2); //response
                    mv.visitVarInsn(FLOAD, maxLocals);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", "(F)Ljava/lang/String;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpResponse", "finish", "(Ljava/lang/String;)V", false);
                } else {
                    //mv.visitVarInsn(ALOAD, 0);
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitLdcInsn(jsvar);
                    mv.visitVarInsn(FLOAD, maxLocals);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpResponse", "finishJsResult", "(Ljava/lang/String;Ljava/lang/Object;)V", false);
                    //mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, "sendJsResult", "(Lorg/redkale/net/http/HttpResponse;Ljava/lang/String;Ljava/lang/Object;)V", false);
                }
                mv.visitInsn(RETURN);
                maxLocals++;
            } else if (returnType == long.class) {
                mv.visitVarInsn(LSTORE, maxLocals);
                if (jsvar == null) {
                    mv.visitVarInsn(ALOAD, 2); //response
                    mv.visitVarInsn(LLOAD, maxLocals);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", "(J)Ljava/lang/String;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpResponse", "finish", "(Ljava/lang/String;)V", false);
                } else {
                    //mv.visitVarInsn(ALOAD, 0);
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitLdcInsn(jsvar);
                    mv.visitVarInsn(LLOAD, maxLocals);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpResponse", "finishJsResult", "(Ljava/lang/String;Ljava/lang/Object;)V", false);
                    //mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, "sendJsResult", "(Lorg/redkale/net/http/HttpResponse;Ljava/lang/String;Ljava/lang/Object;)V", false);
                }
                mv.visitInsn(RETURN);
                maxLocals += 2;
            } else if (returnType == double.class) {
                mv.visitVarInsn(DSTORE, maxLocals);
                if (jsvar == null) {
                    mv.visitVarInsn(ALOAD, 2); //response
                    mv.visitVarInsn(DLOAD, maxLocals);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", "(D)Ljava/lang/String;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpResponse", "finish", "(Ljava/lang/String;)V", false);
                } else {
                    //mv.visitVarInsn(ALOAD, 0);
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitLdcInsn(jsvar);
                    mv.visitVarInsn(DLOAD, maxLocals);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpResponse", "finishJsResult", "(Ljava/lang/String;Ljava/lang/Object;)V", false);
                    //mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, "sendJsResult", "(Lorg/redkale/net/http/HttpResponse;Ljava/lang/String;Ljava/lang/Object;)V", false);
                }
                mv.visitInsn(RETURN);
                maxLocals += 2;
            } else if (returnType == String.class) {
                mv.visitVarInsn(ASTORE, maxLocals);
                if (jsvar == null) {
                    mv.visitVarInsn(ALOAD, 2); //response
                    mv.visitVarInsn(ALOAD, maxLocals);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpResponse", "finish", "(Ljava/lang/String;)V", false);
                } else {
                    //mv.visitVarInsn(ALOAD, 0);
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitLdcInsn(jsvar);
                    mv.visitVarInsn(ALOAD, maxLocals);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpResponse", "finishJsResult", "(Ljava/lang/String;Ljava/lang/Object;)V", false);
                    //mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, "sendJsResult", "(Lorg/redkale/net/http/HttpResponse;Ljava/lang/String;Ljava/lang/Object;)V", false);
                }
                mv.visitInsn(RETURN);
                maxLocals++;
            } else if (returnType == File.class) {
                mv.visitVarInsn(ASTORE, maxLocals);
                if (jsvar == null) {
                    mv.visitVarInsn(ALOAD, 2); //response
                    mv.visitVarInsn(ALOAD, maxLocals);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpResponse", "finish", "(Ljava/io/File;)V", false);
                } else {
                    throw new RuntimeException(method + " cannot set return Type (java.io.File) to jsvar");
                }
                mv.visitInsn(RETURN);
                maxLocals++;
            } else if (RetResult.class.isAssignableFrom(returnType)) {
                mv.visitVarInsn(ASTORE, maxLocals);
                if (jsvar == null) {
                    //mv.visitVarInsn(ALOAD, 0);
                    mv.visitVarInsn(ALOAD, 2); //response
                    mv.visitVarInsn(ALOAD, maxLocals);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpResponse", "finishJson", "(Lorg/redkale/service/RetResult;)V", false);
                    //mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, "sendRetResult", "(Lorg/redkale/net/http/HttpResponse;Lorg/redkale/service/RetResult;)V", false);
                } else {
                    //mv.visitVarInsn(ALOAD, 0);
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitLdcInsn(jsvar);
                    mv.visitVarInsn(ALOAD, maxLocals);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpResponse", "finishJsResult", "(Ljava/lang/String;Ljava/lang/Object;)V", false);
                    //mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, "sendJsResult", "(Lorg/redkale/net/http/HttpResponse;Ljava/lang/String;Ljava/lang/Object;)V", false);
                }
                mv.visitInsn(RETURN);
                maxLocals++;
            } else if (Number.class.isAssignableFrom(returnType) || CharSequence.class.isAssignableFrom(returnType)) {   //returnType == String.class 必须放在前面
                mv.visitVarInsn(ASTORE, maxLocals);
                if (jsvar == null) {
                    mv.visitVarInsn(ALOAD, 2); //response
                    mv.visitVarInsn(ALOAD, maxLocals);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpResponse", "finish", "(Ljava/lang/String;)V", false);
                } else {
                    //mv.visitVarInsn(ALOAD, 0);
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitLdcInsn(jsvar);
                    mv.visitVarInsn(ALOAD, maxLocals);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpResponse", "finishJsResult", "(Ljava/lang/String;Ljava/lang/Object;)V", false);
                    //mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, "sendJsResult", "(Lorg/redkale/net/http/HttpResponse;Ljava/lang/String;Ljava/lang/Object;)V", false);
                }
                mv.visitInsn(RETURN);
                maxLocals++;
            } else {
                mv.visitVarInsn(ASTORE, maxLocals);
                if (jsvar == null) {
                    mv.visitVarInsn(ALOAD, 2); //response
                    mv.visitVarInsn(ALOAD, maxLocals);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpResponse", "finishJson", "(Ljava/lang/Object;)V", false);
                } else {
                    //mv.visitVarInsn(ALOAD, 0);
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitLdcInsn(jsvar);
                    mv.visitVarInsn(ALOAD, maxLocals);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "org/redkale/net/http/HttpResponse", "finishJsResult", "(Ljava/lang/String;Ljava/lang/Object;)V", false);
                    //mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, "sendJsResult", "(Lorg/redkale/net/http/HttpResponse;Ljava/lang/String;Ljava/lang/Object;)V", false);
                }
                mv.visitInsn(RETURN);
                maxLocals++;
            }
            mv.visitMaxs(maxStack, maxLocals);

        } // end  for each
        cw.visitEnd();
        byte[] bytes = cw.toByteArray();
        Class<?> newClazz = new ClassLoader(loader) {
            public final Class<?> loadClass(String name, byte[] b) {
                return defineClass(name, b, 0, b.length);
            }
        }.loadClass(newDynName.replace('/', '.'), bytes);
        try {
            return ((Class<T>) newClazz).newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Class getSuperUserType(Class servletClass) {
        java.lang.reflect.Type type = servletClass.getGenericSuperclass();
        if (type instanceof Class) return getSuperUserType((Class) type);
        if (type instanceof java.lang.reflect.ParameterizedType) {
            java.lang.reflect.ParameterizedType pt = (java.lang.reflect.ParameterizedType) type;
            if (pt.getRawType() == RestHttpServlet.class) {
                java.lang.reflect.Type usert = pt.getActualTypeArguments()[0];
                if (usert instanceof Class) return (Class) usert;
            }
        }
        return null;
    }

    private static class MappingEntry {

        private static final RestMapping DEFAULT__MAPPING;

        static {
            try {
                DEFAULT__MAPPING = MappingEntry.class.getDeclaredMethod("mapping").getAnnotation(RestMapping.class);
            } catch (Exception e) {
                throw new Error(e);
            }
        }

        public MappingEntry(RestMapping mapping, final String defmodulename, Method method) {
            if (mapping == null) mapping = DEFAULT__MAPPING;
            this.ignore = mapping.ignore();
            String n = mapping.name().toLowerCase();
            if (n.isEmpty()) n = method.getName().toLowerCase().replace(defmodulename.toLowerCase(), "");
            this.name = n;
            this.mappingMethod = method;
            this.methods = mapping.methods();
            this.authignore = mapping.authignore();
            this.actionid = mapping.actionid();
            this.contentType = mapping.contentType();
            this.jsvar = mapping.jsvar();
        }

        public final Method mappingMethod;

        public final boolean ignore;

        public final String name;

        public final String[] methods;

        public final boolean authignore;

        public final int actionid;

        public final String contentType;

        public final String jsvar;

        @RestMapping()
        void mapping() { //用于获取Mapping 默认值
        }

        @Override
        public int hashCode() {
            return this.name.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            return this.name.equals(((MappingEntry) obj).name);
        }

    }
}
