/*
 *
 */
package org.redkale.cluster.spi;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.logging.Level;
import org.redkale.boot.Application;
import org.redkale.boot.ModuleEngine;
import org.redkale.inject.ResourceEvent;
import org.redkale.source.SourceManager;
import org.redkale.util.AnyValue;
import org.redkale.util.AnyValueWriter;
import org.redkale.util.RedkaleClassLoader;

/**
 *
 * @author zhangjx
 */
public class ClusterModuleEngine extends ModuleEngine {

    //第三方服务配置资源
    //@since 2.8.0
    private Properties clusterProperties = new Properties();

    //第三方服务发现管理接口
    //@since 2.1.0
    private ClusterAgent clusterAgent;

    public ClusterModuleEngine(Application application) {
        super(application);
    }

    /**
     * 结束Application.init方法前被调用
     */
    @Override
    public void onAppPostInit() {
        ClusterAgent cluster = null;
        AnyValue clusterConf = application.getAppConfig().getAnyValue("cluster");
        if (clusterConf != null) {
            try {
                String classVal = environment.getPropertyValue(clusterConf.getValue("type", clusterConf.getValue("value"))); //兼容value字段
                if (classVal == null || classVal.isEmpty() || classVal.indexOf('.') < 0) { //不包含.表示非类名，比如值: consul, nacos
                    Iterator<ClusterAgentProvider> it = ServiceLoader.load(ClusterAgentProvider.class, application.getClassLoader()).iterator();
                    RedkaleClassLoader.putServiceLoader(ClusterAgentProvider.class);
                    while (it.hasNext()) {
                        ClusterAgentProvider provider = it.next();
                        if (provider != null) {
                            RedkaleClassLoader.putReflectionPublicConstructors(provider.getClass(), provider.getClass().getName()); //loader class
                        }
                        if (provider != null && provider.acceptsConf(clusterConf)) {
                            cluster = provider.createInstance();
                            cluster.setConfig(clusterConf);
                            break;
                        }
                    }
                    if (cluster == null) {
                        ClusterAgent cacheClusterAgent = new CacheClusterAgent();
                        if (cacheClusterAgent.acceptsConf(clusterConf)) {
                            cluster = cacheClusterAgent;
                            cluster.setConfig(clusterConf);
                        }
                    }
                    if (cluster == null) {
                        logger.log(Level.SEVERE, "load application cluster resource, but not found name='type' value error: " + clusterConf);
                    }
                } else {
                    Class type = application.getClassLoader().loadClass(classVal);
                    if (!ClusterAgent.class.isAssignableFrom(type)) {
                        logger.log(Level.SEVERE, "load application cluster resource, but not found "
                            + ClusterAgent.class.getSimpleName() + " implements class error: " + clusterConf);
                    } else {
                        RedkaleClassLoader.putReflectionDeclaredConstructors(type, type.getName());
                        cluster = (ClusterAgent) type.getDeclaredConstructor().newInstance();
                        cluster.setConfig(clusterConf);
                    }
                }
                //此时不能执行cluster.init，因内置的对象可能依赖config.properties配置项
            } catch (Exception e) {
                logger.log(Level.SEVERE, "load application cluster resource error: " + clusterConf, e);
            }
        }
        this.clusterAgent = cluster;

        if (this.clusterAgent != null) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "ClusterAgent (type = " + this.clusterAgent.getClass().getSimpleName() + ") initing");
            }
            long s = System.currentTimeMillis();
            if (this.clusterAgent instanceof CacheClusterAgent) {
                String sourceName = ((CacheClusterAgent) clusterAgent).getSourceName(); //必须在inject前调用，需要赋值Resourcable.name
                SourceManager sourceManager = application.getResourceFactory().find(SourceManager.class);
                sourceManager.loadCacheSource(sourceName, false);
            }
            this.resourceFactory.inject(clusterAgent);
            clusterAgent.init(clusterAgent.getConfig());
            this.resourceFactory.register(ClusterAgent.class, clusterAgent);
            logger.info("ClusterAgent (type = " + this.clusterAgent.getClass().getSimpleName() + ") init in " + (System.currentTimeMillis() - s) + " ms");
        }
    }

    /**
     * 配置项加载后被调用
     */
    @Override
    public void onEnvironmentLoaded(Properties allProps) {
        allProps.forEach((key, val) -> {
            if (key.toString().startsWith("redkale.cluster.")) {
                this.clusterProperties.put(key, val);
            }
        });
    }

    /**
     * 配置项变更时被调用
     *
     * @param namespace 命名空间
     * @param events    变更项
     */
    @Override
    public void onEnvironmentChanged(String namespace, List<ResourceEvent> events) {
        Set<String> clusterRemovedKeys = new HashSet<>();
        Properties clusterChangedProps = new Properties();

        for (ResourceEvent<String> event : events) {
            if (event.name().startsWith("redkale.cluster.")) {
                if (!Objects.equals(event.newValue(), this.clusterProperties.getProperty(event.name()))) {
                    if (event.newValue() == null) {
                        if (this.clusterProperties.containsKey(event.name())) {
                            clusterRemovedKeys.add(event.name());
                        }
                    } else {
                        clusterChangedProps.put(event.name(), event.newValue());
                    }
                }
            }
        }
        //第三方服务注册配置项的变更
        if (!clusterChangedProps.isEmpty() || !clusterRemovedKeys.isEmpty()) {
            if (this.clusterAgent != null) {
                final AnyValueWriter old = (AnyValueWriter) application.getAppConfig().getAnyValue("cluster");
                Properties newProps = new Properties();
                newProps.putAll(clusterProperties);
                List<ResourceEvent> changeEvents = new ArrayList<>();
                clusterChangedProps.forEach((k, v) -> {
                    final String key = k.toString();
                    newProps.put(k, v);
                    changeEvents.add(ResourceEvent.create(key.substring("redkale.cluster.".length()), v, this.clusterProperties.getProperty(key)));
                });
                clusterRemovedKeys.forEach(k -> {
                    final String key = k;
                    newProps.remove(k);
                    changeEvents.add(ResourceEvent.create(key.substring("redkale.cluster.".length()), null, this.clusterProperties.getProperty(key)));
                });
                if (!changeEvents.isEmpty()) {
                    AnyValueWriter back = old.copy();
                    try {
                        old.replace(AnyValue.loadFromProperties(newProps).getAnyValue("redkale").getAnyValue("cluster"));
                        clusterAgent.onResourceChange(changeEvents.toArray(new ResourceEvent[changeEvents.size()]));
                    } catch (RuntimeException e) {
                        old.replace(back);  //还原配置
                        throw e;
                    }
                }
            } else {
                StringBuilder sb = new StringBuilder();
                clusterChangedProps.forEach((k, v) -> {
                    sb.append(ClusterAgent.class.getSimpleName()).append(" skip change '").append(k).append("'\r\n");
                });
                clusterRemovedKeys.forEach(k -> {
                    sb.append(ClusterAgent.class.getSimpleName()).append(" skip change '").append(k).append("'\r\n");
                });
                if (sb.length() > 0) {
                    logger.log(Level.INFO, sb.toString());
                }
            }
            clusterRemovedKeys.forEach(k -> this.clusterProperties.remove(k));
            this.clusterProperties.putAll(clusterChangedProps);
        }
    }

    /**
     * 判断模块的配置项合并策略， 返回null表示模块不识别此配置项
     *
     * @param path 配置项路径
     * @param key  配置项名称
     * @param val1 配置项原值
     * @param val2 配置项新值
     *
     * @return MergeEnum
     */
    @Override
    public AnyValue.MergeEnum mergeAppConfigStrategy(String path, String key, AnyValue val1, AnyValue val2) {
        if ("".equals(path) && "cluster".equals(key)) {
            return AnyValue.MergeEnum.REPLACE;
        }
        return null;
    }

    /**
     * 进入Application.start方法被调用
     */
    @Override
    public void onAppPreStart() {
        if (!application.isSingletonMode() && !application.isCompileMode() && this.clusterAgent != null) {
            this.clusterAgent.register(application);
        }
    }

    /**
     * 服务全部启动后被调用
     */
    public void onServersPostStart() {
        if (this.clusterAgent != null) {
            this.clusterAgent.start();
        }
    }

    /**
     * 服务全部停掉后被调用
     */
    public void onServersPostStop() {
        if (!application.isCompileMode() && clusterAgent != null) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "ClusterAgent destroying");
            }
            long s = System.currentTimeMillis();
            clusterAgent.deregister(application);
            clusterAgent.destroy(clusterAgent.getConfig());
            logger.info("ClusterAgent destroy in " + (System.currentTimeMillis() - s) + " ms");
        }
    }
}
