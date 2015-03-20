/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.http;

import com.wentch.redkale.net.*;
import com.wentch.redkale.util.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import static java.nio.file.StandardWatchEventKinds.*;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.logging.*;
import java.util.regex.*;

/**
 *
 * @author zhangjx
 */
public final class HttpResourceServlet extends HttpServlet {

    private static final Logger logger = Logger.getLogger(HttpResourceServlet.class.getSimpleName());

    //缓存总大小, 默认128M
    protected long cachelimit = 128 * 1024 * 1024L;

    protected final LongAdder cachedLength = new LongAdder();

    //最大可缓存的文件大小，  大于该值的文件将不被缓存
    protected long cachelengthmax = 1 * 1024 * 1024;

    protected File root = new File("./root/");

    protected final ConcurrentHashMap<String, FileEntry> files = new ConcurrentHashMap<>();

    protected WatchService watcher;

    protected final ConcurrentHashMap<WatchKey, Path> keymaps = new ConcurrentHashMap<>();

    protected SimpleEntry<Pattern, String>[] locationRewrites;

    protected Thread watchThread;

    protected Predicate<String> ranges;

    @Override
    public void init(Context context, AnyValue config) {
        if (config != null) {
            String rootstr = config.getValue("webroot", "root").trim();
            if (rootstr.indexOf(':') < 0 && rootstr.indexOf('/') != 0 && System.getProperty("APP_HOME") != null) {
                rootstr = new File(System.getProperty("APP_HOME"), rootstr).getPath();
            }
            String rangesValue = config.getValue("ranges");
            this.ranges = rangesValue != null ? Pattern.compile(rangesValue).asPredicate() : null;
            try {
                this.root = new File(rootstr).getCanonicalFile();
            } catch (IOException ioe) {
                this.root = new File(rootstr);
            }
            AnyValue cacheconf = config.getAnyValue("caches");
            if (cacheconf != null) {
                this.cachelimit = parseLenth(cacheconf.getValue("limit"), 128 * 1024 * 1024L);
                this.cachelengthmax = parseLenth(cacheconf.getValue("lengthmax"), 1 * 1024 * 1024L);
            }
            List<SimpleEntry<Pattern, String>> locations = new ArrayList<>();
            for (AnyValue av : config.getAnyValues("rewrite")) {
                if ("location".equals(av.getValue("type"))) {
                    String m = av.getValue("match");
                    String f = av.getValue("forward");
                    if (m != null && f != null) {
                        locations.add(new SimpleEntry<>(Pattern.compile(m), f));
                    }
                }
            }
            this.locationRewrites = locations.isEmpty() ? null : locations.toArray(new SimpleEntry[locations.size()]);
        }
        if (this.cachelimit < 1) return;
        if (this.root != null) {
            try {
                this.watcher = this.root.toPath().getFileSystem().newWatchService();
                this.watchThread = new Thread() {

                    @Override
                    public void run() {
                        try {
                            final String rootstr = root.getCanonicalPath();
                            while (!this.isInterrupted()) {
                                final WatchKey key = watcher.take();
                                final Path parent = keymaps.get(key);
                                if (parent == null) {
                                    key.cancel();
                                    continue;
                                }
                                key.pollEvents().stream().forEach((event) -> {
                                    try {
                                        Path path = parent.resolve((Path) event.context());
                                        final String uri = path.toString().substring(rootstr.length()).replace('\\', '/');
                                        //logger.log(Level.FINEST, "file(" + uri + ") happen " + event.kind() + " event");
                                        Thread.sleep(1000L);  //等待update file完毕
                                        if (event.kind() == ENTRY_DELETE) {
                                            files.remove(uri);
                                        } else if (event.kind() == ENTRY_MODIFY) {
                                            FileEntry en = files.get(uri);
                                            if (en != null) en.update();
                                        }
                                    } catch (Exception ex) {
                                        logger.log(Level.FINE, event.context() + " occur erroneous", ex);
                                    }
                                });
                                key.reset();
                            }
                        } catch (Exception e) {
                        }
                    }
                };
                this.watchThread.setName("Servlet-ResourceWatch-Thread");
                this.watchThread.setDaemon(true);
                this.watchThread.start();
            } catch (IOException ex) {
                logger.log(Level.WARNING, HttpResourceServlet.class.getSimpleName() + " start watch-thread error", ex);
            }
        }
    }

