/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.boot;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.redkale.convert.json.JsonConvert;
import org.redkale.net.http.*;
import org.redkale.util.*;

/**
 * 继承 HttpBaseServlet 是为了获取 WebAction 信息
 *
 * @author zhangjx
 */
public class ApiDocs extends HttpBaseServlet {

    private final Application app;

    public ApiDocs(Application app) {
        this.app = app;
    }

    public void run() throws Exception {
        List<Map> serverList = new ArrayList<>();

        Map<String, Map<String, Map<String, String>>> typesmap = new LinkedHashMap<>();
        for (NodeServer node : app.servers) {
            if (!(node instanceof NodeHttpServer)) continue;
            final Map<String, Object> map = new LinkedHashMap<>();
            serverList.add(map);
            HttpServer server = node.getServer();
            map.put("address", server.getSocketAddress());
            List<Map<String, Object>> servletsList = new ArrayList<>();
            map.put("servlets", servletsList);
            for (HttpServlet servlet : server.getPrepareServlet().getServlets()) {
                if (!(servlet instanceof HttpServlet)) continue;
                WebServlet ws = servlet.getClass().getAnnotation(WebServlet.class);
                if (ws == null) {
                    System.err.println(servlet + " not found @WebServlet");
                    continue;
                }
                final Map<String, Object> servletmap = new LinkedHashMap<>();
                String prefix = _prefix(servlet);
                String[] mappings = ws.value();
                if (prefix != null && !prefix.isEmpty()) {
                    for (int i = 0; i < mappings.length; i++) {
                        mappings[i] = prefix + mappings[i];
                    }
                }
                servletmap.put("mappings", mappings);
                servletmap.put("moduleid", ws.moduleid());
                servletmap.put("name", ws.name());
                servletmap.put("comment", ws.comment());

                List<Map> actionsList = new ArrayList<>();
                servletmap.put("actions", actionsList);
                for (Method method : servlet.getClass().getMethods()) {
                    if (method.getParameterCount() != 2) continue;
                    WebAction action = method.getAnnotation(WebAction.class);
                    if (action == null) continue;
                    final Map<String, Object> actionmap = new LinkedHashMap<>();
                    actionmap.put("url", prefix + action.url());
                    actionmap.put("auth", method.getAnnotation(AuthIgnore.class) == null);
                    actionmap.put("actionid", action.actionid());
                    actionmap.put("comment", action.comment());
                    List<Map> paramsList = new ArrayList<>();
                    actionmap.put("params", paramsList);
                    for (WebParam param : method.getAnnotationsByType(WebParam.class)) {
                        final Map<String, Object> parammap = new LinkedHashMap<>();
                        final boolean isarray = param.type().isArray();
                        final Class ptype = isarray ? param.type().getComponentType() : param.type();
                        parammap.put("name", param.name());
                        parammap.put("radix", param.radix());
                        parammap.put("type", ptype.getName() + (isarray ? "[]" : ""));
                        parammap.put("src", param.src());
                        parammap.put("comment", param.comment());
                        paramsList.add(parammap);
                        if (ptype.isPrimitive() || ptype == String.class) continue;
                        if (typesmap.containsKey(ptype.getName())) continue;

                        final Map<String, Map<String, String>> typemap = new LinkedHashMap<>();
                        Class loop = ptype;
                        do {
                            if (loop == null || loop.isInterface()) break;
                            for (Field field : loop.getDeclaredFields()) {
                                if (Modifier.isFinal(field.getModifiers())) continue;
                                if (Modifier.isStatic(field.getModifiers())) continue;

                                Map<String, String> fieldmap = new LinkedHashMap<>();
                                fieldmap.put("type", field.getType().isArray() ? (field.getType().getComponentType().getName() + "[]") : field.getGenericType().getTypeName());

                                Comment comment = field.getAnnotation(Comment.class);
                                if (comment != null) fieldmap.put("comment", comment.value());

                                if (servlet.getClass().getAnnotation(Rest.RestDynamic.class) != null) {
                                    if (field.getAnnotation(RestAddress.class) != null) continue;
                                }

                                typemap.put(field.getName(), fieldmap);
                            }
                        } while ((loop = loop.getSuperclass()) != Object.class);

                        typesmap.put(ptype.getName(), typemap);
                    }
                    actionmap.put("result", action.result());
                    actionsList.add(actionmap);
                }
                actionsList.sort((o1, o2) -> ((String) o1.get("url")).compareTo((String) o2.get("url")));
                servletsList.add(servletmap);
            }
            servletsList.sort((o1, o2) -> {
                String[] mappings1 = (String[]) o1.get("mappings");
                String[] mappings2 = (String[]) o2.get("mappings");
                return mappings1.length > 0 ? (mappings2.length > 0 ? mappings1[0].compareTo(mappings2[0]) : 1) : -1;
            });
        }
        Map<String, Object> resultmap = new LinkedHashMap<>();
        resultmap.put("servers", serverList);
        resultmap.put("types", typesmap);
        final String json = JsonConvert.root().convertTo(resultmap);
        final FileOutputStream out = new FileOutputStream(new File(app.getHome(), "apidoc.json"));
        out.write(json.getBytes("UTF-8"));
        out.close();
        File doctemplate = new File(app.getHome(), "conf/apidoc-template.html");
        InputStream in = null;
        if (doctemplate.isFile() && doctemplate.canRead()) {
            in = new FileInputStream(doctemplate);
        }
        if (in == null) in = ApiDocs.class.getResourceAsStream("apidoc-template.html");
        String content = Utility.read(in).replace("${content}", json);
        in.close();
        FileOutputStream outhtml = new FileOutputStream(new File(app.getHome(), "apidoc.html"));
        outhtml.write(content.getBytes("UTF-8"));
        outhtml.close();
    }

    @Override
    public boolean authenticate(int module, int actionid, HttpRequest request, HttpResponse response) throws IOException {
        return true;
    }
}
