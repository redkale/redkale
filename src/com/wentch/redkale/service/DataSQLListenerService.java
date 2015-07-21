/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.service;

import com.wentch.redkale.source.DataSQLListener;
import com.wentch.redkale.source.DataSource;
import com.wentch.redkale.source.DataDefaultSource;
import com.wentch.redkale.util.AnyValue;
import com.wentch.redkale.util.AutoLoad;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import javax.annotation.Resource;

/**
 *
 * @author zhangjx
 */
@AutoLoad(false)
public class DataSQLListenerService implements DataSQLListener, Service {

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    private static final String format = "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%tL";

    private boolean finest;

    @Resource(name = "APP_NODE")
    private String localNodeName = "";

    private String localIDCName = "";

    @Resource(name = "APP_HOME")
    private File home;

    private File root;

    @Resource(name = ".*")
    HashMap<String, DataSource> sourcemaps;

    private ConcurrentHashMap<String, BlockingQueue<String>> queues = new ConcurrentHashMap<>();

    @Resource
    private HashMap<String, DataSQLListenerService> nodemaps;

    private final HashSet<String> allidcs = new HashSet<>();

    private ConcurrentHashMap<String, PrintStream> syncfiles = new ConcurrentHashMap<>();

    @Override
    public void init(AnyValue config) {
        finest = logger.isLoggable(Level.FINEST);
        //nodename的前两位字符表示机房ID
        if (localNodeName.length() > 2) localIDCName = getIDC(localNodeName);
        if (finest) logger.fine("LocalNodeName: " + localNodeName + ", " + localIDCName + "      " + this.nodemaps);
        if (this.nodemaps == null) return;
        this.nodemaps.forEach((x, y) -> allidcs.add(x.substring(0, 2)));
    }

    @Override
    public void destroy(AnyValue config) {
        this.syncfiles.forEach((x, y) -> {
            y.close();
        });
    }

    private void write(String node, String sourceName, String... sqls) {
        if (sourceName == null || sourceName.isEmpty()) sourceName = "<>";
        String key = node + "-" + sourceName;
        PrintStream channel = syncfiles.get(key);
        try {
            if (channel == null) {
                if (this.root == null) {
                    this.root = new File(home, "dbsync");
                    this.root.mkdirs();
                }
                channel = new PrintStream(new FileOutputStream(new File(this.root, key + ".sql"), true), false, "UTF-8");
                syncfiles.put(key, channel);
            }
            for (String sql : sqls) {
                channel.print(sql + ";\r\n");
            }
            channel.flush();
        } catch (Exception e) {
            logger.log(Level.WARNING, "write sql file error. (" + node + ", " + sourceName + ", " + Arrays.toString(sqls) + ")", e);
        }
    }

    @Override
    public void insert(String sourceName, String... sqls) {
        put(sourceName, sqls);
    }

    @Override
    public void update(String sourceName, String... sqls) {
        put(sourceName, sqls);
    }

    @Override
    public void delete(String sourceName, String... sqls) {
        put(sourceName, sqls);
    }

    private void put(final String sourceName, String... sqls) {
        String date = String.format(format, System.currentTimeMillis());
        BlockingQueue<String> queue = this.queues.get(sourceName);
        if (queue == null) {
            synchronized (this) {
                queue = this.queues.get(sourceName);
                if (queue == null) {
                    queue = new ArrayBlockingQueue<>(1024 * 1024);
                    this.queues.put(sourceName, queue);
                    final BlockingQueue<String> tq = queue;
                    new Thread() {
                        {
                            setName(DataSQLListener.class.getSimpleName() + "-" + (sourceName.isEmpty() ? "<>" : sourceName) + "-Thread");
                            setDaemon(true);
                        }

                        @Override
                        public void run() {
                            while (true) {
                                try {
                                    String sql = tq.take();
                                    send(sourceName, sql);
                                } catch (Exception e) {
                                    logger.log(Level.SEVERE, this.getName() + " occur error");
                                }
                            }

                        }
                    }.start();
                }
            }
        }
        try {
            for (String sql : sqls) {
                queue.put("/* " + date + " */ " + sql);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, this.getClass().getSimpleName() + " put queue error" + Arrays.toString(sqls), e);
        }
    }

    private String getIDC(String nodeName) {
        return nodeName.substring(0, 2);
    }

    @RemoteOn
    public void send(String sourceName, String... sqls) {
        if (this.nodemaps == null) return;
        final Set<String> idcs = new HashSet<>();
        idcs.add(localIDCName);
        nodemaps.forEach((x, y) -> {
            try {
                String idc = getIDC(x);
                if (!idcs.contains(idc)) {
                    y.send(sourceName, sqls);
                    idcs.add(idc);
                }
            } catch (Exception e) {
                logger.log(Level.FINE, this.getClass().getSimpleName() + " send error (" + x + ", " + sourceName + ", " + Arrays.toString(sqls) + ")", e);
            }
        });
        allidcs.forEach(x -> {
            if (!idcs.contains(x)) write(x, sourceName, sqls);
        });
    }

    public final void onSend(String sourceName, String... sqls) {
        ((DataDefaultSource) sourcemaps.get(sourceName)).execute(sqls);
    }

}
