/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.boot;

import java.io.*;
import java.nio.file.Files;
import static java.nio.file.StandardCopyOption.*;
import java.util.Calendar;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.*;
import java.util.logging.*;
import java.util.regex.Pattern;
import org.redkale.util.*;

/**
 * 自定义的日志输出类
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@SuppressWarnings("unchecked")
public class LoggingFileHandler extends LoggingBaseHandler {

    /**
     * SNCP的日志输出Handler
     */
    public static class LoggingSncpFileHandler extends LoggingFileHandler {

        @Override
        public String getPrefix() {
            return "sncp-";
        }
    }

    public static class LoggingConsoleHandler extends ConsoleHandler {

        private Pattern denyRegx;

        public LoggingConsoleHandler() {
            super();
            setFormatter(new LoggingFormater());
            configure();
        }

        private void configure() {
            LogManager manager = LogManager.getLogManager();
            String denyregxstr = manager.getProperty(LoggingConsoleHandler.class.getName() + ".denyregx");
            if (denyregxstr == null) {
                denyregxstr = manager.getProperty("java.util.logging.ConsoleHandler.denyregx");
            }
            try {
                if (denyregxstr != null && !denyregxstr.trim().isEmpty()) {
                    this.denyRegx = Pattern.compile(denyregxstr);
                }
            } catch (Exception e) {
                //do nothing
            }
        }

        @Override
        public void publish(LogRecord log) {
            if (denyRegx != null && denyRegx.matcher(log.getMessage()).find()) {
                return;
            }
            fillLogRecord(log);
            super.publish(log);
        }
    }

    protected final LinkedBlockingQueue<LogRecord> logqueue = new LinkedBlockingQueue();

    protected String pattern;

    protected String patternDateFormat; //需要时间格式化

    protected String unusual; //不为null表示将 WARNING、SEVERE 级别的日志写入单独的文件中

    protected String unusualDateFormat; //需要时间格式化

    private int limit;   //文件大小限制

    private final AtomicInteger logindex = new AtomicInteger();

    private final AtomicInteger logunusualindex = new AtomicInteger();

    private int count = 1;  //文件限制

    private long tomorrow;

    protected boolean append;

    protected Pattern denyregx;

    private final AtomicLong logLength = new AtomicLong();

    private final AtomicLong logUnusualLength = new AtomicLong();

    private File logfile;

    private File logunusualfile;

    private OutputStream logstream;

    private OutputStream logunusualstream;

    public LoggingFileHandler() {
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
        if (this.tomorrow != t) {
            logindex.set(0);
        }
        this.tomorrow = t;
    }

    private void open() {
        final String name = "Redkale-Logging-" + getClass().getSimpleName().replace("Logging", "") + "-Thread";
        new Thread() {
            {
                setName(name);
                setDaemon(true);
            }

            @Override
            public void run() {
                while (true) {
                    try {
                        LogRecord log = logqueue.take();
                        final boolean bigger = (limit > 0 && limit <= logLength.get());
                        final boolean changeday = tomorrow <= log.getMillis();
                        if (bigger || changeday) {
                            updateTomorrow();
                            if (logstream != null) {
                                logstream.close();
                                if (bigger) {
                                    for (int i = Math.min(count - 2, logindex.get() - 1); i > 0; i--) {
                                        File greater = new File(logfile.getPath() + "." + i);
                                        if (greater.exists()) {
                                            Files.move(greater.toPath(), new File(logfile.getPath() + "." + (i + 1)).toPath(), REPLACE_EXISTING, ATOMIC_MOVE);
                                        }
                                    }
                                    Files.move(logfile.toPath(), new File(logfile.getPath() + ".1").toPath(), REPLACE_EXISTING, ATOMIC_MOVE);
                                } else {
                                    if (logfile.exists() && logfile.length() < 1) {
                                        logfile.delete();
                                    }
                                }
                                logstream = null;
                            }
                        }
                        if (unusual != null && changeday && logunusualstream != null) {
                            logunusualstream.close();
                            if (limit > 0 && limit <= logUnusualLength.get()) {
                                for (int i = Math.min(count - 2, logunusualindex.get() - 1); i > 0; i--) {
                                    File greater = new File(logunusualfile.getPath() + "." + i);
                                    if (greater.exists()) {
                                        Files.move(greater.toPath(), new File(logunusualfile.getPath() + "." + (i + 1)).toPath(), REPLACE_EXISTING, ATOMIC_MOVE);
                                    }
                                }
                                Files.move(logunusualfile.toPath(), new File(logunusualfile.getPath() + ".1").toPath(), REPLACE_EXISTING, ATOMIC_MOVE);
                            } else {
                                if (logunusualfile.exists() && logunusualfile.length() < 1) {
                                    logunusualfile.delete();
                                }
                            }
                            logunusualstream = null;
                        }
                        if (logstream == null) {
                            logindex.incrementAndGet();
                            logfile = new File(patternDateFormat == null ? pattern : Times.formatTime(patternDateFormat, -1, System.currentTimeMillis()));
                            logfile.getParentFile().mkdirs();
                            logLength.set(logfile.length());
                            logstream = new FileOutputStream(logfile, append);
                        }
                        if (unusual != null && logunusualstream == null) {
                            logunusualindex.incrementAndGet();
                            logunusualfile = new File(unusualDateFormat == null ? unusual : Times.formatTime(unusualDateFormat, -1, System.currentTimeMillis()));
                            logunusualfile.getParentFile().mkdirs();
                            logUnusualLength.set(logunusualfile.length());
                            logunusualstream = new FileOutputStream(logunusualfile, append);
                        }
                        //----------------------写日志-------------------------
                        String message = getFormatter().format(log);
                        String encoding = getEncoding();
                        byte[] bytes = encoding == null ? message.getBytes() : message.getBytes(encoding);
                        logstream.write(bytes);
                        logLength.addAndGet(bytes.length);
                        if (unusual != null && (log.getLevel() == Level.WARNING || log.getLevel() == Level.SEVERE)) {
                            logunusualstream.write(bytes);
                            logUnusualLength.addAndGet(bytes.length);
                        }
                    } catch (Exception e) {
                        ErrorManager err = getErrorManager();
                        if (err != null) {
                            err.error(null, e, ErrorManager.WRITE_FAILURE);
                        }
                    }
                }

            }
        }.start();
    }

    public String getPrefix() {
        return "";
    }

    private void configure() {
        LogManager manager = LogManager.getLogManager();
        String cname = LoggingFileHandler.class.getName();
        this.pattern = manager.getProperty(cname + ".pattern");
        if (this.pattern == null) {
            this.pattern = "logs-%tm/" + getPrefix() + "log-%td.log";
        } else {
            int pos = this.pattern.lastIndexOf('/');
            if (pos > 0) {
                this.pattern = this.pattern.substring(0, pos + 1) + getPrefix() + this.pattern.substring(pos + 1);
            } else {
                this.pattern = getPrefix() + this.pattern;
            }
        }
        if (this.pattern != null && this.pattern.contains("%")) { //需要时间格式化
            this.patternDateFormat = this.pattern;
            Times.formatTime(this.patternDateFormat, -1, System.currentTimeMillis()); //测试时间格式是否正确
        }
        String unusualstr = manager.getProperty(cname + ".unusual");
        if (unusualstr != null) {
            int pos = unusualstr.lastIndexOf('/');
            if (pos > 0) {
                this.unusual = unusualstr.substring(0, pos + 1) + getPrefix() + unusualstr.substring(pos + 1);
            } else {
                this.unusual = getPrefix() + unusualstr;
            }
        }
        if (this.unusual != null && this.unusual.contains("%")) { //需要时间格式化
            this.unusualDateFormat = this.unusual;
            Times.formatTime(this.unusualDateFormat, -1, System.currentTimeMillis()); //测试时间格式是否正确
        }
        String limitstr = manager.getProperty(cname + ".limit");
        try {
            if (limitstr != null) {
                limitstr = limitstr.toUpperCase();
                boolean g = limitstr.indexOf('G') > 0;
                boolean m = limitstr.indexOf('M') > 0;
                boolean k = limitstr.indexOf('K') > 0;
                int ls = Math.abs(Integer.decode(limitstr.replace("G", "").replace("M", "").replace("K", "").replace("B", "")));
                if (g) {
                    ls *= 1024 * 1024 * 1024;
                } else if (m) {
                    ls *= 1024 * 1024;
                } else if (k) {
                    ls *= 1024;
                }
                this.limit = ls;
            }
        } catch (Exception e) {
            //do nothing
        }
        String countstr = manager.getProperty(cname + ".count");
        try {
            if (countstr != null) {
                this.count = Math.max(1, Math.abs(Integer.decode(countstr)));
            }
        } catch (Exception e) {
            //do nothing
        }
        String appendstr = manager.getProperty(cname + ".append");
        try {
            if (appendstr != null) {
                this.append = "true".equalsIgnoreCase(appendstr) || "1".equals(appendstr);
            }
        } catch (Exception e) {
            //do nothing
        }
        String levelstr = manager.getProperty(cname + ".level");
        try {
            if (levelstr != null) {
                Level l = Level.parse(levelstr);
                setLevel(l != null ? l : Level.ALL);
            }
        } catch (Exception e) {
            //do nothing
        }
        String filterstr = manager.getProperty(cname + ".filter");
        try {
            if (filterstr != null) {
                Class<?> clz = ClassLoader.getSystemClassLoader().loadClass(filterstr);
                RedkaleClassLoader.putReflectionDeclaredConstructors(clz, clz.getName());
                setFilter((Filter) clz.getDeclaredConstructor().newInstance());
            }
        } catch (Exception e) {
            //do nothing
        }
        String formatterstr = manager.getProperty(cname + ".formatter");
        try {
            if (formatterstr != null) {
                Class<?> clz = ClassLoader.getSystemClassLoader().loadClass(formatterstr);
                RedkaleClassLoader.putReflectionDeclaredConstructors(clz, clz.getName());
                setFormatter((Formatter) clz.getDeclaredConstructor().newInstance());
            }
        } catch (Exception e) {
            //do nothing
        }
        if (getFormatter() == null) {
            setFormatter(new SimpleFormatter());
        }

        String encodingstr = manager.getProperty(cname + ".encoding");
        try {
            if (encodingstr != null) {
                setEncoding(encodingstr);
            }
        } catch (Exception e) {
            //do nothing
        }

        String denyregxstr = manager.getProperty(cname + ".denyregx");
        try {
            if (denyregxstr != null && !denyregxstr.trim().isEmpty()) {
                denyregx = Pattern.compile(denyregxstr);
            }
        } catch (Exception e) {
            //do nothing
        }
    }

    @Override
    public void publish(LogRecord log) {
        if (!isLoggable(log)) {
            return;
        }
        if (denyregx != null && denyregx.matcher(log.getMessage()).find()) {
            return;
        }
        fillLogRecord(log);
        logqueue.offer(log);
    }

    @Override
    public void flush() {
        try {
            if (logstream != null) {
                logstream.flush();
            }
        } catch (Exception e) {
            ErrorManager err = getErrorManager();
            if (err != null) {
                err.error(null, e, ErrorManager.FLUSH_FAILURE);
            }
        }
    }

    @Override
    public void close() throws SecurityException {
        try {
            if (logstream != null) {
                logstream.close();
            }
        } catch (Exception e) {
            ErrorManager err = getErrorManager();
            if (err != null) {
                err.error(null, e, ErrorManager.CLOSE_FAILURE);
            }
        }
    }

}
