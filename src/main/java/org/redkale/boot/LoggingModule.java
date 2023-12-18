/*
 *
 */
package org.redkale.boot;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.SimpleFormatter;
import org.redkale.net.sncp.SncpClient;
import org.redkale.util.Environment;
import org.redkale.util.ResourceEvent;

/**
 *
 * 日志模块组件
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
class LoggingModule {

    private final Application application;

    //日志配置资源
    private final Properties loggingProperties = new Properties();

    LoggingModule(Application application) {
        this.application = application;
    }

    /**
     * 配置变更
     *
     * @param events 变更项
     */
    public void onEnvironmentUpdated(List<ResourceEvent> events) {
        Set<String> loggingRemovedKeys = new HashSet<>();
        Properties loggingChangedProps = new Properties();
        for (ResourceEvent event : events) {
            if (event.newValue() == null) {
                if (loggingProperties.containsKey(event.name())) {
                    loggingRemovedKeys.add(event.name());
                }
            } else {
                loggingChangedProps.put(event.name(), event.newValue());
            }
        }
        if (!loggingRemovedKeys.isEmpty() || !loggingChangedProps.isEmpty()) {
            Properties newProps = new Properties(this.loggingProperties);
            loggingRemovedKeys.forEach(newProps::remove);
            newProps.putAll(loggingChangedProps);
            reconfigLogging(false, newProps);
        }
    }

    /**
     * 设置日志策略
     *
     * @param first    是否首次设置
     * @param allProps 配置项全量
     */
    public void reconfigLogging(boolean first, Properties allProps) {
        String searchRawHandler = "java.util.logging.SearchHandler";
        String searchReadHandler = LoggingSearchHandler.class.getName();
        Properties onlyLogProps = new Properties();
        Environment environment = application.getEnvironment();
        allProps.entrySet().forEach(x -> {
            String key = x.getKey().toString();
            if (key.startsWith("java.util.logging.") || key.contains(".level") || key.equals("handlers")) {
                String val = environment.getPropertyValue(x.getValue().toString()
                    .replace("%m", "%tY%tm").replace("%d", "%tY%tm%td") //兼容旧时间格式
                    .replace(searchRawHandler, searchReadHandler));
                onlyLogProps.put(key.replace(searchRawHandler, searchReadHandler), val);
            }
        });
        if (onlyLogProps.getProperty("java.util.logging.FileHandler.formatter") == null) {
            if (application.isCompileMode()) {
                onlyLogProps.setProperty("java.util.logging.FileHandler.formatter", SimpleFormatter.class.getName());
                if (onlyLogProps.getProperty("java.util.logging.SimpleFormatter.format") == null) {
                    onlyLogProps.setProperty("java.util.logging.SimpleFormatter.format", LoggingFileHandler.FORMATTER_FORMAT.replaceAll("\r\n", "%n"));
                }
            } else {
                onlyLogProps.setProperty("java.util.logging.FileHandler.formatter", LoggingFileHandler.LoggingFormater.class.getName());
            }
        }
        if (onlyLogProps.getProperty("java.util.logging.ConsoleHandler.formatter") == null) {
            if (application.isCompileMode()) {
                onlyLogProps.setProperty("java.util.logging.ConsoleHandler.formatter", SimpleFormatter.class.getName());
                if (onlyLogProps.getProperty("java.util.logging.SimpleFormatter.format") == null) {
                    onlyLogProps.setProperty("java.util.logging.SimpleFormatter.format", LoggingFileHandler.FORMATTER_FORMAT.replaceAll("\r\n", "%n"));
                }
            } else {
                onlyLogProps.setProperty("java.util.logging.ConsoleHandler.formatter", LoggingFileHandler.LoggingFormater.class.getName());
            }
        }
        if (!application.isCompileMode()) { //ConsoleHandler替换成LoggingConsoleHandler
            final String handlers = onlyLogProps.getProperty("handlers");
            if (handlers != null && handlers.contains("java.util.logging.ConsoleHandler")) {
                final String consoleHandlerClass = LoggingFileHandler.LoggingConsoleHandler.class.getName();
                onlyLogProps.setProperty("handlers", handlers.replace("java.util.logging.ConsoleHandler", consoleHandlerClass));
                Properties prop = new Properties();
                String prefix = consoleHandlerClass + ".";
                onlyLogProps.entrySet().forEach(x -> {
                    if (x.getKey().toString().startsWith("java.util.logging.ConsoleHandler.")) {
                        prop.put(x.getKey().toString().replace("java.util.logging.ConsoleHandler.", prefix), x.getValue());
                    }
                });
                prop.entrySet().forEach(x -> {
                    onlyLogProps.put(x.getKey(), x.getValue());
                });
            }
        }
        String fileHandlerPattern = onlyLogProps.getProperty("java.util.logging.FileHandler.pattern");
        if (fileHandlerPattern != null && fileHandlerPattern.contains("%")) { //带日期格式
            final String fileHandlerClass = LoggingFileHandler.class.getName();
            Properties prop = new Properties();
            final String handlers = onlyLogProps.getProperty("handlers");
            if (handlers != null && handlers.contains("java.util.logging.FileHandler")) {
                //singletonrun模式下不输出文件日志
                prop.setProperty("handlers", handlers.replace("java.util.logging.FileHandler",
                    application.isSingletonMode() || application.isCompileMode() ? "" : fileHandlerClass));
            }
            if (!prop.isEmpty()) {
                String prefix = fileHandlerClass + ".";
                onlyLogProps.entrySet().forEach(x -> {
                    if (x.getKey().toString().startsWith("java.util.logging.FileHandler.")) {
                        prop.put(x.getKey().toString().replace("java.util.logging.FileHandler.", prefix), x.getValue());
                    }
                });
                prop.entrySet().forEach(x -> onlyLogProps.put(x.getKey(), x.getValue()));
            }
            if (!application.isCompileMode()) {
                onlyLogProps.put(SncpClient.class.getSimpleName() + ".handlers", LoggingFileHandler.LoggingSncpFileHandler.class.getName());
            }
        }
        if (application.isCompileMode()) {
            onlyLogProps.put("handlers", "java.util.logging.ConsoleHandler");
            Map newprop = new HashMap(onlyLogProps);
            newprop.forEach((k, v) -> {
                if (k.toString().startsWith("java.util.logging.FileHandler.")) {
                    onlyLogProps.remove(k);
                }
            });
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        final PrintStream ps = new PrintStream(out);
        onlyLogProps.forEach((x, y) -> ps.println(x + "=" + y));
        try {
            LogManager manager = LogManager.getLogManager();
            manager.readConfiguration(new ByteArrayInputStream(out.toByteArray()));
            this.loggingProperties.clear();
            this.loggingProperties.putAll(onlyLogProps);
            Enumeration<String> en = manager.getLoggerNames();
            while (en.hasMoreElements()) {
                for (Handler handler : manager.getLogger(en.nextElement()).getHandlers()) {
                    if (handler instanceof LoggingSearchHandler) {
                        ((LoggingSearchHandler) handler).application = application;
                    }
                }
            }
        } catch (IOException e) { //不会发生
        }
    }
}
