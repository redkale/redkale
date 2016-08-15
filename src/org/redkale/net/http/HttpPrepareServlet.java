/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import org.redkale.util.AnyValue.DefaultAnyValue;
import java.io.*;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;
import java.util.function.*;
import java.util.logging.*;
import java.util.regex.*;
import org.redkale.net.*;
import org.redkale.util.*;
import org.redkale.watch.*;

/**
 *
 * <p>
 * 详情见: http://redkale.org
 *
 * @author zhangjx
 */
public final class HttpPrepareServlet extends PrepareServlet<String, HttpContext, HttpRequest, HttpResponse, HttpServlet> {

    private SimpleEntry<Predicate<String>, HttpServlet>[] regArray = new SimpleEntry[0];

    private HttpServlet resourceHttpServlet = new HttpResourceServlet();

    private final Map<String, Class> mapStrings = new HashMap<>();

    @Override
    public void init(HttpContext context, AnyValue config) {
        this.servlets.forEach(s -> {
            if (s instanceof WebSocketServlet) {
                ((WebSocketServlet) s).preInit(context, getServletConf(s));
            } else if (s instanceof BasedHttpServlet) {
                ((BasedHttpServlet) s).preInit(context, getServletConf(s));
            }
            s.init(context, getServletConf(s));
        });
        final WatchFactory watch = context.getWatchFactory();
        if (watch != null) {
            this.servlets.forEach(s -> {
                watch.inject(s);
            });
        }
        if (config != null) {
            AnyValue ssConfig = config.getAnyValue("servlets");
            AnyValue resConfig = null;
            if (ssConfig != null) {
                resConfig = ssConfig.getAnyValue("resource-servlet");
                if ((resConfig instanceof DefaultAnyValue) && resConfig.getValue("webroot") == null) {
                    ((DefaultAnyValue) resConfig).addValue("webroot", config.getValue("root"));
                }
            }
            if (resConfig == null) resConfig = config.getAnyValue("resource-servlet"); //兼容
            if (resConfig == null) {
                DefaultAnyValue dresConfig = new DefaultAnyValue();
                dresConfig.addValue("webroot", config.getValue("root"));
                dresConfig.addValue("ranges", config.getValue("ranges"));
                dresConfig.addValue("cache", config.getAnyValue("cache"));
                AnyValue[] rewrites = config.getAnyValues("rewrite");
                if (rewrites != null) {
                    for (AnyValue rewrite : rewrites) {
                        dresConfig.addValue("rewrite", rewrite);
                    }
                }
                resConfig = dresConfig;
            }
            this.resourceHttpServlet.init(context, resConfig);
        }
    }

    @Override
    public void execute(HttpRequest request, HttpResponse response) throws IOException {
        try {
            final String uri = request.getRequestURI();
            Servlet<HttpContext, HttpRequest, HttpResponse> servlet = this.mappings.isEmpty() ? null : this.mappings.get(uri);
            if (servlet == null && this.regArray != null) {
                for (SimpleEntry<Predicate<String>, HttpServlet> en : regArray) {
                    if (en.getKey().test(uri)) {
                        servlet = en.getValue();
                        break;
                    }
                }
            }
            if (servlet == null) servlet = this.resourceHttpServlet;
            servlet.execute(request, response);
        } catch (Exception e) {
            request.getContext().getLogger().log(Level.WARNING, "Servlet occur, forece to close channel. request = " + request, e);
            response.finish(500, null);
        }
    }

    @Override
    public void addServlet(HttpServlet servlet, Object prefix, AnyValue conf, String... mappings) {
        if (prefix == null) prefix = "";
        if (mappings.length < 1) {
            WebServlet ws = servlet.getClass().getAnnotation(WebServlet.class);
            if (ws != null) {
                mappings = ws.value();
                if (!ws.repair()) prefix = "";//被设置为自动追加前缀则清空prefix
            }
        }
        synchronized (mapStrings) {
            for (String mapping : mappings) {
                if(mapping == null) continue;
                if (!prefix.toString().isEmpty()) mapping = prefix + mapping;
                if (this.mapStrings.containsKey(mapping)) {
                    Class old = this.mapStrings.get(mapping);
                    throw new RuntimeException("mapping [" + mapping + "] repeat on " + old.getName() + " and " + servlet.getClass().getName());
                }
                if (contains(mapping, '.', '*', '{', '[', '(', '|', '^', '$', '+', '?', '\\')) { //是否是正则表达式))
                    if (mapping.charAt(0) != '^') mapping = '^' + mapping;
                    if (mapping.endsWith("/*")) {
                        mapping = mapping.substring(0, mapping.length() - 1) + ".*";
                    } else {
                        mapping = mapping + "$";
                    }
                    if (regArray == null) {
                        regArray = new SimpleEntry[1];
                        regArray[0] = new SimpleEntry<>(Pattern.compile(mapping).asPredicate(), servlet);
                    } else {
                        regArray = Arrays.copyOf(regArray, regArray.length + 1);
                        regArray[regArray.length - 1] = new SimpleEntry<>(Pattern.compile(mapping).asPredicate(), servlet);
                    }
                } else if (mapping != null && !mapping.isEmpty()) {
                    super.mappings.put(mapping, servlet);
                }
                this.mapStrings.put(mapping, servlet.getClass());
            }
            setServletConf(servlet, conf);
            servlet._prefix = prefix.toString();
            this.servlets.add(servlet);
        }
    }

    private static boolean contains(String string, char... values) {
        if (string == null) return false;
        for (char ch : Utility.charArray(string)) {
            for (char ch2 : values) {
                if (ch == ch2) return true;
            }
        }
        return false;
    }

    public void setResourceServlet(HttpServlet servlet) {
        if (servlet != null) {
            this.resourceHttpServlet = servlet;
        }
    }

    public HttpServlet getResourceServlet() {
        return this.resourceHttpServlet;
    }

    @Override
    public void destroy(HttpContext context, AnyValue config) {
        this.resourceHttpServlet.destroy(context, config);
        this.servlets.forEach(s -> {
            s.destroy(context, getServletConf(s));
            if (s instanceof WebSocketServlet) {
                ((WebSocketServlet) s).postDestroy(context, getServletConf(s));
            } else if (s instanceof BasedHttpServlet) {
                ((BasedHttpServlet) s).postDestroy(context, getServletConf(s));
            }
        });
        this.mapStrings.clear();
    }

}
