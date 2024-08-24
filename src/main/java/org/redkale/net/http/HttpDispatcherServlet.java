/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.*;
import java.util.logging.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.redkale.annotation.Nonnull;
import org.redkale.net.*;
import org.redkale.net.Filter;
import org.redkale.net.http.Rest.RestDynSourceType;
import org.redkale.service.Service;
import org.redkale.util.*;

/**
 * HTTP Servlet的总入口，请求在HttpDispatcherServlet中进行分流。 <br>
 * 一个HttpServer只有一个HttpDispatcherServlet， 用于管理所有HttpServlet。 <br>
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class HttpDispatcherServlet
        extends DispatcherServlet<String, HttpContext, HttpRequest, HttpResponse, HttpServlet> {

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    protected HttpServlet resourceHttpServlet = new HttpResourceServlet();

    protected MappingEntry[] regexArray = null; // regexArray 包含 regexWsArray

    protected MappingEntry[] regexWsArray = null;

    protected Map<String, WebSocketServlet> wsMappings = new HashMap<>(); // super.mappings 包含 wsMappings

    protected final Map<String, Class> allMapStrings = new HashMap<>();

    private final ReentrantLock allMapLock = new ReentrantLock();

    private final ReentrantLock excludeLock = new ReentrantLock();

    protected HttpContext context;

    private Map<String, BiPredicate<String, String>> forbidURIMaps; // 禁用的URL的正则表达式, 必须与 forbidURIPredicates 保持一致

    private BiPredicate<String, String>[] forbidURIPredicates; // 禁用的URL的Predicate, 必须与 forbidURIMaps 保持一致

    private HttpServlet lastRunServlet;

    protected HttpDispatcherServlet() {
        super();
    }

    private List<HttpServlet> removeHttpServlet(
            final Predicate<MappingEntry> predicateEntry,
            final Predicate<Map.Entry<String, WebSocketServlet>> predicateFilter) {
        List<HttpServlet> servlets = new ArrayList<>();
        allMapLock.lock();
        try {
            List<String> keys = new ArrayList<>();
            if (regexArray != null) {
                for (MappingEntry me : regexArray) {
                    if (predicateEntry.test(me)) {
                        servlets.add(me.servlet);
                        keys.add(me.mapping);
                    }
                }
            }
            if (regexWsArray != null) {
                for (MappingEntry me : regexWsArray) {
                    if (predicateEntry.test(me)) {
                        servlets.add(me.servlet);
                        keys.add(me.mapping);
                    }
                }
            }
            Map<String, WebSocketServlet> newWsMappings = new HashMap<>();
            for (Map.Entry<String, WebSocketServlet> en : wsMappings.entrySet()) {
                if (predicateFilter.test(en)) {
                    servlets.add(en.getValue());
                    keys.add(en.getKey());
                } else {
                    newWsMappings.put(en.getKey(), en.getValue());
                }
            }
            if (newWsMappings.size() != wsMappings.size()) {
                this.wsMappings = newWsMappings;
            }
            if (!keys.isEmpty()) {
                this.regexArray = Utility.remove(this.regexArray, predicateEntry);
                this.regexWsArray = Utility.remove(this.regexWsArray, predicateEntry);
                for (HttpServlet rs : servlets) {
                    super.removeServlet(rs);
                }
                for (String key : keys) {
                    super.removeMapping(key);
                    allMapStrings.remove(key);
                }
            }
            this.lastRunServlet = null;
        } finally {
            allMapLock.unlock();
        }
        return servlets;
    }

    public HttpServlet removeHttpServlet(final HttpServlet servlet) {
        Predicate<MappingEntry> predicateEntry = t -> t.servlet == servlet;
        Predicate<Map.Entry<String, WebSocketServlet>> predicateFilter = t -> t.getValue() == servlet;
        removeHttpServlet(predicateEntry, predicateFilter);
        return servlet;
    }

    public HttpServlet removeHttpServlet(Service service) {
        Predicate<MappingEntry> predicateEntry = t -> {
            if (!Rest.isRestDyn(t.servlet)) {
                return false;
            }
            Service s = Rest.getService(t.servlet);
            if (s == service) {
                return true;
            }
            if (s != null) {
                return false;
            }
            Map<String, Service> map = Rest.getServiceMap(t.servlet);
            if (map == null) {
                return false;
            }
            boolean rs = map.values().contains(service);
            if (rs && map.size() == 1) {
                return true;
            }
            if (rs && map.size() > 1) {
                String key = null;
                for (Map.Entry<String, Service> en : map.entrySet()) {
                    if (en.getValue() == service) {
                        key = en.getKey();
                        break;
                    }
                }
                if (key != null) {
                    map.remove(key);
                }
                return false; // 还有其他Resouce.name 的Service
            }
            return rs;
        };
        Predicate<Map.Entry<String, WebSocketServlet>> predicateFilter = null;
        List<HttpServlet> list = removeHttpServlet(predicateEntry, predicateFilter);
        return list == null || list.isEmpty() ? null : list.get(0);
    }

    @SuppressWarnings("unchecked")
    public <T extends WebSocket> HttpServlet removeHttpServlet(Class<T> websocketOrServletType) {
        Predicate<MappingEntry> predicateEntry = t -> {
            Class type = t.servlet.getClass();
            if (type == websocketOrServletType) {
                return true;
            }
            RestDynSourceType rdt = (RestDynSourceType) type.getAnnotation(RestDynSourceType.class);
            return (rdt != null && rdt.value() == websocketOrServletType);
        };
        Predicate<Map.Entry<String, WebSocketServlet>> predicateFilter = t -> {
            Class type = t.getValue().getClass();
            if (type == websocketOrServletType) {
                return true;
            }
            RestDynSourceType rdt = (RestDynSourceType) type.getAnnotation(RestDynSourceType.class);
            return (rdt != null && rdt.value() == websocketOrServletType);
        };
        List<HttpServlet> list = removeHttpServlet(predicateEntry, predicateFilter);
        return list == null || list.isEmpty() ? null : list.get(0);
    }

    @SuppressWarnings("unchecked")
    public boolean addForbidURIRegex(final String urlRegex) {
        if (urlRegex == null || urlRegex.isEmpty()) {
            return false;
        }
        excludeLock.lock();
        try {
            if (forbidURIMaps != null && forbidURIMaps.containsKey(urlRegex)) {
                return false;
            }
            if (forbidURIMaps == null) {
                forbidURIMaps = new HashMap<>();
            }
            String mapping = urlRegex;
            if (Utility.contains(mapping, '*', '{', '[', '(', '|', '^', '$', '+', '?', '\\')) { // 是否是正则表达式))
                if (mapping.endsWith("/*")) {
                    mapping = mapping.substring(0, mapping.length() - 1) + ".*";
                } else {
                    mapping = mapping + "$";
                }
            }
            final String reg = mapping;
            final boolean begin = mapping.charAt(0) == '^';
            final Predicate regPredicate = Pattern.compile(reg).asPredicate();
            BiPredicate<String, String> predicate = (prefix, uri) -> {
                return begin || prefix.isEmpty() ? regPredicate.test(uri) : uri.matches(prefix + reg);
            };
            forbidURIMaps.put(urlRegex, predicate);
            forbidURIPredicates = Utility.append(forbidURIPredicates, predicate);
            return true;
        } finally {
            excludeLock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    public boolean removeForbidURIReg(final String urlreg) {
        if (urlreg == null || urlreg.isEmpty()) {
            return false;
        }
        excludeLock.lock();
        try {
            if (forbidURIMaps == null || forbidURIPredicates == null || !forbidURIMaps.containsKey(urlreg)) {
                return false;
            }
            BiPredicate<String, String> predicate = forbidURIMaps.get(urlreg);
            forbidURIMaps.remove(urlreg);
            int index = -1;
            for (int i = 0; i < forbidURIPredicates.length; i++) {
                if (forbidURIPredicates[i] == predicate) {
                    index = i;
                    break;
                }
            }
            if (index > -1) {
                if (forbidURIPredicates.length == 1) {
                    forbidURIPredicates = null;
                } else {
                    int newlen = forbidURIPredicates.length - 1;
                    BiPredicate[] news = new BiPredicate[newlen];
                    System.arraycopy(forbidURIPredicates, 0, news, 0, index);
                    System.arraycopy(forbidURIPredicates, index + 1, news, index, newlen - index);
                    forbidURIPredicates = news;
                }
            }
            return true;
        } finally {
            excludeLock.unlock();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void init(HttpContext context, AnyValue config) {
        super.init(context, config); // 必须要执行
        this.context = context;
        Collection<HttpServlet> servlets = getServlets();
        servlets.forEach(s -> {
            s.preInit(application, context, getServletConf(s));
            if (application == null || !application.isCompileMode()) {
                s.init(context, getServletConf(s));
            }
        });
        { // 设置ResourceServlet
            AnyValue resConfig = config.getAnyValue("resource-servlet");
            if ((resConfig instanceof AnyValueWriter)
                    && resConfig.getValue("webroot", "").isEmpty()) {
                ((AnyValueWriter) resConfig).addValue("webroot", config.getValue("root"));
            }
            if (resConfig == null) { // 主要用于嵌入式的HttpServer初始化
                AnyValueWriter dresConfig = new AnyValueWriter();
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
            String resServlet = resConfig.getValue("servlet", HttpResourceServlet.class.getName());
            try {
                Class resClazz = Thread.currentThread().getContextClassLoader().loadClass(resServlet);
                RedkaleClassLoader.putReflectionDeclaredConstructors(resClazz, resClazz.getName());
                this.resourceHttpServlet =
                        (HttpServlet) resClazz.getDeclaredConstructor().newInstance();
            } catch (Throwable e) {
                this.resourceHttpServlet = new HttpResourceServlet();
                logger.log(Level.WARNING, "init HttpResourceSerlvet(" + resServlet + ") error", e);
            }
            { // 获取render的suffixs
                AnyValue renderConfig = config.getAnyValue("render");
                if (renderConfig != null) {
                    String[] suffixs = renderConfig
                            .getValue("suffixs", ".htel")
                            .toLowerCase()
                            .split(";");
                    ((HttpResourceServlet) this.resourceHttpServlet).renderSuffixs = suffixs;
                }
            }
            context.getResourceFactory().inject(this.resourceHttpServlet);
            if (application == null || !application.isCompileMode()) {
                this.resourceHttpServlet.init(context, resConfig);
            }
        }
    }

    @Override
    public void execute(HttpRequest request, HttpResponse response) throws IOException {
        try {
            final String uri = request.getRequestPath();
            HttpServlet servlet;
            if (response.isAutoOptions() && HttpRequest.METHOD_OPTIONS.equals(request.getMethod())) {
                response.finish(200, null);
                return;
            }
            if (request.isExpect()) {
                response.finish(100, response.getHttpCode(100));
                return;
            }
            if (request.isWebSocket()) {
                servlet = wsMappings.get(uri);
                if (servlet == null && this.regexWsArray != null) {
                    for (MappingEntry en : regexWsArray) {
                        if (en.predicate.test(uri)) {
                            servlet = en.servlet;
                            break;
                        }
                    }
                }
                if (servlet == null) {
                    response.finish(500, null);
                    return;
                }
            } else {
                servlet = mappingServlet(uri);
                if (servlet == null && this.regexArray != null) {
                    for (MappingEntry en : regexArray) {
                        if (en.predicate.test(uri)) {
                            servlet = en.servlet;
                            break;
                        }
                    }
                }
                // 找不到匹配的HttpServlet则使用静态资源HttpResourceServlet
                if (servlet == null) {
                    servlet = this.resourceHttpServlet;
                }
            }
            boolean forbid = false;
            BiPredicate<String, String>[] forbidUrlPredicates = this.forbidURIPredicates;
            if (forbidUrlPredicates != null && forbidUrlPredicates.length > 0) {
                for (BiPredicate<String, String> predicate : forbidUrlPredicates) {
                    if (predicate != null && predicate.test(servlet._prefix, uri)) {
                        forbid = true;
                        break;
                    }
                }
            }
            if (forbid) {
                response.finish(403, response.getHttpCode(403));
                return;
            }
            servlet.execute(request, response);
        } catch (Throwable e) {
            request.getContext()
                    .getLogger()
                    .log(Level.WARNING, "Dispatch servlet occur exception. request = " + request, e);
            response.finishError(e);
        }
    }

    @Override
    public void addFilter(Filter<HttpContext, HttpRequest, HttpResponse> filter, AnyValue conf) {
        super.addFilter(filter, conf);
    }

    /**
     * 添加HttpServlet
     *
     * @param servlet HttpServlet
     * @param prefix url前缀
     * @param conf 配置信息
     * @param mappingPaths 匹配规则
     */
    @Override
    public void addServlet(@Nonnull HttpServlet servlet, Object prefix, AnyValue conf, String... mappingPaths) {
        if (prefix == null) {
            prefix = "";
        }
        if (mappingPaths.length < 1) {
            WebServlet ws = servlet.getClass().getAnnotation(WebServlet.class);
            if (ws != null) {
                mappingPaths = ws.value();
                if (!ws.repair()) {
                    prefix = ""; // 被设置为不自动追加前缀则清空prefix
                }
            }
        }
        allMapLock.lock();
        try { // 需要整段锁住
            for (String mappingPath : mappingPaths) {
                if (mappingPath == null) {
                    continue;
                }
                if (!prefix.toString().isEmpty()) {
                    mappingPath = prefix + mappingPath;
                }

                if (Utility.contains(mappingPath, '*', '{', '[', '(', '|', '^', '$', '+', '?', '\\')) { // 是否是正则表达式))
                    if (mappingPath.charAt(0) != '^') {
                        mappingPath = '^' + mappingPath;
                    }
                    if (mappingPath.endsWith("/*")) {
                        mappingPath = mappingPath.substring(0, mappingPath.length() - 1) + ".*";
                    } else {
                        mappingPath = mappingPath + "$";
                    }
                    if (regexArray == null) {
                        regexArray = new MappingEntry[1];
                        regexArray[0] = new MappingEntry(
                                mappingPath, Pattern.compile(mappingPath).asPredicate(), servlet);
                    } else {
                        regexArray = Arrays.copyOf(regexArray, regexArray.length + 1);
                        regexArray[regexArray.length - 1] = new MappingEntry(
                                mappingPath, Pattern.compile(mappingPath).asPredicate(), servlet);
                        Arrays.sort(regexArray);
                    }
                    if (servlet instanceof WebSocketServlet) {
                        if (regexWsArray == null) {
                            regexWsArray = new MappingEntry[1];
                            regexWsArray[0] = new MappingEntry(
                                    mappingPath, Pattern.compile(mappingPath).asPredicate(), (WebSocketServlet)
                                            servlet);
                        } else {
                            regexWsArray = Arrays.copyOf(regexWsArray, regexWsArray.length + 1);
                            regexWsArray[regexWsArray.length - 1] = new MappingEntry(
                                    mappingPath, Pattern.compile(mappingPath).asPredicate(), (WebSocketServlet)
                                            servlet);
                            Arrays.sort(regexWsArray);
                        }
                    }
                } else if (mappingPath != null && !mappingPath.isEmpty()) {
                    if (servlet._actionmap != null && servlet._actionmap.containsKey(mappingPath)) {
                        putMapping(
                                mappingPath,
                                new HttpServlet.HttpActionServlet(
                                        servlet._actionmap.get(mappingPath), servlet, mappingPath));
                    } else {
                        putMapping(mappingPath, servlet);
                    }
                    if (servlet instanceof WebSocketServlet) {
                        Map<String, WebSocketServlet> newMappings = new HashMap<>(wsMappings);
                        newMappings.put(mappingPath, (WebSocketServlet) servlet);
                        this.wsMappings = newMappings;
                    }
                }
                if (this.allMapStrings.containsKey(mappingPath)) {
                    Class old = this.allMapStrings.get(mappingPath);
                    throw new HttpException("mapping [" + mappingPath + "] repeat on " + old.getName() + " and "
                            + servlet.getClass().getName());
                }
                this.allMapStrings.put(mappingPath, servlet.getClass());
            }
            setServletConf(servlet, conf);
            servlet._prefix = prefix.toString();
            putServlet(servlet);
        } finally {
            allMapLock.unlock();
        }
    }

    /**
     * 设置静态资源HttpServlet
     *
     * @param servlet HttpServlet
     */
    public void setResourceServlet(HttpServlet servlet) {
        if (servlet != null) {
            this.resourceHttpServlet = servlet;
        }
    }

    /**
     * 获取静态资源HttpServlet
     *
     * @return HttpServlet
     */
    public HttpServlet getResourceServlet() {
        return this.resourceHttpServlet;
    }

    public void postStart(HttpContext context, AnyValue config) {
        List filters = getFilters();
        filtersLock.lock();
        try {
            if (!filters.isEmpty()) {
                for (Object filter : filters) {
                    ((HttpFilter) filter).postStart(context, config);
                }
            }
        } finally {
            filtersLock.unlock();
        }
        this.resourceHttpServlet.postStart(context, config);
        getServlets().forEach(s -> {
            s.postStart(context, getServletConf(s));
        });
        forEachMappingKey((k, s) -> {
            byte[] bs = k.getBytes(StandardCharsets.UTF_8);
            int index = bs.length >= context.uriPathCaches.length ? 0 : bs.length;
            Map<ByteArray, String> map = context.uriPathCaches[index];
            if (map == null) {
                map = new HashMap<>();
                context.uriPathCaches[index] = map;
            }
            map.put(new ByteArray().put(bs), k);
        });
    }

    public HttpServlet findServletByTopic(String topic) {
        return filterServlets(x -> Objects.equals(x._reqtopic, topic))
                .findFirst()
                .orElse(null);
    }

    public Stream<HttpServlet> filterServlets(Predicate<HttpServlet> predicate) {
        return predicate == null ? servletStream() : servletStream().filter(predicate);
    }

    @Override
    protected HttpServlet mappingServlet(String key) {
        HttpServlet last = this.lastRunServlet;
        if (last != null
                && last._actionSimpleMappingUrl != null
                && last._actionSimpleMappingUrl.equalsIgnoreCase(key)) {
            return last;
        }
        HttpServlet s = super.mappingServlet(key);
        this.lastRunServlet = s;
        return s;
    }

    @Override
    protected void doAfterRemove(HttpServlet servlet) {
        this.lastRunServlet = null;
    }

    @Override
    public void destroy(HttpContext context, AnyValue config) {
        super.destroy(context, config); // 必须要执行
        this.resourceHttpServlet.destroy(context, config);
        getServlets().forEach(s -> {
            s.destroy(context, getServletConf(s));
            s.postDestroy(application, context, getServletConf(s));
        });
        this.allMapStrings.clear();
        this.wsMappings.clear();
        this.regexArray = null;
        this.regexWsArray = null;
    }

    protected static class MappingEntry implements Comparable<MappingEntry> {

        public final String mapping;

        public final Predicate<String> predicate;

        public final HttpServlet servlet;

        public MappingEntry(String mapping, Predicate<String> predicate, HttpServlet servlet) {
            this.mapping = Objects.requireNonNull(mapping);
            this.predicate = predicate;
            this.servlet = Objects.requireNonNull(servlet);
        }

        @Override
        public int compareTo(MappingEntry o) {
            return o.mapping.compareTo(this.mapping);
        }
    }
}
