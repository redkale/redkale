/*
 */
package org.redkale.boot;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.redkale.net.http.MimeType;
import org.redkale.util.*;
import org.redkale.util.AnyValue.DefaultAnyValue;

/**
 * 配置源Agent, 在init方法内需要实现读取配置信息，如果支持配置更改通知，也需要在init里实现监听
 *
 * 配置项优先级: 本地配置 &#60; 配置中心 &#60; 环境变量
 *
 *
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.7.0
 */
public abstract class PropertiesAgent {

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    //envProperties更新锁
    private final ReentrantLock updateLock = new ReentrantLock();

    /**
     * 编译时进行的操作
     *
     * @param conf 节点配置
     */
    public void compile(AnyValue conf) {
    }

    /**
     * ServiceLoader时判断配置是否符合当前实现类
     *
     * @param config 节点配置
     *
     * @return boolean
     */
    public abstract boolean acceptsConf(AnyValue config);

    /**
     * 初始化配置源，配置项需要写入envProperties，并监听配置项的变化
     *
     * @param application Application
     * @param conf        节点配置
     *
     * @return 加载的配置项, key:namespace
     */
    public abstract Map<String, Properties> init(Application application, AnyValue conf);

    /**
     * 销毁动作
     *
     * @param conf 节点配置
     */
    public abstract void destroy(AnyValue conf);

    protected final void updateEnvironmentProperties(Application application, String namespace, List<ResourceEvent> events) {
        if (Utility.isEmpty(events)) {
            return;
        }
        updateLock.lock();
        try {
            Properties envRegisterProps = new Properties();
            Set<String> envRemovedKeys = new HashSet<>();
            Properties envChangedProps = new Properties();

//            Set<String> sourceRemovedKeys = new HashSet<>();
//            Properties sourceChangedProps = new Properties();
            Set<String> loggingRemovedKeys = new HashSet<>();
            Properties loggingChangedProps = new Properties();

//            Set<String> clusterRemovedKeys = new HashSet<>();
//            Properties clusterChangedProps = new Properties();
//
//            Set<String> messageRemovedKeys = new HashSet<>();
//            Properties messageChangedProps = new Properties();
            for (ResourceEvent<String> event : events) {
                if (namespace != null && namespace.startsWith("logging")) {
                    if (event.newValue() == null) {
                        if (application.loggingProperties.containsKey(event.name())) {
                            loggingRemovedKeys.add(event.name());
                        }
                    } else {
                        loggingChangedProps.put(event.name(), event.newValue());
                    }
                    continue;
                }
                if (event.name().startsWith("redkale.datasource.") || event.name().startsWith("redkale.datasource[")
                    || event.name().startsWith("redkale.cachesource.") || event.name().startsWith("redkale.cachesource[")) {
//                    if (event.name().endsWith(".name")) {
//                        logger.log(Level.WARNING, "skip illegal key " + event.name() + " in source config " + (namespace == null ? "" : namespace) + ", key cannot endsWith '.name'");
//                    } else {
//                        if (!Objects.equals(event.newValue(), this.sourceProperties.getProperty(event.name()))) {
//                            if (event.newValue() == null) {
//                                if (this.sourceProperties.containsKey(event.name())) {
//                                    sourceRemovedKeys.add(event.name());
//                                }
//                            } else {
//                                sourceChangedProps.put(event.name(), event.newValue());
//                            }
//                        }
//                    }
                } else if (event.name().startsWith("redkale.mq.") || event.name().startsWith("redkale.mq[")) {
//                    if (event.name().endsWith(".name")) {
//                        logger.log(Level.WARNING, "skip illegal key " + event.name() + " in mq config " + (namespace == null ? "" : namespace) + ", key cannot endsWith '.name'");
//                    } else {
//                        if (!Objects.equals(event.newValue(), this.messageProperties.getProperty(event.name()))) {
//                            if (event.newValue() == null) {
//                                if (this.messageProperties.containsKey(event.name())) {
//                                    messageRemovedKeys.add(event.name());
//                                }
//                            } else {
//                                messageChangedProps.put(event.name(), event.newValue());
//                            }
//                        }
//                    }
                } else if (event.name().startsWith("redkale.cluster.")) {
//                    if (!Objects.equals(event.newValue(), this.clusterProperties.getProperty(event.name()))) {
//                        if (event.newValue() == null) {
//                            if (this.clusterProperties.containsKey(event.name())) {
//                                clusterRemovedKeys.add(event.name());
//                            }
//                        } else {
//                            clusterChangedProps.put(event.name(), event.newValue());
//                        }
//                    }
                } else if (event.name().startsWith("system.property.")) {
                    String propName = event.name().substring("system.property.".length());
                    if (event.newValue() == null) {
                        System.getProperties().remove(propName);
                    } else {
                        System.setProperty(propName, event.newValue());
                    }
                } else if (event.name().startsWith("mimetype.property.")) {
                    String propName = event.name().substring("system.property.".length());
                    if (event.newValue() != null) {
                        MimeType.add(propName, event.newValue());
                    }
                } else if (event.name().startsWith("redkale.")) {
                    logger.log(Level.WARNING, "not support the environment property key " + event.name() + " on change event");
                } else {
                    if (!Objects.equals(event.newValue(), application.envProperties.getProperty(event.name()))) {
                        envRegisterProps.put(event.name(), event.newValue());
                        if (event.newValue() == null) {
                            if (application.envProperties.containsKey(event.name())) {
                                envRemovedKeys.add(event.name());
                            }
                        } else {
                            envChangedProps.put(event.name(), event.newValue());
                        }
                    }
                }
            }
            //普通配置项的变更
            if (!envRegisterProps.isEmpty()) {
                application.envProperties.putAll(envChangedProps);
                envRemovedKeys.forEach(application.envProperties::remove);
                DefaultAnyValue oldConf = (DefaultAnyValue) application.getAppConfig().getAnyValue("properties");
                DefaultAnyValue newConf = new DefaultAnyValue();
                oldConf.forEach((k, v) -> newConf.addValue(k, v));
                application.envProperties.forEach((k, v) -> {
                    newConf.addValue("property", new DefaultAnyValue().addValue("name", k.toString()).addValue("value", v.toString()));
                });
                oldConf.replace(newConf);
                application.getResourceFactory().register(envRegisterProps, "", Environment.class);
            }

            //日志配置项的变更
            if (!loggingChangedProps.isEmpty() || !loggingRemovedKeys.isEmpty()) {
                //只是简单变更日志级别则直接操作，无需重新配置日志
                if (loggingRemovedKeys.isEmpty() && loggingChangedProps.size() == 1 && loggingChangedProps.containsKey(".level")) {
                    try {
                        Level logLevel = Level.parse(loggingChangedProps.getProperty(".level"));
                        Logger.getGlobal().setLevel(logLevel);
                        application.loggingProperties.putAll(loggingChangedProps);
                        logger.log(Level.INFO, "Reconfig logging level to " + logLevel);
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Reconfig logging level error, new level is " + loggingChangedProps.getProperty(".level"));
                    }
                } else {
                    Properties newLogProps = new Properties();
                    newLogProps.putAll(application.loggingProperties);
                    newLogProps.putAll(loggingChangedProps);
                    loggingRemovedKeys.forEach(newLogProps::remove);
                    application.reconfigLogging(false, newLogProps);
                    logger.log(Level.INFO, "Reconfig logging finished ");
                }
            }
        } finally {
            updateLock.unlock();
        }
    }

}
