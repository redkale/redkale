/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.boot;

import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import java.util.jar.*;
import java.util.logging.*;
import java.util.regex.Pattern;
import org.redkale.annotation.*;
import org.redkale.annotation.AutoLoad;
import org.redkale.util.*;

/**
 * class过滤器， 符合条件的class会保留下来存入FilterEntry。
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <T> 泛型
 */
@SuppressWarnings("unchecked")
public final class ClassFilter<T> {

    private static final Logger logger = Logger.getLogger(ClassFilter.class.getName()); // 日志对象

    private static final String dian = new String(new char[] {7}); // .
    private static final String dxing = new String(new char[] {8}); // *
    private static final String sxing = new String(new char[] {9}); // **

    private final Set<FilterEntry<T>> entrys = new HashSet<>(); // 符合条件的结果

    private final Set<FilterEntry<T>> expectEntrys = new HashSet<>(); // 准备符合条件的结果

    private Predicate<String> expectPredicate;

    // 是否拒绝所有数据,设置true，则其他规则失效,都是拒绝.
    private boolean refused;

    // 符合的父类型。不为空时，扫描结果的class必须是superClass的子类
    private Class superClass;

    // 不符合的父类型。
    private Class[] excludeSuperClasses;

    // 符合的注解。不为空时，扫描结果的class必须包含该注解
    private Class<? extends Annotation> annotationClass;

    // 符合的className正则表达式
    private Pattern[] includePatterns;

    // 拒绝的className正则表达式
    private Pattern[] excludePatterns;

    // 特批符合条件的className
    private Set<String> privilegeIncludes;

    // 特批拒绝条件的className
    private Set<String> privilegeExcludes;

    // 或关系的其他ClassFilter
    private List<ClassFilter> ors;

    // 与关系的其他ClassFilter
    private List<ClassFilter> ands;

    // 基本配置信息, 当符合条件时将conf的属性赋值到FilterEntry中去。
    private AnyValue conf;

    private final ClassLoader classLoader;

    public ClassFilter(RedkaleClassLoader classLoader, Class superClass) {
        this(classLoader, null, superClass, (Class[]) null, null);
    }

    public ClassFilter(RedkaleClassLoader classLoader, Class<? extends Annotation> annotationClass, Class superClass) {
        this(classLoader, annotationClass, superClass, (Class[]) null, null);
    }

    public ClassFilter(
            RedkaleClassLoader classLoader,
            Class<? extends Annotation> annotationClass,
            Class superClass,
            Class[] excludeSuperClasses) {
        this(classLoader, annotationClass, superClass, excludeSuperClasses, null);
    }

    public ClassFilter(
            RedkaleClassLoader classLoader,
            Class<? extends Annotation> annotationClass,
            Class superClass,
            Class[] excludeSuperClasses,
            AnyValue conf) {
        this.annotationClass = annotationClass;
        this.superClass = superClass;
        this.excludeSuperClasses = excludeSuperClasses;
        this.conf = conf;
        this.classLoader = classLoader == null ? Thread.currentThread().getContextClassLoader() : classLoader;
    }

    public static ClassFilter create(String includeRegexs, String excludeRegexs) {
        return create(null, null, includeRegexs, excludeRegexs, null, null);
    }

    public static ClassFilter create(
            RedkaleClassLoader classLoader,
            Class[] excludeSuperClasses,
            String includeRegexs,
            String excludeRegexs,
            Set<String> includeValues,
            Set<String> excludeValues) {
        ClassFilter filter = new ClassFilter(classLoader, null, null, excludeSuperClasses);
        filter.setIncludePatterns(
                includeRegexs == null ? null : includeRegexs.replace(',', ';').split(";"));
        filter.setExcludePatterns(
                excludeRegexs == null ? null : excludeRegexs.replace(',', ';').split(";"));
        filter.setPrivilegeIncludes(includeValues);
        filter.setPrivilegeExcludes(excludeValues);
        return filter;
    }

    public ClassFilter<T> or(ClassFilter<T> filter) {
        if (ors == null) {
            ors = new ArrayList<>();
        }
        ors.add(filter);
        return this;
    }

