/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.boot;

import java.io.*;
import java.nio.file.*;
import static java.nio.file.StandardCopyOption.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;
import java.util.logging.Formatter;

/**
 * 自定义的日志存储类
 * <p>
 * @author zhangjx
 */
public class LogFileHandler extends Handler {

    public static class LoggingFormater extends Formatter {

        private static final String format = "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%tL %4$s %2$s\r\n%5$s%6$s\r\n";

        @Override
        public String format(LogRecord record) {
            String source;
            if (record.getSourceClassName() != null) {
                source = record.getSourceClassName();
                if (record.getSourceMethodName() != null) {
                    source += " " + record.getSourceMethodName();
                }
            } else {
                source = record.getLoggerName();
            }
            String message = formatMessage(record);
            String throwable = "";
            if (record.getThrown() != null) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw) {
                    @Override
                    public void println() {
                        super.print("\r\n");
                    }
                };
                pw.println();
                record.getThrown().printStackTrace(pw);
                pw.close();
                throwable = sw.toString();
            }
            return String.format(format,
                    System.currentTimeMillis(),
                    source,
                    record.getLoggerName(),
                    record.getLevel().getName(),
                    message,
                    throwable);
        }

    }

    protected final LinkedBlockingQueue<LogRecord> records = new LinkedBlockingQueue();

    private String pattern;

    private int limit;   //文件大小限制

    private final AtomicInteger index = new AtomicInteger();

    private int count = 1;  //文件限制

    private long tomorrow;

    private boolean append;

    private final AtomicLong length = new AtomicLong();

    private File logfile;

    private OutputStream stream;

    public LogFileHandler() {
        updateTomorrow();
        configure();
        open();
    }

    private void updateTomorrow() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.DAY_OF_YEAR, 1);
        long t = cal.getTimeInMillis();
        if (this.tomorrow != t) index.set(0);
        this.tomorrow = t;
    }

    private void open() {
        new Thread() {
            {
                setName("Logging-FileHandler-Thread");
                setDaemon(true);
            }

            @Override
            public void run() {
                while (true) {
                    try {
                        LogRecord record = records.take();
                        final boolean bigger = (limit > 0 && limit <= length.get());
                        if (bigger || tomorrow <= record.getMillis()) {
                            updateTomorrow();
                            if (stream != null) {
                                stream.close();
                                if (bigger) {
                                    for (int i = Math.min(count - 2, index.get() - 1); i > 0; i--) {
                                        File greater = new File(logfile.getPath() + "." + i);
                                        if (greater.exists()) Files.move(greater.toPath(), new File(logfile.getPath() + "." + (i + 1)).toPath(), REPLACE_EXISTING, ATOMIC_MOVE);
                                    }
                                    Files.move(logfile.toPath(), new File(logfile.getPath() + ".1").toPath(), REPLACE_EXISTING, ATOMIC_MOVE);
                                }
                                stream = null;
                            }
                        }
                        if (stream == null) {
                            index.incrementAndGet();
                            java.time.LocalDate date = LocalDate.now();
                            logfile = new File(pattern.replace("%d", String.valueOf((date.getYear() * 10000 + date.getMonthValue() * 100 + date.getDayOfMonth()))));
                            logfile.getParentFile().mkdirs();
                            length.set(logfile.length());
                            stream = new FileOutputStream(logfile, append);
                        }
                        //----------------------写日志-------------------------
                        String message = getFormatter().format(record);
                        String encoding = getEncoding();
                        byte[] bytes = encoding == null ? message.getBytes() : message.getBytes(encoding);
                        stream.write(bytes);
                        length.addAndGet(bytes.length);
                    } catch (Exception e) {
                        ErrorManager err = getErrorManager();
                        if (err != null) err.error(null, e, ErrorManager.WRITE_FAILURE);
                    }
                }

            }
        }.start();
    }

    private void configure() {
        LogManager manager = LogManager.getLogManager();
        String cname = getClass().getName();
        pattern = manager.getProperty(cname + ".pattern");
        if (pattern == null) pattern = "logs/log-%d.log";
        String limitstr = manager.getProperty(cname + ".limit");
        try {
            if (limitstr != null) limit = Math.abs(Integer.decode(limitstr));
        } catch (Exception e) {
        }
        String countstr = manager.getProperty(cname + ".count");
        try {
            if (countstr != null) count = Math.max(1, Math.abs(Integer.decode(countstr)));
        } catch (Exception e) {
        }
        String appendstr = manager.getProperty(cname + ".append");
        try {
            if (appendstr != null) append = "true".equalsIgnoreCase(appendstr) || "1".equals(appendstr);
        } catch (Exception e) {
        }
        String levelstr = manager.getProperty(cname + ".level");
        try {
            if (levelstr != null) {
                Level l = Level.parse(levelstr);
                setLevel(l != null ? l : Level.ALL);
            }
        } catch (Exception e) {
        }
        String filterstr = manager.getProperty(cname + ".filter");
        try {
            if (filterstr != null) {
                Class<?> clz = ClassLoader.getSystemClassLoader().loadClass(filterstr);
                setFilter((Filter) clz.newInstance());
            }
        } catch (Exception e) {
        }
        String formatterstr = manager.getProperty(cname + ".formatter");
        try {
            if (formatterstr != null) {
                Class<?> clz = ClassLoader.getSystemClassLoader().loadClass(formatterstr);
                setFormatter((Formatter) clz.newInstance());
            }
        } catch (Exception e) {
        }
        if (getFormatter() == null) setFormatter(new SimpleFormatter());

        String encodingstr = manager.getProperty(cname + ".encoding");
        try {
            if (encodingstr != null) setEncoding(encodingstr);
        } catch (Exception e) {
        }
    }

    @Override
    public void publish(LogRecord record) {
        final String sourceClassName = record.getSourceClassName();
        if (sourceClassName == null || true) {
            StackTraceElement[] ses = new Throwable().getStackTrace();
            for (int i = 2; i < ses.length; i++) {
                if (ses[i].getClassName().startsWith("java.util.logging")) continue;
                record.setSourceClassName('[' + Thread.currentThread().getName() + "] " + ses[i].getClassName());
                record.setSourceMethodName(ses[i].getMethodName());
                break;
            }
        } else {
            record.setSourceClassName('[' + Thread.currentThread().getName() + "] " + sourceClassName);
        }
        records.offer(record);
    }

    @Override
    public void flush() {
        try {
            if (stream != null) stream.flush();
        } catch (Exception e) {
            ErrorManager err = getErrorManager();
            if (err != null) err.error(null, e, ErrorManager.FLUSH_FAILURE);
        }
    }

    @Override
    public void close() throws SecurityException {
        try {
            if (stream != null) stream.close();
        } catch (Exception e) {
            ErrorManager err = getErrorManager();
            if (err != null) err.error(null, e, ErrorManager.CLOSE_FAILURE);
        }
    }

}
