/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.service;

import com.wentch.redkale.source.*;
import com.wentch.redkale.util.*;
import java.io.*;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;
import java.util.concurrent.*;
import java.util.logging.*;
import javax.annotation.*;

/**
 *
 * @author zhangjx
 */
@AutoLoad(false)
public class DataCacheListenerService implements DataCacheListener, Service {

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    private static final String format = "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%tL";

    private final ConcurrentHashMap<String, BlockingQueue<Map.Entry<Class, Object[]>>> insertQueues = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, BlockingQueue<Map.Entry<Class, Object[]>>> updateQueues = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, BlockingQueue<Map.Entry<Class, Serializable[]>>> deleteQueues = new ConcurrentHashMap<>();

    private boolean finer;

    @Resource(name = "APP_NODE")
    private String localNodeName = "";

    @Resource(name = ".*")
    HashMap<String, DataSource> sourcemap;

    @Resource(name = ".*")
    HashMap<String, DataCacheListenerService> nodemap;

    @Override
    public void init(AnyValue config) {
        finer = logger.isLoggable(Level.FINER);
    }

    @Override
    public <T> void insert(String sourceName, Class<T> clazz, T... entitys) {
        BlockingQueue<Map.Entry<Class, Object[]>> queue = this.insertQueues.get(sourceName);
        if (queue == null) {
            synchronized (this.insertQueues) {
                queue = this.insertQueues.get(sourceName);
                if (queue == null) {
                    queue = new ArrayBlockingQueue<>(10240);
                    this.insertQueues.put(sourceName, queue);
                    final BlockingQueue<Map.Entry<Class, Object[]>> tq = queue;
                    new Thread() {
                        {
                            setName(DataCacheListener.class.getSimpleName() + "-" + (sourceName.isEmpty() ? "<>" : sourceName) + "-Insert-Thread");
                            setDaemon(true);
                        }

                        @Override
                        public void run() {
                            while (true) {
                                try {
                                    Map.Entry<Class, Object[]> entry = tq.take();
                                    sendInsert(sourceName, entry.getKey(), entry.getValue());
                                } catch (Exception e) {
                                    logger.log(Level.SEVERE, this.getName() + " sendInsert occur error", e);
                                }
                            }

                        }
                    }.start();
                }
            }
        }
        try {
            queue.put(new SimpleEntry<>(clazz, entitys));
        } catch (Exception e) {
            logger.log(Level.WARNING, this.getClass().getSimpleName() + " put insert queue error " + Arrays.toString(entitys), e);
        }
    }

    @RemoteOn
    public <T> void sendInsert(String sourceName, Class<T> clazz, T... entitys) {
        if (nodemap == null) return;
        nodemap.forEach((x, y) -> {
            try {
                y.sendInsert(sourceName, clazz, entitys);
            } catch (Exception e) {
                logger.log(Level.FINE, this.getClass().getSimpleName() + " send insert error (" + x + ", " + sourceName + ", " + clazz + ", " + Arrays.toString(entitys) + ")", e);
            }
        });
    }

    public final <T> void onSendInsert(String sourceName, Class<T> clazz, T... entitys) {
        ((DataJDBCSource) sourcemap.get(sourceName)).insertCache(entitys);
        if (finer) logger.finer(DataSource.class.getSimpleName() + "(" + this.localNodeName + "," + sourceName + ") onSendInsert " + Arrays.toString(entitys));
    }