    public ClassFilter<T> and(ClassFilter<T> filter) {
        if (ands == null) {
            ands = new ArrayList<>();
        }
        ands.add(filter);
        return this;
    }

    /**
     * 获取符合条件的class集合
     *
     * @return Set&lt;FilterEntry&lt;T&gt;&gt;
     */
    public final Set<FilterEntry<T>> getFilterEntrys() {
        List<FilterEntry<T>> list = new ArrayList<>();
        list.addAll(entrys);
        if (ors != null) {
            ors.forEach(f -> list.addAll(f.getFilterEntrys()));
        }
        if (ands != null) {
            ands.forEach(f -> list.addAll(f.getFilterEntrys()));
        }
        Collections.sort(list);
        return new LinkedHashSet<>(list);
    }

    /**
     * 获取预留的class集合
     *
     * @return Set&lt;FilterEntry&lt;T&gt;&gt;
     */
    public final Set<FilterEntry<T>> getFilterExpectEntrys() {
        List<FilterEntry<T>> list = new ArrayList<>();
        list.addAll(expectEntrys);
        if (ors != null) {
            ors.forEach(f -> list.addAll(f.getFilterExpectEntrys()));
        }
        if (ands != null) {
            ands.forEach(f -> list.addAll(f.getFilterExpectEntrys()));
        }
        Collections.sort(list);
        return new LinkedHashSet<>(list);
    }

    /**
     * 获取所有的class集合
     *
     * @return Set&lt;FilterEntry&lt;T&gt;&gt;
     */
    public final Set<FilterEntry<T>> getAllFilterEntrys() {
        HashSet<FilterEntry<T>> rs = new LinkedHashSet<>();
        rs.addAll(getFilterEntrys());
        rs.addAll(getFilterExpectEntrys());
        return rs;
    }

    /**
     * 自动扫描地过滤指定的class
     *
     * @param property AnyValue
     * @param clazzName String
     * @param url URL
     */
    @SuppressWarnings("unchecked")
    public final void filter(AnyValue property, String clazzName, URI url) {
        filter(property, clazzName, true, url);
    }

    /**
     * 过滤指定的class
     *
     * @param property application.xml中对应class节点下的property属性项
     * @param clazzName class名称
     * @param autoscan 为true表示自动扫描的， false表示显著调用filter， AutoLoad的注解将被忽略
     */
    public final void filter(AnyValue property, String clazzName, boolean autoscan) {
        filter(property, clazzName, autoscan, null);
    }

