/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.boot;

import com.wentch.redkale.util.Ignore;
import com.wentch.redkale.util.AutoLoad;
import com.wentch.redkale.util.AnyValue;
import java.io.*;
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.jar.*;
import java.util.logging.*;
import java.util.regex.*;

/**
 * class过滤器， 符合条件的class会保留下来存入FilterEntry。
 *
 * @author zhangjx
 * @param <T>
 */
public final class ClassFilter<T> {

    private final Set<FilterEntry<T>> entrys = new HashSet<>();

    private boolean refused;

    private Class superClass;

    private Class<? extends Annotation> annotationClass;

    private Pattern[] includePatterns;

    private Pattern[] excludePatterns;

    private List<ClassFilter> ors;

    private List<ClassFilter> ands;

    private AnyValue conf;

    public ClassFilter(Class<? extends Annotation> annotationClass, Class superClass) {
        this(annotationClass, superClass, null);
    }

    public ClassFilter(Class<? extends Annotation> annotationClass, Class superClass, AnyValue conf) {
        this.annotationClass = annotationClass;
        this.superClass = superClass;
        this.conf = conf;
    }

    public ClassFilter<T> or(ClassFilter<T> filter) {
        if (ors == null) ors = new ArrayList<>();
        ors.add(filter);
        return this;
    }

    public ClassFilter<T> and(ClassFilter<T> filter) {
        if (ands == null) ands = new ArrayList<>();
        ands.add(filter);
        return this;
    }

    /**
     * 获取符合条件的class集合
     * <p>
     * @return
     */
    public final Set<FilterEntry<T>> getFilterEntrys() {
        return entrys;
    }

    /**
     * 自动扫描地过滤指定的class
     * <p>
     * @param property
     * @param clazzname
     */
    @SuppressWarnings("unchecked")
    public final void filter(AnyValue property, String clazzname) {
        filter(property, clazzname, true);
    }

    /**
     * 过滤指定的class
     * <p>
     * @param property  application.xml中对应class节点下的property属性项
     * @param clazzname class名称
     * @param autoscan  为true表示自动扫描的， false表示显著调用filter， AutoLoad的注解将被忽略
     */
    public final void filter(AnyValue property, String clazzname, boolean autoscan) {
        boolean r = accept0(property, clazzname);
        ClassFilter cf = r ? this : null;
        if (r && ands != null) {
            for (ClassFilter filter : ands) {
                if (!filter.accept(property, clazzname)) return;
            }
        }
        if (!r && ors != null) {
            for (ClassFilter filter : ors) {
                if (filter.accept(property, clazzname)) {
                    cf = filter;
                    break;
                }
            }
        }
        if (cf == null) return;
        try {
            Class clazz = Class.forName(clazzname);
            if (cf.accept(property, clazz, autoscan)) {
                if (conf != null) {
                    if (property == null) {
                        property = cf.conf;
                    }
                }
                entrys.add(new FilterEntry(clazz, property));
            }
        } catch (Throwable cfe) {
        }
    }

    private static Pattern[] toPattern(String[] regs) {
        if (regs == null) return null;
        int i = 0;
        Pattern[] rs = new Pattern[regs.length];
        for (String reg : regs) {
            if (reg == null || reg.trim().isEmpty()) continue;
            rs[i++] = Pattern.compile(reg.trim());
        }
        if (i == 0) return null;
        if (i == rs.length) return rs;
        Pattern[] ps = new Pattern[i];
        System.arraycopy(rs, 0, ps, 0, i);
        return ps;
    }

    /**
     * 判断class是否有效
     * <p>
     * @param property
     * @param classname
     * @return
     */
    public boolean accept(AnyValue property, String classname) {
        boolean r = accept0(property, classname);
        if (r && ands != null) {
            for (ClassFilter filter : ands) {
                if (!filter.accept(property, classname)) return false;
            }
        }
        if (!r && ors != null) {
            for (ClassFilter filter : ors) {
                if (filter.accept(property, classname)) return true;
            }
        }
        return r;
    }

    private boolean accept0(AnyValue property, String classname) {
        if (this.refused) return false;
        if (classname.startsWith("java.") || classname.startsWith("javax.")) return false;
        if (excludePatterns != null) {
            for (Pattern reg : excludePatterns) {
                if (reg.matcher(classname).matches()) return false;
            }
        }
        if (includePatterns != null) {
            for (Pattern reg : includePatterns) {
                if (reg.matcher(classname).matches()) return true;
            }
        }
        return includePatterns == null;
    }

