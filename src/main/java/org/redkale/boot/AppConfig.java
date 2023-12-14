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
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import static org.redkale.boot.Application.RESNAME_APP_CONF_DIR;
import static org.redkale.boot.Application.RESNAME_APP_CONF_FILE;
import static org.redkale.boot.Application.RESNAME_APP_HOME;
import org.redkale.util.AnyValue;
import org.redkale.util.RedkaleClassLoader;
import org.redkale.util.RedkaleException;
import org.redkale.util.Utility;

/**
 * 加载系统参数配置
 *
 * @author zhangjx
 */
class AppConfig {

    //日志
    private final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    final long startTime = System.currentTimeMillis();

    //是否用于main方法运行
    final boolean singletonMode;

    //是否用于编译模式运行
    final boolean compileMode;

    //application.xml原始配置信息
    AnyValue config;

    //是否从/META-INF中读取配置
    boolean configFromCache;

    //本进程节点ID
    int nodeid;

    //本进程节点ID
    String name;

    //本地IP地址
    InetSocketAddress localAddress;

    //配置信息Properties
    Properties envProperties;
    //进程根目录

    File home;

    //进程根目录
    String homePath;

    //配置文件目录
    File confFile;

    //配置文件目录
    URI confPath;

    //根ClassLoader
    RedkaleClassLoader classLoader;

    //Server根ClassLoader
    RedkaleClassLoader serverClassLoader;

    //本地文件非system.property.开头的配置项
    Properties localEnvProperties = new Properties();

    //本地文件设置System.properties且不存于System.properties的配置项
    Properties localSysProperties = new Properties();

