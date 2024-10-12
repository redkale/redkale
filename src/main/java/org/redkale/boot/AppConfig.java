/*
 *
 */
package org.redkale.boot;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.SimpleFormatter;
import static org.redkale.boot.Application.*;
import org.redkale.source.DataSources;
import org.redkale.util.AnyValue;
import org.redkale.util.AnyValueWriter;
import org.redkale.util.RedkaleClassLoader;
import static org.redkale.util.RedkaleClassLoader.putReflectionClass;
import static org.redkale.util.RedkaleClassLoader.putReflectionPublicConstructors;
import org.redkale.util.RedkaleException;
import org.redkale.util.Utility;
import org.redkale.util.YamlReader;

/**
 * 加载系统参数配置
 *
 * @author zhangjx
 */
class AppConfig {

    /**
     * 当前进程的配置文件， 类型：String、URI、File、Path <br>
     * 一般命名为: application.xml、application.properties， 若配置文件不是本地文件， 则File、Path类型的值为null
     */
    static final String PARAM_APP_CONF_FILE = "APP_CONF_FILE";

    // 是否用于main方法运行
    final boolean singletonMode;

    // 是否用于编译模式运行
    final boolean compileMode;

    // application.xml原始配置信息
    AnyValue config;

    // 是否从/META-INF中读取配置
    boolean configFromCache;

    // 本进程节点ID
    String nodeid;

    // 本进程节点ID
    String name;

    // 本地IP地址
    InetSocketAddress localAddress;

    // 进程根目录
    File home;

    // 配置文件目录
    File confFile;

    // 配置文件目录
    URI confDir;

    // 根ClassLoader
    RedkaleClassLoader classLoader;

    // Server根ClassLoader
    RedkaleClassLoader serverClassLoader;

    // 本地文件日志配置项
    final Properties locaLogProperties = new Properties();

    // 本地文件除logging配置之外的所有的配置项, 包含system.property.、mimetype.property.开头的
    final Properties localEnvProperties = new Properties();

    public AppConfig(boolean singletonMode, boolean compileMode) {
        this.singletonMode = singletonMode;
        this.compileMode = compileMode;
    }

    public static AppConfig create(boolean singletonMode, boolean compileMode) throws IOException {
        AppConfig rs = new AppConfig(singletonMode, compileMode);
        rs.init(loadAppConfig());
        return rs;
    }

    private void init(AnyValue conf) {
        this.config = conf;
        this.name = checkName(config.getValue("name", ""));
        this.nodeid = checkNodeid(config.getValue("nodeid", String.valueOf(Math.abs(System.nanoTime()))));
        this.configFromCache = "true".equals(config.getValue("[config-from-cache]"));
        // 初始化classLoader、serverClassLoader
        this.initClassLoader();
        // 初始化home、confDir、localAddress等信息
        this.initAppHome();
        // 读取本地参数配置
        this.initLocalProperties();
        // 读取本地日志配置
        this.initLogProperties();
        // 读取本地数据库配置
        this.initSourceProperties();
    }