    /**
     * 判断class是否有效
     * <p>
     * @param property
     * @param clazz
     * @param autoscan
     * @return
     */
    @SuppressWarnings("unchecked")
    public boolean accept(AnyValue property, Class clazz, boolean autoscan) {
        if (this.refused || !Modifier.isPublic(clazz.getModifiers())) return false;
        if (clazz.getAnnotation(Ignore.class) != null) return false;
        if (autoscan) {
            AutoLoad auto = (AutoLoad) clazz.getAnnotation(AutoLoad.class);
            if (auto != null && !auto.value()) return false;
        }
        if (annotationClass != null && clazz.getAnnotation(annotationClass) == null) return false;
        return superClass == null || (clazz != superClass && superClass.isAssignableFrom(clazz));
    }

    public void setSuperClass(Class superClass) {
        this.superClass = superClass;
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

    public Class getSuperClass() {
        return superClass;
    }

    public boolean isRefused() {
        return refused;
    }

    public void setRefused(boolean refused) {
        this.refused = refused;
    }

    /**
     * 存放符合条件的class与class指定的属性项
     * <p>
     * @param <T>
     */
    public static final class FilterEntry<T> {

        private String group;

        private final String name;

        private final Class<T> type;

        private final AnyValue property;

        public FilterEntry(Class<T> type, AnyValue property) {
            this.type = type;
            this.group = property == null ? "" : property.getValue("group", "");
            this.property = property;
            this.name = property == null ? "" : property.getValue("name", "");
        }

        @Override
        public String toString() {
            return this.getClass().getSimpleName() + "[thread=" + Thread.currentThread().getName()
                    + ", type=" + this.type.getSimpleName() + ", name=" + name + ", group=" + group + "]";
        }

        @Override
        public int hashCode() {
            return this.type.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            return (this.type == ((FilterEntry<?>) obj).type && this.group.equals(((FilterEntry<?>) obj).group) && this.name.equals(((FilterEntry<?>) obj).name));
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

        public String getGroup() {
            return group;
        }

        public void setGroup(String group) {
            this.group = group;
        }
    }

    /**
     * class加载类
     */
    public static class Loader {

        protected static final Logger logger = Logger.getLogger(Loader.class.getName());

        /**
         * 加载当前线程的classpath扫描所有class进行过滤
         * <p>
         * @param exclude 不需要扫描的文件夹， 可以为null
         * @param filters
         * @throws IOException
         */
        public static void load(final File exclude, final ClassFilter... filters) throws IOException {
            URLClassLoader loader = (URLClassLoader) Thread.currentThread().getContextClassLoader();
            List<URL> urlfiles = new ArrayList<>(2);
            List<URL> urljares = new ArrayList<>(2);
            final URL exurl = exclude != null ? exclude.toURI().toURL() : null;
            for (URL url : loader.getURLs()) {
                if (exurl != null && exurl.sameFile(url)) continue;
                if (url.getPath().endsWith(".jar")) {
                    urljares.add(url);
                } else {
                    urlfiles.add(url);
                }
            }

            List<File> files = new ArrayList<>();
            boolean debug = logger.isLoggable(Level.FINEST);
            StringBuilder debugstr = new StringBuilder();
            for (URL url : urljares) {
                try (JarFile jar = new JarFile(URLDecoder.decode(url.getFile(), "UTF-8"))) {
                    Enumeration<JarEntry> it = jar.entries();
                    while (it.hasMoreElements()) {
                        String entryname = it.nextElement().getName().replace('/', '.');
                        if (entryname.endsWith(".class") && entryname.indexOf('$') < 0) {
                            String classname = entryname.substring(0, entryname.length() - 6);
                            if (classname.startsWith("javax.") || classname.startsWith("org.") || classname.startsWith("com.mysql.")) continue;
                            if (debug) debugstr.append(classname).append("\r\n");
                            for (final ClassFilter filter : filters) {
                                if (filter != null) filter.filter(null, classname);
                            }
                        }
                    }
                }
            }
            for (URL url : urlfiles) {
                files.clear();
                File root = new File(url.getFile());
                String rootpath = root.getPath();
                loadClassFiles(exclude, root, files);
                for (File f : files) {
                    String classname = f.getPath().substring(rootpath.length() + 1, f.getPath().length() - 6).replace(File.separatorChar, '.');
                    if (classname.startsWith("javax.") || classname.startsWith("org.") || classname.startsWith("com.mysql.")) continue;
                    if (debug) debugstr.append(classname).append("\r\n");
                    for (final ClassFilter filter : filters) {
                        if (filter != null) filter.filter(null, classname);
                    }
                }
            }
            //if (debug) logger.log(Level.INFO, "scan classes: \r\n{0}", debugstr);
        }

        private static void loadClassFiles(File exclude, File root, List<File> files) {
            if (root.isFile() && root.getName().endsWith(".class")) {
                files.add(root);
            } else if (root.isDirectory()) {
                if (exclude != null && exclude.equals(root)) return;
                for (File f : root.listFiles()) {
                    loadClassFiles(exclude, f, files);
                }
            }
        }
    }
}