    @Override
    public void destroy(Context context, AnyValue config) {
        if (this.watcher != null) {
            try {
                this.watcher.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        if (this.watchThread != null && this.watchThread.isAlive()) {
            this.watchThread.interrupt();
        }
    }

    private static long parseLenth(String value, long defValue) {
        if (value == null) return defValue;
        value = value.toUpperCase().replace("B", "");
        if (value.endsWith("G")) return Long.decode(value.replace("G", "")) * 1024 * 1024 * 1024;
        if (value.endsWith("M")) return Long.decode(value.replace("M", "")) * 1024 * 1024;
        if (value.endsWith("K")) return Long.decode(value.replace("K", "")) * 1024;
        return Long.decode(value);
    }

    @Override
    public void execute(HttpRequest request, HttpResponse response) throws IOException {
        String uri = request.getRequestURI();
        if (locationRewrites != null) {
            for (SimpleEntry<Pattern, String> entry : locationRewrites) {
                Matcher matcher = entry.getKey().matcher(uri);
                if (matcher.find()) {
                    StringBuffer sb = new StringBuffer(uri.length());
                    matcher.appendReplacement(sb, entry.getValue());
                    matcher.appendTail(sb);
                    uri = sb.toString();
                    break;
                }
            }
        }
        if (uri.length() == 0 || uri.equals("/")) uri = "/index.html";
        //System.out.println(request);
        FileEntry entry = watcher == null ? createFileEntry(uri) : files.get(uri);
        if (entry == null) {
            entry = createFileEntry(uri);
            if (entry != null && watcher != null) files.put(uri, entry);
        }
        if (entry == null) {
            response.finish404();
        } else {
            response.finishFile(entry.file, entry.content);
        }
    }

    private FileEntry createFileEntry(String uri) {
        File file = new File(root, uri);
        if (!file.isFile() || !file.canRead()) return null;
        FileEntry en = new FileEntry(this, file);
        if (watcher == null) return en;
        try {
            Path p = file.getParentFile().toPath();
            keymaps.put(p.register(watcher, ENTRY_MODIFY, ENTRY_DELETE), p);
        } catch (IOException e) {
            logger.log(Level.INFO, HttpResourceServlet.class.getSimpleName() + " create FileEntry(" + uri + ") erroneous", e);
        }
        return en;
    }

    private static final class FileEntry {

        final File file;

        private final HttpResourceServlet servlet;

        ByteBuffer content;

        public FileEntry(final HttpResourceServlet servlet, File file) {
            this.servlet = servlet;
            this.file = file;
            update();
        }

        public void update() {
            if (this.content != null) {
                this.servlet.cachedLength.add(0L - this.content.remaining());
                this.content = null;
            }
            long length = this.file.length();
            if (length > this.servlet.cachelengthmax) return;
            if (this.servlet.cachedLength.longValue() + length > this.servlet.cachelimit) return;
            try {
                FileInputStream in = new FileInputStream(file);
                ByteArrayOutputStream out = new ByteArrayOutputStream((int) file.length());
                byte[] bytes = new byte[10240];
                int pos;
                while ((pos = in.read(bytes)) != -1) {
                    out.write(bytes, 0, pos);
                }
                in.close();
                byte[] bs = out.toByteArray();
                ByteBuffer buf = ByteBuffer.allocateDirect(bs.length);
                buf.put(bs);
                buf.flip();
                this.content = buf.asReadOnlyBuffer();
                this.servlet.cachedLength.add(this.content.remaining());
            } catch (Exception e) {
                logger.log(Level.INFO, HttpResourceServlet.class.getSimpleName() + " update FileEntry(" + file + ") erroneous", e);
            }
        }

        @Override
        protected void finalize() throws Throwable {
            if (this.content != null) this.servlet.cachedLength.add(0L - this.content.remaining());
            super.finalize();
        }

        public long getCachedLength() {
            return this.content == null ? 0L : this.content.remaining();
        }

    }
}
