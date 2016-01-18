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
 * 详情见: http://www.redkale.org
 *
 * @author zhangjx
 */
public final class HttpPrepareServlet extends PrepareServlet<HttpContext, HttpRequest, HttpResponse> {

    private final List<HttpServlet> servlets = new ArrayList<>();

    private final Map<String, HttpServlet> strmaps = new HashMap<>();

    private SimpleEntry<Predicate<String>, HttpServlet>[] regArray = new SimpleEntry[0];

    private HttpServlet resourceHttpServlet = new HttpResourceServlet();

    @Override
    public void init(HttpContext context, AnyValue config) {
        this.servlets.stream().forEach(s -> {
            s.init(context, s._conf);
        });
        final WatchFactory watch = context.getWatchFactory();
        if (watch != null) {
            this.servlets.stream().forEach(s -> {
                watch.inject(s);
            });
        }
        if (config != null) {
            AnyValue ssConfig = config.getAnyValue("servlets");
            if (ssConfig != null) {
                AnyValue resConfig = ssConfig.getAnyValue("resource-servlet");
                if (resConfig instanceof DefaultAnyValue) {
                    if (resConfig.getValue("webroot") == null)
                        ((DefaultAnyValue) resConfig).addValue("webroot", config.getValue("root"));
                }
                if (resConfig == null) {
                    DefaultAnyValue dresConfig = new DefaultAnyValue();
                    dresConfig.addValue("webroot", config.getValue("root"));
                    resConfig = dresConfig;
                }
                this.resourceHttpServlet.init(context, resConfig);
            }
        }
    }

    @Override
    public void execute(HttpRequest request, HttpResponse response) throws IOException {
        try {
            final String uri = request.getRequestURI();
            HttpServlet servlet = this.strmaps.isEmpty() ? null : this.strmaps.get(uri);
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

    public void addHttpServlet(HttpServlet servlet, String prefix, AnyValue conf, String... mappings) {
        if (prefix == null) prefix = "";
        for (String mapping : mappings) {
            if (!prefix.isEmpty()) mapping = prefix + mapping;
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
                strmaps.put(mapping, servlet);
            }
        }
        servlet._conf = conf;
        servlet._prefix = prefix == null ? "" : prefix;
        this.servlets.add(servlet);
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

    @Override
    public void destroy(HttpContext context, AnyValue config) {
        this.resourceHttpServlet.destroy(context, config);
        this.servlets.stream().forEach(s -> {
            s.destroy(context, s._conf);
        });
    }

}
