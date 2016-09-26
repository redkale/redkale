/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.io.*;
import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.reflect.*;
import java.util.*;
import jdk.internal.org.objectweb.asm.*;
import static jdk.internal.org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static jdk.internal.org.objectweb.asm.Opcodes.*;
import jdk.internal.org.objectweb.asm.Type;
import org.redkale.convert.json.JsonConvert;
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
public final class Rest {

    public static final String REST_HEADER_RESOURCE_NAME = "rest-resource-name";

    static final String REST_SERVICE_FIELD_NAME = "_service";

    static final String REST_SERVICEMAP_FIELD_NAME = "_servicemap"; //如果只有name=""的Service资源，则实例中_servicemap必须为null

    private static final Set<String> EXCLUDERMETHODS = new HashSet<>();

    static {
        for (Method m : Object.class.getMethods()) {
            EXCLUDERMETHODS.add(m.getName());
        }
    }

    @Inherited
    @Documented
    @Target({TYPE})
    @Retention(RUNTIME)
    public static @interface RestDynamic {

    }

    private Rest() {
    }

    static String getWebModuleName(Class<? extends Service> serviceType) {
        final RestService controller = serviceType.getAnnotation(RestService.class);
        if (controller == null) return serviceType.getSimpleName().replaceAll("Service.*$", "").toLowerCase();
        if (controller.ignore()) return null;
        return (!controller.name().isEmpty()) ? controller.name() : serviceType.getSimpleName().replaceAll("Service.*$", "").toLowerCase();
    }

