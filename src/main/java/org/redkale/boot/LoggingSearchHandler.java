/*
 */
package org.redkale.boot;

import java.io.*;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.*;
import java.util.logging.Formatter;
import java.util.regex.Pattern;
import static org.redkale.boot.Application.RESNAME_APP_NAME;
import org.redkale.convert.*;
import org.redkale.convert.json.JsonConvert;
import org.redkale.persistence.*;
import org.redkale.persistence.SearchColumn;
import org.redkale.source.*;
import org.redkale.util.*;

/**
 * 基于SearchSource的日志输出类
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.7.0
 */
public class LoggingSearchHandler extends LoggingBaseHandler {

    protected static final String DEFAULT_TABLE_NAME = "log-record";

    protected final LinkedBlockingQueue<SearchLogRecord> logqueue = new LinkedBlockingQueue();

    protected final AtomicInteger retryCount = new AtomicInteger(3);

    protected String tag = DEFAULT_TABLE_NAME;  //用于表前缀， 默认是 

    protected String tagDateFormat; //需要时间格式化

    protected String pattern;

    protected Pattern denyRegx;

    protected String sourceResourceName;

    protected SearchSource source;

    //在LogManager.getLogManager().readConfiguration执行完后，通过反射注入Application
    protected Application application;

    public LoggingSearchHandler() {
        configure();
        open();
    }

    private void open() {
        final String name = "Redkale-Logging-" + getClass().getSimpleName().replace("Logging", "") + "-Thread";
        final int batchSize = 100; //批量最多100条
        final List<SearchLogRecord> logList = new ArrayList<>();
        final SimpleFormatter formatter = new SimpleFormatter();
        final PrintStream outStream = System.out;
        new Thread() {
            {
                setName(name);
                setDaemon(true);
            }

            @Override
            public void run() {
                while (true) {
                    try {
                        SearchLogRecord log = logqueue.take();
                        while (source == null && retryCount.get() > 0) initSource();
                        //----------------------写日志-------------------------
                        if (source == null) { //source加载失败
                            outStream.print(formatter.format(log.rawLog));
                        } else {
                            logList.add(log);
                            int size = batchSize;
                            while (--size > 0) {
                                log = logqueue.poll();
                                if (log == null) {
                                    break;
                                }
                                logList.add(log);
                            }
                            source.insert(logList);
                        }
                    } catch (Exception e) {
                        ErrorManager err = getErrorManager();
                        if (err != null) {
                            err.error(null, e, ErrorManager.WRITE_FAILURE);
                        }
                    } finally {
                        logList.clear();
                    }
                }

            }
        }.start();
    }

    private void initSource() {
        if (retryCount.get() < 1) {
            return;
        }
        try {
            Utility.sleep(3000); //如果SearchSource自身在打印日志，需要停顿一点时间让SearchSource初始化完成
            if (application == null) {
                Utility.sleep(3000);
            }
            if (application == null) {
                return;
            }
            this.source = (SearchSource) application.loadDataSource(sourceResourceName, false);
            if (retryCount.get() == 1 && this.source == null) {
                System.err.println("ERROR: not load logging.source(" + sourceResourceName + ")");
            }
        } catch (Exception t) {
            ErrorManager err = getErrorManager();
            if (err != null) {
                err.error(null, t, ErrorManager.WRITE_FAILURE);
            }
        } finally {
            retryCount.decrementAndGet();
        }
    }

    private static boolean checkTagName(String name) {  //只能是字母、数字、短横、点、%、$和下划线
        if (name.isEmpty()) {
            return false;
        }
        for (char ch : name.toCharArray()) {
            if (ch >= '0' && ch <= '9') {
                continue;
            }
            if (ch >= 'a' && ch <= 'z') {
                continue;
            }
            if (ch >= 'A' && ch <= 'Z') {
                continue;
            }
            if (ch == '_' || ch == '-' || ch == '%' || ch == '$' || ch == '.') {
                continue;
            }
            return false;
        }
        return true;
    }

