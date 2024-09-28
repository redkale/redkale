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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.*;
import java.util.logging.*;
import org.redkale.annotation.Comment;
import org.redkale.convert.*;
import org.redkale.convert.json.*;
import org.redkale.net.http.*;
import org.redkale.persistence.*;
import org.redkale.service.RetResult;
import org.redkale.source.*;
import org.redkale.util.*;

/**
 * API接口文档生成类，作用：生成Application实例中所有HttpServer的可用HttpServlet的API接口方法 <br>
 * 继承 HttpBaseServlet 是为了获取 HttpMapping 信息 <br>
 * https://github.com/OAI/OpenAPI-Specification/blob/main/versions/3.1.0.md
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
public final class ApiDocCommand {

    private static final java.lang.reflect.Type TYPE_RETRESULT_OBJECT = new TypeToken<RetResult<Object>>() {}.getType();

    private static final java.lang.reflect.Type TYPE_RETRESULT_STRING = new TypeToken<RetResult<String>>() {}.getType();

    private static final java.lang.reflect.Type TYPE_RETRESULT_INTEGER =
            new TypeToken<RetResult<Integer>>() {}.getType();

    private static final java.lang.reflect.Type TYPE_RETRESULT_LONG = new TypeToken<RetResult<Long>>() {}.getType();

    // Application全局对象
    private final Application app;

    public ApiDocCommand(Application app) {
        this.app = app;
    }

    public String command(String cmd, String[] params) throws Exception {
        // 是否跳过RPC接口
        boolean skipRPC = true;
        String apiHost = "http://localhost";

        if (params != null && params.length > 0) {
            for (String param : params) {
                if (param == null) {
                    continue;
                }
                param = param.toLowerCase();
                if (param.startsWith("--api-skiprpc=")) {
                    skipRPC = "true".equalsIgnoreCase(param.substring("--api-skiprpc=".length()));
                } else if (param.startsWith("--api-host=")) {
                    apiHost = param.substring("--api-host=".length());
                }
            }
        }

        List<Map> serverList = new ArrayList<>();
        Field prefixField = HttpServlet.class.getDeclaredField("_prefix");
        prefixField.setAccessible(true);
        Map<String, Map<String, Map<String, Object>>> typesMap = new LinkedHashMap<>();
        // https://github.com/OAI/OpenAPI-Specification/blob/main/versions/3.1.0.md
        Map<String, Object> swaggerPathsMap = new LinkedHashMap<>();
        List<Map> swaggerServers = new ArrayList<>();
        List<Map> swaggerTags = new ArrayList<>();
        Map<String, Map<String, Object>> swaggerComponentsMap = new LinkedHashMap<>();
        for (NodeServer node : app.servers) {
            if (!(node instanceof NodeHttpServer)) {
                continue;
            }
            final Map<String, Object> map = new LinkedHashMap<>();
            serverList.add(map);
            HttpServer server = node.getServer();
            map.put("address", server.getSocketAddress());
            swaggerServers.add(Utility.ofMap(
                    "url", apiHost + ":" + server.getSocketAddress().getPort()));
            List<Map<String, Object>> servletsList = new ArrayList<>();
            map.put("servlets", servletsList);
            String plainContentType = server.getResponseConfig() == null
                    ? "application/json"
                    : server.getResponseConfig().plainContentType;
            if (plainContentType == null || plainContentType.isEmpty()) {
                plainContentType = "application/json";
            }
            if (plainContentType.indexOf(';') > 0) {
                plainContentType = plainContentType.substring(0, plainContentType.indexOf(';'));
            }

            for (HttpServlet servlet : server.getDispatcherServlet().getServlets()) {
                if (!(servlet instanceof HttpServlet)) {
                    continue;
                }
                if (servlet instanceof WebSocketServlet) {
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
                final String tag = ws.name().isEmpty()
                        ? servlet.getClass()
                                .getSimpleName()
                                .replace("Servlet", "")
                                .toLowerCase()
                        : ws.name();
                final Map<String, Object> servletMap = new LinkedHashMap<>();
                String prefix = (String) prefixField.get(servlet);
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
                    if (Modifier.isAbstract(clz.getModifiers())) {
                        break;
                    }
                    for (Method method : clz.getMethods()) {
                        if (method.getParameterCount() != 2) {
                            continue;
                        }
                        HttpMapping action = method.getAnnotation(HttpMapping.class);
                        if (action == null) {
                            continue;
                        }
                        if (!action.inherited() && selfClz != clz) {
                            continue; // 忽略不被继承的方法
                        }
                        if (actionUrls.contains(action.url())) {
                            continue;
                        }
                        if (HttpScope.class.isAssignableFrom(action.result())) {
                            continue; // 忽略模板引擎的方法
                        }
                        if (action.rpcOnly() && skipRPC) {
                            continue; // 不生成RPC接口
                        }
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
                        if (!action.resultRef().isEmpty()) {
                            Field f = servlet.getClass().getDeclaredField(action.resultRef());
                            f.setAccessible(true);
                            resultType = (Type) f.get(servlet);
                        }
                        //                        for (final Class rtype : action.results()) {
                        //                            results.add(rtype.getName());
                        //                            if (typesMap.containsKey(rtype.getName())) continue;
                        //                            if (rtype.getName().startsWith("java.")) continue;
                        //                            if (rtype.getName().startsWith("javax.")) continue;
                        //                            final boolean filter = FilterBean.class.isAssignableFrom(rtype);
                        //                            final Map<String, Map<String, Object>> typeMap = new
                        // LinkedHashMap<>();
                        //                            Class loop = rtype;
                        //                            do {
                        //                                if (loop == null || loop.isInterface()) break;
                        //                                for (Field field : loop.getDeclaredFields()) {
                        //                                    if (Modifier.isFinal(field.getModifiers())) continue;
                        //                                    if (Modifier.isStatic(field.getModifiers())) continue;
                        //
                        //                                    Map<String, Object> fieldmap = new LinkedHashMap<>();
                        //                                    fieldmap.put("type", field.getType().isArray() ?
                        // (field.getType().getComponentType().getName() + "[]") :
                        // field.getGenericType().getTypeName());
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
                        //                                    fieldmap.put("primary", !filter &&
                        // (field.getAnnotation(Id.class) != null));
                        //                                    fieldmap.put("updatable", (filter || col == null ||
                        // col.updatable()));
                        //                                    if (servlet.getClass().getAnnotation(Rest.RestDyn.class)
                        // != null) {
                        //                                        if (field.getAnnotation(RestAddress.class) != null)
                        // continue;
                        //                                    }
                        //
                        //                                    typeMap.put(field.getName(), fieldmap);
                        //                                }
                        //                            } while ((loop = loop.getSuperclass()) != Object.class);
                        //                            typesMap.put(rtype.getName(), typeMap);
                        //                        }
                        mappingMap.put("results", results);
                        boolean hasBodyParam = false;
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
                                simpleSchemaType(
                                        null,
                                        node.getLogger(),
                                        swaggerComponentsMap,
                                        param.type(),
                                        paramGenericType,
                                        paramSchemaMap,
                                        true);
                                if (param.style() == HttpParam.HttpParameterStyle.BODY) {
                                    swaggerRequestBody.put("description", param.comment());
                                    swaggerRequestBody.put(
                                            "content",
                                            Utility.ofMap(plainContentType, Utility.ofMap("schema", paramSchemaMap)));
                                } else {
                                    final Map<String, Object> swaggerParamMap = new LinkedHashMap<>();
                                    swaggerParamMap.put("name", param.name());
                                    swaggerParamMap.put(
                                            "in", param.style().name().toLowerCase());
                                    swaggerParamMap.put("description", param.comment());
                                    swaggerParamMap.put("required", param.required());
                                    if (param.deprecated()) {
                                        swaggerParamMap.put("deprecated", param.deprecated());
                                    }
                                    // https://github.com/OAI/OpenAPI-Specification/blob/main/versions/3.1.0.md#parameterStyle
                                    swaggerParamMap.put(
                                            "style",
                                            param.style() == HttpParam.HttpParameterStyle.HEADER
                                                            || param.name().indexOf('#') == 0
                                                    ? "simple"
                                                    : "form");
                                    swaggerParamMap.put("explode", true);
                                    swaggerParamMap.put("schema", paramSchemaMap);
                                    Object example =
                                            formatExample(null, param.example(), param.type(), paramGenericType);
                                    if (example != null) {
                                        swaggerParamMap.put("example", example);
                                    } else if (!param.example().isEmpty()) {
                                        swaggerParamMap.put("example", param.example());
                                    }
                                    swaggerParamsList.add(swaggerParamMap);
                                }
                            }
                            if (param.style() == HttpParam.HttpParameterStyle.BODY) {
                                hasBodyParam = true;
                            }
                            if (ptype.isPrimitive() || ptype == String.class) {
                                continue;
                            }
                            if (typesMap.containsKey(ptype.getName())) {
                                continue;
                            }
                            if (ptype.getName().startsWith("java.")) {
                                continue;
                            }
                            if (ptype.getName().startsWith("javax.")) {
                                continue;
                            }

                            final Map<String, Map<String, Object>> typeMap = new LinkedHashMap<>();
                            Class loop = ptype;
                            final boolean filter = FilterBean.class.isAssignableFrom(loop);
                            do {
                                if (loop == null || loop.isInterface()) {
                                    break;
                                }
                                for (Field field : loop.getDeclaredFields()) {
                                    if (Modifier.isFinal(field.getModifiers())) {
                                        continue;
                                    }
                                    if (Modifier.isStatic(field.getModifiers())) {
                                        continue;
                                    }

                                    Map<String, Object> fieldmap = new LinkedHashMap<>();
                                    fieldmap.put(
                                            "type",
                                            field.getType().isArray()
                                                    ? (field.getType()
                                                                    .getComponentType()
                                                                    .getName() + "[]")
                                                    : field.getGenericType().getTypeName());

                                    Column col = field.getAnnotation(Column.class);
                                    Comment comment = field.getAnnotation(Comment.class);
                                    org.redkale.util.Comment comment2 =
                                            field.getAnnotation(org.redkale.util.Comment.class);
                                    if (comment != null) {
                                        fieldmap.put("comment", comment.value());
                                    } else if (comment2 != null) {
                                        fieldmap.put("comment", comment2.value());
                                    } else if (col != null) {
                                        fieldmap.put("comment", col.comment());
                                    }
                                    fieldmap.put(
                                            "primary",
                                            !filter
                                                    && (field.getAnnotation(Id.class) != null
                                                            || field.getAnnotation(javax.persistence.Id.class)
                                                                    != null));
                                    fieldmap.put("updatable", (filter || col == null || col.updatable()));

                                    if (servlet.getClass().getAnnotation(Rest.RestDyn.class) != null) {
                                        if (field.getAnnotation(RestAddress.class) != null) {
                                            continue;
                                        }
                                    }

                                    typeMap.put(field.getName(), fieldmap);
                                }
                            } while ((loop = loop.getSuperclass()) != Object.class);

                            typesMap.put(ptype.getName(), typeMap);
                        }
                        mappingMap.put("result", action.result().getSimpleName().replace("void", "Object"));
                        mappingsList.add(mappingMap);

                        final Map<String, Object> swaggerOperatMap = new LinkedHashMap<>();
                        swaggerOperatMap.put("tags", new String[] {tag});
                        swaggerOperatMap.put("operationId", action.name());
                        if (method.getAnnotation(Deprecated.class) != null) {
                            swaggerOperatMap.put("deprecated", true);
                        }
                        Map<String, Object> respSchemaMap = new LinkedHashMap<>();
                        JsonFactory returnFactory = Rest.createJsonFactory(
                                0,
                                method.getAnnotationsByType(RestConvert.class),
                                method.getAnnotationsByType(RestConvertCoder.class));
                        simpleSchemaType(
                                returnFactory,
                                node.getLogger(),
                                swaggerComponentsMap,
                                action.result(),
                                resultType,
                                respSchemaMap,
                                true);

                        Map<String, Object> respMap = new LinkedHashMap<>();
                        respMap.put("schema", respSchemaMap);
                        Object example = formatExample(returnFactory, action.example(), action.result(), resultType);
                        if (example != null) {
                            respSchemaMap.put("example", example);
                        }
                        if (!swaggerRequestBody.isEmpty()) {
                            swaggerOperatMap.put("requestBody", swaggerRequestBody);
                        }
                        swaggerOperatMap.put("parameters", swaggerParamsList);
                        String actiondesc = action.comment();
                        if (action.rpcOnly()) {
                            actiondesc = "[Only for RPC API] " + actiondesc;
                        }
                        swaggerOperatMap.put(
                                "responses",
                                Utility.ofMap(
                                        "200",
                                        Utility.ofMap(
                                                "description",
                                                actiondesc,
                                                "content",
                                                Utility.ofMap("application/json", respMap))));

                        String m = action.methods() == null || action.methods().length == 0
                                ? null
                                : action.methods()[0].toLowerCase();
                        if (m == null) {
                            m = hasBodyParam
                                            || TYPE_RETRESULT_STRING.equals(resultType)
                                            || TYPE_RETRESULT_INTEGER.equals(resultType)
                                            || TYPE_RETRESULT_LONG.equals(resultType)
                                            || action.name().contains("create")
                                            || action.name().contains("insert")
                                            || action.name().contains("update")
                                            || action.name().contains("delete")
                                            || action.name().contains("send")
                                    ? "post"
                                    : "get";
                        }
                        swaggerPathsMap.put(
                                prefix + action.url(),
                                Utility.ofMap("description", action.comment(), m, swaggerOperatMap));
                    }
                } while ((clz = clz.getSuperclass()) != HttpServlet.class);
                mappingsList.sort((o1, o2) -> ((String) o1.get("url")).compareTo((String) o2.get("url")));
                servletsList.add(servletMap);
                if (!actionUrls.isEmpty()) {
                    swaggerTags.add(Utility.ofMap("name", tag, "description", ws.comment()));
                }
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
            if (!swaggerComponentsMap.isEmpty()) {
                swaggerResultMap.put("components", Utility.ofMap("schemas", swaggerComponentsMap));
            }
            try (FileOutputStream out = new FileOutputStream(new File(app.getHome(), "openapi-doc.json"))) {
                out.write(JsonConvert.root().convertTo(swaggerResultMap).getBytes(StandardCharsets.UTF_8));
            }
        }
        {
            Map<String, Object> oldapisResultMap = new LinkedHashMap<>();
            oldapisResultMap.put("servers", serverList);
            oldapisResultMap.put("types", typesMap);
            final String json = JsonConvert.root().convertTo(oldapisResultMap);
            try (FileOutputStream out = new FileOutputStream(new File(app.getHome(), "apidoc.json"))) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            }
            File doctemplate = new File(app.getConfDir().toString(), "apidoc-template.html");
            InputStream in = null;
            if (doctemplate.isFile() && doctemplate.canRead()) {
                in = new FileInputStream(doctemplate);
            }
            if (in != null) {
                String content = Utility.read(in).replace("'#{content}'", json);
                in.close();
                try (FileOutputStream outhtml = new FileOutputStream(new File(app.getHome(), "apidoc.html"))) {
                    outhtml.write(content.getBytes(StandardCharsets.UTF_8));
                }
            }
        }
        return "apidoc success";
    }

    private static void simpleSchemaType(
            JsonFactory factory,
            Logger logger,
            Map<String, Map<String, Object>> componentsMap,
            Class type,
            Type genericType,
            Map<String, Object> schemaMap,
            boolean recursive) {
        if (type == int.class || type == Integer.class || type == AtomicInteger.class) {
            schemaMap.put("type", "integer");
            schemaMap.put("format", "int32");
        } else if (type == long.class
                || type == Long.class
                || type == AtomicLong.class
                || type == LongAdder.class
                || type == BigInteger.class) {
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
                simpleSchemaType(
                        factory,
                        logger,
                        componentsMap,
                        type.getComponentType(),
                        type.getComponentType(),
                        sbumap,
                        false);
            } else if (genericType instanceof ParameterizedType) {
                Type subpt = ((ParameterizedType) genericType).getActualTypeArguments()[0];
                if (subpt instanceof Class) {
                    simpleSchemaType(factory, logger, componentsMap, (Class) subpt, subpt, sbumap, false);
                } else if (subpt instanceof ParameterizedType
                        && ((ParameterizedType) subpt).getOwnerType() instanceof Class) {
                    simpleSchemaType(
                            factory,
                            logger,
                            componentsMap,
                            (Class) ((ParameterizedType) subpt).getOwnerType(),
                            subpt,
                            sbumap,
                            false);
                } else {
                    sbumap.put("type", "object");
                }
            } else {
                sbumap.put("type", "object");
            }
            schemaMap.put("items", sbumap);
        } else if (!type.getName().startsWith("java.") && !type.getName().startsWith("javax.")) {
            String ct = simpleComponentType(factory, logger, componentsMap, type, genericType);
            if (ct == null) {
                schemaMap.put("type", "object");
            } else {
                schemaMap.put("$ref", "#/components/schemas/" + ct);
            }
        } else {
            schemaMap.put("type", "object");
        }
    }

    private static String simpleComponentType(
            JsonFactory factory,
            Logger logger,
            Map<String, Map<String, Object>> componentsMap,
            Class type,
            Type genericType) {
        try {
            Set<Type> types = new HashSet<>();
            Encodeable encodeable = JsonFactory.root().loadEncoder(genericType);
            String ct = componentKey(factory, logger, types, componentsMap, null, encodeable, true);
            if (ct == null || ct.length() == 0) {
                return null;
            }
            if (componentsMap.containsKey(ct)) {
                return ct;
            }
            Map<String, Object> cmap = new LinkedHashMap<>();
            componentsMap.put(ct, cmap); // 必须在调用simpleSchemaType之前put，不然嵌套情况下死循环

            cmap.put("type", "object");
            List<String> requireds = new ArrayList<>();
            Map<String, Object> properties = new LinkedHashMap<>();
            if (encodeable instanceof ObjectEncoder) {
                for (EnMember member : ((ObjectEncoder) encodeable).getMembers()) {
                    Map<String, Object> schemaMap = new LinkedHashMap<>();
                    simpleSchemaType(
                            factory,
                            logger,
                            componentsMap,
                            TypeToken.typeToClassOrElse(member.getEncoder().getType(), Object.class),
                            member.getEncoder().getType(),
                            schemaMap,
                            true);
                    String desc = "";
                    if (member.getField() != null) {
                        Column col = member.getField().getAnnotation(Column.class);
                        if (col != null) {
                            desc = col.comment();
                            if (!col.nullable()) {
                                requireds.add(member.getFieldName());
                            }
                        }
                        if (desc.isEmpty() && member.getField().getAnnotation(Comment.class) != null) {
                            desc = member.getField()
                                    .getAnnotation(Comment.class)
                                    .value();
                        } else if (desc.isEmpty()
                                && member.getField().getAnnotation(org.redkale.util.Comment.class) != null) {
                            desc = member.getField()
                                    .getAnnotation(org.redkale.util.Comment.class)
                                    .value();
                        }
                    } else if (member.getMethod() != null) {
                        Column col = member.getMethod().getAnnotation(Column.class);
                        if (col != null) {
                            desc = col.comment();
                            if (!col.nullable()) {
                                requireds.add(member.getFieldName());
                            }
                        }
                        if (desc.isEmpty() && member.getMethod().getAnnotation(Comment.class) != null) {
                            desc = member.getMethod()
                                    .getAnnotation(Comment.class)
                                    .value();
                        } else if (desc.isEmpty()
                                && member.getMethod().getAnnotation(org.redkale.util.Comment.class) != null) {
                            desc = member.getMethod()
                                    .getAnnotation(org.redkale.util.Comment.class)
                                    .value();
                        }
                    }
                    if (!desc.isEmpty()) {
                        schemaMap.put("description", desc);
                    }
                    properties.put(member.getFieldName(), schemaMap);
                }
            }
            if (!requireds.isEmpty()) {
                cmap.put("required", requireds);
            }
            cmap.put("properties", properties);
            return ct;
        } catch (Exception e) {
            logger.log(Level.WARNING, genericType + " generate component info error", e);
            return null;
        }
    }

    private static String componentKey(
            JsonFactory factory,
            Logger logger,
            Set<Type> types,
            Map<String, Map<String, Object>> componentsMap,
            EnMember field,
            Encodeable encodeable,
            boolean first) {
        if (encodeable instanceof ObjectEncoder) {
            if (types.contains(encodeable.getType())) {
                return "";
            }
            types.add(encodeable.getType());
            StringBuilder sb = new StringBuilder();
            sb.append(((ObjectEncoder) encodeable).getTypeClass().getSimpleName());
            for (EnMember member : ((ObjectEncoder) encodeable).getMembers()) {
                if (member.getEncoder() instanceof ArrayEncoder || member.getEncoder() instanceof CollectionEncoder) {
                    String subsb =
                            componentKey(factory, logger, types, componentsMap, member, member.getEncoder(), false);
                    if (subsb == null) {
                        return null;
                    }
                    AccessibleObject real = member.getField() == null ? member.getMethod() : member.getField();
                    if (real == null) {
                        continue;
                    }
                    Class cz = real instanceof Field ? ((Field) real).getType() : ((Method) real).getReturnType();
                    Type ct = real instanceof Field
                            ? ((Field) real).getGenericType()
                            : ((Method) real).getGenericReturnType();
                    if (cz == ct) {
                        continue;
                    }
                    if (field == null && encodeable.getType() instanceof Class) {
                        continue;
                    }
                    if (sb.length() > 0 && subsb.length() > 0) {
                        sb.append("_");
                    }
                    sb.append(subsb);
                } else if (member.getEncoder() instanceof ObjectEncoder
                        || member.getEncoder() instanceof SimpledCoder) {
                    AccessibleObject real = member.getField() == null ? member.getMethod() : member.getField();
                    if (real == null) {
                        continue;
                    }
                    if (types.contains(member.getEncoder().getType())) {
                        continue;
                    }
                    types.add(member.getEncoder().getType());
                    if (member.getEncoder() instanceof SimpledCoder) {
                        simpleSchemaType(
                                factory,
                                logger,
                                componentsMap,
                                ((SimpledCoder) member.getEncoder()).getType(),
                                ((SimpledCoder) member.getEncoder()).getType(),
                                new LinkedHashMap<>(),
                                true);
                    } else {
                        simpleSchemaType(
                                factory,
                                logger,
                                componentsMap,
                                ((ObjectEncoder) member.getEncoder()).getTypeClass(),
                                ((ObjectEncoder) member.getEncoder()).getType(),
                                new LinkedHashMap<>(),
                                true);
                    }
                    Class cz = real instanceof Field ? ((Field) real).getType() : ((Method) real).getReturnType();
                    Type ct = real instanceof Field
                            ? ((Field) real).getGenericType()
                            : ((Method) real).getGenericReturnType();
                    if (cz == ct) {
                        continue;
                    }
                    String subsb =
                            componentKey(factory, logger, types, componentsMap, member, member.getEncoder(), false);
                    if (subsb == null) {
                        return null;
                    }
                    if (field == null && member.getEncoder().getType() instanceof Class) {
                        continue;
                    }
                    if (sb.length() > 0 && subsb.length() > 0) {
                        sb.append("_");
                    }
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
            Encodeable subEncodeable = array
                    ? ((ArrayEncoder) encodeable).getComponentEncoder()
                    : ((CollectionEncoder) encodeable).getComponentEncoder();
            if (subEncodeable instanceof SimpledCoder && field != null) {
                return "";
            }
            final String sb = componentKey(factory, logger, types, componentsMap, null, subEncodeable, false);
            if (sb == null || sb.isEmpty()) {
                return sb;
            }
            if (field != null && field.getField() != null && field.getField().getDeclaringClass() == Sheet.class) {
                return sb;
            }
            return sb + (array ? "_Array" : "_Collection");
        } else if (encodeable instanceof SimpledCoder) {
            Class stype = ((SimpledCoder) encodeable).getType();
            if (stype.isPrimitive()
                    || stype == Boolean.class
                    || Number.class.isAssignableFrom(stype)
                    || CharSequence.class.isAssignableFrom(stype)) {
                return stype.getSimpleName();
            }
            return "";
        } else if (encodeable instanceof MapEncoder) {
            return first ? null : "";
        } else {
            return null;
        }
    }

    private static Object formatExample(JsonFactory factory, String example, Class type, Type genericType) {
        if (example != null && !example.isEmpty()) {
            return example;
        }
        JsonFactory jsonFactory = factory == null || factory == JsonFactory.root() ? exampleFactory : factory;
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
        } else if (type == boolean.class || type == Boolean.class) {
            return true;
        } else if (type.isPrimitive()) {
            return 0;
        } else if (type == boolean[].class || type == Boolean[].class) {
            return new boolean[] {true, false};
        } else if (type == byte[].class || type == Byte[].class) {
            return new byte[] {0, 0};
        } else if (type == char[].class || type == Character[].class) {
            return new char[] {'a', 'b'};
        } else if (type == short[].class || type == Short[].class) {
            return new short[] {0, 0};
        } else if (type == int[].class || type == Integer[].class) {
            return new int[] {0, 0};
        } else if (type == long[].class || type == Long[].class) {
            return new long[] {0, 0};
        } else if (type == float[].class || type == Float[].class) {
            return new float[] {0, 0};
        } else if (type == double[].class || type == Double[].class) {
            return new double[] {0, 0};
        } else if (Number.class.isAssignableFrom(type)) {
            return 0;
        } else if (CharSequence.class.isAssignableFrom(type)) {
            return "";
        } else if (CompletableFuture.class.isAssignableFrom(type)) {
            if (genericType instanceof ParameterizedType) {
                try {
                    ParameterizedType pt = (ParameterizedType) genericType;
                    Type valType = pt.getActualTypeArguments()[0];
                    return formatExample(
                            factory,
                            example,
                            valType instanceof ParameterizedType
                                    ? (Class) ((ParameterizedType) valType).getRawType()
                                    : ((Class) valType),
                            valType);
                } catch (Throwable t) {
                    // do nothing
                }
            }
        } else if (Sheet.class.isAssignableFrom(type)) { // 要在Collection前面
            if (genericType instanceof ParameterizedType) {
                try {
                    ParameterizedType pt = (ParameterizedType) genericType;
                    Type valType = pt.getActualTypeArguments()[0];
                    Class valClass = valType instanceof ParameterizedType
                            ? (Class) ((ParameterizedType) valType).getRawType()
                            : (Class) valType;
                    Object val = formatExample(factory, example, valClass, valType);
                    return new StringWrapper(jsonFactory
                            .getConvert()
                            .convertTo(jsonFactory
                                    .getConvert()
                                    .convertFrom(genericType, "{'rows':[" + val + "," + val + "]}")));
                } catch (Throwable t) {
                    // do nothing
                }
            }
        } else if (type.isArray()) {
            try {
                Object val = formatExample(factory, example, type.getComponentType(), type.getComponentType());
                return new StringWrapper(jsonFactory
                        .getConvert()
                        .convertTo(jsonFactory.getConvert().convertFrom(genericType, "[" + val + "," + val + "]")));
            } catch (Throwable t) {
                // do nothing
            }
        } else if (Collection.class.isAssignableFrom(type)) {
            if (genericType instanceof ParameterizedType) {
                try {
                    ParameterizedType pt = (ParameterizedType) genericType;
                    Type valType = pt.getActualTypeArguments()[0];
                    Class valClass = valType instanceof ParameterizedType
                            ? (Class) ((ParameterizedType) valType).getRawType()
                            : (Class) valType;
                    Object val = formatExample(factory, example, valClass, valType);
                    return new StringWrapper(jsonFactory
                            .getConvert()
                            .convertTo(jsonFactory.getConvert().convertFrom(genericType, "[" + val + "," + val + "]")));
                } catch (Throwable t) {
                    // do nothing
                }
            }
        } else if (type == RetResult.class) {
            if (genericType instanceof ParameterizedType) {
                try {
                    ParameterizedType pt = (ParameterizedType) genericType;
                    Type valType = pt.getActualTypeArguments()[0];
                    Class valClass = valType instanceof ParameterizedType
                            ? (Class) ((ParameterizedType) valType).getRawType()
                            : (Class) valType;
                    Object val = formatExample(factory, example, valClass, valType);
                    return new StringWrapper(jsonFactory
                            .getConvert()
                            .convertTo(jsonFactory.getConvert().convertFrom(genericType, "{'result':" + val + "}")));
                } catch (Throwable t) {
                    // do nothing
                }
            }
        } else if (type != void.class) {
            try {
                Decodeable decoder = jsonFactory.loadDecoder(genericType);
                if (decoder instanceof ObjectDecoder) {
                    StringBuilder json = new StringBuilder();
                    json.append("{");
                    int index = 0;
                    for (DeMember member : ((ObjectDecoder) decoder).getMembers()) {
                        if (!(member.getDecoder() instanceof ObjectDecoder)) {
                            continue;
                        }
                        if (index > 0) {
                            json.append(",");
                        }
                        json.append('"').append(member.getFieldName()).append("\":{}");
                        index++;
                    }
                    json.append("}");
                    Object val = jsonFactory.getConvert().convertFrom(genericType, json.toString());
                    return new StringWrapper(jsonFactory.getConvert().convertTo(val));
                }
                Creator creator = Creator.create(type);
                return new StringWrapper(jsonFactory.getConvert().convertTo(creator.create()));
            } catch (Throwable t) {
                // do nothing
            }
        }
        return example;
    }

    private static final JsonFactory exampleFactory = JsonFactory.create().withFeatures(0);
}