    /**
     * 过滤指定的class
     *
     * @param property application.xml中对应class节点下的property属性项
     * @param clazzName class名称
     * @param autoScan 为true表示自动扫描的， false表示显著调用filter， AutoLoad的注解将被忽略
     * @param url URL
     */
    public final void filter(AnyValue property, String clazzName, boolean autoScan, URI url) {
        boolean r = accept0(property, clazzName);
        ClassFilter cf = r ? this : null;
        if (r && ands != null) {
            for (ClassFilter filter : ands) {
                if (!filter.accept(property, clazzName)) {
                    return;
                }
            }
        }
        if (!r && ors != null) {
            for (ClassFilter filter : ors) {
                if (filter.accept(filter.conf, clazzName)) {
                    cf = filter;
                    property = cf.conf;
                    break;
                }
            }
        }
        if (cf == null || clazzName.startsWith("sun.") || clazzName.contains("module-info")) {
            return;
        }
        try {
            Class clazz = classLoader.loadClass(clazzName);
            if (!cf.accept(property, clazz, autoScan)) {
                return;
            }
            if (cf.conf != null) {
                if (property == null) {
                    property = cf.conf;
                } else if (property instanceof AnyValueWriter) {
                    ((AnyValueWriter) property).addAllStringSet(cf.conf);
                } else {
                    AnyValueWriter dav = new AnyValueWriter();
                    dav.addAllStringSet(property);
                    dav.addAllStringSet(cf.conf);
                    property = dav;
                }
            }

            AutoLoad auto = (AutoLoad) clazz.getAnnotation(AutoLoad.class);
            org.redkale.util.AutoLoad auto2 =
                    (org.redkale.util.AutoLoad) clazz.getAnnotation(org.redkale.util.AutoLoad.class);
            if ((expectPredicate != null && expectPredicate.test(clazzName))
                    || (autoScan && auto != null && !auto.value())
                    || (autoScan && auto2 != null && !auto2.value())) { // 自动扫描且被标记为@AutoLoad(false)的
                expectEntrys.add(new FilterEntry(clazz, autoScan, true, property));
            } else {
                entrys.add(new FilterEntry(clazz, autoScan, false, property));
            }
        } catch (Throwable cfe) {
            if (logger.isLoggable(Level.FINEST)
                    && !clazzName.startsWith("sun.")
                    && !clazzName.startsWith("javax.")
                    && !clazzName.startsWith("com.sun.")
                    && !clazzName.startsWith("jdk.")
                    && !clazzName.startsWith("META-INF")
                    && !clazzName.startsWith("com.mysql.")
                    && !clazzName.startsWith("com.microsoft.")
                    && !clazzName.startsWith("freemarker.")
                    && !clazzName.startsWith("org.redkale")
                    && (clazzName.contains("Service") || clazzName.contains("Servlet"))) {
                if (cfe instanceof NoClassDefFoundError) {
                    String msg = ((NoClassDefFoundError) cfe).getMessage();
                    if (msg.startsWith("java.lang.NoClassDefFoundError: java") || msg.startsWith("javax/")) {
                        return;
                    }
                }
                // && (!(cfe instanceof NoClassDefFoundError) || (cfe instanceof UnsupportedClassVersionError)
                // || ((NoClassDefFoundError) cfe).getMessage().startsWith("java.lang.NoClassDefFoundError: java"))) {
                logger.log(
                        Level.FINEST,
                        ClassFilter.class.getSimpleName() + " filter error for class: " + clazzName
                                + (url == null ? "" : (" in " + url)),
                        cfe);
            }
        }
    }

    /**
     * 判断class是否有效
     *
     * @param className String
     * @return boolean
     */
    public boolean accept(String className) {
        return accept(null, className);
    }

    /**
     * 判断class是否有效
     *
     * @param property AnyValue
     * @param className String
     * @return boolean
     */
    public boolean accept(AnyValue property, String className) {
        boolean r = accept0(property, className);
        if (r && ands != null) {
            for (ClassFilter filter : ands) {
                if (!filter.accept(property, className)) {
                    return false;
                }
            }
        }
        if (!r && ors != null) {
            for (ClassFilter filter : ors) {
                if (filter.accept(filter.conf, className)) {
                    return true;
                }
            }
        }
        return r;
    }

    private boolean accept0(AnyValue property, String className) {
        if (this.refused) {
            return false;
        }
        if (this.privilegeIncludes != null && this.privilegeIncludes.contains(className)) {
            return true;
        }
        if (this.privilegeExcludes != null && this.privilegeExcludes.contains(className)) {
            return false;
        }
        if (className.startsWith("java.") || className.startsWith("javax.")) {
            return false;
        }
        return acceptPattern(className);
    }

    /**
     * 判断class是否符合正则表达式
     *
     * @param className Class
     * @return  boolean
     */
    public boolean acceptPattern(String className) {
        if (excludePatterns != null) {
            for (Pattern reg : excludePatterns) {
                if (reg.matcher(className).matches()) {
                    return false;
                }
            }
        }
        if (includePatterns != null) {
            for (Pattern reg : includePatterns) {
                if (reg.matcher(className).matches()) {
                    return true;
                }
            }
            return false;
        }
        return includePatterns == null;
    }

