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
 * 暂时不实现
 *
 * @author zhangjx
 */
@AutoLoad(false)
public class DataSQLListenerService implements DataSQLListener, Service {

    private static final String format = "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%tL";

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    private final boolean finest = logger.isLoggable(Level.FINEST);

    @Resource(name = "APP_HOME")
    private File home;

    @Resource(name = "$")
    private DataSource source;

    private final BlockingQueue<String> queue = new ArrayBlockingQueue<>(1024 * 1024);

    private PrintStream syncfile;

    @Override
    public void init(AnyValue config) {
        new Thread() {
            {
                setName(DataSQLListener.class.getSimpleName() + "-Thread");
                setDaemon(true);
            }

            @Override
            public void run() {
                while (true) {
                    try {
                        String sql = queue.take();
                        send(sql);
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, this.getName() + " occur error");
                    }
                }
            }
        }.start();

    }

    @Override
    public void destroy(AnyValue config) {
        if (syncfile != null) syncfile.close();
    }

    private void write(String... sqls) {
        try {
            if (syncfile == null) {
                File root = new File(home, "dbsync");
                root.mkdirs();
                syncfile = new PrintStream(new FileOutputStream(new File(root, "sql-" + name() + ".sql"), true), false, "UTF-8");
            }
            for (String sql : sqls) {
                syncfile.print(sql + ";\r\n");
            }
            syncfile.flush();
        } catch (Exception e) {
            logger.log(Level.WARNING, "write sql file error. (" + name() + ", " + Arrays.toString(sqls) + ")", e);
        }
    }

    @Override
    public void insert(String... sqls) {
        put(sqls);
    }

    @Override
    public void update(String... sqls) {
        put(sqls);
    }

    @Override
    public void delete(String... sqls) {
        put(sqls);
    }

    private void put(String... sqls) {
        String date = String.format(format, System.currentTimeMillis());
        for (String sql : sqls) {
            try {
                queue.put("/* " + date + " */ " + sql);
            } catch (Exception e) {
                write(sql);
            }
        }
    }

    @MultiRun
    public void send(String... sqls) {
        ((DataDefaultSource) source).execute(sqls);
    }

}
