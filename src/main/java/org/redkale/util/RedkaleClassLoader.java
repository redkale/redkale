/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.*;

/**
 * Redkale内部ClassLoader
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class RedkaleClassLoader extends URLClassLoader {

    public static final String RESOURCE_CACHE_CLASSES_PATH = "/META-INF/redkale/redkale.load.classes";

    public static final String RESOURCE_CACHE_CONF_PATH = "/META-INF/redkale/conf";

    public static final URI URI_NONE = URI.create("file://redkale/uri"); // 不能是jar结尾，否则会视为jar文件url

    private static final String[] buildClasses = {};

    private static final String[] buildPackages = {
        "org.redkaledyn", // 所有动态生成类的根package
        "org.redkale.annotation",
        "org.redkale.asm",
        "org.redkale.boot",
        "org.redkale.boot.watch",
        "org.redkale.cached",
        "org.redkale.cached.spi",
        "org.redkale.cluster",
        "org.redkale.cluster.spi",
        "org.redkale.convert",
        "org.redkale.convert.ext",
        "org.redkale.convert.json",
        "org.redkale.convert.pb",
        "org.redkale.convert.spi",
        "org.redkale.inject",
        "org.redkale.locked",
        "org.redkale.locked.spi",
        "org.redkale.mq",
        "org.redkale.mq.spi",
        "org.redkale.net",
        "org.redkale.net.client",
        "org.redkale.net.http",
        "org.redkale.net.sncp",
        "org.redkale.persistence",
        "org.redkale.props.spi",
        "org.redkale.scheduled",
        "org.redkale.scheduled.spi",
        "org.redkale.service",
        "org.redkale.source",
        "org.redkale.source.spi",
        "org.redkale.util",
        "org.redkale.watch"
    };

    private final ConcurrentHashMap<String, byte[]> dynClassBytesMap = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Class> dynClassTypeMap = new ConcurrentHashMap<>();

    // redkale里所有使用动态字节码生成的类都需要存于此处
    private static final ConcurrentHashMap<String, byte[]> allDynClassBytesMap = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<String, Class> allDynClassTypeMap = new ConcurrentHashMap<>();

    private static final CopyOnWriteArraySet<String> resourcePathSet = new CopyOnWriteArraySet<>();

    private static final ConcurrentHashMap<String, Class> serviceLoaderMap = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<String, Set<String>> bundleResourcesMap = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<String, Map<String, Object>> reflectionMap = new ConcurrentHashMap<>();

    private static final ReentrantLock reflectionLock = new ReentrantLock();

    public RedkaleClassLoader(ClassLoader parent) {
        super(new URL[0], parent);
    }

    public RedkaleClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    public static RedkaleClassLoader currentClassLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader instanceof RedkaleClassLoader) {
            return (RedkaleClassLoader) loader;
        }
        ClassLoader c = loader;
        while ((c = c.getParent()) != null) {
            if (c instanceof RedkaleClassLoader) {
                return (RedkaleClassLoader) c;
            }
        }
        return new RedkaleClassLoader(loader);
    }

    public static RedkaleClassLoader createCacheClassLoader(ClassLoader parent, Set<String> classes) {
        return new RedkaleCacheClassLoader(parent, classes);
    }

    public static boolean isCacheClassLoader(ClassLoader loader) {
        return !(loader instanceof RedkaleCacheClassLoader);
    }

    public static URI getConfResourceAsURI(String confURI, String file) {
        if (file.startsWith("http:") || file.startsWith("https:") || file.startsWith("ftp:")) {
            return URI.create(file);
        }
        if (confURI != null && !confURI.contains("!")) { // 带!的是 /usr/xxx.jar!/META-INF/conf/xxx
            File f = new File(URI.create(confURI).getPath(), file);
            if (f.isFile() && f.canRead()) {
                return f.toURI();
            }
        }
        URL url = RedkaleClassLoader.class.getResource(
                RESOURCE_CACHE_CONF_PATH + (file.startsWith("/") ? file : ("/" + file)));
        return url == null ? null : URI.create(url.toString());
    }

    public static InputStream getConfResourceAsStream(String confURI, String file) {
        if (confURI != null && !confURI.contains("!")) { // 带!的是 /usr/xxx.jar!/META-INF/conf/xxx
            File f = new File(URI.create(confURI).getPath(), file);
            if (f.isFile() && f.canRead()) {
                try {
                    return new FileInputStream(f);
                } catch (FileNotFoundException e) { // 几乎不会发生
                    throw new RedkaleException(e);
                }
            }
        }
        return RedkaleClassLoader.class.getResourceAsStream(
                RESOURCE_CACHE_CONF_PATH + (file.startsWith("/") ? file : ("/" + file)));
    }

    public static void forEachBundleResource(BiConsumer<String, Set<String>> action) {
        bundleResourcesMap.forEach(action);
    }

    public static void putBundleResource(String name, String locale) {
        bundleResourcesMap
                .computeIfAbsent(name, k -> new CopyOnWriteArraySet<>())
                .add(locale);
    }

    public static void putResourcePath(String name) {
        resourcePathSet.add(name);
    }

    public static void forEachResourcePath(Consumer<String> action) {
        for (String name : resourcePathSet) {
            action.accept(name);
        }
    }

    public static void forEachBuildClass(Consumer<String> action) {
        for (String name : buildClasses) {
            action.accept(name);
        }
    }

    public static void forEachBuildPackage(Consumer<String> action) {
        for (String name : buildPackages) {
            action.accept(name);
        }
    }

    public static void forEachDynClass(BiConsumer<String, byte[]> action) {
        allDynClassBytesMap.forEach(action);
    }

    public static void putReflectionClass(String className) {
        reflectionLock.lock();
        try {
            Map<String, Object> map = reflectionMap.get(className);
            if (map == null) {
                map = new LinkedHashMap<>();
                map.put("name", className);
                reflectionMap.put(className, map);
            }
        } finally {
            reflectionLock.unlock();
        }
    }

    public static void putServiceLoader(Class clazz) {
        serviceLoaderMap.put(clazz.getName(), clazz);
        putReflectionClass(clazz.getName());
    }

    public static void forEachServiceLoader(BiConsumer<String, Class> action) {
        serviceLoaderMap.forEach(action);
    }

    public static void putReflectionField(String className, Field field) {
        reflectionLock.lock();
        try {
            Map<String, Object> map = reflectionMap.get(className);
            if (map == null) {
                map = new LinkedHashMap();
                map.put("name", className);
                reflectionMap.put(className, map);
            }
            List<Map<String, Object>> list = (List) map.get("fields");
            if (list == null) {
                list = new ArrayList<>();
                map.put("fields", list);
                list.add((Map) Utility.ofMap("name", field.getName()));
            } else {
                boolean contains = false;
                for (Map<String, Object> item : list) {
                    if (field.getName().equals(item.get("name"))) {
                        contains = true;
                        break;
                    }
                }
                if (!contains) {
                    list.add((Map) Utility.ofMap("name", field.getName()));
                }
            }
        } finally {
            reflectionLock.unlock();
        }
    }

    public static void putReflectionMethod(String className, Method method) {
        reflectionLock.lock();
        try {
            Map<String, Object> map = reflectionMap.get(className);
            if (map == null) {
                map = new LinkedHashMap();
                map.put("name", className);
                reflectionMap.put(className, map);
            }
            List<Map<String, Object>> list = (List) map.get("methods");
            if (list == null) {
                list = new ArrayList<>();
                map.put("methods", list);
                list.add(createMap(method.getName(), method.getParameterTypes()));
            } else {
                Class[] cts = method.getParameterTypes();
                String[] types = new String[cts.length];
                for (int i = 0; i < types.length; i++) {
                    types[i] = cts[i].getName();
                }
                boolean contains = false;
                for (Map<String, Object> item : list) {
                    if (method.getName().equals(item.get("name"))
                            && Arrays.equals(types, (String[]) item.get("parameterTypes"))) {
                        contains = true;
                        break;
                    }
                }
                if (!contains) {
                    list.add(createMap(method.getName(), method.getParameterTypes()));
                }
            }
        } finally {
            reflectionLock.unlock();
        }
    }

    public static void putReflectionDeclaredConstructors(Class clazz, String className, Class... cts) {
        reflectionLock.lock();
        try {
            Map<String, Object> map = reflectionMap.get(className);
            if (map == null) {
                map = new LinkedHashMap();
                map.put("name", className);
                reflectionMap.put(className, map);
            }
            map.put("allDeclaredConstructors", true);

            if (clazz != null) {
                if (clazz.isInterface()) {
                    return;
                }
                if (Modifier.isAbstract(clazz.getModifiers())) {
                    return;
                }
                try {
                    clazz.getDeclaredConstructor(cts);
                } catch (Throwable t) {
                    return;
                }
            }
            String[] types = new String[cts.length];
            for (int i = 0; i < types.length; i++) {
                types[i] = cts[i].getName();
            }
            List<Map<String, Object>> list = (List) map.get("methods");
            if (list == null) {
                list = new ArrayList<>();
                map.put("methods", list);
                list.add((Map) Utility.ofMap("name", "<init>", "parameterTypes", types));
            } else {
                boolean contains = false;
                for (Map<String, Object> item : list) {
                    if ("<init>".equals(item.get("name"))
                            && Arrays.equals(types, (String[]) item.get("parameterTypes"))) {
                        contains = true;
                        break;
                    }
                }
                if (!contains) {
                    list.add((Map) Utility.ofMap("name", "<init>", "parameterTypes", types));
                }
            }
        } finally {
            reflectionLock.unlock();
        }
    }

    public static void putReflectionPublicConstructors(Class clazz, String className) {
        reflectionLock.lock();
        try {
            Map<String, Object> map = reflectionMap.get(className);
            if (map == null) {
                map = new LinkedHashMap();
                map.put("name", className);
                reflectionMap.put(className, map);
            }
            map.put("allPublicConstructors", true);

            if (clazz != null) {
                if (clazz.isInterface()) {
                    return;
                }
                if (Modifier.isAbstract(clazz.getModifiers())) {
                    return;
                }
                try {
                    clazz.getConstructor();
                } catch (Throwable t) {
                    return;
                }
            }
            List<Map<String, Object>> list = (List) map.get("methods");
            if (list == null) {
                list = new ArrayList<>();
                map.put("methods", list);
                list.add((Map) Utility.ofMap("name", "<init>", "parameterTypes", new String[0]));
            } else {
                boolean contains = false;
                for (Map<String, Object> item : list) {
                    if ("<init>".equals(item.get("name")) && ((String[]) item.get("parameterTypes")).length == 0) {
                        contains = true;
                        break;
                    }
                }
                if (!contains) {
                    list.add((Map) Utility.ofMap("name", "<init>", "parameterTypes", new String[0]));
                }
            }
        } finally {
            reflectionLock.unlock();
        }
    }

    public static void putReflectionDeclaredMethods(String className) {
        reflectionLock.lock();
        try {
            Map<String, Object> map = reflectionMap.get(className);
            if (map == null) {
                map = new LinkedHashMap();
                map.put("name", className);
                reflectionMap.put(className, map);
            }
            map.put("allDeclaredMethods", true);
        } finally {
            reflectionLock.unlock();
        }
    }

    public static void putReflectionPublicMethods(String className) {
        reflectionLock.lock();
        try {
            Map<String, Object> map = reflectionMap.get(className);
            if (map == null) {
                map = new LinkedHashMap();
                map.put("name", className);
                reflectionMap.put(className, map);
            }
            map.put("allPublicMethods", true);
        } finally {
            reflectionLock.unlock();
        }
    }

    public static void putReflectionDeclaredFields(String className) {
        reflectionLock.lock();
        try {
            Map<String, Object> map = reflectionMap.get(className);
            if (map == null) {
                map = new LinkedHashMap();
                map.put("name", className);
                reflectionMap.put(className, map);
            }
            map.put("allDeclaredFields", true);
        } finally {
            reflectionLock.unlock();
        }
    }

    public static void putReflectionPublicFields(String className) {
        reflectionLock.lock();
        try {
            Map<String, Object> map = reflectionMap.get(className);
            if (map == null) {
                map = new LinkedHashMap();
                map.put("name", className);
                reflectionMap.put(className, map);
            }
            map.put("allPublicFields", true);
        } finally {
            reflectionLock.unlock();
        }
    }

    public static void putReflectionDeclaredClasses(String className) {
        reflectionLock.lock();
        try {
            Map<String, Object> map = reflectionMap.get(className);
            if (map == null) {
                map = new LinkedHashMap();
                map.put("name", className);
                reflectionMap.put(className, map);
            }
            map.put("allDeclaredClasses", true);
        } finally {
            reflectionLock.unlock();
        }
    }

    public static void putReflectionPublicClasses(String className) {
        reflectionLock.lock();
        try {
            Map<String, Object> map = reflectionMap.get(className);
            if (map == null) {
                map = new LinkedHashMap();
                map.put("name", className);
                reflectionMap.put(className, map);
            }
            map.put("allPublicClasses", true);
        } finally {
            reflectionLock.unlock();
        }
    }

    public static byte[] getDynClassBytes(String clazzName) {
        return allDynClassBytesMap.get(clazzName);
    }

    // https://www.graalvm.org/reference-manual/native-image/Reflection/#manual-configuration
    private static Map<String, Object> createMap(String name, Class... cts) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", name);
        String[] types = new String[cts.length];
        for (int i = 0; i < types.length; i++) {
            types[i] = cts[i].getName();
        }
        map.put("parameterTypes", types);
        return map;
    }

    public static void forEachReflection(BiConsumer<String, Map<String, Object>> action) {
        reflectionMap.forEach(action);
    }

    static void putDynClass0(String name, byte[] bs, Class clazz) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(bs);
        Objects.requireNonNull(clazz);
        allDynClassTypeMap.put(name, clazz);
        allDynClassBytesMap.put(name, bs);
    }

    public void putDynClass2(String name, byte[] bs, Class clazz) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(bs);
        Objects.requireNonNull(clazz);
        dynClassTypeMap.put(name, clazz);
        dynClassBytesMap.put(name, bs);
        allDynClassTypeMap.put(name, clazz);
        allDynClassBytesMap.put(name, bs);
    }

    public Class loadClass(String name, byte[] bs) {
        Class clz = defineClass(name, bs, 0, bs.length);
        dynClassTypeMap.put(name, clz);
        dynClassBytesMap.put(name, bs);
        allDynClassTypeMap.put(name, clz);
        allDynClassBytesMap.put(name, bs);
        return clz;
    }

    public Class loadClass(String name, byte[] bs, Map<String, byte[]> innerClassMap) {
        innerClassMap.forEach((k, v) -> {
            dynClassBytesMap.put(k, v);
            allDynClassBytesMap.put(k, v);
        });
        Class clz = defineClass(name, bs, 0, bs.length);
        dynClassTypeMap.put(name, clz);
        dynClassBytesMap.put(name, bs);
        allDynClassTypeMap.put(name, clz);
        allDynClassBytesMap.put(name, bs);
        return clz;
    }

    @Override
    public Class loadClass(String name) throws ClassNotFoundException {
        Class clazz = allDynClassTypeMap.get(name);
        if (clazz != null) {
            return clazz;
        }
        return super.loadClass(name);
    }

    @Override
    public Class findClass(String name) throws ClassNotFoundException {
        byte[] classData = dynClassBytesMap.get(name);
        if (classData == null) {
            Class clazz = dynClassTypeMap.get(name);
            if (clazz != null) {
                return clazz;
            }
            return super.findClass(name);
        }
        return super.defineClass(name, classData, 0, classData.length);
    }

    public void forEachCacheClass(Consumer<String> action) { // getAllURLs返回URL_NONE时需要重载此方法
        if (this.getParent() instanceof RedkaleClassLoader) {
            ((RedkaleClassLoader) getParent()).forEachCacheClass(action);
        }
    }

    public void addURI(URI uri) {
        try {
            super.addURL(uri.toURL());
        } catch (Exception e) {
            throw new RedkaleException(e);
        }
    }

    @Override
    public void addURL(URL url) {
        super.addURL(url);
    }

    @Override
    public URL[] getURLs() {
        return super.getURLs();
    }

    public URI[] getAllURIs() {
        ClassLoader loader = this;
        HashSet<URI> set = new HashSet<>();
        String appPath = System.getProperty("java.class.path");
        if (appPath != null && !appPath.isEmpty()) {
            for (String path : appPath.replace("://", "&&")
                    .replace(":\\", "##")
                    .replace(':', ';')
                    .split(";")) {
                try {
                    set.add(Paths.get(path.replace("&&", "://").replace("##", ":\\"))
                            .toRealPath()
                            .toFile()
                            .toURI());
                } catch (Exception e) {
                    // do nothing
                }
            }
        }
        try {
            do {
                String loaderName = loader.getClass().getName();
                if (loaderName.startsWith("sun.") && loaderName.contains("ExtClassLoader")) {
                    continue;
                }
                if (loader instanceof URLClassLoader) {
                    for (URL url : ((URLClassLoader) loader).getURLs()) {
                        set.add(url.toURI());
                    }
                } else { // 可能JDK9及以上
                    loader.getResource("org.redkale"); // 必须要运行一次，确保URLClassPath的值被填充完毕
                    Class loaderClazz = loader.getClass();
                    Object ucp = null;
                    do { // 读取 java.base/jdk.internal.loader.BuiltinClassLoader的URLClassPath ucp值
                        try {
                            // 需要在命令行里加入：  --add-opens java.base/jdk.internal.loader=ALL-UNNAMED
                            Field field = loaderClazz.getDeclaredField("ucp");
                            field.setAccessible(true);
                            ucp = field.get(loader);
                            break;
                        } catch (Throwable e) {
                            // do nothing
                        }
                    } while ((loaderClazz = loaderClazz.getSuperclass()) != Object.class);
                    if (ucp != null) { // URLClassPath
                        URL[] urls = null;
                        try { // 读取 java.base/jdk.internal.loader.URLClassPath的urls值
                            Method method = ucp.getClass().getMethod("getURLs");
                            urls = (URL[]) method.invoke(ucp);
                        } catch (Exception e) {
                            // do nothing
                        }
                        if (urls != null) {
                            for (URL url : urls) {
                                set.add(url.toURI());
                            }
                        }
                    }
                }
            } while ((loader = loader.getParent()) != null);
        } catch (URISyntaxException e) {
            throw new RedkaleException(e);
        }
        return set.toArray(new URI[set.size()]);
    }

    protected static class RedkaleCacheClassLoader extends RedkaleClassLoader {

        protected final Set<String> classes;

        public RedkaleCacheClassLoader(ClassLoader parent, Set<String> classes) {
            super(parent);
            this.classes = classes;
        }

        @Override
        public URI[] getAllURIs() {
            return new URI[] {URI_NONE};
        }

        @Override
        public void forEachCacheClass(Consumer<String> action) {
            classes.forEach(action);
            if (getParent() instanceof RedkaleClassLoader) {
                ((RedkaleClassLoader) getParent()).forEachCacheClass(action);
            }
        }
    }
}