    /**
     * 判断class是否有效
     *
     * @param property AnyValue
     * @param clazz Class
     * @param autoscan boolean
     * @return boolean
     */
    @SuppressWarnings("unchecked")
    public boolean accept(AnyValue property, Class clazz, boolean autoscan) {
        if (this.refused || !Modifier.isPublic(clazz.getModifiers())) {
            return false;
        }
        if (annotationClass != null && clazz.getAnnotation(annotationClass) == null) {
            return false;
        }
        boolean rs = superClass == null || (clazz != superClass && superClass.isAssignableFrom(clazz));
        if (rs && this.excludeSuperClasses != null && this.excludeSuperClasses.length > 0) {
            for (Class c : this.excludeSuperClasses) {
                if (c != null && (clazz == c || c.isAssignableFrom(clazz))) {
                    return false;
                }
            }
        }
        return rs;
    }

    public static Pattern[] toPattern(String[] regexs) {
        if (regexs == null || regexs.length == 0) {
            return null;
        }
        int i = 0;
        Pattern[] rs = new Pattern[regexs.length];
        for (String regex : regexs) {
            if (regex == null || regex.trim().isEmpty()) {
                continue;
            }
            rs[i++] = Pattern.compile(formatPackageRegex(regex.trim()));
        }
        if (i == 0) {
            return null;
        }
        if (i == rs.length) {
            return rs;
        }
        Pattern[] ps = new Pattern[i];
        System.arraycopy(rs, 0, ps, 0, i);
        return ps;
    }

    /**
     *  将通配符转成标准的正则表达式 <br>
     *  包含^、$、\字符的视为标准正则表达式， 其他视为通配符
     *  一个*表示包的一个层级， 两个*表示包的多层级
     * 例如：
     * *.platf.** 转成  ^(\w+)\.platf\.(.*)$
     * .platf.    转成  ^(.*)\.platf\.(.*)$
     *
     * @param regex 正则表达式
     * @return  Pattern
     */
    public static String formatPackageRegex(String regex) {
        if (regex.indexOf('^') >= 0 || regex.indexOf('$') >= 0 || regex.indexOf('\\') >= 0) { // 已经是标准正则表达式
            return regex;
        }
        if (regex.startsWith(".") && regex.endsWith(".")) { // .platf.
            regex = "**" + regex + "**";
        }
        String str = regex.replace("**", sxing).replace("*", dxing);
        str = str.replace("\\.", dian).replace(".", dian);
        return "^" + str.replace(dian, "\\.").replace(dxing, "(\\w+)").replace(sxing, "(.*)") + "$";
    }

    public void setSuperClass(Class superClass) {
        this.superClass = superClass;
    }

    public Class getSuperClass() {
        return superClass;
    }

    public Class[] getExcludeSuperClasses() {
        return excludeSuperClasses;
    }

    public void setExcludeSuperClasses(Class[] excludeSuperClasses) {
        this.excludeSuperClasses = excludeSuperClasses;
    }

    public void setAnnotationClass(Class<? extends Annotation> annotationClass) {
        this.annotationClass = annotationClass;
    }

    public Pattern[] getIncludePatterns() {
        return includePatterns;
    }

    public void setIncludePatterns(String[] includePatterns) {
        this.includePatterns = toPattern(includePatterns);
    }

    public Pattern[] getExcludePatterns() {
        return excludePatterns;
    }

    public void setExcludePatterns(String[] excludePatterns) {
        this.excludePatterns = toPattern(excludePatterns);
    }

    public Class<? extends Annotation> getAnnotationClass() {
        return annotationClass;
    }

    public boolean isRefused() {
        return refused;
    }

    public void setRefused(boolean refused) {
        this.refused = refused;
    }

    public Predicate<String> getExpectPredicate() {
        return expectPredicate;
    }

    public void setExpectPredicate(Predicate<String> predicate) {
        this.expectPredicate = predicate;
    }

    public Set<String> getPrivilegeIncludes() {
        return privilegeIncludes;
    }

    public void setPrivilegeIncludes(Set<String> privilegeIncludes) {
        this.privilegeIncludes = privilegeIncludes == null || privilegeIncludes.isEmpty() ? null : privilegeIncludes;
    }

    public Set<String> getPrivilegeExcludes() {
        return privilegeExcludes;
    }

