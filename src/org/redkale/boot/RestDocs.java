/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.boot;

import java.io.*;
import java.lang.reflect.Method;
import java.util.*;
import org.redkale.convert.json.JsonConvert;
import org.redkale.net.http.*;

/**
 * 继承 HttpBaseServlet 是为了获取 WebAction 信息
 *
 * @author zhangjx
 */
public class RestDocs extends HttpBaseServlet {

    private final Application app;

    public RestDocs(Application app) {
        this.app = app;
    }

    public void run() throws Exception {
        List<Map> serverList = new ArrayList<>();
        for (NodeServer node : app.servers) {
            if (!(node instanceof NodeHttpServer)) continue;
            final Map<String, Object> map = new LinkedHashMap<>();
            serverList.add(map);
            HttpServer server = node.getServer();
            map.put("address", server.getSocketAddress());
            List<Map> servletsList = new ArrayList<>();
            map.put("servlets", servletsList);
            for (HttpServlet servlet : server.getPrepareServlet().getServlets()) {
                if (!(servlet instanceof RestHttpServlet)) continue;
                WebServlet ws = servlet.getClass().getAnnotation(WebServlet.class);
                if (ws == null) {
                    System.err.println(servlet + " not found @WebServlet");
                    continue;
                }
                final Map<String, Object> servletmap = new LinkedHashMap<>();
                servletmap.put("mappings", ws.value());
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
                    actionmap.put("url", action.url());
                    actionmap.put("auth", method.getAnnotation(AuthIgnore.class) == null);
                    actionmap.put("actionid", action.actionid());
                    actionmap.put("comment", action.comment());
                    List<Map> paramsList = new ArrayList<>();
                    actionmap.put("params", paramsList);
                    for (WebParam param : action.params()) {
                        final Map<String, Object> parammap = new LinkedHashMap<>();
                        parammap.put("name", param.value());
                        parammap.put("radix", param.radix());
                        parammap.put("type", param.type().getName());
                        parammap.put("src", param.src());
                        paramsList.add(parammap);
                    }
                    actionsList.add(actionmap);
                }
                servletsList.add(servletmap);
            }
        }
        final FileOutputStream out = new FileOutputStream(new File(app.getHome(), "restdoc.json"));
        out.write(JsonConvert.root().convertTo(serverList).getBytes("UTF-8"));
        out.close();
    }

    @Override
    public boolean authenticate(int module, int actionid, HttpRequest request, HttpResponse response) throws IOException {
        return true;
    }
}
