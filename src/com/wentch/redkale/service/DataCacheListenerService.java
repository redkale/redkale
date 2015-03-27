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

    private final boolean finest = logger.isLoggable(Level.FINEST);

    ;

    @Resource(name = "APP_NODE")
    private String localNodeName = "";

    @Resource(name = "APP_GROUP")
    private String localGroupName = "";

    @Resource(name = "APP_GROUP")
    private Map<String, Set<String>> groups;

    @Resource(name = ".*")
    private HashMap<String, DataSource> sourcesmap;

    @Resource(name = ".*")
    private HashMap<String, DataCacheListenerService> nodesmap;

    @Override
    public void init(AnyValue config) {
        if (finest) {
            logger.finest(this.getClass().getSimpleName() + "-localgroup: " + localGroupName);
            logger.finest(this.getClass().getSimpleName() + "-groups: " + groups);
            logger.finest(this.getClass().getSimpleName() + "-sources: " + sourcesmap);
        }
    }

    @Override
    public <T> void insert(String sourceName, Class<T> clazz, T... entitys) {
        if (finest) logger.finest("(source:" + sourceName + ") insert " + clazz + " --> " + Arrays.toString(entitys));
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
                                    sendInsert(localGroupName, false, sourceName, entry.getKey(), entry.getValue());
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
    public <T> void sendInsert(String group, boolean ignoreRemote, String sourceName, Class<T> clazz, T... entitys) {
        if (nodesmap == null || groups == null) return;
        if (ignoreRemote && finest) logger.finest(DataSource.class.getSimpleName() + "(" + group + "--" + this.localNodeName + "," + sourceName + ") onGroupSendInsert " + Arrays.toString(entitys));
        for (Map.Entry<String, Set<String>> en : groups.entrySet()) {
            if (group != null && group.equals(en.getKey())) { //同机房
                for (String onode : en.getValue()) {
                    if (onode.equals(localNodeName)) continue;
                    DataCacheListenerService service = nodesmap.get(onode);
                    if (service != null) {
                        try {
                            service.sendInsert(group, false, sourceName, clazz, entitys);
                        } catch (Exception e) {
                            logger.log(Level.FINE, this.getClass().getSimpleName() + " send insert error (" + group + "--" + onode + ", " + sourceName + ", " + clazz + ", " + Arrays.toString(entitys) + ")", e);
                        }
                    }
                }
                if (ignoreRemote) break;
            } else if (!ignoreRemote) {
                for (String onode : en.getValue()) {
                    DataCacheListenerService service = nodesmap.get(onode);
                    if (service != null) {
                        try {
                            service.sendInsert(group, false, sourceName, clazz, entitys);
                            break;   //有一个成功就退出
                        } catch (Exception e) {
                            logger.log(Level.FINE, this.getClass().getSimpleName() + " send insert error (" + group + "--" + onode + ", " + sourceName + ", " + clazz + ", " + Arrays.toString(entitys) + ")", e);
                        }
                    }
                }
            }
        }
    }

    public final <T> void onSendInsert(String group, boolean ignoreRemote, String sourceName, Class<T> clazz, T... entitys) {
        if (finest) logger.finest(DataSource.class.getSimpleName() + "(" + this.localNodeName + "," + sourceName + ") onSendInsert " + Arrays.toString(entitys));
        ((DataJDBCSource) sourcesmap.get(sourceName)).insertCache(entitys);
        if (!this.localGroupName.equals(group)) sendInsert(this.localGroupName, true, sourceName, clazz, entitys); //不是同一机房来的资源需要同步到其他同机房的节点上
    }

    @Override
    public <T> void update(String sourceName, Class<T> clazz, T... values) {
        if (finest) logger.finest("(source:" + sourceName + ") update " + clazz + " --> " + Arrays.toString(values));
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
                                    sendUpdate(localGroupName, false, sourceName, entry.getKey(), entry.getValue());
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
    public <T> void sendUpdate(String group, boolean ignoreRemote, String sourceName, Class<T> clazz, T... entitys) {
        if (nodesmap == null || groups == null) return;
        if (ignoreRemote && finest) logger.finest(DataSource.class.getSimpleName() + "(" + group + "--" + this.localNodeName + "," + sourceName + ") onGroupSendUpdate " + Arrays.toString(entitys));
        for (Map.Entry<String, Set<String>> en : groups.entrySet()) {
            if (group != null && group.equals(en.getKey())) { //同机房
                for (String onode : en.getValue()) {
                    if (onode.equals(localNodeName)) continue;
                    DataCacheListenerService service = nodesmap.get(onode);
                    if (service != null) {
                        try {
                            service.sendUpdate(group, false, sourceName, clazz, entitys);
                        } catch (Exception e) {
                            logger.log(Level.FINE, this.getClass().getSimpleName() + " send update error (" + group + "--" + onode + ", " + sourceName + ", " + clazz + ", " + Arrays.toString(entitys) + ")", e);
                        }
                    }
                }
                if (ignoreRemote) break;
            } else if (!ignoreRemote) {
                for (String onode : en.getValue()) {
                    DataCacheListenerService service = nodesmap.get(onode);
                    if (service != null) {
                        try {
                            service.sendUpdate(group, false, sourceName, clazz, entitys);
                            break;   //有一个成功就退出
                        } catch (Exception e) {
                            logger.log(Level.FINE, this.getClass().getSimpleName() + " send update error (" + group + "--" + onode + ", " + sourceName + ", " + clazz + ", " + Arrays.toString(entitys) + ")", e);
                        }
                    }
                }
            }
        }
    }

    public final <T> void onSendUpdate(String group, boolean ignoreRemote, String sourceName, Class<T> clazz, T... entitys) {
        if (finest) logger.finest(DataSource.class.getSimpleName() + "(" + group + "--" + this.localNodeName + "," + sourceName + ") onSendUpdate " + Arrays.toString(entitys));
        ((DataJDBCSource) sourcesmap.get(sourceName)).updateCache(clazz, entitys);
        if (!this.localGroupName.equals(group)) sendUpdate(this.localGroupName, true, sourceName, clazz, entitys); //不是同一机房来的资源需要同步到其他同机房的节点上
    }

    @Override
    public <T> void delete(String sourceName, Class<T> clazz, Serializable... ids) {
        if (finest) logger.finest("(source:" + sourceName + ") delete " + clazz + " --> " + Arrays.toString(ids));
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
                                    sendDelete(localGroupName, false, sourceName, entry.getKey(), entry.getValue());
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
    public <T> void sendDelete(String group, boolean ignoreRemote, String sourceName, Class<T> clazz, Serializable... ids) {
        if (nodesmap == null || groups == null) return;
        if (ignoreRemote && finest) logger.finest(DataSource.class.getSimpleName() + "(" + group + "--" + this.localNodeName + "," + sourceName + ") onGroupSendDelete " + Arrays.toString(ids));
        for (Map.Entry<String, Set<String>> en : groups.entrySet()) {
            if (group != null && group.equals(en.getKey())) { //同机房
                for (String onode : en.getValue()) {
                    if (onode.equals(localNodeName)) continue;
                    DataCacheListenerService service = nodesmap.get(onode);
                    if (service != null) {
                        try {
                            service.sendDelete(group, false, sourceName, clazz, ids);
                        } catch (Exception e) {
                            logger.log(Level.FINE, this.getClass().getSimpleName() + " send delete error (" + group + "--" + onode + ", " + sourceName + ", " + clazz + ", " + Arrays.toString(ids) + ")", e);
                        }
                    }
                }
                if (ignoreRemote) break;
            } else if (!ignoreRemote) {
                for (String onode : en.getValue()) {
                    DataCacheListenerService service = nodesmap.get(onode);
                    if (service != null) {
                        try {
                            service.sendDelete(group, false, sourceName, clazz, ids);
                            break;   //有一个成功就退出
                        } catch (Exception e) {
                            logger.log(Level.FINE, this.getClass().getSimpleName() + " send delete error (" + group + "--" + onode + ", " + sourceName + ", " + clazz + ", " + Arrays.toString(ids) + ")", e);
                        }
                    }
                }
            }
        }
    }

    public final <T> void onSendDelete(String group, boolean ignoreRemote, String sourceName, Class<T> clazz, Serializable... ids) {
        if (finest) logger.finest(DataSource.class.getSimpleName() + "(" + group + "--" + this.localNodeName + "," + sourceName + ") onSendDelete " + clazz.getName() + " " + Arrays.toString(ids));
        ((DataJDBCSource) sourcesmap.get(sourceName)).deleteCache(clazz, ids);
        if (!this.localGroupName.equals(group)) sendDelete(this.localGroupName, true, sourceName, clazz, ids); //不是同一机房来的资源需要同步到其他同机房的节点上
    }
}