    public void setPrivilegeExcludes(Set<String> privilegeExcludes) {
        this.privilegeExcludes = privilegeExcludes == null || privilegeExcludes.isEmpty() ? null : privilegeExcludes;
    }

    /**
     * 存放符合条件的class与class指定的属性项
     *
     * @param <T> 泛型
     */
    public static final class FilterEntry<T> implements Comparable<FilterEntry<T>> {

        private final String group; // 优先级高于remote属性

        private final String name;

        private final Class<T> type;

        private final AnyValue property;

        private final boolean autoload;

        private final boolean expect;

        public FilterEntry(Class<T> type, AnyValue property) {
            this(type, false, false, property);
        }

        public FilterEntry(Class<T> type, final boolean autoload, boolean expect, AnyValue property) {
            this.type = type;
            this.property = property;
            this.autoload = autoload;
            this.expect = expect;
            this.group =
                    property == null ? null : property.getValue("group", "").trim();
            this.name = property == null ? "" : property.getValue("name", "");
        }

        @Override // @Priority值越大，优先级越高, 需要排前面
        public int compareTo(FilterEntry o) {
            if (!(o instanceof FilterEntry)) {
                return 1;
            }
            Priority p1 = this.type.getAnnotation(Priority.class);
            Priority p2 = ((FilterEntry<T>) o).type.getAnnotation(Priority.class);
            return (p2 == null ? 0 : p2.value()) - (p1 == null ? 0 : p1.value());
        }

        @Override
        public String toString() {
            return this.getClass().getSimpleName() + "[type=" + this.type.getSimpleName() + ", name=" + name
                    + ", group=" + this.group + "]";
        }

        @Override
        public int hashCode() {
            return this.type.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            return (this.type == ((FilterEntry<?>) obj).type && this.name.equals(((FilterEntry<?>) obj).name));
        }

        public Class<T> getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        public AnyValue getProperty() {
            return property;
        }

        public boolean isEmptyGroup() {
            return group == null || group.isEmpty();
        }

        public String getGroup() {
            return group;
        }

        public boolean isRemote() {
            return "$remote".equalsIgnoreCase(group);
        }

        public boolean isAutoload() {
            return autoload;
        }

        public boolean isExpect() {
            return expect;
        }
    }

    /** class加载类 */
    public static class Loader {

        protected static final Logger logger = Logger.getLogger(Loader.class.getName());

        protected static final ConcurrentMap<URI, Set<String>> cache = new ConcurrentHashMap<>();

        private Loader() {
            // do nothng
        }

        public static void close() {
            cache.clear();
        }