    //本地文件日志配置项
    Properties locaLogProperties = new Properties();

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
        this.nodeid = config.getIntValue("nodeid", 0);
        this.configFromCache = "true".equals(config.getValue("[config-from-cache]"));
        //初始化classLoader、serverClassLoader
        this.initClassLoader();
        //初始化home、confPath、localAddress等信息
        this.initAppHome();
        //读取本地参数配置
        this.initLocalProperties();
        //读取本地日志配置
        this.initLogProperties();
    }

    /**
     * 初始化classLoader、serverClassLoader
     */
    private void initClassLoader() {
        ClassLoader currClassLoader = Thread.currentThread().getContextClassLoader();
        if (currClassLoader instanceof RedkaleClassLoader) {
            this.classLoader = (RedkaleClassLoader) currClassLoader;
        } else {
            Set<String> cacheClasses = null;
            if (!singletonMode && !compileMode) {
                try {
                    InputStream in = Application.class.getResourceAsStream(RedkaleClassLoader.RESOURCE_CACHE_CLASSES_PATH);
                    if (in != null) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 1024);
                        List<String> list = new ArrayList<>();
                        reader.lines().forEach(list::add);
                        Collections.sort(list);
                        if (!list.isEmpty()) {
                            cacheClasses = new LinkedHashSet<>(list);
                        }
                        in.close();
                    }
                } catch (Exception e) {
                    //do nothing
                }
            }
            if (cacheClasses == null) {
                this.classLoader = new RedkaleClassLoader(currClassLoader);
            } else {
                this.classLoader = new RedkaleClassLoader.RedkaleCacheClassLoader(currClassLoader, cacheClasses);
            }
            Thread.currentThread().setContextClassLoader(this.classLoader);
        }
        if (compileMode || this.classLoader instanceof RedkaleClassLoader.RedkaleCacheClassLoader) {
            this.serverClassLoader = this.classLoader;
        } else {
            this.serverClassLoader = new RedkaleClassLoader(this.classLoader);
        }
    }

    /**
     * 初始化home、confPath、localAddress等信息
     */
    private void initAppHome() {
        final File root = new File(System.getProperty(RESNAME_APP_HOME, ""));
        final String rootPath = getCanonicalPath(root);
        this.home = new File(rootPath);
        this.homePath = this.home.getPath();
        String confDir = System.getProperty(RESNAME_APP_CONF_DIR, "conf");
        if (confDir.contains("://") || confDir.startsWith("file:")
            || confDir.startsWith("resource:") || confDir.contains("!")) { //graalvm native-image startwith resource:META-INF
            this.confPath = URI.create(confDir);
            if (confDir.startsWith("file:")) {
                this.confFile = getCanonicalFile(new File(this.confPath.getPath()));
            }
        } else if (confDir.charAt(0) == '/' || confDir.indexOf(':') > -1) {
            this.confFile = getCanonicalFile(new File(confDir));
            this.confPath = confFile.toURI();
        } else {
            this.confFile = new File(getCanonicalPath(new File(this.home, confDir)));
            this.confPath = confFile.toURI();
        }
        String localaddr = config.getValue("address", "").trim();
        InetAddress addr = localaddr.isEmpty() ? Utility.localInetAddress() : new InetSocketAddress(localaddr, config.getIntValue("port")).getAddress();
        this.localAddress = new InetSocketAddress(addr, config.getIntValue("port"));
    }

    /**
     * 读取本地参数配置
     */
    private void initLocalProperties() {
        AnyValue propsConf = this.config.getAnyValue("properties");
        if (propsConf != null) { //设置配置文件中的系统变量
            for (AnyValue prop : propsConf.getAnyValues("property")) {
                String key = prop.getValue("name", "");
                String value = prop.getValue("value");
                if (value != null && key.startsWith("system.property.")) {
                    String propName = key.substring("system.property.".length());
                    if (System.getProperty(propName) == null) { //命令行传参数优先级高
                        localSysProperties.put(propName, value);
                    }
                } else if (value != null) {
                    localEnvProperties.put(key, value);
                }
            }
        }
        //设置Convert默认配置项
        if (System.getProperty("redkale.convert.pool.size") == null
            && localSysProperties.getProperty("redkale.convert.pool.size") == null) {
            localSysProperties.put("redkale.convert.pool.size", "128");
        }
        if (System.getProperty("redkale.convert.writer.buffer.defsize") == null
            && localSysProperties.getProperty("redkale.convert.writer.buffer.defsize") == null) {
            localSysProperties.put("redkale.convert.writer.buffer.defsize", "4096");
        }
    }

    /**
     * 读取本地日志配置
     */
    private void initLogProperties() {
        URI logConfURI;
        File logConfFile = null;
        if (configFromCache) {
            logConfURI = RedkaleClassLoader.getConfResourceAsURI(null, "logging.properties");
        } else if ("file".equals(confPath.getScheme())) {
            logConfFile = new File(confPath.getPath(), "logging.properties");
            logConfURI = logConfFile.toURI();
            if (!logConfFile.isFile() || !logConfFile.canRead()) {
                logConfFile = null;
            }
        } else {
            logConfURI = URI.create(confPath + (confPath.toString().endsWith("/") ? "" : "/") + "logging.properties");
        }
        if (!"file".equals(confPath.getScheme()) || logConfFile != null) {
            try {
                InputStream fin = logConfURI.toURL().openStream();
                Properties properties0 = new Properties();
                properties0.load(fin);
                fin.close();
                properties0.forEach((k, v) -> locaLogProperties.put(k.toString(), v.toString()));
            } catch (IOException e) {
                throw new RedkaleException("read logging.properties error", e);
            }
        }
        if (compileMode) {
            RedkaleClassLoader.putReflectionClass(java.lang.Class.class.getName());
            RedkaleClassLoader.putReflectionPublicConstructors(SimpleFormatter.class, SimpleFormatter.class.getName());
            RedkaleClassLoader.putReflectionPublicConstructors(LoggingSearchHandler.class, LoggingSearchHandler.class.getName());
            RedkaleClassLoader.putReflectionPublicConstructors(LoggingFileHandler.class, LoggingFileHandler.class.getName());
            RedkaleClassLoader.putReflectionPublicConstructors(LoggingFileHandler.LoggingFormater.class, LoggingFileHandler.LoggingFormater.class.getName());
            RedkaleClassLoader.putReflectionPublicConstructors(LoggingFileHandler.LoggingConsoleHandler.class, LoggingFileHandler.LoggingConsoleHandler.class.getName());
            RedkaleClassLoader.putReflectionPublicConstructors(LoggingFileHandler.LoggingSncpFileHandler.class, LoggingFileHandler.LoggingSncpFileHandler.class.getName());
        }
    }

    /**
     * 从本地application.xml或application.properties文件加载配置信息
     *
     * @return 配置信息
     * @throws IOException
     */
    static AnyValue loadAppConfig() throws IOException {
        final String home = new File(System.getProperty(RESNAME_APP_HOME, "")).getCanonicalPath().replace('\\', '/');
        String sysConfFile = System.getProperty(RESNAME_APP_CONF_FILE);
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
            return text.trim().startsWith("<") ? AnyValue.loadFromXml(text, (k, v) -> v.replace("${" + RESNAME_APP_HOME + "}", home))
                .getAnyValue("application") : AnyValue.loadFromProperties(text).getAnyValue("redkale");
        }
        String confDir = System.getProperty(RESNAME_APP_CONF_DIR, "conf");
        URI appConfFile;
        boolean fromCache = false;
        if (confDir.contains("://")) { //jar内部资源
            appConfFile = URI.create(confDir + (confDir.endsWith("/") ? "" : "/") + "application.xml");
            try {
                appConfFile.toURL().openStream().close();
            } catch (IOException e) { //没有application.xml就尝试读application.properties
                appConfFile = URI.create(confDir + (confDir.endsWith("/") ? "" : "/") + "application.properties");
            }
        } else if (confDir.charAt(0) == '/' || confDir.indexOf(':') > 0) { //绝对路径
            File f = new File(confDir, "application.xml");
            if (f.isFile() && f.canRead()) {
                appConfFile = f.toURI();
                confDir = f.getParentFile().getCanonicalPath();
            } else {
                f = new File(confDir, "application.properties");
                if (f.isFile() && f.canRead()) {
                    appConfFile = f.toURI();
                    confDir = f.getParentFile().getCanonicalPath();
                } else {
                    appConfFile = RedkaleClassLoader.getConfResourceAsURI(null, "application.xml"); //不能传confDir
                    try {
                        appConfFile.toURL().openStream().close();
                    } catch (IOException e) { //没有application.xml就尝试读application.properties
                        appConfFile = RedkaleClassLoader.getConfResourceAsURI(null, "application.properties");
                    }
                    confDir = appConfFile.toString().replace("/application.xml", "").replace("/application.properties", "");
                    fromCache = true;
                }
            }
        } else { //相对路径
            File f = new File(new File(home, confDir), "application.xml");
            if (f.isFile() && f.canRead()) {
                appConfFile = f.toURI();
                confDir = f.getParentFile().getCanonicalPath();
            } else {
                f = new File(new File(home, confDir), "application.properties");
                if (f.isFile() && f.canRead()) {
                    appConfFile = f.toURI();
                    confDir = f.getParentFile().getCanonicalPath();
                } else {
                    appConfFile = RedkaleClassLoader.getConfResourceAsURI(null, "application.xml"); //不能传confDir
                    try {
                        appConfFile.toURL().openStream().close();
                    } catch (IOException e) { //没有application.xml就尝试读application.properties
                        appConfFile = RedkaleClassLoader.getConfResourceAsURI(null, "application.properties");
                    }
                    confDir = appConfFile.toString().replace("/application.xml", "").replace("/application.properties", "");
                    fromCache = true;
                }
            }
        }
        System.setProperty(RESNAME_APP_CONF_DIR, confDir);
        String text = Utility.readThenClose(appConfFile.toURL().openStream());
        AnyValue conf;
        if (text.trim().startsWith("<")) {
            conf = AnyValue.loadFromXml(text, (k, v) -> v.replace("${APP_HOME}", home)).getAnyValue("application");
        } else {
            conf = AnyValue.loadFromProperties(text).getAnyValue("redkale");
        }
        if (fromCache) {
            ((AnyValue.DefaultAnyValue) conf).addValue("[config-from-cache]", "true");
        }

        return conf;
    }

    /**
     * 检查name是否含特殊字符
     *
     * @param name
     *
     * @return
     */
    private String checkName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        for (char ch : name.toCharArray()) {
            if (!((ch >= '0' && ch <= '9') || ch == '_' || ch == '.' || ch == '-' || (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z'))) { //不能含特殊字符
                throw new RedkaleException("name only 0-9 a-z A-Z _ - . cannot begin 0-9");
            }
        }
        return name;
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