    @Override
    public <T> void update(String sourceName, Class<T> clazz, T... values) {
        BlockingQueue<Map.Entry<Class, Object[]>> queue = this.updateQueues.get(sourceName);
        if (queue == null) {
            synchronized (this.updateQueues) {
                queue = this.updateQueues.get(sourceName);
                if (queue == null) {
                    queue = new ArrayBlockingQueue<>(10240);
                    this.updateQueues.put(sourceName, queue);
                    final BlockingQueue<Map.Entry<Class, Object[]>> tq = queue;
                    new Thread() {
                        {
                            setName(DataCacheListener.class.getSimpleName() + "-" + (sourceName.isEmpty() ? "<>" : sourceName) + "-Update-Thread");
                            setDaemon(true);
                        }

                        @Override
                        public void run() {
                            while (true) {
                                try {
                                    Map.Entry<Class, Object[]> entry = tq.take();
                                    sendUpdate(sourceName, entry.getKey(), entry.getValue());
                                } catch (Exception e) {
                                    logger.log(Level.SEVERE, this.getName() + " sendUpdate occur error", e);
                                }
                            }

                        }
                    }.start();
                }
            }
        }
        try {
            queue.put(new SimpleEntry<>(clazz, values));
        } catch (Exception e) {
            logger.log(Level.WARNING, this.getClass().getSimpleName() + " put update queue error " + clazz + "," + Arrays.toString(values), e);
        }
    }

    @RemoteOn
    public <T> void sendUpdate(String sourceName, Class<T> clazz, Object... values) {
        if (nodemap == null) return;
        nodemap.forEach((x, y) -> {
            try {
                y.sendUpdate(sourceName, clazz, values);
            } catch (Exception e) {
                logger.log(Level.FINE, this.getClass().getSimpleName() + " send update error (" + x + ", " + sourceName + ", " + clazz + ", " + Arrays.toString(values) + ")", e);
            }
        });
    }

    public final <T> void onSendUpdate(String sourceName, Class<T> clazz, T... entitys) {
        ((DataJDBCSource) sourcemap.get(sourceName)).updateCache(clazz, entitys);
        if (finer) logger.finer(DataSource.class.getSimpleName() + "(" + this.localNodeName + "," + sourceName + ") onSendUpdate " + Arrays.toString(entitys));
    }

    @Override
    public <T> void delete(String sourceName, Class<T> clazz, Serializable... ids) {
        BlockingQueue<Map.Entry<Class, Serializable[]>> queue = this.deleteQueues.get(sourceName);
        if (queue == null) {
            synchronized (this.deleteQueues) {
                queue = this.deleteQueues.get(sourceName);
                if (queue == null) {
                    queue = new ArrayBlockingQueue<>(10240);
                    this.deleteQueues.put(sourceName, queue);
                    final BlockingQueue<Map.Entry<Class, Serializable[]>> tq = queue;
                    new Thread() {
                        {
                            setName(DataCacheListener.class.getSimpleName() + "-" + (sourceName.isEmpty() ? "<>" : sourceName) + "-Delete-Thread");
                            setDaemon(true);
                        }

                        @Override
                        public void run() {
                            while (true) {
                                try {
                                    Map.Entry<Class, Serializable[]> entry = tq.take();
                                    sendDelete(sourceName, entry.getKey(), entry.getValue());
                                } catch (Exception e) {
                                    logger.log(Level.SEVERE, this.getName() + " sendDelete occur error", e);
                                }
                            }

                        }
                    }.start();
                }
            }
        }
        try {
            queue.put(new SimpleEntry<>(clazz, ids));
        } catch (Exception e) {
            logger.log(Level.WARNING, this.getClass().getSimpleName() + " put delete queue error " + clazz + "," + Arrays.toString(ids), e);
        }
    }

    @RemoteOn
    public <T> void sendDelete(String sourceName, Class<T> clazz, Serializable... ids) {
        if (nodemap == null) return;
        nodemap.forEach((x, y) -> {
            try {
                y.sendDelete(sourceName, clazz, ids);
            } catch (Exception e) {
                logger.log(Level.FINE, this.getClass().getSimpleName() + " send delete error (" + x + ", " + sourceName + ", " + clazz + ", " + Arrays.toString(ids) + ")", e);
            }
        });
    }

    public final <T> void onSendDelete(String sourceName, Class<T> clazz, Serializable... ids) {
        ((DataJDBCSource) sourcemap.get(sourceName)).deleteCache(clazz, ids);
        if (finer) logger.finer(DataSource.class.getSimpleName() + "(" + this.localNodeName + "," + sourceName + ") onSendDelete " + clazz.getName() + " " + Arrays.toString(ids));
    }
}