        /**
         * 加载当前线程的classpath扫描所有class进行过滤
         *
         * @param excludeFile 不需要扫描的文件夹， 可以为null
         * @param loader RedkaleClassloader， 不可为null
         * @param filters 过滤器
         * @throws IOException 异常
         */
        public static void load(final File excludeFile, RedkaleClassLoader loader, final ClassFilter... filters)
                throws IOException {
            List<URI> urlFiles = new ArrayList<>(2);
            List<URI> urlJares = new ArrayList<>(2);
            final URI exurl = excludeFile != null ? excludeFile.toURI() : null;
            for (URI url : loader.getAllURIs()) {
                if (exurl != null && exurl.equals(url)) {
                    continue;
                }
                if (url.getPath().endsWith(".jar")) {
                    urlJares.add(url);
                } else {
                    urlFiles.add(url);
                }
            }
            List<File> files = new ArrayList<>();
            boolean debug = logger.isLoggable(Level.FINEST);
            StringBuilder debugstr = new StringBuilder();
            for (final URI url : urlJares) {
                Set<String> classes = cache.get(url);
                if (classes == null) {
                    classes = new LinkedHashSet<>();
                    try (JarFile jar = new JarFile(URLDecoder.decode(url.toURL().getFile(), StandardCharsets.UTF_8))) {
                        Enumeration<JarEntry> it = jar.entries();
                        while (it.hasMoreElements()) {
                            String entryName = it.nextElement().getName().replace('/', '.');
                            if (entryName.endsWith(".class") && entryName.indexOf('$') < 0) {
                                String className = entryName.substring(0, entryName.length() - 6);
                                if (className.startsWith("javax.") || className.startsWith("com.sun.")) {
                                    continue;
                                }
                                // 常见的jar跳过
                                if (className.startsWith("org.redkaledyn.")) {
                                    break; // redkale动态生成的类
                                }
                                if (className.startsWith("org.junit.")) {
                                    break;
                                }
                                if (className.startsWith("org.openjfx.")) {
                                    break;
                                }
                                if (className.startsWith("org.mariadb.")) {
                                    break;
                                }
                                if (className.startsWith("com.mysql.")) {
                                    break;
                                }
                                if (className.startsWith("oracle.jdbc.")) {
                                    break;
                                }
                                if (className.startsWith("org.postgresql.")) {
                                    break;
                                }
                                if (className.startsWith("com.microsoft.sqlserver.")) {
                                    break;
                                }
                                if (className.startsWith("org.apache.")) {
                                    break;
                                }
                                if (className.startsWith("org.redisson.")) {
                                    break;
                                }
                                if (className.startsWith("com.fasterxml.")) {
                                    break;
                                }
                                if (className.startsWith("org.yaml.")) {
                                    break;
                                }
                                if (className.startsWith("reactor.")) {
                                    break;
                                }
                                if (className.startsWith("io.netty.")) {
                                    break;
                                }
                                if (className.startsWith("io.vertx.")) {
                                    break;
                                }
                                classes.add(className);
                                if (debug) {
                                    debugstr.append(className).append("\r\n");
                                }
                                for (final ClassFilter filter : filters) {
                                    if (filter != null) {
                                        filter.filter(null, className, url);
                                    }
                                }
                            }
                        }
                    }
                    cache.put(url, classes);
                } else {
                    for (String className : classes) {
                        for (final ClassFilter filter : filters) {
                            if (filter != null) {
                                filter.filter(null, className, url);
                            }
                        }
                    }
                }
            }
            for (final URI url : urlFiles) {
                Set<String> classes = cache.get(url);
                if (classes == null) {
                    classes = new LinkedHashSet<>();
                    final Set<String> cs = classes;
                    if (url == RedkaleClassLoader.URI_NONE) {
                        loader.forEachCacheClass(v -> cs.add(v));
                    }
                    if (cs.isEmpty()) {
                        files.clear();
                        File root = new File(url.toURL().getFile());
                        String rootPath = root.getPath();
                        loadClassFiles(excludeFile, root, files);
                        for (File f : files) {
                            String className = f.getPath()
                                    .substring(
                                            rootPath.length() + 1, f.getPath().length() - 6)
                                    .replace(File.separatorChar, '.');
                            if (className.startsWith("javax.") || className.startsWith("com.sun.")) {
                                continue;
                            }
                            classes.add(className);
                            if (debug) {
                                debugstr.append(className).append("\r\n");
                            }
                            for (final ClassFilter filter : filters) {
                                if (filter != null) {
                                    filter.filter(null, className, url);
                                }
                            }
                        }
                    } else {
                        for (String className : classes) {
                            for (final ClassFilter filter : filters) {
                                if (filter != null) {
                                    filter.filter(null, className, url);
                                }
                            }
                        }
                    }
                    cache.put(url, classes);
                } else {
                    for (String className : classes) {
                        for (final ClassFilter filter : filters) {
                            if (filter != null) {
                                filter.filter(null, className, url);
                            }
                        }
                    }
                }
            }
            // if (debug) logger.log(Level.INFO, "Scan classes: \r\n{0}", debugstr);
        }

        private static void loadClassFiles(File exclude, File root, List<File> files) {
            if (root.isFile() && root.getName().endsWith(".class")) {
                files.add(root);
            } else if (root.isDirectory()) {
                if (exclude != null && exclude.equals(root)) {
                    return;
                }
                File[] lfs = root.listFiles();
                if (lfs != null) {
                    for (File f : lfs) {
                        loadClassFiles(exclude, f, files);
                    }
                }
            }
        }
    }
}