    static <T extends RestHttpServlet> T createRestServlet(final Class<T> baseServletClass, final Class<? extends Service> serviceType) {
        if (baseServletClass == null || serviceType == null) return null;
        if (!RestHttpServlet.class.isAssignableFrom(baseServletClass)) return null;
        int mod = baseServletClass.getModifiers();
        if (!java.lang.reflect.Modifier.isPublic(mod)) return null;
        if (java.lang.reflect.Modifier.isAbstract(mod)) return null;

        final String serviceDesc = Type.getDescriptor(serviceType);
        final String webServletDesc = Type.getDescriptor(WebServlet.class);
        final String reqDesc = Type.getDescriptor(HttpRequest.class);
        final String respDesc = Type.getDescriptor(HttpResponse.class);
        final String retDesc = Type.getDescriptor(RetResult.class);
        final String flipperDesc = Type.getDescriptor(Flipper.class);
        final String restoutputDesc = Type.getDescriptor(RestOutput.class);
        final String attrDesc = Type.getDescriptor(org.redkale.util.Attribute.class);
        final String authDesc = Type.getDescriptor(HttpBaseServlet.AuthIgnore.class);
        final String cacheDesc = Type.getDescriptor(HttpBaseServlet.HttpCacheable.class);
        final String actionDesc = Type.getDescriptor(HttpBaseServlet.WebAction.class);
        final String webparamDesc = Type.getDescriptor(HttpBaseServlet.WebParam.class);
        final String webparamsDesc = Type.getDescriptor(HttpBaseServlet.WebParams.class);
        final String sourcetypeDesc = Type.getDescriptor(HttpBaseServlet.ParamSourceType.class);

        final String reqInternalName = Type.getInternalName(HttpRequest.class);
        final String respInternalName = Type.getInternalName(HttpResponse.class);
        final String attrInternalName = Type.getInternalName(org.redkale.util.Attribute.class);
        final String retInternalName = Type.getInternalName(RetResult.class);
        final String serviceTypeInternalName = Type.getInternalName(serviceType);

        final Class userType = getSuperUserType(baseServletClass);
        final String supDynName = baseServletClass.getName().replace('.', '/');
        final RestService controller = serviceType.getAnnotation(RestService.class);
        if (controller != null && controller.ignore()) return null; //标记为ignore=true不创建Servlet
        ClassLoader loader = Sncp.class.getClassLoader();
        String newDynName = serviceTypeInternalName.substring(0, serviceTypeInternalName.lastIndexOf('/') + 1) + "_Dyn" + serviceType.getSimpleName().replaceAll("Service.*$", "") + "RestServlet";
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
        final String defmodulename = getWebModuleName(serviceType);
        for (char ch : defmodulename.toCharArray()) {
            if (!((ch >= '0' && ch <= '9') || ch == '$' || ch == '_' || (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z'))) { //不能含特殊字符
                throw new RuntimeException(serviceType.getName() + " has illeal " + RestService.class.getSimpleName() + ".value, only 0-9 a-z A-Z _ $");
            }
        }

        ClassWriter cw = new ClassWriter(COMPUTE_FRAMES);
        FieldVisitor fv;
        AsmMethodVisitor mv;
        AnnotationVisitor av0;
        Map<String, Object> classMap = new LinkedHashMap<>();
        List<Map<String, Object>> actionMaps = new ArrayList<>();
        cw.visit(V1_8, ACC_PUBLIC + ACC_FINAL + ACC_SUPER, newDynName, null, supDynName, null);

        { //RestDynamic
            av0 = cw.visitAnnotation(Type.getDescriptor(RestDynamic.class), true);
            av0.visitEnd();
        }

        { //注入 @WebServlet 注解
            String urlpath = "/" + defmodulename + "/*";
            int moduleid = controller == null ? 0 : controller.moduleid();
            boolean repair = controller == null ? true : controller.repair();
            String comment = controller == null ? "" : controller.comment();
            av0 = cw.visitAnnotation(webServletDesc, true);
            {
                AnnotationVisitor av1 = av0.visitArray("value");
                av1.visit(null, urlpath);
                av1.visitEnd();
            }
            av0.visit("moduleid", moduleid);
            av0.visit("repair", repair);
            av0.visit("comment", comment);
            av0.visitEnd();
            classMap.put("type", serviceType.getName());
            classMap.put("url", urlpath);
            classMap.put("moduleid", moduleid);
            classMap.put("repair", repair);
            classMap.put("comment", comment);
        }

        {  //注入 @Resource  private XXXService _service;
            fv = cw.visitField(ACC_PRIVATE, REST_SERVICE_FIELD_NAME, serviceDesc, null, null);
            av0 = fv.visitAnnotation("Ljavax/annotation/Resource;", true);
            av0.visit("name", "");
            av0.visitEnd();
            fv.visitEnd();
        }
        { //_servicemap字段 Map<String, XXXService>
            fv = cw.visitField(ACC_PRIVATE, REST_SERVICEMAP_FIELD_NAME, "Ljava/util/Map;", "Ljava/util/Map<Ljava/lang/String;" + serviceDesc + ">;", null);
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
        final Map<String, org.redkale.util.Attribute> restAttributes = new LinkedHashMap<>();

        for (final Method method : serviceType.getMethods()) {
            if (Modifier.isStatic(method.getModifiers())) continue;
            Class[] extypes = method.getExceptionTypes();
            if (extypes.length > 1) continue;
            if (extypes.length == 1 && extypes[0] != IOException.class) continue;
            if (EXCLUDERMETHODS.contains(method.getName())) continue;
            if ("init".equals(method.getName())) continue;
            if ("destroy".equals(method.getName())) continue;
            if ("version".equals(method.getName())) continue;

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
        if (entrys.isEmpty()) return null; //没有可WebAction的方法
        for (final MappingEntry entry : entrys) {
            final Method method = entry.mappingMethod;
            final Class returnType = method.getReturnType();
            final String methodDesc = Type.getMethodDescriptor(method);
            final Parameter[] params = method.getParameters();

            mv = new AsmMethodVisitor(cw.visitMethod(ACC_PUBLIC, entry.name, "(" + reqDesc + respDesc + ")V", null, new String[]{"java/io/IOException"}));
            //mv.setDebug(true);
            mv.debugLine();
            if (!entry.auth) { //设置 AuthIgnore
                av0 = mv.visitAnnotation(authDesc, true);
                av0.visitEnd();
            }
            if (entry.cachetimeout > 0) { //设置 HttpCacheable
                av0 = mv.visitAnnotation(cacheDesc, true);
                av0.visit("timeout", entry.cachetimeout);
                av0.visitEnd();
            }

            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, newDynName, REST_SERVICEMAP_FIELD_NAME, "Ljava/util/Map;");
            Label lmapif = new Label();
            mv.visitJumpInsn(IFNONNULL, lmapif);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, newDynName, REST_SERVICE_FIELD_NAME, serviceDesc);
            Label lserif = new Label();
            mv.visitJumpInsn(GOTO, lserif);
            mv.visitLabel(lmapif);

            mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);

            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, newDynName, REST_SERVICEMAP_FIELD_NAME, "Ljava/util/Map;");
            mv.visitVarInsn(ALOAD, 1);
            mv.visitLdcInsn(REST_HEADER_RESOURCE_NAME);
            mv.visitLdcInsn("");
            mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, "getHeader", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
            mv.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
            mv.visitTypeInsn(CHECKCAST, serviceTypeInternalName);
            mv.visitLabel(lserif);
            mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[]{serviceTypeInternalName});
            mv.visitVarInsn(ASTORE, 3);

            final int maxStack = 3 + params.length;
            List<int[]> varInsns = new ArrayList<>();
            int maxLocals = 4;
            final String jsvar = entry.jsvar.isEmpty() ? null : entry.jsvar;
            int argIndex = 0;

            List<Object[]> paramlist = new ArrayList<>();
            for (final Parameter param : params) {
                final Class ptype = param.getType();
                String n = null;
                String comment = "";
                int radix = 10;

                RestHeader annhead = param.getAnnotation(RestHeader.class);
                if (annhead != null) {
                    n = annhead.name();
                    radix = annhead.radix();
                    comment = annhead.comment();
                    if (n.isEmpty()) throw new RuntimeException("@RestHeader.value is illegal in " + method);
                }
                RestCookie anncookie = param.getAnnotation(RestCookie.class);
                if (anncookie != null) {
                    if (annhead != null) throw new RuntimeException("@RestCookie and @RestHeader cannot on the same Parameter in " + method);
                    if (ptype != String.class) throw new RuntimeException("@RestCookie must on String Parameter in " + method);
                    n = anncookie.name();
                    radix = anncookie.radix();
                    comment = anncookie.comment();
                    if (n.isEmpty()) throw new RuntimeException("@RestCookie.value is illegal in " + method);
                }
                RestSessionid annsid = param.getAnnotation(RestSessionid.class);
                if (annsid != null) {
                    if (annhead != null) throw new RuntimeException("@RestSessionid and @RestHeader cannot on the same Parameter in " + method);
                    if (anncookie != null) throw new RuntimeException("@RestSessionid and @RestCookie cannot on the same Parameter in " + method);
                    if (ptype != String.class) throw new RuntimeException("@RestSessionid must on String Parameter in " + method);
                }
                RestAddress annaddr = param.getAnnotation(RestAddress.class);
                if (annaddr != null) {
                    if (annhead != null) throw new RuntimeException("@RestAddress and @RestHeader cannot on the same Parameter in " + method);
                    if (anncookie != null) throw new RuntimeException("@RestAddress and @RestCookie cannot on the same Parameter in " + method);
                    if (annsid != null) throw new RuntimeException("@RestAddress and @RestSessionid cannot on the same Parameter in " + method);
                    if (ptype != String.class) throw new RuntimeException("@RestAddress must on String Parameter in " + method);
                }

                RestParam annpara = param.getAnnotation(RestParam.class);
                if (annpara != null) radix = annpara.radix();
                if (annpara != null) comment = annpara.comment();
                if (n == null) n = (annpara == null || annpara.name().isEmpty()) ? null : annpara.name();
                if (n == null && ptype == userType) n = "&"; //用户类型特殊处理
                if (n == null) {
                    if (param.isNamePresent()) {
                        n = param.getName();
                    } else if (ptype == Flipper.class) {
                        n = "flipper";
                    } else {
                        n = (++argIndex > 1) ? ("bean" + argIndex) : "bean";
                    }
                }
                if (annhead == null && anncookie == null && annaddr == null
                    && (entry.name.startsWith("find") || entry.name.startsWith("delete")) && params.length == 1) {
                    if (ptype.isPrimitive() || ptype == String.class) n = "#";
                }
                paramlist.add(new Object[]{param, n, ptype, radix, comment, annpara, annsid, annaddr, annhead, anncookie});
            }

            Map<String, Object> actionMap = new LinkedHashMap<>();
            {
                //设置 WebAction
                boolean reqpath = false;
                for (Object[] ps : paramlist) {
                    if ("#".equals((String) ps[1])) {
                        reqpath = true;
                        break;
                    }
                }
                av0 = mv.visitAnnotation(actionDesc, true);
                String url = "/" + defmodulename.toLowerCase() + "/" + entry.name + (reqpath ? "/" : "");
                av0.visit("url", url);
                av0.visit("actionid", entry.actionid);
                av0.visit("comment", entry.comment);

                AnnotationVisitor av1 = av0.visitArray("methods");
                for (String m : entry.methods) {
                    av1.visit(null, m);
                }
                av1.visitEnd();

                java.lang.reflect.Type grt = method.getGenericReturnType();
                av0.visit("result", grt == returnType ? returnType.getName() : String.valueOf(grt));

                av0.visitEnd();
                actionMap.put("url", url);
                actionMap.put("auth", entry.auth);
                actionMap.put("cachetimeout", entry.cachetimeout);
                actionMap.put("actionid", entry.actionid);
                actionMap.put("comment", entry.comment);
                actionMap.put("methods", entry.methods);
                actionMap.put("result", grt == returnType ? returnType.getName() : String.valueOf(grt));
            }

            {
                av0 = mv.visitAnnotation(webparamsDesc, true);
                AnnotationVisitor av1 = av0.visitArray("value");
                //设置 WebParam
                for (Object[] ps : paramlist) { //{param, n, ptype, radix, comment, annpara, annsid, annaddr, annhead, anncookie}   
                    final boolean ishead = ((RestHeader) ps[8]) != null; //是否取getHeader 而不是 getParameter
                    final boolean iscookie = ((RestCookie) ps[9]) != null; //是否取getCookie

                    AnnotationVisitor av2 = av1.visitAnnotation(null, webparamDesc);
                    av2.visit("name", (String) ps[1]);
                    av2.visit("type", Type.getType(Type.getDescriptor((Class) ps[2])));
                    av2.visit("radix", (Integer) ps[3]);
                    av2.visitEnum("src", sourcetypeDesc, ishead ? HttpBaseServlet.ParamSourceType.HEADER.name()
                        : (iscookie ? HttpBaseServlet.ParamSourceType.COOKIE.name() : HttpBaseServlet.ParamSourceType.PARAMETER.name()));
                    av2.visit("comment", (String) ps[4]);
                    av2.visitEnd();
                }
                av1.visitEnd();
                av0.visitEnd();
            }
            List<Map<String, Object>> paramMaps = new ArrayList<>();
            for (Object[] ps : paramlist) {
                Map<String, Object> paramMap = new LinkedHashMap<>();
                String pname = (String) ps[1]; //参数名
                Class ptype = (Class) ps[2];
                int radix = (Integer) ps[3];
                String comment = (String) ps[4];
                RestParam annpara = (RestParam) ps[5];
                RestSessionid annsid = (RestSessionid) ps[6];
                RestAddress annaddr = (RestAddress) ps[7];
                RestHeader annhead = (RestHeader) ps[8];
                RestCookie anncookie = (RestCookie) ps[9];

                final boolean ishead = annhead != null; //是否取getHeader 而不是 getParameter
                final boolean iscookie = anncookie != null; //是否取getCookie

                paramMap.put("name", pname);
                paramMap.put("type", ptype.getName());
                if (annsid != null) { //HttpRequest.getSessionid(true|false)
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitInsn(annsid.create() ? ICONST_1 : ICONST_0);
                    mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, "getSessionid", "(Z)Ljava/lang/String;", false);
                    mv.visitVarInsn(ASTORE, maxLocals);
                    varInsns.add(new int[]{ALOAD, maxLocals});
                } else if (annaddr != null) { //HttpRequest.getRemoteAddr
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, "getRemoteAddr", "()Ljava/lang/String;", false);
                    mv.visitVarInsn(ASTORE, maxLocals);
                    varInsns.add(new int[]{ALOAD, maxLocals});
                } else if ("#".equals(pname)) { //从request.getRequstURI 中取参数
                    if (ptype == boolean.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, "getRequstURILastPath", "()Ljava/lang/String;", false);
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "parseBoolean", "(Ljava/lang/String;)Z", false);
                        mv.visitVarInsn(ISTORE, maxLocals);
                        varInsns.add(new int[]{ILOAD, maxLocals});
                    } else if (ptype == byte.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, "getRequstURILastPath", "()Ljava/lang/String;", false);
                        mv.visitIntInsn(BIPUSH, radix);
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Byte", "parseByte", "(Ljava/lang/String;I)B", false);
                        mv.visitVarInsn(ISTORE, maxLocals);
                        varInsns.add(new int[]{ILOAD, maxLocals});
                    } else if (ptype == short.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, "getRequstURILastPath", "()Ljava/lang/String;", false);
                        mv.visitIntInsn(BIPUSH, radix);
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Short", "parseShort", "(Ljava/lang/String;I)S", false);
                        mv.visitVarInsn(ISTORE, maxLocals);
                        varInsns.add(new int[]{ILOAD, maxLocals});
                    } else if (ptype == char.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, "getRequstURILastPath", "()Ljava/lang/String;", false);
                        mv.visitInsn(ICONST_0);
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false);
                        mv.visitVarInsn(ISTORE, maxLocals);
                        varInsns.add(new int[]{ILOAD, maxLocals});
                    } else if (ptype == int.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, "getRequstURILastPath", "()Ljava/lang/String;", false);
                        mv.visitIntInsn(BIPUSH, radix);
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;I)I", false);
                        mv.visitVarInsn(ISTORE, maxLocals);
                        varInsns.add(new int[]{ILOAD, maxLocals});
                    } else if (ptype == float.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, "getRequstURILastPath", "()Ljava/lang/String;", false);
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "parseFloat", "(Ljava/lang/String;)F", false);
                        mv.visitVarInsn(FSTORE, maxLocals);
                        varInsns.add(new int[]{FLOAD, maxLocals});
                    } else if (ptype == long.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, "getRequstURILastPath", "()Ljava/lang/String;", false);
                        mv.visitIntInsn(BIPUSH, radix);
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "parseLong", "(Ljava/lang/String;I)J", false);
                        mv.visitVarInsn(LSTORE, maxLocals);
                        varInsns.add(new int[]{LLOAD, maxLocals});
                        maxLocals++;
                    } else if (ptype == double.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, "getRequstURILastPath", "()Ljava/lang/String;", false);
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "parseDouble", "(Ljava/lang/String;)D", false);
                        mv.visitVarInsn(DSTORE, maxLocals);
                        varInsns.add(new int[]{DLOAD, maxLocals});
                        maxLocals++;
                    } else if (ptype == String.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, "getRequstURILastPath", "()Ljava/lang/String;", false);
                        mv.visitVarInsn(ASTORE, maxLocals);
                        varInsns.add(new int[]{ALOAD, maxLocals});
                    } else {
                        throw new RuntimeException(method + " only " + RestParam.class.getSimpleName() + "(#) to Type(primitive class or String)");
                    }
                } else if (pname.charAt(0) == '#') { //从request.getRequstURIPath 中去参数
                    if (ptype == boolean.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitLdcInsn(pname.substring(1));
                        mv.visitLdcInsn("false");
                        mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, "getRequstURIPath", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "parseBoolean", "(Ljava/lang/String;)Z", false);
                        mv.visitVarInsn(ISTORE, maxLocals);
                        varInsns.add(new int[]{ILOAD, maxLocals});
                    } else if (ptype == byte.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitLdcInsn(pname.substring(1));
                        mv.visitLdcInsn("0");
                        mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, "getRequstURIPath", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
                        mv.visitIntInsn(BIPUSH, radix);
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Byte", "parseByte", "(Ljava/lang/String;I)B", false);
                        mv.visitVarInsn(ISTORE, maxLocals);
                        varInsns.add(new int[]{ILOAD, maxLocals});
                    } else if (ptype == short.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitLdcInsn(pname.substring(1));
                        mv.visitLdcInsn("0");
                        mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, "getRequstURIPath", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
                        mv.visitIntInsn(BIPUSH, radix);
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Short", "parseShort", "(Ljava/lang/String;I)S", false);
                        mv.visitVarInsn(ISTORE, maxLocals);
                        varInsns.add(new int[]{ILOAD, maxLocals});
                    } else if (ptype == char.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitLdcInsn(pname.substring(1));
                        mv.visitLdcInsn("0");
                        mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, "getRequstURIPath", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
                        mv.visitInsn(ICONST_0);
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false);
                        mv.visitVarInsn(ISTORE, maxLocals);
                        varInsns.add(new int[]{ILOAD, maxLocals});
                    } else if (ptype == int.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitLdcInsn(pname.substring(1));
                        mv.visitLdcInsn("0");
                        mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, "getRequstURIPath", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
                        mv.visitIntInsn(BIPUSH, radix);
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;I)I", false);
                        mv.visitVarInsn(ISTORE, maxLocals);
                        varInsns.add(new int[]{ILOAD, maxLocals});
                    } else if (ptype == float.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitLdcInsn(pname.substring(1));
                        mv.visitLdcInsn("0");
                        mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, "getRequstURIPath", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "parseFloat", "(Ljava/lang/String;)F", false);
                        mv.visitVarInsn(FSTORE, maxLocals);
                        varInsns.add(new int[]{FLOAD, maxLocals});
                    } else if (ptype == long.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitLdcInsn(pname.substring(1));
                        mv.visitLdcInsn("0");
                        mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, "getRequstURIPath", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
                        mv.visitIntInsn(BIPUSH, radix);
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "parseLong", "(Ljava/lang/String;I)J", false);
                        mv.visitVarInsn(LSTORE, maxLocals);
                        varInsns.add(new int[]{LLOAD, maxLocals});
                        maxLocals++;
                    } else if (ptype == double.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitLdcInsn(pname.substring(1));
                        mv.visitLdcInsn("0");
                        mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, "getRequstURIPath", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "parseDouble", "(Ljava/lang/String;)D", false);
                        mv.visitVarInsn(DSTORE, maxLocals);
                        varInsns.add(new int[]{DLOAD, maxLocals});
                        maxLocals++;
                    } else if (ptype == String.class) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitLdcInsn(pname.substring(1));
                        mv.visitLdcInsn("");
                        mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, "getRequstURIPath", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
                        mv.visitVarInsn(ASTORE, maxLocals);
                        varInsns.add(new int[]{ALOAD, maxLocals});
                    } else {
                        throw new RuntimeException(method + " only " + RestParam.class.getSimpleName() + "(#) to Type(primitive class or String)");
                    }
                } else if ("&".equals(pname) && ptype == userType) { //当前用户对象的类名
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, "currentUser", Type.getMethodDescriptor(currentUserMethod), false);
                    mv.visitVarInsn(ASTORE, maxLocals);
                    varInsns.add(new int[]{ALOAD, maxLocals});
                } else if (ptype == boolean.class) {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitLdcInsn(pname);
                    mv.visitInsn(ICONST_0);
                    mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, ishead ? "getBooleanHeader" : "getBooleanParameter", "(Ljava/lang/String;Z)Z", false);
                    mv.visitVarInsn(ISTORE, maxLocals);
                    varInsns.add(new int[]{ILOAD, maxLocals});
                } else if (ptype == byte.class) {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitLdcInsn(pname);
                    mv.visitLdcInsn("0");
                    mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, ishead ? "getHeader" : "getParameter", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
                    mv.visitIntInsn(BIPUSH, radix);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Byte", "parseByte", "(Ljava/lang/String;I)B", false);
                    mv.visitVarInsn(ISTORE, maxLocals);
                    varInsns.add(new int[]{ILOAD, maxLocals});
                } else if (ptype == short.class) {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitIntInsn(BIPUSH, radix);
                    mv.visitLdcInsn(pname);
                    mv.visitInsn(ICONST_0);
                    mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, ishead ? "getShortHeader" : "getShortParameter", "(ILjava/lang/String;S)S", false);
                    mv.visitVarInsn(ISTORE, maxLocals);
                    varInsns.add(new int[]{ILOAD, maxLocals});
                } else if (ptype == char.class) {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitLdcInsn(pname);
                    mv.visitLdcInsn("0");
                    mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, ishead ? "getHeader" : "getParameter", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
                    mv.visitInsn(ICONST_0);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false);
                    mv.visitVarInsn(ISTORE, maxLocals);
                    varInsns.add(new int[]{ILOAD, maxLocals});
                } else if (ptype == int.class) {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitIntInsn(BIPUSH, radix);
                    mv.visitLdcInsn(pname);
                    mv.visitInsn(ICONST_0);
                    mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, ishead ? "getIntHeader" : "getIntParameter", "(ILjava/lang/String;I)I", false);
                    mv.visitVarInsn(ISTORE, maxLocals);
                    varInsns.add(new int[]{ILOAD, maxLocals});
                } else if (ptype == float.class) {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitLdcInsn(pname);
                    mv.visitInsn(FCONST_0);
                    mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, ishead ? "getFloatHeader" : "getFloatParameter", "(Ljava/lang/String;F)F", false);
                    mv.visitVarInsn(FSTORE, maxLocals);
                    varInsns.add(new int[]{FLOAD, maxLocals});
                } else if (ptype == long.class) {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitIntInsn(BIPUSH, radix);
                    mv.visitLdcInsn(pname);
                    mv.visitInsn(LCONST_0);
                    mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, ishead ? "getLongHeader" : "getLongParameter", "(ILjava/lang/String;J)J", false);
                    mv.visitVarInsn(LSTORE, maxLocals);
                    varInsns.add(new int[]{LLOAD, maxLocals});
                    maxLocals++;
                } else if (ptype == double.class) {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitLdcInsn(pname);
                    mv.visitInsn(DCONST_0);
                    mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, ishead ? "getDoubleHeader" : "getDoubleParameter", "(Ljava/lang/String;D)D", false);
                    mv.visitVarInsn(DSTORE, maxLocals);
                    varInsns.add(new int[]{DLOAD, maxLocals});
                    maxLocals++;
                } else if (ptype == String.class) {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitLdcInsn(pname);
                    mv.visitLdcInsn("");
                    mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, iscookie ? "getCookie" : (ishead ? "getHeader" : "getParameter"), "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
                    mv.visitVarInsn(ASTORE, maxLocals);
                    varInsns.add(new int[]{ALOAD, maxLocals});
                } else if (ptype == Flipper.class) {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, "getFlipper", "()" + flipperDesc, false);
                    mv.visitVarInsn(ASTORE, maxLocals);
                    varInsns.add(new int[]{ALOAD, maxLocals});
                } else { //其他Json对象
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitLdcInsn(Type.getType(Type.getDescriptor(ptype)));
                    mv.visitLdcInsn(pname);
                    mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, ishead ? "getJsonHeader" : "getJsonParameter", "(Ljava/lang/reflect/Type;Ljava/lang/String;)Ljava/lang/Object;", false);
                    mv.visitTypeInsn(CHECKCAST, ptype.getName().replace('.', '/'));
                    mv.visitVarInsn(ASTORE, maxLocals);
                    varInsns.add(new int[]{ALOAD, maxLocals});

                    //构建 RestHeader、RestCookie、RestAddress 等赋值操作
                    Class loop = ptype;
                    Set<String> fields = new HashSet<>();
                    Map<String, Object[]> attrParaNames = new LinkedHashMap<>();
                    do {
                        if (loop == null || loop.isInterface()) break; //接口时getSuperclass可能会得到null
                        for (Field field : loop.getDeclaredFields()) {
                            if (Modifier.isStatic(field.getModifiers())) continue;
                            if (Modifier.isFinal(field.getModifiers())) continue;
                            if (fields.contains(field.getName())) continue;
                            RestHeader rh = field.getAnnotation(RestHeader.class);
                            RestCookie rc = field.getAnnotation(RestCookie.class);
                            RestSessionid rs = field.getAnnotation(RestSessionid.class);
                            RestAddress ra = field.getAnnotation(RestAddress.class);
                            if (rh == null && rc == null && ra == null) continue;
                            if (rh != null && field.getType() != String.class) throw new RuntimeException("@RestHeader must on String Field in " + field);
                            if (rc != null && field.getType() != String.class) throw new RuntimeException("@RestCookie must on String Field in " + field);
                            if (rs != null && field.getType() != String.class) throw new RuntimeException("@RestSessionid must on String Field in " + field);
                            if (ra != null && field.getType() != String.class) throw new RuntimeException("@RestAddress must on String Field in " + field);
                            org.redkale.util.Attribute attr = org.redkale.util.Attribute.create(loop, field);
                            String attrFieldName;
                            String restname = "";
                            if (rh != null) {
                                attrFieldName = "_redkale_attr_header_" + restAttributes.size();
                                restname = rh.name();
                            } else if (rc != null) {
                                attrFieldName = "_redkale_attr_cookie_" + restAttributes.size();
                                restname = rc.name();
                            } else if (rs != null) {
                                attrFieldName = "_redkale_attr_sessionid_" + restAttributes.size();
                                restname = rs.create() ? "1" : ""; //用于下面区分create值
                            } else if (ra != null) {
                                attrFieldName = "_redkale_attr_address_" + restAttributes.size();
                                //restname = "";
                            } else {
                                continue;
                            }
                            restAttributes.put(attrFieldName, attr);
                            attrParaNames.put(attrFieldName, new Object[]{restname, field.getType()});
                            fields.add(field.getName());
                        }
                    } while ((loop = loop.getSuperclass()) != Object.class);

                    if (!attrParaNames.isEmpty()) { //参数存在 RestHeader、RestCookie、RestSessionid、RestAddress字段
                        mv.visitVarInsn(ALOAD, maxLocals);
                        Label lif = new Label();
                        mv.visitJumpInsn(IFNULL, lif);  //if(bean != null) {
                        for (Map.Entry<String, Object[]> en : attrParaNames.entrySet()) {
                            mv.visitVarInsn(ALOAD, 0);
                            mv.visitFieldInsn(GETFIELD, newDynName, en.getKey(), attrDesc);
                            mv.visitVarInsn(ALOAD, maxLocals);
                            mv.visitVarInsn(ALOAD, 1);
                            if (en.getKey().contains("_header_")) {
                                mv.visitLdcInsn(en.getValue()[0].toString());
                                mv.visitLdcInsn("");
                                mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, "getHeader", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
                            } else if (en.getKey().contains("_cookie_")) {
                                mv.visitLdcInsn(en.getValue()[0].toString());
                                mv.visitLdcInsn("");
                                mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, "getCookie", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
                            } else if (en.getKey().contains("_sessionid_")) {
                                mv.visitInsn(en.getValue()[0].toString().isEmpty() ? ICONST_0 : ICONST_1);
                                mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, "getSessionid", "(Z)Ljava/lang/String;", false);
                            } else if (en.getKey().contains("_address_")) {
                                mv.visitMethodInsn(INVOKEVIRTUAL, reqInternalName, "getRemoteAddr", "()Ljava/lang/String;", false);
                            }
                            mv.visitMethodInsn(INVOKEINTERFACE, attrInternalName, "set", "(Ljava/lang/Object;Ljava/lang/Object;)V", true);
                        }
                        mv.visitLabel(lif); // end if }
                        mv.visitFrame(Opcodes.F_APPEND, 1, new Object[]{ptype.getName().replace('.', '/')}, 0, null);
                    }
                }
                maxLocals++;
                paramMaps.add(paramMap);
            } // end params for each

            //mv.visitVarInsn(ALOAD, 0); //调用this
            //mv.visitFieldInsn(GETFIELD, newDynName, REST_SERVICE_FIELD_NAME, serviceDesc);
            mv.visitVarInsn(ALOAD, 3);
            for (int[] ins : varInsns) {
                mv.visitVarInsn(ins[0], ins[1]);
            }
            mv.visitMethodInsn(INVOKEVIRTUAL, serviceTypeInternalName, method.getName(), methodDesc, false);
            if (returnType == void.class) {
                mv.visitVarInsn(ALOAD, 2);
                mv.visitMethodInsn(INVOKESTATIC, retInternalName, "success", "()" + retDesc, false);
                mv.visitMethodInsn(INVOKEVIRTUAL, respInternalName, "finishJson", "(" + retDesc + ")V", false);
                mv.visitInsn(RETURN);
            } else if (returnType == boolean.class) {
                mv.visitVarInsn(ISTORE, maxLocals);
                if (jsvar == null) {
                    mv.visitVarInsn(ALOAD, 2); //response
                    mv.visitVarInsn(ILOAD, maxLocals);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", "(Z)Ljava/lang/String;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, respInternalName, "finish", "(Ljava/lang/String;)V", false);
                } else {
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitLdcInsn(jsvar);
                    mv.visitVarInsn(ILOAD, maxLocals);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, respInternalName, "finishJsResult", "(Ljava/lang/String;Ljava/lang/Object;)V", false);
                }
                mv.visitInsn(RETURN);
                maxLocals++;
            } else if (returnType == byte.class) {
                mv.visitVarInsn(ISTORE, maxLocals);
                if (jsvar == null) {
                    mv.visitVarInsn(ALOAD, 2); //response
                    mv.visitVarInsn(ILOAD, maxLocals);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", "(I)Ljava/lang/String;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, respInternalName, "finish", "(Ljava/lang/String;)V", false);
                } else {
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitLdcInsn(jsvar);
                    mv.visitVarInsn(ILOAD, maxLocals);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, respInternalName, "finishJsResult", "(Ljava/lang/String;Ljava/lang/Object;)V", false);
                }
                mv.visitInsn(RETURN);
                maxLocals++;
            } else if (returnType == short.class) {
                mv.visitVarInsn(ISTORE, maxLocals);
                if (jsvar == null) {
                    mv.visitVarInsn(ALOAD, 2); //response
                    mv.visitVarInsn(ILOAD, maxLocals);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", "(I)Ljava/lang/String;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, respInternalName, "finish", "(Ljava/lang/String;)V", false);
                } else {
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitLdcInsn(jsvar);
                    mv.visitVarInsn(ILOAD, maxLocals);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, respInternalName, "finishJsResult", "(Ljava/lang/String;Ljava/lang/Object;)V", false);
                }
                mv.visitInsn(RETURN);
                maxLocals++;
            } else if (returnType == char.class) {
                mv.visitVarInsn(ISTORE, maxLocals);
                if (jsvar == null) {
                    mv.visitVarInsn(ALOAD, 2); //response
                    mv.visitVarInsn(ILOAD, maxLocals);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", "(C)Ljava/lang/String;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, respInternalName, "finish", "(Ljava/lang/String;)V", false);
                } else {
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitLdcInsn(jsvar);
                    mv.visitVarInsn(ILOAD, maxLocals);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, respInternalName, "finishJsResult", "(Ljava/lang/String;Ljava/lang/Object;)V", false);
                }
                mv.visitInsn(RETURN);
                maxLocals++;
            } else if (returnType == int.class) {
                mv.visitVarInsn(ISTORE, maxLocals);
                if (jsvar == null) {
                    mv.visitVarInsn(ALOAD, 2); //response
                    mv.visitVarInsn(ILOAD, maxLocals);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", "(I)Ljava/lang/String;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, respInternalName, "finish", "(Ljava/lang/String;)V", false);
                } else {
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitLdcInsn(jsvar);
                    mv.visitVarInsn(ILOAD, maxLocals);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, respInternalName, "finishJsResult", "(Ljava/lang/String;Ljava/lang/Object;)V", false);
                }
                mv.visitInsn(RETURN);
                maxLocals++;
            } else if (returnType == float.class) {
                mv.visitVarInsn(FSTORE, maxLocals);
                if (jsvar == null) {
                    mv.visitVarInsn(ALOAD, 2); //response
                    mv.visitVarInsn(FLOAD, maxLocals);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", "(F)Ljava/lang/String;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, respInternalName, "finish", "(Ljava/lang/String;)V", false);
                } else {
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitLdcInsn(jsvar);
                    mv.visitVarInsn(FLOAD, maxLocals);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, respInternalName, "finishJsResult", "(Ljava/lang/String;Ljava/lang/Object;)V", false);
                }
                mv.visitInsn(RETURN);
                maxLocals++;
            } else if (returnType == long.class) {
                mv.visitVarInsn(LSTORE, maxLocals);
                if (jsvar == null) {
                    mv.visitVarInsn(ALOAD, 2); //response
                    mv.visitVarInsn(LLOAD, maxLocals);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", "(J)Ljava/lang/String;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, respInternalName, "finish", "(Ljava/lang/String;)V", false);
                } else {
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitLdcInsn(jsvar);
                    mv.visitVarInsn(LLOAD, maxLocals);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, respInternalName, "finishJsResult", "(Ljava/lang/String;Ljava/lang/Object;)V", false);
                }
                mv.visitInsn(RETURN);
                maxLocals += 2;
            } else if (returnType == double.class) {
                mv.visitVarInsn(DSTORE, maxLocals);
                if (jsvar == null) {
                    mv.visitVarInsn(ALOAD, 2); //response
                    mv.visitVarInsn(DLOAD, maxLocals);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", "(D)Ljava/lang/String;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, respInternalName, "finish", "(Ljava/lang/String;)V", false);
                } else {
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitLdcInsn(jsvar);
                    mv.visitVarInsn(DLOAD, maxLocals);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, respInternalName, "finishJsResult", "(Ljava/lang/String;Ljava/lang/Object;)V", false);
                }
                mv.visitInsn(RETURN);
                maxLocals += 2;
            } else if (returnType == String.class) {
                mv.visitVarInsn(ASTORE, maxLocals);
                if (jsvar == null) {
                    mv.visitVarInsn(ALOAD, 2); //response
                    mv.visitVarInsn(ALOAD, maxLocals);
                    mv.visitMethodInsn(INVOKEVIRTUAL, respInternalName, "finish", "(Ljava/lang/String;)V", false);
                } else {
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitLdcInsn(jsvar);
                    mv.visitVarInsn(ALOAD, maxLocals);
                    mv.visitMethodInsn(INVOKEVIRTUAL, respInternalName, "finishJsResult", "(Ljava/lang/String;Ljava/lang/Object;)V", false);
                }
                mv.visitInsn(RETURN);
                maxLocals++;
            } else if (returnType == File.class) {
                mv.visitVarInsn(ASTORE, maxLocals);
                if (jsvar == null) {
                    mv.visitVarInsn(ALOAD, 2); //response
                    mv.visitVarInsn(ALOAD, maxLocals);
                    mv.visitMethodInsn(INVOKEVIRTUAL, respInternalName, "finish", "(Ljava/io/File;)V", false);
                } else {
                    throw new RuntimeException(method + " cannot set return Type (java.io.File) to jsvar");
                }
                mv.visitInsn(RETURN);
                maxLocals++;
            } else if (RetResult.class.isAssignableFrom(returnType)) {
                mv.visitVarInsn(ASTORE, maxLocals);
                if (jsvar == null) {
                    mv.visitVarInsn(ALOAD, 2); //response
                    mv.visitVarInsn(ALOAD, maxLocals);
                    mv.visitMethodInsn(INVOKEVIRTUAL, respInternalName, "finishJson", "(" + retDesc + ")V", false);
                } else {
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitLdcInsn(jsvar);
                    mv.visitVarInsn(ALOAD, maxLocals);
                    mv.visitMethodInsn(INVOKEVIRTUAL, respInternalName, "finishJsResult", "(Ljava/lang/String;Ljava/lang/Object;)V", false);
                }
                mv.visitInsn(RETURN);
                maxLocals++;
            } else if (RestOutput.class.isAssignableFrom(returnType)) {
                mv.visitVarInsn(ASTORE, maxLocals);
                if (jsvar == null) {
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitVarInsn(ALOAD, maxLocals);
                    mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, "finishJson", "(" + respDesc + restoutputDesc + ")V", false);
                } else {
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitLdcInsn(jsvar);
                    mv.visitVarInsn(ALOAD, maxLocals);
                    mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, "finishJsResult", "(" + respDesc + "Ljava/lang/String;" + restoutputDesc + ")V", false);
                }
                mv.visitInsn(RETURN);
                maxLocals++;
            } else if (Number.class.isAssignableFrom(returnType) || CharSequence.class.isAssignableFrom(returnType)) {   //returnType == String.class 必须放在前面
                mv.visitVarInsn(ASTORE, maxLocals);
                if (jsvar == null) {
                    mv.visitVarInsn(ALOAD, 2); //response
                    mv.visitVarInsn(ALOAD, maxLocals);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/String", "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, respInternalName, "finish", "(Ljava/lang/String;)V", false);
                } else {
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitLdcInsn(jsvar);
                    mv.visitVarInsn(ALOAD, maxLocals);
                    mv.visitMethodInsn(INVOKEVIRTUAL, respInternalName, "finishJsResult", "(Ljava/lang/String;Ljava/lang/Object;)V", false);
                }
                mv.visitInsn(RETURN);
                maxLocals++;
            } else {
                mv.visitVarInsn(ASTORE, maxLocals);
                if (jsvar == null) {
                    mv.visitVarInsn(ALOAD, 2); //response
                    mv.visitVarInsn(ALOAD, maxLocals);
                    mv.visitMethodInsn(INVOKEVIRTUAL, respInternalName, "finishJson", "(Ljava/lang/Object;)V", false);
                } else {
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitLdcInsn(jsvar);
                    mv.visitVarInsn(ALOAD, maxLocals);
                    mv.visitMethodInsn(INVOKEVIRTUAL, respInternalName, "finishJsResult", "(Ljava/lang/String;Ljava/lang/Object;)V", false);
                }
                mv.visitInsn(RETURN);
                maxLocals++;
            }
            mv.visitMaxs(maxStack, maxLocals);
            actionMap.put("params", paramMaps);
            actionMaps.add(actionMap);
        } // end  for each 

        for (String attrname : restAttributes.keySet()) {
            fv = cw.visitField(ACC_PRIVATE, attrname, attrDesc, null, null);
            fv.visitEnd();
        }

        classMap.put("actions", actionMaps);

        { //toString函数
            mv = new AsmMethodVisitor(cw.visitMethod(ACC_PUBLIC, "toString", "()Ljava/lang/String;", null, null));
            //mv.setDebug(true);
            mv.visitLdcInsn(JsonConvert.root().convertTo(classMap));
            mv.visitInsn(ARETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }

        cw.visitEnd();
        byte[] bytes = cw.toByteArray();
        Class<?> newClazz = new ClassLoader(loader) {
            public final Class<?> loadClass(String name, byte[] b) {
                return defineClass(name, b, 0, b.length);
            }
        }.loadClass(newDynName.replace('/', '.'), bytes);
        try {
            T obj = ((Class<T>) newClazz).newInstance();
            for (Map.Entry<String, org.redkale.util.Attribute> en : restAttributes.entrySet()) {
                Field attrField = newClazz.getDeclaredField(en.getKey());
                attrField.setAccessible(true);
                attrField.set(obj, en.getValue());
            }
            return obj;
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
            this.auth = mapping.auth();
            this.actionid = mapping.actionid();
            this.cachetimeout = mapping.cachetimeout();
            //this.contentType = mapping.contentType();
            this.comment = mapping.comment();
            this.jsvar = mapping.jsvar();
        }

        public final Method mappingMethod;

        public final boolean ignore;

        public final String name;

        public final String comment;

        public final String[] methods;

        public final boolean auth;

        public final int actionid;

        public final int cachetimeout;

        //public final String contentType;
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