    private void configure() {
        LogManager manager = LogManager.getLogManager();
        String cname = getClass().getName();
        this.sourceResourceName = manager.getProperty(cname + ".source");
        if (this.sourceResourceName == null || this.sourceResourceName.isEmpty()) {
            throw new RedkaleException("not found logging.property " + cname + ".source");
        }
        String tagstr = manager.getProperty(cname + ".tag");
        if (tagstr != null && !tagstr.isEmpty()) {
            if (!checkTagName(tagstr.replaceAll("\\$\\{.+\\}", ""))) {
                throw new RedkaleException("found illegal logging.property " + cname + ".tag = " + tagstr);
            }
            this.tag = tagstr.replace("${" + RESNAME_APP_NAME + "}", System.getProperty(RESNAME_APP_NAME, ""));
            if (this.tag.contains("%")) {
                this.tagDateFormat = this.tag;
                Utility.formatTime(this.tagDateFormat, -1, System.currentTimeMillis()); //测试时间格式是否正确
            }
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
                RedkaleClassLoader.putReflectionDeclaredConstructors(clz, clz.getName());
                setFilter((Filter) clz.getDeclaredConstructor().newInstance());
            }
        } catch (Exception e) {
        }
        String formatterstr = manager.getProperty(cname + ".formatter");
        try {
            if (formatterstr != null) {
                Class<?> clz = ClassLoader.getSystemClassLoader().loadClass(formatterstr);
                RedkaleClassLoader.putReflectionDeclaredConstructors(clz, clz.getName());
                setFormatter((Formatter) clz.getDeclaredConstructor().newInstance());
            }
        } catch (Exception e) {
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
        }

        String denyregxstr = manager.getProperty(cname + ".denyregx");
        try {
            if (denyregxstr != null && !denyregxstr.trim().isEmpty()) {
                denyRegx = Pattern.compile(denyregxstr);
            }
        } catch (Exception e) {
        }
    }

    @Override
    public void publish(LogRecord log) {
        if (!isLoggable(log)) {
            return;
        }
        if (denyRegx != null && denyRegx.matcher(log.getMessage()).find()) {
            return;
        }
        if (log.getSourceClassName() != null) {
            StackTraceElement[] ses = new Throwable().getStackTrace();
            for (int i = 2; i < ses.length; i++) {
                if (ses[i].getClassName().startsWith("java.util.logging")) {
                    continue;
                }
                log.setSourceClassName(ses[i].getClassName());
                log.setSourceMethodName(ses[i].getMethodName());
                break;
            }
        }
        String rawTag = tagDateFormat == null ? tag : Utility.formatTime(tagDateFormat, -1, log.getInstant().toEpochMilli());
        fillLogRecord(log);
        logqueue.offer(new SearchLogRecord(rawTag, log));
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() throws SecurityException {
    }

    @Entity
    @Table(name = DEFAULT_TABLE_NAME)
    @DistributeTable(strategy = SearchLogRecord.TableStrategy.class)
    public static class SearchLogRecord {

        @Id
        @ConvertColumn(index = 1)
        @SearchColumn(options = "false")
        public String logid;

        @ConvertColumn(index = 2)
        @SearchColumn(options = "false")
        public String level;

        @ConvertColumn(index = 3)
        @SearchColumn(date = true)
        public long timestamp;

        @ConvertColumn(index = 4)
        @SearchColumn(options = "false")
        public String traceid;

        @ConvertColumn(index = 5)
        public String threadName;

        @ConvertColumn(index = 6)
        @SearchColumn(text = true, options = "offsets")
        public String loggerName;

        @ConvertColumn(index = 7)
        @SearchColumn(text = true, options = "offsets")
        public String methodName;

        @ConvertColumn(index = 8)
        @SearchColumn(text = true, options = "offsets") //, analyzer = "ik_max_word"
        public String message;  //log.message +"\r\n"+ log.thrown

        @Transient
        @ConvertDisabled
        LogRecord rawLog;

        @Transient
        @ConvertDisabled
        String rawTag;

        public SearchLogRecord() {
        }

        protected SearchLogRecord(String tag, LogRecord log) {
            this.rawLog = log;
            this.rawTag = tag;
            this.threadName = Thread.currentThread().getName();
            this.traceid = LoggingBaseHandler.traceEnable && Traces.enable() ? Traces.currentTraceid() : null;
            String msg = log.getMessage();
            if (log.getThrown() != null) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                pw.println();
                log.getThrown().printStackTrace(pw);
                pw.close();
                String throwable = sw.toString();
                this.message = (msg != null && !msg.isEmpty()) ? (msg + "\r\n" + throwable) : throwable;
            } else {
                this.message = msg;
            }
            this.level = log.getLevel().toString();
            this.loggerName = log.getLoggerName();
            this.methodName = log.getSourceClassName() + " " + log.getSourceMethodName();
            this.timestamp = log.getInstant().toEpochMilli();
            this.logid = Utility.format36time(timestamp) + "_" + Utility.uuid();
        }

        @Override
        public String toString() {
            return JsonConvert.root().convertTo(this);
        }

        public static class TableStrategy implements DistributeTableStrategy<SearchLogRecord> {

            @Override
            public String getTable(String table, SearchLogRecord bean) {
                return bean.rawTag;
            }

            @Override
            public String getTable(String table, Serializable primary) {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public String[] getTables(String table, FilterNode node) {
                throw new UnsupportedOperationException("Not supported yet.");
            }

        }
    }
}
