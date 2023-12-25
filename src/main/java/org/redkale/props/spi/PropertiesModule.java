/*
 *
 */
package org.redkale.props.spi;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.redkale.boot.Application;
import org.redkale.boot.BootModule;
import org.redkale.boot.ModuleEngine;
import org.redkale.inject.ResourceEvent;
import org.redkale.util.AnyValue;
import org.redkale.util.Environment;
import org.redkale.util.InstanceProvider;
import org.redkale.util.RedkaleClassLoader;
import org.redkale.util.Utility;

/**
 *
 * 配置模块组件
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
public class PropertiesModule extends BootModule {

    private final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    //配置源管理接口
    //@since 2.7.0
    private PropertiesAgent propertiesAgent;

    //envProperties更新锁
    private final ReentrantLock updateLock = new ReentrantLock();

    public PropertiesModule(Application application) {
        super(application);
    }

    public void destroy() {
        if (this.propertiesAgent != null) {
            long s = System.currentTimeMillis();
            this.propertiesAgent.destroy(application.getAppConfig().getAnyValue("properties"));
            logger.info(this.propertiesAgent.getClass().getSimpleName() + " destroy in " + (System.currentTimeMillis() - s) + " ms");
        }
    }

    /**
     * 读取远程配置，并合并app.config
     */
    public void initRemoteProperties() {
        final AnyValue config = application.getAppConfig();
        //所有配置项，包含本地配置项、logging配置项和配置中心获取的配置项
        final Environment environment = application.getEnvironment();
        Properties logProps = null; //新的日志配置项
        //------------------------------------ 读取配置项 ------------------------------------       
        AnyValue propsConf = config.getAnyValue("properties");
        if (propsConf == null) {
            final AnyValue resources = config.getAnyValue("resources");
            if (resources != null) {
                logger.log(Level.WARNING, "<resources> in application config file is deprecated");
                propsConf = resources.getAnyValue("properties");
            }
        }
        final Properties remoteEnvs = new Properties();
        if (propsConf != null) {
            //可能通过系统环境变量配置信息
            Iterator<PropertiesAgentProvider> it = ServiceLoader.load(PropertiesAgentProvider.class, application.getClassLoader()).iterator();
            RedkaleClassLoader.putServiceLoader(PropertiesAgentProvider.class);
            List<PropertiesAgentProvider> providers = new ArrayList<>();
            while (it.hasNext()) {
                PropertiesAgentProvider provider = it.next();
                if (provider != null && provider.acceptsConf(propsConf)) {
                    RedkaleClassLoader.putReflectionPublicConstructors(provider.getClass(), provider.getClass().getName());
                    providers.add(provider);
                }
            }
            for (PropertiesAgentProvider provider : InstanceProvider.sort(providers)) {
                long s = System.currentTimeMillis();
                this.propertiesAgent = provider.createInstance();
                this.propertiesAgent.bootModule = this;
                application.getResourceFactory().inject(this.propertiesAgent);
                if (application.isCompileMode()) {
                    this.propertiesAgent.compile(propsConf);
                } else {
                    Map<String, Properties> propMap = this.propertiesAgent.init(application, propsConf);
                    int propCount = 0;
                    if (propMap != null) {
                        for (Map.Entry<String, Properties> en : propMap.entrySet()) {
                            propCount += en.getValue().size();
                            if (en.getKey().contains("logging")) {
                                if (logProps != null) {
                                    logger.log(Level.WARNING, "skip repeat logging config properties(" + en.getKey() + ")");
                                } else {
                                    logProps = en.getValue();
                                }
                            } else {
                                remoteEnvs.putAll(en.getValue());
                            }
                        }
                    }
                    logger.info("PropertiesAgent (type = " + this.propertiesAgent.getClass().getSimpleName()
                        + ") load " + propCount + " data in " + (System.currentTimeMillis() - s) + " ms");
                }
                break;  //only first provider
            }
        }

        //重置远程日志配置
        if (Utility.isNotEmpty(logProps)) {
            reconfigLogging(false, logProps);
        }

        if (!remoteEnvs.isEmpty()) {
            mergeEnvProperties(remoteEnvs, null);
        }

    }

    public void onEnvironmentUpdated(String namespace, List<ResourceEvent> events) {
        if (Utility.isEmpty(events)) {
            return;
        }
        updateLock.lock();
        try {
            if (namespace != null && namespace.contains("logging")) {
                //日志配置单独处理
                onEnvironmentUpdated(namespace, events);
                return;
            }
            Set<String> removedKeys = new HashSet<>();
            Properties newEnvs = environment.newProperties();
            for (ResourceEvent<String> event : events) {
                if (event.newValue() != null) {
                    newEnvs.put(event.name(), event.newValue());
                } else {
                    newEnvs.remove(event.name());
                    removedKeys.add(event.name());
                }
            }
            mergeEnvProperties(newEnvs, removedKeys);
            onEnvironmentChanged(namespace, events);
        } finally {
            updateLock.unlock();
        }
    }

    private void mergeEnvProperties(final Properties remoteEnvs, final Set<String> removedKeys) {
        //此时this.envProperties中的内容: 
        //  1、application.xml的properties.property节点配置项
        //  2、application.xml的properties.load节点的配置项
        //  3、logging.properties
        //  4、source.properties
        final Properties newMergeProps = new Properties();
        final AtomicInteger propertyIndex = new AtomicInteger();
        //remoteEnvs包含redkale.properties.mykey.name自定义配置项，也包含mykey.name的配置项
        remoteEnvs.forEach((k, v) -> {
            String key = k.toString();
            if (key.startsWith("redkale.executor.") //节点全局唯一
                || key.startsWith("redkale.transport.") //节点全局唯一
                || key.startsWith("redkale.cluster.") //节点全局唯一
                || key.startsWith("redkale.cache.") //节点全局唯一
                || key.startsWith("redkale.schedule.") //节点全局唯一
                || key.startsWith("redkale.lock.")//节点全局唯一
                || key.startsWith("redkale.mq.")
                || key.startsWith("redkale.mq[")
                || key.startsWith("redkale.group.")
                || key.startsWith("redkale.group[")
                || key.startsWith("redkale.listener.")
                || key.startsWith("redkale.listener[")
                || key.startsWith("redkale.server.")
                || key.startsWith("redkale.server[")) {
                newMergeProps.put(k, v);
            } else { //其他视为普通配置项
                if (key.startsWith("system.property.")) {
                    putEnvValue(k, v);
                } else if (key.startsWith("mimetype.property.")) {
                    putEnvValue(k, v);
                } else if (key.startsWith("redkale.properties.property.")) {
                    newMergeProps.put(k, v);
                    String name = key.substring("redkale.properties.".length());
                    putEnvValue(name, v);
                } else if (key.startsWith("redkale.properties.property[")) {
                    newMergeProps.put(k, v);
                    String name = key.substring("redkale.properties[".length());
                    name = name.substring(0, name.indexOf(']'));
                    putEnvValue(name, v);
                } else if (key.startsWith("redkale.properties.")) { //支持 -Dredkale.properties.mykey = myvalue
                    String prefix = "redkale.properties.property[" + propertyIndex.getAndIncrement() + "]";
                    String name = key.substring("redkale.properties.".length());
                    newMergeProps.put(prefix + ".name", name);
                    newMergeProps.put(prefix + ".value", v);
                    putEnvValue(name, v);
                } else { //独立的普通配置项文件，比如：config.properties文件中的配置项
                    String prefix = "redkale.properties.property[" + propertyIndex.getAndIncrement() + "]";
                    newMergeProps.put(prefix + ".name", k);
                    newMergeProps.put(prefix + ".value", v);
                    putEnvValue(k, v);
                }
            }
        });
        if (removedKeys != null && !removedKeys.isEmpty()) {
            removedKeys.forEach(this::removeEnvValue);
        }
        if (!newMergeProps.isEmpty()) {
            Properties newDyncProps = new Properties();
            newMergeProps.forEach((k, v) -> newDyncProps.put(k.toString(), application.getEnvironment().getPropertyValue(v.toString(), newMergeProps)));
            //合并配置
            application.getAppConfig().merge(AnyValue.loadFromProperties(newDyncProps).getAnyValue("redkale"), createMergeStrategy());
        }
    }

    /**
     * 合并系统配置项的策略
     */
    AnyValue.MergeStrategy createMergeStrategy() {
        return (path, key, val1, val2) -> {
            for (ModuleEngine m : getModuleEngines()) {
                AnyValue.MergeEnum rs = m.mergeAppConfigStrategy(path, key, val1, val2);
                if (rs != null) {
                    return rs;
                }
            }
            if ("".equals(path)) {
                if ("executor".equals(key)) {
                    return AnyValue.MergeEnum.REPLACE;
                }
                if ("listener".equals(key)) {
                    if (Objects.equals(val1.getValue("value"), val2.getValue("value"))) {
                        return AnyValue.MergeEnum.IGNORE;
                    } else {
                        return AnyValue.MergeEnum.DEFAULT;
                    }
                }
                if ("group".equals(key)) {
                    if (Objects.equals(val1.getValue("name"), val2.getValue("name"))) {
                        return AnyValue.MergeEnum.REPLACE;
                    } else {
                        return AnyValue.MergeEnum.DEFAULT;
                    }
                }
                if ("server".equals(key)) {
                    if (Objects.equals(val1.getValue("name", val1.getValue("protocol") + "_" + val1.getValue("port")),
                        val2.getValue("name", val2.getValue("protocol") + "_" + val2.getValue("port")))) {
                        return AnyValue.MergeEnum.REPLACE;
                    } else {
                        return AnyValue.MergeEnum.DEFAULT;
                    }
                }
            }
            if ("properties".equals(path)) {
                if ("property".equals(key)) {
                    if (Objects.equals(val1.getValue("name"), val2.getValue("name"))) {
                        return AnyValue.MergeEnum.REPLACE;
                    } else {
                        return AnyValue.MergeEnum.DEFAULT;
                    }
                }
            }
            if ("server".equals(path)) {
                if ("ssl".equals(key)) {
                    return AnyValue.MergeEnum.REPLACE;
                }
                if ("render".equals(key)) {
                    return AnyValue.MergeEnum.REPLACE;
                }
                if ("resource-servlet".equals(key)) {
                    return AnyValue.MergeEnum.REPLACE;
                }
            }
            if ("server.request".equals(path)) {
                if ("remoteaddr".equals(key)) {
                    return AnyValue.MergeEnum.REPLACE;
                }
                if ("rpc".equals(key)) {
                    return AnyValue.MergeEnum.REPLACE;
                }
                if ("locale".equals(key)) {
                    if (Objects.equals(val1.getValue("name"), val2.getValue("name"))) {
                        return AnyValue.MergeEnum.REPLACE;
                    } else {
                        return AnyValue.MergeEnum.DEFAULT;
                    }
                }
            }
            if ("server.response".equals(path)) {
                if ("content-type".equals(key)) {
                    return AnyValue.MergeEnum.REPLACE;
                }
                if ("defcookie".equals(key)) {
                    return AnyValue.MergeEnum.REPLACE;
                }
                if ("options".equals(key)) {
                    return AnyValue.MergeEnum.REPLACE;
                }
                if ("date".equals(key)) {
                    return AnyValue.MergeEnum.REPLACE;
                }
                if ("addheader".equals(key) || "setheader".equals(key)) {
                    if (Objects.equals(val1.getValue("name"), val2.getValue("name"))) {
                        return AnyValue.MergeEnum.REPLACE;
                    } else {
                        return AnyValue.MergeEnum.DEFAULT;
                    }
                }
            }
            return AnyValue.MergeEnum.MERGE;
        };
    }
}
