/*
 */
package org.redkale.boot;

import java.io.*;
import java.util.logging.*;
import org.redkale.util.Traces;

/**
 * Handler基类
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.7.0
 */
public abstract class LoggingBaseHandler extends Handler {

    // public static final String FORMATTER_FORMAT = "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%tL %4$s %2$s%n%5$s%6$s%n";
    // 无threadName、TID
    public static final String FORMATTER_FORMAT = "[%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%tL] %4$s %2$s\r\n%5$s%6$s\r\n";

    // 有threadName
    public static final String FORMATTER_FORMAT2 =
            "[%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%tL] [%7$s] %4$s %2$s\r\n%5$s%6$s\r\n";

    // 有threadName、TID
    public static final String FORMATTER_FORMAT3 =
            "[%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%tL] [%7$s] %8$s %4$s %2$s\r\n%5$s%6$s\r\n";

    // 防止设置system.property前调用Traces类导致enable提前初始化
    static boolean traceEnable = false;

    /** 默认的日志时间格式化类 与SimpleFormatter的区别在于level不使用本地化 */
    public static class LoggingFormater extends Formatter {

        @Override
        public String format(LogRecord log) {
            if (log.getThrown() == null
                    && log.getMessage() != null
                    && log.getMessage().startsWith("------")) {
                return formatMessage(log) + "\r\n";
            }
            String source;
            if (log.getSourceClassName() != null) {
                source = log.getSourceClassName();
                if (log.getSourceMethodName() != null) {
                    source += " " + log.getSourceMethodName();
                }
            } else {
                source = log.getLoggerName();
            }
            String message = formatMessage(log);
            String throwable = "";
            if (log.getThrown() != null) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw) {
                    @Override
                    public void println() {
                        super.print("\r\n");
                    }
                };
                pw.println();
                log.getThrown().printStackTrace(pw);
                pw.close();
                throwable = sw.toString();
            }
            Object[] params = log.getParameters();
            if (params != null) {
                if (params.length == 1) {
                    return String.format(
                            FORMATTER_FORMAT2,
                            log.getInstant().toEpochMilli(),
                            source,
                            log.getLoggerName(),
                            log.getLevel().getName(),
                            message,
                            throwable,
                            params[0]);
                } else if (params.length == 2) {
                    return String.format(
                            FORMATTER_FORMAT3,
                            log.getInstant().toEpochMilli(),
                            source,
                            log.getLoggerName(),
                            log.getLevel().getName(),
                            message,
                            throwable,
                            params[0],
                            params[1]);
                }
            }
            return String.format(
                    FORMATTER_FORMAT,
                    log.getInstant().toEpochMilli(),
                    source,
                    log.getLoggerName(),
                    log.getLevel().getName(),
                    message,
                    throwable);
        }
    }

    protected static void fillLogRecord(LogRecord log) {
        String traceid = null;
        if (traceEnable && Traces.enable()) {
            traceid = Traces.currentTraceid();
            if (traceid == null || traceid.isEmpty()) {
                traceid = "[TID:N/A] ";
            } else {
                traceid = "[TID:" + traceid + "] ";
            }
        }
        if (traceid == null) {
            log.setParameters(new String[] {Thread.currentThread().getName()});
        } else {
            log.setParameters(new String[] {Thread.currentThread().getName(), traceid});
        }
    }

    public static void initDebugLogConfig() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            final PrintStream ps = new PrintStream(out);
            final String handlerName =
                    LoggingFileHandler.LoggingConsoleHandler.class.getName(); // java.util.logging.ConsoleHandler
            ps.println("handlers = " + handlerName);
            ps.println(".level = FINEST");
            ps.println("jdk.level = INFO");
            ps.println("sun.level = INFO");
            ps.println("javax.level = INFO");
            ps.println("com.sun.level = INFO");
            ps.println("io.level = INFO");
            ps.println("org.junit.level = INFO");
            ps.println(handlerName + ".level = FINEST");
            ps.println(handlerName + ".formatter = " + LoggingFormater.class.getName());
            LogManager.getLogManager().readConfiguration(new ByteArrayInputStream(out.toByteArray()));
        } catch (Exception e) {
            // do nothing
        }
    }
}
