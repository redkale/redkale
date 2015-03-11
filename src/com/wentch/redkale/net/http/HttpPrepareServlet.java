/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.http;

import com.wentch.redkale.net.PrepareServlet;
import com.wentch.redkale.net.Context;
import com.wentch.redkale.util.AnyValue;
import com.wentch.redkale.util.Utility;
import com.wentch.redkale.util.AnyValue.DefaultAnyValue;
import com.wentch.redkale.watch.WatchFactory;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;
import java.util.function.Predicate; 
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 *
 * @author zhangjx
 */
public final class HttpPrepareServlet extends PrepareServlet<HttpRequest, HttpResponse> {

    private ByteBuffer flashPolicyBuffer;

    private final List<SimpleEntry<HttpServlet, AnyValue>> servlets = new ArrayList<>();

    private final Map<String, HttpServlet> strmaps = new HashMap<>();

    private SimpleEntry<Predicate<String>, HttpServlet>[] regArray = new SimpleEntry[0];

    private boolean forwardproxy = false;

    private final HttpServlet proxyServlet = new HttpProxyServlet();

    private HttpServlet resourceHttpServlet = new HttpResourceServlet();

    private String flashdomain = "*";

    private String flashports = "80,443,$";

    @Override
    public void init(Context context, AnyValue config) {
        this.servlets.stream().forEach((en) -> {
            en.getKey().init(context, en.getValue());
        });
        final WatchFactory watch = ((HttpContext) context).getWatchFactory();
        if (watch != null) {
            this.servlets.stream().forEach((en) -> {
                watch.inject(en.getKey());
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
            this.flashdomain = config.getValue("crossdomain-domain", "*");
            this.flashports = config.getValue("crossdomain-ports", "80,443,$");
            this.forwardproxy = config.getBoolValue("forwardproxy", false);
            if (forwardproxy) this.proxyServlet.init(context, config);
        }
    }

    @Override
    public void execute(HttpRequest request, HttpResponse response) throws IOException {
        try {
            if (request.flashPolicy) {
                response.skipHeader();
                if (flashPolicyBuffer == null) {
                    flashPolicyBuffer = ByteBuffer.wrap(("<?xml version=\"1.0\"?>"
                            + "<!DOCTYPE cross-domain-policy SYSTEM \"http://www.macromedia.com/xml/dtds/cross-domain-policy.dtd\">"
                            + "<cross-domain-policy><allow-access-from domain=\"" + flashdomain + "\"  to-ports=\""
                            + flashports.replace("$", "" + request.getContext().getServerAddress().getPort()) + "\"/>"
                            + "</cross-domain-policy>").getBytes()).asReadOnlyBuffer();
                }
                response.finish(flashPolicyBuffer.duplicate(), true);
                return;
            }
            final String uri = request.getRequestURI();
            if (forwardproxy && (uri.charAt(0) != '/' || "CONNECT".equalsIgnoreCase(request.getMethod()))) { //正向代理
                proxyServlet.execute(request, response);
                return;
            }
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
            request.getContext().getLogger().log(Level.WARNING, "Servlet occur, forece to close channel ", e);
            response.finish(505, null);
        }
    }

    public void addHttpServlet(HttpServlet servlet, AnyValue conf, String... mappings) {
        for (String mapping : mappings) {
            if (contains(mapping, '.', '*', '{', '[', '(', '|', '^', '$', '+', '?', '\\')) { //是否是正则表达式))
                if (mapping.charAt(0) != '^') mapping = '^' + mapping;
                if (mapping.endsWith("/*")) {
                    mapping = mapping.substring(0, mapping.length() - 1) + ".*";
                } else {
                    mapping += "$";
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
        this.servlets.add(new SimpleEntry<>(servlet, conf));
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
    public void destroy(Context context, AnyValue config) {
        this.resourceHttpServlet.destroy(context, config);
        this.servlets.stream().forEach((en) -> {
            en.getKey().destroy(context, en.getValue());
        });
    }

}
