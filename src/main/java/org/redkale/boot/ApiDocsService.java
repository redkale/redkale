/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.boot;

import java.io.*;
import java.lang.reflect.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;
import javax.persistence.*;
import org.redkale.convert.*;
import org.redkale.convert.json.*;
import org.redkale.mq.MessageMultiConsumer;
import org.redkale.net.http.*;
import org.redkale.service.RetResult;
import org.redkale.source.*;
import org.redkale.util.*;

/**
 * API接口文档生成类，作用：生成Application实例中所有HttpServer的可用HttpServlet的API接口方法   <br>
 * 继承 HttpBaseServlet 是为了获取 HttpMapping 信息 <br>
 * https://github.com/OAI/OpenAPI-Specification/blob/main/versions/3.1.0.md
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public final class ApiDocsService {

    private static final java.lang.reflect.Type TYPE_RETRESULT_OBJECT = new TypeToken<RetResult<Object>>() {
    }.getType();

    private static final java.lang.reflect.Type TYPE_RETRESULT_STRING = new TypeToken<RetResult<String>>() {
    }.getType();

    private static final java.lang.reflect.Type TYPE_RETRESULT_INTEGER = new TypeToken<RetResult<Integer>>() {
    }.getType();

    private static final java.lang.reflect.Type TYPE_RETRESULT_LONG = new TypeToken<RetResult<Long>>() {
    }.getType();

    private final Application app; //Application全局对象

    public ApiDocsService(Application app) {
        this.app = app;
    }

    public void run(String[] args) throws Exception {
        //是否跳过RPC接口
        final boolean skipRPC = Arrays.toString(args).toLowerCase().contains("skip-rpc") && !Arrays.toString(args).toLowerCase().contains("skip-rpc=false");

        List<Map> serverList = new ArrayList<>();
        Field __prefix = HttpServlet.class.getDeclaredField("_prefix");
        __prefix.setAccessible(true);
        Map<String, Map<String, Map<String, Object>>> typesMap = new LinkedHashMap<>();
        //https://github.com/OAI/OpenAPI-Specification/blob/main/versions/3.1.0.md
        Map<String, Object> swaggerPathsMap = new LinkedHashMap<>();
        List<Map> swaggerServers = new ArrayList<>();
        List<Map> swaggerTags = new ArrayList<>();
        Map<String, Map<String, Object>> swaggerComponentsMap = new LinkedHashMap<>();
        for (NodeServer node : app.servers) {
            if (!(node instanceof NodeHttpServer)) continue;
            final Map<String, Object> map = new LinkedHashMap<>();
            serverList.add(map);
            HttpServer server = node.getServer();
            map.put("address", server.getSocketAddress());
            swaggerServers.add(Utility.ofMap("url", "http://localhost:" + server.getSocketAddress().getPort()));
            List<Map<String, Object>> servletsList = new ArrayList<>();
            map.put("servlets", servletsList);
            String plainContentType = server.getResponseConfig() == null ? "application/json" : server.getResponseConfig().plainContentType;
            if (plainContentType == null || plainContentType.isEmpty()) plainContentType = "application/json";
            if (plainContentType.indexOf(';') > 0) plainContentType = plainContentType.substring(0, plainContentType.indexOf(';'));

            for (HttpServlet servlet : server.getPrepareServlet().getServlets()) {
                if (!(servlet instanceof HttpServlet)) continue;
                if (servlet instanceof WebSocketServlet) continue;
                if (servlet.getClass().getAnnotation(MessageMultiConsumer.class) != null) {
                    node.logger.log(Level.INFO, servlet + " be skipped because has @MessageMultiConsumer");
                    continue;
                }
                WebServlet ws = servlet.getClass().getAnnotation(WebServlet.class);
                if (ws == null) {
                    node.logger.log(Level.WARNING, servlet + " not found @WebServlet");
                    continue;
                }
                if (ws.name().isEmpty()) {
                    node.logger.log(Level.INFO, servlet + " be skipped because @WebServlet.name is empty");
                    continue;
                }
                final String tag = ws.name().isEmpty() ? servlet.getClass().getSimpleName().replace("Servlet", "").toLowerCase() : ws.name();
                final Map<String, Object> servletMap = new LinkedHashMap<>();
                String prefix = (String) __prefix.get(servlet);
                String[] urlregs = ws.value();
                if (prefix != null && !prefix.isEmpty()) {
                    for (int i = 0; i < urlregs.length; i++) {
                        urlregs[i] = prefix + urlregs[i];
                    }
                }
                servletMap.put("urlregs", urlregs);
                servletMap.put("moduleid", ws.moduleid());
                servletMap.put("name", ws.name());
                servletMap.put("comment", ws.comment());

                List<Map> mappingsList = new ArrayList<>();
                servletMap.put("mappings", mappingsList);
                final Class selfClz = servlet.getClass();
                Class clz = servlet.getClass();
                HashSet<String> actionUrls = new HashSet<>();
                do {
                    if (Modifier.isAbstract(clz.getModifiers())) break;
                    for (Method method : clz.getMethods()) {
                        if (method.getParameterCount() != 2) continue;
                        HttpMapping action = method.getAnnotation(HttpMapping.class);
                        if (action == null) continue;
                        if (!action.inherited() && selfClz != clz) continue; //忽略不被继承的方法
                        if (actionUrls.contains(action.url())) continue;
                        if (HttpScope.class.isAssignableFrom(action.result())) continue; //忽略模板引擎的方法
                        if (action.rpconly() && skipRPC) continue; //不生成RPC接口

                        final List<Map<String, Object>> swaggerParamsList = new ArrayList<>();

                        final Map<String, Object> mappingMap = new LinkedHashMap<>();
                        mappingMap.put("url", prefix + action.url());
                        actionUrls.add(action.url());
                        mappingMap.put("auth", action.auth());
                        mappingMap.put("actionid", action.actionid());
                        mappingMap.put("comment", action.comment());
                        List<Map> paramsList = new ArrayList<>();
                        mappingMap.put("params", paramsList);
                        List<String> results = new ArrayList<>();
                        Type resultType = action.result();
                        if (!action.resultref().isEmpty()) {
                            Field f = servlet.getClass().getDeclaredField(action.resultref());
                            f.setAccessible(true);
                            resultType = (Type) f.get(servlet);
                        }
//                        for (final Class rtype : action.results()) {
//                            results.add(rtype.getName());
//                            if (typesMap.containsKey(rtype.getName())) continue;
//                            if (rtype.getName().startsWith("java.")) continue;
//                            if (rtype.getName().startsWith("javax.")) continue;
//                            final boolean filter = FilterBean.class.isAssignableFrom(rtype);
//                            final Map<String, Map<String, Object>> typeMap = new LinkedHashMap<>();
//                            Class loop = rtype;
//                            do {
//                                if (loop == null || loop.isInterface()) break;
//                                for (Field field : loop.getDeclaredFields()) {
//                                    if (Modifier.isFinal(field.getModifiers())) continue;
//                                    if (Modifier.isStatic(field.getModifiers())) continue;
//
//                                    Map<String, Object> fieldmap = new LinkedHashMap<>();
//                                    fieldmap.put("type", field.getType().isArray() ? (field.getType().getComponentType().getName() + "[]") : field.getGenericType().getTypeName());
//
//                                    Comment comment = field.getAnnotation(Comment.class);
//                                    Column col = field.getAnnotation(Column.class);
//                                    FilterColumn fc = field.getAnnotation(FilterColumn.class);
//                                    if (comment != null) {
//                                        fieldmap.put("comment", comment.value());
//                                    } else if (col != null) {
//                                        fieldmap.put("comment", col.comment());
//                                    } else if (fc != null) {
//                                        fieldmap.put("comment", fc.comment());
//                                    }
//                                    fieldmap.put("primary", !filter && (field.getAnnotation(Id.class) != null));
//                                    fieldmap.put("updatable", (filter || col == null || col.updatable()));
//                                    if (servlet.getClass().getAnnotation(Rest.RestDyn.class) != null) {
//                                        if (field.getAnnotation(RestAddress.class) != null) continue;
//                                    }
//
//                                    typeMap.put(field.getName(), fieldmap);
//                                }
//                            } while ((loop = loop.getSuperclass()) != Object.class);
//                            typesMap.put(rtype.getName(), typeMap);
//                        }
                        mappingMap.put("results", results);
                        boolean hasbodyparam = false;
                        Map<String, Object> swaggerRequestBody = new LinkedHashMap<>();
                        for (HttpParam param : method.getAnnotationsByType(HttpParam.class)) {
                            final Map<String, Object> oldapisParamMap = new LinkedHashMap<>();
                            final boolean isarray = param.type().isArray();
                            final Class ptype = isarray ? param.type().getComponentType() : param.type();
                            oldapisParamMap.put("name", param.name());
                            oldapisParamMap.put("radix", param.radix());
                            oldapisParamMap.put("type", ptype.getName() + (isarray ? "[]" : ""));
                            oldapisParamMap.put("style", param.style());
                            oldapisParamMap.put("comment", param.comment());
                            oldapisParamMap.put("required", param.required());
                            paramsList.add(oldapisParamMap);
                            {
                                final Map<String, Object> paramSchemaMap = new LinkedHashMap<>();
                                Type paramGenericType = param.type();
                                if (!param.typeref().isEmpty()) {
                                    Field f = servlet.getClass().getDeclaredField(param.typeref());
                                    f.setAccessible(true);
                                    paramGenericType = (Type) f.get(servlet);
                                }
                                simpleSchemaType(node.getLogger(), swaggerComponentsMap, param.type(), paramGenericType, paramSchemaMap, true);
                                if (param.style() == HttpParam.HttpParameterStyle.BODY) {
                                    swaggerRequestBody.put("description", param.comment());
                                    swaggerRequestBody.put("content", Utility.ofMap(plainContentType, Utility.ofMap("schema", paramSchemaMap)));
                                } else {
                                    final Map<String, Object> swaggerParamMap = new LinkedHashMap<>();
                                    swaggerParamMap.put("name", param.name());
                                    swaggerParamMap.put("in", param.style().name().toLowerCase());
                                    swaggerParamMap.put("description", param.comment());
                                    swaggerParamMap.put("required", param.required());
                                    if (param.deprecated()) {
                                        swaggerParamMap.put("deprecated", param.deprecated());
                                    }
                                    //https://github.com/OAI/OpenAPI-Specification/blob/main/versions/3.1.0.md#parameterStyle
                                    swaggerParamMap.put("style", param.style() == HttpParam.HttpParameterStyle.HEADER || param.name().indexOf('#') == 0 ? "simple" : "form");
                                    swaggerParamMap.put("explode", true);
                                    swaggerParamMap.put("schema", paramSchemaMap);
                                    Object example = formatExample(param.example(), param.type(), paramGenericType);
                                    if (example != null) swaggerParamMap.put("example", example);
                                    if (!param.example().isEmpty()) {
                                        swaggerParamMap.put("example", param.example());
                                    }
                                    swaggerParamsList.add(swaggerParamMap);
                                }
                            }
                            if (param.style() == HttpParam.HttpParameterStyle.BODY) hasbodyparam = true;
                            if (ptype.isPrimitive() || ptype == String.class) continue;
                            if (typesMap.containsKey(ptype.getName())) continue;
                            if (ptype.getName().startsWith("java.")) continue;
                            if (ptype.getName().startsWith("javax.")) continue;

                            final Map<String, Map<String, Object>> typeMap = new LinkedHashMap<>();
                            Class loop = ptype;
                            final boolean filter = FilterBean.class.isAssignableFrom(loop);
                            do {
                                if (loop == null || loop.isInterface()) break;
                                for (Field field : loop.getDeclaredFields()) {
                                    if (Modifier.isFinal(field.getModifiers())) continue;
                                    if (Modifier.isStatic(field.getModifiers())) continue;

                                    Map<String, Object> fieldmap = new LinkedHashMap<>();
                                    fieldmap.put("type", field.getType().isArray() ? (field.getType().getComponentType().getName() + "[]") : field.getGenericType().getTypeName());

                                    Column col = field.getAnnotation(Column.class);
                                    FilterColumn fc = field.getAnnotation(FilterColumn.class);
                                    Comment comment = field.getAnnotation(Comment.class);
                                    if (comment != null) {
                                        fieldmap.put("comment", comment.value());
                                    } else if (col != null) {
                                        fieldmap.put("comment", col.comment());
                                    } else if (fc != null) {
                                        fieldmap.put("comment", fc.comment());
                                    }
                                    fieldmap.put("primary", !filter && (field.getAnnotation(Id.class) != null));
                                    fieldmap.put("updatable", (filter || col == null || col.updatable()));

                                    if (servlet.getClass().getAnnotation(Rest.RestDyn.class) != null) {
                                        if (field.getAnnotation(RestAddress.class) != null) continue;
                                    }

                                    typeMap.put(field.getName(), fieldmap);
                                }
                            } while ((loop = loop.getSuperclass()) != Object.class);

                            typesMap.put(ptype.getName(), typeMap);
                        }
                        mappingMap.put("result", action.result().getSimpleName().replace("void", "Object"));
                        mappingsList.add(mappingMap);

                        final Map<String, Object> swaggerOperatMap = new LinkedHashMap<>();
                        swaggerOperatMap.put("tags", new String[]{tag});
                        swaggerOperatMap.put("operationId", action.name());
                        if (method.getAnnotation(Deprecated.class) != null) {
                            swaggerOperatMap.put("deprecated", true);
                        }
                        Map<String, Object> respSchemaMap = new LinkedHashMap<>();
                        simpleSchemaType(node.getLogger(), swaggerComponentsMap, action.result(), resultType, respSchemaMap, true);

                        Map<String, Object> respMap = new LinkedHashMap<>();
                        respMap.put("schema", respSchemaMap);
                        Object example = formatExample(action.example(), action.result(), resultType);
                        if (example != null) swaggerOperatMap.put("example", example);
                        if (!swaggerRequestBody.isEmpty()) swaggerOperatMap.put("requestBody", swaggerRequestBody);
                        swaggerOperatMap.put("parameters", swaggerParamsList);
                        String actiondesc = action.comment();
                        if (action.rpconly()) actiondesc = "[Only for RPC API] " + actiondesc;
                        swaggerOperatMap.put("responses", Utility.ofMap("200", Utility.ofMap("description", actiondesc, "content", Utility.ofMap("application/json", respMap))));

                        String m = action.methods() == null || action.methods().length == 0 ? null : action.methods()[0].toLowerCase();
                        if (m == null) {
                            m = hasbodyparam || TYPE_RETRESULT_STRING.equals(resultType) || TYPE_RETRESULT_INTEGER.equals(resultType)
                                || TYPE_RETRESULT_LONG.equals(resultType) || action.name().contains("create") || action.name().contains("insert")
                                || action.name().contains("update") || action.name().contains("delete") || action.name().contains("send") ? "post" : "get";
                        }
                        swaggerPathsMap.put(prefix + action.url(), Utility.ofMap("description", action.comment(), m, swaggerOperatMap));
                    }
                } while ((clz = clz.getSuperclass()) != HttpServlet.class);
                mappingsList.sort((o1, o2) -> ((String) o1.get("url")).compareTo((String) o2.get("url")));
                servletsList.add(servletMap);
                if (!actionUrls.isEmpty()) swaggerTags.add(Utility.ofMap("name", tag, "description", ws.comment()));
            }
            servletsList.sort((o1, o2) -> {
                String[] urlregs1 = (String[]) o1.get("urlregs");
                String[] urlregs2 = (String[]) o2.get("urlregs");
                return urlregs1.length > 0 ? (urlregs2.length > 0 ? urlregs1[0].compareTo(urlregs2[0]) : 1) : -1;
            });
        }
        { // https://github.com/OAI/OpenAPI-Specification 
            Map<String, Object> swaggerResultMap = new LinkedHashMap<>();
            swaggerResultMap.put("openapi", "3.0.0");
            Map<String, Object> infomap = new LinkedHashMap<>();
            infomap.put("title", "Redkale generate apidoc");
            infomap.put("version", "1.0.0");
            swaggerResultMap.put("info", infomap);
            swaggerResultMap.put("servers", swaggerServers);
            swaggerResultMap.put("paths", swaggerPathsMap);
            swaggerResultMap.put("tags", swaggerTags);
            if (!swaggerComponentsMap.isEmpty()) swaggerResultMap.put("components", Utility.ofMap("schemas", swaggerComponentsMap));
            final FileOutputStream out = new FileOutputStream(new File(app.getHome(), "openapi-doc.json"));
            out.write(JsonConvert.root().convertTo(swaggerResultMap).getBytes(StandardCharsets.UTF_8));
            out.close();
        }
        {
            Map<String, Object> oldapisResultMap = new LinkedHashMap<>();
            oldapisResultMap.put("servers", serverList);
            oldapisResultMap.put("types", typesMap);
            final String json = JsonConvert.root().convertTo(oldapisResultMap);
            final FileOutputStream out = new FileOutputStream(new File(app.getHome(), "apidoc.json"));
            out.write(json.getBytes(StandardCharsets.UTF_8));
            out.close();
            File doctemplate = new File(app.getConfPath().toString(), "apidoc-template.html");
            InputStream in = null;
            if (doctemplate.isFile() && doctemplate.canRead()) {
                in = new FileInputStream(doctemplate);
            }
            if (in == null) in = ApiDocsService.class.getResourceAsStream("apidoc-template.html");
            String content = Utility.read(in).replace("'${content}'", json);
            in.close();
            FileOutputStream outhtml = new FileOutputStream(new File(app.getHome(), "apidoc.html"));
            outhtml.write(content.getBytes(StandardCharsets.UTF_8));
            outhtml.close();
        }
    }

    private static void simpleSchemaType(Logger logger, Map<String, Map<String, Object>> componentsMap, Class type, Type genericType, Map<String, Object> schemaMap, boolean recursive) {
        if (type == int.class || type == Integer.class || type == AtomicInteger.class) {
            schemaMap.put("type", "integer");
            schemaMap.put("format", "int32");
        } else if (type == long.class || type == Long.class
            || type == AtomicLong.class || type == LongAdder.class || type == BigInteger.class) {
            schemaMap.put("type", "integer");
            schemaMap.put("format", "int64");
        } else if (type == float.class || type == Float.class) {
            schemaMap.put("type", "number");
            schemaMap.put("format", "float");
        } else if (type == double.class || type == Double.class || type == BigDecimal.class) {
            schemaMap.put("type", "number");
            schemaMap.put("format", "double");
        } else if (type == boolean.class || type == Boolean.class || type == AtomicBoolean.class) {
            schemaMap.put("type", "boolean");
        } else if (type.isPrimitive() || Number.class.isAssignableFrom(type)) {
            schemaMap.put("type", "number");
        } else if (type == String.class || CharSequence.class.isAssignableFrom(type)) {
            schemaMap.put("type", "string");
        } else if (recursive && (type.isArray() || Collection.class.isAssignableFrom(type))) {
            schemaMap.put("type", "array");
            Map<String, Object> sbumap = new LinkedHashMap<>();
            if (type.isArray()) {
                simpleSchemaType(logger, componentsMap, type.getComponentType(), type.getComponentType(), sbumap, false);
            } else if (genericType instanceof ParameterizedType) {
                Type subpt = ((ParameterizedType) genericType).getActualTypeArguments()[0];
                if (subpt instanceof Class) {
                    simpleSchemaType(logger, componentsMap, (Class) subpt, subpt, sbumap, false);
                } else if (subpt instanceof ParameterizedType && ((ParameterizedType) subpt).getOwnerType() instanceof Class) {
                    simpleSchemaType(logger, componentsMap, (Class) ((ParameterizedType) subpt).getOwnerType(), subpt, sbumap, false);
                } else {
                    sbumap.put("type", "object");
                }
            } else {
                sbumap.put("type", "object");
            }
            schemaMap.put("items", sbumap);
        } else if (!type.getName().startsWith("java.") && !type.getName().startsWith("javax.")) {
            String ct = simpleComponentType(logger, componentsMap, type, genericType);
            if (ct == null) {
                schemaMap.put("type", "object");
            } else {
                schemaMap.put("$ref", "#/components/schemas/" + ct);
            }
        } else {
            schemaMap.put("type", "object");
        }
    }

    private static String simpleComponentType(Logger logger, Map<String, Map<String, Object>> componentsMap, Class type, Type genericType) {
        try {
            Encodeable encodeable = JsonFactory.root().loadEncoder(genericType);
            String ct = componentKey(logger, componentsMap, null, encodeable, true);
            if (ct == null || ct.length() == 0) return null;
            if (componentsMap.containsKey(ct)) return ct;
            Map<String, Object> cmap = new LinkedHashMap<>();
            componentsMap.put(ct, cmap); //必须在调用simpleSchemaType之前put，不然嵌套情况下死循环

            cmap.put("type", "object");
            List<String> requireds = new ArrayList<>();
            Map<String, Object> properties = new LinkedHashMap<>();
            if (encodeable instanceof ObjectEncoder) {
                for (EnMember member : ((ObjectEncoder) encodeable).getMembers()) {
                    Map<String, Object> schemaMap = new LinkedHashMap<>();
                    simpleSchemaType(logger, componentsMap, TypeToken.typeToClassOrElse(member.getEncoder().getType(), Object.class), member.getEncoder().getType(), schemaMap, true);
                    String desc = "";
                    if (member.getField() != null) {
                        Column col = member.getField().getAnnotation(Column.class);
                        if (col == null) {
                            FilterColumn fcol = member.getField().getAnnotation(FilterColumn.class);
                            if (fcol != null) {
                                desc = fcol.comment();
                                if (fcol.required()) requireds.add(member.getAttribute().field());
                            }
                        } else {
                            desc = col.comment();
                            if (!col.nullable()) requireds.add(member.getAttribute().field());
                        }
                        if (desc.isEmpty() && member.getField().getAnnotation(Comment.class) != null) {
                            desc = member.getField().getAnnotation(Comment.class).value();
                        }
                    } else if (member.getMethod() != null) {
                        Column col = member.getMethod().getAnnotation(Column.class);
                        if (col == null) {
                            FilterColumn fcol = member.getMethod().getAnnotation(FilterColumn.class);
                            if (fcol != null) {
                                desc = fcol.comment();
                                if (fcol.required()) requireds.add(member.getAttribute().field());
                            }
                        } else {
                            desc = col.comment();
                            if (!col.nullable()) requireds.add(member.getAttribute().field());
                        }
                        if (desc.isEmpty() && member.getMethod().getAnnotation(Comment.class) != null) {
                            desc = member.getMethod().getAnnotation(Comment.class).value();
                        }
                    }
                    if (!desc.isEmpty()) schemaMap.put("description", desc);
                    properties.put(member.getAttribute().field(), schemaMap);
                }
            }
            if (!requireds.isEmpty()) cmap.put("required", requireds);
            cmap.put("properties", properties);
            return ct;
        } catch (Exception e) {
            logger.log(Level.WARNING, genericType + " generate component info error", e);
            return null;
        }
    }

    private static String componentKey(Logger logger, Map<String, Map<String, Object>> componentsMap, EnMember field, Encodeable encodeable, boolean first) {
        if (encodeable instanceof ObjectEncoder) {
            StringBuilder sb = new StringBuilder();
            sb.append(((ObjectEncoder) encodeable).getTypeClass().getSimpleName());
            for (EnMember member : ((ObjectEncoder) encodeable).getMembers()) {
                if (member.getEncoder() instanceof ArrayEncoder
                    || member.getEncoder() instanceof CollectionEncoder) {
                    String subsb = componentKey(logger, componentsMap, member, member.getEncoder(), false);
                    if (subsb == null) return null;
                    AccessibleObject real = member.getField() == null ? member.getMethod() : member.getField();
                    if (real == null) continue;
                    Class cz = real instanceof Field ? ((Field) real).getType() : ((Method) real).getReturnType();
                    Type ct = real instanceof Field ? ((Field) real).getGenericType() : ((Method) real).getGenericReturnType();
                    if (cz == ct) continue;
                    if (sb.length() > 0 && subsb.length() > 0) sb.append("_");
                    sb.append(subsb);
                } else if (member.getEncoder() instanceof ObjectEncoder || member.getEncoder() instanceof SimpledCoder) {
                    AccessibleObject real = member.getField() == null ? member.getMethod() : member.getField();
                    if (real == null) continue;
                    if (member.getEncoder() instanceof SimpledCoder) {
                        simpleSchemaType(logger, componentsMap, ((SimpledCoder) member.getEncoder()).getType(), ((SimpledCoder) member.getEncoder()).getType(), new LinkedHashMap<>(), true);
                    } else {
                        simpleSchemaType(logger, componentsMap, ((ObjectEncoder) member.getEncoder()).getTypeClass(), ((ObjectEncoder) member.getEncoder()).getType(), new LinkedHashMap<>(), true);
                    }
                    Class cz = real instanceof Field ? ((Field) real).getType() : ((Method) real).getReturnType();
                    Type ct = real instanceof Field ? ((Field) real).getGenericType() : ((Method) real).getGenericReturnType();
                    if (cz == ct) continue;
                    String subsb = componentKey(logger, componentsMap, member, member.getEncoder(), false);
                    if (subsb == null) return null;
                    if (sb.length() > 0 && subsb.length() > 0) sb.append("_");
                    sb.append(subsb);
                } else if (member.getEncoder() instanceof MapEncoder) {
                    continue;
                } else {
                    return null;
                }
            }
            return sb.toString();
        } else if (encodeable instanceof ArrayEncoder || encodeable instanceof CollectionEncoder) {
            final boolean array = (encodeable instanceof ArrayEncoder);
            Encodeable subEncodeable = array ? ((ArrayEncoder) encodeable).getComponentEncoder() : ((CollectionEncoder) encodeable).getComponentEncoder();
            if (subEncodeable instanceof SimpledCoder && field != null) return "";
            final String sb = componentKey(logger, componentsMap, null, subEncodeable, false);
            if (sb == null || sb.isEmpty()) return sb;
            if (field != null && field.getField() != null && field.getField().getDeclaringClass() == Sheet.class) {
                return sb;
            }
            return sb + (array ? "_Array" : "_Collection");
        } else if (encodeable instanceof SimpledCoder) {
            Class stype = ((SimpledCoder) encodeable).getType();
            if (stype.isPrimitive() || stype == Boolean.class || Number.class.isAssignableFrom(stype) || CharSequence.class.isAssignableFrom(stype)) {
                return stype.getSimpleName();
            }
            return "";
        } else if (encodeable instanceof MapEncoder) {
            return first ? null : "";
        } else {
            return null;
        }
    }

    private static Object formatExample(String example, Class type, Type genericType) {
        if (example == null || example.isEmpty()) return null;
        if (type == Flipper.class) {
            return new Flipper();
        } else if (TYPE_RETRESULT_OBJECT.equals(genericType)) {
            return RetResult.success();
        } else if (TYPE_RETRESULT_STRING.equals(genericType)) {
            return RetResult.success();
        } else if (TYPE_RETRESULT_INTEGER.equals(genericType)) {
            return RetResult.success(0);
        } else if (TYPE_RETRESULT_LONG.equals(genericType)) {
            return RetResult.success(0L);
        }
        return example;
    }

}