    /** 初始化classLoader、serverClassLoader */
    private void initClassLoader() {
        ClassLoader currClassLoader = Thread.currentThread().getContextClassLoader();
        if (currClassLoader instanceof RedkaleClassLoader) {
            this.classLoader = (RedkaleClassLoader) currClassLoader;
        } else {
            Set<String> cacheClasses = null;
            if (!singletonMode && !compileMode) {
                try {
                    InputStream in =
                            Application.class.getResourceAsStream(RedkaleClassLoader.RESOURCE_CACHE_CLASSES_PATH);
                    if (in != null) {
                        BufferedReader reader =
                                new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 1024);
                        List<String> list = new ArrayList<>();
                        reader.lines().forEach(list::add);
                        Collections.sort(list);
                        if (!list.isEmpty()) {
                            cacheClasses = new LinkedHashSet<>(list);
                        }
                        in.close();
                    }
                } catch (Exception e) {
                    // do nothing
                }
            }
            if (cacheClasses == null) {
                this.classLoader = new RedkaleClassLoader(currClassLoader);
            } else {
                this.classLoader = new RedkaleClassLoader.createCacheClassLoader(currClassLoader, cacheClasses);
            }
            Thread.currentThread().setContextClassLoader(this.classLoader);
        }
        this.serverClassLoader = this.classLoader;
    }

    /** 初始化home、confDir、localAddress等信息 */
    private void initAppHome() {
        final File root = new File(System.getProperty(RESNAME_APP_HOME, ""));
        final String rootPath = getCanonicalPath(root);
        this.home = new File(rootPath);
        String confDir = System.getProperty(RESNAME_APP_CONF_DIR, "conf");
        if (confDir.contains("://")
                || confDir.startsWith("file:")
                || confDir.startsWith("resource:")
                || confDir.contains("!")) { // graalvm native-image startwith resource:META-INF
            this.confDir = URI.create(confDir);
            if (confDir.startsWith("file:")) {
                this.confFile = getCanonicalFile(new File(this.confDir.getPath()));
            }
        } else if (confDir.charAt(0) == '/' || confDir.indexOf(':') > -1) {
            this.confFile = getCanonicalFile(new File(confDir));
            this.confDir = confFile.toURI();
        } else {
            this.confFile = new File(getCanonicalPath(new File(this.home, confDir)));
            this.confDir = confFile.toURI();
        }
        String localaddr = config.getValue("address", "").trim();
        InetAddress addr = localaddr.isEmpty()
                ? Utility.localInetAddress()
                : new InetSocketAddress(localaddr, config.getIntValue("port")).getAddress();
        this.localAddress = new InetSocketAddress(addr, config.getIntValue("port"));
    }

    /** 读取本地参数配置 */
    private void initLocalProperties() {
        // 环境变量的优先级最高
        System.getProperties().forEach((k, v) -> {
            if (k.toString().startsWith("redkale.")) {
                localEnvProperties.put(k, v);
            }
        });
        AnyValue propsConf = this.config.getAnyValue("properties");
        if (propsConf == null) {
            final AnyValue resources = config.getAnyValue("resources");
            if (resources != null) {
                System.err.println("<resources> in application config file is deprecated");
                propsConf = resources.getAnyValue("properties");
            }
        }
        if (propsConf != null) { // 设置配置文件中的系统变量
            for (AnyValue prop : propsConf.getAnyValues("property")) {
                String key = prop.getValue("name", "");
                String value = prop.getValue("value");
                if (value != null) {
                    localEnvProperties.put(key, value);
                }
            }
            if (propsConf.getValue("load") != null) { // 加载本地配置项文件
                for (String dfload :
                        propsConf.getValue("load").replace(',', ';').split(";")) {
                    if (dfload.trim().isEmpty()) {
                        continue;
                    }
                    final URI df = RedkaleClassLoader.getConfResourceAsURI(
                            configFromCache ? null : this.confDir.toString(), dfload.trim());
                    if (df == null) {
                        continue;
                    }
                    if (!"file".equals(df.getScheme()) || df.toString().contains("!") || new File(df).isFile()) {
                        Properties props = new Properties();
                        try {
                            InputStream in = df.toURL().openStream();
                            props.load(in);
                            in.close();
                            localEnvProperties.putAll(props);
                        } catch (Exception e) {
                            throw new RedkaleException(e);
                        }
                    }
                }
            }
        }
        // 设置Convert默认配置项
        if (System.getProperty("redkale.convert.pool.size") == null
                && localEnvProperties.getProperty("system.property.redkale.convert.pool.size") == null) {
            localEnvProperties.put("system.property.redkale.convert.pool.size", "128");
        }
        if (System.getProperty("redkale.convert.writer.buffer.defsize") == null
                && localEnvProperties.getProperty("system.property.redkale.convert.writer.buffer.defsize") == null) {
            localEnvProperties.put("system.property.redkale.convert.writer.buffer.defsize", "4096");
        }
    }

    /** 读取本地DataSource、CacheSource配置 */
    private void initSourceProperties() {
        if ("file".equals(this.confDir.getScheme())) {
            File sourceFile = new File(new File(confDir), "source.properties");
            if (sourceFile.isFile() && sourceFile.canRead()) {
                try {
                    InputStream in = new FileInputStream(sourceFile);
                    Properties props = new Properties();
                    props.load(in);
                    in.close();
                    this.localEnvProperties.putAll(props);
                } catch (IOException e) {
                    throw new RedkaleException(e);
                }
            } else {
                sourceFile = new File(new File(confDir), "source.yml");
                if (!sourceFile.isFile() || !sourceFile.canRead()) {
                    sourceFile = new File(new File(confDir), "source.yaml");
                }
                if (sourceFile.isFile() && sourceFile.canRead()) {
                    try {
                        InputStream in = new FileInputStream(sourceFile);
                        String content = Utility.readThenClose(in);
                        Properties props = new YamlReader(content).read().toProperties();
                        this.localEnvProperties.putAll(props);
                    } catch (IOException e) {
                        throw new RedkaleException(e);
                    }
                } else {
                    // 兼容 persistence.xml 【已废弃】
                    File persist = new File(new File(confDir), "persistence.xml");
                    if (persist.isFile() && persist.canRead()) {
                        System.err.println("persistence.xml is deprecated, replaced by source.properties");
                        try {
                            InputStream in = new FileInputStream(persist);
                            this.localEnvProperties.putAll(DataSources.loadSourceProperties(in));
                            in.close();
                        } catch (IOException e) {
                            throw new RedkaleException(e);
                        }
                    }
                }
            }
        } else { // 从url或jar文件中resources读取
            Properties props = new Properties();
            try {
                final URI sourceURI = RedkaleClassLoader.getConfResourceAsURI(
                        configFromCache ? null : this.confDir.toString(), "source.properties");
                InputStream in = sourceURI.toURL().openStream();
                props.load(in);
                in.close();
            } catch (Exception e) {
                try {
                    final URI sourceURI = RedkaleClassLoader.getConfResourceAsURI(
                            configFromCache ? null : this.confDir.toString(), "source.yml");
                    InputStream in = sourceURI.toURL().openStream();
                    String content = Utility.readThenClose(in);
                    props.putAll(new YamlReader(content).read().toProperties());
                } catch (Exception e2) {
                    try {
                        final URI sourceURI = RedkaleClassLoader.getConfResourceAsURI(
                                configFromCache ? null : this.confDir.toString(), "source.yaml");
                        InputStream in = sourceURI.toURL().openStream();
                        String content = Utility.readThenClose(in);
                        props.putAll(new YamlReader(content).read().toProperties());
                    } catch (Exception e3) {
                        // 没有文件 跳过
                    }
                }
            }
            this.localEnvProperties.putAll(props);
        }
    }

    /** 读取本地日志配置 */
    private void initLogProperties() {
        URI logConfURI;
        File logConfFile = null;
        if (configFromCache) {
            logConfURI = RedkaleClassLoader.getConfResourceAsURI(null, "logging.properties");
        } else if ("file".equals(confDir.getScheme())) {
            logConfFile = new File(confDir.getPath(), "logging.properties");
            logConfURI = logConfFile.toURI();
            if (!logConfFile.isFile() || !logConfFile.canRead()) {
                logConfFile = null;
            }
        } else {
            logConfURI = URI.create(confDir + (confDir.toString().endsWith("/") ? "" : "/") + "logging.properties");
        }
        if (!"file".equals(confDir.getScheme()) || logConfFile != null) {
            try {
                InputStream fin = logConfURI.toURL().openStream();
                Properties properties0 = new Properties();
                properties0.load(fin);
                fin.close();
                properties0.forEach(locaLogProperties::put);
            } catch (IOException e) {
                throw new RedkaleException("read logging.properties error", e);
            }
        }
        if (compileMode) {
            putReflectionClass(java.lang.Class.class.getName());
            putReflectionPublicConstructors(SimpleFormatter.class, SimpleFormatter.class.getName());
            putReflectionPublicConstructors(LoggingSearchHandler.class, LoggingSearchHandler.class.getName());
            putReflectionPublicConstructors(LoggingFileHandler.class, LoggingFileHandler.class.getName());
            putReflectionPublicConstructors(
                    LoggingFileHandler.LoggingFormater.class, LoggingFileHandler.LoggingFormater.class.getName());
            putReflectionPublicConstructors(
                    LoggingFileHandler.LoggingConsoleHandler.class,
                    LoggingFileHandler.LoggingConsoleHandler.class.getName());
            putReflectionPublicConstructors(
                    LoggingFileHandler.LoggingSncpFileHandler.class,
                    LoggingFileHandler.LoggingSncpFileHandler.class.getName());
        }
    }

    /**
     * 从本地application.xml或application.properties文件加载配置信息
     *
     * @return 配置信息
     * @throws IOException
     */
    static AnyValue loadAppConfig() throws IOException {
        final String home = new File(System.getProperty(RESNAME_APP_HOME, ""))
                .getCanonicalPath()
                .replace('\\', '/');
        String sysConfFile = System.getProperty(PARAM_APP_CONF_FILE);
        if (sysConfFile != null) {
            String text;
            if (sysConfFile.contains("://")) {
                text = Utility.readThenClose(URI.create(sysConfFile).toURL().openStream());
            } else {
                File f = new File(sysConfFile);
                if (f.isFile() && f.canRead()) {
                    text = Utility.readThenClose(new FileInputStream(f));
                } else {
                    throw new IOException("Read application conf file (" + sysConfFile + ") error ");
                }
            }
            if (sysConfFile.endsWith(".yml") || sysConfFile.endsWith(".yaml")) {
                return AnyValue.loadFromYaml(text).getAnyValue("redkale");
            }
            return sysConfFile.endsWith(".xml")
                    ? AnyValue.loadFromXml(text, (k, v) -> v.replace("${" + RESNAME_APP_HOME + "}", home))
                            .getAnyValue("application")
                    : AnyValue.loadFromProperties(text).getAnyValue("redkale");
        }
        String confDir = System.getProperty(RESNAME_APP_CONF_DIR, "conf");
        URI appConfFile;
        boolean fromCache = false;
        boolean yaml = false;
        if (confDir.contains("://")) { // jar内部资源
            appConfFile = URI.create(confDir + (confDir.endsWith("/") ? "" : "/") + "application.xml");
            try {
                appConfFile.toURL().openStream().close();
            } catch (IOException e) { // 没有application.xml就尝试读application.yaml
                appConfFile = URI.create(confDir + (confDir.endsWith("/") ? "" : "/") + "application.yml");
                try {
                    appConfFile.toURL().openStream().close();
                    yaml = true;
                } catch (IOException e2) { // 没有application.yml就尝试读application.yaml
                    appConfFile = URI.create(confDir + (confDir.endsWith("/") ? "" : "/") + "application.yaml");
                    try {
                        appConfFile.toURL().openStream().close();
                        yaml = true;
                    } catch (IOException e3) { // 没有application.yaml就尝试读application.properties
                        appConfFile =
                                URI.create(confDir + (confDir.endsWith("/") ? "" : "/") + "application.properties");
                    }
                }
            }
        } else if (confDir.charAt(0) == '/' || confDir.indexOf(':') > 0) { // 绝对路径
            File f = new File(confDir, "application.xml");
            if (f.isFile() && f.canRead()) {
                appConfFile = f.toURI();
                confDir = f.getParentFile().getCanonicalPath();
            } else {
                f = new File(confDir, "application.yml");
                if (!f.isFile() || !f.canRead()) {
                    f = new File(confDir, "application.yaml");
                }
                if (f.isFile() && f.canRead()) {
                    appConfFile = f.toURI();
                    confDir = f.getParentFile().getCanonicalPath();
                    yaml = true;
                } else {
                    f = new File(confDir, "application.properties");
                    if (f.isFile() && f.canRead()) {
                        appConfFile = f.toURI();
                        confDir = f.getParentFile().getCanonicalPath();
                    } else {
                        // 不能传confDir
                        appConfFile = RedkaleClassLoader.getConfResourceAsURI(null, "application.xml");
                        try {
                            appConfFile.toURL().openStream().close();
                        } catch (IOException e) { // 没有application.xml就尝试读application.yml
                            appConfFile = RedkaleClassLoader.getConfResourceAsURI(null, "application.yml");
                            try {
                                appConfFile.toURL().openStream().close();
                                yaml = true;
                            } catch (IOException e2) { // 没有application.yml就尝试读application.yaml
                                appConfFile = RedkaleClassLoader.getConfResourceAsURI(null, "application.yaml");
                                try {
                                    appConfFile.toURL().openStream().close();
                                    yaml = true;
                                } catch (IOException e3) { // 没有application.yaml就尝试读application.properties
                                    appConfFile =
                                            RedkaleClassLoader.getConfResourceAsURI(null, "application.properties");
                                }
                            }
                        }
                        confDir = appConfFile
                                .toString()
                                .replace("/application.xml", "")
                                .replace("/application.yml", "")
                                .replace("/application.yaml", "")
                                .replace("/application.properties", "");
                        fromCache = true;
                    }
                }
            }
        } else { // 相对路径
            File f = new File(new File(home, confDir), "application.xml");
            if (f.isFile() && f.canRead()) {
                appConfFile = f.toURI();
                confDir = f.getParentFile().getCanonicalPath();
            } else {
                f = new File(new File(home, confDir), "application.yml");
                if (!f.isFile() || !f.canRead()) {
                    f = new File(confDir, "application.yaml");
                }
                if (f.isFile() && f.canRead()) {
                    appConfFile = f.toURI();
                    confDir = f.getParentFile().getCanonicalPath();
                    yaml = true;
                } else {
                    f = new File(new File(home, confDir), "application.properties");
                    if (f.isFile() && f.canRead()) {
                        appConfFile = f.toURI();
                        confDir = f.getParentFile().getCanonicalPath();
                    } else {
                        // 不能传confDir
                        appConfFile = RedkaleClassLoader.getConfResourceAsURI(null, "application.xml");
                        try {
                            appConfFile.toURL().openStream().close();
                        } catch (IOException e) { // 没有application.xml就尝试读application.yaml
                            appConfFile = RedkaleClassLoader.getConfResourceAsURI(null, "application.yml");
                            try {
                                appConfFile.toURL().openStream().close();
                                yaml = true;
                            } catch (IOException e2) { // 没有application.yml就尝试读application.yaml
                                appConfFile = RedkaleClassLoader.getConfResourceAsURI(null, "application.yaml");
                                try {
                                    appConfFile.toURL().openStream().close();
                                    yaml = true;
                                } catch (IOException e3) { // 没有application.yaml就尝试读application.properties
                                    appConfFile =
                                            RedkaleClassLoader.getConfResourceAsURI(null, "application.properties");
                                }
                            }
                        }
                        confDir = appConfFile
                                .toString()
                                .replace("/application.xml", "")
                                .replace("/application.yml", "")
                                .replace("/application.yaml", "")
                                .replace("/application.properties", "");
                        fromCache = true;
                    }
                }
            }
        }
        System.setProperty(RESNAME_APP_CONF_DIR, confDir);
        String text = Utility.readThenClose(appConfFile.toURL().openStream());
        AnyValue conf;
        if (yaml) {
            conf = AnyValue.loadFromYaml(text).getAnyValue("redkale");
        } else if (text.trim().startsWith("<")) {
            conf = AnyValue.loadFromXml(text, (k, v) -> v.replace("${APP_HOME}", home))
                    .getAnyValue("application");
        } else {
            conf = AnyValue.loadFromProperties(text).getAnyValue("redkale");
        }
        if (fromCache) {
            ((AnyValueWriter) conf).addValue("[config-from-cache]", "true");
        }
        return conf;
    }

    /**
     * 检查name是否含特殊字符
     *
     * @param name
     * @return
     */
    private static String checkName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        for (char ch : name.toCharArray()) {
            if (!((ch >= '0' && ch <= '9')
                    || (ch >= 'a' && ch <= 'z')
                    || (ch >= 'A' && ch <= 'Z')
                    || ch == '_'
                    || ch == '.'
                    || ch == '-')) { // 不能含特殊字符
                throw new RedkaleException("name only 0-9 a-z A-Z _ - .");
            }
        }
        return name;
    }

    /**
     * 检查node是否含特殊字符
     *
     * @param name
     * @return
     */
    private static String checkNodeid(String nodeid) {
        if (nodeid == null || nodeid.isEmpty()) {
            return nodeid;
        }
        for (char ch : nodeid.toCharArray()) {
            if (!((ch >= '0' && ch <= '9')
                    || (ch >= 'a' && ch <= 'z')
                    || (ch >= 'A' && ch <= 'Z')
                    || ch == '_'
                    || ch == '.'
                    || ch == '-')) { // 不能含特殊字符
                throw new RedkaleException("nodeid only 0-9 a-z A-Z _ - .");
            }
        }
        return nodeid;
    }

    private String getCanonicalPath(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            return file.getAbsolutePath();
        }
    }

    private File getCanonicalFile(File file) {
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            return file;
        }
    }
}
